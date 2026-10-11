package io.github.k7t3.simplefx.lab;

import javafx.geometry.Side;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.VBox;

import static io.github.k7t3.simplefx.lab.DemoSupport.*;

final class MenuPage extends VBox {
    private final EventJournal journal;
    private final Runnable modalAction;
    private final Label result = status("menu-status", "メニュー実行待ち");
    private final ContextMenu context;
    private final MenuButton menuButton;
    private final Menu menu;

    MenuPage(EventJournal journal, Runnable modalAction) {
        super(16);
        this.journal = journal;
        this.modalAction = modalAction;
        getStyleClass().add("demo-page");
        context = new ContextMenu(items());
        var target = identified("context-target", new Label("ここを右クリック"));
        target.getStyleClass().add("context-target");
        target.setContextMenu(context);
        menuButton = identified("menu-button", new MenuButton("MenuButton を開く", null, items()));
        menu = new Menu("確認メニュー", null, items());
        var bar = identified("menu-bar", new MenuBar(menu));
        // macOS のネイティブメニューではなく、検査できる Scene 内のメニューを使う。
        bar.setUseSystemMenuBar(false);
        getChildren().addAll(
                heading("メニューとサブメニュー", "ContextMenu・MenuButton・MenuBar に同じ確認項目を用意しています。開いた後に stages を再取得し、描画ノードを検索してください。"),
                card("右クリックと明示的な表示", target,
                        button("open-context-menu", "ContextMenu を開く", () -> context.show(target, Side.BOTTOM, 0, 4))),
                card("MenuButton / MenuBar", menuButton, bar),
                result);
    }

    private MenuItem[] items() {
        var disabled = action("実行不可");
        disabled.setDisable(true);
        var check = new CheckMenuItem("チェック項目");
        check.setOnAction(event -> record("チェック項目 selected=" + check.isSelected()));
        var nested = new Menu("詳細メニュー", null, action("入れ子のアクション"),
                new Menu("さらに深いメニュー", null, action("深いアクション")));
        var modal = new MenuItem("モーダルを開く");
        modal.setOnAction(event -> {
            record("モーダルを開く");
            modalAction.run();
        });
        return new MenuItem[] {action("通常のアクション"), disabled, check,
                new SeparatorMenuItem(), nested, modal};
    }

    private MenuItem action(String text) {
        var item = new MenuItem(text);
        item.setOnAction(event -> record(text));
        return item;
    }

    private void record(String text) {
        result.setText("メニュー実行: " + text);
        journal.record(result.getText());
    }

    void dispose() {
        context.hide();
        menuButton.hide();
        menu.hide();
    }
}
