package io.github.k7t3.fxgraph.mcp.agent.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AgentCommand and AgentResponse protocol classes.
 * Verifies JSON serialization/deserialization round-trip behavior.
 */
class AgentProtocolTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // ===== AgentCommand Tests =====

    @Test
    void commandSerializationWithoutParams() throws Exception {
        AgentCommand cmd = new AgentCommand(AgentCommand.CommandType.PING);
        String json = mapper.writeValueAsString(cmd);

        assertTrue(json.contains("\"command\":\"PING\""));
        // params is null, should be excluded by @JsonInclude(NON_NULL)
        assertFalse(json.contains("\"params\""));
    }

    @Test
    void commandSerializationWithParams() throws Exception {
        Map<String, Object> params = Map.of("stageId", "123", "depth", 3);
        AgentCommand cmd = new AgentCommand(AgentCommand.CommandType.GET_SCENEGRAPH, params);
        String json = mapper.writeValueAsString(cmd);

        assertTrue(json.contains("\"command\":\"GET_SCENEGRAPH\""));
        assertTrue(json.contains("\"stageId\":\"123\""));
        assertTrue(json.contains("\"depth\":3"));
    }

    @Test
    void commandDeserializationRoundTrip() throws Exception {
        Map<String, Object> params = Map.of("nodeId", 42);
        AgentCommand original = new AgentCommand(AgentCommand.CommandType.GET_NODE_DETAILS, params);

        String json = mapper.writeValueAsString(original);
        AgentCommand deserialized = mapper.readValue(json, AgentCommand.class);

        assertEquals(AgentCommand.CommandType.GET_NODE_DETAILS, deserialized.getCommand());
        assertNotNull(deserialized.getParams());
        assertEquals(42, ((Number) deserialized.getParams().get("nodeId")).intValue());
    }

    @Test
    void commandDeserializationFromRawJson() throws Exception {
        String json = "{\"command\":\"SET_PROPERTY\",\"params\":{\"nodeId\":100,\"propertyName\":\"text\",\"value\":\"hello\"}}";
        AgentCommand cmd = mapper.readValue(json, AgentCommand.class);

        assertEquals(AgentCommand.CommandType.SET_PROPERTY, cmd.getCommand());
        assertEquals(100, ((Number) cmd.getParams().get("nodeId")).intValue());
        assertEquals("text", cmd.getParams().get("propertyName"));
        assertEquals("hello", cmd.getParams().get("value"));
    }

    @Test
    void commandDeserializationForTypeKey() throws Exception {
        String json = "{\"command\":\"TYPE_KEY\",\"params\":{\"nodeId\":100,\"key\":\"ENTER\"}}";
        AgentCommand cmd = mapper.readValue(json, AgentCommand.class);

        assertEquals(AgentCommand.CommandType.TYPE_KEY, cmd.getCommand());
        assertEquals(100, ((Number) cmd.getParams().get("nodeId")).intValue());
        assertEquals("ENTER", cmd.getParams().get("key"));
    }

    @Test
    void commandDeserializationForTakeScreenshot() throws Exception {
        String json = "{\"command\":\"TAKE_SCREENSHOT\",\"params\":{\"stageId\":\"123\"}}";
        AgentCommand cmd = mapper.readValue(json, AgentCommand.class);

        assertEquals(AgentCommand.CommandType.TAKE_SCREENSHOT, cmd.getCommand());
        assertEquals("123", cmd.getParams().get("stageId"));
    }

    @Test
    void allCommandTypesSerialize() throws Exception {
        for (AgentCommand.CommandType type : AgentCommand.CommandType.values()) {
            AgentCommand cmd = new AgentCommand(type);
            String json = mapper.writeValueAsString(cmd);
            AgentCommand deserialized = mapper.readValue(json, AgentCommand.class);
            assertEquals(type, deserialized.getCommand());
        }
    }

    @Test
    void commandDefaultConstructor() {
        AgentCommand cmd = new AgentCommand();
        assertNull(cmd.getCommand());
        assertNull(cmd.getParams());
    }

    @Test
    void commandSetters() {
        AgentCommand cmd = new AgentCommand();
        cmd.setCommand(AgentCommand.CommandType.SELECT_NODE);
        cmd.setParams(Map.of("nodeId", 999));

        assertEquals(AgentCommand.CommandType.SELECT_NODE, cmd.getCommand());
        assertEquals(999, ((Number) cmd.getParams().get("nodeId")).intValue());
    }

    // ===== AgentResponse Tests =====

    @Test
    void successResponseSerialization() throws Exception {
        AgentResponse resp = AgentResponse.success("pong");
        String json = mapper.writeValueAsString(resp);

        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"data\":\"pong\""));
        // error is null, should be excluded
        assertFalse(json.contains("\"error\""));
    }

    @Test
    void errorResponseSerialization() throws Exception {
        AgentResponse resp = AgentResponse.error("something went wrong");
        String json = mapper.writeValueAsString(resp);

        assertTrue(json.contains("\"success\":false"));
        assertTrue(json.contains("\"error\":\"something went wrong\""));
        // data is null, should be excluded
        assertFalse(json.contains("\"data\""));
    }

    @Test
    void successResponseWithMapData() throws Exception {
        Map<String, Object> data = Map.of("stageId", "abc", "title", "Window");
        AgentResponse resp = AgentResponse.success(data);
        String json = mapper.writeValueAsString(resp);

        AgentResponse deserialized = mapper.readValue(json, AgentResponse.class);
        assertTrue(deserialized.isSuccess());
        assertNull(deserialized.getError());
        assertNotNull(deserialized.getData());
    }

    @Test
    void responseDeserializationRoundTrip() throws Exception {
        AgentResponse original = AgentResponse.success(Map.of("highlighted", true));
        String json = mapper.writeValueAsString(original);
        AgentResponse deserialized = mapper.readValue(json, AgentResponse.class);

        assertTrue(deserialized.isSuccess());
        assertNull(deserialized.getError());
    }

    @Test
    void errorResponseDeserializationRoundTrip() throws Exception {
        AgentResponse original = AgentResponse.error("Node not found: 123");
        String json = mapper.writeValueAsString(original);
        AgentResponse deserialized = mapper.readValue(json, AgentResponse.class);

        assertFalse(deserialized.isSuccess());
        assertEquals("Node not found: 123", deserialized.getError());
        assertNull(deserialized.getData());
    }

    @Test
    void responseSetters() {
        AgentResponse resp = new AgentResponse();
        resp.setSuccess(true);
        resp.setData("test-data");
        resp.setError(null);

        assertTrue(resp.isSuccess());
        assertEquals("test-data", resp.getData());
        assertNull(resp.getError());
    }

    // ===== Line-delimited Protocol Simulation =====

    @Test
    void lineDelimitedProtocol() throws Exception {
        // Simulates the actual wire protocol: command JSON + newline -> response JSON + newline
        AgentCommand cmd = new AgentCommand(AgentCommand.CommandType.PING);
        String cmdLine = mapper.writeValueAsString(cmd);
        // Verify no newlines in the JSON itself
        assertFalse(cmdLine.contains("\n"));

        AgentResponse resp = AgentResponse.success("pong");
        String respLine = mapper.writeValueAsString(resp);
        assertFalse(respLine.contains("\n"));

        // Verify both can be parsed from a "line"
        AgentCommand parsedCmd = mapper.readValue(cmdLine, AgentCommand.class);
        AgentResponse parsedResp = mapper.readValue(respLine, AgentResponse.class);
        assertEquals(AgentCommand.CommandType.PING, parsedCmd.getCommand());
        assertTrue(parsedResp.isSuccess());
    }
}
