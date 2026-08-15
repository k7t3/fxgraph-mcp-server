package io.github.k7t3.fxgraph.mcp.agent.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Java agent injected into the target JavaFX JVM via Attach API.
 * Opens a server socket and listens for commands from the MCP server.
 */
public class FxGraphInspectorAgent {

    private static volatile ServerSocket serverSocket;
    private static volatile boolean running = false;
    private static volatile int assignedPort = -1;
    private static Thread serverThread;

    /**
     * Entry point when loaded via Attach API (agentmain).
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        startServer(agentArgs);
    }

    /**
     * Entry point when loaded via -javaagent (premain).
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        startServer(agentArgs);
    }

    private static void startServer(String agentArgs) {
        if (running) {
            // Agent already running, just report the port
            System.setProperty("fxgraph.agent.port", String.valueOf(assignedPort));
            return;
        }

        try {
            // Use port 0 for OS to assign a free port
            serverSocket = new ServerSocket(0);
            assignedPort = serverSocket.getLocalPort();

            // Communicate the port back via system property
            System.setProperty("fxgraph.agent.port", String.valueOf(assignedPort));

            running = true;
            var listeningSocket = serverSocket;
            serverThread = new Thread(() -> runServer(listeningSocket), "fxgraph-inspector-agent");
            serverThread.setDaemon(true);
            serverThread.start();

        } catch (IOException e) {
            System.err.println("[FxGraphInspectorAgent] Failed to start server: " + e.getMessage());
        }
    }

    private static void runServer(ServerSocket listeningSocket) {
        var mapper = new ObjectMapper();
        var inspector = new SceneGraphInspector();

        while (running && serverSocket == listeningSocket) {
            try (var client = listeningSocket.accept()) {
                handleClient(client, mapper, inspector);
            } catch (IOException e) {
                if (running && serverSocket == listeningSocket) {
                    System.err.println("[FxGraphInspectorAgent] Connection error: " + e.getMessage());
                }
            }
        }
    }

    private static void handleClient(Socket client, ObjectMapper mapper, SceneGraphInspector inspector)
            throws IOException {
        // No socket timeout - the MCP server may not send commands for long periods.
        // The connection stays open until explicitly closed or SHUTDOWN is sent.
        client.setKeepAlive(true);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = client.getOutputStream();

        // Protocol: read one line of JSON command, write one line of JSON response
        String line;
        while ((line = reader.readLine()) != null) {
            AgentResponse response;
            try {
                AgentCommand command = mapper.readValue(line, AgentCommand.class);
                response = processCommand(command, inspector);
            } catch (Exception e) {
                response = AgentResponse.error("Failed to process command: " + e.getMessage());
            }

            if (response == null) {
                // SHUTDOWN command
                break;
            }

            String json = mapper.writeValueAsString(response);
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
        }
    }

    private static AgentResponse processCommand(AgentCommand command, SceneGraphInspector inspector) {
        if (command == null || command.getCommand() == null) {
            return AgentResponse.error("Invalid command");
        }

        return switch (command.getCommand()) {
            case PING -> AgentResponse.success("pong");
            case GET_STAGES -> inspector.getStages();
            case GET_SCENEGRAPH -> inspector.getScenegraph(command.getParams());
            case GET_NODE_DETAILS -> inspector.getNodeDetails(command.getParams());
            case FIND_NODES -> inspector.findNodes(command.getParams());
            case SET_PROPERTY -> inspector.setProperty(command.getParams());
            case SELECT_NODE -> inspector.selectNode(command.getParams());
            case CLICK_NODE -> inspector.clickNode(command.getParams());
            case ACTIVATE_NODE -> inspector.activateNode(command.getParams());
            case REQUEST_FOCUS -> inspector.requestFocus(command.getParams());
            case TYPE_KEY -> inspector.typeKey(command.getParams());
            case TAKE_SCREENSHOT -> inspector.takeScreenshot(command.getParams());
            case CAPTURE_VIDEO -> inspector.captureVideo(command.getParams());
            case SHUTDOWN -> {
                shutdown();
                yield null;
            }
        };
    }

    public static void shutdown() {
        running = false;
        System.clearProperty("fxgraph.agent.port");
        assignedPort = -1;
        var listeningSocket = serverSocket;
        serverSocket = null;
        try {
            if (listeningSocket != null) {
                listeningSocket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    public static int getPort() {
        return assignedPort;
    }
}
