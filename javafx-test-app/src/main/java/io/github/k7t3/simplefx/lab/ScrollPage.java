package io.github.k7t3.simplefx.lab;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.util.List;
import java.util.stream.IntStream;

import static io.github.k7t3.simplefx.lab.DemoSupport.*;

final class ScrollPage extends VBox {
    private final VirtualFlow<String, Cell<String, Label>> flow;
    private final VirtualFlow<String, Cell<String, Label>> wrappedFlow;

    ScrollPage(EventJournal journal) {
        super(16);
        getStyleClass().add("demo-page");
        var list = identified("scroll-list", new ListView<>(FXCollections.observableArrayList(items("List item"))));
        list.setFixedCellSize(28);
        var table = identified("scroll-table", new TableView<>(FXCollections.observableArrayList(items("Table row"))));
        table.setFixedCellSize(28);
        var name = new TableColumn<String, String>("行");
        name.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue()));
        name.setPrefWidth(165);
        var index = new TableColumn<String, String>("状態");
        index.setCellValueFactory(cell -> new SimpleStringProperty("確認対象"));
        index.setPrefWidth(110);
        table.getColumns().add(name);
        table.getColumns().add(index);
        var grid = new GridPane(28, 28);
        grid.setPrefSize(1200, 1000);
        for (int row = 0; row < 12; row++) {
            for (int column = 0; column < 8; column++) {
                var cell = identified("grid-r%d-c%d".formatted(row, column), new Label("Grid %02d,%02d".formatted(row, column)));
                cell.setMinSize(125, 55);
                grid.add(cell, column, row);
            }
        }
        var pane = identified("scroll-pane", new ScrollPane(grid));
        pane.setFitToWidth(false);
        pane.setFitToHeight(false);
        var position = status("scroll-position", "hvalue=0.000 vvalue=0.000");
        Runnable updatePosition = () -> position.setText("hvalue=%.3f vvalue=%.3f".formatted(pane.getHvalue(), pane.getVvalue()));
        pane.hvalueProperty().addListener(obs -> updatePosition.run());
        pane.vvalueProperty().addListener(obs -> updatePosition.run());
        var selection = status("scroll-selection", "行の選択待ち");
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            selection.setText("選択: " + value);
            journal.record(selection.getText());
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            selection.setText("選択: " + value);
            journal.record(selection.getText());
        });
        flow = identified("flowless-flow", flow("Flowless item"));
        wrappedFlow = identified("wrapped-flow", flow("Wrapped item"));
        var wrapper = identified("flowless-wrapper", new VirtualizedScrollPane<>(wrappedFlow));
        var containers = new GridPane(14, 14);
        for (int i = 0; i < 3; i++) {
            var column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3);
            containers.getColumnConstraints().add(column);
        }
        containers.add(card("ListView（100件）", list), 0, 0);
        containers.add(card("TableView（100件）", table), 1, 0);
        containers.add(card("ScrollPane（縦・横）", pane), 2, 0);
        containers.add(card("Flowless VirtualFlow（100件）", flow), 0, 1);
        containers.add(card("VirtualizedScrollPane（100件）", wrapper), 1, 1);
        containers.add(card("結果", selection, position,
                new Label("index=80 または bottom で末尾付近を表示。\nスクロール後はノード ID を再取得してください。")), 2, 1);
        for (var container : List.of(list, table, pane, flow, wrapper)) {
            container.setPrefHeight(180);
            container.setMinHeight(180);
            VBox.setVgrow(container, Priority.ALWAYS);
        }
        getChildren().addAll(heading("スクロールと仮想化", "各リストは index=0〜99。ListView / TableView 内の JavaFX VirtualFlow と Flowless の単体・ラッパーを確認できます。"), containers);
    }

    private VirtualFlow<String, Cell<String, Label>> flow(String prefix) {
        return VirtualFlow.createVertical(FXCollections.observableArrayList(items(prefix)), item -> {
            var label = new Label(item);
            label.setMinHeight(28);
            label.setPrefHeight(28);
            return Cell.wrapNode(label);
        });
    }

    private List<String> items(String prefix) {
        return IntStream.range(0, 100).mapToObj(i -> "%s %03d".formatted(prefix, i)).toList();
    }

    void dispose() {
        flow.dispose();
        wrappedFlow.dispose();
    }
}
