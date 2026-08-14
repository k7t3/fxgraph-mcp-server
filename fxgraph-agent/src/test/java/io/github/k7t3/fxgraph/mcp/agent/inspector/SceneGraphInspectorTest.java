package io.github.k7t3.fxgraph.mcp.agent.inspector;

import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jcodec.api.awt.AWTFrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(ApplicationExtension.class)
class SceneGraphInspectorTest {

    private Stage stage;
    private VBox root;
    private final List<Stage> extraStages = new ArrayList<>();
    private final List<PopupWindow> extraPopups = new ArrayList<>();

    @Start
    private void start(Stage stage) {
        this.stage = stage;
        root = new VBox();
        root.setId("root");
        stage.setTitle("Primary");
        stage.setScene(new Scene(root, 400, 300));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        for (var popup : extraPopups) {
            runOnFxThread(popup::hide);
        }
        extraPopups.clear();
        for (Stage s : extraStages) {
            runOnFxThread(s::close);
        }
        extraStages.clear();
    }

    @Test
    void constructor_hasNoArgAndNoObjectMapper() {
        assertDoesNotThrow(() -> SceneGraphInspector.class.getConstructor());
        assertThrows(NoSuchMethodException.class, () -> SceneGraphInspector.class.getConstructor(Class.forName("com.fasterxml.jackson.databind.ObjectMapper")));
    }

    @Test
    void getStages_returnsStageInfo() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.getStages();

        assertTrue(response.isSuccess());
        List<Map<String, Object>> stages = castList(response.getData());

        String stageId = stageId(stage);
        Map<String, Object> stageInfo = stages.stream()
                .filter(info -> stageId.equals(info.get("stageId")))
                .findFirst()
                .orElseThrow();

