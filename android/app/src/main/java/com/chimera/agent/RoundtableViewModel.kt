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

class RoundtableViewModel : ViewModel() {
    private val client = OllamaClient()
    private val mutable = MutableStateFlow(DiscussionState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    /** Preserve the original single-model API for existing callers. */
    fun start(endpoint: String, model: String, topic: String, rounds: Int = 3) {
        if (rounds !in 1..5) {
            if (!mutable.value.running) mutable.value = mutable.value.copy(error = "Choose one to five turns")
            return
        }
        startAgents(endpoint, List(rounds) { LocalAgentSpec("Agent ${it + 1}", model) }, topic)
    }

    fun startAgents(endpoint: String, agents: List<LocalAgentSpec>, topic: String) {
        if (mutable.value.running) return
        val snapshot: List<LocalAgentSpec>
        val base = endpoint.trim()
        try {
            client.validateEndpoint(base)
            require(topic.isNotBlank() && topic.length <= 16000) { "Topic must contain 1-16000 characters" }
            snapshot = LocalAgentRoster.validate(agents)
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(error = e.message ?: "Invalid input")
            return
        }
        val runTopic = topic.trim()
        mutable.value = DiscussionState(running = true, status = "Starting local roundtable...")
        val task = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var prior = ""
            try {
                for ((index, agent) in snapshot.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    mutable.value = mutable.value.copy(activeAgent = agent.name,
                        status = "Turn ${index + 1}/${snapshot.size}: ${agent.name}")
                    val turn = try {
                        val response = client.chat(base, agent.model,
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
                    mutable.value = mutable.value.copy(turns = mutable.value.turns + turn)
                    // Only the latest successful response becomes peer context.
                    if (turn.isSuccess) prior = turn.text
                }
                val failures = mutable.value.turns.count { !it.isSuccess }
                mutable.value = mutable.value.copy(status =
                    "Completed with $failures failed request(s); model agreement is not verification")
            } catch (e: CancellationException) {
                mutable.value = mutable.value.copy(status = "Cancelled")
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(status = "Roundtable failed; no cloud fallback",
                    error = e.message ?: "Session error")
            } finally {
                mutable.value = mutable.value.copy(running = false, stopping = false, activeAgent = null)
                job = null
            }
        }
        job = task
        task.start()
    }

    fun stop() {
        if (mutable.value.running) {
            mutable.value = mutable.value.copy(stopping = true)
            job?.cancel()
        }
    }

    override fun onCleared() {
        job?.cancel()
        client.close()
        super.onCleared()
    }
}
