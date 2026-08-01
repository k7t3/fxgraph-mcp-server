# Inspect Commands Reference

Detailed options and output schemas for read-only inspection commands.

> **Prerequisite**: Set up `$CLI` as described in SKILL.md before running any command.
> On Windows PowerShell, prefix every `$CLI` call with `&` (e.g. `& $CLI discover`).

## discover

Detect running JavaFX JVM processes.

```bash
$CLI discover
```

**Output** — JSON array:
```json
[
  {
    "pid": 12345,
    "mainClass": "com.example.MyApp",
    "vmName": "OpenJDK 64-Bit Server VM",
    "javaFX": true,
    "connected": false
  }
]
```

- `javaFX: true` — process has JavaFX on the classpath.
- `connected: true` — agent is already attached and listening.

Inspect every candidate's `mainClass` before choosing a PID. Do not use `.[0].pid` when more than
one JVM might be present. If discovery is empty even though a Gradle process is running, follow
[troubleshooting.md](troubleshooting.md); the Gradle wrapper or daemon may be alive before the
actual JavaFX application JVM starts.

---

## stages

List windows (JavaFX Stages) in a running process.

```bash
$CLI $PID stages
```

**Output** — JSON array:
```json
[
  {
    "stageId": "123456789",
    "title": "Main Window",
    "width": 800,
    "height": 600,
    "x": 100,
    "y": 100,
    "focused": true,
    "rootNodeId": 987654321
  }
]
```

- `rootNodeId` — use this as the entry point for `scenegraph`.
- `focused: true` — the currently active window.

Only JavaFX `Stage` instances are returned. `PopupWindow` subclasses such as `ContextMenu`, a
`MenuButton` popup, and tooltips are intentionally absent.

```bash
# bash / zsh
ROOT=$($CLI $PID stages | jq '.[] | select(.focused) | .rootNodeId')
STAGE_ID=$($CLI $PID stages | jq -r '.[] | select(.focused) | .stageId')

# PowerShell
$ROOT = (& $CLI $PID stages | jq '.[] | select(.focused) | .rootNodeId')
$STAGE_ID = (& $CLI $PID stages | jq -r '.[] | select(.focused) | .stageId')
```

---

## find-nodes

Search Stage scene graphs without serializing the full tree.

```bash
$CLI $PID find-nodes [OPTIONS]
```

| Option | Description |
|---|---|
| `--type TYPE` | Match the JavaFX class simple name, such as `Button` |
| `--id ID` | Match the node CSS ID |
| `--text TEXT` | Match text contained by `Labeled` or `TextInputControl` nodes |
| `--styleClass CLASS` | Match one style-class entry |
| `--stageId ID` | Limit the search to one Stage |

Combine filters to reduce ambiguity:

```bash
$CLI $PID find-nodes --type Button --text "Submit" --stageId "$STAGE_ID"
$CLI $PID find-nodes --id submitBtn --stageId "$STAGE_ID"
```

Output is a JSON array of compact matches containing `nodeId`, `type`, and, when present, `id`,
`text`, and `visible`.

Like `scenegraph`, this command searches only `Stage` roots. It cannot find children hosted by a
`ContextMenu` or another `PopupWindow`. Stop retrying Stage searches once that boundary is known.

---

## scenegraph

Retrieve the scene graph tree. Returns a compact tree by default.

Traversal is limited to JavaFX `Stage` scenes. Popup scenes are not included.

```
$CLI $PID scenegraph [OPTIONS]
```

| Option | Description |
|--------|-------------|
| `--stageId ID` | Inspect a specific stage (default: all stages) |
| `--depth N` | Maximum tree depth to traverse |
| `--bounds` | Include bounding box `{ x, y, w, h }` for each node |
| `--props` | Include property details for each node |
| `--filter p1,p2` | Limit `--props` to specific property names (e.g. `text,visible,disable`) |

> **Note:** `--props` is required for `--filter` to have any effect.
> There is no `--json` flag — output is always JSON.

**Default (compact) node schema:**
```json
{
  "nodeId": 987654321,
  "type": "Button"
}
```

