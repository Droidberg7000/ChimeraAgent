package com.chimera.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SspParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext

data class DiscussionTurn(val number: Int, val model: String, val text: String,
    val agreement: Double?, val structured: Boolean)
data class DiscussionState(val running: Boolean = false, val stopping: Boolean = false,
    val turns: List<DiscussionTurn> = emptyList(), val status: String = "Ready", val error: String? = null)

class RoundtableViewModel : ViewModel() {
    private val client = OllamaClient()
    private val mutable = MutableStateFlow(DiscussionState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun start(endpoint: String, model: String, topic: String, rounds: Int = 3) {
        if (mutable.value.running) return
        try {
            client.validateEndpoint(endpoint)
            require(topic.isNotBlank() && topic.length <= 16000)
            require(rounds in 1..5)
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(error = e.message ?: "Invalid input")
            return
        }
        mutable.value = DiscussionState(running = true, status = "Requesting local model...")
        val task = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var prior = ""
            try {
                repeat(rounds) { index ->
                    currentCoroutineContext().ensureActive()
                    val response = client.chat(endpoint.trim(), model.trim(), topic.trim(), prior)
                    currentCoroutineContext().ensureActive()
                    // Production UI uses the SAME parser that the :parser JVM tests compile.
                    val parsed = SspParser.parse(response.text)
                    val turn = DiscussionTurn(index + 1, response.model, parsed?.text ?: response.text,
                        parsed?.consensusScore, parsed != null)
                    mutable.value = mutable.value.copy(turns = mutable.value.turns + turn,
                        status = "Turn ${index + 1} completed")
                    prior = turn.text
                }
                mutable.value = mutable.value.copy(status = "Completed; responses are unverified")
            } catch (e: CancellationException) {
                mutable.value = mutable.value.copy(status = "Cancelled")
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(status = "Failed; no cloud fallback",
                    error = e.message ?: "Request failed")
            } finally {
                mutable.value = mutable.value.copy(running = false, stopping = false)
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
