---
name: fxgraph-interact
description: >
  Interact with running JavaFX applications using the fxgraph CLI tool.
  Use this skill to modify node properties, simulate mouse clicks and keyboard input,
  highlight nodes with a visual overlay, request focus, and capture screenshots.
  Best for automated UI testing, live property editing, and visual verification.
tools:
  - bash
---

# fxgraph-interact

Interact with running JavaFX application UIs using the `fxgraph-cli.jar` tool.

## Prerequisites

`fxgraph-cli.jar` and `fxgraph-agent.jar` must be in the same directory.
Obtain a `nodeId` using the `fxgraph-inspect` skill or `node-details` command first.

## Commands

### Set a property

```bash
java -jar fxgraph-cli.jar $PID set-property $NODE_ID text "Hello World"
java -jar fxgraph-cli.jar $PID set-property $NODE_ID visible false   --type boolean
java -jar fxgraph-cli.jar $PID set-property $NODE_ID opacity 0.5     --type number
java -jar fxgraph-cli.jar $PID set-property $NODE_ID style "-fx-background-color: red;"
java -jar fxgraph-cli.jar $PID set-property $NODE_ID textFill "#FF0000" --type color
```

`--type` hint: `string` (default), `number`, `boolean`, `color`.
Returns `{ success, oldValue, newValue }`.

### Highlight a node (visual overlay)

```bash
# Highlight with red border overlay
java -jar fxgraph-cli.jar $PID select-node $NODE_ID

# Highlight without bounds rectangle
java -jar fxgraph-cli.jar $PID select-node $NODE_ID --no-bounds

# Clear highlight
java -jar fxgraph-cli.jar $PID select-node 0
```

### Click a node

```bash
java -jar fxgraph-cli.jar $PID click-node $NODE_ID
```

Fires a JavaFX `MouseEvent.MOUSE_CLICKED` on the node's center.

### Set keyboard focus

```bash
java -jar fxgraph-cli.jar $PID focus $NODE_ID
```

### Type a key

```bash
# Type into the currently focused node
java -jar fxgraph-cli.jar $PID type-key ENTER
java -jar fxgraph-cli.jar $PID type-key a

# Type into a specific node
java -jar fxgraph-cli.jar $PID type-key TAB --nodeId $NODE_ID
```

Key names: standard `KeyCode` names (`ENTER`, `SPACE`, `TAB`, `BACK_SPACE`, `DELETE`,
`UP`, `DOWN`, `LEFT`, `RIGHT`, `F1`…`F12`) or a single character (`a`, `1`, etc.).

### Take a screenshot

```bash
# Full scene of the first/only window
java -jar fxgraph-cli.jar $PID screenshot ./screenshot.png

# Specific node
java -jar fxgraph-cli.jar $PID screenshot ./node.png --nodeId $NODE_ID

# Specific stage
java -jar fxgraph-cli.jar $PID screenshot ./stage.png --stageId $STAGE_ID
```

Returns `{ success, path }`.

## Typical interaction workflow

```bash
# 1. Discover app and get PID
PID=$(java -jar fxgraph-cli.jar discover | jq '.[0].pid')

# 2. Find the target node
NODE_ID=$(java -jar fxgraph-cli.jar $PID scenegraph --props --filter text \
  | jq '.. | select(.type? == "TextField") | .nodeId' | head -1)

# 3. Highlight to confirm we found the right node
java -jar fxgraph-cli.jar $PID select-node $NODE_ID

# 4. Take a before screenshot
java -jar fxgraph-cli.jar $PID screenshot ./before.png

# 5. Focus and type
java -jar fxgraph-cli.jar $PID focus $NODE_ID
java -jar fxgraph-cli.jar $PID type-key a --nodeId $NODE_ID

# 6. Set a property directly
java -jar fxgraph-cli.jar $PID set-property $NODE_ID text "new value"

# 7. Confirm result with screenshot
java -jar fxgraph-cli.jar $PID screenshot ./after.png

# 8. Clear highlight
java -jar fxgraph-cli.jar $PID select-node 0
```

## Tips

- Always verify changes with a screenshot or `node-details` query.
- Properties are set via JavaFX reflection — property name must match the bean property
  (e.g. `text`, `style`, `visible`, `disable`, `opacity`, `prefWidth`, `prefHeight`).
- For complex style changes, use `style` property with a full inline CSS string.
- `click-node` uses simulated events — the app must be running and responsive.
