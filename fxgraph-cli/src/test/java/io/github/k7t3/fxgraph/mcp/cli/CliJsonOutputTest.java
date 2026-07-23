package io.github.k7t3.fxgraph.mcp.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CliJsonOutput — verifies stdout/stderr capture and return values.
 */
class CliJsonOutputTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void redirectStreams() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void successReturnsZero() {
        int code = CliJsonOutput.success("hello");
        assertEquals(0, code);
    }

    @Test
    void successWritesToStdout() {
        CliJsonOutput.success("hello");
        assertFalse(outContent.toString().isBlank(), "stdout should contain output");
        assertTrue(errContent.toString().isBlank(), "stderr should be empty on success");
    }

    @Test
    void successOutputIsValidJson() throws Exception {
        CliJsonOutput.success(Map.of("key", "value"));
        String output = outContent.toString().trim();
        // Should be parseable by Jackson
        new ObjectMapper().readTree(output);
    }

    @Test
    void successWithMap() throws Exception {
        CliJsonOutput.success(Map.of("nodeId", 42, "type", "Button"));
        String output = outContent.toString();
        assertTrue(output.contains("\"nodeId\""));
        assertTrue(output.contains("42"));
    }

    @Test
    void successWithList() throws Exception {
        CliJsonOutput.success(List.of("a", "b", "c"));
        String output = outContent.toString().trim();
        // Should be a JSON array
        assertTrue(output.startsWith("[") || output.contains("[ \"a\""),
                "Expected JSON array, got: " + output);
        new ObjectMapper().readTree(output);
    }

    @Test
    void failureReturnsOne() {
        int code = CliJsonOutput.failure("something went wrong");
        assertEquals(1, code);
    }

    @Test
    void failureWritesToStderr() {
        CliJsonOutput.failure("something went wrong");
        assertTrue(outContent.toString().isBlank(), "stdout should be empty on failure");
        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Error:"), "stderr should contain 'Error:'");
        assertTrue(errOutput.contains("something went wrong"));
    }
}
