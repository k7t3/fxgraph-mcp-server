package io.github.k7t3.fxgraph.mcp.agent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response sent from injected agent back to the MCP server or CLI.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {

    private boolean success;
    private Object data;
    private String error;

    public AgentResponse() {}

    public static AgentResponse success(Object data) {
        AgentResponse r = new AgentResponse();
        r.success = true;
        r.data = data;
        return r;
    }

    public static AgentResponse error(String error) {
        AgentResponse r = new AgentResponse();
        r.success = false;
        r.error = error;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
