package io.github.k7t3.fxgraph.mcp.tools;

import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import io.github.k7t3.fxgraph.mcp.model.JavaFxApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the stateless MCP service independently of the Attach API.
 */
class FxgraphServiceTest {

    private static final int PID = 12345;

    private JavaFxAgent agent;
    private FxgraphService service;

    @BeforeEach
    void setUp() {
        agent = mock(JavaFxAgent.class);
        service = new FxgraphService(pid -> agent);
    }

    @Test
    void discoverApplicationsReturnsResult() {
        var result = service.discoverApplications();

        assertNotNull(result);
        assertTrue(result.containsKey("success"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoverApplicationsReturnsOnlyJavaFxApplications() {
        var result = service.discoverApplications();

        assertTrue((boolean) result.get("success"));
        var applications = (List<JavaFxApplication>) result.get("applications");
        assertNotNull(applications);
        assertTrue(applications.stream().allMatch(JavaFxApplication::isJavaFX));
    }

    @Test
    void connectApplicationRejectsInvalidPid() {
        var result = service.connectApplication(0);

        assertFalse((boolean) result.get("success"));
        assertEquals("PID must be a positive integer: 0", result.get("error"));
        verifyNoInteractions(agent);
    }

    @Test
    void connectApplicationReportsConnectionFailureAndCleansUp() throws Exception {
        when(agent.connect()).thenThrow(new Exception("attach failed"));

        var result = service.connectApplication(PID);

        assertFalse((boolean) result.get("success"));
        assertEquals("attach failed", result.get("error"));
        verify(agent).disconnectWithoutShutdown();
    }

    @Test
    void connectApplicationExplainsMissingJavaInstrumentModule() throws Exception {
        var failure = new Exception(
                "Failed to load agent library: java.lang.module.FindException: "
                        + "Module java.instrument not found");
        when(agent.connect()).thenThrow(failure);

        var result = service.connectApplication(PID);

        assertThat(result)
                .containsEntry("success", false)
                .containsEntry("errorCode", "TARGET_RUNTIME_MISSING_JAVA_INSTRUMENT")
                .containsEntry(
                        "error",
                        "Cannot connect to PID 12345 because the target Java runtime "
                                + "does not include the java.instrument module.")
                .containsEntry(
                        "action",
                        "Rebuild the target jlink/jpackage runtime with java.instrument included.");
        assertThat(result.get("details").toString())
                .contains("Module java.instrument not found");
    }

    @Test
    void getStagesExplainsNestedMissingJavaInstrumentModuleFailure() throws Exception {
        var moduleFailure = new IllegalStateException(
                "java.lang.module.FindException: Module java.instrument not found");
        when(agent.connect()).thenThrow(new Exception("Agent loading failed", moduleFailure));

        var result = service.getStages(PID);

        assertThat(result)
                .containsEntry("success", false)
                .containsEntry("errorCode", "TARGET_RUNTIME_MISSING_JAVA_INSTRUMENT")
                .containsEntry(
                        "action",
                        "Rebuild the target jlink/jpackage runtime with java.instrument included.");
        assertThat(result.get("details").toString())
                .contains("Module java.instrument not found");
    }

    @Test
    void disconnectApplicationRejectsInvalidPid() {
        var result = service.disconnectApplication(-1);

        assertFalse((boolean) result.get("success"));
        assertEquals("PID must be a positive integer: -1", result.get("error"));
        verifyNoInteractions(agent);
    }

    @Test
    void disconnectApplicationExplainsMissingJavaInstrumentModule() throws Exception {
        var failure = new Exception(
                "Failed to load agent library: java.lang.module.FindException: "
                        + "Module java.instrument not found");
        when(agent.connect()).thenThrow(failure);

        var result = service.disconnectApplication(PID);

        assertThat(result)
                .containsEntry("success", false)
                .containsEntry("errorCode", "TARGET_RUNTIME_MISSING_JAVA_INSTRUMENT")
                .containsEntry(
                        "action",
                        "Rebuild the target jlink/jpackage runtime with java.instrument included.");
        verify(agent).disconnectWithoutShutdown();
    }

    @Test
    void getStagesReturnsAgentData() throws Exception {
        var stages = List.of(
                Map.of("stageId", "123", "title", "Main", "width", 800.0, "height", 600.0)
        );
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(stages));

        var result = service.getStages(PID);

        assertTrue((boolean) result.get("success"));
        assertEquals(stages, result.get("data"));
        assertCommandType(AgentCommand.CommandType.GET_STAGES);
    }

    @Test
    void getScenegraphReturnsMapDataAtTopLevel() throws Exception {
        var scenegraph = Map.of("stages", List.of(), "rootNodes", List.of());
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(scenegraph));

        var result = service.getScenegraph(PID, null, 3, null, false, null, false);

        assertTrue((boolean) result.get("success"));
        assertEquals(List.of(), result.get("rootNodes"));
        assertFalse(result.containsKey("totalNodeCount"));
    }

    @Test
    void getScenegraphSendsFilters() throws Exception {
        stubSuccessfulMapResponse();

        service.getScenegraph(PID, "stage-123", 5, true, true, List.of("text", "value"), false);

        var command = capturedCommand();
        assertEquals(AgentCommand.CommandType.GET_SCENEGRAPH, command.getCommand());
        assertEquals("stage-123", command.getParams().get("stageId"));
        assertEquals(5, command.getParams().get("depth"));
        assertEquals(true, command.getParams().get("includeBounds"));
        assertEquals(true, command.getParams().get("includeProperties"));
        assertEquals(List.of("text", "value"), command.getParams().get("propertyFilter"));
        assertEquals(false, command.getParams().get("includeTransforms"));
    }

    @Test
    void getNodeDetailsReturnsNodeData() throws Exception {
        var nodeData = Map.of(
                "node", Map.of("nodeId", 42, "type", "Button"),
                "properties", List.of(),
                "children", List.of()
        );
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(nodeData));

        var result = service.getNodeDetails(PID, 42, null);

        assertTrue((boolean) result.get("success"));
        assertTrue(result.containsKey("node"));
    }

