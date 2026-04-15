package io.github.k7t3.fxgraph.mcp.tools;

import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.agent.SessionManager;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import io.github.k7t3.fxgraph.mcp.model.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * MCP tool definitions for inspecting JavaFX application scene graphs.
 * Each tool communicates with an injected agent running inside the target JVM.
 */
@Service
public class FxgraphService {

    private final SessionManager sessionManager;

    public FxgraphService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
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

    @Tool(description = "Connect to a JavaFX application by PID. This injects an inspection agent into the target JVM and establishes a communication channel. If the application is already connected, the existing session is returned. Returns a sessionId to use with other tools.")
    public Map<String, Object> connectApplication(
            @ToolParam(description = "Process ID of the target JavaFX application") int pid) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // Return existing session if the PID is already connected
            String existingSessionId = sessionManager.findSessionIdByPid(String.valueOf(pid));
            if (existingSessionId != null) {
                JavaFxAgent existingAgent = sessionManager.get(existingSessionId);
                result.put("success", true);
                result.put("sessionId", existingSessionId);
                result.put("agentPort", existingAgent.getAgentPort());
                return result;
            }

            JavaFxAgent agent = new JavaFxAgent(String.valueOf(pid));
            agent.connect();

            String sessionId = UUID.randomUUID().toString();
            sessionManager.register(sessionId, agent);

            result.put("success", true);
            result.put("sessionId", sessionId);
            result.put("agentPort", agent.getAgentPort());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Tool(description = "Disconnect from a connected JavaFX application and clean up resources.")
    public Map<String, Object> disconnectApplication(
            @ToolParam(description = "Session ID obtained from connectApplication") String sessionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (!sessionManager.isActive(sessionId)) {
                result.put("success", false);
                result.put("error", "Session not found or already disconnected: " + sessionId);
                return result;
            }
            sessionManager.remove(sessionId);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ===================================================
    // Scene Graph Inspection
    // ===================================================

    @Tool(description = "Get the list of JavaFX Stages (windows) in the connected application. Each stage has a stageId, title, dimensions, and a rootNodeId pointing to the root of its scene graph.")
    public Map<String, Object> getStages(
            @ToolParam(description = "Session ID") String sessionId) {
        return sendAgentCommand(sessionId, new AgentCommand(AgentCommand.CommandType.GET_STAGES));
    }

    @Tool(description = "Get the scene graph tree structure from a connected JavaFX application. Returns a compact hierarchical tree by default. Use depth to limit tree depth, includeBounds to include node bounding boxes, includeProperties to get property details, propertyFilter to limit which properties, and includeTransforms for transform details.")
    public Map<String, Object> getScenegraph(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Stage ID to inspect (omit to get all stages)", required = false) String stageId,
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

        return sendAgentCommand(sessionId,
                new AgentCommand(AgentCommand.CommandType.GET_SCENEGRAPH, params));
    }

    @Tool(description = "Search for nodes in the scene graph by criteria. Returns matching nodes with their IDs without requiring full scene graph traversal. Search by CSS id, node type, text content, or style class. Multiple criteria are combined with AND logic.")
    public Map<String, Object> findNodes(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "CSS fx:id of the node (exact match)", required = false) String id,
            @ToolParam(description = "Node type/class name to match (e.g. 'Button', 'TextField', 'Label'). Matches against the class hierarchy.", required = false) String type,
            @ToolParam(description = "Text content to search for (partial match on text/label properties)", required = false) String text,
            @ToolParam(description = "CSS style class to match (exact match on a single class)", required = false) String styleClass,
            @ToolParam(description = "Stage ID to restrict search to a specific window (optional)", required = false) String stageId,
            @ToolParam(description = "Maximum number of results to return (default: 100)", required = false) Integer maxResults) {

        Map<String, Object> params = new LinkedHashMap<>();
        if (id != null) params.put("id", id);
        if (type != null) params.put("type", type);
        if (text != null) params.put("text", text);
        if (styleClass != null) params.put("styleClass", styleClass);
        if (stageId != null) params.put("stageId", stageId);
        if (maxResults != null) params.put("maxResults", maxResults);

        return sendAgentCommand(sessionId,
                new AgentCommand(AgentCommand.CommandType.FIND_NODES, params));
    }

