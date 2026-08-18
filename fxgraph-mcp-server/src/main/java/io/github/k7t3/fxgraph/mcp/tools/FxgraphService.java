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

    @Tool(description = "Get detailed information about a specific node including all its properties, children summary, bounds, style classes, and more. Use the nodeId obtained from getScenegraph. Optionally filter properties with propertyFilter.")
    public Map<String, Object> getNodeDetails(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Node ID (identityHashCode of the JavaFX Node)") int nodeId,
            @ToolParam(description = "List of property names to include (e.g., ['text', 'value']). Omit to get all properties.", required = false) List<String> propertyFilter) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        if (propertyFilter != null) params.put("propertyFilter", propertyFilter);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.GET_NODE_DETAILS, params));
    }

    @Tool(description = "Search showing JavaFX Stage and PopupWindow scene graphs by type, CSS id, text content, or style class. Returns matching nodes with their nodeId, type, id, and text. Use stageId to limit search to a specific window.")
    public Map<String, Object> findNodes(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "JavaFX class name to filter (e.g., 'Button', 'TextField'). Omit to match all types.", required = false) String type,
            @ToolParam(description = "CSS id (fx:id) to match exactly. Omit to match all ids.", required = false) String id,
            @ToolParam(description = "Text content to search for (case-sensitive contains match). Omit to match all text.", required = false) String text,
            @ToolParam(description = "Style class name to filter. Omit to match all style classes.", required = false) String styleClass,
            @ToolParam(description = "Window ID from the stageId field. Omit to search all showing Stage and PopupWindow scenes.", required = false) String stageId) {

        Map<String, Object> params = new LinkedHashMap<>();
        if (type != null) params.put("type", type);
        if (id != null) params.put("id", id);
        if (text != null) params.put("text", text);
        if (styleClass != null) params.put("styleClass", styleClass);
        if (stageId != null) params.put("stageId", stageId);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.FIND_NODES, params));
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

    @Tool(description = "Click a JavaFX node by nodeId. Uses a synthetic press/release/click gesture by default without moving the system pointer or changing window focus. Robot input is available explicitly.")
    public Map<String, Object> clickNode(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Node ID") int nodeId,
            @ToolParam(description = "Click mode: synthetic (default) or robot", required = false)
                    String mode) {

        var params = new LinkedHashMap<String, Object>();
        params.put("nodeId", nodeId);
        if (mode != null) {
            params.put("mode", mode);
        }

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.CLICK_NODE, params));
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
    @Tool(description = "Activate a JavaFX ButtonBase by nodeId through its semantic fire action without emitting mouse events.")
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

    @Tool(description = "Type a key into a JavaFX node using JavaFX Event System. If nodeId is omitted, the currently focused node is used.")
    public Map<String, Object> typeKey(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Key text or key code name (e.g. 'a', 'ENTER')") String key,
            @ToolParam(description = "Target node ID (optional, defaults to focused node)", required = false) Integer nodeId) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", key);
        if (nodeId != null) params.put("nodeId", nodeId);

        return sendAgentCommand(pid,
                new AgentCommand(AgentCommand.CommandType.TYPE_KEY, params));
    }

    @Tool(description = "Take a screenshot of a specific node or one JavaFX window scene, including an individually selected popup scene. Saves PNG to the specified path.")
    public Map<String, Object> takeScreenshot(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid,
            @ToolParam(description = "Target node ID (optional; if omitted, captures full scene graph)", required = false) Integer nodeId,
            @ToolParam(description = "Window ID from the stageId field for scene capture (optional; defaults to the first Stage)", required = false) String stageId,
            @ToolParam(description = "Path to save the PNG screenshot") String savePath,
            @ToolParam(description = "Maximum width for the screenshot (default: 1280)", required = false) Integer maxWidth,
            @ToolParam(description = "Maximum height for the screenshot (default: 720)", required = false) Integer maxHeight) {

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
