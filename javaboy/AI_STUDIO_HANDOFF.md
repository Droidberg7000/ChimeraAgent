# AI Studio source handoff

    git clone --branch local-ollama-workbench --single-branch https://github.com/Droidberg7000/ChimeraAgent.git

Work only in javaboy/ and .github/workflows/javaboy-tests.yml; preserve other apps and main. This does not perform an AI Studio import. Keep Java MIDlets as Java; do not replace them with a pretend React emulator. External SDKs are required for native builds.

Run tests, inspect CI, build/test diagnostics on real hardware, then integrate the missing actual emulator through Core.Factory/Core and a real ROM loader. Preserve boot/cartridge offsets, validated battery data and sole-worker ownership. Do not fabricate binaries, test results, signatures, performance or compatibility. Do not commit proprietary SDKs, BIOS or commercial ROMs.
