package io.github.k7t3.fxgraph.mcp.agent.inspector;

import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javafx.collections.ObservableList;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ButtonBase;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jcodec.api.awt.AWTSequenceEncoder;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Inspects the JavaFX scene graph from inside the target JVM.
 * All scene graph access must happen on the JavaFX Application Thread.
 */
public class SceneGraphInspector {

    private static final String SPACE_CHAR = " ";

    private static final int DEFAULT_SCREENSHOT_MAX_WIDTH = 1280;
    private static final int DEFAULT_SCREENSHOT_MAX_HEIGHT = 720;
    private static final int DEFAULT_VIDEO_DURATION_SECONDS = 5;
    private static final int MAX_VIDEO_DURATION_SECONDS = 30;
    private static final int DEFAULT_VIDEO_FRAMES_PER_SECOND = 10;
    private static final int MAX_VIDEO_FRAMES_PER_SECOND = 30;

    /** Tracks highlighted overlay nodes so they can be removed. */
    private Node currentHighlight;
    private Parent currentHighlightParent;
    private final RobotClicker robotClicker;

    public SceneGraphInspector() {
        this(point -> {
            var robot = new Robot();
            robot.mouseMove(point);
            robot.mouseClick(MouseButton.PRIMARY);
        });
    }

    SceneGraphInspector(RobotClicker robotClicker) {
        this.robotClicker = Objects.requireNonNull(robotClicker);
    }

    // =============================================
    // GET_STAGES
    // =============================================

    public AgentResponse getStages() {
        try {
            List<Map<String, Object>> windows = runOnFxThread(this::collectWindows);
            return AgentResponse.success(windows);
        } catch (Exception e) {
            return AgentResponse.error("Failed to get stages: " + e.getMessage());
        }
    }

    // =============================================
    // FIND_NODES
    // =============================================

