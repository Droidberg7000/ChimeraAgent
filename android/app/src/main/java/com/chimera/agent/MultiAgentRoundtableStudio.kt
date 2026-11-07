package com.chimera.agent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Local Ollama agents only. Uses the shared SSP parser through RoundtableViewModel. */
@Composable
fun MultiAgentRoundtableStudio(
    onBack: () -> Unit = {},
    initialTopic: String? = null,
    vm: RoundtableViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var endpoint by rememberSaveable { mutableStateOf("http://127.0.0.1:11434/") }
    var topic by rememberSaveable { mutableStateOf(initialTopic.orEmpty()) }
    var roster by rememberSaveable { mutableStateOf(
        "Architect=qwen3:4b\nAuditor=qwen3:4b\nSynthesizer=qwen3:4b") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(initialTopic) {
        if (!state.running && initialTopic != null) topic = initialTopic
        // Never initiate inference just because navigation supplies a topic.
    }
    LaunchedEffect(state.turns.size) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.size)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp).testTag("multi_agent_roundtable_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.stop(); onBack() }) { Text("Back") }
                Text("Multi-Agent Roundtable Studio", style = MaterialTheme.typography.headlineSmall)
                Text("Local Ollama only. Up to five sequential agent roles; no cloud fallback.")
                OutlinedTextField(endpoint, { endpoint = it }, enabled = !state.running,
                    label = { Text("Local Ollama base URL") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(topic, { topic = it }, enabled = !state.running,
                    label = { Text("Discussion topic") }, minLines = 2, maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("roundtable_topic"))
                OutlinedTextField(roster, { roster = it }, enabled = !state.running,
                    label = { Text("One Agent name=model:tag per line") }, minLines = 3, maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("roundtable_roster"))
                Text("Every model must already be downloaded in Ollama. Repeating a model gives role-based turns, not independent verification.")
                Button(
                    enabled = if (state.running) !state.stopping else topic.isNotBlank() && roster.isNotBlank(),
                    modifier = Modifier.testTag("roundtable_start_stop"),
                    onClick = {
                        if (state.running) vm.stop() else {
                            inputError = null
                            try { vm.startAgents(endpoint, LocalAgentRoster.parse(roster), topic) }
                            catch (e: IllegalArgumentException) { inputError = e.message }
                        }
                    }
                ) { Text(if (state.stopping) "Stopping..." else if (state.running) "STOP DEBATE" else "START ROUNDTABLE") }
                Text(state.status)
                state.activeAgent?.let { Text("Current speaker: $it") }
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                (inputError ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.turns.isEmpty()) Text("No responses yet. Starting sends the topic and prior successful response to the configured local server.")
            }
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
