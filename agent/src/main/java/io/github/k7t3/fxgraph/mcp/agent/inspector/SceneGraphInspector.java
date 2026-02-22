package io.github.k7t3.fxgraph.mcp.agent.inspector;

import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.ButtonBase;
import javafx.event.ActionEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Inspects the JavaFX scene graph from inside the target JVM.
 * All scene graph access must happen on the JavaFX Application Thread.
 */
public class SceneGraphInspector {

    private static final String SPACE_CHAR = " ";

    /** Tracks highlighted overlay nodes so they can be removed. */
    private Node currentHighlight;
    private Parent currentHighlightParent;

    public SceneGraphInspector() {
    }

    // =============================================
    // GET_STAGES
    // =============================================

    public AgentResponse getStages() {
        try {
            List<Map<String, Object>> stages = runOnFxThread(this::collectStages);
            return AgentResponse.success(stages);
        } catch (Exception e) {
            return AgentResponse.error("Failed to get stages: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> collectStages() {
        List<Map<String, Object>> result = new ArrayList<>();
        ObservableList<Window> windows = Window.getWindows();
        for (Window window : windows) {
            if (window instanceof Stage stage) {
                Scene scene = stage.getScene();
                if (scene == null || scene.getRoot() == null) continue;

                Map<String, Object> stageInfo = new LinkedHashMap<>();
                stageInfo.put("stageId", String.valueOf(System.identityHashCode(stage)));
                stageInfo.put("title", stage.getTitle());
                stageInfo.put("width", stage.getWidth());
                stageInfo.put("height", stage.getHeight());
                stageInfo.put("x", stage.getX());
                stageInfo.put("y", stage.getY());
                stageInfo.put("focused", stage.isFocused());
                stageInfo.put("rootNodeId", System.identityHashCode(scene.getRoot()));
                result.add(stageInfo);
            }
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
            List<String> propertyFilter = params != null && params.get("propertyFilter") != null
                    ? (List<String>) params.get("propertyFilter") : null;

            Map<String, Object> result = runOnFxThread(() ->
                    collectScenegraph(stageId, maxDepth, includeProperties, propertyFilter, includeTransforms));
            return AgentResponse.success(result);
        } catch (Exception e) {
            return AgentResponse.error("Failed to get scenegraph: " + e.getMessage());
        }
    }

    private Map<String, Object> collectScenegraph(String stageId, int maxDepth, boolean includeProperties,
                                                   List<String> propertyFilter, boolean includeTransforms) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> stages = new ArrayList<>();
        List<Map<String, Object>> rootNodes = new ArrayList<>();
        int totalNodeCount = 0;

        ObservableList<Window> windows = Window.getWindows();
        for (Window window : windows) {
            if (!(window instanceof Stage stage)) continue;
            Scene scene = stage.getScene();
            if (scene == null || scene.getRoot() == null) continue;

            String sid = String.valueOf(System.identityHashCode(stage));
            if (stageId != null && !stageId.equals(sid)) continue;

            Map<String, Object> stageInfo = new LinkedHashMap<>();
            stageInfo.put("stageId", sid);
            stageInfo.put("title", stage.getTitle());
            stageInfo.put("width", stage.getWidth());
            stageInfo.put("height", stage.getHeight());
            stageInfo.put("x", stage.getX());
            stageInfo.put("y", stage.getY());
            stageInfo.put("focused", stage.isFocused());
            stageInfo.put("rootNodeId", System.identityHashCode(scene.getRoot()));
            stages.add(stageInfo);

            Map<String, Object> rootNode = serializeNodeLightweight(scene.getRoot(), 0, maxDepth, includeProperties, propertyFilter, includeTransforms, null);
            rootNodes.add(rootNode);
            totalNodeCount += countNodes(scene.getRoot());
        }

        result.put("stages", stages);
        result.put("rootNodes", rootNodes);
        result.put("totalNodeCount", totalNodeCount);
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

        // Node basic info (with full depth=1 for immediate children summary)
        Map<String, Object> nodeInfo = serializeNodeLightweight(node, 0, 1, true, propertyFilter, true, null);
        result.put("node", nodeInfo);

        // Detailed properties (filtered if specified)
        List<Map<String, Object>> properties = extractPropertiesFiltered(node, propertyFilter);
        result.put("properties", properties);

        // Children summary (just IDs and classes, no recursion)
        List<Map<String, Object>> childrenSummary = new ArrayList<>();
        ObservableList<Node> children = ChildrenGetter.getChildren(node);
        for (Node child : children) {
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

    public AgentResponse clickNode(Map<String, Object> params) {
        try {
            if (params == null || params.get("nodeId") == null) {
                return AgentResponse.error("nodeId is required");
            }
            int nodeId = ((Number) params.get("nodeId")).intValue();

            String error = runOnFxThread(() -> doClickNode(nodeId));
            if (error != null) {
                return AgentResponse.error(error);
            }
            return AgentResponse.success(Map.of("clicked", true));
        } catch (Exception e) {
            return AgentResponse.error("Failed to click node: " + e.getMessage());
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

            Map<String, Object> screenshot = runOnFxThread(() -> doTakeScreenshot(nodeId, stageId, savePath));
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

    private String doClickNode(int nodeId) {
        Node node = findNodeById(nodeId);
        if (node == null) return "Node not found: " + nodeId;

        Window window = node.getScene() != null ? node.getScene().getWindow() : null;
        if (window != null) {
            window.requestFocus();
        }

        if (node instanceof ButtonBase buttonBase) {
            ActionEvent actionEvent = new ActionEvent(ActionEvent.ACTION, buttonBase);
            buttonBase.fireEvent(actionEvent);
            return null;
        }

        Bounds bounds = node.getBoundsInLocal();
        if (bounds == null || bounds.getWidth() == 0 || bounds.getHeight() == 0) {
            return "Node is not visible or has zero size: " + nodeId;
        }

        double localX = bounds.getMinX() + (bounds.getWidth() / 2.0);
        double localY = bounds.getMinY() + (bounds.getHeight() / 2.0);

        javafx.geometry.Point2D screenCoords = node.localToScreen(localX, localY);
        double screenX = screenCoords.getX();
        double screenY = screenCoords.getY();

        MouseEvent clickEvent = new MouseEvent(
            MouseEvent.MOUSE_CLICKED,
            localX, localY,
            screenX, screenY,
            MouseButton.PRIMARY,
            1,
            false, false, false, false,
            true,
            true,
            true,
            true,
            true,
            true,
            null
        );
        node.fireEvent(clickEvent);
        return null;
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

    private Map<String, Object> doTakeScreenshot(Integer nodeId, String stageId, String savePath) {
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
            Stage stage = findStage(stageId);
            if (stage == null || stage.getScene() == null) return null;
            image = stage.getScene().snapshot(null);
            targetType = "scenegraph";
            targetId = String.valueOf(System.identityHashCode(stage));
        }

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

    private Stage findStage(String stageId) {
        ObservableList<Window> windows = Window.getWindows();
        for (Window window : windows) {
            if (!(window instanceof Stage stage) || stage.getScene() == null || stage.getScene().getRoot() == null) {
                continue;
            }
            if (stageId == null || stageId.equals(String.valueOf(System.identityHashCode(stage)))) {
                return stage;
            }
        }
        return null;
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
                                                           boolean includeTransforms, Integer parentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", System.identityHashCode(node));
        result.put("id", node.getId());
        result.put("type", nodeClassName(node));
        result.put("visible", node.isVisible());

        // Style classes (empty list filtered out)
        List<String> styleClasses = new ArrayList<>(node.getStyleClass());
        if (!styleClasses.isEmpty()) {
            result.put("styleClass", styleClasses);
        }

        // Simplified bounds
        Bounds bp = node.getBoundsInParent();
        result.put("bounds", Map.of(
                "x", Math.round(bp.getMinX()),
                "y", Math.round(bp.getMinY()),
                "w", Math.round(bp.getWidth()),
                "h", Math.round(bp.getHeight())));

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
            for (Node child : ChildrenGetter.getChildren(node)) {
                if (isInspectorNode(child)) continue;
                children.add(serializeNodeLightweight(child, currentDepth + 1, maxDepth,
                        includeProperties, propertyFilter, includeTransforms, myId));
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
        for (Node child : ChildrenGetter.getChildren(node)) {
            Node found = searchNode(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    private int countNodes(Node node) {
        if (isInspectorNode(node)) return 0;
        int count = 1;
        for (Node child : ChildrenGetter.getChildren(node)) {
            count += countNodes(child);
        }
        return count;
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
