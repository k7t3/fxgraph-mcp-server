package io.github.k7t3.fxgraph.mcp.cli;

/**
 * Entry point for the fxgraph CLI tool.
 *
 * <p>Usage:
 * <pre>
 *   fxgraph discover
 *   fxgraph &lt;pid&gt; stages
 *   fxgraph &lt;pid&gt; scenegraph [--stageId S] [--depth N] [--bounds] [--props] [--transforms] [--filter p1,p2]
 *   fxgraph &lt;pid&gt; node-details &lt;nodeId&gt; [--filter p1,p2] [--ancestors]
 *   fxgraph &lt;pid&gt; window-details --stageId S
 *   fxgraph &lt;pid&gt; set-window-property &lt;stageId&gt; &lt;property&gt; &lt;value&gt; [--type TYPE]
 *   fxgraph &lt;pid&gt; close-window &lt;stageId&gt;
 *   fxgraph &lt;pid&gt; close-popup &lt;stageId&gt;
 *   fxgraph &lt;pid&gt; scroll-node &lt;nodeId&gt; [--dx N] [--dy N] [--align top|bottom|left|right]
 *   fxgraph &lt;pid&gt; scroll-to-index &lt;nodeId&gt; --index N
 *   fxgraph &lt;pid&gt; set-property &lt;nodeId&gt; &lt;property&gt; &lt;value&gt; [--type TYPE]
 *   fxgraph &lt;pid&gt; select-node &lt;nodeId&gt; [--no-bounds]
 *   fxgraph &lt;pid&gt; click-node &lt;nodeId&gt; [--mode synthetic] [--button primary|secondary|middle] [--clickCount 1|2]
 *   fxgraph &lt;pid&gt; right-click-node &lt;nodeId&gt; [--mode synthetic]
 *   fxgraph &lt;pid&gt; double-click-node &lt;nodeId&gt; [--mode synthetic]
 *   fxgraph &lt;pid&gt; activate-node &lt;nodeId&gt;
 *   fxgraph &lt;pid&gt; focus &lt;nodeId&gt;
 *   fxgraph &lt;pid&gt; type-key &lt;key&gt; [--nodeId N] [--modifiers META,SHIFT] [--mode synthetic]
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
        System.err.println("  fxgraph <pid> window-details <stageId> (or --stageId <id>)");
        System.err.println("      Read window geometry, state and Stage flags.");
        System.err.println("  fxgraph <pid> set-window-property <stageId> <property> <value> [--type TYPE]");
        System.err.println("      Set x/y/width/height/opacity/title/maximized/iconified/alwaysOnTop/resizable.");
        System.err.println("  fxgraph <pid> close-window <stageId> (or --stageId <id>)");
        System.err.println("      Request close; the application can consume WINDOW_CLOSE_REQUEST.");
        System.err.println("  fxgraph <pid> close-popup <stageId> (or --stageId <id>)");
        System.err.println("      Hide only the selected PopupWindow.");
        System.err.println();
        System.err.println("  fxgraph <pid> find-nodes [--type T] [--id ID] [--text TEXT] [--styleClass C] [--stageId S] [--visible-only]");
        System.err.println("      Match Node classes, including subclasses; effective visibility includes ancestor state and clipping.");
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
        System.err.println("      --ancestors       Include containing nodes through the scene root");
        System.err.println();
        System.err.println("  fxgraph <pid> set-property <nodeId> <property> <value> [--type TYPE]");
        System.err.println("      Set a property on a node. Types: string, number, boolean, color");
        System.err.println();
        System.err.println("  fxgraph <pid> select-node <nodeId> [--no-bounds]");
        System.err.println("      Highlight a node with a red border overlay.");
        System.err.println();
        System.err.println("  fxgraph <pid> click-node <nodeId> [--mode synthetic] [--button primary|secondary|middle] [--clickCount 1|2]");
        System.err.println("  fxgraph <pid> right-click-node <nodeId> [--mode synthetic]");
        System.err.println("  fxgraph <pid> double-click-node <nodeId> [--mode synthetic]");
        System.err.println("      Click with a synthetic JavaFX gesture without OS input permissions.");
        System.err.println();
        System.err.println("  fxgraph <pid> activate-node <nodeId>");
        System.err.println("      Fire a ButtonBase action without mouse input.");
        System.err.println();
        System.err.println("  fxgraph <pid> focus <nodeId>");
        System.err.println("      Request keyboard focus for a node.");
        System.err.println();
        System.err.println("  fxgraph <pid> type-key <key> [--nodeId N] [--modifiers META,SHIFT] [--mode synthetic]");
        System.err.println("      Send a key gesture into a node or the focused scene (default: synthetic).");
        System.err.println("      Modifiers: SHIFT, CTRL/CONTROL, ALT, CMD/META. Only synthetic JavaFX input is supported.");
        System.err.println();
        System.err.println("  fxgraph <pid> scroll-node <nodeId> [--dx N] [--dy N] [--align top|bottom|left|right]");
        System.err.println("  fxgraph <pid> scroll-to-index <nodeId> --index N");
        System.err.println("      Scroll supported containers; positive pixels move right/down. Re-run find-nodes afterwards.");
        System.err.println();
        System.err.println("  fxgraph <pid> screenshot <outputPath> [--nodeId N] [--stageId S] [--maxWidth W] [--maxHeight H] [--no-limit]");
        System.err.println("      Save PNG at source resolution by default; 0 disables an axis resize limit.");
        System.err.println("      Source safety limits: 8192 pixels per dimension, 16777216 pixels total (also with --no-limit).");
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
