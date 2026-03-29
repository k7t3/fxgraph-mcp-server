package io.github.k7t3.fxgraph.mcp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import io.github.k7t3.fxgraph.mcp.model.JavaFxApplication;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Manages the connection to a target JavaFX JVM.
 * Uses the Attach API to inject the FxGraphInspectorAgent into the target JVM,
 * then communicates with it via a socket-based JSON protocol.
 */
public class JavaFxAgent {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final String pid;
    private VirtualMachine vm;
    private boolean connected = false;
    private int agentPort = -1;
    private Socket socket;
    private BufferedReader reader;
    private OutputStream writer;

    public JavaFxAgent(String pid) {
        this.pid = pid;
    }

    public String getPid() {
        return pid;
    }

    /**
     * Attach to the target JVM and inject the inspector agent if not already running.
     * If the agent is already injected (port property is set), skips injection and
     * connects directly to the existing agent socket.
     */
    public boolean connect() throws Exception {
        try {
            vm = VirtualMachine.attach(pid);

            // Check if agent is already running in the target JVM
            String portStr = vm.getSystemProperties().getProperty("fxgraph.agent.port");
            if (portStr == null) {
                portStr = vm.getAgentProperties().getProperty("fxgraph.agent.port");
            }

            if (portStr == null) {
                // Agent not yet running — inject it
                String agentJarPath = findAgentJar();
                vm.loadAgent(agentJarPath);

                // Wait for agent to start and publish its port
                for (int i = 0; i < 10 && portStr == null; i++) {
                    portStr = vm.getSystemProperties().getProperty("fxgraph.agent.port");
                    if (portStr == null) {
                        portStr = vm.getAgentProperties().getProperty("fxgraph.agent.port");
                    }
                    if (portStr == null) {
                        Thread.sleep(500);
                    }
                }
            }

            if (portStr == null) {
                throw new Exception("Agent started but port not found. The agent may not be loaded correctly.");
            }

            agentPort = Integer.parseInt(portStr);

            // Connect to the agent socket
            socket = new Socket("127.0.0.1", agentPort);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = socket.getOutputStream();

            // Verify connection with a ping
            AgentResponse pingResponse = sendCommand(new AgentCommand(AgentCommand.CommandType.PING));
            if (!pingResponse.isSuccess()) {
                throw new Exception("Agent ping failed");
            }

            connected = true;
            return true;
        } catch (Exception e) {
            disconnect();
            throw new Exception("Failed to connect to JavaFX application (PID: " + pid + "): " + e.getMessage(), e);
        }
    }

    /**
     * Disconnect from the target JVM, sending a SHUTDOWN command to stop the agent.
     * Use this when the session should be fully terminated (e.g., MCP server mode).
     */
    public void disconnect() {
        connected = false;
        agentPort = -1;

        if (socket != null) {
            try {
                sendCommandNoResponse(new AgentCommand(AgentCommand.CommandType.SHUTDOWN));
            } catch (Exception e) {
                // Ignore
            }
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            socket = null;
            reader = null;
            writer = null;
        }

        if (vm != null) {
            try {
                vm.detach();
            } catch (IOException e) {
                // Ignore
            }
            vm = null;
        }
    }

    /**
     * Close the socket connection and detach the VM without shutting down the agent.
     * The agent continues running in the target JVM, ready for future connections.
     * Use this in CLI mode where each invocation connects and disconnects independently.
     */
    public void disconnectWithoutShutdown() {
        connected = false;
        agentPort = -1;

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            socket = null;
            reader = null;
            writer = null;
        }

