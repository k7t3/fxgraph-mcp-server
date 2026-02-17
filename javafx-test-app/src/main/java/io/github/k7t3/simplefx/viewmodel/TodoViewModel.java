package io.github.k7t3.simplefx.viewmodel;

import io.github.k7t3.simplefx.model.TodoItem;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.UUID;
import java.util.function.Predicate;

public class TodoViewModel {

    private final ObservableList<TodoItem> items;
    private final FilteredList<TodoItem> filteredItems;

    public TodoViewModel() {
        this.items = FXCollections.observableArrayList();
        this.filteredItems = new FilteredList<>(items);
    }

    public ObservableList<TodoItem> getItems() {
        return items;
    }

    public FilteredList<TodoItem> getFilteredItems() {
        return filteredItems;
    }

    public void addTodo(String title, String description) {
        if (title == null || title.trim().isEmpty()) {
            return;
        }
        String id = UUID.randomUUID().toString();
        TodoItem item = new TodoItem(id, title.trim(), description != null ? description.trim() : "", false);
        items.add(0, item);
    }

    public void removeTodo(TodoItem item) {
        if (item != null) {
            items.remove(item);
        }
    }

    public void toggleCompleted(TodoItem item) {
        if (item != null) {
            item.setCompleted(!item.isCompleted());
        }
    }

    public void updateTodo(TodoItem item, String newTitle, String newDescription) {
        if (item != null && newTitle != null && !newTitle.trim().isEmpty()) {
            item.setTitle(newTitle.trim());
            item.setDescription(newDescription != null ? newDescription.trim() : "");
        }
    }

    public void setFilter(Filter filter) {
        Predicate<TodoItem> predicate = switch (filter) {
            case ALL -> null;
            case ACTIVE -> item -> !item.isCompleted();
            case COMPLETED -> item -> item.isCompleted();
        };
        filteredItems.setPredicate(predicate);
    }

    public int getActiveCount() {
        return (int) items.stream().filter(item -> !item.isCompleted()).count();
    }

    public int getCompletedCount() {
        return (int) items.stream().filter(TodoItem::isCompleted).count();
    }

    public void clearCompleted() {
        items.removeIf(TodoItem::isCompleted);
    }

    public enum Filter {
        ALL, ACTIVE, COMPLETED
    }
}