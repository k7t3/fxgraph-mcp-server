package io.github.k7t3.simplefx.lab;

import atlantafx.base.controls.ModalPane;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.github.k7t3.simplefx.lab.DemoSupport.*;

final class WindowPage extends StackPane {
    private static final ButtonType CONFIRM = new ButtonType("確定", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
    private final Stage owner;
    private final EventJournal journal;
    private final HashMap<String, Stage> stages = new HashMap<>();
    private final List<Dialog<ButtonType>> dialogs = new ArrayList<>();
    private final Label windowStatus = status("window-status", "補助ウィンドウ: 未表示");
    private final Label dialogStatus = status("dialog-status", "ダイアログ: 未表示");
    private final Label popupStatus = status("popup-status", "ポップアップ: 未表示");
    private final Label overlayStatus = status("overlay-status", "オーバーレイ: 未表示");
    private final ModalPane overlay = identified("embedded-overlay", new ModalPane());
    private final Tooltip tooltip = new Tooltip("FXGraph Tooltip: 明示的に表示したポップアップ");
    private final Popup popup = new Popup();

    WindowPage(Stage owner, EventJournal journal) {
        this.owner = owner;
        this.journal = journal;
        var page = new VBox(16);
        page.getStyleClass().add("demo-page");
        var tooltipButton = identified("show-tooltip", new Button("Tooltip を表示"));
        tooltipButton.setOnAction(event -> {
            var bounds = tooltipButton.localToScreen(tooltipButton.getBoundsInLocal());
            tooltip.show(tooltipButton, bounds.getMinX(), bounds.getMaxY() + 8);
            popupStatus.setText("Tooltip: 表示中");
            journal.record(popupStatus.getText());
        });
        tooltip.setAutoHide(false);
        tooltip.setOnHidden(event -> {
            popupStatus.setText("Tooltip: 閉じました");
            journal.record(popupStatus.getText());
        });
        popup.setAutoHide(false);
        popup.getContent().add(card("Popup の内容", identified("popup-content", new Label("独立した PopupWindow")),
                button("popup-close", "Popup を閉じる", popup::hide)));
        popup.setOnHidden(event -> {
            popupStatus.setText("Popup: 閉じました");
            journal.record(popupStatus.getText());
        });
        overlay.setPersistent(true);
        overlay.setInTransitionFactory(null);
        overlay.setOutTransitionFactory(null);
        overlay.setAlignment(Pos.CENTER);
        var overlayContent = card("AtlantaFX ModalPane", new Label("Scene 内に表示するオーバーレイです。"),
                button("overlay-close", "オーバーレイを閉じる", () -> overlay.setDisplay(false)));
        overlayContent.setMaxSize(460, 180);
        overlay.displayProperty().addListener((obs, old, showing) -> {
            overlayStatus.setText("オーバーレイ: " + (showing ? "表示中" : "閉じました"));
            journal.record(overlayStatus.getText());
        });
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && overlay.isDisplay()) {
                overlay.setDisplay(false);
                event.consume();
            }
        });
        page.getChildren().addAll(
                heading("ウィンドウとダイアログ", "補助 Stage で位置・サイズ・タイトル・状態変更と終了要求を確認します。表示した Popup / Tooltip は stages で再検索できます。"),
                card("補助 Stage と終了要求", new FlowPane(10, 10,
                        button("open-window", "通常のウィンドウ", () -> showWindow("normal")),
                        button("open-guarded-window", "終了を拒否するウィンドウ", () -> showWindow("guarded")),
                        button("open-confirm-window", "終了確認のあるウィンドウ", () -> showWindow("confirm"))), windowStatus),
                card("ダイアログ", new FlowPane(10, 10,
                        button("open-dialog", "非モーダルを表示", this::showDialog),
                        button("open-modal-dialog", "モーダルを表示して待機", this::showModalDialog)), dialogStatus),
                card("ポップアップとオーバーレイ", new FlowPane(10, 10, tooltipButton,
                        button("show-popup", "Popup を表示", () -> {
                            popup.show(owner, owner.getX() + 120, owner.getY() + 160);
                            popupStatus.setText("Popup: 表示中");
                            journal.record(popupStatus.getText());
                        }),
                        button("show-overlay", "オーバーレイを表示", () -> overlay.show(overlayContent))), popupStatus, overlayStatus));
        getChildren().addAll(page, overlay);
    }

    private void showWindow(String mode) {
        if (stages.containsKey(mode)) {
            stages.get(mode).toFront();
            return;
        }
        var stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(switch (mode) {
            case "guarded" -> "FXGraph 終了拒否ウィンドウ";
            case "confirm" -> "FXGraph 終了確認ウィンドウ";
            default -> "FXGraph 補助ウィンドウ";
        });
        var allow = identified("window-allow-close", new CheckBox("終了を許可する"));
        allow.setVisible(mode.equals("guarded"));
        allow.setManaged(mode.equals("guarded"));
        var state = status("secondary-state", "");
        var requests = new SimpleIntegerProperty();
        Runnable update = () -> state.setText("x=%.0f y=%.0f width=%.0f height=%.0f opacity=%.2f resizable=%s closeRequests=%d".formatted(
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), stage.getOpacity(), stage.isResizable(), requests.get()));
        for (var property : List.of(stage.xProperty(), stage.yProperty(), stage.widthProperty(), stage.heightProperty(),
                stage.opacityProperty(), stage.resizableProperty(), stage.maximizedProperty(), stage.iconifiedProperty(),
                stage.alwaysOnTopProperty(), requests)) {
            property.addListener(observable -> update.run());
        }
        var content = new VBox(16, heading(stage.getTitle(), "メイン画面を残したまま、この Stage の状態と終了要求を試せます。"),
                state, allow, button("secondary-close", "終了を要求", () -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST))));
        content.getStyleClass().add("demo-page");
        stage.setScene(styledScene(content, 560, 300));
        stage.setOnCloseRequest(event -> {
            requests.set(requests.get() + 1);
            journal.record(stage.getTitle() + ": WINDOW_CLOSE_REQUEST " + requests.get());
            if (mode.equals("guarded") && !allow.isSelected()) {
                event.consume();
                windowStatus.setText("終了要求を拒否しました");
            } else if (mode.equals("confirm")) {
                var dialog = dialog(stage, "FXGraph 終了確認", Modality.WINDOW_MODAL);
                if (dialog.showAndWait().orElse(CANCEL) != CONFIRM) {
                    event.consume();
                    windowStatus.setText("終了を取り消しました");
                }
            }
        });
        stage.setOnHidden(event -> {
            stages.remove(mode, stage);
            windowStatus.setText(stage.getTitle() + ": 閉じました");
            journal.record(windowStatus.getText());
        });
        stages.put(mode, stage);
        stage.show();
        update.run();
        windowStatus.setText(stage.getTitle() + ": 表示中");
        journal.record(windowStatus.getText());
    }

    private void showDialog() {
        var dialog = dialog(owner, "FXGraph 非モーダルダイアログ", Modality.NONE);
        dialog.setOnHidden(event -> {
            dialogs.remove(dialog);
            dialogStatus.setText("非モーダル結果: " + (dialog.getResult() == CONFIRM ? "確定" : "取消"));
            journal.record(dialogStatus.getText());
        });
        dialog.show();
        dialogStatus.setText("非モーダル: 表示中");
    }

    void showModalDialog() {
        var dialog = dialog(owner, "FXGraph モーダルダイアログ", Modality.WINDOW_MODAL);
        dialogStatus.setText("モーダル: 結果を待機中");
        journal.record(dialogStatus.getText());
        var result = dialog.showAndWait().orElse(CANCEL);
        dialogStatus.setText("モーダル結果: " + (result == CONFIRM ? "確定" : "取消"));
        journal.record(dialogStatus.getText());
    }

    private Dialog<ButtonType> dialog(Stage dialogOwner, String title, Modality modality) {
        var dialog = new Dialog<ButtonType>();
        dialog.initOwner(dialogOwner);
        dialog.initModality(modality);
        dialog.setTitle(title);
        var pane = dialog.getDialogPane();
        pane.setId("verification-dialog");
        pane.setContentText("確定または取消で処理を終了し、メイン画面の結果を確認してください。");
        pane.getButtonTypes().addAll(CONFIRM, CANCEL);
        pane.lookupButton(CONFIRM).setId("dialog-confirm");
        pane.lookupButton(CANCEL).setId("dialog-cancel");
        pane.getStylesheets().addAll(owner.getScene().getStylesheets());
        dialog.setOnHidden(event -> dialogs.remove(dialog));
        dialogs.add(dialog);
        return dialog;
    }

    private Scene styledScene(javafx.scene.Parent content, double width, double height) {
        var scene = new Scene(content, width, height);
        scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        return scene;
    }

    void dispose() {
        for (var dialog : List.copyOf(dialogs)) {
            dialog.setResult(CANCEL);
            dialog.close();
        }
        for (var stage : List.copyOf(stages.values())) stage.hide();
        tooltip.hide();
        popup.hide();
        overlay.setDisplay(false);
    }
}
