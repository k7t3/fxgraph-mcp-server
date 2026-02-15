package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SubScene;

/**
 * Utility to retrieve child nodes from any JavaFX node.
 * Handles Parent and SubScene cases.
 * 
 * Based on Scenic View's ChildrenGetter.
 */
public class ChildrenGetter {

    private ChildrenGetter() {}

    public static ObservableList<Node> getChildren(Node node) {
        if (node == null) return FXCollections.emptyObservableList();

        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable();
        } else if (node instanceof SubScene subScene) {
            if (subScene.getRoot() != null) {
                return subScene.getRoot().getChildrenUnmodifiable();
            }
        }

        return FXCollections.emptyObservableList();
    }
}
