# Roundtable ViewModel JVM tests

RoundtableViewModelTest defines twenty JUnit tests executed on the JVM with kotlinx-coroutines-test 1.9.0 and the existing real JVM org.json test dependency. No emulator, Android device, Ollama, API key or network is used. Production parsing is not mocked: successful fake replies pass through the shared SspParser.

The ViewModel now accepts RoundtableBackend with a production default OllamaRoundtableBackend adapter. The existing no-argument production construction and public start/startAgents/stop methods remain available. The adapter delegates unchanged to OllamaClient's existing validation, HTTP/cancellation behavior and close operation; no networking policy is relaxed.

A JUnit MainDispatcherRule replaces Main with StandardTestDispatcher, and runTest uses the same scheduler. Tests advance that scheduler without Thread.sleep or real delays. Each fixture owns a ViewModelStore and clears it after the test. Store clearing verifies actual onCleared resource cleanup.

Coverage: initial idle state; invalid topics, rosters and endpoints; compatibility turn-count limits; ordered agent calls and prior-success handoff; actual/requested model attribution; real SSP parsing and raw fallback; peer failures and continuation; duplicate-start prevention; cancellation before the job body runs; in-flight cancellation; retaining prior completed turns; propagating cancellation instead of creating failure turns; restart after stop; roster snapshot isolation; store-clear cancellation and exactly-once backend closure; compatibility API delegation; fresh-run state reset; observable active speaker; idle Stop.

Cancellation cleanup moved from body-only finally to an identity-guarded job completion handler. That handler also runs if cancellation happens before the queued coroutine body begins, preventing a stuck running/stopping UI. An old completed job cannot clear a different run's job reference. This is an intentional small production change with explicit regression coverage.

From android/, with JDK 17, installed Gradle 8.9 and the configured Android SDK:

```sh
# Only the new ViewModel suite.
gradle :app:testDebugUnitTest --tests 'com.chimera.agent.RoundtableViewModelTest'

# All app JVM tests plus the separate shared parser tests.
gradle :app:testDebugUnitTest :parser:test
```

Reports: app/build/reports/tests/testDebugUnitTest/ and app/build/test-results/testDebugUnitTest/. Existing android-build CI already invokes :app:testDebugUnitTest, so no workflow modification is needed.

Defined tests are not execution results. The authoring environment has no Java/Kotlin/Gradle/Android SDK, so this revision has not been compiled or executed here. Fake-backend cancellation does not prove real socket cancellation, device lifecycle behavior or live server behavior; those require transport/device integration tests. The endpoint validator itself remains covered by separate endpoint-policy tests.
