package io.github.k7t3.fxgraph.mcp.cli;

import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for CliCommandDispatcher.
 * Uses a mock JavaFxAgent to verify argument parsing and command dispatch without real JVM connections.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CliCommandDispatcherTest {

    @Mock
    private JavaFxAgent mockAgent;

    private CliCommandDispatcher dispatcher;

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        dispatcher = new CliCommandDispatcher(pid -> mockAgent);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ===================================================
    // Argument routing / error cases
    // ===================================================

    @Test
    void unknownFirstArgReturnsFailure() {
        int code = dispatcher.dispatch(new String[]{"unknown"});
        assertEquals(1, code);
        assertTrue(errContent.toString().contains("Error:"));
    }

    @Test
    void pidWithoutCommandReturnsFailure() {
        int code = dispatcher.dispatch(new String[]{"12345"});
        assertEquals(1, code);
        assertTrue(errContent.toString().contains("Error:"));
    }

    @Test
    void unknownCommandReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "bogus"});
        assertEquals(1, code);
        assertTrue(errContent.toString().contains("Error:"));
    }

    @Test
    void connectFailureReturnsFailure() throws Exception {
        doThrow(new RuntimeException("attach failed")).when(mockAgent).connect();
        int code = dispatcher.dispatch(new String[]{"12345", "stages"});
        assertEquals(1, code);
        assertTrue(errContent.toString().contains("Failed to connect"));
    }

    @Test
    void agentErrorResponseReturnsFailure() throws Exception {
        when(mockAgent.sendCommand(any())).thenReturn(AgentResponse.error("node not found"));
        int code = dispatcher.dispatch(new String[]{"12345", "stages"});
        assertEquals(1, code);
        assertTrue(errContent.toString().contains("Error:"));
    }

    @Test
    void agentExceptionReturnsFailure() throws Exception {
        when(mockAgent.sendCommand(any())).thenThrow(new RuntimeException("socket error"));
        int code = dispatcher.dispatch(new String[]{"12345", "stages"});
        assertEquals(1, code);
    }

    @Test
    void disconnectWithoutShutdownCalledEvenOnSuccess() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "stages"});
        verify(mockAgent).disconnectWithoutShutdown();
    }

    @Test
    void disconnectWithoutShutdownCalledEvenOnCommandError() throws Exception {
        when(mockAgent.sendCommand(any())).thenThrow(new RuntimeException("oops"));
        dispatcher.dispatch(new String[]{"12345", "stages"});
        verify(mockAgent).disconnectWithoutShutdown();
    }

    // ===================================================
    // stages
    // ===================================================

    @Test
    void stagesDispatchesGetStages() throws Exception {
        successResponse();
        int code = dispatcher.dispatch(new String[]{"12345", "stages"});
        assertEquals(0, code);
        AgentCommand cmd = captureCommand();
        assertEquals(AgentCommand.CommandType.GET_STAGES, cmd.getCommand());
        assertNull(cmd.getParams());
    }

    // ===================================================
    // scenegraph
    // ===================================================

    @Test
    void scenegraphNoOptionsHasNullParams() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "scenegraph"});
        AgentCommand cmd = captureCommand();
        assertEquals(AgentCommand.CommandType.GET_SCENEGRAPH, cmd.getCommand());
        assertNull(cmd.getParams());
    }

    @Test
    void scenegraphWithBoundsAndProps() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "scenegraph", "--bounds", "--props"});
        AgentCommand cmd = captureCommand();
        Map<String, Object> params = cmd.getParams();
        assertEquals(true, params.get("includeBounds"));
        assertEquals(true, params.get("includeProperties"));
    }

    @Test
    void scenegraphWithDepth() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "scenegraph", "--depth", "5"});
        AgentCommand cmd = captureCommand();
        assertEquals(5, cmd.getParams().get("depth"));
    }

    @Test
    void scenegraphWithStageId() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "scenegraph", "--stageId", "abc"});
        AgentCommand cmd = captureCommand();
        assertEquals("abc", cmd.getParams().get("stageId"));
    }

    @Test
    void scenegraphWithFilter() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "scenegraph", "--filter", "text,value"});
        AgentCommand cmd = captureCommand();
        List<String> filter = (List<String>) cmd.getParams().get("propertyFilter");
        assertEquals(List.of("text", "value"), filter);
    }

    @Test
    void scenegraphWithTransforms() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "scenegraph", "--transforms"});
        AgentCommand cmd = captureCommand();
        assertEquals(true, cmd.getParams().get("includeTransforms"));
    }

    @Test
    void scenegraphUnknownOptionReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "scenegraph", "--unknown"});
        assertEquals(1, code);
    }

    // ===================================================
    // node-details
    // ===================================================

    @Test
    void nodeDetailsMissingNodeIdReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "node-details"});
        assertEquals(1, code);
    }

    @Test
    void nodeDetailsSendsCorrectNodeId() throws Exception {
        successResponse();
        int code = dispatcher.dispatch(new String[]{"12345", "node-details", "123"});
        assertEquals(0, code);
        AgentCommand cmd = captureCommand();
        assertEquals(AgentCommand.CommandType.GET_NODE_DETAILS, cmd.getCommand());
        assertEquals(123, cmd.getParams().get("nodeId"));
    }

    @Test
    void nodeDetailsWithFilter() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "node-details", "123", "--filter", "text"});
        AgentCommand cmd = captureCommand();
        List<String> filter = (List<String>) cmd.getParams().get("propertyFilter");
        assertEquals(List.of("text"), filter);
    }

    @Test
    void nodeDetailsInvalidNodeIdReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "node-details", "abc"});
        assertEquals(1, code);
    }

    @Test
    void nodeDetailsWithPropsHintsAtFilter() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "node-details", "123", "--props"});
        assertEquals(1, code);
        String err = errContent.toString();
        assertTrue(err.contains("--props"), "error should mention --props");
        assertTrue(err.contains("--filter"), "error should suggest --filter as the alternative");
    }

    @Test
    void scenegraphWithJsonHintsNoFlagNeeded() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "scenegraph", "--json"});
        assertEquals(1, code);
        String err = errContent.toString();
        assertTrue(err.contains("--json"), "error should mention --json");
        assertTrue(err.contains("JSON"), "error should clarify JSON is always the output format");
    }

    // ===================================================
    // set-property
    // ===================================================

    @Test
    void setPropertyTooFewArgsReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "set-property", "123", "text"});
        assertEquals(1, code);
    }

    @Test
    void setPropertySendsCorrectParams() throws Exception {
        successResponse();
        int code = dispatcher.dispatch(new String[]{"12345", "set-property", "123", "text", "Hello"});
        assertEquals(0, code);
        AgentCommand cmd = captureCommand();
        assertEquals(AgentCommand.CommandType.SET_PROPERTY, cmd.getCommand());
        assertEquals(123, cmd.getParams().get("nodeId"));
        assertEquals("text", cmd.getParams().get("propertyName"));
        assertEquals("Hello", cmd.getParams().get("value"));
        assertNull(cmd.getParams().get("valueType"));
    }

    @Test
    void setPropertyWithType() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "set-property", "123", "fill", "red", "--type", "color"});
        AgentCommand cmd = captureCommand();
        assertEquals("color", cmd.getParams().get("valueType"));
    }

    // ===================================================
    // select-node
    // ===================================================

    @Test
    void selectNodeMissingNodeIdReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "select-node"});
        assertEquals(1, code);
    }

    @Test
    void selectNodeDefaultShowBoundsTrue() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "select-node", "99"});
        AgentCommand cmd = captureCommand();
        assertEquals(AgentCommand.CommandType.SELECT_NODE, cmd.getCommand());
        assertEquals(99, cmd.getParams().get("nodeId"));
        assertEquals(true, cmd.getParams().get("showBounds"));
    }

    @Test
    void selectNodeNoBoundsFlag() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "select-node", "99", "--no-bounds"});
        AgentCommand cmd = captureCommand();
        assertEquals(false, cmd.getParams().get("showBounds"));
    }

    // ===================================================
    // click-node
    // ===================================================

    @Test
    void clickNodeMissingNodeIdReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "click-node"});
        assertEquals(1, code);
    }

    @Test
    void clickNodeSendsCorrectNodeId() throws Exception {
        successResponse();
        int code = dispatcher.dispatch(new String[]{"12345", "click-node", "42"});
        assertEquals(0, code);
        AgentCommand cmd = captureCommand();
        assertEquals(AgentCommand.CommandType.CLICK_NODE, cmd.getCommand());
        assertEquals(42, cmd.getParams().get("nodeId"));
    }

    // ===================================================
    // focus
    // ===================================================

    @Test
    void focusMissingNodeIdReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "focus"});
        assertEquals(1, code);
    }

    @Test
    void focusSendsCorrectNodeId() throws Exception {
        successResponse();
        int code = dispatcher.dispatch(new String[]{"12345", "focus", "42"});
        assertEquals(0, code);
        AgentCommand cmd = captureCommand();
        assertEquals(AgentCommand.CommandType.REQUEST_FOCUS, cmd.getCommand());
        assertEquals(42, cmd.getParams().get("nodeId"));
    }

    // ===================================================
    // type-key
    // ===================================================

    @Test
    void typeKeyMissingKeyReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "type-key"});
        assertEquals(1, code);
    }

    @Test
    void typeKeyNoNodeId() throws Exception {
        successResponse();
        int code = dispatcher.dispatch(new String[]{"12345", "type-key", "ENTER"});
        assertEquals(0, code);
        AgentCommand cmd = captureCommand();
        assertEquals(AgentCommand.CommandType.TYPE_KEY, cmd.getCommand());
        assertEquals("ENTER", cmd.getParams().get("key"));
        assertNull(cmd.getParams().get("nodeId"));
    }

    @Test
    void typeKeyWithNodeId() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "type-key", "a", "--nodeId", "123"});
        AgentCommand cmd = captureCommand();
        assertEquals("a", cmd.getParams().get("key"));
        assertEquals(123, cmd.getParams().get("nodeId"));
    }

    // ===================================================
    // screenshot
    // ===================================================

    @Test
    void screenshotMissingPathReturnsFailure() throws Exception {
        int code = dispatcher.dispatch(new String[]{"12345", "screenshot"});
        assertEquals(1, code);
    }

    @Test
    void screenshotSendsSavePath() throws Exception {
        successResponse();
        int code = dispatcher.dispatch(new String[]{"12345", "screenshot", "/tmp/test.png"});
        assertEquals(0, code);
        AgentCommand cmd = captureCommand();
        assertEquals(AgentCommand.CommandType.TAKE_SCREENSHOT, cmd.getCommand());
        assertEquals("/tmp/test.png", cmd.getParams().get("savePath"));
    }

    @Test
    void screenshotWithNodeId() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "screenshot", "/tmp/t.png", "--nodeId", "999"});
        AgentCommand cmd = captureCommand();
        assertEquals(999, cmd.getParams().get("nodeId"));
    }

    @Test
    void screenshotWithStageId() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "screenshot", "/tmp/t.png", "--stageId", "s1"});
        AgentCommand cmd = captureCommand();
        assertEquals("s1", cmd.getParams().get("stageId"));
    }

    @Test
    void screenshotWithMaxWidth() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "screenshot", "/tmp/t.png", "--maxWidth", "800"});
        AgentCommand cmd = captureCommand();
        assertEquals(800, cmd.getParams().get("maxWidth"));
    }

    @Test
    void screenshotWithMaxHeight() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "screenshot", "/tmp/t.png", "--maxHeight", "600"});
        AgentCommand cmd = captureCommand();
        assertEquals(600, cmd.getParams().get("maxHeight"));
    }

    @Test
    void screenshotWithBothMaxDimensions() throws Exception {
        successResponse();
        dispatcher.dispatch(new String[]{"12345", "screenshot", "/tmp/t.png", "--maxWidth", "640", "--maxHeight", "480"});
        AgentCommand cmd = captureCommand();
        assertEquals(640, cmd.getParams().get("maxWidth"));
        assertEquals(480, cmd.getParams().get("maxHeight"));
    }

    // ===================================================
    // Helpers
    // ===================================================

    private void successResponse() throws Exception {
        when(mockAgent.sendCommand(any())).thenReturn(AgentResponse.success(Map.of("ok", true)));
    }

    private AgentCommand captureCommand() throws Exception {
        ArgumentCaptor<AgentCommand> captor = ArgumentCaptor.forClass(AgentCommand.class);
        verify(mockAgent).sendCommand(captor.capture());
        return captor.getValue();
    }
}
