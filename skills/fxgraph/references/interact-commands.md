# Interact Commands Reference

Detailed options and output schemas for commands that modify or interact with the running UI.

> **Prerequisite**: Set up `$CLI` as described in SKILL.md before running any command.

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

Fire a `MouseEvent.MOUSE_CLICKED` on the center of a node.

```bash
$CLI $PID click-node $NODE_ID
```

**Output:**
```json
{ "success": true, "clicked": true }
```

- The application must be running and the node must be visible and responsive.
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
# Full scene of the primary window
$CLI $PID screenshot ./screenshot.png

# Specific node
$CLI $PID screenshot ./node.png --nodeId $NODE_ID

# Specific stage
$CLI $PID screenshot ./stage.png --stageId $STAGE_ID
```

**Output:**
```json
{
  "success": true,
  "path": "./screenshot.png"
}
```

- Output format is always PNG.
- The path is relative to the working directory of the CLI process.

---

## Complete interaction workflow

```bash
# 0. Setup
CLI="<path-to-skill>/scripts/fxgraph"

# 1. Discover app and get PID
PID=$($CLI discover | jq '.[0].pid')

# 2. Find target node (e.g. first TextField)
NODE_ID=$($CLI $PID scenegraph --props --filter text \
  | jq '.. | select(.type? == "TextField") | .nodeId' | head -1)

# 3. Highlight to confirm the right node
$CLI $PID select-node $NODE_ID

# 4. Take a before screenshot
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
