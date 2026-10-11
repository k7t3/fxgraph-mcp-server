package io.github.k7t3.simplefx;

import io.github.k7t3.fxgraph.mcp.agent.inspector.SceneGraphInspector;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class WorkbenchTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private Main app;
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        app = new Main();
        app.start(stage);
    }

    @AfterEach
    void dispose() {
        onFx(() -> {
            app.stop();
            return null;
        });
    }

    @Test
    void exposesPidAndEveryVerificationPage() {
        assertThat(text("app-pid")).contains(Long.toString(ProcessHandle.current().pid()));
        onFx(() -> {
            assertThat(node("workbench-tabs", TabPane.class).getTabs())
                    .extracting(tab -> tab.getId())
                    .containsExactly("input", "inspection", "windows", "menus", "scroll", "capture");
            return null;
        });
    }

    @Test
    void propertyInputAndActivationExposeApplicationResult() {
        succeeds(inspector.setProperty(params("input-text", "propertyName", "text", "value", "日本語の入力")));
        succeeds(inspector.activateNode(params("input-submit")));

        assertThat(text("input-result")).isEqualTo("送信: 日本語の入力");
    }

    @ParameterizedTest
    @CsvSource({"primary, 1, PRIMARY", "secondary, 1, SECONDARY", "middle, 1, MIDDLE", "primary, 2, PRIMARY"})
    void recordsCompleteMouseGesture(String button, int count, String expected) {
        succeeds(inspector.clickNode(params("gesture-target", "button", button, "clickCount", count)));

        assertThat(text("mouse-status")).contains("MOUSE_CLICKED", "button=" + expected,
                "clickCount=" + count, "synthesized=false");
        assertThat(text("event-log")).contains("MOUSE_PRESSED", "MOUSE_RELEASED");
    }

    @Test
    void activationCanBeDistinguishedFromMouseInput() {
        succeeds(inspector.activateNode(params("gesture-target")));

        assertThat(text("action-status")).isEqualTo("action=1");
        assertThat(text("mouse-status")).isEqualTo("マウス入力待ち");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SHIFT", "CTRL", "ALT", "META"})
    void recordsKeyModifiers(String modifier) {
        succeeds(inspector.typeKey(params("key-target", "key", "K", "modifiers", List.of(modifier))));

        assertThat(text("key-status")).contains("KEY_RELEASED", "code=K", "modifiers=" + modifier);
        if (modifier.equals("CTRL") || modifier.equals("META")) {
            assertThat(text("shortcut-status")).isEqualTo("shortcut=1");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"hidden-target", "transparent-target", "clipped-target"})
    void visibilityFixturesRemainSearchableButAreExcludedByVisibleOnly(String target) {
        selectPage("inspection");
        var all = inspector.findNodes(Map.of("id", target));
        var visible = inspector.findNodes(Map.of("id", target, "effectiveVisible", true));

        succeeds(all);
        succeeds(visible);
        assertThat((List<?>) all.getData()).hasSize(1);
        assertThat((List<?>) visible.getData()).isEmpty();
    }

    @Test
    void subsceneFixtureExposesAncestorBoundary() {
        selectPage("inspection");
        var response = inspector.getNodeDetails(params("subscene-target", "includeAncestors", true,
                "propertyFilter", List.of("text")));

        succeeds(response);
        assertThat((List<?>) ((Map<?, ?>) response.getData()).get("ancestors"))
                .anyMatch(ancestor -> "SubScene".equals(((Map<?, ?>) ancestor).get("type")));
    }

    @ParameterizedTest
    @CsvSource({"open-window, true", "open-guarded-window, false"})
    void secondaryWindowCloseRequestHasObservableOutcome(String opener, boolean closes) {
        selectPage("windows");
        activate(opener);
        var secondary = secondaryStage();

        var response = inspector.closeWindow(Map.of("stageId", id(secondary)));

        succeeds(response);
        assertThat(((Map<?, ?>) response.getData()).get("closed")).isEqualTo(closes);
        assertThat(text("window-status")).contains(closes ? "閉じました" : "拒否");
        assertThat(onFx(secondary::isShowing)).isEqualTo(!closes);
    }

    @Test
    void modalHandlerCanBeDismissedThenItsFinalResultVerified() {
        selectPage("windows");
        var response = inspector.activateNode(params("open-modal-dialog"));

        succeeds(response);
        assertThat(((Map<?, ?>) response.getData()).get("handlerPending")).isEqualTo(true);
        var confirm = onFx(() -> Window.getWindows().stream().filter(Window::isShowing)
                .filter(window -> window != stage).map(window -> window.getScene().lookup("#dialog-confirm"))
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow());
        succeeds(inspector.activateNode(Map.of("nodeId", System.identityHashCode(confirm))));
        assertThat(text("dialog-status")).isEqualTo("モーダル結果: 確定");
    }

    @Test
    void tooltipCanBeOpenedAndClosedWithoutHover() {
        selectPage("windows");
        activate("show-tooltip");
        var popup = onFx(() -> Window.getWindows().stream().filter(Window::isShowing)
                .filter(window -> window instanceof javafx.scene.control.Tooltip).findFirst().orElseThrow());

        succeeds(inspector.closePopup(Map.of("stageId", id(popup))));

        assertThat(onFx(popup::isShowing)).isFalse();
        assertThat(text("popup-status")).isEqualTo("Tooltip: 閉じました");
    }

    @Test
    void embeddedOverlayHasApplicationCloseAction() {
        selectPage("windows");
        activate("show-overlay");
        assertThat(onFx(() -> node("embedded-overlay", atlantafx.base.controls.ModalPane.class).isDisplay())).isTrue();

        activate("overlay-close");

        assertThat(onFx(() -> node("embedded-overlay", atlantafx.base.controls.ModalPane.class).isDisplay())).isFalse();
        assertThat(text("overlay-status")).isEqualTo("オーバーレイ: 閉じました");
    }

    @Test
    void contextMenuExposesDisabledItemAndNestedAction() {
        selectPage("menus");
        succeeds(inspector.clickNode(params("context-target", "button", "secondary")));
        var disabled = menuNode("実行不可");
        var rejected = inspector.clickNode(Map.of("nodeId", System.identityHashCode(disabled)));
        assertThat(rejected.isSuccess()).isFalse();
        assertThat(rejected.getError()).contains("disabled");
        var submenu = menuNode("詳細メニュー");
        succeeds(inspector.clickNode(Map.of("nodeId", System.identityHashCode(submenu))));
        var nested = menuNode("入れ子のアクション");
        succeeds(inspector.clickNode(Map.of("nodeId", System.identityHashCode(nested))));
        assertThat(text("menu-status")).isEqualTo("メニュー実行: 入れ子のアクション");
    }

    @ParameterizedTest
    @CsvSource({"scroll-list, List item 080", "scroll-table, Table row 080",
            "flowless-flow, Flowless item 080", "flowless-wrapper, Wrapped item 080"})
    void virtualScrollRevealsPreviouslyOffscreenContent(String container, String expected) {
        selectPage("scroll");
        var before = inspector.findNodes(Map.of("text", expected, "effectiveVisible", true));
        succeeds(before);
        assertThat((List<?>) before.getData()).isEmpty();

        succeeds(inspector.scrollToIndex(params(container, "index", 80)));

        var after = inspector.findNodes(Map.of("text", expected, "effectiveVisible", true));
        succeeds(after);
        assertThat((List<?>) after.getData()).isNotEmpty();
    }

    @Test
    void twoDimensionalScrollPaneSupportsBothAxes() {
        selectPage("scroll");
        succeeds(inspector.scrollNode(params("scroll-pane", "align", "bottom")));
        succeeds(inspector.scrollNode(params("scroll-pane", "align", "right")));

        onFx(() -> {
            var pane = node("scroll-pane", javafx.scene.control.ScrollPane.class);
            assertThat(pane.getVvalue()).isEqualTo(pane.getVmax());
            assertThat(pane.getHvalue()).isEqualTo(pane.getHmax());
            return null;
        });
    }

    @Test
    void captureTargetSupportsOriginalAndScaledScreenshot(@TempDir Path dir) {
        selectPage("capture");
        var original = inspector.takeScreenshot(params("capture-target", "savePath", dir.resolve("original.png").toString()));
        var scaled = inspector.takeScreenshot(params("capture-target", "savePath", dir.resolve("scaled.png").toString(),
                "maxWidth", 320, "maxHeight", 150));

        succeeds(original);
        succeeds(scaled);
        assertThat(((Map<?, ?>) original.getData()).get("width")).isEqualTo(640);
        assertThat(((Map<?, ?>) original.getData()).get("height")).isEqualTo(300);
        assertThat(((Map<?, ?>) original.getData()).get("scaled")).isEqualTo(false);
        assertThat(((Map<?, ?>) scaled.getData()).get("width")).isEqualTo(320);
        assertThat(((Map<?, ?>) scaled.getData()).get("height")).isEqualTo(150);
        assertThat(((Map<?, ?>) scaled.getData()).get("scaled")).isEqualTo(true);
        assertThat(dir.resolve("original.png")).exists();
        assertThat(dir.resolve("scaled.png")).exists();
    }

    @Test
    void globalResetDisposesWindowsAndRestoresCaptureAndInput() {
        selectPage("windows");
        activate("open-window");
        selectPage("capture");
        activate("animation-start");
        activate("workbench-reset");

        assertThat(onFx(() -> Window.getWindows().stream().filter(Window::isShowing).count())).isEqualTo(1);
        assertThat(text("input-result")).isEqualTo("送信待ち");
        selectPage("capture");
        assertThat(text("animation-status")).isEqualTo("アニメーション: 停止");
    }

    private void selectPage(String id) {
        onFx(() -> {
            var tabs = node("workbench-tabs", TabPane.class);
            tabs.getSelectionModel().select(tabs.getTabs().stream()
                    .filter(tab -> id.equals(tab.getId())).findFirst().orElseThrow());
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return null;
        });
    }

    private void activate(String id) {
        succeeds(inspector.activateNode(params(id)));
        onFx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return null;
        });
    }

    private Map<String, Object> params(String id, Object... pairs) {
        var params = new java.util.HashMap<String, Object>();
        var response = inspector.findNodes(Map.of("id", id));
        succeeds(response);
        var matches = (List<?>) response.getData();
        assertThat(matches).as("確認用ノード #%s", id).hasSize(1);
        params.put("nodeId", ((Map<?, ?>) matches.getFirst()).get("nodeId"));
        for (int i = 0; i < pairs.length; i += 2) params.put((String) pairs[i], pairs[i + 1]);
        return params;
    }

    private <T extends Node> T node(String id, Class<T> type) {
        var found = stage.getScene().lookup("#" + id);
        assertThat(found).as("確認用ノード #%s", id).isNotNull();
        return type.cast(found);
    }

    private String text(String id) {
        return onFx(() -> {
            var target = node(id, Node.class);
            return target instanceof Label label ? label.getText() : ((javafx.scene.control.TextArea) target).getText();
        });
    }

    private Stage secondaryStage() {
        return onFx(() -> Window.getWindows().stream().filter(Window::isShowing)
                .filter(window -> window instanceof Stage && window != stage)
                .map(Stage.class::cast).findFirst().orElseThrow());
    }

    private Node menuNode(String text) {
        return onFx(() -> Window.getWindows().stream().filter(Window::isShowing)
                .flatMap(window -> window.getScene().getRoot().lookupAll(".menu-item").stream())
                .filter(node -> node.getProperties().get(MenuItem.class) instanceof MenuItem item
                        && text.equals(item.getText())).findFirst().orElseThrow());
    }

    private String id(Window window) {
        return Integer.toString(System.identityHashCode(window));
    }

    private void succeeds(AgentResponse response) {
        assertThat(response.isSuccess()).as(response.getError()).isTrue();
    }

    private <T> T onFx(Callable<T> action) {
        try {
            return WaitForAsyncUtils.asyncFx(action).get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
