# BB7 Milestone 0 Smoke-Test Matrix

**Branch:** `bb7-m0-smoke`  
**Target:** BlackBerry Bold 9900/9930, BlackBerry Device Software 7.x  
**Status:** Pending local compiler and simulator verification

| ID | Test | Expected result | Actual result | Pass | Notes |
|---|---|---|---|---|---|
| M0.1 | Import project | BlackBerry Java Application project accepts all five Java sources | Pending | [ ] | |
| M0.2 | Compile | Target BB7 compiler reports no source errors | Pending | [ ] | Paste any errors into `build-notes/COMPILER-ERROR-LOG.md` |
| M0.3 | Launch | Application opens in the matching simulator | Pending | [ ] | |
| M0.4 | Render | Near-black screen, amber title, white status text render legibly | Pending | [ ] | |
| M0.5 | First save | Fire/trackpad activation changes status to saved and increments revision | Pending | [ ] | |
| M0.6 | Load | Closing and reopening reads the previous 30-byte CHIM record | Pending | [ ] | |
| M0.7 | Corrupt-save handling | Invalid/missing state returns safely to a new smoke state | Pending | [ ] | Optional initial check |
| M0.8 | Device install | Authorized local install on owner’s Bold 9900/9930 launches successfully | Pending | [ ] | Only after M0.1–M0.6 pass |

## Smoke-test evidence

Record the following after each attempt:

```text
Date:
Host operating system:
Java/JDK version:
BlackBerry SDK/JDE or Eclipse Plug-in version:
Target OS/API version:
Simulator model/image:
Build artifact produced:
Compiler result:
Runtime result:
Exact errors or warnings:
```

## Exit criteria

Milestone 0 foundation passes only when M0.1 through M0.6 pass in the local toolchain. M0.8 is the physical-device confirmation, not a prerequisite for source-level work.
