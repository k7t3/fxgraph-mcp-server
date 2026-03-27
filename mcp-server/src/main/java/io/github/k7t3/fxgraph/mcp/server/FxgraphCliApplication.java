package io.github.k7t3.fxgraph.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.agent.SessionManager;
import io.github.k7t3.fxgraph.mcp.tools.FxgraphService;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides a stateless CLI facade over the FXGraph inspection features.
 */
public final class FxgraphCliApplication {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FxgraphCliApplication() {
    }

    /**
     * Runs the CLI and writes JSON output to the process streams.
     *
     * @param args command-line arguments without the leading {@code cli} token
     * @return process exit code
     */
    public static int run(String[] args) {
        return run(args, System.out, System.err);
    }

    /**
     * Runs the CLI using explicit output streams.
     *
     * @param args command-line arguments without the leading {@code cli} token
     * @param out standard output stream
     * @param err standard error stream
     * @return process exit code
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || isHelpCommand(args[0])) {
            writeUsage(out);
            return 0;
        }

        try {
            ParsedCommand parsed = parse(args);
            Map<String, Object> result = execute(parsed);
            writeJson(out, result);
            return isSuccess(result) ? 0 : 1;
        } catch (Exception e) {
            Map<String, Object> error = errorResult(e.getMessage());
            writeJson(err, error);
            return 1;
        }
    }

    private static boolean isHelpCommand(String arg) {
        return "help".equalsIgnoreCase(arg) || "--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg);
    }

    private static ParsedCommand parse(String[] args) {
        String commandName = args[0];
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + token);
            }
            String optionName = token.substring(2);
            if (optionName.isBlank()) {
                throw new IllegalArgumentException("Option name must not be blank");
            }
            String value = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            }
            options.computeIfAbsent(optionName, ignored -> new ArrayList<>()).add(value);
        }
        return new ParsedCommand(commandName, options);
    }

    private static Map<String, Object> execute(ParsedCommand parsed) throws Exception {
        SessionManager sessionManager = new SessionManager();
        FxgraphService service = new FxgraphService(sessionManager);
        return switch (parsed.commandName()) {
            case "discoverApplications" -> service.discoverApplications();
            case "connectApplication" -> connectApplication(parsed, sessionManager, service);
            case "disconnectApplication" -> disconnectApplication(parsed);
            case "getStages" -> withPidSession(parsed, sessionManager, service, service::getStages);
            case "getScenegraph" -> withPidSession(parsed, sessionManager, service,
                    sessionId -> service.getScenegraph(
                            sessionId,
                            parsed.optionalValue("stageId"),
                            parsed.optionalInteger("depth"),
                            parsed.optionalBoolean("includeBounds"),
                            parsed.optionalBoolean("includeProperties"),
                            parsed.optionalList("propertyFilter"),
                            parsed.optionalBoolean("includeTransforms")));
            case "getNodeDetails" -> withPidSession(parsed, sessionManager, service,
                    sessionId -> service.getNodeDetails(
                            sessionId,
                            parsed.requiredInteger("nodeId"),
                            parsed.optionalList("propertyFilter")));
            case "setProperty" -> withPidSession(parsed, sessionManager, service,
                    sessionId -> service.setProperty(
                            sessionId,
                            parsed.requiredInteger("nodeId"),
                            parsed.requiredValue("propertyName"),
                            parsed.requiredValue("value"),
                            parsed.optionalValue("valueType")));
            case "selectNode" -> withPidSession(parsed, sessionManager, service,
                    sessionId -> service.selectNode(
                            sessionId,
                            parsed.requiredInteger("nodeId"),
                            parsed.optionalBoolean("showBounds")));
            case "clickNode" -> withPidSession(parsed, sessionManager, service,
                    sessionId -> service.clickNode(sessionId, parsed.requiredInteger("nodeId")));
            case "requestFocus" -> withPidSession(parsed, sessionManager, service,
                    sessionId -> service.requestFocus(sessionId, parsed.requiredInteger("nodeId")));
            case "typeKey" -> withPidSession(parsed, sessionManager, service,
                    sessionId -> service.typeKey(
                            sessionId,
                            parsed.requiredValue("key"),
                            parsed.optionalInteger("nodeId")));
            case "takeScreenshot" -> withPidSession(parsed, sessionManager, service,
                    sessionId -> service.takeScreenshot(
                            sessionId,
                            parsed.optionalInteger("nodeId"),
                            parsed.optionalValue("stageId"),
                            parsed.requiredValue("savePath")));
            default -> throw new IllegalArgumentException("Unknown CLI command: " + parsed.commandName());
        };
    }

    private static Map<String, Object> connectApplication(
            ParsedCommand parsed,
            SessionManager sessionManager,
            FxgraphService service) throws Exception {
        int pid = parsed.requiredInteger("pid");
        Integer existingAgentPort = JavaFxAgent.findRunningAgentPort(String.valueOf(pid));
        Map<String, Object> result = service.connectApplication(pid);
        Object sessionId = result.get("sessionId");
        if (sessionId instanceof String sessionIdValue) {
            sessionManager.remove(sessionIdValue, false);
            result.remove("sessionId");
        }
        result.put("pid", pid);
        result.put("alreadyRunning", existingAgentPort != null);
        return result;
    }

    private static Map<String, Object> disconnectApplication(ParsedCommand parsed) throws Exception {
        int pid = parsed.requiredInteger("pid");
        Integer existingAgentPort = JavaFxAgent.findRunningAgentPort(String.valueOf(pid));
        if (existingAgentPort == null) {
            return errorResult("FXGraph agent is not running for PID: " + pid);
        }

        JavaFxAgent agent = new JavaFxAgent(String.valueOf(pid));
        try {
            agent.connect();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("pid", pid);
            result.put("agentPort", agent.getAgentPort());
            return result;
        } finally {
            agent.disconnect();
        }
    }

    private static Map<String, Object> withPidSession(
            ParsedCommand parsed,
            SessionManager sessionManager,
            FxgraphService service,
            Function<String, Map<String, Object>> callback) throws Exception {
        int pid = parsed.requiredInteger("pid");
        Integer existingAgentPort = JavaFxAgent.findRunningAgentPort(String.valueOf(pid));
        Map<String, Object> connectResult = service.connectApplication(pid);
        if (!isSuccess(connectResult)) {
            return connectResult;
        }

        String sessionId = (String) connectResult.get("sessionId");
        try {
            return callback.apply(sessionId);
        } finally {
            sessionManager.remove(sessionId, existingAgentPort == null);
        }
    }

    private static boolean isSuccess(Map<String, Object> result) {
        return Boolean.TRUE.equals(result.get("success"));
    }

    private static void writeJson(PrintStream stream, Map<String, Object> value) {
        try {
            stream.println(OBJECT_MAPPER.writeValueAsString(value));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize CLI response", e);
        }
    }

    private static Map<String, Object> errorResult(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("error", message);
        return error;
    }

    private static void writeUsage(PrintStream out) {
        out.println("""
                FXGraph CLI

                Usage:
                  java -jar mcp-server.jar cli <command> [--option value]

                Commands:
                  discoverApplications
                  connectApplication --pid <pid>
                  disconnectApplication --pid <pid>
                  getStages --pid <pid>
                  getScenegraph --pid <pid> [--stageId <id>] [--depth <n>] [--includeBounds [true|false]]
                                [--includeProperties [true|false]] [--propertyFilter text,value]
                                [--includeTransforms [true|false]]
                  getNodeDetails --pid <pid> --nodeId <id> [--propertyFilter text,value]
                  setProperty --pid <pid> --nodeId <id> --propertyName <name> --value <value> [--valueType <type>]
                  selectNode --pid <pid> --nodeId <id> [--showBounds [true|false]]
                  clickNode --pid <pid> --nodeId <id>
                  requestFocus --pid <pid> --nodeId <id>
                  typeKey --pid <pid> --key <text> [--nodeId <id>]
                  takeScreenshot --pid <pid> --savePath <path> [--nodeId <id>] [--stageId <id>]

                Notes:
                  - Commands output compact JSON for easy piping.
                  - App-specific commands connect by PID automatically.
                  - propertyFilter accepts a comma-separated list or repeated --propertyFilter options.
                """);
    }

    private record ParsedCommand(String commandName, Map<String, List<String>> options) {

        private String requiredValue(String name) {
            String value = optionalValue(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required option --" + name);
            }
            return value;
        }

        private String optionalValue(String name) {
            List<String> values = options.get(name);
            return values == null || values.isEmpty() ? null : values.getLast();
        }

        private Integer requiredInteger(String name) {
            return Integer.valueOf(requiredValue(name));
        }

        private Integer optionalInteger(String name) {
            String value = optionalValue(name);
            return value == null ? null : Integer.valueOf(value);
        }

        private Boolean optionalBoolean(String name) {
            String value = optionalValue(name);
            return value == null ? null : Boolean.valueOf(value);
        }

        private List<String> optionalList(String name) {
            List<String> values = options.get(name);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.stream()
                    .flatMap(value -> Arrays.stream(value.split(",")))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }
}
