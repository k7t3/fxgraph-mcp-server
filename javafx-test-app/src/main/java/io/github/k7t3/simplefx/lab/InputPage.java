package io.github.k7t3.simplefx.lab;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;

import static io.github.k7t3.simplefx.lab.DemoSupport.*;

final class InputPage extends VBox {
    private final Label shortcut = status("shortcut-status", "shortcut=0");
    private int shortcuts;
    private int actions;

    InputPage(EventJournal journal) {
        super(16);
        getStyleClass().add("demo-page");
        var input = identified("input-text", new TextField());
        input.setPromptText("文字列を入力、または text プロパティを設定");
        HBox.setHgrow(input, Priority.ALWAYS);
        var result = status("input-result", "送信待ち");
        Runnable submit = () -> {
            result.setText("送信: " + input.getText());
            journal.record(result.getText());
        };
        input.setOnAction(event -> submit.run());
        var check = identified("input-check", new CheckBox("boolean: 選択状態"));
        var slider = identified("input-number", new Slider(0, 100, 25));
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        var values = status("input-values", "selected=false value=25.0");
        Runnable updateValues = () -> values.setText("selected=%s value=%.1f".formatted(check.isSelected(), slider.getValue()));
        check.selectedProperty().addListener((obs, old, value) -> updateValues.run());
        slider.valueProperty().addListener((obs, old, value) -> updateValues.run());

        var mouse = status("mouse-status", "マウス入力待ち");
        var action = status("action-status", "action=0");
        var gesture = button("gesture-target", "クリック・ダブルクリック・中クリック", () -> {
            action.setText("action=" + ++actions);
            journal.record(action.getText());
        });
        gesture.addEventHandler(MouseEvent.ANY, event -> {
            if (event.getEventType() == MouseEvent.MOUSE_PRESSED
                    || event.getEventType() == MouseEvent.MOUSE_RELEASED
                    || event.getEventType() == MouseEvent.MOUSE_CLICKED) {
                var detail = "%s button=%s clickCount=%d synthesized=%s".formatted(
                        event.getEventType(), event.getButton(), event.getClickCount(), event.isSynthesized());
                mouse.setText(detail);
                journal.record(detail);
            }
        });
        var keyTarget = identified("key-target", new TextField());
        keyTarget.setPromptText("キー入力・Tab / Shift+Tab・Ctrl+K / Meta+K");
        var key = status("key-status", "キー入力待ち");
        keyTarget.addEventFilter(KeyEvent.ANY, event -> {
            var modifiers = new ArrayList<String>();
            if (event.isShiftDown()) modifiers.add("SHIFT");
            if (event.isControlDown()) modifiers.add("CTRL");
            if (event.isAltDown()) modifiers.add("ALT");
            if (event.isMetaDown()) modifiers.add("META");
            var detail = "%s code=%s character=%s modifiers=%s".formatted(event.getEventType(),
                    event.getCode(), event.getCharacter(), String.join(",", modifiers));
            key.setText(detail);
            journal.record(detail);
        });
        var focus = status("focus-status", "focus=なし");
        ChangeListener<Node> focusListener = (obs, old, node) -> focus.setText("focus="
                + (node == null ? "なし" : node.getId() != null ? node.getId() : node.getClass().getSimpleName()));
        var controlK = new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN);
        var metaK = new KeyCodeCombination(KeyCode.K, KeyCombination.META_DOWN);
        Runnable shortcutAction = () -> {
            shortcut.setText("shortcut=" + ++shortcuts);
            journal.record(shortcut.getText());
        };
        sceneProperty().addListener((obs, old, scene) -> {
            if (old != null) {
                old.focusOwnerProperty().removeListener(focusListener);
                old.getAccelerators().remove(controlK);
                old.getAccelerators().remove(metaK);
            }
            if (scene != null) {
                scene.focusOwnerProperty().addListener(focusListener);
                scene.getAccelerators().put(controlK, shortcutAction);
                scene.getAccelerators().put(metaK, shortcutAction);
            }
        });
        getChildren().addAll(
                heading("入力とイベント", "プロパティ設定、ButtonBase の起動、マウス・キーのイベント列、フォーカスを結果表示で確認します。"),
                card("文字列・boolean・number", new HBox(10, input, button("input-submit", "送信", submit)),
                        result, new HBox(16, check, slider), values),
                card("マウスとアクション", gesture, mouse, action),
                card("キーボードとフォーカス", keyTarget, key, shortcut, focus));
    }
}
