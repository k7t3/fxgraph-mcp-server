package io.github.k7t3.fxgraph.mcp.tools;

import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.agent.SessionManager;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for FxgraphService.
 * Uses mocked JavaFxAgent to test the service layer independently of actual JVM attachment.
 */
@SuppressWarnings("unchecked")
class FxgraphServiceTest {

    private SessionManager sessionManager;
    private FxgraphService service;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        service = new FxgraphService(sessionManager);
    }

    // ===== discoverApplications =====

    @Test
    void discoverApplicationsReturnsResult() {
        // discoverApplications() is a static call to JavaFxAgent, hard to mock fully.
        // We just verify it doesn't throw and returns a valid structure.
        Map<String, Object> result = service.discoverApplications();
        assertNotNull(result);
        assertTrue(result.containsKey("success"));
    }

    // ===== connectApplication =====

    @Test
    void connectApplicationFailsForInvalidPid() {
        // Using PID 0 which is invalid
        Map<String, Object> result = service.connectApplication(0);
        assertNotNull(result);
        assertFalse((boolean) result.get("success"));
        assertNotNull(result.get("error"));
    }

    // ===== disconnectApplication =====

    @Test
    void disconnectApplicationWithInvalidSession() {
        Map<String, Object> result = service.disconnectApplication("nonexistent-session");
        assertFalse((boolean) result.get("success"));
        assertEquals("Session not found or already disconnected: nonexistent-session", result.get("error"));
    }

    @Test
    void disconnectApplicationSuccess() throws Exception {
        // Register a mock agent
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        sessionManager.register("session-1", agent);

        Map<String, Object> result = service.disconnectApplication("session-1");
        assertTrue((boolean) result.get("success"));
        // Session should be removed
        assertNull(sessionManager.get("session-1"));
    }

    // ===== getStages =====

    @Test
    void getStagesWithInvalidSession() {
        Map<String, Object> result = service.getStages("invalid");
        assertFalse((boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("Session not found"));
    }

    @Test
    void getStagesSuccess() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        List<Map<String, Object>> stages = List.of(
                Map.of("stageId", "123", "title", "Main", "width", 800.0, "height", 600.0)
        );
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(stages));
        sessionManager.register("session-1", agent);

        Map<String, Object> result = service.getStages("session-1");
        assertTrue((boolean) result.get("success"));
        assertNotNull(result.get("data"));
    }

    // ===== getScenegraph =====

    @Test
    void getScenegraphWithInvalidSession() {
        Map<String, Object> result = service.getScenegraph("invalid", null, null, null, null, null);
        assertFalse((boolean) result.get("success"));
    }

    @Test
    void getScenegraphSuccess() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        Map<String, Object> scenegraphData = Map.of(
                "stages", List.of(),
                "rootNodes", List.of(),
                "totalNodeCount", 0
        );
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(scenegraphData));
        sessionManager.register("session-1", agent);

        Map<String, Object> result = service.getScenegraph("session-1", null, 3, false, null, false);
        assertTrue((boolean) result.get("success"));
        assertTrue(result.containsKey("totalNodeCount"));
    }

    @Test
    void getScenegraphWithStageIdFilter() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of("stages", List.of(), "rootNodes", List.of())));
        sessionManager.register("session-1", agent);

        service.getScenegraph("session-1", "stage-123", 5, true, List.of("text", "value"), false);

        // Verify the command was sent with correct params
        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        AgentCommand cmd = captor.getValue();
        assertEquals(AgentCommand.CommandType.GET_SCENEGRAPH, cmd.getCommand());
        assertEquals("stage-123", cmd.getParams().get("stageId"));
        assertEquals(5, cmd.getParams().get("depth"));
        assertEquals(true, cmd.getParams().get("includeProperties"));
        assertEquals(List.of("text", "value"), cmd.getParams().get("propertyFilter"));
    }

    // ===== getNodeDetails =====

    @Test
    void getNodeDetailsWithInvalidSession() {
        Map<String, Object> result = service.getNodeDetails("invalid", 42, null);
        assertFalse((boolean) result.get("success"));
    }

    @Test
    void getNodeDetailsSuccess() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        Map<String, Object> nodeData = Map.of(
                "node", Map.of("nodeId", 42, "type", "Button"),
                "properties", List.of(),
                "children", List.of()
        );
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(nodeData));
        sessionManager.register("session-1", agent);

        Map<String, Object> result = service.getNodeDetails("session-1", 42, null);
        assertTrue((boolean) result.get("success"));
        assertTrue(result.containsKey("node"));
    }

    @Test
    void getNodeDetailsSendsCorrectNodeId() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of()));
        sessionManager.register("session-1", agent);

        service.getNodeDetails("session-1", 99999, List.of("text"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        assertEquals(AgentCommand.CommandType.GET_NODE_DETAILS, captor.getValue().getCommand());
        assertEquals(99999, captor.getValue().getParams().get("nodeId"));
        assertEquals(List.of("text"), captor.getValue().getParams().get("propertyFilter"));
    }

    // ===== setProperty =====

    @Test
    void setPropertyWithInvalidSession() {
        Map<String, Object> result = service.setProperty("invalid", 42, "text", "hello", null);
        assertFalse((boolean) result.get("success"));
    }

    @Test
    void setPropertySuccess() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of("oldValue", "old", "newValue", "new")));
        sessionManager.register("session-1", agent);

        Map<String, Object> result = service.setProperty("session-1", 42, "text", "hello", "string");
        assertTrue((boolean) result.get("success"));
        assertEquals("old", result.get("oldValue"));
        assertEquals("new", result.get("newValue"));
    }

    @Test
    void setPropertySendsCorrectParams() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of()));
        sessionManager.register("session-1", agent);

        service.setProperty("session-1", 42, "opacity", "0.5", "number");

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        AgentCommand cmd = captor.getValue();
        assertEquals(AgentCommand.CommandType.SET_PROPERTY, cmd.getCommand());
        assertEquals(42, cmd.getParams().get("nodeId"));
        assertEquals("opacity", cmd.getParams().get("propertyName"));
        assertEquals("0.5", cmd.getParams().get("value"));
        assertEquals("number", cmd.getParams().get("valueType"));
    }

    @Test
    void setPropertyWithoutValueType() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of()));
        sessionManager.register("session-1", agent);

        service.setProperty("session-1", 42, "text", "hello", null);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        // valueType should not be in params when null
        assertFalse(captor.getValue().getParams().containsKey("valueType"));
    }

    // ===== selectNode =====

    @Test
    void selectNodeWithInvalidSession() {
        Map<String, Object> result = service.selectNode("invalid", 42, null);
        assertFalse((boolean) result.get("success"));
    }

    @Test
    void selectNodeSuccess() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of("highlighted", true)));
        sessionManager.register("session-1", agent);

        Map<String, Object> result = service.selectNode("session-1", 42, true);
        assertTrue((boolean) result.get("success"));
        assertEquals(true, result.get("highlighted"));
    }

    @Test
    void selectNodeClearHighlight() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(Map.of("highlighted", true)));
        sessionManager.register("session-1", agent);

        service.selectNode("session-1", 0, null);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        assertEquals(0, captor.getValue().getParams().get("nodeId"));
        // showBounds defaults to true when null
        assertEquals(true, captor.getValue().getParams().get("showBounds"));
    }

    // ===== clickNode / requestFocus / typeKey =====

    @Test
    void clickNodeWithInvalidSession() {
        Map<String, Object> result = service.clickNode("invalid", 42);
        assertFalse((boolean) result.get("success"));
    }

    @Test
    void clickNodeSendsCorrectParams() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class))).thenReturn(AgentResponse.success(Map.of("clicked", true)));
        sessionManager.register("session-1", agent);

        service.clickNode("session-1", 42);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        assertEquals(AgentCommand.CommandType.CLICK_NODE, captor.getValue().getCommand());
        assertEquals(42, captor.getValue().getParams().get("nodeId"));
    }

    @Test
    void requestFocusSendsCorrectParams() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class))).thenReturn(AgentResponse.success(Map.of("focused", true)));
        sessionManager.register("session-1", agent);

        service.requestFocus("session-1", 77);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        assertEquals(AgentCommand.CommandType.REQUEST_FOCUS, captor.getValue().getCommand());
        assertEquals(77, captor.getValue().getParams().get("nodeId"));
    }

    @Test
    void typeKeyWithNodeIdSendsCorrectParams() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class))).thenReturn(AgentResponse.success(Map.of("typed", true)));
        sessionManager.register("session-1", agent);

        service.typeKey("session-1", "ENTER", 88);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        assertEquals(AgentCommand.CommandType.TYPE_KEY, captor.getValue().getCommand());
        assertEquals("ENTER", captor.getValue().getParams().get("key"));
        assertEquals(88, captor.getValue().getParams().get("nodeId"));
    }

    @Test
    void typeKeyWithoutNodeIdOmitsNodeParam() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class))).thenReturn(AgentResponse.success(Map.of("typed", true)));
        sessionManager.register("session-1", agent);

        service.typeKey("session-1", "a", null);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        assertEquals("a", captor.getValue().getParams().get("key"));
        assertFalse(captor.getValue().getParams().containsKey("nodeId"));
    }

    @Test
    void takeScreenshotWithNodeIdSendsCorrectParams() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class))).thenReturn(AgentResponse.success(Map.of("savedPath", "/tmp/a.png")));
        sessionManager.register("session-1", agent);

        service.takeScreenshot("session-1", 88, null, "/tmp/a.png");

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        assertEquals(AgentCommand.CommandType.TAKE_SCREENSHOT, captor.getValue().getCommand());
        assertEquals(88, captor.getValue().getParams().get("nodeId"));
        assertFalse(captor.getValue().getParams().containsKey("stageId"));
        assertEquals("/tmp/a.png", captor.getValue().getParams().get("savePath"));
    }

    @Test
    void takeScreenshotScenegraphWithStageIdSendsCorrectParams() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class))).thenReturn(AgentResponse.success(Map.of("savedPath", "/tmp/b.png")));
        sessionManager.register("session-1", agent);

        service.takeScreenshot("session-1", null, "stage-1", "/tmp/b.png");

        var captor = org.mockito.ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captor.capture());
        assertEquals(AgentCommand.CommandType.TAKE_SCREENSHOT, captor.getValue().getCommand());
        assertEquals("stage-1", captor.getValue().getParams().get("stageId"));
        assertFalse(captor.getValue().getParams().containsKey("nodeId"));
        assertEquals("/tmp/b.png", captor.getValue().getParams().get("savePath"));
    }

    // ===== Agent communication error handling =====

    @Test
    void agentCommunicationError() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenThrow(new Exception("Connection closed by agent"));
        sessionManager.register("session-1", agent);

        Map<String, Object> result = service.getStages("session-1");
        assertFalse((boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("Communication error"));
    }

    @Test
    void agentReturnsError() throws Exception {
        JavaFxAgent agent = mock(JavaFxAgent.class);
        when(agent.isConnected()).thenReturn(true);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.error("Node not found: 42"));
        sessionManager.register("session-1", agent);

        Map<String, Object> result = service.getNodeDetails("session-1", 42, null);
        assertFalse((boolean) result.get("success"));
        assertEquals("Node not found: 42", result.get("error"));
    }
}
