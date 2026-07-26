package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SubScene;

import java.util.List;

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
}
