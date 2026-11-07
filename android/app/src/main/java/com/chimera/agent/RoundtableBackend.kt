package com.chimera.agent

/** Inject this boundary to test orchestration without constructing a network client. */
interface RoundtableBackend {
    fun validateEndpoint(value: String)
    suspend fun chat(endpoint: String, model: String, topic: String, prior: String): ModelReply
    fun close()
}

/** Production adapter preserves OllamaClient's endpoint, transport and cancellation policies. */
class OllamaRoundtableBackend(private val client: OllamaClient = OllamaClient()) : RoundtableBackend {
    override fun validateEndpoint(value: String) { client.validateEndpoint(value) }
    override suspend fun chat(endpoint: String, model: String, topic: String, prior: String): ModelReply =
        client.chat(endpoint, model, topic, prior)
    override fun close() { client.close() }
}
