package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.TitledPane;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class NodeInspectionTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private StackPane root;
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        root = new StackPane();
        root.setId("root");
        stage.setScene(new Scene(root, 400, 300));
        stage.show();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ancestorsDescribeContainingPaneThroughRoot() {
        var button = new Button("Action");
        var pane = new StackPane(button);
        pane.setId("cell");
        pane.getStyleClass().add("cell-container");
        show(pane);

        var response = inspector.getNodeDetails(Map.of(
                "nodeId", System.identityHashCode(button), "includeAncestors", true));

        assertThat(response.isSuccess()).isTrue();
        var data = (Map<String, Object>) response.getData();
        var ancestors = (List<Map<String, Object>>) data.get("ancestors");
        assertThat(ancestors).extracting(a -> a.get("nodeId")).containsExactly(
                System.identityHashCode(pane), System.identityHashCode(root));
        assertThat(ancestors.getFirst()).containsEntry("id", "cell")
                .containsEntry("type", "StackPane")
                .containsEntry("styleClass", List.of("cell-container"))
                .containsKey("bounds");
        var node = (Map<String, Object>) data.get("node");
        assertThat(node).containsEntry("parentId", System.identityHashCode(pane));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ancestorsCrossSubSceneBoundary() {
        var button = new Button("Nested");
        var nestedRoot = new StackPane(button);
        var subScene = new SubScene(nestedRoot, 200, 150);
        show(subScene);

        var response = inspector.getNodeDetails(Map.of(
                "nodeId", System.identityHashCode(button), "includeAncestors", true));

        assertThat(response.isSuccess()).isTrue();
        var ancestors = (List<Map<String, Object>>) ((Map<?, ?>) response.getData()).get("ancestors");
        assertThat(ancestors).extracting(a -> a.get("nodeId")).containsExactly(
                System.identityHashCode(nestedRoot), System.identityHashCode(subScene), System.identityHashCode(root));
    }

    @Test
    @SuppressWarnings("unchecked")
    void scenegraphReportsParentIdForSubSceneRoot() {
        var nestedRoot = new StackPane(new Button("Nested"));
        var subScene = new SubScene(nestedRoot, 200, 150);
        show(subScene);

        var response = inspector.getScenegraph(Map.of("depth", 3));

        assertThat(response.isSuccess()).isTrue();
        var roots = (List<Map<String, Object>>) ((Map<?, ?>) response.getData()).get("rootNodes");
        var tree = roots.stream().filter(n -> n.get("nodeId").equals(System.identityHashCode(root)))
                .findFirst().orElseThrow();
        var child = ((List<Map<String, Object>>) tree.get("children")).getFirst();
        var nested = ((List<Map<String, Object>>) child.get("children")).getFirst();
        assertThat(nested).containsEntry("parentId", System.identityHashCode(subScene));
    }

    private void show(Node node) {
        onFx(() -> {
            root.getChildren().setAll(node);
            root.applyCss();
            root.layout();
        });
    }

    @Test
    void invisibleAncestorIsReportedAndExcludedFromVisibleSearch() {
        var button = new Button("Hidden action");
        var pane = new StackPane(button);
        pane.setVisible(false);
        show(pane);

        assertThat(details(button)).containsEntry("inScene", true)
                .containsEntry("effectiveVisible", false).containsEntry("visibilityReason", "ancestor invisible");
        assertThat(inspector.findNodes(Map.of("text", "Hidden action", "effectiveVisible", true)).getData())
                .isEqualTo(List.of());
        var response = inspector.activateNode(Map.of("nodeId", System.identityHashCode(button)));
        assertThat(response.getError()).contains("ancestor invisible");
    }

    @Test
    void clippingAncestorExplainsInvisibleButton() {
        var button = new Button("Clipped");
        button.relocate(100, 100);
        var pane = new Pane(button);
        pane.setClip(new Rectangle(20, 20));
        show(pane);

        assertThat(details(button)).containsEntry("effectiveVisible", false)
                .containsEntry("clipped", true).containsEntry("visibilityReason", "clipped");
        assertThat(inspector.clickNode(Map.of("nodeId", System.identityHashCode(button))).getError())
                .contains("clipped");
    }

    @Test
    void collapsedTitledPaneContentIsNotEffectivelyVisible() {
        var button = new Button("Collapsed action");
        var pane = new TitledPane("Section", button);
        pane.setAnimated(false);
        pane.setExpanded(false);
        show(pane);

        assertThat(details(button)).containsEntry("effectiveVisible", false);
        assertThat(inspector.activateNode(Map.of("nodeId", System.identityHashCode(button))).isSuccess()).isFalse();
    }

    @Test
    void invisibleSubSceneIsIncludedInVisibilityCheck() {
        var button = new Button("Nested hidden");
        var subScene = new SubScene(new StackPane(button), 200, 150);
        subScene.setVisible(false);
        show(subScene);

        assertThat(details(button)).containsEntry("effectiveVisible", false)
                .containsEntry("visibilityReason", "ancestor invisible");
    }

    @Test
    void zeroSizeNodeHasSpecificReason() {
        var canvas = new Canvas(0, 0);
        show(canvas);

        assertThat(details(canvas)).containsEntry("effectiveVisible", false)
                .containsEntry("visibilityReason", "zero-size");
    }

    @Test
    void transparentAncestorHasSpecificReason() {
        var button = new Button("Transparent action");
        var pane = new StackPane(button);
        pane.setOpacity(0);
        show(pane);

        assertThat(details(button)).containsEntry("effectiveVisible", false)
                .containsEntry("visibilityReason", "ancestor transparent");
    }

    @Test
    void transparentWindowHasSpecificReason() {
        var button = new Button("Window transparent");
        show(button);
        onFx(() -> stage.setOpacity(0));
        try {
            assertThat(details(button)).containsEntry("effectiveVisible", false)
                    .containsEntry("visibilityReason", "window transparent");
        } finally {
            onFx(() -> stage.setOpacity(1));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> details(Node node) {
        var response = inspector.getNodeDetails(Map.of("nodeId", System.identityHashCode(node)));
        assertThat(response.isSuccess()).isTrue();
        return (Map<String, Object>) ((Map<?, ?>) response.getData()).get("node");
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
