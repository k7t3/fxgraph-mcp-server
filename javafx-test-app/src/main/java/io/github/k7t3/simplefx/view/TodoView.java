package io.github.k7t3.simplefx.view;

import io.github.k7t3.simplefx.model.TodoItem;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class TodoView extends BorderPane {

    private final TextField titleField;
    private final TextField descriptionField;
    private final Button addButton;
    private final ListView<TodoItem> listView;
    private final Label statusLabel;
    private final ToggleButton allButton;
    private final ToggleButton activeButton;
    private final ToggleButton completedButton;
    private final Button clearCompletedButton;

    public TodoView() {
        titleField = new TextField();
        titleField.setPromptText("新しいタスクを入力...");
        titleField.getStyleClass().add("title-field");
        HBox.setHgrow(titleField, Priority.ALWAYS);

        descriptionField = new TextField();
        descriptionField.setPromptText("説明（オプション）");
        descriptionField.getStyleClass().add("description-field");
        HBox.setHgrow(descriptionField, Priority.ALWAYS);

        addButton = new Button("追加");
        addButton.getStyleClass().add("add-button");

        HBox inputBox = new HBox(8, titleField, descriptionField, addButton);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(16));
        inputBox.getStyleClass().add("input-box");

        listView = new ListView<>();
        listView.setCellFactory(lv -> new TodoListCell());
        listView.getStyleClass().add("todo-list");

        VBox.setVgrow(listView, Priority.ALWAYS);

        allButton = new ToggleButton("すべて");
        allButton.getStyleClass().add("filter-button");
        allButton.setSelected(true);

        activeButton = new ToggleButton("未完了");
        activeButton.getStyleClass().add("filter-button");

        completedButton = new ToggleButton("完了済み");
        completedButton.getStyleClass().add("filter-button");

        ToggleGroup filterGroup = new ToggleGroup();
        filterGroup.getToggles().addAll(allButton, activeButton, completedButton);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        clearCompletedButton = new Button("完了済みを削除");
        clearCompletedButton.getStyleClass().add("clear-button");

        HBox filterBox = new HBox(8, allButton, activeButton, completedButton);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox footerBox = new HBox(16, filterBox, spacer, statusLabel, clearCompletedButton);
        footerBox.setAlignment(Pos.CENTER_LEFT);
        footerBox.setPadding(new Insets(12, 16, 12, 16));
        footerBox.getStyleClass().add("footer-box");

        VBox centerBox = new VBox(listView);
        VBox.setVgrow(centerBox, Priority.ALWAYS);

        setTop(inputBox);
        setCenter(centerBox);
        setBottom(footerBox);

        getStyleClass().add("todo-view");
    }

    public TextField getTitleField() {
        return titleField;
    }

    public TextField getDescriptionField() {
        return descriptionField;
    }

    public Button getAddButton() {
        return addButton;
    }

    public ListView<TodoItem> getListView() {
        return listView;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    public ToggleButton getAllButton() {
        return allButton;
    }

    public ToggleButton getActiveButton() {
        return activeButton;
    }

    public ToggleButton getCompletedButton() {
        return completedButton;
    }

    public Button getClearCompletedButton() {
        return clearCompletedButton;
    }
}