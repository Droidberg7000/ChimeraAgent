# Chimera Android app module

Native Compose local-Ollama client inside ChimeraAgent. MainActivity launches MultiAgentRoundtableStudio.kt; the old RoundtableScreen entry point remains as a compatibility delegate. The Python workbench and main branch are not changed.

## Roundtable

Enter a topic, a trusted local Ollama endpoint, and one Agent name=model:tag per line. Example:

```text
Architect=qwen3:4b
Auditor=llama3.2:latest
Synthesizer=qwen3:4b
```

All chosen models must already be downloaded. One to five uniquely named agents run sequentially. The default roster repeats qwen3:4b in three roles so it works with the previously configured model; repeated models are explicitly not independent verification. Different installed model tags are supported. There are no cloud providers, API keys or automatic fallbacks.

Each turn displays the agent name, requested model, actual model reported by Ollama, structured/raw status, and unverified agreement score. Failed requests stay visible, do not become peer evidence, and do not prevent later agents from attempting their configured model. Each agent receives the topic and the latest successful response, not a full debate transcript. Stop cancels the coroutine and OkHttp call; back through the screen's Back button also requests cancellation. Backgrounding is not guaranteed to stop a run. No automatic audio, modem/MEP tools, kernel claims, or private chain-of-thought collection is included.

## Shared parser

The app depends on :parser mapped to ../parser-tests. There is one SspParser implementation used by the real response handler and the six JVM tests. Invalid SSP output remains raw text, never a fabricated score. Android instrumentation sources reuse the same six tests against Android's org.json. The JVM org.json library is compileOnly plus testImplementation, not an APK runtime dependency.

## Build and test

Baseline: JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin/Compose compiler plugin 2.0.21, Android SDK 35, build tools 34.0.0, minimum API 26. Open android/ in Android Studio or, with the SDK and Gradle installed, run from android/:

```sh
gradle :parser:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

App APK: app/build/outputs/apk/debug/app-debug.apk. A device/emulator is required to actually execute instrumentation tests:

```sh
gradle :app:connectedDebugAndroidTest
```

No Gradle wrapper JAR is committed. Generate one locally with Gradle 8.9 if needed. Roster unit tests cover parsing, repeated models, invalid/cloud/routed tags and the five-agent limit; endpoint and parser tests remain separate.

## Local connection

The app calls Ollama /api/chat directly, not the Python workbench API. For a local Ollama service and an adb-connected device/emulator:

```sh
adb reverse tcp:11434 tcp:11434
```

Use the default http://127.0.0.1:11434/ app endpoint. The standard Android emulator also supports http://10.0.2.2:11434/ for host loopback access. With the existing Docker stack, publish Ollama only on host loopback using this command from workbench/:

```sh
docker compose -f docker-compose.yml -f ../android/docker-compose.android.yml up -d --build
```

Add the NVIDIA/AMD override when appropriate. Download additional models with `docker compose exec ollama ollama pull MODEL` using your stack's same Compose file arguments.

Debug cleartext is restricted to localhost, 127.0.0.1 and 10.0.2.2. Private LAN endpoints require trusted HTTPS unless debug policy is deliberately changed. Release builds disallow cleartext. Client redirects/proxy routing/public hosts/embedded credentials are disabled. A private server can itself route elsewhere; endpoint policy and cloud-tag rejection are not proof of offline inference. Only use a server you trust.

## Scope and verification

This is a local multi-model discussion UI, not cloud-provider fusion, independent fact checking, or the Python tool-agent frontend. Responses are non-streaming, bounded to 256 KB; topics to 16000 characters. ViewModel state survives rotation but not process death; no disk chat persistence is implemented. Server work already started is not rolled back by cancellation.

Authoring environment has no Android/Kotlin toolchain. Source checks are not compilation. The existing Android CI workflow tests JVM code, runs lint and builds app/instrumentation APKs; it does not run an emulator. A passing build/test result, working APK, live model or device behavior is not claimed until actual results are inspected.
