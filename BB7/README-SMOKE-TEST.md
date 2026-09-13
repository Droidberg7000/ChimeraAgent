# BB7 Milestone 0 Smoke Test

## Purpose

This branch contains a **compiler-first smoke-test kit** for the private fictional game *Grup0Zzzz: Operativo Chimera*. It targets a maintained BlackBerry Bold 9900/9930 running BlackBerry Device Software 7.x.

This is not a claim that the app has compiled, run in a simulator, or been installed on physical hardware. The BlackBerry OS 7.1 SDK/compiler and device or simulator are the authority for those results.

## Scope

This initial commit proves only the smallest native-app foundation:

```text
BlackBerry Java application launches
→ one dark Chimera screen renders
→ CHIM state serializes to a fixed 30-byte record
→ PersistentObject stores and reloads that record
→ a locally saved test value can be checked after restart
```

Do not add gameplay, networking, combat, real-world data, external APIs, or platform workarounds to this stage.

## Runtime target

```text
DEVICE: BlackBerry Bold 9900 / 9930
OS: BlackBerry Device Software 7.x
RUNTIME: Native BlackBerry Java
UI: UiApplication, MainScreen, Field / Manager APIs
PERSISTENCE: PersistentStore / PersistentObject
```

This is not a BB10/QNX/Cascades/BBNDK project, not a WebWorks project, and not a generic MIDlet-only project.

## Canon boundary

The game is governed by the repository canonical files:

- `CANON/OPERATIVO-CHIMERA-CANON-LOCK.md`
- `CANON/FICTION-PROTOCOL-OC-00NULL.md`
- `CANON/DECISION-LOG.md`

The smoke-test source uses a compact header referring to OC-00NULL. It does not replace the full first-launch notice or the permanent archive record that will be implemented after this foundation compiles.

## How to run the smoke test

1. Create a **BlackBerry Java Application** project in a locally installed BlackBerry Java SDK/JDE or Eclipse BlackBerry Java Plug-in environment.
2. Select the closest available BlackBerry OS 7.1 API/runtime target for the Bold 9900/9930.
3. Copy the Java sources in `BB7/src/com/grup0zzzz/chimera/` into the project source folder.
4. Build the project without adding unavailable APIs or platform bypasses.
5. If compilation fails, paste the exact compiler output into `BB7/build-notes/COMPILER-ERROR-LOG.md`; do not rewrite the project from scratch.
6. If it compiles, launch it in the matching simulator and record the results in `BB7/SMOKE-TEST-MATRIX.md`.
7. Only after simulator confirmation should the app be installed using documented, authorized methods on the owner’s Bold 9900/9930.

## Expected behavior

On first launch the app should show a basic title screen containing:

```text
GRUP0ZZZZ: OPERATIVO CHIMERA
BB7 M0 SMOKE TEST

SAVE STATUS: NONE / AVAILABLE

[ FIRE ] SAVE TEST STATE
[ 0 ] EXIT
```

The first Fire/trackpad activation writes a test campaign record. A later launch should show `SAVE STATUS: AVAILABLE` and render the saved campaign revision.

## Explicit non-goals

- No final game UI.
- No title art asset requirement.
- No Z-rain animation yet.
- No OC-00NULL screen implementation yet.
- No archive browser yet.
- No Angie call flow yet.
- No network or AI integration.
- No signing bypasses, device-security bypasses, or OS modifications.

After this passes compilation and simulator checks, the next milestone adds the canonical first-launch notice and then the archive → note → Angie-call loop.
