# Interact Commands Reference

Detailed options and output schemas for commands that modify or interact with the running UI.

> **Prerequisite**: Set up `$CLI` as described in SKILL.md before running any command.
> On Windows PowerShell, prefix every `$CLI` call with `&` (e.g. `& $CLI discover`).

## set-property

Set a JavaFX node property via reflection.

```bash
$CLI $PID set-property $NODE_ID <propertyName> <value> [--type TYPE]
```

| `--type` value | When to use |
|----------------|-------------|
| `string` (default) | Text, style strings |
| `number` | Numeric values (`opacity`, `prefWidth`, etc.) |
| `boolean` | `true` / `false` values |
| `color` | CSS color string (`#RRGGBB`, named colors) |

**Examples:**
```bash
$CLI $PID set-property $NODE_ID text "Hello World"
$CLI $PID set-property $NODE_ID visible false   --type boolean
$CLI $PID set-property $NODE_ID opacity 0.5     --type number
$CLI $PID set-property $NODE_ID prefWidth 200   --type number
$CLI $PID set-property $NODE_ID style "-fx-background-color: red;"
$CLI $PID set-property $NODE_ID textFill "#FF0000" --type color
```

**Output:**
```json
{ "success": true, "oldValue": "Previous Text", "newValue": "Hello World" }
```

- Property name must match the JavaFX bean property (e.g. `text`, `style`, `visible`, `disable`, `opacity`, `prefWidth`, `prefHeight`).
- For multi-rule style changes, pass a full inline CSS string to the `style` property.

---

## select-node

Draw a visual overlay (red border) on a node in the live application.

```bash
$CLI $PID select-node $NODE_ID           # highlight with bounds
$CLI $PID select-node $NODE_ID --no-bounds  # highlight without rectangle
$CLI $PID select-node 0                  # clear highlight
```

**Output:**
```json
{ "success": true, "highlighted": true }
```

Use this to visually confirm you have the right node before modifying it.

---

## click-node

Activate a `ButtonBase` through its `fire()` method, or fire a synthetic primary
`MouseEvent.MOUSE_CLICKED` on the center of another node.

```bash
$CLI $PID click-node $NODE_ID
```

**Output:**
```json
{ "success": true, "clicked": true }
```

- The application must be running and the node and its ancestors must be visible.
- Disabled and zero-size nodes are rejected.
- Fires a simulated JavaFX event, not a native OS click.

---

## focus

Request keyboard focus for a node.

```bash
$CLI $PID focus $NODE_ID
```

**Output:**
```json
{ "success": true, "focused": true }
```

Call this before `type-key` when targeting a specific input field.

---

## type-key

Send a key event to the focused node (or a specific node).

```bash
$CLI $PID type-key ENTER
$CLI $PID type-key a
$CLI $PID type-key TAB --nodeId $NODE_ID
```

| Argument | Format |
|----------|--------|
| Key code names | `ENTER`, `SPACE`, `TAB`, `BACK_SPACE`, `DELETE`, `ESCAPE`, `UP`, `DOWN`, `LEFT`, `RIGHT`, `F1`…`F12` |
| Single character | `a`, `1`, `@`, etc. |

`--nodeId` — optional; send to a specific node instead of the focused node.

**Output:**
```json
{ "success": true, "typed": true }
```

---

## screenshot

Capture a PNG of a node, a specific stage, or the full scene.

```bash
# Full scene of the primary window (default max: 1280x720)
$CLI $PID screenshot ./screenshot.png

# Specific node
$CLI $PID screenshot ./node.png --nodeId $NODE_ID

# Specific stage
$CLI $PID screenshot ./stage.png --stageId $STAGE_ID

# Custom maximum dimensions (scales proportionally if exceeded)
$CLI $PID screenshot ./hd.png --maxWidth 1920 --maxHeight 1080
$CLI $PID screenshot ./small.png --maxWidth 640 --maxHeight 480
```

**Output:**
```json
{
  "success": true,
  "savedPath": "./screenshot.png",
  "width": 1280,
  "height": 720,
  "mimeType": "image/png",
  "targetType": "scenegraph",
  "targetId": "123456"
}
```

- Output format is always PNG.
- The path is relative to the working directory of the CLI process.
- **Default max size is 1280x720 (HD).** Images exceeding these limits are scaled down proportionally.
- `--maxWidth` and `--maxHeight` are optional. Set both to override the default HD limit.
- Aspect ratio is always preserved during scaling.

---

## Complete interaction workflow

```bash
# 0. Setup
# macOS / Linux
CLI="<path-to-skill>/scripts/fxgraph"
# Windows PowerShell
# $CLI = "<path-to-skill>\scripts\fxgraph.bat"

# 1. Discover app and get PID
# bash
PID=$($CLI discover | jq '.[0].pid')
# PowerShell: $PID = (& $CLI discover | jq '.[0].pid')

# 2. Find target node (e.g. first TextField)
# bash
NODE_ID=$($CLI $PID scenegraph --props --filter text \
  | jq '.. | select(.type? == "TextField") | .nodeId' | head -1)
# PowerShell: $NODE_ID = (& $CLI $PID scenegraph --props --filter text `
#   | jq '.. | select(.type? == "TextField") | .nodeId' | Select-Object -First 1)

# 3. Highlight to confirm the right node
$CLI $PID select-node $NODE_ID
# PowerShell: & $CLI $PID select-node $NODE_ID

# 4. Take a before screenshot (auto-scaled to HD by default)
$CLI $PID screenshot ./before.png

# 5. Set property directly
$CLI $PID set-property $NODE_ID text "new value"

# 6. Or: focus and type
$CLI $PID focus $NODE_ID
$CLI $PID type-key ENTER

# 7. Click a button
BUTTON_ID=$($CLI $PID scenegraph --props --filter text \
  | jq '.. | select(.type? == "Button" and .properties.text? == "Submit") | .nodeId')
$CLI $PID click-node $BUTTON_ID

# 8. After screenshot for verification
$CLI $PID screenshot ./after.png

# 9. Clear highlight
$CLI $PID select-node 0
```

---

## Tips

- Always verify changes with a screenshot or `node-details` query.
- `select-node` before and after changes provides a quick visual confirmation.
- `click-node` fires a simulated JavaFX event — the node must be part of a live scene.
- For text input fields, prefer `set-property text "..."` for reliability over `type-key` character-by-character.
- Use `focus` + `type-key ENTER` to submit forms without needing to locate the submit button.
