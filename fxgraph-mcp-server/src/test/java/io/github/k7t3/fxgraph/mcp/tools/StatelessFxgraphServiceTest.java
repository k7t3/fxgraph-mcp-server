package io.github.k7t3.fxgraph.mcp.tools;

import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentCommand;
import io.github.k7t3.fxgraph.mcp.agent.protocol.AgentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatelessFxgraphServiceTest {

    private static final Set<String> PID_TARGETED_TOOLS = Set.of(
            "connectApplication",
            "disconnectApplication",
            "getStages",
            "getScenegraph",
            "getNodeDetails",
            "findNodes",
            "setProperty",
            "selectNode",
            "clickNode",
            "requestFocus",
            "typeKey",
            "takeScreenshot",
            "captureVideo"
    );

    @Test
    void everyTargetedToolAcceptsPid() {
        for (var toolName : PID_TARGETED_TOOLS) {
            var hasPidOverload = List.of(FxgraphService.class.getDeclaredMethods()).stream()
                    .filter(method -> method.isAnnotationPresent(Tool.class))
                    .filter(method -> method.getName().equals(toolName))
                    .map(Method::getParameterTypes)
                    .anyMatch(parameterTypes -> parameterTypes.length > 0 && parameterTypes[0] == int.class);

            assertTrue(hasPidOverload, () -> toolName + " must accept pid as its first parameter");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void getStagesConnectsForOneCallAndLeavesInjectedAgentRunning() throws Exception {
        var requestedPid = new AtomicInteger();
        var agent = mock(JavaFxAgent.class);
        when(agent.sendCommand(any(AgentCommand.class)))
                .thenReturn(AgentResponse.success(List.of()));
        var service = new FxgraphService(pid -> {
            requestedPid.set(pid);
            return agent;
        });
        var method = findMethod("getStages", int.class);

        var result = (Map<String, Object>) method.invoke(service, 12345);

        assertTrue((boolean) result.get("success"));
        assertEquals(12345, requestedPid.get());
        verify(agent).connect();
        verify(agent).sendCommand(any(AgentCommand.class));
        verify(agent).disconnectWithoutShutdown();
    }

    @Test
    void connectApplicationDoesNotCreateServerSideSession() throws Exception {
        var agent = mock(JavaFxAgent.class);
        when(agent.getAgentPort()).thenReturn(54321);
        var service = new FxgraphService(pid -> agent);

        var result = service.connectApplication(12345);

        assertTrue((boolean) result.get("success"));
        assertFalse(result.containsKey("sessionId"));
        assertEquals(54321, result.get("agentPort"));
        verify(agent).connect();
        verify(agent).disconnectWithoutShutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    void disconnectApplicationShutsDownAgentByPid() throws Exception {
        var requestedPid = new AtomicInteger();
        var agent = mock(JavaFxAgent.class);
        var service = new FxgraphService(pid -> {
            requestedPid.set(pid);
            return agent;
        });
        var method = findMethod("disconnectApplication", int.class);

        var result = (Map<String, Object>) method.invoke(service, 12345);

        assertTrue((boolean) result.get("success"));
        assertEquals(12345, requestedPid.get());
        verify(agent).connect();
        verify(agent).disconnect();
    }

    private static Method findMethod(String name, Class<?>... parameterTypes) {
        try {
            return FxgraphService.class.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(name + " must expose a PID-based tool method", e);
        }
    }
}
