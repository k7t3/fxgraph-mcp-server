# Skills

This directory contains Agent Skills for use with the FXGraph CLI tool.

## Available Skills

| Skill | Description |
|-------|-------------|
| [`fxgraph/`](fxgraph/SKILL.md) | Inspect and interact with running JavaFX applications |

## Building and Installing the CLI JARs

The `fxgraph` skill requires `fxgraph-cli.jar` and `fxgraph-agent.jar` in `skills/fxgraph/scripts/`.
Use the `installSkillJars` Gradle task to build and deploy both in one step.

### Prerequisites

- Java 21+
- Gradle wrapper included in the repository (`./gradlew`)

### Installation

```bash
# Run from the project root — builds the JARs and copies them to skills/fxgraph/scripts/
./gradlew :fxgraph-cli:installSkillJars
```

This task:
1. Compiles and packages `fxgraph-cli.jar` (via `shadowJar`)
2. Copies `fxgraph-agent.jar` alongside the CLI jar (via `copyAgentJar`)
3. Deploys both JARs to `skills/fxgraph/scripts/`

After the task completes, the directory will contain:
```
skills/fxgraph/scripts/
├── fxgraph             # Executable wrapper script
├── fxgraph-cli.jar     # CLI tool
└── fxgraph-agent.jar   # Agent injected into the target JVM
```

> **Note:** The JAR files are excluded from version control (`.gitignore`).
> Re-run `installSkillJars` after any source changes.

### Quick Start

```bash
# Build and install
./gradlew :fxgraph-cli:installSkillJars

# Use the wrapper script directly (recommended)
CLI="<path-to-skill>/scripts/fxgraph"

# List running JavaFX processes
$CLI discover

# Explore the scene graph of the first found process
PID=$($CLI discover | jq '.[0].pid')
$CLI $PID scenegraph --depth 3
```

### Notes

- `fxgraph-agent.jar` is automatically injected into the target JVM on the first command for a given PID — no manual step required.
- The agent uses the Java Attach API. Some JVM configurations may require `--add-opens` flags. See the project `README.md` if you encounter `AttachNotSupportedException`.
- To rebuild after source changes, run the same `./gradlew :fxgraph-cli:installSkillJars` command.
