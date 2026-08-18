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

Click the center of a node with synthetic `MOUSE_PRESSED`, `MOUSE_RELEASED`, and `MOUSE_CLICKED`
events by default. This mode does not move the system pointer or request window focus. Use
`--mode robot` when normal platform hit testing and native pointer input are required. If explicitly
requested Robot input is unavailable, fxgraph automatically falls back to the synthetic gesture.

```bash
$CLI $PID click-node $NODE_ID
$CLI $PID click-node $NODE_ID --mode robot
```

**Output:**
```json
{ "success": true, "clicked": true, "mode": "synthetic" }
```

- The application must be running and the node and its ancestors must be visible.
- Disabled and zero-size nodes are rejected.
- `--mode synthetic` is the default and sends the complete gesture without pointer or focus changes.
- `--mode robot` requests window focus and moves the system pointer to the node center.
- Robot fallback returns `mode: "synthetic"` plus `fallbackReason`.

---

## activate-node

Activate a `ButtonBase` through its `fire()` method without emitting mouse events.

```bash
$CLI $PID activate-node $BUTTON_NODE_ID
```

**Output:**
```json
{ "success": true, "activated": true }
```

Use this for deterministic action invocation when pointer hit testing and mouse handlers are not
part of the assertion. Non-`ButtonBase` nodes are rejected.

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

Send a synthetic JavaFX key event to the focused node (or a specific node).

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

This is not native keyboard input. Treat it as best-effort and verify the application effect. For
text fields, prefer `set-property ... text`; for deterministic button activation, prefer
`activate-node`. Use `click-node` when pointer behavior is material. Use TestFX or native automation
when exact key-code or input-method behavior matters.

---

## screenshot

Capture a PNG of a node or one specific window scene. A window can be a Stage or a currently showing
popup.

```bash
# Full scene of the primary window (default max: 1280x720)
$CLI $PID screenshot ./screenshot.png

# Specific node
$CLI $PID screenshot ./node.png --nodeId $NODE_ID

# Specific Stage
$CLI $PID screenshot ./stage.png --stageId $STAGE_ID

# Specific popup scene, using its ID from `stages`
$CLI $PID screenshot ./popup.png --stageId $POPUP_ID

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
- Window screenshots use `Scene.snapshot`; node screenshots use `Node.snapshot`.
- A popup ID captures that popup scene by itself. A Stage snapshot does not composite separately
  hosted `PopupWindow` content or OS window decorations. Use a native OS/compositor capture when one
  image must contain the Stage, popup, and decorations.

---

## capture-video

Capture motion in a node or one JavaFX window scene as a silent MP4/H.264 clip.

```bash
# First available Stage, using defaults: 5 seconds, 10 fps, maximum 1280x720
$CLI $PID capture-video /tmp/clip.mp4

# Specific node for 10 seconds
$CLI $PID capture-video /tmp/node.mp4 --nodeId $NODE_ID --durationSeconds 10

# Specific Stage with custom frame rate and dimensions
$CLI $PID capture-video /tmp/stage.mp4 --stageId $STAGE_ID \
  --durationSeconds 15 --framesPerSecond 15 --maxWidth 960 --maxHeight 540

# Specific popup scene
$CLI $PID capture-video /tmp/popup.mp4 --stageId $POPUP_ID --durationSeconds 5
```

| Option | Constraint | Default |
|---|---:|---:|
| `--nodeId ID` | Takes precedence over `--stageId` | — |
| `--stageId ID` | Selects one Stage or popup; captures the first Stage when omitted | — |
| `--durationSeconds N` | `1` through `30` | `5` |
| `--framesPerSecond N` | `1` through `30` | `10` |
| `--maxWidth N` | At least `2` | `1280` |
| `--maxHeight N` | At least `2` | `720` |

**Output:**
```json
{
  "success": true,
  "savedPath": "/tmp/clip.mp4",
  "width": 1280,
  "height": 720,
  "mimeType": "video/mp4",
  "codec": "H.264",
  "durationSeconds": 5,
  "framesPerSecond": 10,
  "frameCount": 50,
  "targetType": "scenegraph",
  "targetId": "123456"
}
```

- Recording is synchronous; the command returns after the finalized MP4 has been written.
- Output is silent. Use a native screen recorder when audio or OS-composited content is required.
- Frames use the same `Node.snapshot` or single-window `Scene.snapshot` boundary as `screenshot`,
  so other windows and decorations are excluded.
- Frame dimensions stay fixed if the window or node changes size during recording. Smaller frames
  are centered on a black background.
- Prefer an absolute output path because the injected agent writes from the target JVM.

---

## Complete interaction workflow

```bash
# 0. Setup
# macOS / Linux
CLI="<path-to-skill>/scripts/fxgraph"
# Windows PowerShell
# $CLI = "<path-to-skill>\scripts\fxgraph.bat"

# 1. Discover candidates, inspect mainClass, then set the verified PID explicitly
$CLI discover | jq '.[] | {pid, mainClass, connected}'
PID=12345

# 2. Find the target node narrowly and require a unique match
NODE_ID=$($CLI $PID find-nodes --type TextField \
  | jq -er 'if length == 1 then .[0].nodeId else error("TextField is not unique") end')

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

# 7. Click a uniquely identified button
BUTTON_ID=$($CLI $PID find-nodes --type Button --text "Submit" \
  | jq -er 'if length == 1 then .[0].nodeId else error("Submit button is not unique") end')
$CLI $PID click-node $BUTTON_ID

# Or activate ButtonBase semantics without moving the pointer
$CLI $PID activate-node $BUTTON_ID

# 8. After screenshot for verification
$CLI $PID screenshot ./after.png

# 9. Clear highlight
$CLI $PID select-node 0
```

---

## Tips

- Always verify changes with a screenshot or `node-details` query.
- `select-node` before and after changes provides a quick visual confirmation.
- `click-node` uses synthetic input by default without moving the system pointer or changing focus.
- Use `click-node --mode robot` only when native pointer behavior is part of the verification.
- Use `activate-node` when only a `ButtonBase` action needs verification.
- For text input fields, prefer `set-property text "..."` for reliability over `type-key` character-by-character.
- Prefer locating and clicking the submit control over relying on synthetic Enter behavior.
