package io.github.k7t3.fxgraph.mcp.tools;

import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import io.github.k7t3.fxgraph.mcp.model.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.IntFunction;

/**
 * MCP tool definitions for inspecting JavaFX application scene graphs.
 * Each tool communicates with an injected agent running inside the target JVM.
 */
@Service
public class FxgraphService {

    private static final String MISSING_JAVA_INSTRUMENT_MESSAGE =
            "Module java.instrument not found";
    private static final String MISSING_JAVA_INSTRUMENT_ERROR_CODE =
            "TARGET_RUNTIME_MISSING_JAVA_INSTRUMENT";
    private static final String MISSING_JAVA_INSTRUMENT_ACTION =
            "Rebuild the target jlink/jpackage runtime with java.instrument included.";

    private final IntFunction<JavaFxAgent> agentFactory;

    /**
     * Creates a service that opens a fresh connection for each tool invocation.
     */
    public FxgraphService() {
        this(pid -> new JavaFxAgent(Integer.toString(pid)));
    }

    FxgraphService(IntFunction<JavaFxAgent> agentFactory) {
        this.agentFactory = Objects.requireNonNull(agentFactory);
    }

    // ===================================================
    // Discovery & Connection
    // ===================================================

    @Tool(description = "Discover running JavaFX applications. Returns a list of JVM processes that are identified as JavaFX applications, with their PIDs and main classes.")
    public Map<String, Object> discoverApplications() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<JavaFxApplication> apps = JavaFxAgent.discoverApplications();
            result.put("success", true);
            result.put("applications", apps);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Tool(description = "Prepare a JavaFX application for inspection by PID. This injects the inspection agent when necessary, verifies communication, and then closes the transient connection. Other tools connect independently using the same PID.")
    public Map<String, Object> connectApplication(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid) {
        var result = new LinkedHashMap<String, Object>();
        if (pid <= 0) {
            result.put("success", false);
            result.put("error", "PID must be a positive integer: " + pid);
            return result;
        }

        var agent = agentFactory.apply(pid);
        try {
            agent.connect();

            result.put("success", true);
            result.put("agentPort", agent.getAgentPort());
        } catch (Exception e) {
            putConnectionFailure(result, pid, e, e.getMessage());
        } finally {
            agent.disconnectWithoutShutdown();
        }
        return result;
    }

    @Tool(description = "Stop the injected inspection agent in a JavaFX application by PID. Normal tool calls close their transient connections automatically and do not require this operation.")
    public Map<String, Object> disconnectApplication(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid) {
        var result = new LinkedHashMap<String, Object>();
        if (pid <= 0) {
            result.put("success", false);
            result.put("error", "PID must be a positive integer: " + pid);
            return result;
        }

        var agent = agentFactory.apply(pid);
        var stopped = false;
        try {
            agent.connect();
            agent.disconnect();
            stopped = true;
            result.put("success", true);
        } catch (Exception e) {
            putConnectionFailure(result, pid, e, e.getMessage());
        } finally {
            if (!stopped) {
                agent.disconnectWithoutShutdown();
            }
        }
        return result;
    }

    // ===================================================
    // Scene Graph Inspection
    // ===================================================

    @Tool(description = "Get the list of showing JavaFX windows, including Stages and PopupWindows such as ContextMenu and Tooltip. Each entry has a stageId (the legacy name for a window ID), windowType, dimensions, and rootNodeId. Popup entries also include ownerWindowId; Stage entries include title.")
    public Map<String, Object> getStages(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid) {
        return sendAgentCommand(pid, new AgentCommand(AgentCommand.CommandType.GET_STAGES));
    }

    /** Reads public geometry and state from the selected showing window. */
    @Tool(description = "Get geometry and state of a showing Stage or PopupWindow: x, y, width, height, opacity, focused, showing, and Stage-specific maximized, iconified, alwaysOnTop, resizable, title.")
    public Map<String, Object> getWindowDetails(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Window ID from stages.stageId") String stageId) {
        return sendAgentCommand(pid, new AgentCommand(AgentCommand.CommandType.GET_WINDOW_DETAILS, Map.of("stageId", stageId)));
    }