Field presence rules:
- `id` — only when a CSS ID is set
- `visible` — only when `false` (default is `true`)
- `styleClass` — only when non-empty
- `bounds` — only with `--bounds`
- `children` — only when the node has children
- `properties` — only with `--props`

**With `--bounds`:**
```json
{ "nodeId": 123, "type": "VBox", "bounds": { "x": 0, "y": 0, "w": 800, "h": 600 } }
```

**With `--props --filter text`:**
> `properties` is always an **array** of property objects, not a flat object.

```json
{
  "nodeId": 456,
  "type": "Button",
  "properties": [
    { "name": "text", "value": "Submit", "type": "string", "writable": true, "category": "content" }
  ]
}
```

**Root output structure:**
```json
{
  "stages": [{ "stageId": "123", "title": "Main Window", "rootNodeId": 987654321 }],
  "rootNodes": [ { "nodeId": 987654321, "type": "VBox", "children": [...] } ]
}
```

---

## node-details

Get properties of a single node.

> **Warning:** `--props` is NOT a valid option for `node-details` — it will cause an error.
> Use `--filter` to select specific properties.
>
> **Output size warning:** Without `--filter`, this command dumps ALL properties (60+) of the
> node and its children, often producing hundreds of lines. Always use `--filter` unless you
> genuinely need every property.

```bash
# Recommended: always filter to the properties you need
$CLI $PID node-details $NODE_ID --filter text,visible,disable
$CLI $PID node-details $NODE_ID --filter items          # list/table item count
$CLI $PID node-details $NODE_ID --filter text,style     # label/button content
$CLI $PID node-details $NODE_ID --filter focused,disabled,managed

# Full dump (only when you need everything — output is very large)
$CLI $PID node-details $NODE_ID
```

**Output:**
```json
{
  "node": {
    "nodeId": 123456,
    "id": "myButton",
    "type": "Button",
    "visible": true,
    "styleClass": ["button", "primary"],
    "bounds": { "x": 10, "y": 10, "w": 100, "h": 30 },
    "properties": [
      { "name": "text",    "value": "Click Me", "type": "string",  "writable": true, "category": "content" },
      { "name": "visible", "value": true,       "type": "boolean", "writable": true, "category": "visual"  }
    ],
    "children": [{ "nodeId": 789, "type": "LabeledText" }]
  },
  "properties": [
    { "name": "text",    "value": "Click Me", "type": "string",  "writable": true, "category": "content" },
    { "name": "visible", "value": true,       "type": "boolean", "writable": true, "category": "visual"  }
  ]
}
```

**Property categories:** `layout`, `style`, `visual`, `content`, `interaction`, `properties`.

---

## jq Pipeline Patterns

> **Important:** `properties` is always an **array** of `{name, value, type, writable, category}`
> objects. Use array traversal to extract values — `.properties.text` will NOT work.

```bash
# All Buttons with text
$CLI $PID scenegraph --props --filter text | \
  jq '[.. | select(.type? == "Button") | {nodeId, text: (.properties[]? | select(.name == "text") | .value)}]'

# Find node by CSS ID
$CLI $PID scenegraph | jq '.. | select(.id? == "submitBtn")'

# All hidden nodes
$CLI $PID scenegraph | jq '[.. | select(.visible? == false)]'

# All nodeIds (any node)
$CLI $PID scenegraph | jq '[.. | .nodeId? // empty]'

# Child types of a specific node; keep the property payload filtered
$CLI $PID node-details $NODE_ID --filter visible | jq '.node.children[].type'

# Get a specific property value from node-details output
$CLI $PID node-details $NODE_ID --filter text | \
  jq '.properties[] | select(.name == "text") | .value'
```

---

## Tips

- Start with `--depth 2` for a structural overview, then drill into interesting sub-trees.
- Use `--filter` to keep output small when properties are not all needed.
- `nodeId` is `System.identityHashCode()` — stable per JVM session, resets on restart.
- After a restart, rediscover the PID, Stage ID, and node IDs together.
- Pipe to `jq .` for pretty-printing large outputs.
- To verify a ListView/TableView has items, use: `node-details $NODE_ID --filter items`
