# Troubleshooting live JavaFX verification

Use this decision guide when the normal `discover` → `stages` → `find-nodes` workflow stalls.

## `discover` returns an empty array

1. Read the application launch log before changing the command.
2. Inspect the process tree and identify the configured JavaFX main class.
3. Distinguish the actual application JVM from Gradle wrappers, Gradle daemons, test workers, and
   unrelated Java servers.
4. Check whether a root Gradle `run` task is waiting on an earlier long-lived subproject. Prefer the
   qualified application task when it exists.
5. If a generated application launcher is available, use it to preserve the build-derived classpath
   or module path. Otherwise derive those values from the build configuration.
6. Confirm the application reached JavaFX initialization, then retry `discover`.

Do not attach to an unrelated PID just because it is the only Java process visible.

Discovery attaches briefly to candidate JVMs to read their properties and skips inaccessible VMs.
If the verified target exists but remains absent, check same-user access, sandbox restrictions, and
Attach API policy. Request approval for the specific blocked operation when required.

## Connection or agent injection fails

- Confirm `fxgraph-agent.jar` is beside `fxgraph-cli.jar`.
- Confirm the target runtime contains `java.instrument`. A custom `jlink` or `jpackage` runtime can
  omit it. Rebuild that runtime with the module included; the application normally does not need
  `requires java.instrument` in `module-info.java`.
- Treat future-JDK dynamic-agent warnings as warnings when the operation succeeds.
- Do not use `sudo` or switch to another PID to work around an Attach API denial.

## Commands report missing nodes after an app restart

PID, Stage IDs, and node IDs are session-local. Discard all cached identifiers and rerun:

```bash
$CLI discover
$CLI $PID stages
$CLI $PID find-nodes --id expectedId --stageId "$STAGE_ID"
```

## An open menu or tooltip is absent from inspection

`stages`, `scenegraph`, and `find-nodes` traverse only `Stage` scenes. JavaFX popups such as
`ContextMenu`, `MenuButton` popup content, and tooltips are `PopupWindow` instances and are outside
that traversal.

- Use fxgraph to inspect the owning Stage and opener control.
- Query the opener's state, such as `showing`, when the property exists.
- Use TestFX or another in-process UI test to locate and manipulate popup children reliably.
- Use native UI automation only when a live external process must be exercised, and verify each
  action rather than assuming accessibility exposure.

## A screenshot omits the open popup

The fxgraph screenshot command snapshots a JavaFX `Scene` or `Node`; it is not a desktop capture.
Use it for deterministic Stage content. Use the operating system's compositor screenshot facility
when the evidence must include popup windows or window chrome. Native capture can require GUI,
Accessibility, Screen Recording, or sandbox approval depending on the platform.

For popup verification, retain both forms of evidence when useful:

- a property or in-process test assertion proving behavior;
- a native composite screenshot proving final appearance.

## A command produces excessive or malformed output

- Remove `--json`; JSON is always enabled.
- For `scenegraph`, add `--props` only when properties are needed and pair it with `--filter`.
- For `node-details`, remove `--props` and add `--filter name1,name2`.
- Traverse `.properties[]` in `jq`; it is an array, not an object keyed by property name.
