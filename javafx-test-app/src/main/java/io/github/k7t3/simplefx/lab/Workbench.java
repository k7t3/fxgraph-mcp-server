package io.github.k7t3.simplefx.lab;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import static io.github.k7t3.simplefx.lab.DemoSupport.*;

/**
 * 機能別の確認画面を提供する。画面操作、再初期化、破棄は FX スレッドで行う。
 * 初期化し直すと補助ウィンドウ、ポップアップ、動画確認用アニメーションを破棄する。
 */
public final class Workbench extends BorderPane implements AutoCloseable {
    private final Stage owner;
    private final EventJournal journal = new EventJournal();
    private WindowPage windows;
    private MenuPage menus;
    private ScrollPage scroll;
    private CapturePage capture;

    /** @param owner 補助ウィンドウとダイアログの所有者となるメイン Stage */
    public Workbench(Stage owner) {
        this.owner = owner;
        setId("workbench");
        var title = new Label("FXGraph 機能検証");
        title.getStyleClass().add("app-title");
        var pid = status("app-pid", "PID: " + ProcessHandle.current().pid());
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(16, title, pid, spacer,
                button("workbench-reset", "初期状態に戻す", this::reset));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("workbench-header");
        setTop(header);
        setBottom(journal);
        reset();
    }

    private void reset() {
        close();
        setCenter(null);
        journal.clear();
        windows = new WindowPage(owner, journal);
        menus = new MenuPage(journal, windows::showModalDialog);
        scroll = new ScrollPage(journal);
        capture = new CapturePage(journal);
        var tabs = identified("workbench-tabs", new TabPane());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab("input", "入力・イベント", new InputPage(journal)),
                tab("inspection", "プロパティ・可視性", new InspectionPage()),
                tab("windows", "ウィンドウ", windows),
                tab("menus", "メニュー", menus),
                tab("scroll", "スクロール", scroll),
                tab("capture", "画像・動画", capture));
        setCenter(tabs);
        journal.record("初期状態に戻しました。タブの見出しから確認対象を選んでください。");
    }

    private Tab tab(String id, String text, Node content) {
        // Tab は Node ではないため、クリック対象を見出しの Label に持たせる。
        var tab = new Tab();
        tab.setId(id);
        tab.setGraphic(identified("tab-" + id, new Label(text)));
        tab.setContent(content);
        return tab;
    }

    /** 補助画面のリソースを解放する。複数回呼んでもよい。 */
    @Override
    public void close() {
        if (windows != null) windows.dispose();
        if (menus != null) menus.dispose();
        if (scroll != null) scroll.dispose();
        if (capture != null) capture.dispose();
    }
}
