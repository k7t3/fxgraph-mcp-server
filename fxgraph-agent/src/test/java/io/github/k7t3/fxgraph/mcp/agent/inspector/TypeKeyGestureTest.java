package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class TypeKeyGestureTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private VBox root;
    private Stage stage;
    private final List<Stage> additionalStages = new ArrayList<>();

    @Start
    void start(Stage stage) {
        this.stage = stage;
        root = new VBox();
        stage.setScene(new Scene(root, 400, 300));
        stage.show();
    }

    @AfterEach
    void hideAdditionalStages() {
        onFx(() -> additionalStages.forEach(Stage::hide));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "A", "1", "@", "あ"})
    void syntheticInputInsertsRequestedCharacter(String character) {
        var field = new TextField();
        show(field);

        var response = inspector.typeKey(Map.of("nodeId", id(field), "key", character));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        onFx(() -> assertThat(field.getText()).isEqualTo(character));
    }

    @Test
    void shiftProducesUppercaseCharacter() {
        var field = new TextField();
        show(field);

        var response = inspector.typeKey(Map.of("nodeId", id(field), "key", "a", "modifiers", List.of("SHIFT")));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        onFx(() -> assertThat(field.getText()).isEqualTo("A"));
    }

    @Test
    void namedKeySendsPressedAndReleasedEvents() {
        var target = new Rectangle(40, 40);
        var events = recordEvents(target);
        show(target);

        var response = inspector.typeKey(Map.of("nodeId", id(target), "key", "ENTER"));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(events).extracting(KeyEvent::getEventType).containsExactly(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED);
        assertThat(events).extracting(KeyEvent::getCode).containsOnly(KeyCode.ENTER);
    }

    @Test
    void enterFiresTextFieldAction() {
        var field = new TextField();
        var actions = new AtomicInteger();
        field.setOnAction(event -> actions.incrementAndGet());
        show(field);

        var response = inspector.typeKey(Map.of("nodeId", id(field), "key", "ENTER"));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(actions).hasValue(1);
    }

    @Test
    void shiftTabMovesFocusToPreviousControl() {
        var first = new TextField();
        var second = new TextField();
        show(first, second);
        onFx(second::requestFocus);

        var response = inspector.typeKey(Map.of("nodeId", id(second), "key", "TAB",
                "modifiers", List.of("SHIFT"), "mode", "synthetic"));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        onFx(() -> assertThat(stage.getScene().getFocusOwner()).isSameAs(first));
    }

    @ParameterizedTest
    @CsvSource({"SHIFT,true,false,false,false", "CTRL,false,true,false,false", "CONTROL,false,true,false,false",
            "ALT,false,false,true,false", "META,false,false,false,true", "CMD,false,false,false,true"})
    void modifiersAndAliasesSetEventFlags(String modifier, boolean shift, boolean control, boolean alt, boolean meta) {
        var target = new Rectangle(40, 40);
        var events = recordEvents(target);
        show(target);

        var response = inspector.typeKey(Map.of("nodeId", id(target), "key", "Q", "modifiers", List.of(modifier.toLowerCase())));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        var pressed = events.stream().filter(event -> event.getEventType() == KeyEvent.KEY_PRESSED).findFirst().orElseThrow();
        assertThat(pressed.getCode()).isEqualTo(KeyCode.Q);
        assertThat(pressed.isShiftDown()).isEqualTo(shift);
        assertThat(pressed.isControlDown()).isEqualTo(control);
        assertThat(pressed.isAltDown()).isEqualTo(alt);
        assertThat(pressed.isMetaDown()).isEqualTo(meta);
    }

    @Test
    void combinedModifiersDoNotInsertShortcutCharacter() {
        var target = new Rectangle(40, 40);
        var events = recordEvents(target);
        show(target);

        var response = inspector.typeKey(Map.of("nodeId", id(target), "key", "Q", "modifiers", List.of("META", "SHIFT")));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(events).extracting(KeyEvent::getEventType).containsExactly(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED);
        assertThat(events).allMatch(event -> event.isMetaDown() && event.isShiftDown());
    }

    @Test
    void shortcutInvokesSceneAccelerator() {
        var target = new Rectangle(40, 40);
        var actions = new AtomicInteger();
        show(target);
        onFx(() -> stage.getScene().getAccelerators().put(
                new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN), actions::incrementAndGet));

        var response = inspector.typeKey(Map.of("nodeId", id(target), "key", "K", "modifiers", List.of("CTRL")));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(actions).hasValue(1);
    }

    @Test
    void metaWClosesWindowAndRunsHidingHandler() {
        var target = new TextField();
        var hiding = new AtomicInteger();
        var window = new CompletableFuture<Stage>();
        onFx(() -> {
            var secondary = new Stage();
            additionalStages.add(secondary);
            secondary.setScene(new Scene(new VBox(target), 200, 100));
            secondary.addEventHandler(WindowEvent.WINDOW_HIDING, event -> hiding.incrementAndGet());
            secondary.getScene().getAccelerators().put(
                    new KeyCodeCombination(KeyCode.W, KeyCombination.META_DOWN), secondary::close);
            secondary.show();
            window.complete(secondary);
        });

        var response = inspector.typeKey(Map.of("nodeId", id(target), "key", "W", "modifiers", List.of("CMD")));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(hiding).hasValue(1);
        onFx(() -> assertThat(window.join().isShowing()).isFalse());
    }

    @Test
    void invalidKeyDoesNotDispatchEvents() {
        assertRejected(Map.of("key", "NOT_A_KEY"), "Unsupported key");
    }

    @Test
    void invalidModifierDoesNotDispatchEvents() {
        assertRejected(Map.of("key", "Q", "modifiers", List.of("SUPER")), "Unsupported modifier");
    }

    @Test
    void modifiersMustBeAnArray() {
        assertRejected(Map.of("key", "Q", "modifiers", "META,SHIFT"), "modifiers must be an array");
    }

    @Test
    void invalidModeDoesNotDispatchEvents() {
        assertRejected(Map.of("key", "Q", "mode", "native"), "Only synthetic input is supported");
    }

    @Test
    void robotModeDoesNotDispatchEvents() {
        assertRejected(Map.of("key", "Q", "mode", "robot"), "Only synthetic input is supported");
    }

    @Test
    void undefinedKeyCodeIsRejected() {
        assertRejected(Map.of("key", "UNDEFINED"), "Unsupported key");
    }

    @Test
    void rejectedRobotModePreservesFocusAndText() {
        var first = new TextField("first");
        var second = new TextField("second");
        show(first, second);
        onFx(first::requestFocus);

        var response = inspector.typeKey(Map.of("nodeId", id(second), "key", "a", "mode", "robot"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("Only synthetic input is supported");
        onFx(() -> {
            assertThat(stage.getScene().getFocusOwner()).isSameAs(first);
            assertThat(first.getText()).isEqualTo("first");
            assertThat(second.getText()).isEqualTo("second");
        });
    }

    private void assertRejected(Map<String, Object> options, String error) {
        var target = new Rectangle(40, 40);
        var events = recordEvents(target);
        show(target);
        var params = new java.util.HashMap<>(options);
        params.put("nodeId", id(target));

        var response = inspector.typeKey(params);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains(error);
        assertThat(events).isEmpty();
    }

    private List<KeyEvent> recordEvents(Node target) {
        var events = new ArrayList<KeyEvent>();
        target.addEventFilter(KeyEvent.ANY, events::add);
        return events;
    }

    private void show(Node... nodes) {
        onFx(() -> {
            root.getChildren().setAll(nodes);
            stage.requestFocus();
            nodes[0].requestFocus();
        });
    }

    private static int id(Node node) {
        return System.identityHashCode(node);
    }

    private static void onFx(Runnable action) {
        var completion = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                action.run();
                completion.complete(null);
            } catch (Throwable error) {
                completion.completeExceptionally(error);
            }
        });
        try {
            completion.get(5, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }
}
