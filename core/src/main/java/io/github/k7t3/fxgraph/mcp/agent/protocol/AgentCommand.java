package io.github.k7t3.fxgraph.mcp.agent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Command sent from MCP server or CLI to the injected agent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCommand {

    public enum CommandType {
        GET_STAGES,
        GET_SCENEGRAPH,
        GET_NODE_DETAILS,
        SET_PROPERTY,
        SELECT_NODE,
        CLICK_NODE,
        REQUEST_FOCUS,
        TYPE_KEY,
        TAKE_SCREENSHOT,
        PING,
        SHUTDOWN
    }

    private CommandType command;
    private Map<String, Object> params;

    public AgentCommand() {}

    public AgentCommand(CommandType command) {
        this.command = command;
    }

    public AgentCommand(CommandType command, Map<String, Object> params) {
        this.command = command;
        this.params = params;
    }

    public CommandType getCommand() { return command; }
    public void setCommand(CommandType command) { this.command = command; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