    /** Sets a validated public Stage property and returns the previous and current values. */
    @Tool(description = "Set a Stage property: x, y, width, height, opacity, title, maximized, iconified, alwaysOnTop, or resizable. Numbers must be finite; dimensions positive; opacity 0 through 1. Returns oldValue and newValue. Window-manager changes may be asynchronous; re-read window details.")
    public Map<String, Object> setWindowProperty(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Stage ID from stages.stageId") String stageId,
            @ToolParam(description = "Supported Stage property name") String propertyName,
            @ToolParam(description = "New value as string") String value,
            @ToolParam(description = "Type hint: number, boolean or string; inferred when omitted", required = false) String valueType) {
        var params = new LinkedHashMap<String, Object>();
        params.put("stageId", stageId);
        params.put("propertyName", propertyName);
        params.put("value", value);
        if (valueType != null) params.put("valueType", valueType);
        return sendAgentCommand(pid, new AgentCommand(AgentCommand.CommandType.SET_WINDOW_PROPERTY, params));
    }

    /** Requests close through the application's normal window event handlers. */
    @Tool(description = "Fire WINDOW_CLOSE_REQUEST for the selected window. The application may consume the request. closeRequested means delivered; closed reports the window being hidden; handlerPending means a modal handler is still waiting. Does not force close or synthesize OS shortcuts.")
    public Map<String, Object> closeWindow(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Window ID from stages.stageId") String stageId) {
        return sendAgentCommand(pid, new AgentCommand(AgentCommand.CommandType.CLOSE_WINDOW, Map.of("stageId", stageId)));
    }

    /** Hides a selected popup without dismissing ordinary Stage windows. */
    @Tool(description = "Hide the selected PopupWindow, such as ContextMenu or Tooltip. Rejects ordinary Stages. Embedded overlays such as AtlantaFX ModalPane require their own close action or documented display-property workaround.")
    public Map<String, Object> closePopup(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Popup ID from stages.stageId") String stageId) {
        return sendAgentCommand(pid, new AgentCommand(AgentCommand.CommandType.CLOSE_POPUP, Map.of("stageId", stageId)));
    }

