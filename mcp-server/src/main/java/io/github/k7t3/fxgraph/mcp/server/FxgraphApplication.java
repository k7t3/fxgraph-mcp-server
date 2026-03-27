package io.github.k7t3.fxgraph.mcp.server;

import java.util.Arrays;

/**
 * Launches either the MCP server or the CLI entry point.
 */
public final class FxgraphApplication {

    private FxgraphApplication() {
    }

    /**
     * Starts the MCP server by default, or the CLI when the first argument is {@code cli}.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        if (args.length > 0 && "cli".equalsIgnoreCase(args[0])) {
            int exitCode = FxgraphCliApplication.run(Arrays.copyOfRange(args, 1, args.length));
            if (exitCode != 0) {
                System.exit(exitCode);
            }
            return;
        }
        McpServerApplication.run(args);
    }
}
