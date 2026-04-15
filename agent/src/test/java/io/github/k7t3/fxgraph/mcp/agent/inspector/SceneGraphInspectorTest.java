package io.github.k7t3.fxgraph.mcp.agent.inspector;

import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SceneGraphInspectorTest {

    private Stage stage;
    private VBox root;
    private final List<Stage> extraStages = new ArrayList<>();

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
    void findNodes_byId_findsMatchingNode() {
        SceneGraphInspector inspector = createInspector();
        Label label = new Label("Hello");
        label.setId("my-label");
        runOnFxThread(() -> root.getChildren().setAll(label));

        AgentResponse response = inspector.findNodes(Map.of("id", "my-label"));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        assertEquals(1, nodes.size());
        assertEquals(System.identityHashCode(label), nodes.get(0).get("nodeId"));
        assertEquals("my-label", nodes.get(0).get("id"));
    }

    @Test
    void findNodes_byId_returnsEmptyWhenNoMatch() {
        SceneGraphInspector inspector = createInspector();
        runOnFxThread(() -> root.getChildren().setAll(new Label("x")));

        AgentResponse response = inspector.findNodes(Map.of("id", "nonexistent"));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        assertTrue(nodes.isEmpty());
        assertEquals(0, data.get("count"));
    }

    @Test
    void findNodes_byType_findsAllButtons() {
        SceneGraphInspector inspector = createInspector();
        Button btn1 = new Button("A");
        Button btn2 = new Button("B");
        Label label = new Label("L");
        runOnFxThread(() -> root.getChildren().setAll(btn1, btn2, label));

        AgentResponse response = inspector.findNodes(Map.of("type", "Button"));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        assertEquals(2, (int) data.get("count"));
        assertTrue(nodes.stream().allMatch(n -> "Button".equals(n.get("type"))));
    }

    @Test
    void findNodes_byType_isCaseInsensitive() {
        SceneGraphInspector inspector = createInspector();
        Button btn = new Button("Go");
        runOnFxThread(() -> root.getChildren().setAll(btn));

        AgentResponse response = inspector.findNodes(Map.of("type", "button"));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        assertFalse(nodes.isEmpty());
        assertTrue(nodes.stream().anyMatch(n -> System.identityHashCode(btn) == (int) n.get("nodeId")));
    }

    @Test
    void findNodes_byText_findsPartialMatch() {
        SceneGraphInspector inspector = createInspector();
        Label label = new Label("Hello World");
        Button btn = new Button("Hello");
        runOnFxThread(() -> root.getChildren().setAll(label, btn));

        AgentResponse response = inspector.findNodes(Map.of("text", "Hello"));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        // Both the Label and Button (at minimum) should be found
        int labelId = System.identityHashCode(label);
        int btnId = System.identityHashCode(btn);
        assertTrue(nodes.stream().anyMatch(n -> labelId == (int) n.get("nodeId")));
        assertTrue(nodes.stream().anyMatch(n -> btnId == (int) n.get("nodeId")));
    }

    @Test
    void findNodes_byText_returnsTextInResult() {
        SceneGraphInspector inspector = createInspector();
        Label label = new Label("Find Me");
        label.setId("target");
        runOnFxThread(() -> root.getChildren().setAll(label));

        AgentResponse response = inspector.findNodes(Map.of("id", "target", "text", "Find Me"));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        assertEquals(1, nodes.size());
        assertEquals("Find Me", nodes.get(0).get("text"));
    }

    @Test
    void findNodes_byStyleClass_findsMatchingNodes() {
        SceneGraphInspector inspector = createInspector();
        Label label = new Label("Styled");
        label.getStyleClass().add("my-class");
        Button btn = new Button("Plain");
        runOnFxThread(() -> root.getChildren().setAll(label, btn));

        AgentResponse response = inspector.findNodes(Map.of("styleClass", "my-class"));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        assertEquals(1, nodes.size());
        assertEquals(System.identityHashCode(label), nodes.get(0).get("nodeId"));
    }

    @Test
    void findNodes_combinedCriteria_appliesAndLogic() {
        SceneGraphInspector inspector = createInspector();
        Button btn1 = new Button("Submit");
        btn1.getStyleClass().add("primary");
        Button btn2 = new Button("Cancel");
        btn2.getStyleClass().add("primary");
        runOnFxThread(() -> root.getChildren().setAll(btn1, btn2));

        AgentResponse response = inspector.findNodes(Map.of("type", "Button", "text", "Submit"));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        assertEquals(1, nodes.size());
        assertEquals(System.identityHashCode(btn1), nodes.get(0).get("nodeId"));
    }

    @Test
    void findNodes_maxResults_limitsOutput() {
        SceneGraphInspector inspector = createInspector();
        HBox hbox = new HBox();
        runOnFxThread(() -> {
            for (int i = 0; i < 10; i++) {
                hbox.getChildren().add(new Label("Item " + i));
            }
            root.getChildren().setAll(hbox);
        });

        AgentResponse response = inspector.findNodes(Map.of("type", "Label", "maxResults", 3));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        assertEquals(3, nodes.size());
        assertEquals(3, data.get("count"));
    }

    @Test
    void findNodes_requiresAtLeastOneCriterion() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.findNodes(Map.of());

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("At least one search criterion"));
    }

    @Test
    void findNodes_nullParamsReturnsError() {
        SceneGraphInspector inspector = createInspector();
        AgentResponse response = inspector.findNodes(null);

        assertFalse(response.isSuccess());
    }

    @Test
    void findNodes_byStageId_restrictsSearch() {
        SceneGraphInspector inspector = createInspector();
        Label primaryLabel = new Label("Primary");
        primaryLabel.setId("primary-label");
        runOnFxThread(() -> root.getChildren().setAll(primaryLabel));

        Stage extra = createStage(new VBox(new Label("Secondary")), "Secondary");
        String primaryId = stageId(stage);

        AgentResponse response = inspector.findNodes(Map.of("type", "Label", "stageId", primaryId));
        assertTrue(response.isSuccess());
        Map<String, Object> data = castMap(response.getData());
        List<Map<String, Object>> nodes = castList(data.get("nodes"));
        assertTrue(nodes.stream().allMatch(n -> primaryId.equals(n.get("stageId"))));
        assertFalse(nodes.isEmpty());
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

    private boolean hasHighlight(Parent parent) {
        Optional<Node> highlight = parent.getChildrenUnmodifiable().stream()
                .filter(node -> Objects.equals(node.getId(), "__fxgraph_highlight__"))
                .findFirst();
        return highlight.isPresent();
    }

    private String stageId(Stage stage) {
        return String.valueOf(System.identityHashCode(stage));
    }

    private Path tempPngPath(String suffix) {
        try {
            Path dir = Files.createTempDirectory("fxgraph-test-");
            return dir.resolve("screenshot-" + suffix + ".png");
        } catch (Exception e) {
            throw new AssertionError("Failed to create temp file", e);
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
