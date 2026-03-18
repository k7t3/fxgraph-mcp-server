package io.github.k7t3.simplefx.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;
import java.util.Objects;

public class TodoItem {

    private final String id;
    private final StringProperty title;
    private final StringProperty description;
    private final BooleanProperty completed;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public TodoItem(String id, String title) {
        this(id, title, "", false);
    }

    public TodoItem(String id, String title, String description, boolean completed) {
        this.id = Objects.requireNonNull(id);
        this.title = new SimpleStringProperty(title);
        this.description = new SimpleStringProperty(description);
        this.completed = new SimpleBooleanProperty(completed);
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public String getDescription() {
        return description.get();
    }

    public void setDescription(String description) {
        this.description.set(description);
    }

    public BooleanProperty completedProperty() {
        return completed;
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public void setCompleted(boolean completed) {
        this.completed.set(completed);
        this.completedAt = completed ? LocalDateTime.now() : null;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TodoItem todoItem = (TodoItem) o;
        return Objects.equals(id, todoItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}