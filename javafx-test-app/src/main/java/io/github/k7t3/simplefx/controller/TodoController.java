package io.github.k7t3.simplefx.controller;

import io.github.k7t3.simplefx.model.TodoItem;
import io.github.k7t3.simplefx.view.TodoListCell;
import io.github.k7t3.simplefx.view.TodoView;
import io.github.k7t3.simplefx.viewmodel.TodoViewModel;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class TodoController {

    private final TodoView view;
    private final TodoViewModel viewModel;

    public TodoController(TodoView view, TodoViewModel viewModel) {
        this.view = view;
        this.viewModel = viewModel;
        setupBindings();
        setupEventHandlers();
    }

    private void setupBindings() {
        view.getListView().setItems(viewModel.getFilteredItems());
    }

    private void setupEventHandlers() {
        view.getAddButton().setOnAction(e -> addTodo());

        view.getTitleField().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addTodo();
            }
        });

        view.getDescriptionField().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addTodo();
            }
        });

        view.getListView().setCellFactory(lv -> {
            TodoListCell cell = new TodoListCell();
            cell.getDeleteButton().setOnAction(e -> {
                TodoItem item = cell.getItem();
                if (item != null) {
                    viewModel.removeTodo(item);
                    updateStatus();
                }
            });
            return cell;
        });

        view.getAllButton().setOnAction(e -> {
            viewModel.setFilter(TodoViewModel.Filter.ALL);
            updateStatus();
        });

        view.getActiveButton().setOnAction(e -> {
            viewModel.setFilter(TodoViewModel.Filter.ACTIVE);
            updateStatus();
        });

        view.getCompletedButton().setOnAction(e -> {
            viewModel.setFilter(TodoViewModel.Filter.COMPLETED);
            updateStatus();
        });

        view.getClearCompletedButton().setOnAction(e -> {
            viewModel.clearCompleted();
            updateStatus();
        });

        viewModel.getItems().addListener((javafx.collections.ListChangeListener<TodoItem>) c -> {
            Platform.runLater(this::updateStatus);
        });

        updateStatus();
    }

    private void addTodo() {
        String title = view.getTitleField().getText();
        String description = view.getDescriptionField().getText();

        if (title != null && !title.trim().isEmpty()) {
            viewModel.addTodo(title, description);
            view.getTitleField().clear();
            view.getDescriptionField().clear();
            view.getTitleField().requestFocus();
            updateStatus();
        }
    }

    private void updateStatus() {
        int active = viewModel.getActiveCount();
        int completed = viewModel.getCompletedCount();
        String text = String.format("未完了: %d / 完了: %d", active, completed);
        view.getStatusLabel().setText(text);
    }
}