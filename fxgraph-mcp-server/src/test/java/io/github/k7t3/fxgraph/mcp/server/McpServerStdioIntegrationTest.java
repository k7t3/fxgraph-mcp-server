package io.github.k7t3.fxgraph.mcp.server;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the MCP server over STDIO transport.
 * <p>
 * These tests launch the MCP server as a subprocess using the shadow JAR,
 * then communicate with it via the MCP SDK's StdioClientTransport to verify
 * the full JSON-RPC protocol flow.
 * <p>
 * The shadow JAR must be built before running these tests
 * (./gradlew :fxgraph-mcp-server:shadowJar).
 */
class McpServerStdioIntegrationTest {

    private static Path logbackConfigPath;

    private McpSyncClient client;

    @BeforeAll
    static void setUpLogbackConfig() throws IOException {
        // Extract logback config to a temp file for the server subprocess.
        // This ensures no log output leaks to stdout, keeping STDIO clean for MCP protocol.
        logbackConfigPath = Files.createTempFile("logback-integration-test", ".xml");
        try (InputStream is = McpServerStdioIntegrationTest.class
                .getResourceAsStream("/logback-integration-test.xml")) {
            assertNotNull(is, "logback-integration-test.xml not found on classpath");
            Files.write(logbackConfigPath, is.readAllBytes());
        }
    }

