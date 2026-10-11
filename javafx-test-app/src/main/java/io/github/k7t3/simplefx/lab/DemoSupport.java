package io.github.k7t3.simplefx.lab;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

final class DemoSupport {
    private DemoSupport() {}

    static <T extends Node> T identified(String id, T node) {
        node.setId(id);
        return node;
    }

    static Button button(String id, String text, Runnable action) {
        var button = identified(id, new Button(text));
        button.setOnAction(event -> action.run());
        return button;
    }

    static Label status(String id, String text) {
        var label = identified(id, new Label(text));
        label.getStyleClass().add("demo-status");
        label.setWrapText(true);
        return label;
    }

    static VBox heading(String title, String description) {
        var label = new Label(title);
        label.getStyleClass().add("demo-heading");
        var help = new Label(description);
        help.setWrapText(true);
        help.getStyleClass().add("demo-help");
        return new VBox(6, label, help);
    }

    static VBox card(String title, Node... nodes) {
        var card = new VBox(10, new Label(title));
        card.getChildren().addAll(nodes);
        card.getStyleClass().add("demo-card");
        return card;
    }
}
