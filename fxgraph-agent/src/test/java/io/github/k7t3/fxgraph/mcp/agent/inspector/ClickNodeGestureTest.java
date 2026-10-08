package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.control.TextField;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class ClickNodeGestureTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private final List<ContextMenu> menus = new ArrayList<>();
    private VBox root;
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        root = new VBox();
        stage.setScene(new Scene(root, 400, 300));
        stage.show();
    }

    @AfterEach
    void hideMenus() {
        onFx(() -> menus.forEach(ContextMenu::hide));
    }

    @ParameterizedTest
    @ValueSource(strings = {"secondary", "middle"})
    void syntheticClickUsesRequestedButton(String button) {
        var rectangle = new Rectangle(40, 40);
        var events = new ArrayList<MouseEvent>();
        rectangle.addEventHandler(MouseEvent.ANY, events::add);
        show(rectangle);

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(rectangle), "button", button));

        assertThat(response.isSuccess()).isTrue();
        assertThat(events).extracting(MouseEvent::getEventType).containsExactly(
                MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED);
        var expectedButton = MouseButton.valueOf(button.toUpperCase(java.util.Locale.ROOT));
        assertThat(events).allMatch(event -> event.getButton() == expectedButton);
        assertThat(events.getFirst().isSecondaryButtonDown()).isEqualTo(expectedButton == MouseButton.SECONDARY);
        assertThat(events.getFirst().isMiddleButtonDown()).isEqualTo(expectedButton == MouseButton.MIDDLE);
        assertThat(events).allMatch(event -> !event.isPrimaryButtonDown());
        assertThat(events.subList(1, 3)).allMatch(event ->
                !event.isSecondaryButtonDown() && !event.isMiddleButtonDown());
        assertThat(events.get(1).isPopupTrigger()).isEqualTo(expectedButton == MouseButton.SECONDARY);
    }

    @Test
    void doubleClickSendsTwoCompleteGesturesWithIncreasingCounts() {
        var rectangle = new Rectangle(40, 40);
        var events = new ArrayList<MouseEvent>();
        rectangle.addEventHandler(MouseEvent.ANY, events::add);
        show(rectangle);

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(rectangle), "clickCount", 2));

        assertThat(response.isSuccess()).isTrue();
        assertThat(events).extracting(MouseEvent::getEventType).containsExactly(
                MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED,
                MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED);
        assertThat(events).extracting(MouseEvent::getClickCount).containsExactly(1, 1, 1, 2, 2, 2);
    }

    @Test
    void syntheticClickIsAcceptedByHandlersThatIgnoreTouchInput() {
        var rectangle = new Rectangle(40, 40);
        var accepted = new AtomicInteger();
        rectangle.setOnMouseClicked(event -> {
            if (!event.isSynthesized()) accepted.incrementAndGet();
        });
        show(rectangle);

        var response = inspector.clickNode(Map.of("nodeId", System.identityHashCode(rectangle)));

        assertThat(response.isSuccess()).isTrue();
        assertThat(accepted).hasValue(1);
    }

    @Test
    void syntheticClickOpensSubmenu() {
        var submenu = new Menu("Submenu", null, new MenuItem("Nested action"));
        var button = new MenuButton("Options", null, submenu);
        show(button);
        var container = new AtomicReference<Node>();
        onFx(() -> {
            button.show();
            var popup = (ContextMenu) javafx.stage.Window.getWindows().stream()
                    .filter(w -> w instanceof ContextMenu && w.isShowing()).findFirst().orElseThrow();
            menus.add(popup);
            popup.getScene().getRoot().applyCss();
            popup.getScene().getRoot().layout();
            container.set(popup.getScene().getRoot().lookupAll(".menu-item").stream()
                    .filter(n -> n.getProperties().get(MenuItem.class) == submenu).findFirst().orElseThrow());
        });

        var response = inspector.clickNode(Map.of("nodeId", System.identityHashCode(container.get())));

        assertThat(response.isSuccess()).isTrue();
        onFx(() -> assertThat(submenu.isShowing()).isTrue());
    }

    @Test
    void menuActionOpeningModalDialogReturnsBeforeDialogIsDismissed() {
        var dialog = new AtomicReference<Dialog<Void>>();
        var actions = new AtomicInteger();
        var item = new MenuItem("Open dialog");
        item.setOnAction(event -> {
            actions.incrementAndGet();
            var modal = new Dialog<Void>();
            modal.initOwner(stage);
            modal.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.set(modal);
            var timeout = new PauseTransition(Duration.seconds(12));
            timeout.setOnFinished(e -> modal.close());
            timeout.play();
            modal.showAndWait();
            timeout.stop();
        });
        var anchor = new Button("Menu");
        var menu = new ContextMenu(item);
        menus.add(menu);
        anchor.setContextMenu(menu);
        show(anchor);
        var container = new AtomicReference<Node>();
        onFx(() -> {
            menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
            menu.getScene().getRoot().applyCss();
            menu.getScene().getRoot().layout();
            container.set(menu.getScene().getRoot().lookup(".menu-item"));
        });

        try {
            var response = inspector.clickNode(Map.of("nodeId", System.identityHashCode(container.get())));

            assertThat(response.isSuccess()).as(response.getError()).isTrue();
            assertThat(actions).hasValue(1);
            onFx(() -> assertThat(dialog.get().isShowing()).isTrue());
        } finally {
            onFx(() -> { if (dialog.get() != null) dialog.get().close(); });
        }
    }

    @Test
    void doubleClickStartsNameEditing() {
        var name = new TextField("Original name");
        name.setEditable(false);
        name.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                name.setEditable(true);
                name.selectAll();
            }
        });
        show(name);

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(name), "clickCount", 2));

        assertThat(response.isSuccess()).isTrue();
        assertThat(name.isEditable()).isTrue();
        assertThat(name.getSelectedText()).isEqualTo("Original name");
    }

    @Test
    @SuppressWarnings("unchecked")
    void secondaryClickOpensContextMenuAndExposesDisabledItemState() {
        var anchor = new Button("Menu");
        var disabledItem = new MenuItem("Unavailable");
        disabledItem.setDisable(true);
        var menu = new ContextMenu(new MenuItem("Available"), disabledItem);
        menus.add(menu);
        anchor.setContextMenu(menu);
        var actions = new AtomicInteger();
        anchor.setOnAction(event -> actions.incrementAndGet());
        show(anchor);

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(anchor), "button", "secondary"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(menu.isShowing()).isTrue();
        assertThat(actions).hasValue(0);
        onFx(() -> {
            var containers = menu.getScene().getRoot().lookupAll(".menu-item");
            assertThat(containers).hasSize(2);
            for (var container : containers) {
                var item = (MenuItem) container.getProperties().get(MenuItem.class);
                var details = inspector.getNodeDetails(Map.of("nodeId", System.identityHashCode(container)));
                var node = (Map<String, Object>) ((Map<String, Object>) details.getData()).get("node");
                assertThat(node.getOrDefault("disabled", false)).isEqualTo(item.isDisable());
            }
        });
    }

    @Test
    void consumedContextMenuRequestSuppressesDefaultMenu() {
        var anchor = new Button("Menu");
        var menu = new ContextMenu(new MenuItem("Default action"));
        menus.add(menu);
        anchor.setContextMenu(menu);
        var requests = new AtomicInteger();
        anchor.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            requests.incrementAndGet();
            event.consume();
        });
        show(anchor);

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(anchor), "button", "secondary"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(requests).hasValue(1);
        assertThat(menu.isShowing()).isFalse();
    }

    @Test
    void secondaryClickRequestsContextMenuOnce() {
        var rectangle = new Rectangle(40, 40);
        var requests = new ArrayList<ContextMenuEvent>();
        rectangle.setOnContextMenuRequested(requests::add);
        show(rectangle);

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(rectangle), "button", "secondary"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().isKeyboardTrigger()).isFalse();
        assertThat(requests.getFirst().getX()).isEqualTo(20);
        assertThat(requests.getFirst().getY()).isEqualTo(20);
    }

    @ParameterizedTest
    @ValueSource(strings = {"robot", "native"})
    void unsupportedModeDoesNotDispatchEvents(String mode) {
        var rectangle = new Rectangle(40, 40);
        var events = new ArrayList<MouseEvent>();
        rectangle.addEventFilter(MouseEvent.ANY, events::add);
        show(rectangle);

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(rectangle), "mode", mode, "clickCount", 2));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("Only synthetic input is supported");
        assertThat(events).isEmpty();
    }

    @Test
    void invalidClickParametersDoNotDispatchEvents() {
        var rectangle = new Rectangle(40, 40);
        var clicks = new AtomicInteger();
        rectangle.setOnMouseClicked(event -> clicks.incrementAndGet());
        show(rectangle);
        var nodeId = System.identityHashCode(rectangle);

        for (var button : List.of("unknown", "none", 1)) {
            var response = inspector.clickNode(Map.of("nodeId", nodeId, "button", button));
            assertThat(response.isSuccess()).as("button=%s", button).isFalse();
            assertThat(response.getError()).contains("button");
        }
        for (var count : List.of(0, -1, 3, 1.5, "2", 2_147_483_649L)) {
            var response = inspector.clickNode(Map.of("nodeId", nodeId, "clickCount", count));
            assertThat(response.isSuccess()).as("clickCount=%s", count).isFalse();
            assertThat(response.getError()).contains("clickCount");
        }
        assertThat(clicks).hasValue(0);
    }

    @Test
    void rejectedRobotModeDoesNotOpenContextMenu() {
        var menuRequests = new AtomicInteger();
        var rectangle = new Rectangle(40, 40);
        var events = new ArrayList<MouseEvent>();
        rectangle.setOnMouseClicked(events::add);
        rectangle.setOnContextMenuRequested(event -> menuRequests.incrementAndGet());
        show(rectangle);

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(rectangle), "mode", "robot",
                "button", "secondary", "clickCount", 2));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("Only synthetic input is supported");
        assertThat(events).isEmpty();
        assertThat(menuRequests).hasValue(0);
    }

    @Test
    void disabledMenuItemRejectsClicksOnItsLabel() {
        var anchor = new Button("Menu");
        var item = new MenuItem("Unavailable");
        item.setDisable(true);
        var actions = new AtomicInteger();
        item.setOnAction(event -> actions.incrementAndGet());
        var menu = new ContextMenu(item);
        menus.add(menu);
        anchor.setContextMenu(menu);
        show(anchor);
        inspector.clickNode(Map.of("nodeId", System.identityHashCode(anchor), "button", "secondary"));
        var label = new java.util.concurrent.atomic.AtomicReference<Node>();
        onFx(() -> label.set(menu.getScene().getRoot().lookup(".menu-item .label")));
        assertThat(label.get()).isNotNull();

        var response = inspector.clickNode(Map.of("nodeId", System.identityHashCode(label.get())));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("disabled");
        assertThat(actions).hasValue(0);
    }

    private void show(Node node) {
        onFx(() -> {
            root.getChildren().setAll(node);
            root.applyCss();
            root.layout();
            stage.requestFocus();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        var completion = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                action.run();
                completion.complete(null);
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
        });
        try {
            completion.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
