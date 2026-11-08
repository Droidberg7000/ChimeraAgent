# ChimeraTube client repair, revision 2

Implemented fixes for the six reviewed client problems. This is source, not a claimed
handset-tested JAR/COD. It does not change Android, the AI workbench or JavaBoyChimera.

## Implemented

- Feed.read parses CT1 incrementally with a reusable 512-byte network buffer and
  4096-byte row buffer. Total response <=32768 bytes, <=20 rows, exact five fields.
  Blank rows, excess fields, invalid IDs/profiles/durations and malformed UTF-8 fail
  early. No whole-response String, line Vector, field Vector or raw-feed callback.
- A single coalesced UI drain holds at most one pending parsed result, status and
  video-readiness notification. Every publication is generation-checked.
- Settings changes invalidate requests/playback and clear old results. Displayed
  entries and versioned RMS metadata caches are bound to gateway AND source.
- A single I/O worker handles settings/cache reads/writes and searches, with one
  latest pending request and one latest settings snapshot. No RMS I/O on UI callbacks.
- Cleanup has four admission tickets, one worker, at-most-once retirement and no
  per-click threads. Native stream/player close is outside UI callbacks/owner locks.
  No new player preparation until its predecessor's ticket completes. Fatal JVM
  Errors are not swallowed; native close can still stall and backpressure new work.
- Realize/prefetch runs on the playback worker. Canvas setup/visibility runs on the
  UI event thread, then Canvas.showNotify plus successful setup signal a cancellation-aware wait BEFORE start().
  Failed/time-expired setup retires the player and returns the UI to browsing.
- Old player events atomically recheck ownership before invalidating anything.
  Closed players remove listeners; Canvas references are cleared. Invalid gateway
  settings show an Alert on the form instead of changing an invisible list title.

## Protocol and server

This client uses the CT1 gateway protocol provided in the earlier full ChimeraTube
source bundle: /v1/search?q=...&source=catalog|invidious, header CT1<TAB>source<LF>,
then percent-encoded ID, title, author, nonnegative seconds and profile-list fields
separated by TAB. No blank rows. Prepared playback: /v1/media/ID/mp4 or /3gp.
This commit does NOT add the separately generated gateway, Docker files or FFmpeg
scripts to GitHub. It does not claim direct YouTube URL extraction, RTSP, offline
video, a JTube/MiniTube source fusion or fabricated playable fallback content.

## Tests

Desktop JDK portable-core tests, without MMAPI:

    mkdir -p build/core
    javac -encoding UTF-8 -d build/core client/src/com/chimeratube/Feed.java client/src/com/chimeratube/Cleanup.java client/test/com/chimeratube/CoreTest.java
    java -cp build/core com.chimeratube.CoreTest

These test actual parser/cache/cleanup classes. They do not run native networking,
VideoControl, lifecycle callbacks, display readiness or RMS. The authoring environment
has no Java/Javac/vendor APIs/preverify, so these tests are supplied but NOT RUN.
Source-structure checks are not runtime tests. CI supplies a desktop JDK for the
portable suite; its result must be inspected, not inferred from committing a workflow.

## Real MIDP build

Use MIDP 2.0, CLDC 1.1 and JSR-135 APIs. Supply CLDC_API, MIDP_API, MMAPI_API,
ECJ_JAR (a compiler supporting source 1.3/target 1.1), and PREVERIFY (an actual vendor
preverifier). Run python build_client.py. No proprietary SDK, wrapper binary,
pretend .cod or signing bypass is supplied. Configure the trusted gateway using the
Gateway form. A native-device build/playback test remains required.

## Limits

There is one persistent I/O worker, one cleanup worker and at most one playback
preparation worker. Backend-native decoder/network buffers are implementation-defined.
Socket/Player creation and close can block despite cancellation; thread count and
queues stay bounded, but prompt native shutdown is not guaranteed. The UI readiness
wait uses at most fifty 200-ms waits (best effort, not a real-time guarantee).
Do not mutate native/player state through outside code. HTTP gateway traffic is not
encrypted; use only a trusted endpoint/network. TLS and BlackBerry transport suffixes,
codecs, Canvas resizing and permissions require real target-device validation.