    @Tool(description = "Get scene graph trees for showing JavaFX Stage and PopupWindow scenes. Returns compact hierarchical trees by default. Use depth to limit tree depth, includeBounds to include node bounding boxes, includeProperties to get property details, propertyFilter to limit which properties, and includeTransforms for transform details.")
    public Map<String, Object> getScenegraph(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Window ID from the stageId field (omit to get all showing windows)", required = false) String stageId,
            @ToolParam(description = "Maximum depth to traverse (default: unlimited)", required = false) Integer depth,
            @ToolParam(description = "Include bounding box (x,y,w,h) for each node (default: false)", required = false) Boolean includeBounds,
            @ToolParam(description = "Include property details for each node (default: false)", required = false) Boolean includeProperties,
            @ToolParam(description = "List of property names to include (e.g., ['text', 'value']). Only used when includeProperties=true. Omit to get all properties.", required = false) List<String> propertyFilter,
            @ToolParam(description = "Include transform properties (opacity, scale, rotate) when they differ from defaults (default: false)", required = false) Boolean includeTransforms) {

        Map<String, Object> params = new LinkedHashMap<>();
        if (stageId != null) params.put("stageId", stageId);
        if (depth != null) params.put("depth", depth);
        if (includeBounds != null) params.put("includeBounds", includeBounds);
        if (includeProperties != null) params.put("includeProperties", includeProperties);
        if (propertyFilter != null) params.put("propertyFilter", propertyFilter);
        if (includeTransforms != null) params.put("includeTransforms", includeTransforms);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.GET_SCENEGRAPH, params));
    }

    @Tool(description = "Get a node's properties, children, bounds, style classes, parentId and effective visibility. Use a current nodeId from getScenegraph/findNodes. propertyFilter selects properties; includeAncestors adds the nearest-first containment path to the scene root, including SubScene boundaries.")
    public Map<String, Object> getNodeDetails(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Node ID (identityHashCode of the JavaFX Node)") int nodeId,
            @ToolParam(description = "List of property names to include (e.g., ['text', 'value']). Omit to get all properties.", required = false) List<String> propertyFilter,
            @ToolParam(description = "Include ancestors from immediate container to scene root, crossing SubScene boundaries", required = false) Boolean includeAncestors) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        if (propertyFilter != null) params.put("propertyFilter", propertyFilter);
        if (includeAncestors != null) params.put("includeAncestors", includeAncestors);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.GET_NODE_DETAILS, params));
    }

    /** Preserves the Java caller overload without ancestor information. */
    public Map<String, Object> getNodeDetails(int pid, int nodeId, List<String> propertyFilter) {
        return getNodeDetails(pid, nodeId, propertyFilter, null);
    }

    @Tool(description = "Search showing Stage and PopupWindow scenes by Node class/superclass, CSS id, text or style class. Non-Node FXML controllers and MenuItem objects cannot match; search open menus' rendering nodes instead. Returns node IDs, parentId and effective visibility. stageId selects a window; effectiveVisible filters visibility. Reacquire IDs after reopening popups or recycling virtualized cells.")
    public Map<String, Object> findNodes(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "JavaFX class name to filter (e.g., 'Button', 'TextField'). Omit to match all types.", required = false) String type,
            @ToolParam(description = "CSS id (fx:id) to match exactly. Omit to match all ids.", required = false) String id,
            @ToolParam(description = "Text content to search for (case-sensitive contains match). Omit to match all text.", required = false) String text,
            @ToolParam(description = "Style class name to filter. Omit to match all style classes.", required = false) String styleClass,
            @ToolParam(description = "Window ID from the stageId field. Omit to search all showing Stage and PopupWindow scenes.", required = false) String stageId,
            @ToolParam(description = "Filter by effective visibility, accounting for ancestors, opacity, clipping and scene viewport; does not detect occlusion by other nodes/windows", required = false) Boolean effectiveVisible) {

        Map<String, Object> params = new LinkedHashMap<>();
        if (type != null) params.put("type", type);
        if (id != null) params.put("id", id);
        if (text != null) params.put("text", text);
        if (styleClass != null) params.put("styleClass", styleClass);
        if (stageId != null) params.put("stageId", stageId);
        if (effectiveVisible != null) params.put("effectiveVisible", effectiveVisible);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.FIND_NODES, params));
    }

    /** Preserves the Java caller overload without a visibility filter. */
    public Map<String, Object> findNodes(int pid, String type, String id, String text, String styleClass, String stageId) {
        return findNodes(pid, type, id, text, styleClass, stageId, null);
    }

    /** Scrolls supported containers and lays out the new visible cells before responding. */
    @Tool(description = "Scroll a ListView, TableView, ScrollPane, JavaFX VirtualFlow, Flowless VirtualFlow or VirtualizedScrollPane. Use dx/dy pixels (positive right/down) or align (top/bottom/left/right), exclusively. Re-run findNodes afterwards because virtualized cell IDs may change.")
    public Map<String, Object> scrollNode(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Scrollable container node ID") int nodeId,
            @ToolParam(description = "Horizontal pixel delta, positive right", required = false) Double dx,
            @ToolParam(description = "Vertical pixel delta, positive down", required = false) Double dy,
            @ToolParam(description = "Edge: top, bottom, left or right. Cannot be combined with dx/dy", required = false) String align) {
        var params = new LinkedHashMap<String, Object>();
        params.put("nodeId", nodeId);
        if (dx != null) params.put("dx", dx);
        if (dy != null) params.put("dy", dy);
        if (align != null) params.put("align", align);
        return sendAgentCommand(pid, new AgentCommand(AgentCommand.CommandType.SCROLL_NODE, params));
    }

    /** Reveals a zero-based item in a supported virtualized container. */
    @Tool(description = "Reveal a zero-based index in a ListView, TableView, JavaFX VirtualFlow or Flowless VirtualFlow (including a VirtualizedScrollPane wrapping one). Rejects indices outside the item list. Re-run findNodes afterwards to obtain current cell IDs.")
    public Map<String, Object> scrollToIndex(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Virtualized container node ID") int nodeId,
            @ToolParam(description = "Zero-based item index") int index) {
        return sendAgentCommand(pid, new AgentCommand(AgentCommand.CommandType.SCROLL_TO_INDEX, Map.of("nodeId", nodeId, "index", index)));
    }

    // ===================================================
    // Node Manipulation
    // ===================================================

    @Tool(description = "Set a property value on a JavaFX node. Supports setting text, numbers, booleans, colors, and style strings. Returns the old and new values.")
    public Map<String, Object> setProperty(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Node ID") int nodeId,
            @ToolParam(description = "Property name (e.g. 'text', 'style', 'visible', 'opacity')") String propertyName,
            @ToolParam(description = "New value as string") String value,
            @ToolParam(description = "Value type hint: string, number, boolean, color (optional)", required = false) String valueType) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        params.put("propertyName", propertyName);
        params.put("value", value);
        if (valueType != null) params.put("valueType", valueType);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.SET_PROPERTY, params));
    }

    @Tool(description = "Highlight/select a node in the target JavaFX application by drawing a visual overlay (red border). Pass nodeId=0 to clear the highlight.")
    public Map<String, Object> selectNode(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Node ID (use 0 to clear selection)") int nodeId,
            @ToolParam(description = "Show bounds rectangle overlay (default: true)", required = false) Boolean showBounds) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        params.put("showBounds", showBounds != null ? showBounds : true);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.SELECT_NODE, params));
    }

    /**
     * Sends one or two clicks with the selected button to a node in the target JVM.
     *
     * @param pid process ID of the target JavaFX application
     * @param nodeId node ID in the current target JVM session
     * @param mode synthetic only; null selects the default
     * @param button primary (default), secondary or middle; null selects the default
     * @param clickCount 1 (default) or 2; null selects the default
     * @return agent response with the synthetic mode, button and click count
     */
    @Tool(description = "Click a node with primary, secondary or middle button and one or two clicks. Dispatches JavaFX gestures without OS pointer movement or window focus. Secondary clicks request a context menu; standard submenus receive mouse-enter. Only synthetic input is supported. handlerPending=true means a dispatched action is waiting in a modal handler: inspect its dialog and verify the eventual result, without repeating the action.")
    public Map<String, Object> clickNode(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Node ID") int nodeId,
            @ToolParam(description = "Click mode: synthetic only (default)", required = false)
                    String mode,
            @ToolParam(description = "Mouse button: primary (default), secondary (right), or middle", required = false)
                    String button,
            @ToolParam(description = "Number of clicks: 1 (default) or 2 for a double click", required = false)
                    Integer clickCount) {

        var params = new LinkedHashMap<String, Object>();
        params.put("nodeId", nodeId);
        if (mode != null) {
            params.put("mode", mode);
        }
        if (button != null) {
            params.put("button", button);
        }
        if (clickCount != null) {
            params.put("clickCount", clickCount);
        }

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.CLICK_NODE, params));
    }

    /**
     * Sends a single primary click using the selected mode.
     *
     * @param pid target process ID
     * @param nodeId node ID in the current target JVM session
     * @param mode synthetic only; null selects the default
     * @return agent response describing the delivered click
     */
    public Map<String, Object> clickNode(int pid, int nodeId, String mode) {
        return clickNode(pid, nodeId, mode, null, null);
    }

    /**
     * Clicks a JavaFX node using the non-interfering synthetic mode by default.
     *
     * @param pid process ID of the target JavaFX application
     * @param nodeId node ID in the current target JVM session
     * @return agent response describing the delivered click mode
     */
    public Map<String, Object> clickNode(int pid, int nodeId) {
        return clickNode(pid, nodeId, null);
    }

    /**
     * Activates a {@code ButtonBase} through its semantic action without emitting mouse events.
     *
     * @param pid process ID of the target JavaFX application
     * @param nodeId button node ID in the current target JVM session
     * @return agent response describing whether activation succeeded
     */
    @Tool(description = "Activate a ButtonBase through fire() without mouse events. handlerPending=true means a dispatched action is waiting in a modal handler: inspect its dialog and verify the eventual result, without repeating the action.")
    public Map<String, Object> activateNode(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "ButtonBase node ID") int nodeId) {

        return sendAgentCommand(pid, new AgentCommand(
                AgentCommand.CommandType.ACTIVATE_NODE,
                Map.of("nodeId", nodeId)
        ));
    }

    @Tool(description = "Request keyboard focus for a JavaFX node by nodeId.")
    public Map<String, Object> requestFocus(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Node ID") int nodeId) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.REQUEST_FOCUS, params));
    }

    /**
     * Submits a synthetic JavaFX key gesture with optional modifiers.
     *
     * @param pid target JVM process ID
     * @param key key code name or exact single character
     * @param nodeId target node, or null for the focused scene
     * @param modifiers modifier names, or null for none
     * @param mode synthetic only; null selects the default
     * @return agent input submission result, not a guarantee that a shortcut completed
     */
    @Tool(description = "Send a synthetic JavaFX key gesture to a node or the focused scene. Supports SHIFT, CTRL/CONTROL, ALT, CMD/META modifiers and exact single characters. Only synthetic input is supported; no OS input permissions are required. Application event handlers and scene accelerators may handle shortcuts; native OS shortcuts are not sent. Termination may close the connection before a response.")
    public Map<String, Object> typeKey(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Key text or key code name (e.g. 'a', 'ENTER')") String key,
            @ToolParam(description = "Target node ID (optional, defaults to focused scene)", required = false) Integer nodeId,
            @ToolParam(description = "Modifier names: SHIFT, CTRL/CONTROL, ALT, CMD/META (e.g. ['META', 'SHIFT'])", required = false) List<String> modifiers,
            @ToolParam(description = "Input mode: synthetic only (default)", required = false) String mode) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", key);
        if (nodeId != null) params.put("nodeId", nodeId);
        if (modifiers != null) params.put("modifiers", modifiers);
        if (mode != null) params.put("mode", mode);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.TYPE_KEY, params));
    }

    /**
     * Sends a synthetic key gesture without modifiers.
     *
     * @param pid target JVM process ID
     * @param key key code name or single character
     * @param nodeId target node, or null for the focused scene
     * @return agent input submission result
     */
    public Map<String, Object> typeKey(int pid, String key, Integer nodeId) {
        return typeKey(pid, key, nodeId, null, null);
    }

    @Tool(description = "Save a node or single window scene as PNG, including a selected popup scene. Preserves original dimensions by default; optional maxWidth/maxHeight shrink while preserving aspect ratio, with zero unlimited. Returns sourceWidth/sourceHeight and scaled. Rejects source images over 8192 pixels per axis or 16777216 pixels total before allocation.")
    public Map<String, Object> takeScreenshot(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Target node ID (optional; if omitted, captures full scene graph)", required = false) Integer nodeId,
            @ToolParam(description = "Window ID from the stageId field for scene capture (optional; defaults to the first Stage)", required = false) String stageId,
            @ToolParam(description = "Path to save the PNG screenshot") String savePath,
            @ToolParam(description = "Resize limit for screenshot width; omitted or 0 preserves source resolution. Sources are limited to 8192 per dimension and 16777216 pixels total", required = false) Integer maxWidth,
            @ToolParam(description = "Resize limit for screenshot height; omitted or 0 preserves source resolution", required = false) Integer maxHeight) {

        Map<String, Object> params = new LinkedHashMap<>();
        if (nodeId != null) params.put("nodeId", nodeId);
        if (stageId != null) params.put("stageId", stageId);
        params.put("savePath", savePath);
        if (maxWidth != null) params.put("maxWidth", maxWidth);
        if (maxHeight != null) params.put("maxHeight", maxHeight);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.TAKE_SCREENSHOT, params));
    }

    /**
     * Captures a silent MP4 clip from a JavaFX node or window scene.
     *
     * <p>The injected agent records synchronously for at most 30 seconds. When both target IDs are
     * supplied, {@code nodeId} takes precedence. Omitted timing and size limits are selected by the
     * injected agent.
     *
     * @param pid target JavaFX process ID
     * @param nodeId target node ID, or {@code null} to capture a window scene
     * @param stageId target window ID from the {@code stageId} field, or {@code null} to use the
     *                first available Stage
     * @param savePath destination path for the MP4 file
     * @param durationSeconds clip duration from 1 through 30 seconds, or {@code null} for the default
     * @param framesPerSecond frame rate from 1 through 30, or {@code null} for the default
     * @param maxWidth maximum frame width, or {@code null} for the default
     * @param maxHeight maximum frame height, or {@code null} for the default
     * @return command result containing the saved path and encoded video metadata
     */
    @Tool(description = "Capture a silent MP4/H.264 video clip of a specific JavaFX node or one window scene, including an individually selected popup scene. Duration is limited to 30 seconds.")
    public Map<String, Object> captureVideo(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Target node ID (optional; takes precedence over stageId)", required = false) Integer nodeId,
            @ToolParam(description = "Window ID from the stageId field for scene capture (optional; defaults to the first Stage)", required = false) String stageId,
            @ToolParam(description = "Path to save the MP4 video clip") String savePath,
            @ToolParam(description = "Clip duration in seconds, from 1 through 30 (default: 5)", required = false) Integer durationSeconds,
            @ToolParam(description = "Frames per second, from 1 through 30 (default: 10)", required = false) Integer framesPerSecond,
            @ToolParam(description = "Maximum video width (default: 1280)", required = false) Integer maxWidth,
            @ToolParam(description = "Maximum video height (default: 720)", required = false) Integer maxHeight) {

        var params = new LinkedHashMap<String, Object>();
        if (nodeId != null) params.put("nodeId", nodeId);
        if (stageId != null) params.put("stageId", stageId);
        params.put("savePath", savePath);
        if (durationSeconds != null) params.put("durationSeconds", durationSeconds);
        if (framesPerSecond != null) params.put("framesPerSecond", framesPerSecond);
        if (maxWidth != null) params.put("maxWidth", maxWidth);
        if (maxHeight != null) params.put("maxHeight", maxHeight);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.CAPTURE_VIDEO, params));
    }

    // ===================================================
    // Internal Helper
    // ===================================================

    /**
     * Open a transient connection, send one command, and return the response as a Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sendAgentCommand(int pid, AgentCommand command) {
        var result = new LinkedHashMap<String, Object>();
        if (pid <= 0) {
            result.put("success", false);
            result.put("error", "PID must be a positive integer: " + pid);
            return result;
        }

        var agent = agentFactory.apply(pid);
        try {
            agent.connect();
            var response = agent.sendCommand(command);

            result.put("success", response.isSuccess());
            if (response.isSuccess()) {
                if (response.getData() instanceof Map) {
                    result.putAll((Map<String, Object>) response.getData());
                } else {
                    result.put("data", response.getData());
                }
            } else {
                result.put("error", response.getError());
            }
        } catch (Exception e) {
            putConnectionFailure(
                    result,
                    pid,
                    e,
                    "Communication error with PID " + pid + ": " + e.getMessage());
        } finally {
            agent.disconnectWithoutShutdown();
        }
        return result;
    }

    private static void putConnectionFailure(
            Map<String, Object> result,
            int pid,
            Exception failure,
            String fallbackMessage) {
        result.put("success", false);

        var details = findCauseMessage(failure, MISSING_JAVA_INSTRUMENT_MESSAGE);
        if (details == null) {
            result.put("error", fallbackMessage);
            return;
        }

        result.put("errorCode", MISSING_JAVA_INSTRUMENT_ERROR_CODE);
        result.put(
                "error",
                "Cannot connect to PID " + pid + " because the target Java runtime "
                        + "does not include the java.instrument module.");
        result.put("action", MISSING_JAVA_INSTRUMENT_ACTION);
        result.put("details", details);
    }

    private static String findCauseMessage(Throwable failure, String expectedText) {
        for (var current = failure; current != null; current = current.getCause()) {
            var message = current.getMessage();
            if (message != null && message.contains(expectedText)) {
                return message;
            }
        }
        return null;
    }
}