    @Test
    void getNodeDetailsSendsNodeIdAndPropertyFilter() throws Exception {
        stubSuccessfulMapResponse();

        service.getNodeDetails(PID, 99999, List.of("text"));

        var command = capturedCommand();
        assertEquals(AgentCommand.CommandType.GET_NODE_DETAILS, command.getCommand());
        assertEquals(99999, command.getParams().get("nodeId"));
        assertEquals(List.of("text"), command.getParams().get("propertyFilter"));
    }

    @Test
    void findNodesReturnsMatches() throws Exception {
        var nodes = List.of(
                Map.of("nodeId", 12345, "type", "Button", "id", "submitBtn", "text", "Submit")
        );
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(nodes));

        var result = service.findNodes(PID, "Button", null, null, null, null);

        assertTrue((boolean) result.get("success"));
        assertEquals(nodes, result.get("data"));
    }

    @Test
    void findNodesSendsSearchCriteria() throws Exception {
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(List.of()));

        service.findNodes(PID, "TextField", "usernameField", "Enter", null, "stage-123");

        var command = capturedCommand();
        assertEquals(AgentCommand.CommandType.FIND_NODES, command.getCommand());
        assertEquals("TextField", command.getParams().get("type"));
        assertEquals("usernameField", command.getParams().get("id"));
        assertEquals("Enter", command.getParams().get("text"));
        assertEquals("stage-123", command.getParams().get("stageId"));
        assertFalse(command.getParams().containsKey("styleClass"));
    }

    @Test
    void setPropertyReturnsOldAndNewValues() throws Exception {
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of("oldValue", "old", "newValue", "new")));

        var result = service.setProperty(PID, 42, "text", "hello", "string");

        assertTrue((boolean) result.get("success"));
        assertEquals("old", result.get("oldValue"));
        assertEquals("new", result.get("newValue"));
    }

    @Test
    void setPropertySendsValueTypeWhenProvided() throws Exception {
        stubSuccessfulMapResponse();

        service.setProperty(PID, 42, "opacity", "0.5", "number");

        var command = capturedCommand();
        assertEquals(AgentCommand.CommandType.SET_PROPERTY, command.getCommand());
        assertEquals(42, command.getParams().get("nodeId"));
        assertEquals("opacity", command.getParams().get("propertyName"));
        assertEquals("0.5", command.getParams().get("value"));
        assertEquals("number", command.getParams().get("valueType"));
    }

    @Test
    void setPropertyOmitsValueTypeWhenAbsent() throws Exception {
        stubSuccessfulMapResponse();

        service.setProperty(PID, 42, "text", "hello", null);

        assertFalse(capturedCommand().getParams().containsKey("valueType"));
    }

    @Test
    void selectNodeReturnsHighlightState() throws Exception {
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of("highlighted", true)));

        var result = service.selectNode(PID, 42, true);

        assertTrue((boolean) result.get("success"));
        assertEquals(true, result.get("highlighted"));
    }

    @Test
    void selectNodeDefaultsToShowingBounds() throws Exception {
        stubSuccessfulMapResponse();

        service.selectNode(PID, 0, null);

        var command = capturedCommand();
        assertEquals(AgentCommand.CommandType.SELECT_NODE, command.getCommand());
        assertEquals(0, command.getParams().get("nodeId"));
        assertEquals(true, command.getParams().get("showBounds"));
    }

    @Test
    void clickNodeSendsNodeId() throws Exception {
        stubSuccessfulMapResponse();

        service.clickNode(PID, 42);

        var command = capturedCommand();
        assertEquals(AgentCommand.CommandType.CLICK_NODE, command.getCommand());
        assertEquals(42, command.getParams().get("nodeId"));
    }

    @Test
    void clickNodeSendsOptionalMode() throws Exception {
        stubSuccessfulMapResponse();

        service.clickNode(PID, 42, "synthetic");

        var command = capturedCommand();
        assertThat(command.getParams())
                .containsEntry("nodeId", 42)
                .containsEntry("mode", "synthetic");
    }

    @Test
    void activateNodeSendsNodeId() throws Exception {
        stubSuccessfulMapResponse();

        service.activateNode(PID, 42);

        var command = capturedCommand();
        assertThat(command.getCommand()).isEqualTo(AgentCommand.CommandType.ACTIVATE_NODE);
        assertThat(command.getParams()).containsEntry("nodeId", 42);
    }

    @Test
    void requestFocusSendsNodeId() throws Exception {
        stubSuccessfulMapResponse();

        service.requestFocus(PID, 77);

        var command = capturedCommand();
        assertEquals(AgentCommand.CommandType.REQUEST_FOCUS, command.getCommand());
        assertEquals(77, command.getParams().get("nodeId"));
    }

    @Test
    void typeKeySendsOptionalNodeId() throws Exception {
        stubSuccessfulMapResponse();

        service.typeKey(PID, "ENTER", 88);

        var command = capturedCommand();
        assertEquals(AgentCommand.CommandType.TYPE_KEY, command.getCommand());
        assertEquals("ENTER", command.getParams().get("key"));
        assertEquals(88, command.getParams().get("nodeId"));
    }

    @Test
    void typeKeyOmitsNodeIdWhenAbsent() throws Exception {
        stubSuccessfulMapResponse();

        service.typeKey(PID, "a", null);

        var command = capturedCommand();
        assertEquals("a", command.getParams().get("key"));
        assertFalse(command.getParams().containsKey("nodeId"));
    }

    @Test
    void takeScreenshotSendsNodeTargetAndDimensions() throws Exception {
        stubSuccessfulMapResponse();

        service.takeScreenshot(PID, 88, "stage-1", "/tmp/result.png", 640, 480);

        var command = capturedCommand();
        assertEquals(AgentCommand.CommandType.TAKE_SCREENSHOT, command.getCommand());
        assertEquals(88, command.getParams().get("nodeId"));
        assertEquals("stage-1", command.getParams().get("stageId"));
        assertEquals("/tmp/result.png", command.getParams().get("savePath"));
        assertEquals(640, command.getParams().get("maxWidth"));
        assertEquals(480, command.getParams().get("maxHeight"));
    }

    @Test
    void takeScreenshotOmitsAbsentOptionalParameters() throws Exception {
        stubSuccessfulMapResponse();

        service.takeScreenshot(PID, null, null, "/tmp/result.png", null, null);

        var params = capturedCommand().getParams();
        assertFalse(params.containsKey("nodeId"));
        assertFalse(params.containsKey("stageId"));
        assertFalse(params.containsKey("maxWidth"));
        assertFalse(params.containsKey("maxHeight"));
    }

    @Test
    void captureVideoSendsTargetTimingAndDimensions() throws Exception {
        stubSuccessfulMapResponse();

        service.captureVideo(PID, 88, "stage-1", "/tmp/clip.mp4", 12, 15, 640, 480);

        var command = capturedCommand();
        assertThat(command.getCommand()).isEqualTo(AgentCommand.CommandType.CAPTURE_VIDEO);
        assertThat(command.getParams()).containsAllEntriesOf(Map.of(
                "nodeId", 88,
                "stageId", "stage-1",
                "savePath", "/tmp/clip.mp4",
                "durationSeconds", 12,
                "framesPerSecond", 15,
                "maxWidth", 640,
                "maxHeight", 480));
    }

    @Test
    void captureVideoOmitsAbsentOptionalParameters() throws Exception {
        stubSuccessfulMapResponse();

        service.captureVideo(PID, null, null, "/tmp/clip.mp4", null, null, null, null);

        assertThat(capturedCommand().getParams())
                .containsOnly(Map.entry("savePath", "/tmp/clip.mp4"));
    }

    @Test
    void communicationFailureReturnsPidAndClosesConnection() throws Exception {
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenThrow(new Exception("Connection closed by agent"));

        var result = service.getStages(PID);

        assertFalse((boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("PID " + PID));
        assertTrue(result.get("error").toString().contains("Connection closed by agent"));
        verify(agent).disconnectWithoutShutdown();
    }

    @Test
    void agentErrorIsReturnedWithoutRewritingIt() throws Exception {
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.error("Node not found: 42"));

        var result = service.getNodeDetails(PID, 42, null);

        assertFalse((boolean) result.get("success"));
        assertEquals("Node not found: 42", result.get("error"));
    }

    private void stubSuccessfulMapResponse() throws Exception {
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of()));
    }

    private AgentCommand capturedCommand() throws Exception {
        var captor = ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        return captor.getValue();
    }

    private void assertCommandType(AgentCommand.CommandType commandType) throws Exception {
        assertEquals(commandType, capturedCommand().getCommand());
        verify(agent).connect();
        verify(agent).disconnectWithoutShutdown();
        verify(agent, never()).disconnect();
    }
}
