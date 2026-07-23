package io.github.k7t3.fxgraph.mcp.agent.inspector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxGraphInspectorAgentLifecycleTest {

    @AfterEach
    void cleanUp() {
        FxGraphInspectorAgent.shutdown();
        System.clearProperty("fxgraph.agent.port");
    }

    @Test
    void shutdownClearsPublishedPortSoAgentCanBeStartedAgain() {
        FxGraphInspectorAgent.premain(null, null);

        assertTrue(FxGraphInspectorAgent.getPort() > 0);

        FxGraphInspectorAgent.shutdown();

        assertNull(System.getProperty("fxgraph.agent.port"));
        assertEquals(-1, FxGraphInspectorAgent.getPort());
    }
}