        assertEquals("Primary", stageInfo.get("title"));
        assertEquals(stageId, stageInfo.get("stageId"));
        assertEquals(System.identityHashCode(root), stageInfo.get("rootNodeId"));
        assertTrue(((Number) stageInfo.get("width")).doubleValue() > 0);
        assertTrue(((Number) stageInfo.get("height")).doubleValue() > 0);
    }

    @Test
    @DisplayName("Should list shown popup windows")
    void shouldListShownPopupWindows() {
        var inspector = createInspector();
        var popup = createPopup(new Label("Popup content"));

        var response = inspector.getStages();
        var windows = castList(response.getData());

        assertThat(response.isSuccess()).isTrue();
        assertThat(windows)
                .filteredOn(info -> windowId(popup).equals(info.get("stageId")))
                .singleElement()
                .satisfies(info -> {
                    assertThat(info)
                            .containsEntry("windowType", "Popup")
                            .containsEntry("ownerWindowId", windowId(stage))
                            .containsEntry("rootNodeId", System.identityHashCode(popup.getScene().getRoot()));
                });
    }

    @Test
    void getStages_ignoresStagesWithoutScene() {
        SceneGraphInspector inspector = createInspector();
        runOnFxThread(() -> stage.setScene(null));

        AgentResponse response = inspector.getStages();
        assertTrue(response.isSuccess());
        List<Map<String, Object>> stages = castList(response.getData());

        String stageId = stageId(stage);
        assertTrue(stages.stream().noneMatch(info -> stageId.equals(info.get("stageId"))));
    }

    @Test
    void getScenegraph_respectsDepth() {
        SceneGraphInspector inspector = createInspector();

        Rectangle rectA = new Rectangle(10, 10);
        Rectangle rectB = new Rectangle(12, 12);
        Group group = new Group(rectB);

        runOnFxThread(() -> {
            VBox newRoot = new VBox(rectA, group);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse depth0 = inspector.getScenegraph(Map.of("depth", 0));
        Map<String, Object> data0 = castMap(depth0.getData());
        List<Map<String, Object>> roots0 = castList(data0.get("rootNodes"));
        Map<String, Object> rootNode0 = roots0.get(0);
        assertFalse(rootNode0.containsKey("children"));

        AgentResponse depth2 = inspector.getScenegraph(Map.of("depth", 2));
        Map<String, Object> data2 = castMap(depth2.getData());
        List<Map<String, Object>> roots2 = castList(data2.get("rootNodes"));
        Map<String, Object> rootNode2 = roots2.get(0);
        List<Map<String, Object>> children2 = castList(rootNode2.get("children"));
        assertEquals(2, children2.size());
        // totalNodeCount は廃止済み
        assertFalse(data2.containsKey("totalNodeCount"));
    }

    @Test
    void getScenegraph_filtersPropertiesAndTransforms() {
        SceneGraphInspector inspector = createInspector();

        Label label = new Label("Hello");
        label.setId("label");
        runOnFxThread(() -> {
            VBox newRoot = new VBox(label);
            newRoot.setOpacity(0.8);
            newRoot.setScaleX(1.2);
            newRoot.setScaleY(1.1);
            newRoot.setRotate(10);
            stage.setScene(new Scene(newRoot, 320, 200));
            stage.show();
        });

        AgentResponse response = inspector.getScenegraph(Map.of(
                "includeProperties", true,
                "includeTransforms", true,
                "propertyFilter", List.of("text")
        ));

        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> roots = castList(data.get("rootNodes"));
        Map<String, Object> rootNode = roots.get(0);
        assertTrue(rootNode.containsKey("opacity"));
        assertTrue(rootNode.containsKey("scaleX"));
        assertTrue(rootNode.containsKey("scaleY"));
        assertTrue(rootNode.containsKey("rotate"));

        List<Map<String, Object>> children = castList(rootNode.get("children"));
        Map<String, Object> labelNode = children.stream()
                .filter(child -> Objects.equals(child.get("id"), "label"))
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> properties = castList(labelNode.get("properties"));
        assertFalse(properties.isEmpty());
        assertTrue(properties.stream().allMatch(p -> Objects.equals(p.get("name"), "text")));
    }

    @Test
    void getScenegraph_filtersByStageId() {
        SceneGraphInspector inspector = createInspector();

        Stage extra = createStage(new StackPane(new Label("Second")), "Secondary");
        String primaryId = stageId(stage);

        AgentResponse response = inspector.getScenegraph(Map.of("stageId", primaryId));
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> stages = castList(data.get("stages"));

        assertEquals(1, stages.size());
        assertEquals(primaryId, stages.get(0).get("stageId"));
        assertNotEquals(primaryId, stageId(extra));
    }

    @Test
    @DisplayName("Should return the scene graph for a popup window")
    void shouldReturnSceneGraphForPopupWindow() {
        var inspector = createInspector();
        var popupLabel = new Label("Popup content");
        popupLabel.setId("popupLabel");
        var popup = createPopup(popupLabel);

        var response = inspector.getScenegraph(Map.of("stageId", windowId(popup)));
        var data = castMap(response.getData());
        var windows = castList(data.get("stages"));
        var rootNodes = castList(data.get("rootNodes"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(windows)
                .singleElement()
                .satisfies(info -> assertThat(info)
                        .containsEntry("stageId", windowId(popup))
                        .containsEntry("windowType", "Popup")
                        .containsEntry("ownerWindowId", windowId(stage)));
        assertThat(rootNodes)
                .singleElement()
                .satisfies(rootNode -> assertThat(findSerializedNodeById(rootNode, "popupLabel")).isPresent());
    }

    @Test
    void getNodeDetails_returnsNodeAndChildren() {
        SceneGraphInspector inspector = createInspector();

        Label child = new Label("Child");
        runOnFxThread(() -> {
            VBox newRoot = new VBox(child);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        int nodeId = System.identityHashCode(stage.getScene().getRoot());
        AgentResponse response = inspector.getNodeDetails(Map.of("nodeId", nodeId));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        Map<String, Object> nodeInfo = castMap(data.get("node"));
        assertEquals(nodeId, nodeInfo.get("nodeId"));

        List<Map<String, Object>> children = castList(data.get("children"));
        assertEquals(1, children.size());
        assertEquals(System.identityHashCode(child), children.get(0).get("nodeId"));
    }

    @Test
    void getNodeDetails_filtersProperties() {
        SceneGraphInspector inspector = createInspector();

        Label label = new Label("Hello");
        runOnFxThread(() -> {
            VBox newRoot = new VBox(label);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        int nodeId = System.identityHashCode(label);
        AgentResponse response = inspector.getNodeDetails(Map.of(
                "nodeId", nodeId,
                "propertyFilter", List.of("text")
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> properties = castList(data.get("properties"));
        assertFalse(properties.isEmpty());
        assertTrue(properties.stream().allMatch(p -> Objects.equals(p.get("name"), "text")));
    }

    @Test
    void getNodeDetails_returnsErrorWhenMissing() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.getNodeDetails(Map.of("nodeId", 999999));

        assertFalse(response.isSuccess());
        assertEquals("Node not found: 999999", response.getError());
    }

    @Test
    void setProperty_updatesStyle() {
        SceneGraphInspector inspector = createInspector();
        Label label = new Label("Hello");
        runOnFxThread(() -> root.getChildren().setAll(label));

        int nodeId = System.identityHashCode(label);
        AgentResponse response = inspector.setProperty(Map.of(
                "nodeId", nodeId,
                "propertyName", "style",
                "value", "-fx-opacity: 0.5;"
        ));

        assertTrue(response.isSuccess());
        assertEquals("-fx-opacity: 0.5;", label.getStyle());
    }

    @Test
    void setProperty_updatesTypedValue() {
        SceneGraphInspector inspector = createInspector();
        Rectangle rect = new Rectangle(10, 10);
        runOnFxThread(() -> root.getChildren().setAll(rect));

        int nodeId = System.identityHashCode(rect);
        AgentResponse response = inspector.setProperty(Map.of(
                "nodeId", nodeId,
                "propertyName", "opacity",
                "value", "0.4",
                "valueType", "number"
        ));

        assertTrue(response.isSuccess());
        assertEquals(0.4, rect.getOpacity(), 0.0001);
    }

    @Test
    void setProperty_returnsErrorForMissingProperty() {
        SceneGraphInspector inspector = createInspector();
        Label label = new Label("Hello");
        runOnFxThread(() -> root.getChildren().setAll(label));

        int nodeId = System.identityHashCode(label);
        AgentResponse response = inspector.setProperty(Map.of(
                "nodeId", nodeId,
                "propertyName", "nope",
                "value", "x"
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        assertTrue(((String) data.get("error")).contains("Property not found"));
    }

    @Test
    void setProperty_returnsErrorForMissingNode() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.setProperty(Map.of(
                "nodeId", 123456,
                "propertyName", "opacity",
                "value", "0.5"
        ));

        assertFalse(response.isSuccess());
        assertEquals("Node not found or property not writable", response.getError());
    }

    @Test
    void selectNode_addsHighlightAndClears() {
        SceneGraphInspector inspector = createInspector();
        Rectangle rect = new Rectangle(20, 20);
        runOnFxThread(() -> root.getChildren().setAll(rect));

        int nodeId = System.identityHashCode(rect);
        AgentResponse response = inspector.selectNode(Map.of(
                "nodeId", nodeId,
                "showBounds", true
        ));

        assertTrue(response.isSuccess());
        assertTrue(hasHighlight(root));

        AgentResponse clear = inspector.selectNode(Map.of("nodeId", 0, "showBounds", true));
        assertTrue(clear.isSuccess());
        assertFalse(hasHighlight(root));
    }

    @Test
    void selectNode_returnsErrorForMissingNode() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.selectNode(Map.of(
                "nodeId", 999999,
                "showBounds", true
        ));

        assertFalse(response.isSuccess());
        assertEquals("Node not found: 999999", response.getError());
    }

    @Test
    void clickNode_firesActionForButton() {
        SceneGraphInspector inspector = createInspector();
        Button button = new Button("Click");
        AtomicInteger fired = new AtomicInteger();
        button.setOnAction(event -> fired.incrementAndGet());
        runOnFxThread(() -> root.getChildren().setAll(button));

        int nodeId = System.identityHashCode(button);
        AgentResponse response = inspector.clickNode(Map.of("nodeId", nodeId));

        assertTrue(response.isSuccess());
        assertEquals(1, fired.get());
    }

    @Test
    void clickNode_updatesButtonBaseState() {
        var inspector = createInspector();
        var toggleButton = new ToggleButton("Toggle");
        runOnFxThread(() -> root.getChildren().setAll(toggleButton));

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(toggleButton)
        ));

        assertThat(response.isSuccess()).isTrue();
        assertThat(toggleButton.isSelected()).isTrue();
    }

    @Test
    void clickNode_returnsErrorForDisabledNode() {
        var inspector = createInspector();
        var button = new Button("Disabled");
        button.setDisable(true);
        var actions = new AtomicInteger();
        button.setOnAction(event -> actions.incrementAndGet());
        runOnFxThread(() -> root.getChildren().setAll(button));
        var nodeId = System.identityHashCode(button);

        var response = inspector.clickNode(Map.of("nodeId", nodeId));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Node is disabled: " + nodeId);
        assertThat(actions).hasValue(0);
    }

    @Test
    void clickNode_returnsErrorWhenAncestorIsInvisible() {
        var inspector = createInspector();
        var button = new Button("Hidden");
        var hiddenContainer = new VBox(button);
        hiddenContainer.setVisible(false);
        var actions = new AtomicInteger();
        button.setOnAction(event -> actions.incrementAndGet());
        runOnFxThread(() -> root.getChildren().setAll(hiddenContainer));
        var nodeId = System.identityHashCode(button);

        var response = inspector.clickNode(Map.of("nodeId", nodeId));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Node is not visible: " + nodeId);
        assertThat(actions).hasValue(0);
    }

    @Test
    void clickNode_firesMouseEventForNode() {
        SceneGraphInspector inspector = createInspector();
        Rectangle rect = new Rectangle(20, 20);
        AtomicInteger clicks = new AtomicInteger();
        rect.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> clicks.incrementAndGet());
        runOnFxThread(() -> root.getChildren().setAll(rect));

        int nodeId = System.identityHashCode(rect);
        AgentResponse response = inspector.clickNode(Map.of("nodeId", nodeId));

        assertTrue(response.isSuccess());
        assertEquals(1, clicks.get());
    }

    @Test
    void clickNode_createsConsistentMouseEvent() {
        var inspector = createInspector();
        var rectangle = new Rectangle(20, 20);
        rectangle.setTranslateX(80);
        rectangle.setTranslateY(40);
        var clickedEvent = new AtomicReference<MouseEvent>();
        var expectedScenePoint = new AtomicReference<Point2D>();
        var expectedScreenPoint = new AtomicReference<Point2D>();
        rectangle.addEventHandler(MouseEvent.MOUSE_CLICKED, clickedEvent::set);
        runOnFxThread(() -> root.getChildren().setAll(rectangle));
        runOnFxThread(() -> {
            expectedScenePoint.set(rectangle.localToScene(10, 10));
            expectedScreenPoint.set(rectangle.localToScreen(10, 10));
        });

        var response = inspector.clickNode(Map.of(
                "nodeId", System.identityHashCode(rectangle)
        ));

        var event = clickedEvent.get();
        assertThat(response.isSuccess()).isTrue();
        assertThat(event).isNotNull();
        assertThat(event.getSource()).isSameAs(rectangle);
        assertThat(event.getTarget()).isSameAs(rectangle);
        assertThat(event.getX()).isCloseTo(10, within(0.0001));
        assertThat(event.getY()).isCloseTo(10, within(0.0001));
        assertThat(event.getSceneX()).isCloseTo(expectedScenePoint.get().getX(), within(0.0001));
        assertThat(event.getSceneY()).isCloseTo(expectedScenePoint.get().getY(), within(0.0001));
        assertThat(event.getScreenX()).isCloseTo(expectedScreenPoint.get().getX(), within(0.0001));
        assertThat(event.getScreenY()).isCloseTo(expectedScreenPoint.get().getY(), within(0.0001));
        assertThat(event.getButton()).isEqualTo(MouseButton.PRIMARY);
        assertThat(event.isPrimaryButtonDown()).isFalse();
        assertThat(event.isMiddleButtonDown()).isFalse();
        assertThat(event.isSecondaryButtonDown()).isFalse();
        assertThat(event.isSynthesized()).isTrue();
        assertThat(event.isPopupTrigger()).isFalse();
        assertThat(event.isStillSincePress()).isTrue();
    }

    @Test
    void clickNode_returnsErrorForMissingNode() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.clickNode(Map.of("nodeId", 999999));

        assertFalse(response.isSuccess());
        assertEquals("Node not found: 999999", response.getError());
    }

    @Test
    void clickNode_returnsErrorForZeroSize() {
        SceneGraphInspector inspector = createInspector();
        Rectangle rect = new Rectangle(0, 0);
        runOnFxThread(() -> root.getChildren().setAll(rect));

        int nodeId = System.identityHashCode(rect);
        AgentResponse response = inspector.clickNode(Map.of("nodeId", nodeId));

        assertFalse(response.isSuccess());
        assertEquals("Node is not visible or has zero size: " + nodeId, response.getError());
    }

    @Test
    void clickNode_returnsErrorForZeroSizeButton() {
        var inspector = createInspector();
        var button = new Button("Zero");
        button.setManaged(false);
        button.resize(0, 0);
        var actions = new AtomicInteger();
        button.setOnAction(event -> actions.incrementAndGet());
        runOnFxThread(() -> root.getChildren().setAll(button));
        var nodeId = System.identityHashCode(button);
        assertThat(button.getWidth()).isZero();
        assertThat(button.getHeight()).isZero();

        var response = inspector.clickNode(Map.of("nodeId", nodeId));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo(
                "Node is not visible or has zero size: " + nodeId
        );
        assertThat(actions).hasValue(0);
    }

    @Test
    void requestFocus_focusesNode() {
        SceneGraphInspector inspector = createInspector();
        TextField field = new TextField();
        runOnFxThread(() -> root.getChildren().setAll(field));
        runOnFxThread(stage::requestFocus);

        int nodeId = System.identityHashCode(field);
        AgentResponse response = inspector.requestFocus(Map.of("nodeId", nodeId));

        assertTrue(response.isSuccess());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(field, stage.getScene().getFocusOwner());
    }

    @Test
    void requestFocus_returnsErrorForMissingNode() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.requestFocus(Map.of("nodeId", 999999));

        assertFalse(response.isSuccess());
        assertEquals("Node not found: 999999", response.getError());
    }

    @Test
    void typeKey_sendsKeyTypedToNode() {
        SceneGraphInspector inspector = createInspector();
        TextField field = new TextField();
        AtomicInteger typed = new AtomicInteger();
        field.addEventHandler(KeyEvent.KEY_TYPED, event -> typed.incrementAndGet());
        runOnFxThread(() -> root.getChildren().setAll(field));

        int nodeId = System.identityHashCode(field);
        AgentResponse response = inspector.typeKey(Map.of(
                "nodeId", nodeId,
                "key", "A"
        ));

        assertTrue(response.isSuccess());
        assertEquals(1, typed.get());
    }

    @Test
    void typeKey_usesFocusedNodeWhenMissingId() {
        SceneGraphInspector inspector = createInspector();
        TextField field = new TextField();
        AtomicInteger typed = new AtomicInteger();
        field.addEventHandler(KeyEvent.KEY_TYPED, event -> typed.incrementAndGet());
        runOnFxThread(() -> {
            root.getChildren().setAll(field);
            stage.requestFocus();
            field.requestFocus();
        });

        AgentResponse response = inspector.typeKey(Map.of("key", "A"));

        assertTrue(response.isSuccess());
        assertEquals(1, typed.get());
    }

    @Test
    void typeKey_returnsErrorForMissingNode() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.typeKey(Map.of(
                "nodeId", 999999,
                "key", "A"
        ));

        assertFalse(response.isSuccess());
        assertEquals("Node not found: 999999", response.getError());
    }

    @Test
    void typeKey_returnsErrorForEmptyKey() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.typeKey(Map.of("key", ""));

        assertFalse(response.isSuccess());
        assertEquals("key is required", response.getError());
    }

    @Test
    void takeScreenshot_capturesNode() {
        SceneGraphInspector inspector = createInspector();
        Rectangle rect = new Rectangle(40, 30);
        runOnFxThread(() -> root.getChildren().setAll(rect));

        int nodeId = System.identityHashCode(rect);
        Path output = tempPngPath("node");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "nodeId", nodeId,
                "savePath", output.toString()
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        assertEquals("image/png", data.get("mimeType"));
        assertEquals("node", data.get("targetType"));
        assertEquals(String.valueOf(nodeId), data.get("targetId"));
        assertEquals(output.toAbsolutePath().toString(), data.get("savedPath"));
        assertTrue(Files.exists(output));
        assertTrue(((Number) data.get("width")).intValue() > 0);
        assertTrue(((Number) data.get("height")).intValue() > 0);
    }

    @Test
    void takeScreenshot_capturesStage() {
        SceneGraphInspector inspector = createInspector();
        String sid = stageId(stage);

        Path output = tempPngPath("stage");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "stageId", sid,
                "savePath", output.toString()
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        assertEquals("image/png", data.get("mimeType"));
        assertEquals("scenegraph", data.get("targetType"));
        assertEquals(sid, data.get("targetId"));
        assertEquals(output.toAbsolutePath().toString(), data.get("savedPath"));
        assertTrue(Files.exists(output));
    }

    @Test
    @DisplayName("Should capture a popup window scene")
    void shouldCapturePopupWindowScene() {
        var inspector = createInspector();
        var popup = createPopup(new Label("Popup screenshot"));
        var output = tempPngPath("popup-window");

        var response = inspector.takeScreenshot(Map.of(
                "stageId", windowId(popup),
                "savePath", output.toString()));
        var data = castMap(response.getData());

        assertThat(response.isSuccess()).isTrue();
        assertThat(output).exists();
        assertThat(data)
                .containsEntry("targetType", "scenegraph")
                .containsEntry("targetId", windowId(popup));
    }

    @Test
    void takeScreenshot_returnsErrorForMissingNode() {
        SceneGraphInspector inspector = createInspector();
        Path output = tempPngPath("missing-node");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "nodeId", 999999,
                "savePath", output.toString()
        ));

        assertFalse(response.isSuccess());
        assertEquals("Node not found: 999999", response.getError());
    }

    @Test
    void takeScreenshot_returnsErrorForMissingStage() {
        SceneGraphInspector inspector = createInspector();
        Path output = tempPngPath("missing-stage");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "stageId", "missing",
                "savePath", output.toString()
        ));

        assertFalse(response.isSuccess());
        assertEquals("Stage not found", response.getError());
    }

    @Test
    void takeScreenshot_returnsErrorWhenSavePathMissing() {
        SceneGraphInspector inspector = createInspector();
        Rectangle rect = new Rectangle(40, 30);
        runOnFxThread(() -> root.getChildren().setAll(rect));
        int nodeId = System.identityHashCode(rect);

        AgentResponse response = inspector.takeScreenshot(Map.of("nodeId", nodeId));

        assertFalse(response.isSuccess());
        assertEquals("savePath is required", response.getError());
    }

    @Test
    @DisplayName("Should capture multiple node frames as an MP4 clip")
    void shouldCaptureNodeFramesAsMp4Clip() throws Exception {
        // Arrange
        var inspector = createInspector();
        var rectangle = new Rectangle(64, 48, Color.CORNFLOWERBLUE);
        runOnFxThread(() -> root.getChildren().setAll(rectangle));
        var nodeId = System.identityHashCode(rectangle);
        var output = tempVideoPath("node");

        // Act
        var response = inspector.captureVideo(Map.of(
                "nodeId", nodeId,
                "savePath", output.toString(),
                "durationSeconds", 1,
                "framesPerSecond", 2,
                "maxWidth", 64,
                "maxHeight", 48));

        // Assert
        assertThat(response.isSuccess()).isTrue();
        var data = castMap(response.getData());
        assertThat(data).containsAllEntriesOf(Map.of(
                "mimeType", "video/mp4",
                "codec", "H.264",
                "targetType", "node",
                "targetId", String.valueOf(nodeId),
                "durationSeconds", 1,
                "framesPerSecond", 2,
                "frameCount", 2,
                "width", 64,
                "height", 48));
        assertThat(data.get("savedPath")).isEqualTo(output.toAbsolutePath().toString());
        assertThat(Files.size(output)).isPositive();
        assertThat(new String(Files.readAllBytes(output), 4, 4, StandardCharsets.US_ASCII))
                .isEqualTo("ftyp");
    }

    @Test
    @DisplayName("Should keep MP4 frame dimensions stable when node bounds change")
    void shouldKeepVideoFrameDimensionsStableWhenNodeBoundsChange() throws Exception {
        // Arrange
        var inspector = createInspector();
        var rectangle = new Rectangle(64, 48, Color.CORNFLOWERBLUE);
        runOnFxThread(() -> {
            root.getChildren().setAll(rectangle);
            var resize = new PauseTransition(Duration.millis(200));
            resize.setOnFinished(event -> rectangle.setWidth(32));
            resize.play();
        });
        var output = tempVideoPath("resizing-node");

        // Act
        var response = inspector.captureVideo(Map.of(
                "nodeId", System.identityHashCode(rectangle),
                "savePath", output.toString(),
                "durationSeconds", 1,
                "framesPerSecond", 2,
                "maxWidth", 64,
                "maxHeight", 48));

        // Assert
        assertThat(response.isSuccess())
                .as(response.getError())
                .isTrue();
        assertThat(castMap(response.getData()))
                .containsEntry("width", 64)
                .containsEntry("height", 48)
                .containsEntry("frameCount", 2);
        try (var channel = NIOUtils.readableChannel(output.toFile())) {
            var frameGrab = AWTFrameGrab.createAWTFrameGrab(channel);
            var firstFrame = frameGrab.getFrame();
            var secondFrame = frameGrab.getFrame();
            var firstPixel = new java.awt.Color(firstFrame.getRGB(56, 24));
            var secondPixel = new java.awt.Color(secondFrame.getRGB(56, 24));

            assertThat(firstPixel.getBlue() - secondPixel.getBlue()).isGreaterThan(50);
        }
    }

    @Test
    @DisplayName("Should capture a Stage scene as an MP4 clip")
    void shouldCaptureStageSceneAsMp4Clip() {
        // Arrange
        var inspector = createInspector();
        var output = tempVideoPath("stage");
        var currentStageId = stageId(stage);

        // Act
        var response = inspector.captureVideo(Map.of(
                "stageId", currentStageId,
                "savePath", output.toString(),
                "durationSeconds", 1,
                "framesPerSecond", 1));

        // Assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(castMap(response.getData()))
                .containsEntry("mimeType", "video/mp4")
                .containsEntry("targetType", "scenegraph")
                .containsEntry("targetId", currentStageId)
                .containsEntry("frameCount", 1);
        assertThat(output).exists();
    }

    @Test
    @DisplayName("Should capture a popup window scene as an MP4 clip")
    void shouldCapturePopupWindowSceneAsMp4Clip() {
        var inspector = createInspector();
        var popup = createPopup(new Label("Popup video"));
        var output = tempVideoPath("popup-window");

        var response = inspector.captureVideo(Map.of(
                "stageId", windowId(popup),
                "savePath", output.toString(),
                "durationSeconds", 1,
                "framesPerSecond", 1));

        assertThat(response.isSuccess()).isTrue();
        assertThat(castMap(response.getData()))
                .containsEntry("targetType", "scenegraph")
                .containsEntry("targetId", windowId(popup))
                .containsEntry("frameCount", 1);
        assertThat(output).exists();
    }

    @Test
    @DisplayName("Should reject video clips longer than 30 seconds")
    void shouldRejectVideoDurationOverThirtySeconds() {
        // Arrange
        var inspector = createInspector();

        // Act
        var response = inspector.captureVideo(Map.of(
                "savePath", tempVideoPath("too-long").toString(),
                "durationSeconds", 31));

        // Assert
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError())
                .isEqualTo("Failed to capture video: durationSeconds must be between 1 and 30");
    }

    @Test
    void takeScreenshot_scalesWhenExceedsMaxWidth() {
        SceneGraphInspector inspector = createInspector();
        Canvas canvas = new Canvas(1920, 500);
        runOnFxThread(() -> {
            var gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.RED);
            gc.fillRect(0, 0, 1920, 500);
            root.getChildren().setAll(canvas);
        });
        int nodeId = System.identityHashCode(canvas);
        Path output = tempPngPath("scale-width");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "nodeId", nodeId,
                "savePath", output.toString(),
                "maxWidth", 800,
                "maxHeight", 600
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        int width = ((Number) data.get("width")).intValue();
        int height = ((Number) data.get("height")).intValue();
        assertTrue(width <= 800, "width should be <= 800 but was " + width);
        assertTrue(height <= 600, "height should be <= 600 but was " + height);
        double expectedScale = Math.min(800.0 / 1920.0, 600.0 / 500.0);
        int expectedWidth = (int) (1920 * expectedScale);
        int expectedHeight = (int) (500 * expectedScale);
        assertEquals(expectedWidth, width);
        assertEquals(expectedHeight, height);
    }

    @Test
    void takeScreenshot_scalesWhenExceedsMaxHeight() {
        SceneGraphInspector inspector = createInspector();
        Canvas canvas = new Canvas(400, 1200);
        runOnFxThread(() -> {
            var gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.BLUE);
            gc.fillRect(0, 0, 400, 1200);
            root.getChildren().setAll(canvas);
        });
        int nodeId = System.identityHashCode(canvas);
        Path output = tempPngPath("scale-height");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "nodeId", nodeId,
                "savePath", output.toString(),
                "maxWidth", 800,
                "maxHeight", 600
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        int width = ((Number) data.get("width")).intValue();
        int height = ((Number) data.get("height")).intValue();
        assertTrue(width <= 800, "width should be <= 800 but was " + width);
        assertTrue(height <= 600, "height should be <= 600 but was " + height);
        double expectedScale = Math.min(800.0 / 400.0, 600.0 / 1200.0);
        int expectedWidth = (int) (400 * expectedScale);
        int expectedHeight = (int) (1200 * expectedScale);
        assertEquals(expectedWidth, width);
        assertEquals(expectedHeight, height);
    }

    @Test
    void takeScreenshot_noScaleWhenWithinLimits() {
        SceneGraphInspector inspector = createInspector();
        Rectangle rect = new Rectangle(100, 80);
        runOnFxThread(() -> root.getChildren().setAll(rect));
        int nodeId = System.identityHashCode(rect);
        Path output = tempPngPath("no-scale");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "nodeId", nodeId,
                "savePath", output.toString(),
                "maxWidth", 800,
                "maxHeight", 600
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        int width = ((Number) data.get("width")).intValue();
        int height = ((Number) data.get("height")).intValue();
        assertEquals(100, width);
        assertEquals(80, height);
    }

    @Test
    void takeScreenshot_preservesAspectRatio() {
        SceneGraphInspector inspector = createInspector();
        Canvas canvas = new Canvas(1920, 1080);
        runOnFxThread(() -> {
            var gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.GREEN);
            gc.fillRect(0, 0, 1920, 1080);
            root.getChildren().setAll(canvas);
        });
        int nodeId = System.identityHashCode(canvas);
        Path output = tempPngPath("aspect-ratio");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "nodeId", nodeId,
                "savePath", output.toString(),
                "maxWidth", 640,
                "maxHeight", 480
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        int width = ((Number) data.get("width")).intValue();
        int height = ((Number) data.get("height")).intValue();
        double originalRatio = 1920.0 / 1080.0;
        double resultRatio = (double) width / height;
        assertTrue(Math.abs(originalRatio - resultRatio) < 0.01,
                "Aspect ratio should be preserved: expected ~" + originalRatio + " but was " + resultRatio);
    }

    @Test
    void takeScreenshot_usesDefaultHdLimits() {
        SceneGraphInspector inspector = createInspector();
        Canvas canvas = new Canvas(1920, 1080);
        runOnFxThread(() -> {
            var gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.ORANGE);
            gc.fillRect(0, 0, 1920, 1080);
            root.getChildren().setAll(canvas);
        });
        int nodeId = System.identityHashCode(canvas);
        Path output = tempPngPath("default-hd");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "nodeId", nodeId,
                "savePath", output.toString()
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        int width = ((Number) data.get("width")).intValue();
        int height = ((Number) data.get("height")).intValue();
        assertTrue(width <= 1280, "width should be <= 1280 but was " + width);
        assertTrue(height <= 720, "height should be <= 720 but was " + height);
    }

    @Test
    void takeScreenshot_scalesStageWhenExceedsHd() {
        SceneGraphInspector inspector = createInspector();
        Stage testStage = createStage(new Group(), "TestStage");
        String sid = stageId(testStage);

        runOnFxThread(() -> {
            testStage.setScene(new Scene(new Group(), 1920, 1080));
        });

        Path output = tempPngPath("stage-scale");
        AgentResponse response = inspector.takeScreenshot(Map.of(
                "stageId", sid,
                "savePath", output.toString()
        ));

        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        int width = ((Number) data.get("width")).intValue();
        int height = ((Number) data.get("height")).intValue();
        assertTrue(width <= 1280, "width should be <= 1280 but was " + width);
        assertTrue(height <= 720, "height should be <= 720 but was " + height);
    }

    @Test
    @DisplayName("Should find nodes by type")
    void shouldFindNodesByType() {
        SceneGraphInspector inspector = createInspector();

        Button button = new Button("Click me");
        TextField field = new TextField();
        runOnFxThread(() -> {
            VBox newRoot = new VBox(button, field);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse response = inspector.findNodes(Map.of("type", "Button"));

        assertTrue(response.isSuccess());
        List<Map<String, Object>> results = castList(response.getData());
        assertEquals(1, results.size());
        assertEquals("Button", results.get(0).get("type"));
    }

    @Test
    @DisplayName("Should find nodes by CSS id")
    void shouldFindNodesById() {
        SceneGraphInspector inspector = createInspector();

        TextField field = new TextField();
        field.setId("usernameField");
        runOnFxThread(() -> {
            VBox newRoot = new VBox(field);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse response = inspector.findNodes(Map.of("id", "usernameField"));

        assertTrue(response.isSuccess());
        List<Map<String, Object>> results = castList(response.getData());
        assertEquals(1, results.size());
        assertEquals("usernameField", results.get(0).get("id"));
    }

    @Test
    @DisplayName("Should find nodes by text content")
    void shouldFindNodesByText() {
        SceneGraphInspector inspector = createInspector();

        Button button = new Button("Submit");
        runOnFxThread(() -> {
            VBox newRoot = new VBox(button);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse response = inspector.findNodes(Map.of("text", "Submit"));

        assertTrue(response.isSuccess());
        List<Map<String, Object>> results = castList(response.getData());
        assertEquals(1, results.size());
        assertEquals("Submit", results.get(0).get("text"));
    }

    @Test
    @DisplayName("Should find nodes inside a context menu")
    void shouldFindNodesInsideContextMenu() {
        var inspector = createInspector();
        var anchor = new Button("Open menu");
        var contextMenu = new ContextMenu(new MenuItem("Popup action"));
        runOnFxThread(() -> {
            stage.setScene(new Scene(new VBox(anchor), 300, 200));
            stage.show();
            contextMenu.show(anchor, Side.BOTTOM, 0, 0);
            extraPopups.add(contextMenu);
        });

        var response = inspector.findNodes(Map.of(
                "text", "Popup action",
                "stageId", windowId(contextMenu)));
        var results = castList(response.getData());

        assertThat(response.isSuccess()).isTrue();
        assertThat(results)
                .extracting(info -> info.get("text"))
                .contains("Popup action");
    }

    @Test
    @DisplayName("Should find nodes by style class")
    void shouldFindNodesByStyleClass() {
        SceneGraphInspector inspector = createInspector();

        Button button = new Button("Action");
        button.getStyleClass().add("primary-action");
        runOnFxThread(() -> {
            VBox newRoot = new VBox(button);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse response = inspector.findNodes(Map.of("styleClass", "primary-action"));

        assertTrue(response.isSuccess());
        List<Map<String, Object>> results = castList(response.getData());
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Should return empty list when no nodes match")
    void shouldReturnEmptyListWhenNoMatch() {
        SceneGraphInspector inspector = createInspector();

        runOnFxThread(() -> {
            VBox newRoot = new VBox(new Button("Hello"));
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse response = inspector.findNodes(Map.of("type", "NonExistentType"));

        assertTrue(response.isSuccess());
        List<Map<String, Object>> results = castList(response.getData());
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should find nodes by text from TextField")
    void shouldFindNodesByTextFromTextField() {
        SceneGraphInspector inspector = createInspector();

        TextField field = new TextField("Enter username");
        runOnFxThread(() -> {
            VBox newRoot = new VBox(field);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse response = inspector.findNodes(Map.of("text", "Enter"));

        assertTrue(response.isSuccess());
        List<Map<String, Object>> results = castList(response.getData());
        assertEquals(1, results.size());
        assertEquals("Enter username", results.get(0).get("text"));
    }

    @Test
    @DisplayName("Should find nodes by text from TextArea")
    void shouldFindNodesByTextFromTextArea() {
        SceneGraphInspector inspector = createInspector();

        TextArea area = new TextArea("Multi-line\ntext content");
        runOnFxThread(() -> {
            VBox newRoot = new VBox(area);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse response = inspector.findNodes(Map.of("text", "text content"));

        assertTrue(response.isSuccess());
        List<Map<String, Object>> results = castList(response.getData());
        assertEquals(1, results.size());
        assertEquals("Multi-line\ntext content", results.get(0).get("text"));
    }

    @Test
    @DisplayName("Should find nodes by text from PasswordField")
    void shouldFindNodesByTextFromPasswordField() {
        SceneGraphInspector inspector = createInspector();

        PasswordField field = new PasswordField();
        field.setText("secret");
        runOnFxThread(() -> {
            VBox newRoot = new VBox(field);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse response = inspector.findNodes(Map.of("text", "secret"));

        assertTrue(response.isSuccess());
        List<Map<String, Object>> results = castList(response.getData());
        assertEquals(1, results.size());
        assertEquals("secret", results.get(0).get("text"));
    }

    @Test
    @DisplayName("Should find nodes by type from anonymous class")
    void shouldFindByTypeWithAnonymousClass() {
        SceneGraphInspector inspector = createInspector();

        Button anonymousButton = new Button("Anonymous") {
        };
        runOnFxThread(() -> {
            VBox newRoot = new VBox(anonymousButton);
            stage.setScene(new Scene(newRoot, 300, 200));
            stage.show();
        });

        AgentResponse response = inspector.findNodes(Map.of("type", "Button"));

        assertTrue(response.isSuccess());
        List<Map<String, Object>> results = castList(response.getData());
        assertEquals(1, results.size());
        assertEquals("Button", results.get(0).get("type"));
    }

    private SceneGraphInspector createInspector() {
        try {
            return SceneGraphInspector.class.getConstructor().newInstance();
        } catch (Exception e) {
            throw new AssertionError("Failed to construct SceneGraphInspector", e);
        }
    }

    private Stage createStage(Parent root, String title) {
        AtomicReference<Stage> ref = new AtomicReference<>();
        runOnFxThread(() -> {
            Stage s = new Stage();
            s.setTitle(title);
            s.setScene(new Scene(root, 240, 180));
            s.show();
            extraStages.add(s);
            ref.set(s);
        });
        return ref.get();
    }

    private Popup createPopup(Node content) {
        var reference = new AtomicReference<Popup>();
        runOnFxThread(() -> {
            var popup = new Popup();
            popup.getContent().add(content);
            popup.show(stage, stage.getX() + 20, stage.getY() + 20);
            extraPopups.add(popup);
            reference.set(popup);
        });
        return reference.get();
    }

    private boolean hasHighlight(Parent parent) {
        Optional<Node> highlight = parent.getChildrenUnmodifiable().stream()
                .filter(node -> Objects.equals(node.getId(), "__fxgraph_highlight__"))
                .findFirst();
        return highlight.isPresent();
    }

    private String stageId(Stage stage) {
        return windowId(stage);
    }

    private String windowId(Window window) {
        return String.valueOf(System.identityHashCode(window));
    }

    private Optional<Map<String, Object>> findSerializedNodeById(
            Map<String, Object> node,
            String id) {
        if (Objects.equals(node.get("id"), id)) {
            return Optional.of(node);
        }
        if (!(node.get("children") instanceof List<?>)) {
            return Optional.empty();
        }
        for (var child : castList(node.get("children"))) {
            var match = findSerializedNodeById(child, id);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private Path tempPngPath(String suffix) {
        try {
            Path dir = Files.createTempDirectory("fxgraph-test-");
            return dir.resolve("screenshot-" + suffix + ".png");
        } catch (Exception e) {
            throw new AssertionError("Failed to create temp file", e);
        }
    }

    private Path tempVideoPath(String suffix) {
        try {
            var directory = Files.createTempDirectory("fxgraph-video-test-");
            return directory.resolve("clip-" + suffix + ".mp4");
        } catch (Exception e) {
            throw new AssertionError("Failed to create temporary video path", e);
        }
    }

    private void runOnFxThread(Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
            return;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("FX action failed", e);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
