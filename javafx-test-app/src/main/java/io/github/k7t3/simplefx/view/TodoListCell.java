package io.github.k7t3.simplefx.view;

import io.github.k7t3.simplefx.model.TodoItem;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class TodoListCell extends ListCell<TodoItem> {

    private final CheckBox checkBox;
    private final Label titleLabel;
    private final Text descriptionText;
    private final TextFlow descriptionFlow;
    private final VBox contentBox;
    private final HBox mainBox;
    private final Button deleteButton;
    private final Region spacer;

    public TodoListCell() {
        checkBox = new CheckBox();
        checkBox.getStyleClass().add("todo-checkbox");
        
        titleLabel = new Label();
        titleLabel.getStyleClass().add("todo-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        descriptionText = new Text();
        descriptionFlow = new TextFlow(descriptionText);
        descriptionFlow.getStyleClass().add("todo-description");

        contentBox = new VBox(4, titleLabel, descriptionFlow);
        VBox.setVgrow(titleLabel, Priority.ALWAYS);

        spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        deleteButton = new Button("削除");
        deleteButton.getStyleClass().add("delete-button");

        mainBox = new HBox(12, checkBox, contentBox, spacer, deleteButton);
        mainBox.setAlignment(Pos.CENTER_LEFT);
        mainBox.setPadding(new Insets(8, 12, 8, 12));
        mainBox.getStyleClass().add("todo-item");

        HBox.setHgrow(contentBox, Priority.ALWAYS);
    }

    @Override
    protected void updateItem(TodoItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        checkBox.selectedProperty().unbind();
        checkBox.selectedProperty().bindBidirectional(item.completedProperty());

        titleLabel.textProperty().unbind();
        titleLabel.textProperty().bind(item.titleProperty());

        descriptionText.textProperty().unbind();
        descriptionText.textProperty().bind(item.descriptionProperty());
        descriptionFlow.setVisible(item.getDescription() != null && !item.getDescription().isEmpty());
        descriptionFlow.setManaged(item.getDescription() != null && !item.getDescription().isEmpty());

        updateStyle(item);

        item.completedProperty().addListener((obs, oldVal, newVal) -> updateStyle(item));

        setGraphic(mainBox);
    }

    private void updateStyle(TodoItem item) {
        if (item.isCompleted()) {
            titleLabel.getStyleClass().add("completed");
            contentBox.getStyleClass().add("completed");
        } else {
            titleLabel.getStyleClass().remove("completed");
            contentBox.getStyleClass().remove("completed");
        }
    }

    public Button getDeleteButton() {
        return deleteButton;
    }
}