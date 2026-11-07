package com.chimera.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SspParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiscussionTurn(val number: Int, val model: String, val text: String,
    val agreement: Double?, val structured: Boolean,
    val agentName: String = "Local agent", val requestedModel: String = model,
    val isSuccess: Boolean = true)
data class DiscussionState(val running: Boolean = false, val stopping: Boolean = false,
    val turns: List<DiscussionTurn> = emptyList(), val status: String = "Ready",
    val error: String? = null, val activeAgent: String? = null)

/** Control calls may arrive from any thread; one gate serializes all lifecycle/state changes. */
class RoundtableViewModel(
    private val backend: RoundtableBackend = OllamaRoundtableBackend(),
    private val ownership: BackendOwnership = BackendOwnership.OWNED
) : ViewModel() {
    private val gate = Any()
    private val mutable = MutableStateFlow(DiscussionState())
    val state = mutable.asStateFlow()
    private var job: Job? = null // Access only under gate.
    private var runToken: Any? = null
    private var cleared = false
    private var backendReleased = false

    private fun publish(token: Any, change: (DiscussionState) -> DiscussionState): Boolean = synchronized(gate) {
        if (cleared || runToken !== token) false else {
            mutable.value = change(mutable.value)
            true
        }
    }

    fun start(endpoint: String, model: String, topic: String, rounds: Int = 3) {
        if (rounds !in 1..5) {
            synchronized(gate) {
                if (!cleared && job == null) mutable.value = mutable.value.copy(error = "Choose one to five turns")
            }
            return
        }
        startAgents(endpoint, List(rounds) { LocalAgentSpec("Agent ${it + 1}", model) }, topic)
    }

    fun startAgents(endpoint: String, agents: List<LocalAgentSpec>, topic: String) {
        if (!synchronized(gate) { !cleared && job == null }) return
        val base = endpoint.trim()
        val snapshot: List<LocalAgentSpec>
        try {
            // External validation is outside the gate; recheck eligibility before reserving a run.
            backend.validateEndpoint(base)
            require(topic.isNotBlank() && topic.length <= 16000) { "Topic must contain 1-16000 characters" }
            snapshot = LocalAgentRoster.validate(agents)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(gate) {
                if (!cleared && job == null) mutable.value = mutable.value.copy(error = e.message ?: "Invalid input")
            }
            return
        }
        val runTopic = topic.trim()
        val task = synchronized(gate) {
            if (cleared || job != null) return
            val token = Any()
            runToken = token
            val created = viewModelScope.launch(start = CoroutineStart.LAZY) {
                var prior = ""
                try {
                    for ((index, agent) in snapshot.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        if (!publish(token) { it.copy(activeAgent = agent.name,
                            status = "Turn ${index + 1}/${snapshot.size}: ${agent.name}") }) return@launch
                        val turn = try {
                            val response = backend.chat(base, agent.model,
                                "Discussion role: ${agent.name}\nTopic: $runTopic", prior)
                            currentCoroutineContext().ensureActive()
                            val parsed = SspParser.parse(response.text)
                            DiscussionTurn(index + 1, response.model, parsed?.text ?: response.text,
                                parsed?.consensusScore, parsed != null, agent.name, agent.model)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            DiscussionTurn(index + 1, "", "Request failed: ${e.message ?: "Unknown error"}",
                                null, false, agent.name, agent.model, false)
                        }
                        currentCoroutineContext().ensureActive()
                        if (!publish(token) { it.copy(turns = it.turns + turn) }) return@launch
                        if (turn.isSuccess) prior = turn.text
                    }
                    publish(token) { current ->
                        val failures = current.turns.count { !it.isSuccess }
                        current.copy(status = "Completed with $failures failed request(s); model agreement is not verification")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    publish(token) { it.copy(status = "Roundtable failed; no cloud fallback",
                        error = e.message ?: "Session error") }
                }
            }
            job = created
            mutable.value = DiscussionState(running = true, status = "Starting local roundtable...")
            // May run inline or on another thread, including cancellation before body entry.
            created.invokeOnCompletion { cause ->
                synchronized(gate) {
                    if (runToken === token) {
                        job = null
                        runToken = null
                        mutable.value = mutable.value.copy(running = false, stopping = false, activeAgent = null,
                            status = if (cause is CancellationException) "Cancelled" else mutable.value.status)
                    }
                }
            }
            created
        }
        // Never start backend work or invoke cancellation while holding the state gate.
        task.start()
    }

    fun stop() {
        val target = synchronized(gate) {
            if (cleared) return
            job?.also { mutable.value = mutable.value.copy(stopping = true) }
        }
        target?.cancel()
    }

    private fun releaseOwnedBackend() {
        val release = synchronized(gate) {
            if (ownership == BackendOwnership.OWNED && !backendReleased) {
                backendReleased = true
                true
            } else false
        }
        if (release) try {
            backend.close()
        } catch (e: Exception) {
            // Do not throw an ordinary shutdown failure out of a coroutine completion handler.
            synchronized(gate) {
                mutable.value = mutable.value.copy(error = "Backend cleanup failed: ${e.javaClass.simpleName}")
            }
        }
    }

    override fun onCleared() {
        val target = synchronized(gate) {
            if (cleared) return
            cleared = true
            val current = job
            job = null
            runToken = null // Invalidate all late results and old completion callbacks.
            mutable.value = mutable.value.copy(running = false, stopping = false, activeAgent = null,
                status = if (current != null) "Cancelled" else mutable.value.status)
            current
        }
        try {
            target?.cancel()
            // Completion registration also works for an already-completed job.
            if (target == null) releaseOwnedBackend()
            else target.invokeOnCompletion { releaseOwnedBackend() }
        } finally {
            super.onCleared()
        }
    }
}
