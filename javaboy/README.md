# JavaBoyChimera source + BlackBerry diagnostics

Three MIDlets share a worker/input/RMS runtime. Default build is an actual generated test pattern, clearly labeled DIAGNOSTIC, NOT EMULATION. CPU/PPU/APU, cartridge mappers, ROM loader and the other claimed console engines are NOT supplied. No proprietary SDK, BIOS, ROM or fake binary is bundled.

## Native builds

Use genuine target-compatible BlackBerry SDK tools and their supported Java environment. Set RAPC to rapc.exe and BB_API to its matching net_rim_api.jar. Run from this directory:

    python build.py --target bold9900
    python build.py --target pearl9105
    python build.py --target pearl8100

Select SDK APIs no newer than the minimum installed target OS. The builder refuses missing tools and existing output directories. It invokes RAPC, extracts sibling COD modules, derives descriptor sizes, and emits ALX/checksums. After vendor signing rerun `python build.py --package-only build/bold9900-diagnostic`. SDK invocation, signing, native installation and hardware behavior still require validation; no completed native binary is claimed.

Actual emulator integration: `python build.py --target bold9900 --factory your.package.Factory --backend /actual/source/root`. Implement Core.Factory/Core; validate battery images before mutation and use cartridge fingerprints as storage keys. Resource packaging must be added explicitly if the backend needs it.

## Hardware checks

Verify animation, displayed button mask, scaling, save/restart/load, pause/resume and exit. Sound explicitly reports unavailable in diagnostics. Numeric keys: 2/8/4/6, 5=A, 0=B, *=Select, #=Start. Bold adds WASD/J/K; 8100 adds QW/AS/OP/KL/TY/UI; 9105 uses numeric input. Validate actual keycodes. RMS/shutdown are best-effort, not power-loss-atomic.

## Tests and scope

Run `node --test test/host.test.mjs`. CI also tests portable Java classes, NOT native RMS/MIDP/SDK compatibility. Observed results are in VALIDATION.json; inspect actual CI status. Host JS includes a boot overlay, bounded big-endian N64 RAM, an experimental Slot-2 buffer and honest browser rumble, NOT full emulators or physical accessory protocols. No Pal Park, barcode/light gun, camera/printer or other console engine is supplied. See AI_STUDIO_HANDOFF.md.
