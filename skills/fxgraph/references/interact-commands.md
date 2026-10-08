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
{ "oldValue": "Previous Text", "newValue": "Hello World" }
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
{ "highlighted": true }
```

Use this to visually confirm you have the right node before modifying it.

---

## click-node

Click the center of a node with synthetic `MOUSE_PRESSED`, `MOUSE_RELEASED`, and `MOUSE_CLICKED`
events inside JavaFX. This does not move the system pointer or request window focus, and requires
no OS input permissions. Native pointer hit testing is not reproduced.

```bash
$CLI $PID click-node $NODE_ID
$CLI $PID click-node $NODE_ID --button secondary
$CLI $PID click-node $NODE_ID --clickCount 2
$CLI $PID click-node $NODE_ID --button middle
$CLI $PID right-click-node $NODE_ID
$CLI $PID double-click-node $NODE_ID
```

- `--button`: `primary` (default), `secondary` (right), or `middle`.
- `--clickCount`: integer `1` (default) or `2` (double click).
- `right-click-node` presets `--button secondary`; `double-click-node` presets `--clickCount 2`.
  Both accept the same options as `click-node`; explicit options override the presets.
- Synthetic double clicks send two complete gestures with counts 1 and 2.
- Synthetic secondary clicks also send `CONTEXT_MENU_REQUESTED`, opening standard context menus.
  Custom context-menu handlers should consume that request when replacing the default menu.
- The application must be running and the node and its ancestors must be visible.
- Disabled and zero-size nodes are rejected, including disabled menu items and their children.
- `--mode` is optional and accepts only `synthetic`. Other values, including the removed `robot`
  mode, return an error before input is dispatched.
- A menu or an application event handler may change focus even with synthetic input.
- Inspect menu item state using `node-details`: `node.disabled: true` means disabled; omitted means
  false. A standard menu item's rendered Node `disable` property may differ from the MenuItem state.
- Standard `Menu` submenu containers receive an enter gesture so they can open without native input.
- If an action opens a blocking modal dialog, the response includes `handlerPending: true`.
  It confirms dispatch while the handler is waiting; inspect/dismiss the dialog and verify the
  application's resulting state. Do not repeat the action merely because it has not returned yet.
- Synthetic gestures use `MouseEvent.isSynthesized() == false`; that JavaFX flag identifies
  touch-derived input, rather than programmatically created mouse events.

Older Robot builds moved the system pointer and could change window focus. That mode is now removed.
Legacy macOS IMK/IMKCFRunLoopWakeUpReliable log messages alone do not establish input success or failure;
verify the application's resulting state when diagnosing an older installation.

**Output:**
```json
{ "clicked": true, "mode": "synthetic", "button": "primary", "clickCount": 1 }
```

---

## activate-node

Activate a `ButtonBase` through its `fire()` method without emitting mouse events.

```bash
$CLI $PID activate-node $BUTTON_NODE_ID
```

**Output:**
```json
{ "activated": true, "handlerPending": false }
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
{ "focused": true }
```

Call this before `type-key` when targeting a specific input field.

---

## type-key

Send a synthetic JavaFX key gesture to the focused scene (or a specific node).
It dispatches `KEY_PRESSED` and `KEY_RELEASED`, plus `KEY_TYPED` for printable input without
Control, Alt, or Meta. Named keys such as `ENTER` and `TAB` are sent as key codes.

```bash
$CLI $PID type-key ENTER
$CLI $PID type-key a
$CLI $PID type-key TAB --nodeId $NODE_ID
$CLI $PID type-key TAB --modifiers SHIFT
$CLI $PID type-key Q --modifiers META,SHIFT
$CLI $PID type-key W --modifiers CMD
```

| Argument | Format |
|----------|--------|
| Key code names | `ENTER`, `SPACE`, `TAB`, `BACK_SPACE`, `DELETE`, `ESCAPE`, `UP`, `DOWN`, `LEFT`, `RIGHT`, `F1`…`F12` |
| Single character | `a`, `1`, `@`, etc. |

`--nodeId` — optional; send to a specific node instead of the focused node.
`--modifiers` — comma-separated `SHIFT`, `CTRL`/`CONTROL`, `ALT`, `CMD`/`META`, case-insensitive.
Multiple names and aliases can be combined; duplicates are normalized to one event flag.
`CMD`/`META` sets the JavaFX event's Meta flag.
`--mode` — optional, accepts only `synthetic`. The removed `robot` mode returns an error before
input or focus changes.

**Output:**
```json
{ "typed": true, "mode": "synthetic" }
```

Input is dispatched inside JavaFX after requesting node focus. No OS input permissions or foreground
window are required. Exact single characters, including Unicode, are supported.

Successful output confirms input submission. Verify focus movement, shortcut actions, and window
lifecycle events in the application. Shift+Tab and JavaFX event handlers or scene accelerators can
handle gestures. Native OS shortcuts are not sent; Cmd+W/Cmd+Q require application-side handling.
Cmd+Q may terminate the JVM before the agent replies, producing a connection error; that error alone
does not establish whether the shortcut succeeded. For text fields, prefer `set-property ... text`
for deterministic replacement; for button activation, prefer `activate-node`.
To test a window's normal close-request/hiding handlers directly, use `close-window` instead of
relying on a native Cmd+W/Cmd+Q shortcut.

---

## set-window-property / close-window

```bash
$CLI $PID set-window-property "$STAGE_ID" x 100 --type number
$CLI $PID set-window-property "$STAGE_ID" width 1200 --type number
$CLI $PID set-window-property "$STAGE_ID" maximized true --type boolean
$CLI $PID close-window "$STAGE_ID"
# close-window also accepts --stageId "$STAGE_ID"
```

Writable Stage properties: `x`, `y`, `width`, `height`, `opacity`, `title`, `maximized`,
`iconified`, `alwaysOnTop`, and `resizable`. `--type` may be `number`, `boolean`, or `string`;
it is inferred if omitted. Numeric values must be finite, dimensions positive, and opacity within
0 through 1. Boolean values must be `true` or `false`. Read `window-details` again to confirm the
window manager applied a change.

`close-window` sends `WINDOW_CLOSE_REQUEST`, allowing application handlers to save state, prompt,
or cancel closing. The result has `closeRequested`, `closed`, and `handlerPending`. A consumed
request succeeds with `closed: false`; a modal confirmation reports `handlerPending: true`.
Closing the last window or exiting from a handler may terminate the JVM before it replies.
Verify lifecycle effects in the application's state/logs; a lost connection alone does not prove
either success or failure.

---

## close-popup / embedded overlays

```bash
# Obtain the popup ID after opening it; use its current stageId from stages
$CLI $PID close-popup --stageId "$POPUP_ID"
```

This hides only the specified `PopupWindow` (for example, `ContextMenu` or `Tooltip`) and rejects
normal Stages. It does not generically dismiss embedded application overlays.

For AtlantaFX `ModalPane`, prefer its close control or the application's configured background
click / Escape handler. If those are unavailable, inspect the actual node's `display` property
before using the fallback:

```bash
MODAL_ID=$($CLI $PID find-nodes --type ModalPane --visible-only \
  | jq -er 'if length == 1 then .[0].nodeId else error("ModalPane is not unique") end')
