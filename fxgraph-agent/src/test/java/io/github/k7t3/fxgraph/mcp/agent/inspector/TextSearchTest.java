package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class TextSearchTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private Text text;
    private TextFlow flow;

    @Start
    void start(Stage stage) {
        text = new Text("plain-text-node-marker 日本語\nsecond line");
        flow = new TextFlow(text, new Text("another fragment"));
        stage.setScene(new Scene(new VBox(flow, new Label("label-marker"), new TextField("input-marker")), 400, 240));
        stage.show();
    }

    @Test
    void textSearchReturnsTextInsideTextFlowWithContentAndParent() {
        assertThat(results(Map.of("text", "plain-text-node-marker"))).singleElement().satisfies(node ->
                assertThat(node).containsEntry("nodeId", System.identityHashCode(text))
                        .containsEntry("text", "plain-text-node-marker 日本語\nsecond line")
                        .containsEntry("parentId", System.identityHashCode(flow)));
    }

    @Test
    void typeSearchIncludesTextContentWithoutSeparateDetailsCalls() {
        assertThat(results(Map.of("type", "Text"))).anySatisfy(node ->
                assertThat(node).containsEntry("nodeId", System.identityHashCode(text))
                        .containsEntry("text", "plain-text-node-marker 日本語\nsecond line"));
    }

    @Test
    void existingControlSearchRemainsAvailableAndSearchIsCaseSensitive() {
        assertThat(results(Map.of("type", "Label", "text", "label-marker"))).hasSize(1);
        assertThat(results(Map.of("type", "TextField", "text", "input-marker"))).hasSize(1);
        assertThat(results(Map.of("text", "PLAIN-TEXT-NODE-MARKER"))).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> results(Map<String, Object> params) {
        var response = inspector.findNodes(params);
        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        return (List<Map<String, Object>>) response.getData();
    }
}
