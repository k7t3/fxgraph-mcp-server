package io.github.k7t3.fxgraph.mcp.agent.inspector;

import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class ScrollOperationsTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private StackPane root;

    @Start
    void start(Stage stage) {
        root = new StackPane();
        stage.setScene(new Scene(root, 400, 240));
        stage.show();
    }

    @Test
    void listScrollToIndexMakesOffscreenItemDiscoverable() {
        var list = new ListView<>(FXCollections.observableArrayList(items()));
        show(list);
        assertThat(inspector.findNodes(Map.of("text", "Row 80", "effectiveVisible", true)).getData()).isEqualTo(java.util.List.of());

        var response = invoke("scrollToIndex", params(list, "index", 80));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertVisible("Row 80");
    }

    @Test
    void tableScrollToIndexMakesOffscreenRowDiscoverable() {
        var table = new TableView<>(FXCollections.observableArrayList(items()));
        var column = new TableColumn<String, String>("Name");
        column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue()));
        table.getColumns().add(column);
        show(table);

        var response = invoke("scrollToIndex", params(table, "index", 70));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertVisible("Row 70");
    }

    @Test
    void listPixelScrollMovesVisibleCells() {
        var list = new ListView<>(FXCollections.observableArrayList(items()));
        show(list);

        var response = invoke("scrollNode", params(list, "dy", 500));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        onFx(() -> {
            var flow = (javafx.scene.control.skin.VirtualFlow<?>) list.lookup(".virtual-flow");
            assertThat(flow.getFirstVisibleCell().getIndex()).isPositive();
        });
    }

    @Test
    void listBottomAlignmentShowsFinalItem() {
        var list = new ListView<>(FXCollections.observableArrayList(items()));
        show(list);

        var response = invoke("scrollNode", params(list, "align", "bottom"));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertVisible("Row 99");
    }

    @Test
    void scrollPanePixelAndEdgeOperationsChangeOffsets() {
        var pane = new ScrollPane(new Rectangle(2000, 2000));
        show(pane);

        var response = invoke("scrollNode", params(pane, "dy", 500));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        onFx(() -> assertThat(pane.getVvalue()).isBetween(0.1, 0.5));
        assertThat(invoke("scrollNode", params(pane, "align", "bottom")).isSuccess()).isTrue();
        onFx(() -> assertThat(pane.getVvalue()).isEqualTo(pane.getVmax()));
        assertThat(invoke("scrollNode", params(pane, "align", "top")).isSuccess()).isTrue();
        onFx(() -> assertThat(pane.getVvalue()).isEqualTo(pane.getVmin()));
    }

    @Test
    void flowlessIndexNavigationCreatesNewCells() {
        var flow = flowless();
        show(flow);
        try {
            var response = invoke("scrollToIndex", params(flow, "index", 80));

            assertThat(response.isSuccess()).as(response.getError()).isTrue();
            onFx(() -> assertThat(flow.getCellIfVisible(80)).isPresent());
            assertVisible("Row 80");
        } finally {
            onFx(flow::dispose);
        }
    }

    @Test
    void flowlessScrollPaneSupportsPixelsAndBottomAlignment() {
        var flow = flowless();
        var pane = new VirtualizedScrollPane<>(flow);
        show(pane);
        try {
            assertThat(invoke("scrollNode", params(pane, "dy", 500)).isSuccess()).isTrue();
            onFx(() -> assertThat(flow.getFirstVisibleIndex()).isPositive());
            assertThat(invoke("scrollNode", params(pane, "align", "bottom")).isSuccess()).isTrue();
            onFx(() -> assertThat(flow.getCellIfVisible(99)).isPresent());
            assertThat(invoke("scrollToIndex", params(pane, "index", 10)).isSuccess()).isTrue();
            onFx(() -> assertThat(flow.getCellIfVisible(10)).isPresent());
        } finally {
            onFx(flow::dispose);
        }
    }

    @Test
    void invalidIndexIsRejectedWithoutScrolling() {
        var list = new ListView<>(FXCollections.observableArrayList(items()));
        show(list);

        var response = invoke("scrollToIndex", params(list, "index", 100));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("index");
        onFx(() -> assertThat(((javafx.scene.control.skin.VirtualFlow<?>) list.lookup(".virtual-flow"))
                .getFirstVisibleCell().getIndex()).isZero());
    }

    @Test
    void unknownTargetIsRejectedWithReason() {
        var rectangle = new Rectangle(50, 50);
        show(rectangle);

        var response = invoke("scrollNode", params(rectangle, "dy", 500));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("Unsupported scroll target");
    }

    private VirtualFlow<String, Cell<String, Label>> flowless() {
        return VirtualFlow.createVertical(FXCollections.observableArrayList(items()), item -> {
            var label = new Label(item);
            label.setPrefHeight(30);
            return Cell.wrapNode(label);
        });
    }

    private java.util.List<String> items() {
        return IntStream.range(0, 100).mapToObj(i -> "Row " + i).toList();
    }

    private Map<String, Object> params(Node node, String name, Object value) {
        return Map.of("nodeId", System.identityHashCode(node), name, value);
    }

    private void assertVisible(String text) {
        var response = inspector.findNodes(Map.of("text", text, "effectiveVisible", true));
        assertThat(response.isSuccess()).isTrue();
        assertThat((java.util.List<?>) response.getData()).isNotEmpty();
    }

    private AgentResponse invoke(String method, Map<String, Object> params) {
        return switch (method) {
            case "scrollNode" -> inspector.scrollNode(params);
            case "scrollToIndex" -> inspector.scrollToIndex(params);
            default -> throw new IllegalArgumentException(method);
        };
    }

    private void show(Node node) {
        onFx(() -> {
            root.getChildren().setAll(node);
            root.applyCss();
            root.layout();
        });
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
