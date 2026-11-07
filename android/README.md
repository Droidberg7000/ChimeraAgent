# Chimera Android app module

A new, separate Gradle Android application in ChimeraAgent. This is a local-Ollama client, not a WebView, and not a port of unverified cloud/modem tools. The Python workbench remains separate. main is not modified by this branch commit.

## Parser integration

The app depends on :parser, mapped to ../parser-tests. RoundtableViewModel imports com.example.ai.SspParser and parses real Ollama replies. Invalid structured output remains visible as raw text. Agreement scores remain unverified. There is one parser implementation, not a copied Android fork. Existing six JVM tests compile that production implementation. The same six tests are also included in Android instrumentation sources to check the actual platform org.json.

## Build

Pinned baseline: JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin/Compose compiler plugin 2.0.21, compile/target SDK 35, min SDK 26. These are explicit baseline versions, not a claim of latest releases. Install Android SDK platform 35 and build tools 34.0.0. Open this android/ directory in Android Studio, or use an installed Gradle 8.9:

```sh
gradle :parser:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Debug APK: app/build/outputs/apk/debug/app-debug.apk. Connected device/emulator parser tests:

```sh
gradle :app:connectedDebugAndroidTest
```

No wrapper JAR or generated binary is committed. You need installed Gradle 8.9; generate a wrapper locally with `gradle wrapper --gradle-version 8.9` if desired. First sync/build downloads dependencies.

## Connect to Ollama

The app connects directly to Ollama /api/chat, not the Python workbench API. It does not manage workbench tools, sessions, approvals or backups. Run Ollama on your development computer, download qwen3:4b (or another suitable local model), then for an adb-connected Android device/emulator:

```sh
adb reverse tcp:11434 tcp:11434
```

Keep the default app URL http://127.0.0.1:11434/. In the standard Android emulator, http://10.0.2.2:11434/ is an alternative host-loopback route. The original Compose stack does not publish Ollama. To use it with adb reverse, from workbench/ explicitly add the Android loopback-port override:

```sh
docker compose -f docker-compose.yml -f ../android/docker-compose.android.yml up -d --build
```

Add your NVIDIA/AMD override as another -f argument when needed. No LAN/public Ollama port is published by this override.

Debug builds permit cleartext only for localhost, 127.0.0.1 and 10.0.2.2. Private LAN IPv4 endpoints require HTTPS with a normally trusted certificate unless you deliberately change debug policy. Release builds disallow cleartext. There is no trust-all TLS handler. The client rejects public endpoints, embedded credentials, paths, redirects and obvious cloud model tags. This is an endpoint policy, not proof of air-gap isolation: a private Ollama server can itself route elsewhere. Do not send secrets to an untrusted endpoint.

## Behavior and boundaries

Start makes three sequential calls to the same selected local model, carrying the previous reply forward as untrusted context. It is not advertised as multiple independent agents or verification. Stop cancels the coroutine and underlying OkHttp call; it does not undo server processing or unload the model. State survives rotation through ViewModel but is not persisted to disk and is lost on process death. Leaving the app in the background does not necessarily stop it; press Stop explicitly. Inference is non-streaming in this initial Android client. Responses are capped at 256 KB; prompts at 16000 characters. No automatic fallback, Firebase, API keys, modem commands, unlock calculation or audio playback is included.

## Validation status

Authoring environment has no Java/Kotlin/Gradle/Android SDK. Source/XML checks are not a build. The android-build workflow installs tools, runs JVM tests and lint, compiles app and instrumentation APKs, and uploads reports/APKs. It does NOT run an emulator. Device tests require the connectedDebugAndroidTest command above. Inspect actual CI results before treating the app as built or tested. No APK, live model, emulator, hardware or parser runtime success is claimed in advance.
