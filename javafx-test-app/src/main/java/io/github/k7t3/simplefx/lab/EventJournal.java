package io.github.k7t3.simplefx.lab;

import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayDeque;

import static io.github.k7t3.simplefx.lab.DemoSupport.identified;
import static io.github.k7t3.simplefx.lab.DemoSupport.status;

final class EventJournal extends VBox {
    private final ArrayDeque<String> entries = new ArrayDeque<>();
    private final TextArea log = identified("event-log", new TextArea());
    private final javafx.scene.control.Label last = status("last-event", "操作ログ");
    private int sequence;

    EventJournal() {
        super(4);
        getStyleClass().add("event-journal");
        log.setEditable(false);
        log.setWrapText(true);
        log.setPrefRowCount(2);
        log.setPrefHeight(66);
        VBox.setVgrow(log, Priority.ALWAYS);
        getChildren().addAll(last, log);
    }

    void record(String message) {
        var entry = "%03d %s".formatted(++sequence, message);
        entries.addLast(entry);
        if (entries.size() > 100) entries.removeFirst();
        last.setText(entry);
        log.setText(String.join("\n", entries));
        log.positionCaret(log.getLength());
    }

    void clear() {
        entries.clear();
        sequence = 0;
        log.clear();
        last.setText("操作ログ");
    }
}
