package com.chimera.agent

import java.util.concurrent.atomic.AtomicBoolean

/** Whether the ViewModel owns shutdown or only borrows a caller-managed backend. */
enum class BackendOwnership { OWNED, BORROWED }

/**
 * validateEndpoint must be fast, non-blocking, side-effect-free and thread-safe.
 * chat must be main-safe and cooperatively cancellable. close must be non-blocking.
 * An OWNED backend must not be shared. BORROWED backends are closed by their owner.
 */
interface RoundtableBackend {
    fun validateEndpoint(value: String)
    suspend fun chat(endpoint: String, model: String, topic: String, prior: String): ModelReply
    fun close()
}

/** Owns its private client; no externally shared HTTP client is silently taken over. */
class OllamaRoundtableBackend : RoundtableBackend {
    private val client = OllamaClient()
    private val closed = AtomicBoolean(false)
    override fun validateEndpoint(value: String) { client.validateEndpoint(value) }
    override suspend fun chat(endpoint: String, model: String, topic: String, prior: String): ModelReply =
        client.chat(endpoint, model, topic, prior)
    override fun close() {
        if (closed.compareAndSet(false, true)) client.close()
    }
}