    @Tool(description = "Get detailed information about a specific node including all its properties, children summary, bounds, style classes, and more. Use the nodeId obtained from getScenegraph. Optionally filter properties with propertyFilter.")
    public Map<String, Object> getNodeDetails(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Node ID (identityHashCode of the JavaFX Node)") int nodeId,
            @ToolParam(description = "List of property names to include (e.g., ['text', 'value']). Omit to get all properties.", required = false) List<String> propertyFilter) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        if (propertyFilter != null) params.put("propertyFilter", propertyFilter);

        return sendAgentCommand(sessionId,
                new AgentCommand(AgentCommand.CommandType.GET_NODE_DETAILS, params));
    }

    // ===================================================
    // Node Manipulation
    // ===================================================

    @Tool(description = "Set a property value on a JavaFX node. Supports setting text, numbers, booleans, colors, and style strings. Returns the old and new values.")
    public Map<String, Object> setProperty(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Node ID") int nodeId,
            @ToolParam(description = "Property name (e.g. 'text', 'style', 'visible', 'opacity')") String propertyName,
            @ToolParam(description = "New value as string") String value,
            @ToolParam(description = "Value type hint: string, number, boolean, color (optional)", required = false) String valueType) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        params.put("propertyName", propertyName);
        params.put("value", value);
        if (valueType != null) params.put("valueType", valueType);

        return sendAgentCommand(sessionId,
                new AgentCommand(AgentCommand.CommandType.SET_PROPERTY, params));
    }

    @Tool(description = "Highlight/select a node in the target JavaFX application by drawing a visual overlay (red border). Pass nodeId=0 to clear the highlight.")
    public Map<String, Object> selectNode(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Node ID (use 0 to clear selection)") int nodeId,
            @ToolParam(description = "Show bounds rectangle overlay (default: true)", required = false) Boolean showBounds) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        params.put("showBounds", showBounds != null ? showBounds : true);

        return sendAgentCommand(sessionId,
                new AgentCommand(AgentCommand.CommandType.SELECT_NODE, params));
    }

    @Tool(description = "Click a JavaFX node by nodeId using JavaFX Event System for simulated input.")
    public Map<String, Object> clickNode(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Node ID") int nodeId) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);

        return sendAgentCommand(sessionId,
                new AgentCommand(AgentCommand.CommandType.CLICK_NODE, params));
    }

    @Tool(description = "Request keyboard focus for a JavaFX node by nodeId.")
    public Map<String, Object> requestFocus(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Node ID") int nodeId) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);

        return sendAgentCommand(sessionId,
                new AgentCommand(AgentCommand.CommandType.REQUEST_FOCUS, params));
    }

    @Tool(description = "Type a key into a JavaFX node using JavaFX Event System. If nodeId is omitted, the currently focused node is used.")
    public Map<String, Object> typeKey(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Key text or key code name (e.g. 'a', 'ENTER')") String key,
            @ToolParam(description = "Target node ID (optional, defaults to focused node)", required = false) Integer nodeId) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", key);
        if (nodeId != null) params.put("nodeId", nodeId);

        return sendAgentCommand(sessionId,
                new AgentCommand(AgentCommand.CommandType.TYPE_KEY, params));
    }

    @Tool(description = "Take a screenshot of a specific node or the whole scene graph. Saves PNG to the specified path.")
    public Map<String, Object> takeScreenshot(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Target node ID (optional; if omitted, captures full scene graph)", required = false) Integer nodeId,
            @ToolParam(description = "Stage ID for full scene graph capture (optional)", required = false) String stageId,
            @ToolParam(description = "Path to save the PNG screenshot") String savePath) {

        Map<String, Object> params = new LinkedHashMap<>();
        if (nodeId != null) params.put("nodeId", nodeId);
        if (stageId != null) params.put("stageId", stageId);
        params.put("savePath", savePath);

        return sendAgentCommand(sessionId,
                new AgentCommand(AgentCommand.CommandType.TAKE_SCREENSHOT, params));
    }

    // ===================================================
    // Internal Helper
    // ===================================================

    /**
     * Send a command to the agent via the session and return the response as a Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sendAgentCommand(String sessionId, AgentCommand command) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            JavaFxAgent agent = sessionManager.get(sessionId);
            if (agent == null || !agent.isConnected()) {
                result.put("success", false);
                result.put("error", "Session not found or disconnected: " + sessionId);
                return result;
            }

            AgentResponse response = agent.sendCommand(command);

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
            result.put("success", false);
            result.put("error", "Communication error: " + e.getMessage());
        }
        return result;
    }
}
