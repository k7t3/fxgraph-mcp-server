package io.github.k7t3.fxgraph.mcp.server;

import io.github.k7t3.fxgraph.mcp.tools.FxgraphService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider fxgraphTools(FxgraphService fxgraphService) {
        return MethodToolCallbackProvider.builder().toolObjects(fxgraphService).build();
    }
}
