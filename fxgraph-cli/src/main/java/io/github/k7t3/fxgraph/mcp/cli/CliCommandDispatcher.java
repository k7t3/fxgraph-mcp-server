package io.github.k7t3.fxgraph.mcp.cli;

import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import io.github.k7t3.fxgraph.mcp.model.JavaFxApplication;

import java.util.*;
import java.util.function.IntFunction;

/**
 * Parses CLI arguments and dispatches to the appropriate agent command.
 *
 * <p>Supported forms:
 * <ul>
 *   <li>{@code discover} — lists running JavaFX applications</li>
 *   <li>{@code <pid> <command> [args...]} — connects to the given PID and executes a command</li>
 * </ul>
 *
 * <p>Each invocation is stateless: the CLI connects, runs the command, then closes the
 * socket without shutting down the agent. The agent remains running in the target JVM
 * so subsequent CLI calls can reconnect without re-injecting.
 */
public class CliCommandDispatcher {

    /** Factory for creating {@link JavaFxAgent} instances from a PID. Package-private for testing. */
    @FunctionalInterface
    interface AgentFactory extends IntFunction<JavaFxAgent> {}

    private final AgentFactory agentFactory;

    /** Creates a dispatcher that connects to real JavaFX JVMs. */
    public CliCommandDispatcher() {
        this(pid -> new JavaFxAgent(String.valueOf(pid)));
    }

