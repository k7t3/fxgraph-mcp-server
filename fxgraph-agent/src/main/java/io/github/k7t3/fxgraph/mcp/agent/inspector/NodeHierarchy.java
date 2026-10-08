package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SubScene;

import java.util.List;
import java.util.ArrayList;

/**
 * Describes direct containment relationships in a JavaFX scene graph.
 *
 * @since 1.0
 */
final class NodeHierarchy {

    private NodeHierarchy() {
    }

    /**
     * Returns an immutable snapshot of the nodes directly contained by {@code node}.
     *
     * <p>A {@link Parent} directly contains its public children, while a
     * {@link SubScene} directly contains the root of its nested scene graph.</p>
     *
     * @param node the node whose direct children are requested, or {@code null}
     * @return the direct children in scene graph order
     */
    static List<Node> directChildren(Node node) {
        return switch (node) {
            case null -> List.of();
            case SubScene subScene -> List.of(subScene.getRoot());
            case Parent parent -> List.copyOf(parent.getChildrenUnmodifiable());
            default -> List.of();
        };
    }

    /** Returns the containment path from root to target, including SubScene boundaries. */
    static List<Node> pathTo(Node root, Node target) {
        var path = new ArrayList<Node>();
        return collectPath(root, target, path) ? List.copyOf(path) : List.of();
    }

    /** Returns the containing node, including the SubScene that owns a nested root. */
    static Node parentOf(Node node) {
        if (node.getParent() != null) return node.getParent();
        var scene = node.getScene();
        if (scene == null || scene.getRoot() == node) return null;
        var path = pathTo(scene.getRoot(), node);
        return path.size() > 1 ? path.get(path.size() - 2) : null;
    }

    private static boolean collectPath(Node node, Node target, List<Node> path) {
        path.add(node);
        if (node == target) return true;
        for (var child : directChildren(node)) {
            if (collectPath(child, target, path)) return true;
        }
        path.removeLast();
        return false;
    }
}
