package io.github.k7t3.fxgraph.mcp.agent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages sessions to connected JavaFX applications.
 * Each session maps a session ID to a connected JavaFxAgent.
 */
@Component
public class SessionManager {

    private final Map<String, JavaFxAgent> sessions = new ConcurrentHashMap<>();

    /**
     * Register a new session.
     */
    public void register(String sessionId, JavaFxAgent agent) {
        sessions.put(sessionId, agent);
    }

    /**
     * Get an agent by session ID.
     */
    public JavaFxAgent get(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Remove a session and disconnect the agent.
     */
    public void remove(String sessionId) {
        JavaFxAgent agent = sessions.remove(sessionId);
        if (agent != null) {
            agent.disconnect();
        }
    }

    /**
     * Disconnect all sessions.
     */
    public void disconnectAll() {
        for (JavaFxAgent agent : sessions.values()) {
            agent.disconnect();
        }
        sessions.clear();
    }

    /**
     * Check if a session exists and is connected.
     */
    public boolean isActive(String sessionId) {
        JavaFxAgent agent = sessions.get(sessionId);
        return agent != null && agent.isConnected();
    }

    /**
     * Get all active session IDs.
     */
    public java.util.Set<String> getActiveSessions() {
        return sessions.keySet();
    }

    /**
     * Find a session ID by the PID of the connected JavaFX application.
     * Returns null if no session for the given PID exists.
     */
    public String findSessionIdByPid(String pid) {
        for (Map.Entry<String, JavaFxAgent> entry : sessions.entrySet()) {
            if (pid.equals(entry.getValue().getPid())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
