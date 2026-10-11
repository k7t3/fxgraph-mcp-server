package io.github.k7t3.simplefx.lab;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import static io.github.k7t3.simplefx.lab.DemoSupport.*;

final class CapturePage extends VBox {
    private final Timeline animation;

    CapturePage(EventJournal journal) {
        super(16);
        getStyleClass().add("demo-page");
        var canvas = identified("capture-target", new Pane());
        canvas.setMinSize(640, 300);
        canvas.setPrefSize(640, 300);
        canvas.setMaxSize(640, 300);
        canvas.setClip(new Rectangle(640, 300));
        canvas.getStyleClass().add("capture-canvas");
        for (int x = 40; x <= 600; x += 40) {
            var line = new Line(x, 0, x, 300);
            line.setStroke(Color.web("#434c5e"));
            canvas.getChildren().add(line);
        }
        for (int y = 40; y < 300; y += 40) {
            var line = new Line(0, y, 640, y);
            line.setStroke(Color.web("#434c5e"));
            canvas.getChildren().add(line);
        }
        var caption = identified("capture-caption", new Label("FXGraph 640 × 300 / PNG・MP4 確認用"));
        caption.relocate(20, 18);
        var marker = identified("animation-marker", new Circle(50, 155, 24, Color.web("#88c0d0")));
        canvas.getChildren().addAll(caption, marker);
        animation = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(marker.translateXProperty(), 0)),
                new KeyFrame(Duration.seconds(2), new KeyValue(marker.translateXProperty(), 540)));
        animation.setAutoReverse(true);
        animation.setCycleCount(Animation.INDEFINITE);
        var state = status("animation-status", "アニメーション: 停止");
        animation.statusProperty().addListener((obs, old, value) -> {
            state.setText("アニメーション: " + switch (value) {
                case RUNNING -> "再生中";
                case PAUSED -> "一時停止";
                case STOPPED -> "停止";
            });
            journal.record(state.getText());
        });
        getChildren().addAll(
                heading("画像と動画", "capture-target は 640 × 300 の固定領域です。原寸 PNG、縮小 PNG、再生中の MP4 を比較できます。ウィンドウやポップアップは別々に撮影します。"),
                new HBox(10, button("animation-start", "再生", animation::play),
                        button("animation-pause", "一時停止", animation::pause),
                        button("animation-stop", "停止・先頭に戻す", () -> {
                            animation.stop();
                            marker.setTranslateX(0);
                        })), state, canvas);
    }

    void dispose() {
        animation.stop();
    }
}