        if (vm != null) {
            try {
                vm.detach();
            } catch (IOException e) {
                // Ignore
            }
            vm = null;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public int getAgentPort() {
        return agentPort;
    }

    /**
     * Send a command to the agent and return the response.
     */
    public AgentResponse sendCommand(AgentCommand command) throws Exception {
        if (!connected && command.getCommand() != AgentCommand.CommandType.PING) {
            throw new Exception("Not connected");
        }

        String json = mapper.writeValueAsString(command);
        writer.write(json.getBytes(StandardCharsets.UTF_8));
        writer.write('\n');
        writer.flush();

        String responseLine = reader.readLine();
        if (responseLine == null) {
            throw new Exception("Connection closed by agent");
        }

        return mapper.readValue(responseLine, AgentResponse.class);
    }

    private void sendCommandNoResponse(AgentCommand command) throws Exception {
        String json = mapper.writeValueAsString(command);
        writer.write(json.getBytes(StandardCharsets.UTF_8));
        writer.write('\n');
        writer.flush();
    }

    private static final String AGENT_JAR_NAME = "fxgraph-agent.jar";

    /**
     * Find the agent JAR file path.
     * The agent JAR (fxgraph-agent.jar) is a minimal JAR containing only the inspector
     * classes and Jackson, without Spring Boot or other server-side dependencies.
     * It should be co-located with the MCP server or CLI JAR.
     */
    private String findAgentJar() throws Exception {
        // Strategy 1: Explicit system property
        String explicit = System.getProperty("fxgraph.agent.jar");
        if (explicit != null && new File(explicit).exists()) {
            return explicit;
        }

        // Strategy 2: Look for agent JAR next to the current JAR/class
        String currentJar = JavaFxAgent.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
        File currentFile = new File(currentJar);
        File parentDir = currentFile.getParentFile();

        if (parentDir != null) {
            File agentJar = new File(parentDir, AGENT_JAR_NAME);
            if (agentJar.exists()) {
                return agentJar.getAbsolutePath();
            }
        }

        // Strategy 3: Development mode - look in build/libs directories
        File[] devCandidates = {
                new File("agent/build/libs/" + AGENT_JAR_NAME),
                new File("build/libs/" + AGENT_JAR_NAME),
        };
        for (File candidate : devCandidates) {
            if (candidate.exists()) {
                return candidate.getAbsolutePath();
            }
        }

        throw new Exception("Agent JAR (" + AGENT_JAR_NAME + ") not found. "
                + "Ensure it is in the same directory as the JAR, "
                + "or set system property 'fxgraph.agent.jar' to its path.");
    }

    /**
     * Discover running Java processes that could potentially be JavaFX applications.
     */
    public static List<JavaFxApplication> discoverApplications() {
        List<JavaFxApplication> apps = new ArrayList<>();
        String currentPid = String.valueOf(ProcessHandle.current().pid());

        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        for (VirtualMachineDescriptor vmd : vms) {
            if (currentPid.equals(vmd.id())) continue;

            try {
                VirtualMachine vm = VirtualMachine.attach(vmd);
                try {
                    Properties sysProps = vm.getSystemProperties();
                    String mainClass = sysProps.getProperty("sun.java.command");
                    String vmName = sysProps.getProperty("java.vm.name");
                    String classPath = sysProps.getProperty("java.class.path", "");

                    boolean likelyJavaFx = isJavaFxLikely(sysProps, classPath);
                    if (!likelyJavaFx) continue;

                    String agentPort = sysProps.getProperty("fxgraph.agent.port");
                    boolean alreadyConnected = agentPort != null;

                    JavaFxApplication app = new JavaFxApplication();
                    app.setPid(Integer.parseInt(vmd.id()));
                    app.setMainClass(mainClass != null ? mainClass.split(" ")[0] : "unknown");
                    app.setVmName(vmName);
                    app.setJavaFX(true);
                    app.setConnected(alreadyConnected);

                    apps.add(app);
                } finally {
                    vm.detach();
                }
            } catch (Exception e) {
                // Skip inaccessible VMs
            }
        }

        return apps;
    }

    private static boolean isJavaFxLikely(Properties sysProps, String classPath) {
        if (sysProps.containsKey("javafx.version")) {
            return true;
        }
        String modulePath = sysProps.getProperty("jdk.module.path", "");
        return containsJavaFxArtifacts(modulePath) || containsJavaFxArtifacts(classPath);
    }

    private static boolean containsJavaFxArtifacts(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return false;
        }
        String[] entries = pathValue.split(Pattern.quote(File.pathSeparator));
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            File file = new File(entry);
            if (file.isFile()) {
                if (isJavaFxJarName(file.getName())) {
                    return true;
                }
            } else if (file.isDirectory()) {
                File[] matches = file.listFiles((dir, name) -> isJavaFxJarName(name));
                if (matches != null && matches.length > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isJavaFxJarName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar")) {
            return false;
        }
        if (lower.equals("jfxrt.jar")) {
            return true;
        }
        return lower.startsWith("javafx-")
                || lower.startsWith("javafx.")
                || lower.startsWith("openjfx-");
    }
}