$CLI $PID node-details "$MODAL_ID" --filter display
$CLI $PID set-property "$MODAL_ID" display false --type boolean
$CLI $PID node-details "$MODAL_ID" --filter display
```

Direct property changes can bypass application close callbacks. Verify the overlay and underlying
view state afterwards. Other overlay libraries may expose different properties; inspect them
before modifying anything.

---

## scroll-node / scroll-to-index

```bash
$CLI $PID scroll-node "$LIST_NODE_ID" --dy 500
$CLI $PID scroll-to-index "$LIST_NODE_ID" --index 10
$CLI $PID scroll-node "$LIST_NODE_ID" --align bottom
$CLI $PID find-nodes --stageId "$STAGE_ID" --visible-only --text "Target item"
```

Supported containers: standard `ListView`, `TableView`, `ScrollPane`, JavaFX `VirtualFlow`,
Flowless `VirtualFlow`, and Flowless `VirtualizedScrollPane` wrapping one. Flowless must be present
in the target; the agent does not install it. Positive `--dx` / `--dy` move right/down; negative
values move left/up. `--align` accepts `top`, `bottom`, `left`, or `right` and cannot be combined
with pixel deltas. For standard virtual flows, the edge must match their scrolling orientation.

`--index` is zero-based and must refer to an existing item. `ScrollPane` has no index navigation.
Scrolling lays out newly visible cells; rerun `find-nodes` afterwards, because recycled or newly
created cells can change their node IDs. `refreshRequired: true` also marks this in the response.

---

## screenshot

Capture a PNG of a node or one specific window scene. A window can be a Stage or a currently showing
popup.

```bash
# Full scene of the primary window (source resolution by default)
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
$CLI $PID screenshot ./original.png --no-limit
# 0 disables one axis's resize limit
$CLI $PID screenshot ./wide.png --maxWidth 0 --maxHeight 1080
```

**Output:**
```json
{
  "savedPath": "/tmp/fxgraph/screenshot.png",
  "width": 1280,
  "height": 720,
  "sourceWidth": 1920,
  "sourceHeight": 1080,
  "scaled": true,
  "mimeType": "image/png",
  "targetType": "scenegraph",
  "targetId": "123456"
}
```

- Output format is always PNG.
- Prefer an absolute path; relative paths resolve from the target JVM's working directory.
- The default preserves the JavaFX snapshot's source resolution. Explicit positive `--maxWidth`
  / `--maxHeight` resize proportionally when exceeded; omitted or `0` means no limit for that axis.
- `--no-limit` explicitly disables both resize limits and cannot be combined with dimension flags.
- The source safety limit is **8192px per dimension and 16777216 pixels total**, including when
  `--no-limit` is used. Oversized sources are rejected before snapshot allocation; capture a smaller
  node or window. Dimension flags resize the output and do not bypass source limits.
- `sourceWidth`, `sourceHeight`, and `scaled` show whether the saved image was resized.
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

# 4. Take a before screenshot at source resolution
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
- `click-node` uses synthetic input without moving the system pointer or requesting window focus.
- Input commands require no OS input permissions and do not send native OS shortcuts.
- Use `activate-node` when only a `ButtonBase` action needs verification.
- For text input fields, prefer `set-property text "..."` for reliability over `type-key` character-by-character.
- Prefer locating and clicking the submit control over relying on synthetic Enter behavior.
