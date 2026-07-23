package io.github.k7t3.fxgraph.mcp.agent.packaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

class AgentJarIsolationTest {

    private static final Path AGENT_JAR = Path.of("build", "libs", "fxgraph-agent.jar");
    private static final String ORIGINAL_JACKSON_PACKAGE = "com/fasterxml/jackson/";
    private static final String ISOLATED_JACKSON_PACKAGE =
            "io/github/k7t3/fxgraph/mcp/agent/internal/jackson/";

    @Test
    @DisplayName("Should isolate Jackson packages in the agent JAR")
    void shouldIsolateJacksonPackagesInAgentJar() throws IOException {
        try (var jar = new JarFile(AGENT_JAR.toFile())) {
            var entries = jar.stream()
                    .map(entry -> entry.getName())
                    .toList();

            assertThat(entries)
                    .noneMatch(name -> name.startsWith(ORIGINAL_JACKSON_PACKAGE))
                    .anyMatch(name -> name.startsWith(ISOLATED_JACKSON_PACKAGE));
        }
    }
}
