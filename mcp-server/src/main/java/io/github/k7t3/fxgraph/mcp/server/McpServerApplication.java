package io.github.k7t3.fxgraph.mcp.server;

import io.github.k7t3.fxgraph.mcp.tools.FxgraphService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot entry point for the STDIO-based MCP server.
 */
@SpringBootApplication
@ComponentScan(basePackages = "io.github.k7t3.fxgraph.mcp")
public class McpServerApplication {

    /**
     * Starts the Spring-based MCP server.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        run(args);
    }

    /**
     * Starts the Spring-based MCP server.
     *
     * @param args application arguments
     */
    public static void run(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    /**
     * Registers the FXGraph MCP tools with Spring AI.
     *
     * @param fxgraphService tool service implementation
     * @return tool callback provider for the MCP server
     */
    @Bean
    public ToolCallbackProvider fxgraphTools(FxgraphService fxgraphService) {
        return MethodToolCallbackProvider.builder().toolObjects(fxgraphService).build();
    }
}