    @AfterAll
    static void cleanUpLogbackConfig() {
        if (logbackConfigPath != null) {
            try {
                Files.deleteIfExists(logbackConfigPath);
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @BeforeEach
    void setUp() {
        var jarPath = findServerJar();
        Assumptions.assumeTrue(Files.exists(jarPath),
                "Shadow JAR not found at " + jarPath.toAbsolutePath()
                        + ". Run './gradlew :fxgraph-mcp-server:shadowJar' first.");

        ServerParameters serverParams = ServerParameters.builder("java")
                .args("-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener",
                        "-Dlogback.configurationFile=" + logbackConfigPath.toAbsolutePath(),
                        "-Dspring.main.banner-mode=off",
                        "-jar", jarPath.toAbsolutePath().toString())
                .build();

        StdioClientTransport transport = new StdioClientTransport(serverParams, McpJsonMapper.createDefault());
        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(30))
                .clientInfo(new McpSchema.Implementation("test-client", "1.0.0"))
                .build();
    }

    private static Path findServerJar() {
        var moduleLocalPath = Path.of("build/libs/fxgraph-mcp-server.jar");
        if (Files.exists(moduleLocalPath)) {
            return moduleLocalPath;
        }
        return Path.of("fxgraph-mcp-server/build/libs/fxgraph-mcp-server.jar");
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try {
                client.closeGracefully();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    // ===== Initialization =====

    @Test
    void initializeReturnsServerInfo() {
        McpSchema.InitializeResult result = client.initialize();

        assertNotNull(result, "InitializeResult should not be null");
        assertNotNull(result.serverInfo(), "Server info should not be null");
        assertNotNull(result.serverInfo().name(), "Server name should not be null");
        assertFalse(result.serverInfo().name().isEmpty(), "Server name should not be empty");
        assertEquals("1.0.0", result.serverInfo().version());
    }

    @Test
    void initializeReturnsCapabilities() {
        McpSchema.InitializeResult result = client.initialize();

        assertNotNull(result.capabilities(), "Server capabilities should not be null");
        assertNotNull(result.capabilities().tools(), "Tool capabilities should not be null");
    }

    // ===== Ping =====

    @Test
    void pingReturnsSuccessfully() {
        client.initialize();

        Object pingResult = client.ping();
        assertNotNull(pingResult, "Ping result should not be null");
    }

    // ===== Tool Listing =====

    @Test
    void listToolsReturnsAllExpectedTools() {
        client.initialize();

        McpSchema.ListToolsResult result = client.listTools();
        assertNotNull(result, "ListToolsResult should not be null");

        List<McpSchema.Tool> tools = result.tools();
        assertNotNull(tools, "Tool list should not be null");
        assertFalse(tools.isEmpty(), "Tool list should not be empty");

        Set<String> toolNames = tools.stream()
                .map(McpSchema.Tool::name)
                .collect(Collectors.toSet());

        // Verify all expected tools are registered
        Set<String> expectedTools = Set.of(
                "discoverApplications",
                "connectApplication",
                "disconnectApplication",
                "getStages",
                "getScenegraph",
                "getNodeDetails",
                "setProperty",
                "selectNode",
                "clickNode",
                "requestFocus",
                "typeKey",
                "takeScreenshot",
                "captureVideo"
        );

        for (String expected : expectedTools) {
            assertTrue(toolNames.contains(expected),
                    "Tool '" + expected + "' should be registered. Found tools: " + toolNames);
        }
    }

    @Test
    void listToolsReturnsToolsWithDescriptions() {
        client.initialize();

        McpSchema.ListToolsResult result = client.listTools();
        for (McpSchema.Tool tool : result.tools()) {
            assertNotNull(tool.name(), "Tool name should not be null");
            assertNotNull(tool.description(), "Tool '" + tool.name() + "' should have a description");
            assertFalse(tool.description().isEmpty(),
                    "Tool '" + tool.name() + "' description should not be empty");
        }
    }

    @Test
    void listToolsReturnsToolsWithInputSchemas() {
        client.initialize();

        McpSchema.ListToolsResult result = client.listTools();
        for (McpSchema.Tool tool : result.tools()) {
            assertNotNull(tool.inputSchema(),
                    "Tool '" + tool.name() + "' should have an input schema");
        }
    }

    @Test
    void targetedToolsRequirePidAndDoNotExposeSessionId() {
        client.initialize();

        var targetedTools = client.listTools().tools().stream()
                .filter(tool -> !"discoverApplications".equals(tool.name()))
                .toList();

        for (var tool : targetedTools) {
            var schema = tool.inputSchema();
            assertTrue(schema.properties().containsKey("pid"),
                    () -> tool.name() + " should expose pid: " + schema);
            assertTrue(schema.required().contains("pid"),
                    () -> tool.name() + " should require pid: " + schema);
            assertFalse(schema.properties().containsKey("sessionId"),
                    () -> tool.name() + " must not expose sessionId: " + schema);
        }
    }

    // ===== Tool Invocation =====

    @Test
    void callDiscoverApplicationsReturnsResult() {
        client.initialize();

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("discoverApplications", Map.of()));

        assertNotNull(result, "CallToolResult should not be null");
        assertNotNull(result.content(), "Result content should not be null");
        assertFalse(result.content().isEmpty(), "Result content should not be empty");

        // The response should contain text content with JSON
        McpSchema.Content content = result.content().getFirst();
        assertInstanceOf(McpSchema.TextContent.class, content);
        String text = ((McpSchema.TextContent) content).text();
        assertNotNull(text, "Text content should not be null");
        // The result should contain "success" field
        assertTrue(text.contains("success"), "Response should contain 'success' field: " + text);
    }

    @Test
    void callDisconnectApplicationWithInvalidPidReturnsError() {
        client.initialize();

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("disconnectApplication",
                        Map.of("pid", 0)));

        assertNotNull(result);
        assertNotNull(result.content());
        assertFalse(result.content().isEmpty());

        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("PID must be a positive integer") || text.contains("error"),
                "Response should indicate invalid PID: " + text);
    }

    @Test
    void callConnectApplicationWithInvalidPidReturnsError() {
        client.initialize();

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("connectApplication",
                        Map.of("pid", 0)));

        assertNotNull(result);
        assertNotNull(result.content());
        assertFalse(result.content().isEmpty());

        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("error") || text.contains("false"),
                "Response should indicate connection failure: " + text);
    }

    @Test
    void callGetStagesWithInvalidPidReturnsError() {
        client.initialize();

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("getStages",
                        Map.of("pid", 0)));

        assertNotNull(result);
        assertNotNull(result.content());
        assertFalse(result.content().isEmpty());

        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("PID must be a positive integer") || text.contains("error"),
                "Response should indicate invalid PID: " + text);
    }

    @Test
    void callGetScenegraphWithInvalidPidReturnsError() {
        client.initialize();

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("getScenegraph",
                        Map.of("pid", 0)));

        assertNotNull(result);
        assertNotNull(result.content());
        assertFalse(result.content().isEmpty());

        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("PID must be a positive integer") || text.contains("error"),
                "Response should indicate invalid PID: " + text);
    }

    @Test
    void callGetNodeDetailsWithInvalidPidReturnsError() {
        client.initialize();

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("getNodeDetails",
                        Map.of("pid", 0, "nodeId", 42)));

        assertNotNull(result);
        assertNotNull(result.content());
        assertFalse(result.content().isEmpty());

        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("PID must be a positive integer") || text.contains("error"),
                "Response should indicate invalid PID: " + text);
    }

    // ===== Multiple Operations =====

    @Test
    void multipleToolCallsInSequence() {
        client.initialize();

        // First call: list tools
        McpSchema.ListToolsResult toolsResult = client.listTools();
        assertNotNull(toolsResult);
        assertFalse(toolsResult.tools().isEmpty());

        // Second call: discover applications
        McpSchema.CallToolResult discoverResult = client.callTool(
                new McpSchema.CallToolRequest("discoverApplications", Map.of()));
        assertNotNull(discoverResult);

        // Third call: disconnect with invalid PID
        McpSchema.CallToolResult disconnectResult = client.callTool(
                new McpSchema.CallToolRequest("disconnectApplication",
                        Map.of("pid", 0)));
        assertNotNull(disconnectResult);
    }
}
