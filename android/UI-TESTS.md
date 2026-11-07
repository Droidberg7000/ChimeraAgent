# Studio Compose UI tests

MultiAgentRoundtableStudioTest contains twelve Android instrumentation tests. They mount MultiAgentRoundtableContent, the real screen's state-and-callback UI. No ViewModel is created, no Ollama process is needed, and no network, modem, audio, API keys or inference is used.

Coverage: empty state and disabled start; initial-topic prefill without auto-start; exact start callback arguments; malformed/cloud roster rejection; locked inputs while running; Stop callback; stopping-state lockout; stop-before-Back ordering; parsed/raw/failed response rendering and model attribution; session error visibility; completed-state re-enable without dispatch; rememberSaveable form restoration.

Production MultiAgentRoundtableStudio still collects the existing ViewModel and supplies its start/stop callbacks. The UI layer does not claim to test ViewModel orchestration, real HTTP cancellation, parser implementation, network privacy or full activity/process recreation. Those need separate tests. StateRestorationTester checks saveable Compose form values, not durable chat history.

Dependencies use the project's existing Compose BOM. ui-test-junit4 is instrumentation-only; ui-test-manifest is debug-only and supplies the test host activity. The screen has stable test tags, independently scrollable lazy control items, and IME padding. Automatic response scrolling is intentionally removed so it does not move the user's position or Stop control unexpectedly. Editing a form field clears stale local-validation errors.

From android/ with JDK 17, Gradle 8.9 and the configured Android SDK:

```sh
# Compile the application and test APKs; this does NOT execute UI tests.
gradle :app:assembleDebug :app:assembleDebugAndroidTest

# Execute only the studio UI suite on an attached device/emulator.
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chimera.agent.MultiAgentRoundtableStudioTest

# Execute all instrumentation tests, including the six shared parser tests.
gradle :app:connectedDebugAndroidTest
```

Reports appear under app/build/reports/androidTests/connected/ and app/build/outputs/androidTest-results/connected/. Existing Android CI builds instrumentation APKs but does not boot an emulator, so it does not run these tests. No passing run, Kotlin compile, emulator interaction or device test is claimed from source inspection. The authoring environment lacks the Android toolchain.
