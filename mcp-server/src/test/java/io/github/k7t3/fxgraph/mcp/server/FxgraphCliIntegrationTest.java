package io.github.k7t3.fxgraph.mcp.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the CLI entry point packaged in the shadow JAR.
 */
class FxgraphCliIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SERVER_JAR_PATH = "build/libs/mcp-server.jar";

    @Test
    void cliHelpShowsAvailableCommands() throws Exception {
        CliInvocation invocation = invokeCli("--help");

        assertEquals(0, invocation.exitCode());
        assertTrue(invocation.output().contains("FXGraph CLI"));
        assertTrue(invocation.output().contains("discoverApplications"));
        assertTrue(invocation.output().contains("takeScreenshot"));
    }

    @Test
    void cliDiscoverApplicationsReturnsJson() throws Exception {
        CliInvocation invocation = invokeCli("discoverApplications");

        assertEquals(0, invocation.exitCode());
        Map<String, Object> result = parseJson(invocation.output());
        assertEquals(true, result.get("success"));
        assertTrue(result.containsKey("applications"));
    }

    @Test
    void cliUnknownCommandReturnsJsonError() throws Exception {
        CliInvocation invocation = invokeCli("unknownCommand");

        assertEquals(1, invocation.exitCode());
        Map<String, Object> result = parseJson(invocation.output());
        assertEquals(false, result.get("success"));
        assertTrue(result.get("error").toString().contains("Unknown CLI command"));
    }

    @Test
    void cliConnectApplicationWithInvalidPidReturnsError() throws Exception {
        CliInvocation invocation = invokeCli("connectApplication", "--pid", "0");

        assertEquals(1, invocation.exitCode());
        Map<String, Object> result = parseJson(invocation.output());
        assertEquals(false, result.get("success"));
        assertTrue(result.containsKey("error"));
    }

    private CliInvocation invokeCli(String... args) throws Exception {
        Path jarPath = Path.of(SERVER_JAR_PATH);
        Assumptions.assumeTrue(Files.exists(jarPath),
                "Shadow JAR not found at " + jarPath.toAbsolutePath()
                        + ". Run './gradlew :mcp-server:shadowJar' first.");

        List<String> command = new java.util.ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(jarPath.toAbsolutePath().toString());
        command.add("cli");
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        assertTrue(finished, "CLI process should finish within timeout");

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return new CliInvocation(process.exitValue(), output);
    }

    private Map<String, Object> parseJson(String output) throws IOException {
        return OBJECT_MAPPER.readValue(output, new TypeReference<>() {
        });
    }

    private record CliInvocation(int exitCode, String output) {
    }
}
