---
name: fxgraph-inspect
description: >
  Inspect JavaFX application scene graphs at runtime using the fxgraph CLI tool.
  Use this skill to discover running JavaFX apps, retrieve window/stage lists,
  traverse scene graph trees, and read node properties. Best for understanding
  UI structure, finding nodeIds, and diagnosing layout or visibility issues.
tools:
  - bash
---

# fxgraph-inspect

Inspect running JavaFX application scene graphs using the `fxgraph-cli.jar` tool.

## Prerequisites

`fxgraph-cli.jar` and `fxgraph-agent.jar` must be in the same directory.

```bash
java -jar /path/to/fxgraph-cli.jar discover
```

## Typical workflow

### 1. Discover running JavaFX applications

```bash
java -jar fxgraph-cli.jar discover
```

Output: JSON array of `{ pid, mainClass, vmName, javaFX, connected }`.

```bash
# Get the first PID
PID=$(java -jar fxgraph-cli.jar discover | jq '.[0].pid')
```

### 2. List windows (Stages)

```bash
java -jar fxgraph-cli.jar $PID stages
```

Output: JSON array of `{ stageId, title, width, height, x, y, focused, rootNodeId }`.

```bash
# Get root node ID of the focused window
ROOT=$(java -jar fxgraph-cli.jar $PID stages | jq '.[] | select(.focused) | .rootNodeId')
```

### 3. Traverse the scene graph

```bash
# Compact tree (default)
java -jar fxgraph-cli.jar $PID scenegraph

# Limit depth and include bounds
java -jar fxgraph-cli.jar $PID scenegraph --depth 3 --bounds

# Include specific properties for all nodes
java -jar fxgraph-cli.jar $PID scenegraph --props --filter text,visible,disable

# Target a specific stage
java -jar fxgraph-cli.jar $PID scenegraph --stageId $STAGE_ID --depth 4
```

Each node: `{ nodeId, type, id, children, visible, styleClass, bounds?, properties? }`.

### 4. Get detailed node information

```bash
java -jar fxgraph-cli.jar $PID node-details $NODE_ID
```

Returns all properties with categories (layout, style, visual, content, interaction).

```bash
# Filter to specific properties
java -jar fxgraph-cli.jar $PID node-details $NODE_ID --filter text,style,visible,disable
```

## Tips

- `nodeId` is `System.identityHashCode()` — stable for the JVM session.
- Use `--depth 2` for a quick structural overview before drilling down.
- Use `--filter` to reduce JSON size when the full property set is large.
- `visible: false` nodes are still in the tree but not displayed.
- Pipe to `jq` for filtering: `jq '.children[].type'`, `jq '.. | .nodeId? // empty'`.

## Pipeline examples

```bash
# Find all Button nodeIds
java -jar fxgraph-cli.jar $PID scenegraph --props --filter text | \
  jq '[.. | select(.type? == "Button") | {nodeId, text: .properties.text}]'

# Check if a specific CSS ID exists
java -jar fxgraph-cli.jar $PID scenegraph | jq '.. | select(.id? == "submitBtn")'
```
