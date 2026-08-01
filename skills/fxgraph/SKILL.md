---
name: fxgraph
description: >
  Inspect, debug, test, and interact with a live JavaFX application through the bundled fxgraph
  CLI. Use for discovering the correct JavaFX JVM, listing Stages, searching and inspecting scene
  graph nodes, changing properties, sending synthetic interactions, highlighting nodes, taking
  node or Stage screenshots, and capturing short video clips. Also use when live JavaFX verification
  is blocked by application startup, Attach API permissions, stale node IDs, or JavaFX popup and
  screenshot limitations.
---

# fxgraph

Use the bundled `fxgraph` CLI to inspect a running JavaFX application. Treat live verification as
three separate problems: start the actual JavaFX JVM, select the correct target, then inspect or
interact within fxgraph's supported window scope.

## Resolve the CLI

Set the skill directory to the absolute directory that contains this `SKILL.md`. Do not assume the
skill is installed under a particular home-directory layout.

```bash
SKILL_DIR="/absolute/path/to/skills/fxgraph"
CLI="$SKILL_DIR/scripts/fxgraph"
test -x "$CLI"
test -f "$SKILL_DIR/scripts/fxgraph-cli.jar"
test -f "$SKILL_DIR/scripts/fxgraph-agent.jar"
```

On Windows PowerShell, use the sibling `scripts/fxgraph.bat` and invoke it with `& $CLI`.

If either JAR is absent in this repository, run `./gradlew :fxgraph-cli:installSkillJars` from the
fxgraph repository root. Do not rebuild an installed skill unless its repository is available.

## Check capability boundaries first

| Target or action | fxgraph support | Use instead when unsupported |
|---|---|---|
| Nodes in a JavaFX `Stage` scene | Inspect and interact | — |
| Additional windows implemented as `Stage` | Inspect with `stages` and `--stageId` | — |
| `PopupWindow`, `ContextMenu`, `MenuButton` popup contents, tooltips | Not enumerated; traversal is Stage-rooted | TestFX or another in-process UI test for child lookup and interaction |
| Node or Stage scene image | `screenshot` | — |
| Node or Stage scene motion (up to 30 seconds) | `capture-video` | — |
| One image containing separately composited popups or OS decorations | Not captured by scene snapshots | Native OS/compositor screenshot |
| Native mouse and keyboard behavior | Not provided; events are synthetic | TestFX, JavaFX Robot, or approved native UI automation |

For a popup workflow, keep using fxgraph for the owning Stage and the control that opens the popup.
Inspect properties such as `showing` when available. Do not repeatedly search `stages`,
`scenegraph`, or `find-nodes` for popup children after confirming the UI is a `PopupWindow` or
`ContextMenu`. Verify repeatable popup behavior with an in-process UI test, and verify the final
composited appearance with an OS screenshot. Request Accessibility, Screen Recording, GUI, or
sandbox approval only if the chosen native operation is blocked.

## Start the actual JavaFX JVM

Prefer the target project's documented, application-specific launch task or generated launcher.
Inspect `settings.gradle*`, the application module's `build.gradle*`, launch scripts, and existing
logs before constructing a command.

Do not treat a running Gradle wrapper or daemon as proof that the JavaFX application started. A
root `./gradlew run` can remain occupied by another long-lived subproject before the JavaFX task
runs. If `discover` is empty while Gradle is still running:

1. Inspect the captured launch log and the process tree.
2. Confirm that a JVM with the configured JavaFX main class actually exists and initialized JavaFX.
3. Use the qualified application `run` task or a build-generated launcher such as `installDist`.
4. If direct `java` execution is necessary, derive the main class, classpath, module path, and
   JavaFX modules from the build rather than inventing them.
5. Retry `discover` only after the target JVM is alive.

Do not attach to a Gradle daemon, wrapper, build tool, or unrelated server process. If discovery or
attachment is blocked by the execution sandbox or OS permissions, retry the verified launch or
attach command with the required approval; never substitute another PID. The target runtime must
contain the `java.instrument` module. A future-JDK dynamic-agent warning is informational if the
command otherwise succeeds.

Read [troubleshooting.md](references/troubleshooting.md) when startup, discovery, attachment, or
popup capture does not behave as expected.

## Select the target deliberately

First inspect all candidates:

```bash
"$CLI" discover | jq '.[] | {pid, mainClass, vmName, connected}'
```

Choose `PID` by the expected main class and, when needed, confirm its Stage titles. Never select
`.[0].pid` unless the result was first proven to contain exactly one intended application.

```bash
PID=12345
"$CLI" "$PID" stages | jq '.[] | {stageId, title, focused, rootNodeId}'
STAGE_ID="123456789"
```

Choose a Stage by title and focus state. A focused Stage is a useful signal, not sufficient proof
when several applications or windows exist.

## Use the narrow inspection workflow

1. Search directly with `find-nodes`.
2. Use a shallow `scenegraph` only for structural orientation or as a fallback.
3. Read only the properties needed for the assertion.
4. Interact, then verify the observable result rather than trusting a success response alone.

```bash
# Narrow lookup
"$CLI" "$PID" find-nodes --type Button --text "Submit" --stageId "$STAGE_ID"

# Filtered inspection; node-details does not accept --props
NODE_ID=987654321
"$CLI" "$PID" node-details "$NODE_ID" --filter text,visible,disable

# Interaction and verification
"$CLI" "$PID" click-node "$NODE_ID"
"$CLI" "$PID" node-details "$NODE_ID" --filter text,visible,disable
"$CLI" "$PID" screenshot ./after.png --stageId "$STAGE_ID"
```

If direct lookup is insufficient:

```bash
"$CLI" "$PID" scenegraph --stageId "$STAGE_ID" --depth 3 --bounds
"$CLI" "$PID" scenegraph --stageId "$STAGE_ID" --props --filter text,visible,disable
```

Use `select-node` before a risky change when visual confirmation helps. Prefer `set-property` for
deterministic text entry; `click-node` and `type-key` send synthetic JavaFX events, not native input.

## Apply command invariants

- All commands already output JSON. Never add `--json`.
- `--props` belongs only to `scenegraph`; combine it with `--filter` to limit properties.
- Always pass `--filter` to `node-details` unless a complete 60+ property dump is explicitly needed.
- `properties` is an array of `{name, value, type, writable, category}`, not a flat object.
- Treat `nodeId` as valid only for the current JVM session. After restart, rerun `discover`,
  `stages`, and `find-nodes`; do not reuse PID, Stage ID, or node ID values.
- Inspect JSON error output and exit status before continuing.
- Verify changes through application state, a focused property query, a suitable screenshot, or a
  combination of them.
- Use `capture-video` when motion over time is material; use `screenshot` for a single visual state.

## Load command references only as needed

- Read [inspect-commands.md](references/inspect-commands.md) for exhaustive options and schemas for
  `discover`, `stages`, `find-nodes`, `scenegraph`, and `node-details`.
- Read [interact-commands.md](references/interact-commands.md) for `set-property`, `select-node`,
  `click-node`, `focus`, `type-key`, `screenshot`, and `capture-video`.
- Read [troubleshooting.md](references/troubleshooting.md) for decision trees covering empty
  discovery, attach failures, stale IDs, popup controls, and composite screenshots.
