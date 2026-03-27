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
     * Attach to the target JVM and inject the inspector agent.
     */
    public boolean connect() throws Exception {
        try {
            vm = VirtualMachine.attach(pid);

            // Find the agent JAR. It should be bundled alongside the MCP server JAR.
            String agentJarPath = findAgentJar();

            // Load the agent into the target JVM
            vm.loadAgent(agentJarPath);

            // Get the port assigned by the agent.
            // The agent sets it via System.setProperty() in the target JVM,
            // so we read it from system properties.
            // We may need to retry as the agent starts asynchronously.
            String portStr = null;
            for (int i = 0; i < 10 && portStr == null; i++) {
                portStr = vm.getSystemProperties().getProperty("fxgraph.agent.port");
                if (portStr == null) {
                    // Also try agent properties as fallback
                    portStr = vm.getAgentProperties().getProperty("fxgraph.agent.port");
                }
                if (portStr == null) {
                    Thread.sleep(500);
                }
            }

            if (portStr == null) {
                throw new Exception("Agent started but port not found. The agent may not be loaded correctly.");
            }

            agentPort = Integer.parseInt(portStr);

            // Connect to the agent
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
     * Disconnect from the target JVM.
     */
    public void disconnect() {
        connected = false;
        agentPort = -1;

        if (socket != null) {
            try {
                // Send shutdown command
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
     * It should be co-located with the MCP server JAR.
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

        // Strategy 3: Development mode - look in build/libs and agent/build/libs
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
                + "Ensure it is in the same directory as the MCP server JAR, "
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
            // Skip our own JVM process
            if (currentPid.equals(vmd.id())) continue;

            try {
                VirtualMachine vm = VirtualMachine.attach(vmd);
                try {
                    Properties sysProps = vm.getSystemProperties();
                    String mainClass = sysProps.getProperty("sun.java.command");
                    String vmName = sysProps.getProperty("java.vm.name");
                    String classPath = sysProps.getProperty("java.class.path", "");

                    // Check if JavaFX is available - the javafx.version system property
                    // is set when JavaFX runtime is loaded (same approach as Scenic View).
                    // Also check classpath/module path for JavaFX artifacts as fallback.
                    boolean likelyJavaFx = isJavaFxLikely(sysProps, classPath);

                    // Check if our agent is already injected
                    String agentPort = sysProps.getProperty("fxgraph.agent.port");
                    boolean alreadyConnected = agentPort != null;

                    // Only return JavaFX applications
                    if (!likelyJavaFx) continue;

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
