package io.github.k7t3.fxgraph.mcp.cli;

/**
 * Entry point for the fxgraph CLI tool.
 *
 * <p>Usage:
 * <pre>
 *   fxgraph discover
 *   fxgraph &lt;pid&gt; stages
 *   fxgraph &lt;pid&gt; scenegraph [--stageId S] [--depth N] [--bounds] [--props] [--transforms] [--filter p1,p2]
 *   fxgraph &lt;pid&gt; node-details &lt;nodeId&gt; [--filter p1,p2]
 *   fxgraph &lt;pid&gt; set-property &lt;nodeId&gt; &lt;property&gt; &lt;value&gt; [--type TYPE]
 *   fxgraph &lt;pid&gt; select-node &lt;nodeId&gt; [--no-bounds]
 *   fxgraph &lt;pid&gt; click-node &lt;nodeId&gt;
 *   fxgraph &lt;pid&gt; focus &lt;nodeId&gt;
 *   fxgraph &lt;pid&gt; type-key &lt;key&gt; [--nodeId N]
 *   fxgraph &lt;pid&gt; screenshot &lt;outputPath&gt; [--nodeId N] [--stageId S]
 *   fxgraph &lt;pid&gt; capture-video &lt;outputPath&gt; [--nodeId N] [--stageId S] [--durationSeconds N]
 * </pre>
 *
 * <p>All output is JSON to stdout. Errors are written to stderr with exit code 1.
 */
public class FxgraphApplication {

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            System.exit(1);
        }
        int exitCode = new CliCommandDispatcher().dispatch(args);
        System.exit(exitCode);
    }

    static void printHelp() {
        System.err.println("fxgraph - JavaFX Scene Graph CLI Tool");
        System.err.println();
        System.err.println("Usage:");
        System.err.println("  fxgraph discover");
        System.err.println("      List running JavaFX applications (JSON array).");
        System.err.println();
        System.err.println("  fxgraph <pid> stages");
        System.err.println("      List showing windows (Stages and PopupWindows) in the application.");
        System.err.println();
        System.err.println("  fxgraph <pid> scenegraph [options]");
        System.err.println("      Get the scene graph tree.");
        System.err.println("      --stageId <id>     Target a window (legacy option name)");
        System.err.println("      --depth <n>        Limit traversal depth");
        System.err.println("      --bounds           Include bounding boxes");
        System.err.println("      --props            Include node properties");
        System.err.println("      --transforms       Include transform properties");
        System.err.println("      --filter <p1,p2>   Comma-separated property filter");
        System.err.println();
        System.err.println("  fxgraph <pid> node-details <nodeId> [options]");
        System.err.println("      Get detailed information about a specific node.");
        System.err.println("      --filter <p1,p2>   Comma-separated property filter");
        System.err.println();
        System.err.println("  fxgraph <pid> set-property <nodeId> <property> <value> [--type TYPE]");
        System.err.println("      Set a property on a node. Types: string, number, boolean, color");
        System.err.println();
        System.err.println("  fxgraph <pid> select-node <nodeId> [--no-bounds]");
        System.err.println("      Highlight a node with a red border overlay.");
        System.err.println();
        System.err.println("  fxgraph <pid> click-node <nodeId>");
        System.err.println("      Fire a mouse click event on a node.");
        System.err.println();
        System.err.println("  fxgraph <pid> focus <nodeId>");
        System.err.println("      Request keyboard focus for a node.");
        System.err.println();
        System.err.println("  fxgraph <pid> type-key <key> [--nodeId N]");
        System.err.println("      Type a key (e.g. 'a', 'ENTER') into a node or the focused node.");
        System.err.println();
        System.err.println("  fxgraph <pid> screenshot <outputPath> [--nodeId N] [--stageId S] [--maxWidth W] [--maxHeight H]");
        System.err.println("      Save a PNG screenshot of a node or one window scene. Default max size: 1280x720.");
        System.err.println("      --stageId <id>           Target Stage or popup scene (legacy option name)");
        System.err.println();
        System.err.println("  fxgraph <pid> capture-video <outputPath> [options]");
        System.err.println("      Save a silent MP4/H.264 clip of a node or window scene.");
        System.err.println("      --nodeId <id>            Target node (takes precedence over stageId)");
        System.err.println("      --stageId <id>           Target window scene (legacy option name)");
        System.err.println("      --durationSeconds <n>    Duration from 1 through 30 (default: 5)");
        System.err.println("      --framesPerSecond <n>    Frame rate from 1 through 30 (default: 10)");
        System.err.println("      --maxWidth <n>           Maximum width (default: 1280)");
        System.err.println("      --maxHeight <n>          Maximum height (default: 720)");
        System.err.println();
        System.err.println("All output is JSON. Errors are written to stderr (exit code 1).");
    }
}
