package com.chimera.agent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Production route: ViewModel owns inference, parser use and cancellation. */
@Composable
fun MultiAgentRoundtableStudio(
    onBack: () -> Unit = {},
    initialTopic: String? = null,
    vm: RoundtableViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    MultiAgentRoundtableContent(state = state, onStart = vm::startAgents,
        onStop = vm::stop, onBack = onBack, initialTopic = initialTopic)
}

/** The same UI used in production, driven by state/callbacks for network-free tests. */
@Composable
fun MultiAgentRoundtableContent(
    state: DiscussionState,
    onStart: (String, List<LocalAgentSpec>, String) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit = {},
    initialTopic: String? = null
) {
    var endpoint by rememberSaveable { mutableStateOf("http://127.0.0.1:11434/") }
    var topic by rememberSaveable { mutableStateOf(initialTopic.orEmpty()) }
    var roster by rememberSaveable { mutableStateOf(
        "Architect=qwen3:4b\nAuditor=qwen3:4b\nSynthesizer=qwen3:4b") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(initialTopic) {
        if (!state.running && initialTopic != null) topic = initialTopic
        // Navigation prefill must never trigger inference.
    }
    // Each control is its own lazy item, making it reachable on small screens.
    // Do not auto-scroll on responses: keep the user's scroll/Stop position stable.
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .padding(12.dp).testTag("multi_agent_roundtable_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            Column {
                TextButton(modifier = Modifier.testTag("roundtable_back"),
                    onClick = { onStop(); onBack() }) { Text("Back") }
                Text("Multi-Agent Roundtable Studio", style = MaterialTheme.typography.headlineSmall)
                Text("Local Ollama only. Up to five sequential agent roles; no cloud fallback.")
            }
        }
        item(key = "endpoint") {
            OutlinedTextField(endpoint, { endpoint = it; inputError = null }, enabled = !state.running,
                label = { Text("Local Ollama base URL") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("roundtable_endpoint"))
        }
        item(key = "topic") {
            OutlinedTextField(topic, { topic = it; inputError = null }, enabled = !state.running,
                label = { Text("Discussion topic") }, minLines = 2, maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("roundtable_topic"))
        }
        item(key = "roster") {
            OutlinedTextField(roster, { roster = it; inputError = null }, enabled = !state.running,
                label = { Text("One Agent name=model:tag per line") }, minLines = 3, maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("roundtable_roster"))
        }
        item(key = "notice") {
            Text("Every model must already be downloaded in Ollama. Repeating a model gives role-based turns, not independent verification.")
        }
        item(key = "action") {
            Button(
                enabled = if (state.running) !state.stopping else topic.isNotBlank() && roster.isNotBlank(),
                modifier = Modifier.testTag("roundtable_start_stop"),
                onClick = {
                    if (state.running) onStop() else {
                        inputError = null
                        try { onStart(endpoint, LocalAgentRoster.parse(roster), topic) }
                        catch (e: IllegalArgumentException) { inputError = e.message }
                    }
                }
            ) { Text(if (state.stopping) "Stopping..." else if (state.running) "STOP DEBATE" else "START ROUNDTABLE") }
        }
        item(key = "status") {
            Column {
                Text(state.status, modifier = Modifier.testTag("roundtable_status"))
                state.activeAgent?.let { Text("Current speaker: $it", modifier = Modifier.testTag("roundtable_speaker")) }
                if (state.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("roundtable_progress"))
            }
        }
        val error = inputError ?: state.error
        if (error != null) item(key = "error") {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("roundtable_error"))
        }
        if (state.turns.isEmpty()) item(key = "empty") {
            Text("No responses yet. Starting sends the topic and prior successful response to the configured local server.",
                modifier = Modifier.testTag("roundtable_empty"))
        }
        items(state.turns, key = { "turn_${it.number}" }) { AgentPeerBubble(it) }
    }
}

@Composable
fun AgentPeerBubble(turn: DiscussionTurn) {
    Card(Modifier.fillMaxWidth().testTag("roundtable_turn_${turn.number}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Turn ${turn.number}: ${turn.agentName}", style = MaterialTheme.typography.titleMedium)
            Text("Requested local model: ${turn.requestedModel}")
            if (turn.isSuccess) {
                Text("Responded: Ollama / ${turn.model}")
                Text(if (turn.structured) "SSP-V1 parsed" else "Unstructured response shown without alteration")
            } else Text("FAILED - no fallback model was used", color = MaterialTheme.colorScheme.error)
            Text(turn.text)
            turn.agreement?.let { Text("Model-reported agreement: $it (unverified)") }
        }
    }
}
