---
name: fxgraph
description: >
  Inspect and interact with running JavaFX application scene graphs using the fxgraph CLI tool.
  Use this skill whenever you need to work with a live JavaFX app — discovering running processes,
  reading window/stage lists, traversing the scene graph tree, querying node properties, modifying
  UI properties at runtime, simulating mouse clicks or keyboard input, highlighting nodes visually,
  or capturing screenshots. This skill covers the full workflow from discovery to interaction.
  Trigger this skill any time the user wants to explore, debug, test, or automate a JavaFX UI,
  even if they just say something like "check what buttons are visible" or "click the submit button".
tools:
  - bash
---

# fxgraph

Inspect and interact with running JavaFX applications using the bundled `fxgraph-cli.jar`.

## Setup

The CLI jar lives in this skill's `scripts/` directory. Wrapper scripts are provided for each platform.
Use the **absolute path** to the script that matches the current OS.

**macOS / Linux (bash):**
```bash
CLI="/Users/you/.claude/skills/fxgraph/scripts/fxgraph"
```

**Windows (PowerShell):**
```powershell
$CLI = "C:\Users\you\.claude\skills\fxgraph\scripts\fxgraph.bat"
```

**Windows (cmd):**
```bat
SET CLI=C:\Users\you\.claude\skills\fxgraph\scripts\fxgraph.bat
```

If `fxgraph-cli.jar` is missing, build it first — see `skills/README.md`.

`fxgraph-agent.jar` must remain in the same `scripts/` directory; it is injected automatically.

> **Windows PowerShell note:** Prefix every `$CLI` invocation with the call operator `&`.
> The examples below use `$CLI ...` (bash style) — on PowerShell replace with `& $CLI ...`.

## Critical rules — read before using any command

> **All commands always output JSON.** There is no `--json` flag.
> Passing `--json` will cause an error: `Unknown option: --json`.

> **`--props` is a `scenegraph`-only flag.** Do NOT pass `--props` to `node-details` — it
> will cause an error. For `node-details`, use `--filter` instead.

> **`node-details` without `--filter` dumps every property (60+) of the node AND its children.**
> This produces hundreds of lines of noise. Always use `--filter prop1,prop2` to specify
> exactly which properties you need.

## Typical workflow

### Step 1 — Discover the target application

```bash
$CLI discover
```

Output: JSON array of `{ pid, mainClass, vmName, javaFX, connected }`.

```bash
# bash / zsh
PID=$($CLI discover | jq '.[0].pid')

# PowerShell
$PID = (& $CLI discover | jq '.[0].pid')
```

### Step 2 — Get windows (Stages)

```bash
$CLI $PID stages
```

Output: `{ stageId, title, width, height, x, y, focused, rootNodeId }`.

```bash
# bash / zsh
ROOT=$($CLI $PID stages | jq '.[] | select(.focused) | .rootNodeId')

# PowerShell
$ROOT = (& $CLI $PID stages | jq '.[] | select(.focused) | .rootNodeId')
```

### Step 3 — Traverse the scene graph

```bash
# Compact tree (quick structural overview)
$CLI $PID scenegraph --depth 2

# With bounds and selected properties
$CLI $PID scenegraph --depth 3 --bounds --props --filter text,visible
```

Each node: `{ nodeId, type, id?, visible?, styleClass?, bounds?, properties? }`.

### Step 4 — Find a specific node

```bash
# All Buttons with their text
# Note: properties is an array [{name, value, ...}], not a flat object
$CLI $PID scenegraph --props --filter text | \
  jq '[.. | select(.type? == "Button") | {nodeId, text: (.properties[]? | select(.name == "text") | .value)}]'

# Node by CSS ID
$CLI $PID scenegraph | jq '.. | select(.id? == "submitBtn")'
```

### Step 5 — Inspect or modify a node

```bash
# Inspect specific properties — ALWAYS use --filter to avoid huge output
$CLI $PID node-details $NODE_ID --filter text,visible,disable
$CLI $PID node-details $NODE_ID --filter items        # for ListView/TableView counts
$CLI $PID node-details $NODE_ID --filter text,style   # for label/button content

# Modify a property
$CLI $PID set-property $NODE_ID text "Hello"
$CLI $PID set-property $NODE_ID visible false --type boolean
$CLI $PID set-property $NODE_ID style "-fx-background-color: red;"
```

### Step 6 — Interact with a node

```bash
# Highlight (visual overlay)
$CLI $PID select-node $NODE_ID

# Click
$CLI $PID click-node $NODE_ID

# Focus and type
$CLI $PID focus $NODE_ID
$CLI $PID type-key ENTER

# Screenshot
$CLI $PID screenshot ./result.png
$CLI $PID screenshot ./node.png --nodeId $NODE_ID

# Clear highlight
$CLI $PID select-node 0
```

## Key facts

- `nodeId` is `System.identityHashCode()` — stable for the JVM session but not across restarts.
- `visible: false` nodes are still in the scene graph; they just aren't rendered.
- Properties are set via JavaFX reflection — use the bean property name (e.g. `text`, `style`, `visible`, `disable`, `opacity`, `prefWidth`).
- Always verify property changes with a screenshot or `node-details --filter`.

## Reference files

Read these when you need exhaustive command options or output schemas:

- `<path-to-skill>/references/inspect-commands.md` — full options for `discover`, `stages`, `scenegraph`, `node-details` and jq pipeline patterns
- `<path-to-skill>/references/interact-commands.md` — full options for `set-property`, `select-node`, `click-node`, `focus`, `type-key`, `screenshot`
