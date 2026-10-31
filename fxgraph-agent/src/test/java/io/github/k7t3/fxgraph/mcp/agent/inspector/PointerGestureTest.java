package io.github.k7t3.fxgraph.mcp.agent.inspector;

import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(ApplicationExtension.class)
class PointerGestureTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private VBox root;

    @Start
    void start(Stage stage) {
        root = new VBox();
        stage.setScene(new Scene(root, 400, 300));
        stage.show();
    }

    @Test
    void clickUsesLocalOffsetsOnTranslatedScaledNode() {
        var node = new Rectangle(100, 60);
        node.setTranslateX(40);
        node.setTranslateY(30);
        node.setScaleX(1.5);
        var events = new ArrayList<MouseEvent>();
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, events::add);
        show(node);

        var response = inspector.clickNode(params(node, Map.of("x", 12.5, "y", 7.0)));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getX()).isCloseTo(12.5, within(0.01));
            assertThat(event.getY()).isCloseTo(7, within(0.01));
        });
    }

    @Test
    void dragDeliversOrderedEventsAndKeepsButtonDownUntilRelease() {
        var node = new Rectangle(100, 60);
        node.setTranslateX(30);
        var events = new ArrayList<MouseEvent>();
        node.addEventHandler(MouseEvent.ANY, events::add);
        show(node);

        var response = drag(node, Map.of("fromX", 10, "fromY", 20, "toX", 150, "toY", 40, "steps", 2));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(events).extracting(MouseEvent::getEventType).containsExactly(
                MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_RELEASED);
        assertThat(events).extracting(MouseEvent::getX).containsExactly(10.0, 80.0, 150.0, 150.0);
        assertThat(events).extracting(MouseEvent::isPrimaryButtonDown).containsExactly(true, true, true, false);
        assertThat(events).allMatch(event -> !event.isSynthesized());
        assertThat(events.get(1).isStillSincePress()).isFalse();
    }

    @Test
    void coordinateClickHitsSliderTrackAndDragMovesThumb() {
        var slider = new Slider(0, 100, 0);
        show(slider);
        var response = inspector.clickNode(params(slider, Map.of("x", 300, "y", slider.getHeight() / 2)));
        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        onFx(() -> assertThat(slider.getValue()).isGreaterThan(60));
        onFx(() -> slider.setValue(0));

        var thumb = onFxValue(() -> slider.lookup(".thumb"));
        var bounds = onFxValue(thumb::getBoundsInLocal);
        response = drag(thumb, Map.of("fromX", bounds.getWidth() / 2, "fromY", bounds.getHeight() / 2,
                "toX", bounds.getWidth() / 2 + 150, "toY", bounds.getHeight() / 2));
        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        onFx(() -> assertThat(slider.getValue()).isGreaterThan(25));
    }

    @Test
    void dragMovesScrollBarThumb() {
        var bar = new ScrollBar();
        bar.setOrientation(Orientation.HORIZONTAL);
        bar.setMax(100);
        bar.setVisibleAmount(10);
        show(bar);
        var thumb = onFxValue(() -> bar.lookup(".thumb"));
        var bounds = onFxValue(thumb::getBoundsInLocal);

        var response = drag(thumb, Map.of("fromX", bounds.getWidth() / 2, "fromY", bounds.getHeight() / 2,
                "toX", bounds.getWidth() / 2 + 120, "toY", bounds.getHeight() / 2));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        onFx(() -> assertThat(bar.getValue()).isGreaterThan(20));
    }

    @Test
    void dragSelectsTextInTextField() {
        var field = new TextField("abcdefghijklmnopqrstuvwxyz");
        show(field);
        var response = drag(field, Map.of("fromX", 6, "fromY", field.getHeight() / 2,
                "toX", 130, "toY", field.getHeight() / 2));
        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        onFx(() -> assertThat(field.getSelectedText()).isNotEmpty());
    }

    @Test
    void invalidParametersNeverDispatchPartialGesture() {
        var node = new Rectangle(100, 60);
        var events = new ArrayList<MouseEvent>();
        node.addEventFilter(MouseEvent.ANY, events::add);
        show(node);
        for (var invalid : List.<Map<String, Object>>of(Map.of("x", 5), Map.of("x", -1, "y", 0),
                Map.of("x", Double.NaN, "y", 5), Map.of("x", 101, "y", 0))) {
            assertThat(inspector.clickNode(params(node, invalid)).isSuccess()).as(invalid.toString()).isFalse();
        }
        for (var invalid : List.<Map<String, Object>>of(Map.of("steps", 0), Map.of("steps", 121),
                Map.of("steps", 1.5), Map.of("toX", Double.POSITIVE_INFINITY), Map.of("mode", "robot"))) {
            var args = new LinkedHashMap<String, Object>(Map.of("fromX", 10, "fromY", 10, "toX", 90, "toY", 40));
            args.putAll(invalid);
            assertThat(drag(node, args).isSuccess()).as(invalid.toString()).isFalse();
        }
        assertThat(events).isEmpty();
    }

    @Test
    void coordinatePickingRespectsViewOrder() {
        var front = new Rectangle(100, 60);
        front.setViewOrder(-1);
        var behind = new Rectangle(100, 60);
        var selected = new ArrayList<String>();
        front.setOnMouseClicked(event -> selected.add("front"));
        behind.setOnMouseClicked(event -> selected.add("behind"));
        var stack = new javafx.scene.layout.StackPane(front, behind);
        stack.setMaxSize(100, 60);
        show(stack);

        var response = inspector.clickNode(params(stack, Map.of("x", 20, "y", 20)));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(selected).containsExactly("front");
    }

    @Test
    void disabledOrHiddenNodeCannotBeDragged() {
        var node = new Rectangle(100, 60);
        var events = new ArrayList<MouseEvent>();
        node.addEventFilter(MouseEvent.ANY, events::add);
        show(node);
        onFx(() -> node.setDisable(true));
        assertThat(drag(node, Map.of("fromX", 5, "fromY", 5, "toX", 90, "toY", 30)).isSuccess()).isFalse();
        onFx(() -> { node.setDisable(false); node.setVisible(false); });
        assertThat(drag(node, Map.of("fromX", 5, "fromY", 5, "toX", 90, "toY", 30)).isSuccess()).isFalse();
        assertThat(events).isEmpty();
    }

    private AgentResponse drag(Node node, Map<String, Object> options) {
        return inspector.dragNode(params(node, options));
    }

    private Map<String, Object> params(Node node, Map<String, Object> options) {
        var params = new LinkedHashMap<>(options);
        params.put("nodeId", System.identityHashCode(node));
        return params;
    }

    private void show(Node node) {
        onFx(() -> {
            root.getChildren().setAll(node);
            root.applyCss();
            root.layout();
        });
    }

    private static void onFx(Runnable action) {
        onFxValue(() -> { action.run(); return null; });
    }

    private static <T> T onFxValue(java.util.function.Supplier<T> action) {
        var future = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { future.complete(action.get()); }
            catch (Throwable e) { future.completeExceptionally(e); }
        });
        try { return future.get(5, TimeUnit.SECONDS); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