    /** Package-private constructor for testing with a mock agent factory. */
    CliCommandDispatcher(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    public int dispatch(String[] args) {
        if ("discover".equals(args[0])) {
            return cmdDiscover();
        }

        int pid;
        try {
            pid = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            return CliJsonOutput.failure(
                    "Unknown command '" + args[0] + "'. Use 'discover' or a numeric PID.");
        }

        if (args.length < 2) {
            return CliJsonOutput.failure(
                    "No command specified after PID " + pid
                    + ". Available commands: stages, scenegraph, node-details, find-nodes, set-property,"
                    + " select-node, click-node, right-click-node, double-click-node, activate-node,"
                    + " focus, type-key, window-details, set-window-property, close-window, close-popup,"
                    + " scroll-node, scroll-to-index, screenshot, capture-video");
        }

        return runWithAgent(pid, Arrays.copyOfRange(args, 1, args.length));
    }

    // ===================================================
    // discover
    // ===================================================

    private static int cmdDiscover() {
        try {
            List<JavaFxApplication> apps = JavaFxAgent.discoverApplications();
            return CliJsonOutput.success(apps);
        } catch (Exception e) {
            return CliJsonOutput.failure(e.getMessage());
        }
    }

    // ===================================================
    // Agent-connected commands
    // ===================================================

    private int runWithAgent(int pid, String[] args) {
        JavaFxAgent agent = agentFactory.apply(pid);
        try {
            agent.connect();
            return dispatchAgentCommand(agent, args);
        } catch (Exception e) {
            return CliJsonOutput.failure("Failed to connect to PID " + pid + ": " + e.getMessage());
        } finally {
            // Close socket without sending SHUTDOWN so the agent stays alive for future calls
            agent.disconnectWithoutShutdown();
        }
    }

    private static int dispatchAgentCommand(JavaFxAgent agent, String[] args) {
        String command = args[0];
        try {
            return switch (command) {
                case "stages"       -> cmdStages(agent);
                case "window-details" -> cmdWindow(agent, args, AgentCommand.CommandType.GET_WINDOW_DETAILS);
                case "set-window-property" -> cmdSetWindowProperty(agent, args);
                case "close-window" -> cmdWindow(agent, args, AgentCommand.CommandType.CLOSE_WINDOW);
                case "close-popup" -> cmdWindow(agent, args, AgentCommand.CommandType.CLOSE_POPUP);
                case "scenegraph"   -> cmdScenegraph(agent, args);
                case "node-details" -> cmdNodeDetails(agent, args);
                case "find-nodes"   -> cmdFindNodes(agent, args);
                case "set-property" -> cmdSetProperty(agent, args);
                case "select-node"  -> cmdSelectNode(agent, args);
                case "click-node", "right-click-node", "double-click-node" -> cmdClickNode(agent, args);
                case "activate-node" -> cmdActivateNode(agent, args);
                case "focus"        -> cmdFocus(agent, args);
                case "type-key"     -> cmdTypeKey(agent, args);
                case "scroll-node", "scroll-to-index" -> cmdScroll(agent, args);
                case "screenshot"   -> cmdScreenshot(agent, args);
                case "capture-video" -> cmdCaptureVideo(agent, args);
                default             -> CliJsonOutput.failure("Unknown command: " + command);
            };
        } catch (IllegalArgumentException e) {
            return CliJsonOutput.failure("Command failed: " + e.getMessage()
                    + "\nUsage: fxgraph <pid> " + command + " [options]");
        } catch (Exception e) {
            return CliJsonOutput.failure("Command failed: " + e.getMessage());
        }
    }

    // ===================================================
    // Individual command implementations
    // ===================================================

    private static int cmdStages(JavaFxAgent agent) throws Exception {
        AgentResponse resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.GET_STAGES));
        return outputResponse(resp);
    }

    private static int cmdWindow(JavaFxAgent agent, String[] args, AgentCommand.CommandType type) throws Exception {
        var params = new LinkedHashMap<String, Object>();
        var start = 1;
        if (args.length > 1 && !args[1].startsWith("--")) {
            if (args[1].isBlank()) throw new IllegalArgumentException("stageId requires a non-empty value");
            params.put("stageId", args[1]);
            start = 2;
        }
        for (var i = start; i < args.length; i++) {
            if ("--stageId".equals(args[i])) params.put("stageId", requireNext(args, ++i, "--stageId"));
            else throw new IllegalArgumentException(unknownOptionMessage(args[i]));
        }
        if (!params.containsKey("stageId")) throw new IllegalArgumentException(args[0] + " requires <stageId> or --stageId <id>");
        return outputResponse(agent.sendCommand(new AgentCommand(type, params)));
    }

    private static int cmdSetWindowProperty(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 4) throw new IllegalArgumentException("set-window-property requires <stageId> <property> <value> [--type TYPE]");
        if (args[1].isBlank()) throw new IllegalArgumentException("stageId requires a non-empty value");
        var params = new LinkedHashMap<String, Object>();
        params.put("stageId", args[1]);
        params.put("propertyName", args[2]);
        params.put("value", args[3]);
        for (var i = 4; i < args.length; i++) {
            if ("--type".equals(args[i])) params.put("valueType", requireNext(args, ++i, "--type"));
            else throw new IllegalArgumentException(unknownOptionMessage(args[i]));
        }
        return outputResponse(agent.sendCommand(new AgentCommand(AgentCommand.CommandType.SET_WINDOW_PROPERTY, params)));
    }

    private static int cmdScenegraph(JavaFxAgent agent, String[] args) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--stageId"    -> params.put("stageId", requireNext(args, ++i, "--stageId"));
                case "--depth"      -> params.put("depth", Integer.parseInt(requireNext(args, ++i, "--depth")));
                case "--bounds"     -> params.put("includeBounds", true);
                case "--props"      -> params.put("includeProperties", true);
                case "--transforms" -> params.put("includeTransforms", true);
                case "--filter"     -> params.put("propertyFilter",
                        Arrays.asList(requireNext(args, ++i, "--filter").split(",")));
                default             -> throw new IllegalArgumentException(unknownOptionMessage(args[i]));
            }
        }
        AgentResponse resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.GET_SCENEGRAPH,
                        params.isEmpty() ? null : params));
        return outputResponse(resp);
    }

    private static int cmdNodeDetails(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 2) {
            return CliJsonOutput.failure("node-details requires a <nodeId> argument");
        }
        int nodeId = parseNodeId(args[1]);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        for (int i = 2; i < args.length; i++) {
            if ("--filter".equals(args[i])) {
                params.put("propertyFilter",
                        Arrays.asList(requireNext(args, ++i, "--filter").split(",")));
            } else if ("--ancestors".equals(args[i])) {
                params.put("includeAncestors", true);
            } else if ("--props".equals(args[i])) {
                throw new IllegalArgumentException(
                        "--props is not valid for node-details. " +
                        "Use --filter <prop1,prop2> to select specific properties instead. " +
                        "(--props is only available for the scenegraph command.)");
            } else {
                throw new IllegalArgumentException(unknownOptionMessage(args[i]));
            }
        }
        AgentResponse resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.GET_NODE_DETAILS, params));
        return outputResponse(resp);
    }

    private static int cmdFindNodes(JavaFxAgent agent, String[] args) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--type"      -> params.put("type", requireNext(args, ++i, "--type"));
                case "--id"        -> params.put("id", requireNext(args, ++i, "--id"));
                case "--text"      -> params.put("text", requireNext(args, ++i, "--text"));
                case "--styleClass"-> params.put("styleClass", requireNext(args, ++i, "--styleClass"));
                case "--stageId"   -> params.put("stageId", requireNext(args, ++i, "--stageId"));
                case "--visible-only" -> params.put("effectiveVisible", true);
                default            -> throw new IllegalArgumentException(unknownOptionMessage(args[i]));
            }
        }
        AgentResponse resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.FIND_NODES,
                        params.isEmpty() ? null : params));
        return outputResponse(resp);
    }

    private static int cmdSetProperty(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 4) {
            return CliJsonOutput.failure(
                    "set-property requires: <nodeId> <property> <value> [--type TYPE]");
        }
        int nodeId = parseNodeId(args[1]);
        String propertyName = args[2];
        String value = args[3];

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        params.put("propertyName", propertyName);
        params.put("value", value);
        for (int i = 4; i < args.length; i++) {
            if ("--type".equals(args[i])) {
                params.put("valueType", requireNext(args, ++i, "--type"));
            } else {
                throw new IllegalArgumentException(unknownOptionMessage(args[i]));
            }
        }
        AgentResponse resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.SET_PROPERTY, params));
        return outputResponse(resp);
    }

    private static int cmdSelectNode(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 2) {
            return CliJsonOutput.failure("select-node requires a <nodeId> argument (use 0 to clear)");
        }
        int nodeId = parseNodeId(args[1]);
        boolean showBounds = true;
        for (int i = 2; i < args.length; i++) {
            if ("--no-bounds".equals(args[i])) {
                showBounds = false;
            } else {
                throw new IllegalArgumentException(unknownOptionMessage(args[i]));
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nodeId", nodeId);
        params.put("showBounds", showBounds);
        AgentResponse resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.SELECT_NODE, params));
        return outputResponse(resp);
    }

    private static int cmdClickNode(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 2) {
            return CliJsonOutput.failure(args[0] + " requires a <nodeId> argument");
        }
        var nodeId = parseNodeId(args[1]);
        var params = new LinkedHashMap<String, Object>();
        params.put("nodeId", nodeId);
        if ("right-click-node".equals(args[0])) params.put("button", "secondary");
        if ("double-click-node".equals(args[0])) params.put("clickCount", 2);
        for (var i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--mode" -> params.put("mode", requireNext(args, ++i, "--mode"));
                case "--button" -> params.put("button", requireNext(args, ++i, "--button"));
                case "--clickCount" -> {
                    var value = requireNext(args, ++i, "--clickCount");
                    try {
                        params.put("clickCount", Integer.parseInt(value));
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("--clickCount requires an integer of 1 or 2");
                    }
                }
                default -> throw new IllegalArgumentException(unknownOptionMessage(args[i]));
            }
        }
        var resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.CLICK_NODE, params));
        return outputResponse(resp);
    }

    private static int cmdActivateNode(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 2) {
            return CliJsonOutput.failure("activate-node requires a <nodeId> argument");
        }
        var params = Map.<String, Object>of("nodeId", parseNodeId(args[1]));
        var response = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.ACTIVATE_NODE, params));
        return outputResponse(response);
    }

    private static int cmdFocus(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 2) {
            return CliJsonOutput.failure("focus requires a <nodeId> argument");
        }
        int nodeId = parseNodeId(args[1]);
        Map<String, Object> params = Map.of("nodeId", nodeId);
        AgentResponse resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.REQUEST_FOCUS, params));
        return outputResponse(resp);
    }

    private static int cmdTypeKey(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 2) {
            return CliJsonOutput.failure("type-key requires a <key> argument");
        }
        String key = args[1];
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", key);
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--nodeId" -> params.put("nodeId", Integer.parseInt(requireNext(args, ++i, "--nodeId")));
                case "--modifiers" -> params.put("modifiers", Arrays.asList(requireNext(args, ++i, "--modifiers").split(",", -1)));
                case "--mode" -> params.put("mode", requireNext(args, ++i, "--mode"));
                default -> throw new IllegalArgumentException(unknownOptionMessage(args[i]));
            }
        }
        AgentResponse resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.TYPE_KEY, params));
        return outputResponse(resp);
    }

    private static int cmdScreenshot(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 2) {
            return CliJsonOutput.failure("screenshot requires an <outputPath> argument");
        }
        String savePath = args[1];
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("savePath", savePath);
        var noLimit = false;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--nodeId"   -> params.put("nodeId",
                        Integer.parseInt(requireNext(args, ++i, "--nodeId")));
                case "--stageId"  -> params.put("stageId", requireNext(args, ++i, "--stageId"));
                case "--maxWidth" -> params.put("maxWidth",
                        Integer.parseInt(requireNext(args, ++i, "--maxWidth")));
                case "--maxHeight" -> params.put("maxHeight",
                        Integer.parseInt(requireNext(args, ++i, "--maxHeight")));
                case "--no-limit" -> noLimit = true;
                default           -> throw new IllegalArgumentException(unknownOptionMessage(args[i]));
            }
        }
        if (noLimit) {
            if (params.containsKey("maxWidth") || params.containsKey("maxHeight")) {
                throw new IllegalArgumentException("--no-limit cannot be combined with --maxWidth or --maxHeight");
            }
            params.put("maxWidth", 0);
            params.put("maxHeight", 0);
        }
        AgentResponse resp = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.TAKE_SCREENSHOT, params));
        return outputResponse(resp);
    }

    private static int cmdScroll(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException(args[0] + " requires a <nodeId> argument");
        var params = new LinkedHashMap<String, Object>();
        params.put("nodeId", parseNodeId(args[1]));
        var byIndex = "scroll-to-index".equals(args[0]);
        for (var i = 2; i < args.length; i++) {
            var flag = args[i];
            if (byIndex && flag.equals("--index")) {
                params.put("index", Integer.parseInt(requireNext(args, ++i, flag)));
            } else if (!byIndex && (flag.equals("--dx") || flag.equals("--dy"))) {
                params.put(flag.substring(2), Double.parseDouble(requireNext(args, ++i, flag)));
            } else if (!byIndex && flag.equals("--align")) {
                params.put("align", requireNext(args, ++i, flag));
            } else throw new IllegalArgumentException(unknownOptionMessage(flag));
        }
        if (byIndex && !params.containsKey("index")) throw new IllegalArgumentException("--index is required");
        return outputResponse(agent.sendCommand(new AgentCommand(
                byIndex ? AgentCommand.CommandType.SCROLL_TO_INDEX : AgentCommand.CommandType.SCROLL_NODE, params)));
    }

    private static int cmdCaptureVideo(JavaFxAgent agent, String[] args) throws Exception {
        if (args.length < 2) {
            return CliJsonOutput.failure("capture-video requires an <outputPath> argument");
        }
        var params = new LinkedHashMap<String, Object>();
        params.put("savePath", args[1]);
        for (var i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--nodeId" -> params.put("nodeId",
                        Integer.parseInt(requireNext(args, ++i, "--nodeId")));
                case "--stageId" -> params.put("stageId", requireNext(args, ++i, "--stageId"));
                case "--durationSeconds" -> params.put("durationSeconds",
                        Integer.parseInt(requireNext(args, ++i, "--durationSeconds")));
                case "--framesPerSecond" -> params.put("framesPerSecond",
                        Integer.parseInt(requireNext(args, ++i, "--framesPerSecond")));
                case "--maxWidth" -> params.put("maxWidth",
                        Integer.parseInt(requireNext(args, ++i, "--maxWidth")));
                case "--maxHeight" -> params.put("maxHeight",
                        Integer.parseInt(requireNext(args, ++i, "--maxHeight")));
                default -> throw new IllegalArgumentException(unknownOptionMessage(args[i]));
            }
        }
        var response = agent.sendCommand(
                new AgentCommand(AgentCommand.CommandType.CAPTURE_VIDEO, params));
        return outputResponse(response);
    }

    // ===================================================
    // Helpers
    // ===================================================

    /**
     * Produces a descriptive error message for unknown options,
     * providing actionable hints for common mistakes.
     */
    private static String unknownOptionMessage(String option) {
        return switch (option) {
            case "--json" -> "Unknown option: --json. All commands output JSON by default — no flag needed.";
            case "--props" -> "Unknown option: --props. " +
                    "--props is only valid for the scenegraph command. " +
                    "For node-details, use --filter <prop1,prop2> instead.";
            default -> "Unknown option: " + option;
        };
    }

    private static int outputResponse(AgentResponse resp) {
        if (!resp.isSuccess()) {
            return CliJsonOutput.failure(resp.getError());
        }
        Object data = resp.getData();
        if (data instanceof Map<?, ?> map) {
            return CliJsonOutput.success(map);
        }
        return CliJsonOutput.success(data);
    }

    private static int parseNodeId(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid nodeId '" + s + "': must be an integer");
        }
    }

    private static String requireNext(String[] args, int i, String flag) {
        if (i >= args.length || args[i].isBlank() || args[i].startsWith("--")) {
            throw new IllegalArgumentException(flag + " requires a non-empty value");
        }
        return args[i];
    }
}
