package io.github.k7t3.fxgraph.mcp.agent.inspector;

import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class WindowOperationsTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private Stage stage;
    private Button button;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        button = new Button("Anchor");
        stage.setTitle("Window properties");
        stage.setScene(new Scene(new StackPane(button), 400, 300));
        stage.show();
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailsReportStageState() {
        var response = invoke("getWindowDetails", Map.of("stageId", id(stage)));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat((Map<String, Object>) response.getData()).containsKeys("x", "y", "width", "height", "opacity",
                "focused", "maximized", "iconified", "alwaysOnTop", "showing");
    }

    @Test
    void propertyChangesAreReflectedInDetails() {
        var response = invoke("setWindowProperty", Map.of(
                "stageId", id(stage), "propertyName", "x", "value", "123", "valueType", "number"));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(((Map<?, ?>) invoke("getWindowDetails", Map.of("stageId", id(stage))).getData()).get("x"))
                .isEqualTo(123.0);
    }

    @Test
    void invalidWindowPropertyIsRejectedWithoutChangingState() {
        var before = stage.getOpacity();
        var response = invoke("setWindowProperty", Map.of(
                "stageId", id(stage), "propertyName", "opacity", "value", "NaN"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("finite");
        assertThat(stage.getOpacity()).isEqualTo(before);
    }

    @Test
    void nullPreviousTitleDoesNotTurnAppliedChangeIntoFailure() {
        onFx(() -> stage.setTitle(null));

        var response = invoke("setWindowProperty", Map.of("stageId", id(stage),
                "propertyName", "title", "value", "New title"));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(((Map<?, ?>) response.getData()).get("oldValue")).isNull();
        assertThat(stage.getTitle()).isEqualTo("New title");
    }

    @Test
    void consumedCloseRequestKeepsWindowShowing() {
        var requests = new AtomicInteger();
        onFx(() -> stage.setOnCloseRequest(event -> {
            requests.incrementAndGet();
            event.consume();
        }));

        var response = invoke("closeWindow", Map.of("stageId", id(stage)));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(requests).hasValue(1);
        assertThat(((Map<?, ?>) response.getData()).get("closed")).isEqualTo(false);
        assertThat(stage.isShowing()).isTrue();
        onFx(() -> stage.setOnCloseRequest(null));
    }

    @Test
    void closeRequestRunsWindowHidingHandler() {
        var requests = new AtomicInteger();
        var hiding = new AtomicInteger();
        onFx(() -> {
            stage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> requests.incrementAndGet());
            stage.addEventHandler(WindowEvent.WINDOW_HIDING, event -> hiding.incrementAndGet());
        });

        var response = invoke("closeWindow", Map.of("stageId", id(stage)));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(requests).hasValue(1);
        assertThat(hiding).hasValue(1);
        assertThat(((Map<?, ?>) response.getData()).get("closed")).isEqualTo(true);
        assertThat(stage.isShowing()).isFalse();
    }

    @Test
    void closePopupHidesOnlySelectedPopup() {
        var popup = new ContextMenu(new MenuItem("Popup action"));
        onFx(() -> popup.show(button, javafx.geometry.Side.BOTTOM, 0, 0));
        try {
            var response = invoke("closePopup", Map.of("stageId", id(popup)));

            assertThat(response.isSuccess()).as(response.getError()).isTrue();
            assertThat(popup.isShowing()).isFalse();
            assertThat(stage.isShowing()).isTrue();
        } finally {
            onFx(popup::hide);
        }
    }

    @Test
    void closePopupRejectsNormalStage() {
        var response = invoke("closePopup", Map.of("stageId", id(stage)));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("PopupWindow");
        assertThat(stage.isShowing()).isTrue();
    }

    private AgentResponse invoke(String method, Map<String, Object> params) {
        return switch (method) {
            case "getWindowDetails" -> inspector.getWindowDetails(params);
            case "setWindowProperty" -> inspector.setWindowProperty(params);
            case "closeWindow" -> inspector.closeWindow(params);
            case "closePopup" -> inspector.closePopup(params);
            default -> throw new IllegalArgumentException(method);
        };
    }

    private String id(javafx.stage.Window window) {
        return String.valueOf(System.identityHashCode(window));
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
