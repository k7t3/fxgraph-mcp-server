package io.github.k7t3.fxgraph.mcp.tools;

import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IssueToolsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("commands")
    void toolsForwardParametersAndCloseTransientConnection(String method, Object[] args,
                                                          String command, Map<String, Object> params) throws Exception {
        var agent = mock(JavaFxAgent.class);
        when(agent.sendCommand(any())).thenReturn(AgentResponse.success(Map.of("accepted", true)));
        var service = new FxgraphService(pid -> agent);

        var tool = Arrays.stream(FxgraphService.class.getMethods()).filter(m -> m.getName().equals(method)
                && m.getParameterCount() == args.length && m.isAnnotationPresent(Tool.class)).findFirst();
        var result = tool.isPresent() ? (Map<?, ?>) tool.get().invoke(service, args)
                : Map.of("success", false, "error", "MCP tool unavailable: " + method);

        assertThat(result.get("success")).as(method).isEqualTo(true);
        var captured = ArgumentCaptor.forClass(AgentCommand.class);
        verify(agent).sendCommand(captured.capture());
        assertThat(captured.getValue().getCommand().name()).isEqualTo(command);
        assertThat(captured.getValue().getParams()).containsExactlyInAnyOrderEntriesOf(params);
        verify(agent).connect();
        verify(agent).disconnectWithoutShutdown();
    }

    static Stream<Arguments> commands() {
        return Stream.of(
                Arguments.of("getWindowDetails", new Object[]{12345, "s"}, "GET_WINDOW_DETAILS", Map.of("stageId", "s")),
                Arguments.of("setWindowProperty", new Object[]{12345, "s", "x", "100", "number"}, "SET_WINDOW_PROPERTY",
                        Map.of("stageId", "s", "propertyName", "x", "value", "100", "valueType", "number")),
                Arguments.of("closeWindow", new Object[]{12345, "s"}, "CLOSE_WINDOW", Map.of("stageId", "s")),
                Arguments.of("closePopup", new Object[]{12345, "p"}, "CLOSE_POPUP", Map.of("stageId", "p")),
                Arguments.of("scrollNode", new Object[]{12345, 42, -20.0, 500.0, null}, "SCROLL_NODE",
                        Map.of("nodeId", 42, "dx", -20.0, "dy", 500.0)),
                Arguments.of("scrollToIndex", new Object[]{12345, 42, 10}, "SCROLL_TO_INDEX", Map.of("nodeId", 42, "index", 10)),
                Arguments.of("getNodeDetails", new Object[]{12345, 42, List.of("text"), true}, "GET_NODE_DETAILS",
                        Map.of("nodeId", 42, "propertyFilter", List.of("text"), "includeAncestors", true)),
                Arguments.of("findNodes", new Object[]{12345, "Button", null, null, null, "s", true}, "FIND_NODES",
                        Map.of("type", "Button", "stageId", "s", "effectiveVisible", true))
        );
    }
}
