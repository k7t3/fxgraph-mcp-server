package io.github.k7t3.simplefx.lab;

import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import static io.github.k7t3.simplefx.lab.DemoSupport.*;

final class InspectionPage extends VBox {
    InspectionPage() {
        super(16);
        getStyleClass().add("demo-page");
        var target = identified("property-target", new Label("プロパティ変更の対象"));
        target.getStyleClass().add("inspection-target");
        target.setOpacity(0.85);
        target.setScaleX(1.05);
        target.setRotate(5);
        var sample = new StackPane(target);
        sample.setMinHeight(85);
        var disabled = identified("disabled-target", new Button("無効なボタン"));
        disabled.setDisable(true);
        var hidden = identified("hidden-parent", new StackPane(identified("hidden-target", new Label("親が非表示"))));
        hidden.setVisible(false);
        hidden.setPrefSize(240, 40);
        var transparent = identified("transparent-parent", new StackPane(identified("transparent-target", new Label("親が透明"))));
        transparent.setOpacity(0);
        transparent.setPrefSize(240, 40);
        var hiddenCard = card("親の visible=false / opacity=0", new Label("以下の2領域は意図的に見えません。"), hidden, transparent);

        var clipped = identified("clip-parent", new Pane());
        clipped.setPrefSize(260, 80);
        clipped.setClip(new Rectangle(120, 80));
        var partial = identified("partial-target", new Label("一部がクリップされる長いラベル"));
        partial.relocate(60, 10);
        var outside = identified("clipped-target", new Label("クリップ領域の外"));
        outside.relocate(160, 45);
        clipped.getChildren().addAll(partial, outside);
        var subRoot = identified("subscene-root", new StackPane(
                identified("subscene-target", new Label("SubScene 内のノード"))));
        var subscene = identified("inspection-subscene", new SubScene(subRoot, 420, 90));
        getChildren().addAll(
                heading("プロパティと可視性", "text・textFill・style・opacity・scaleX・rotate を変更できます。祖先経路、親による非表示、クリップ、SubScene の境界も確認できます。"),
                card("検索・ハイライト・変形", sample, disabled),
                new HBox(16, hiddenCard, card("クリップ", clipped)),
                card("SubScene の祖先経路", subscene));
    }
}
