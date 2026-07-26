package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Group;
import javafx.scene.SubScene;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ApplicationExtension.class)
class NodeHierarchyTest {

    @Test
    void should_return_empty_list_when_node_has_no_children() {
        assertThat(NodeHierarchy.directChildren(new Rectangle())).isEmpty();
    }

    @Test
    void should_return_empty_list_when_node_is_null() {
        assertThat(NodeHierarchy.directChildren(null)).isEmpty();
    }

    @Test
    void should_return_parent_children_in_scene_graph_order() {
        var firstChild = new Rectangle();
        var secondChild = new Rectangle();
        var parent = new Group(firstChild, secondChild);

        var children = NodeHierarchy.directChildren(parent);

        assertThat(children).containsExactly(firstChild, secondChild);
    }

    @Test
    void should_return_snapshot_of_parent_children() {
        var originalChild = new Rectangle();
        var parent = new Group(originalChild);

        var children = NodeHierarchy.directChildren(parent);
        parent.getChildren().add(new Rectangle());

        assertThat(children).containsExactly(originalChild);
    }

    @Test
    void should_return_immutable_children() {
        var parent = new Group(new Rectangle());
        var children = NodeHierarchy.directChildren(parent);

        assertThatThrownBy(() -> children.add(new Rectangle()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_return_subscene_root_as_direct_child() {
        var root = new Group(new Rectangle());
        var subScene = new SubScene(root, 100, 100);

        var children = NodeHierarchy.directChildren(subScene);

        assertThat(children).containsExactly(root);
    }
}
