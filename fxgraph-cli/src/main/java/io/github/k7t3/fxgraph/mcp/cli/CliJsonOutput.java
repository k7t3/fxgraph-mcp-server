package io.github.k7t3.fxgraph.mcp.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Utility for writing JSON results to stdout and error messages to stderr.
 */
public class CliJsonOutput {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Serialize {@code data} to pretty-printed JSON and print to stdout.
     * Returns 0 on success, 1 on serialization error.
     */
    public static int success(Object data) {
        try {
            System.out.println(MAPPER.writeValueAsString(data));
            return 0;
        } catch (Exception e) {
            return failure("Failed to serialize output: " + e.getMessage());
        }
    }

    /**
     * Print an error message to stderr and return exit code 1.
     */
    public static int failure(String message) {
        System.err.println("Error: " + message);
        return 1;
    }
}
