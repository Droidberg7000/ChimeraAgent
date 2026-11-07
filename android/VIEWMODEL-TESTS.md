# Roundtable ViewModel JVM tests

RoundtableViewModelTest retains the original twenty orchestration tests. RoundtableConcurrencyTest adds twelve lifecycle, ownership and coordination tests. MainDispatcherRule and kotlinx-coroutines-test 1.9.0 provide controlled scheduling; the concurrency suite also uses bounded worker-thread/barrier joins to exercise actual concurrent control calls. No Thread.sleep, emulator, Ollama, API key or network is used.

The real ViewModel and shared SspParser execute against fake RoundtableBackend implementations. The production default remains a private-client OllamaRoundtableBackend, with no-argument ViewModel construction preserved. Existing parser, UI and endpoint-policy tests remain separate.

All job references, cleared/ownership flags, run-token checks and read-copy-write state transitions are protected by one gate. Validation is outside the gate and eligibility is rechecked before reservation. Coroutine/backend execution, cancellation and resource closing occur outside that gate. Completion callbacks use the same gate, including inline completion on a worker and cancellation before body entry. Old run tokens cannot publish into a cleared or different run.

BackendOwnership.OWNED (default) means exclusive ownership transfer; never share such a backend. BORROWED means the external owner closes it after all users finish. ViewModel clearing cancels only that ViewModel's job and does not close borrowed backends. An owned backend closes once, after the active job and its cancellation cleanup complete. Ordinary close failures are recorded instead of escaping completion callbacks. See COROUTINES.md for contracts and limits.

Coverage added: clear before queued body; background Stop; simultaneous starts; calls after clearing; borrowed/shared backend lifetimes; deferred owned shutdown; cancellation during validation; close failures; inline cancelled-scope completion; dropping late responses; no-argument constructor presence. The constructor test checks reflection only, not real Android factory instantiation.

From android/, with the documented SDK/JDK/Gradle setup:

```sh
gradle :app:testDebugUnitTest --tests 'com.chimera.agent.RoundtableViewModelTest' --tests 'com.chimera.agent.RoundtableConcurrencyTest'
gradle :app:testDebugUnitTest :parser:test
```

The existing Android CI invokes :app:testDebugUnitTest. Defined tests are not passing execution results: no Java/Kotlin/Gradle/Android SDK is available in the authoring environment. Transport cancellation, a real default factory, actual Compose lifecycle and device behavior still require integration tests. No compile or runtime success is claimed from source checks.
