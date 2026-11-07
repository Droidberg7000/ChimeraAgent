# Coroutine coordination and ownership

## Serialization policy

Control methods support calls from any thread. A single synchronized gate protects job/run-token references, cleared/backend-release flags and all state mutations. StateFlow is used for observation; its own thread safety is not treated as protection for unrelated fields. Coroutine work still runs in viewModelScope. No backend chat, cancellation or close operation executes under the gate.

Preflight validation is fast, pure and thread-safe by RoundtableBackend contract; it happens outside the lock. A concurrent caller can validate too, but the locked reservation recheck permits only one accepted job. A concurrent clear can prevent a validated request from being accepted. Callers must not mutate an input roster concurrently while startAgents snapshots it; normal mutations after the method returns cannot change the accepted run.

Each run receives an identity token. All coroutine publications and completion cleanup check that token. Completion handlers may execute inline or on any completing thread, and use the same gate. Cancellation before body entry is handled by invokeOnCompletion. Stop captures its target under the gate and cancels that job outside it; it cannot cancel a subsequently created job. Clearing invalidates tokens and permanently rejects future starts.

## Backend ownership

Default construction uses BackendOwnership.OWNED: the ViewModel exclusively owns its backend. The production adapter creates a private OllamaClient and makes close idempotent. It no longer accepts an externally shared client by constructor.

For a backend intentionally shared by an application-level owner:

```kotlin
RoundtableViewModel(sharedBackend, BackendOwnership.BORROWED)
```

The external owner is responsible for thread safety and closing the shared backend only after all borrowers have finished. A borrower still cancels its own coroutine, which a compliant backend should map to that request's cancellation rather than cancelAll. The current Ollama transport already binds a coroutine cancellation to its individual OkHttp Call.

An owned backend is released after the active job finishes, including non-cancellable finally cleanup. No active job means immediate release. A release flag prevents repeated shutdown. Ordinary close exceptions become a generic state error; fatal JVM Errors are not swallowed. If backend cancellation cleanup never finishes, owned release is correspondingly delayed: this is not a forced shutdown guarantee. Backend chat must be main-safe/cooperatively cancellable; validateEndpoint and close must not block.

## Verification limits

The twelve new tests target these contracts using fakes and controlled scheduling, with worker-thread tests invoking controls concurrently or off-main. Static source checks do not demonstrate race freedom. Existing twenty tests remain in place, but neither suite was compiled or executed in the authoring environment. Real transport cancellation, default Android factory instantiation and device lifecycle tests remain pending. main, parser, UI, networking implementation and Docker files are not modified by this change.