    @SuppressWarnings("unchecked")
    public AgentResponse findNodes(Map<String, Object> params) {
        try {
            String typeFilter = params != null ? (String) params.get("type") : null;
            String idFilter = params != null ? (String) params.get("id") : null;
            String textFilter = params != null ? (String) params.get("text") : null;
            String styleClassFilter = params != null ? (String) params.get("styleClass") : null;
            String stageIdFilter = params != null ? (String) params.get("stageId") : null;

            List<Map<String, Object>> results = runOnFxThread(() ->
                    searchNodes(typeFilter, idFilter, textFilter, styleClassFilter, stageIdFilter));
            return AgentResponse.success(results);
        } catch (Exception e) {
            return AgentResponse.error("Failed to find nodes: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> searchNodes(String typeFilter, String idFilter,
                                                    String textFilter, String styleClassFilter,
                                                    String stageIdFilter) {
        List<Map<String, Object>> results = new ArrayList<>();
        ObservableList<Window> windows = Window.getWindows();

        for (Window window : windows) {
            Scene scene = window.getScene();
            if (scene == null || scene.getRoot() == null) continue;

            String stageId = windowId(window);
            if (stageIdFilter != null && !stageIdFilter.equals(stageId)) continue;

            searchNodeRecursive(scene.getRoot(), typeFilter, idFilter, textFilter,
                    styleClassFilter, results);
        }

        return results;
    }

    private void searchNodeRecursive(Node node, String typeFilter, String idFilter,
                                     String textFilter, String styleClassFilter,
                                     List<Map<String, Object>> results) {
        if (isInspectorNode(node)) return;

        boolean matches = true;

        if (typeFilter != null && !matchesType(node, typeFilter)) {
            matches = false;
        }

        if (matches && idFilter != null && !Objects.equals(node.getId(), idFilter)) {
            matches = false;
        }

        if (matches && textFilter != null) {
            String text = getTextContent(node);
            if (text == null || !text.contains(textFilter)) {
                matches = false;
            }
        }

        if (matches && styleClassFilter != null && !node.getStyleClass().contains(styleClassFilter)) {
            matches = false;
        }

        if (matches) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("nodeId", System.identityHashCode(node));
            info.put("type", nodeClassName(node));
            String fxId = node.getId();
            if (fxId != null) info.put("id", fxId);
            String text = getTextContent(node);
            if (text != null) info.put("text", text);
            info.put("visible", node.isVisible());
            results.add(info);
        }

        for (var child : NodeHierarchy.directChildren(node)) {
            searchNodeRecursive(child, typeFilter, idFilter, textFilter, styleClassFilter, results);
        }
    }

    /**
     * Extracts text content from a node, supporting {@link javafx.scene.control.Labeled}
     * and {@link javafx.scene.control.TextInputControl} (TextField, TextArea, PasswordField).
     */
    private String getTextContent(Node node) {
        if (node instanceof javafx.scene.control.TextInputControl textInput) {
            return textInput.getText();
        }
        if (node instanceof javafx.scene.control.Labeled labeled) {
            return labeled.getText();
        }
        return null;
    }

    /**
     * Checks if a node matches the given type filter, handling anonymous and inner classes.
     * Uses {@code getSimpleName()} for direct matches, then walks up the superclass chain
     * to find a matching simple name for anonymous/inner classes.
     */
    private boolean matchesType(Node node, String typeFilter) {
        String simpleName = node.getClass().getSimpleName();
        if (!simpleName.isEmpty() && simpleName.equals(typeFilter)) {
            return true;
        }
        // Anonymous/inner class: walk up the superclass chain to find a matching simple name
        Class<?> cls = node.getClass().getSuperclass();
        while (cls != null && cls != Object.class) {
            String parentSimple = cls.getSimpleName();
            if (!parentSimple.isEmpty() && parentSimple.equals(typeFilter)) {
                return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private List<Map<String, Object>> collectWindows() {
        List<Map<String, Object>> result = new ArrayList<>();
        ObservableList<Window> windows = Window.getWindows();
        for (Window window : windows) {
            Scene scene = window.getScene();
            if (scene == null || scene.getRoot() == null) continue;

            Map<String, Object> windowInfo = serializeWindow(window);
            windowInfo.put("width", window.getWidth());
            windowInfo.put("height", window.getHeight());
            windowInfo.put("x", window.getX());
            windowInfo.put("y", window.getY());
            windowInfo.put("focused", window.isFocused());
            result.add(windowInfo);
        }
        return result;
    }

    // =============================================
    // GET_SCENEGRAPH
    // =============================================

    @SuppressWarnings("unchecked")
    public AgentResponse getScenegraph(Map<String, Object> params) {
        try {
            String stageId = params != null ? (String) params.get("stageId") : null;
            int maxDepth = params != null && params.get("depth") != null
                    ? ((Number) params.get("depth")).intValue() : Integer.MAX_VALUE;
            boolean includeProperties = params != null && Boolean.TRUE.equals(params.get("includeProperties"));
            boolean includeTransforms = params != null && Boolean.TRUE.equals(params.get("includeTransforms"));
            boolean includeBounds = params != null && Boolean.TRUE.equals(params.get("includeBounds"));
            List<String> propertyFilter = params != null && params.get("propertyFilter") != null
                    ? (List<String>) params.get("propertyFilter") : null;

            Map<String, Object> result = runOnFxThread(() ->
                    collectScenegraph(stageId, maxDepth, includeProperties, propertyFilter, includeTransforms, includeBounds));
            return AgentResponse.success(result);
        } catch (Exception e) {
            return AgentResponse.error("Failed to get scenegraph: " + e.getMessage());
        }
    }

    private Map<String, Object> collectScenegraph(String stageId, int maxDepth, boolean includeProperties,
                                                   List<String> propertyFilter, boolean includeTransforms,
                                                   boolean includeBounds) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> stages = new ArrayList<>();
        List<Map<String, Object>> rootNodes = new ArrayList<>();

        ObservableList<Window> windows = Window.getWindows();
        for (Window window : windows) {
            Scene scene = window.getScene();
            if (scene == null || scene.getRoot() == null) continue;

            String sid = windowId(window);
            if (stageId != null && !stageId.equals(sid)) continue;

            // Position and size remain available from getStages; keep tree responses compact.
            stages.add(serializeWindow(window));

            Map<String, Object> rootNode = serializeNodeLightweight(scene.getRoot(), 0, maxDepth,
                    includeProperties, propertyFilter, includeTransforms, includeBounds, null);
            rootNodes.add(rootNode);
        }

        result.put("stages", stages);
        result.put("rootNodes", rootNodes);
        return result;
    }

    // =============================================
    // GET_NODE_DETAILS
    // =============================================

    @SuppressWarnings("unchecked")
    public AgentResponse getNodeDetails(Map<String, Object> params) {
        try {
            if (params == null || params.get("nodeId") == null) {
                return AgentResponse.error("nodeId is required");
            }
            int nodeId = ((Number) params.get("nodeId")).intValue();
            List<String> propertyFilter = params.get("propertyFilter") != null
                    ? (List<String>) params.get("propertyFilter") : null;

            Map<String, Object> result = runOnFxThread(() -> collectNodeDetails(nodeId, propertyFilter));
            if (result == null) {
                return AgentResponse.error("Node not found: " + nodeId);
            }
            return AgentResponse.success(result);
        } catch (Exception e) {
            return AgentResponse.error("Failed to get node details: " + e.getMessage());
        }
    }

    private Map<String, Object> collectNodeDetails(int nodeId, List<String> propertyFilter) {
        Node node = findNodeById(nodeId);
        if (node == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();

        // Node basic info (with full depth=1 for immediate children summary; always include bounds for detail view)
        Map<String, Object> nodeInfo = serializeNodeLightweight(node, 0, 1, true, propertyFilter, true, true, null);
        result.put("node", nodeInfo);

        // Detailed properties (filtered if specified)
        List<Map<String, Object>> properties = extractPropertiesFiltered(node, propertyFilter);
        result.put("properties", properties);

        // Children summary (just IDs and classes, no recursion)
        List<Map<String, Object>> childrenSummary = new ArrayList<>();
        for (var child : NodeHierarchy.directChildren(node)) {
            Map<String, Object> childInfo = new LinkedHashMap<>();
            childInfo.put("nodeId", System.identityHashCode(child));
            childInfo.put("type", nodeClassName(child));
            childInfo.put("id", child.getId());
            childInfo.put("visible", child.isVisible());
            childrenSummary.add(childInfo);
        }
        result.put("children", childrenSummary);

        return result;
    }

    // =============================================
    // SET_PROPERTY
    // =============================================

    public AgentResponse setProperty(Map<String, Object> params) {
        try {
            if (params == null) return AgentResponse.error("params required");
            int nodeId = ((Number) params.get("nodeId")).intValue();
            String propertyName = (String) params.get("propertyName");
            Object value = params.get("value");
            String valueType = (String) params.get("valueType");

            Map<String, Object> result = runOnFxThread(() ->
                    doSetProperty(nodeId, propertyName, value, valueType));
            if (result == null) {
                return AgentResponse.error("Node not found or property not writable");
            }
            return AgentResponse.success(result);
        } catch (Exception e) {
            return AgentResponse.error("Failed to set property: " + e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> doSetProperty(int nodeId, String propertyName, Object value, String valueType) {
        Node node = findNodeById(nodeId);
        if (node == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();

        // Handle "style" directly
        if ("style".equals(propertyName)) {
            String oldValue = node.getStyle();
            node.setStyle(value != null ? value.toString() : "");
            result.put("oldValue", oldValue);
            result.put("newValue", value);
            return result;
        }

        // Try to find a property method
        try {
            Method propertyMethod = findPropertyMethod(node, propertyName);
            if (propertyMethod != null) {
                Object observable = propertyMethod.invoke(node);
                if (observable instanceof WritableValue writable) {
                    Object oldValue = writable.getValue();
                    Object convertedValue = convertValue(value, valueType, oldValue);
                    writable.setValue(convertedValue);
                    result.put("oldValue", oldValue != null ? oldValue.toString() : null);
                    result.put("newValue", convertedValue != null ? convertedValue.toString() : null);
                    return result;
                }
            }
        } catch (Exception e) {
            result.put("error", "Failed to set property: " + e.getMessage());
            return result;
        }

        result.put("error", "Property not found or not writable: " + propertyName);
        return result;
    }

    // =============================================
    // SELECT_NODE (highlight)
    // =============================================

    public AgentResponse selectNode(Map<String, Object> params) {
        try {
            if (params == null) return AgentResponse.error("params required");
            int nodeId = ((Number) params.get("nodeId")).intValue();
            boolean showBounds = params.get("showBounds") != null && Boolean.TRUE.equals(params.get("showBounds"));

            Boolean result = runOnFxThread(() -> doSelectNode(nodeId, showBounds));
            if (result == null || !result) {
                return AgentResponse.error("Node not found: " + nodeId);
            }
            return AgentResponse.success(Map.of("highlighted", true));
        } catch (Exception e) {
            return AgentResponse.error("Failed to select node: " + e.getMessage());
        }
    }

    private Boolean doSelectNode(int nodeId, boolean showBounds) {
        // Remove previous highlight
        removeHighlight();

        if (nodeId == 0) {
            // nodeId 0 means clear selection
            return true;
        }

        Node node = findNodeById(nodeId);
        if (node == null) return false;

        if (!showBounds) return true;

        // Create highlight overlay
        try {
            Bounds boundsInParent = node.getBoundsInParent();
            Rectangle highlight = new Rectangle(
                    boundsInParent.getMinX(), boundsInParent.getMinY(),
                    boundsInParent.getWidth(), boundsInParent.getHeight());
            highlight.setId("__fxgraph_highlight__");
            highlight.setFill(Color.TRANSPARENT);
            highlight.setStroke(Color.RED);
            highlight.setStrokeWidth(2);
            highlight.setStrokeType(StrokeType.OUTSIDE);
            highlight.setMouseTransparent(true);
            highlight.setManaged(false);

            Parent parent = node.getParent();
            if (parent == null && node.getScene() != null) {
                parent = node.getScene().getRoot();
            }
            if (parent != null) {
                addToParent(parent, highlight);
                currentHighlight = highlight;
                currentHighlightParent = parent;
            }
        } catch (Exception e) {
            // Highlight is best-effort
        }

        return true;
    }

    // =============================================
    // CLICK_NODE / REQUEST_FOCUS / TYPE_KEY / TAKE_SCREENSHOT
    // =============================================

    /**
     * Clicks a node through JavaFX Robot or a complete synthetic mouse gesture.
     *
     * <p>The optional {@code mode} parameter accepts {@code robot} or {@code synthetic} and
     * defaults to {@code robot}. Robot failures automatically fall back to synthetic input, with
     * the effective mode and failure reason included in the successful response.
     *
     * @param params command parameters containing {@code nodeId} and an optional click {@code mode}
     * @return success with the effective click mode, or an error response when validation fails
     */
    public AgentResponse clickNode(Map<String, Object> params) {
        try {
            if (params == null || params.get("nodeId") == null) {
                return AgentResponse.error("nodeId is required");
            }
            var nodeId = ((Number) params.get("nodeId")).intValue();
            var mode = ClickMode.from(params.get("mode"));

            var outcome = runOnFxThread(() -> doClickNode(nodeId, mode));
            if (outcome.error() != null) {
                return AgentResponse.error(outcome.error());
            }
            if (outcome.mode() == ClickMode.ROBOT) {
                // Robot posts platform input asynchronously; this barrier lets queued events run
                // before the command reports success to a client that may immediately inspect state.
                runOnFxThread(() -> null);
            }
            var data = new LinkedHashMap<String, Object>();
            data.put("clicked", true);
            data.put("mode", outcome.mode().value());
            if (outcome.fallbackReason() != null) {
                data.put("fallbackReason", outcome.fallbackReason());
            }
            return AgentResponse.success(data);
        } catch (Exception e) {
            return AgentResponse.error("Failed to click node: " + e.getMessage());
        }
    }

    /**
     * Activates a {@link ButtonBase} through its semantic {@link ButtonBase#fire()} action.
     *
     * <p>This operation does not emit mouse events. Use {@link #clickNode(Map)} when pointer input
     * behavior is part of the interaction being tested.
     *
     * @param params command parameters containing the required {@code nodeId}
     * @return success when the button action was fired, otherwise an error response
     */
    public AgentResponse activateNode(Map<String, Object> params) {
        try {
            if (params == null || params.get("nodeId") == null) {
                return AgentResponse.error("nodeId is required");
            }
            var nodeId = ((Number) params.get("nodeId")).intValue();

            var error = runOnFxThread(() -> doActivateNode(nodeId));
            if (error != null) {
                return AgentResponse.error(error);
            }
            return AgentResponse.success(Map.of("activated", true));
        } catch (Exception e) {
            return AgentResponse.error("Failed to activate node: " + e.getMessage());
        }
    }

    public AgentResponse requestFocus(Map<String, Object> params) {
        try {
            if (params == null || params.get("nodeId") == null) {
                return AgentResponse.error("nodeId is required");
            }
            int nodeId = ((Number) params.get("nodeId")).intValue();

            Boolean focused = runOnFxThread(() -> doRequestFocus(nodeId));
            if (focused == null || !focused) {
                return AgentResponse.error("Node not found: " + nodeId);
            }
            return AgentResponse.success(Map.of("focused", true));
        } catch (Exception e) {
            return AgentResponse.error("Failed to request focus: " + e.getMessage());
        }
    }

    public AgentResponse typeKey(Map<String, Object> params) {
        try {
            if (params == null || params.get("key") == null) {
                return AgentResponse.error("key is required");
            }
            String key = String.valueOf(params.get("key"));
            Integer nodeId = params.get("nodeId") != null ? ((Number) params.get("nodeId")).intValue() : null;

            String error = runOnFxThread(() -> doTypeKey(nodeId, key));
            if (error != null) {
                return AgentResponse.error(error);
            }
            return AgentResponse.success(Map.of("typed", true));
        } catch (Exception e) {
            return AgentResponse.error("Failed to type key: " + e.getMessage());
        }
    }

    public AgentResponse takeScreenshot(Map<String, Object> params) {
        try {
            Integer nodeId = params != null && params.get("nodeId") != null ? ((Number) params.get("nodeId")).intValue() : null;
            String stageId = params != null ? (String) params.get("stageId") : null;
            String savePath = params != null ? (String) params.get("savePath") : null;
            if (savePath == null || savePath.isBlank()) {
                return AgentResponse.error("savePath is required");
            }

            int maxWidth = extractMaxDimension(params, "maxWidth", DEFAULT_SCREENSHOT_MAX_WIDTH);
            int maxHeight = extractMaxDimension(params, "maxHeight", DEFAULT_SCREENSHOT_MAX_HEIGHT);

            Map<String, Object> screenshot = runOnFxThread(() -> doTakeScreenshot(nodeId, stageId, savePath, maxWidth, maxHeight));
            if (screenshot == null) {
                if (nodeId != null) {
                    return AgentResponse.error("Node not found: " + nodeId);
                }
                return AgentResponse.error("Stage not found");
            }
            return AgentResponse.success(screenshot);
        } catch (Exception e) {
            return AgentResponse.error("Failed to take screenshot: " + e.getMessage());
        }
    }

    /**
     * Captures a silent MP4/H.264 clip from a node or JavaFX window scene.
     *
     * <p>Frames are sampled on the JavaFX Application Thread and encoded on the requesting agent
     * thread. The call completes only after the clip has been finalized. Existing destination files
     * remain unchanged when capture or encoding fails.
     *
     * @param params capture target, destination, duration, frame rate, and maximum dimensions
     * @return a successful response with video metadata, or an error response when validation,
     *         target resolution, capture, or encoding fails
     */
    public AgentResponse captureVideo(Map<String, Object> params) {
        try {
            var nodeId = params != null && params.get("nodeId") != null
                    ? ((Number) params.get("nodeId")).intValue()
                    : null;
            var stageId = params != null ? (String) params.get("stageId") : null;
            var savePath = params != null ? (String) params.get("savePath") : null;
            if (savePath == null || savePath.isBlank()) {
                return AgentResponse.error("savePath is required");
            }

            var durationSeconds = extractBoundedInteger(
                    params,
                    "durationSeconds",
                    DEFAULT_VIDEO_DURATION_SECONDS,
                    1,
                    MAX_VIDEO_DURATION_SECONDS);
            var framesPerSecond = extractBoundedInteger(
                    params,
                    "framesPerSecond",
                    DEFAULT_VIDEO_FRAMES_PER_SECOND,
                    1,
                    MAX_VIDEO_FRAMES_PER_SECOND);
            var maxWidth = extractBoundedInteger(
                    params,
                    "maxWidth",
                    DEFAULT_SCREENSHOT_MAX_WIDTH,
                    2,
                    Integer.MAX_VALUE);
            var maxHeight = extractBoundedInteger(
                    params,
                    "maxHeight",
                    DEFAULT_SCREENSHOT_MAX_HEIGHT,
                    2,
                    Integer.MAX_VALUE);

            var target = runOnFxThread(() -> resolveCaptureTarget(nodeId, stageId));
            if (target == null) {
                return nodeId != null
                        ? AgentResponse.error("Node not found: " + nodeId)
                        : AgentResponse.error("Stage not found");
            }

            var result = doCaptureVideo(
                    target,
                    Paths.get(savePath),
                    durationSeconds,
                    framesPerSecond,
                    maxWidth,
                    maxHeight);
            return AgentResponse.success(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentResponse.error("Video capture was interrupted");
        } catch (Exception e) {
            return AgentResponse.error("Failed to capture video: " + e.getMessage());
        }
    }

    private int extractBoundedInteger(
            Map<String, Object> params,
            String key,
            int defaultValue,
            int minimum,
            int maximum) {
        if (params == null || params.get(key) == null) {
            return defaultValue;
        }
        var value = ((Number) params.get(key)).intValue();
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    key + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private int extractMaxDimension(Map<String, Object> params, String key, int defaultValue) {
        if (params == null || params.get(key) == null) {
            return defaultValue;
        }
        return ((Number) params.get(key)).intValue();
    }

    private ClickOutcome doClickNode(int nodeId, ClickMode mode) {
        var node = findNodeById(nodeId);
        if (node == null) return ClickOutcome.failure("Node not found: " + nodeId);
        if (!isEffectivelyVisible(node)) {
            return ClickOutcome.failure("Node is not visible: " + nodeId);
        }
        if (node.isDisabled()) return ClickOutcome.failure("Node is disabled: " + nodeId);

        var bounds = node.getBoundsInLocal();
        if (hasZeroSize(node, bounds)) {
            return ClickOutcome.failure("Node is not visible or has zero size: " + nodeId);
        }

        var window = node.getScene() != null ? node.getScene().getWindow() : null;
        if (window != null) {
            window.requestFocus();
        }

        double localX = bounds.getMinX() + (bounds.getWidth() / 2.0);
        double localY = bounds.getMinY() + (bounds.getHeight() / 2.0);
        var screenCoordinates = node.localToScreen(localX, localY);
        if (screenCoordinates == null) {
            return ClickOutcome.failure("Node is not attached to a showing window: " + nodeId);
        }

        if (mode == ClickMode.SYNTHETIC) {
            fireSyntheticClick(node, localX, localY, screenCoordinates.getX(), screenCoordinates.getY());
            return ClickOutcome.success(mode);
        }

        try {
            robotClicker.click(screenCoordinates);
            return ClickOutcome.success(mode);
        } catch (RuntimeException exception) {
            fireSyntheticClick(node, localX, localY, screenCoordinates.getX(), screenCoordinates.getY());
            var reason = exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.getClass().getSimpleName();
            return ClickOutcome.fallback(reason);
        }
    }

    private String doActivateNode(int nodeId) {
        var node = findNodeById(nodeId);
        if (node == null) return "Node not found: " + nodeId;
        if (!isEffectivelyVisible(node)) return "Node is not visible: " + nodeId;
        if (node.isDisabled()) return "Node is disabled: " + nodeId;

        var bounds = node.getBoundsInLocal();
        if (hasZeroSize(node, bounds)) {
            return "Node is not visible or has zero size: " + nodeId;
        }
        if (!(node instanceof ButtonBase buttonBase)) {
            return "Node does not support semantic activation: " + nodeClassName(node);
        }

        buttonBase.fire();
        return null;
    }

    private void fireSyntheticClick(
            Node node,
            double localX,
            double localY,
            double screenX,
            double screenY) {
        var sceneCoordinates = node.localToScene(localX, localY);

        // Control skins may implement activation on press or release rather than MOUSE_CLICKED.
        node.fireEvent(createSyntheticMouseEvent(
                node, MouseEvent.MOUSE_PRESSED, sceneCoordinates, screenX, screenY, true));
        node.fireEvent(createSyntheticMouseEvent(
                node, MouseEvent.MOUSE_RELEASED, sceneCoordinates, screenX, screenY, false));
        node.fireEvent(createSyntheticMouseEvent(
                node, MouseEvent.MOUSE_CLICKED, sceneCoordinates, screenX, screenY, false));
    }

    private MouseEvent createSyntheticMouseEvent(
            Node node,
            EventType<MouseEvent> eventType,
            Point2D sceneCoordinates,
            double screenX,
            double screenY,
            boolean primaryButtonDown) {
        return new MouseEvent(
                eventType,
                sceneCoordinates.getX(), sceneCoordinates.getY(),
                screenX, screenY,
                MouseButton.PRIMARY,
                1,
                false, false, false, false,
                primaryButtonDown, false, false,
                true, false, true,
                new PickResult(node, sceneCoordinates.getX(), sceneCoordinates.getY())
        );
    }

    private enum ClickMode {
        ROBOT("robot"),
        SYNTHETIC("synthetic");

        private final String value;

        ClickMode(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }

        private static ClickMode from(Object value) {
            if (value == null) {
                return ROBOT;
            }
            return switch (String.valueOf(value).toLowerCase(Locale.ROOT)) {
                case "robot" -> ROBOT;
                case "synthetic" -> SYNTHETIC;
                default -> throw new IllegalArgumentException(
                        "Unsupported click mode: " + value + ". Expected robot or synthetic");
            };
        }
    }

    private record ClickOutcome(String error, ClickMode mode, String fallbackReason) {
        private static ClickOutcome success(ClickMode mode) {
            return new ClickOutcome(null, mode, null);
        }

        private static ClickOutcome fallback(String reason) {
            return new ClickOutcome(null, ClickMode.SYNTHETIC, reason);
        }

        private static ClickOutcome failure(String error) {
            return new ClickOutcome(error, null, null);
        }
    }

    @FunctionalInterface
    interface RobotClicker {
        void click(Point2D point);
    }

    private boolean hasZeroSize(Node node, Bounds bounds) {
        if (bounds == null || bounds.getWidth() == 0 || bounds.getHeight() == 0) {
            return true;
        }
        return node instanceof Region region
                && (region.getWidth() == 0 || region.getHeight() == 0);
    }

    private boolean isEffectivelyVisible(Node node) {
        for (var current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private Boolean doRequestFocus(int nodeId) {
        Node node = findNodeById(nodeId);
        if (node == null) return false;
        node.requestFocus();
        return true;
    }

    private String doTypeKey(Integer nodeId, String key) {
        if (key == null || key.isEmpty()) return "key is required";

        Node target = nodeId != null ? findNodeById(nodeId) : findFocusedNode();
        if (target == null) return nodeId != null ? "Node not found: " + nodeId : "No focused node found";

        target.requestFocus();

        KeyEvent keyTyped = new KeyEvent(
            KeyEvent.KEY_TYPED,
            "",
            "",
            null,
            false, false, false, false
        );
        target.fireEvent(keyTyped);
        return null;
    }

    private Map<String, Object> doTakeScreenshot(Integer nodeId, String stageId, String savePath, int maxWidth, int maxHeight) {
        WritableImage image;
        String targetType;
        String targetId;

        if (nodeId != null) {
            Node node = findNodeById(nodeId);
            if (node == null) return null;
            image = node.snapshot(new SnapshotParameters(), null);
            targetType = "node";
            targetId = String.valueOf(nodeId);
        } else {
            Window window = findWindow(stageId);
            if (window == null || window.getScene() == null) return null;
            image = window.getScene().snapshot(null);
            targetType = "scenegraph";
            targetId = windowId(window);
        }

        image = scaleImage(image, maxWidth, maxHeight);
        String savedPath = savePng(image, Paths.get(savePath));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mimeType", "image/png");
        result.put("savedPath", savedPath);
        result.put("width", (int) image.getWidth());
        result.put("height", (int) image.getHeight());
        result.put("targetType", targetType);
        result.put("targetId", targetId);
        return result;
    }

    private Map<String, Object> doCaptureVideo(
            CaptureTarget target,
            Path requestedOutput,
            int durationSeconds,
            int framesPerSecond,
            int maxWidth,
            int maxHeight) throws Exception {
        var outputPath = requestedOutput.toAbsolutePath();
        var parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        var temporaryPath = Files.createTempFile(parent, ".fxgraph-video-", ".mp4");
        var frameCount = durationSeconds * framesPerSecond;
        var frameIntervalNanos = TimeUnit.SECONDS.toNanos(1) / framesPerSecond;
        BufferedImage firstFrame = null;

        try {
            var encoder = AWTSequenceEncoder.createSequenceEncoder(
                    temporaryPath.toFile(),
                    framesPerSecond);
            var recordingStartedAt = System.nanoTime();
            var finished = false;
            try {
                for (var frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                    waitForFrame(recordingStartedAt, frameIntervalNanos, frameIndex);
                    var snapshot = runOnFxThread(target::snapshot);
                    if (firstFrame == null) {
                        firstFrame = createInitialVideoFrame(snapshot, maxWidth, maxHeight);
                    }
                    var frame = frameIndex == 0
                            ? firstFrame
                            : createVideoFrame(snapshot, firstFrame.getWidth(), firstFrame.getHeight());
                    encoder.encodeImage(frame);
                }
                encoder.finish();
                finished = true;
            } finally {
                if (!finished) {
                    try {
                        encoder.finish();
                    } catch (Exception ignored) {
                        // The temporary file is discarded below; this call only releases JCodec's channel.
                    }
                }
            }

            moveCompletedVideo(temporaryPath, outputPath);
        } finally {
            Files.deleteIfExists(temporaryPath);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("mimeType", "video/mp4");
        result.put("codec", "H.264");
        result.put("savedPath", outputPath.toString());
        result.put("width", firstFrame.getWidth());
        result.put("height", firstFrame.getHeight());
        result.put("durationSeconds", durationSeconds);
        result.put("framesPerSecond", framesPerSecond);
        result.put("frameCount", frameCount);
        result.put("targetType", target.targetType());
        result.put("targetId", target.targetId());
        return result;
    }

    private void waitForFrame(long recordingStartedAt, long frameIntervalNanos, int frameIndex)
            throws InterruptedException {
        var targetTime = recordingStartedAt + (frameIntervalNanos * frameIndex);
        var remainingNanos = targetTime - System.nanoTime();
        if (remainingNanos > 0) {
            TimeUnit.NANOSECONDS.sleep(remainingNanos);
        }
    }

    private CaptureTarget resolveCaptureTarget(Integer nodeId, String stageId) {
        if (nodeId != null) {
            var node = findNodeById(nodeId);
            return node != null
                    ? new CaptureTarget(node, null, "node", String.valueOf(nodeId))
                    : null;
        }
        var window = findWindow(stageId);
        return window != null && window.getScene() != null
                ? new CaptureTarget(
                        null,
                        window.getScene(),
                        "scenegraph",
                        windowId(window))
                : null;
    }

    private BufferedImage createInitialVideoFrame(WritableImage snapshot, int maxWidth, int maxHeight) {
        var scaledSnapshot = scaleImage(snapshot, maxWidth, maxHeight);
        var width = evenVideoDimension(
                Math.min((int) scaledSnapshot.getWidth(), maxWidth));
        var height = evenVideoDimension(
                Math.min((int) scaledSnapshot.getHeight(), maxHeight));
        return renderVideoFrame(scaledSnapshot, width, height);
    }

    private BufferedImage createVideoFrame(WritableImage snapshot, int width, int height) {
        var scaledSnapshot = scaleImage(snapshot, width, height);
        return renderVideoFrame(scaledSnapshot, width, height);
    }

    private BufferedImage renderVideoFrame(WritableImage scaledSnapshot, int width, int height) {
        var frame = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        var graphics = frame.createGraphics();
        try {
            graphics.setColor(java.awt.Color.BLACK);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            var source = toBufferedImage(scaledSnapshot);
            var scale = Math.min(width / (double) source.getWidth(), height / (double) source.getHeight());
            var renderedWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
            var renderedHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
            var x = (width - renderedWidth) / 2;
            var y = (height - renderedHeight) / 2;
            graphics.drawImage(source, x, y, renderedWidth, renderedHeight, null);
        } finally {
            graphics.dispose();
        }
        return frame;
    }

    private int evenVideoDimension(int dimension) {
        if (dimension < 2) {
            return 2;
        }
        return dimension % 2 == 0 ? dimension : dimension - 1;
    }

    private void moveCompletedVideo(Path temporaryPath, Path outputPath) throws IOException {
        try {
            Files.move(
                    temporaryPath,
                    outputPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporaryPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Window findWindow(String stageId) {
        ObservableList<Window> windows = Window.getWindows();
        for (Window window : windows) {
            if (window.getScene() == null || window.getScene().getRoot() == null) {
                continue;
            }
            if (stageId != null
                    && stageId.equals(windowId(window))) {
                return window;
            }
            if (stageId == null && window instanceof Stage) {
                return window;
            }
        }
        return null;
    }

    private WritableImage scaleImage(WritableImage image, int maxWidth, int maxHeight) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        double scale = Math.min(Math.min(maxWidth / (double) width, maxHeight / (double) height), 1.0);
        if (scale >= 1.0) return image;

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage src = toBufferedImage(image);
        BufferedImage dst = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return toWritableImage(dst);
    }

    private BufferedImage toBufferedImage(WritableImage image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        int[] argb = new int[width * height];
        reader.getPixels(0, 0, width, height, WritablePixelFormat.getIntArgbInstance(), argb, 0, width);
        buffered.setRGB(0, 0, width, height, argb, 0, width);
        return buffered;
    }

    private WritableImage toWritableImage(BufferedImage buffered) {
        WritableImage image = new WritableImage(buffered.getWidth(), buffered.getHeight());
        WritablePixelFormat<IntBuffer> format = WritablePixelFormat.getIntArgbInstance();
        int[] pixels = new int[buffered.getWidth() * buffered.getHeight()];
        buffered.getRGB(0, 0, buffered.getWidth(), buffered.getHeight(), pixels, 0, buffered.getWidth());
        image.getPixelWriter().setPixels(0, 0, buffered.getWidth(), buffered.getHeight(), format, pixels, 0, buffered.getWidth());
        return image;
    }

    private String savePng(WritableImage image, Path outputPath) {
        try {
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            PixelReader reader = image.getPixelReader();
            int[] argb = new int[width * height];
            reader.getPixels(0, 0, width, height, WritablePixelFormat.getIntArgbInstance(), argb, 0, width);
            buffered.setRGB(0, 0, width, height, argb, 0, width);

            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            if (!ImageIO.write(buffered, "png", outputPath.toFile())) {
                throw new IOException("No PNG writer available");
            }

            return outputPath.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write screenshot: " + e.getMessage(), e);
        }
    }

    private Node findFocusedNode() {
        ObservableList<Window> windows = Window.getWindows();
        for (Window window : windows) {
            if (window.getScene() == null) continue;
            Node focusOwner = window.getScene().getFocusOwner();
            if (focusOwner != null) return focusOwner;
        }
        return null;
    }

    private record CaptureTarget(Node node, Scene scene, String targetType, String targetId) {

        private WritableImage snapshot() {
            return node != null
                    ? node.snapshot(new SnapshotParameters(), null)
                    : scene.snapshot(null);
        }
    }

    private void removeHighlight() {
        if (currentHighlight != null && currentHighlightParent != null) {
            try {
                removeFromParent(currentHighlightParent, currentHighlight);
            } catch (Exception e) {
                // Ignore
            }
            currentHighlight = null;
            currentHighlightParent = null;
        }
    }

    // =============================================
    // Node Serialization
    // =============================================

    private Map<String, Object> serializeNodeLightweight(Node node, int currentDepth, int maxDepth,
                                                           boolean includeProperties, List<String> propertyFilter,
                                                           boolean includeTransforms, boolean includeBounds,
                                                           Integer parentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", System.identityHashCode(node));

        // CSS id: only emit when set
        String fxId = node.getId();
        if (fxId != null) {
            result.put("id", fxId);
        }

        result.put("type", nodeClassName(node));

        // visible: only emit when false (default is true)
        if (!node.isVisible()) {
            result.put("visible", false);
        }

        // Style classes (empty list filtered out)
        List<String> styleClasses = new ArrayList<>(node.getStyleClass());
        if (!styleClasses.isEmpty()) {
            result.put("styleClass", styleClasses);
        }

        // Optional: bounds
        if (includeBounds) {
            Bounds bp = node.getBoundsInParent();
            result.put("bounds", Map.of(
                    "x", Math.round(bp.getMinX()),
                    "y", Math.round(bp.getMinY()),
                    "w", Math.round(bp.getWidth()),
                    "h", Math.round(bp.getHeight())));
        }

        // Optional: transforms
        if (includeTransforms) {
            if (node.getOpacity() != 1.0) result.put("opacity", node.getOpacity());
            if (node.getScaleX() != 1.0) result.put("scaleX", node.getScaleX());
            if (node.getScaleY() != 1.0) result.put("scaleY", node.getScaleY());
            if (node.getRotate() != 0.0) result.put("rotate", node.getRotate());
        }

        // Optional: properties (filtered if specified)
        if (includeProperties) {
            result.put("properties", extractPropertiesFiltered(node, propertyFilter));
        }

        // Children
        if (currentDepth < maxDepth) {
            List<Map<String, Object>> children = new ArrayList<>();
            int myId = System.identityHashCode(node);
            for (var child : NodeHierarchy.directChildren(node)) {
                if (isInspectorNode(child)) continue;
                children.add(serializeNodeLightweight(child, currentDepth + 1, maxDepth,
                        includeProperties, propertyFilter, includeTransforms, includeBounds, myId));
            }
            if (!children.isEmpty()) {
                result.put("children", children);
            }
        }

        return result;
    }

    // =============================================
    // Property Extraction
    // =============================================

    private List<Map<String, Object>> extractPropertiesFiltered(Node node, List<String> propertyFilter) {
        List<Map<String, Object>> allProperties = extractProperties(node);
        if (propertyFilter == null || propertyFilter.isEmpty()) {
            return allProperties;
        }
        // Filter to only include requested properties
        Set<String> filterSet = new HashSet<>(propertyFilter);
        return allProperties.stream()
                .filter(p -> filterSet.contains(p.get("name")))
                .toList();
    }

    private List<Map<String, Object>> extractProperties(Node node) {
        List<Map<String, Object>> properties = new ArrayList<>();

        // Use reflection to find all *Property() methods
        Set<String> visited = new HashSet<>();
        Class<?> cls = node.getClass();
        while (cls != null && cls != Object.class) {
            for (Method method : cls.getDeclaredMethods()) {
                String name = method.getName();
                if (name.endsWith("Property") && method.getParameterCount() == 0) {
                    String propName = name.substring(0, name.length() - "Property".length());
                    if (visited.contains(propName)) continue;
                    visited.add(propName);

                    try {
                        method.setAccessible(true);
                        Object propObj = method.invoke(node);
                        if (propObj instanceof ObservableValue<?> observable) {
                            Map<String, Object> prop = new LinkedHashMap<>();
                            prop.put("name", propName);
                            Object val = observable.getValue();
                            prop.put("value", formatPropertyValue(val));
                            prop.put("type", determineType(val));
                            prop.put("writable", propObj instanceof WritableValue);

                            // Categorize
                            prop.put("category", categorizeProperty(propName, cls));
                            properties.add(prop);
                        }
                    } catch (Exception e) {
                        // Skip inaccessible properties
                    }
                }
            }
            cls = cls.getSuperclass();
        }

        // Sort by category then name
        properties.sort(Comparator
                .comparing((Map<String, Object> p) -> (String) p.get("category"))
                .thenComparing(p -> (String) p.get("name")));

        return properties;
    }

    // =============================================
    // Utility Methods
    // =============================================

    private Node findNodeById(int nodeId) {
        ObservableList<Window> windows = Window.getWindows();
        for (Window window : windows) {
            if (window.getScene() != null && window.getScene().getRoot() != null) {
                Node found = searchNode(window.getScene().getRoot(), nodeId);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Node searchNode(Node node, int nodeId) {
        if (System.identityHashCode(node) == nodeId) return node;
        for (var child : NodeHierarchy.directChildren(node)) {
            Node found = searchNode(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    private boolean isInspectorNode(Node node) {
        return node.getId() != null && node.getId().startsWith("__fxgraph_");
    }

    private static String nodeClassName(Node node) {
        Class<?> cls = node.getClass();
        String name = cls.getSimpleName();
        while (name.isEmpty()) {
            cls = cls.getSuperclass();
            if (cls == null) return "Unknown";
            name = cls.getSimpleName();
        }
        return name;
    }

    private static String windowClassName(Window window) {
        Class<?> type = window.getClass();
        var name = type.getSimpleName();
        while (name.isEmpty()) {
            type = type.getSuperclass();
            if (type == null) return "Unknown";
            name = type.getSimpleName();
        }
        return name;
    }

    private static String windowId(Window window) {
        return String.valueOf(System.identityHashCode(window));
    }

    private static Map<String, Object> serializeWindow(Window window) {
        var scene = window.getScene();
        var result = new LinkedHashMap<String, Object>();
        result.put("stageId", windowId(window));
        result.put("windowType", windowClassName(window));
        if (window instanceof Stage stage) {
            result.put("title", stage.getTitle());
        }
        if (window instanceof PopupWindow popup && popup.getOwnerWindow() != null) {
            result.put("ownerWindowId", windowId(popup.getOwnerWindow()));
        }
        result.put("rootNodeId", System.identityHashCode(scene.getRoot()));
        return result;
    }

    private Object formatPropertyValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Color color) {
            return String.format("#%02X%02X%02X%02X",
                    (int) (color.getRed() * 255),
                    (int) (color.getGreen() * 255),
                    (int) (color.getBlue() * 255),
                    (int) (color.getOpacity() * 255));
        }
        if (value instanceof javafx.geometry.Insets insets) {
            return String.format("Insets[top=%.1f, right=%.1f, bottom=%.1f, left=%.1f]",
                    insets.getTop(), insets.getRight(), insets.getBottom(), insets.getLeft());
        }
        if (value instanceof Bounds b) {
            return String.format("Bounds[minX=%.1f, minY=%.1f, width=%.1f, height=%.1f]",
                    b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
        }
        // Default: use toString()
        return value.toString();
    }

    private String determineType(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "string";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Number) return "number";
        if (value instanceof Color) return "color";
        if (value instanceof Enum) return "enum";
        if (value instanceof Bounds) return "bounds";
        return value.getClass().getSimpleName();
    }

    private String categorizeProperty(String propertyName, Class<?> declaringClass) {
        String name = propertyName.toLowerCase();
        if (name.contains("layout") || name.contains("spacing") || name.contains("padding")
                || name.contains("alignment") || name.contains("hgap") || name.contains("vgap")
                || name.contains("managed") || name.contains("min") || name.contains("max")
                || name.contains("pref")) {
            return "layout";
        }
        if (name.contains("style") || name.contains("css") || name.contains("background")
                || name.contains("border") || name.contains("font") || name.contains("color")
                || name.contains("fill") || name.contains("stroke")) {
            return "style";
        }
        if (name.contains("visible") || name.contains("opacity") || name.contains("rotate")
                || name.contains("scale") || name.contains("translate") || name.contains("clip")
                || name.contains("effect") || name.contains("transform") || name.contains("blend")) {
            return "visual";
        }
        if (name.contains("text") || name.contains("graphic") || name.contains("content")
                || name.contains("value") || name.contains("selected") || name.contains("items")) {
            return "content";
        }
        if (name.contains("event") || name.contains("handler") || name.contains("mouse")
                || name.contains("key") || name.contains("focus") || name.contains("hover")
                || name.contains("pressed") || name.contains("drag")) {
            return "interaction";
        }
        return "properties";
    }

    private Method findPropertyMethod(Node node, String propertyName) {
        String methodName = propertyName + "Property";
        Class<?> cls = node.getClass();
        while (cls != null) {
            try {
                Method m = cls.getDeclaredMethod(methodName);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    private Object convertValue(Object value, String valueType, Object currentValue) {
        if (value == null) return null;
        String str = value.toString();

        if (valueType != null) {
            return switch (valueType) {
                case "number" -> {
                    try {
                        yield Double.parseDouble(str);
                    } catch (NumberFormatException e) {
                        yield str;
                    }
                }
                case "boolean" -> Boolean.parseBoolean(str);
                case "color" -> Color.web(str);
                default -> str;
            };
        }

        // Infer from current value type
        if (currentValue instanceof Boolean) return Boolean.parseBoolean(str);
        if (currentValue instanceof Integer) {
            try { return Integer.parseInt(str); } catch (NumberFormatException e) { /* fall through */ }
        }
        if (currentValue instanceof Double) {
            try { return Double.parseDouble(str); } catch (NumberFormatException e) { /* fall through */ }
        }
        if (currentValue instanceof Color) {
            try { return Color.web(str); } catch (Exception e) { /* fall through */ }
        }

        return str;
    }

    @SuppressWarnings("unchecked")
    private void addToParent(Parent parent, Node child) {
        try {
            Method getChildren = Parent.class.getDeclaredMethod("getChildren");
            getChildren.setAccessible(true);
            ((List<Node>) getChildren.invoke(parent)).add(child);
        } catch (Exception e) {
            // Fallback: try with specific parent types
            if (parent instanceof javafx.scene.layout.Pane pane) {
                pane.getChildren().add(child);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void removeFromParent(Parent parent, Node child) {
        try {
            Method getChildren = Parent.class.getDeclaredMethod("getChildren");
            getChildren.setAccessible(true);
            ((List<Node>) getChildren.invoke(parent)).remove(child);
        } catch (Exception e) {
            if (parent instanceof javafx.scene.layout.Pane pane) {
                pane.getChildren().remove(child);
            }
        }
    }

    /**
     * Run a task on the JavaFX Application Thread and wait for the result.
     */
    private <T> T runOnFxThread(java.util.function.Supplier<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return task.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(task.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(10, TimeUnit.SECONDS);
    }
}
