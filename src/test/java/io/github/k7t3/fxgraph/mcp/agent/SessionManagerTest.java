package io.github.k7t3.fxgraph.mcp.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionManager.
 * Uses a mock JavaFxAgent to avoid real JVM attachment.
 */
class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    @Test
    void registerAndGet() {
        JavaFxAgent agent = new JavaFxAgent("12345");
        sessionManager.register("session-1", agent);

        assertSame(agent, sessionManager.get("session-1"));
    }

    @Test
    void getNonexistentSession() {
        assertNull(sessionManager.get("nonexistent"));
    }

    @Test
    void isActiveReturnsFalseForNonexistent() {
        assertFalse(sessionManager.isActive("nonexistent"));
    }

    @Test
    void isActiveReturnsFalseForDisconnectedAgent() {
        // JavaFxAgent starts disconnected (connected = false)
        JavaFxAgent agent = new JavaFxAgent("12345");
        sessionManager.register("session-1", agent);

        assertFalse(sessionManager.isActive("session-1"));
    }

    @Test
    void removeSession() {
        JavaFxAgent agent = new JavaFxAgent("12345");
        sessionManager.register("session-1", agent);

        sessionManager.remove("session-1");

        assertNull(sessionManager.get("session-1"));
    }

    @Test
    void removeNonexistentSessionDoesNotThrow() {
        assertDoesNotThrow(() -> sessionManager.remove("nonexistent"));
    }

    @Test
    void getActiveSessions() {
        sessionManager.register("session-1", new JavaFxAgent("111"));
        sessionManager.register("session-2", new JavaFxAgent("222"));

        var sessions = sessionManager.getActiveSessions();
        assertEquals(2, sessions.size());
        assertTrue(sessions.contains("session-1"));
        assertTrue(sessions.contains("session-2"));
    }

    @Test
    void disconnectAllClearsAllSessions() {
        sessionManager.register("session-1", new JavaFxAgent("111"));
        sessionManager.register("session-2", new JavaFxAgent("222"));

        sessionManager.disconnectAll();

        assertTrue(sessionManager.getActiveSessions().isEmpty());
        assertNull(sessionManager.get("session-1"));
        assertNull(sessionManager.get("session-2"));
    }

    @Test
    void registerOverwritesExistingSession() {
        JavaFxAgent agent1 = new JavaFxAgent("111");
        JavaFxAgent agent2 = new JavaFxAgent("222");

        sessionManager.register("session-1", agent1);
        sessionManager.register("session-1", agent2);

        assertSame(agent2, sessionManager.get("session-1"));
    }
}
