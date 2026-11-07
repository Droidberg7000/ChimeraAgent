package com.chimera.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { RoundtableScreen() } } }
    }
}

@Composable
fun RoundtableScreen(vm: RoundtableViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var endpoint by rememberSaveable { mutableStateOf("http://127.0.0.1:11434/") }
    var model by rememberSaveable { mutableStateOf("qwen3:4b") }
    var topic by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Chimera Agent", style = MaterialTheme.typography.headlineSmall)
        Text("Local Ollama discussion • no cloud fallback • three turns of the same model")
        OutlinedTextField(endpoint, { endpoint = it }, enabled = !state.running,
            label = { Text("Local Ollama base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(model, { model = it }, enabled = !state.running,
            label = { Text("Downloaded local model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(topic, { topic = it }, enabled = !state.running,
            label = { Text("Discussion topic") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        Button(enabled = if (state.running) !state.stopping else topic.isNotBlank(), onClick = {
            if (state.running) vm.stop() else vm.start(endpoint, model, topic)
        }) { Text(if (state.stopping) "Stopping..." else if (state.running) "Stop" else "Start") }
        Text(state.status)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.turns, key = { it.number }) { turn ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Turn ${turn.number} • Ollama / ${turn.model}")
                        Text(if (turn.structured) "SSP-V1 parsed" else "Unstructured output; shown without alteration")
                        Text(turn.text)
                        turn.agreement?.let { Text("Model-reported agreement: $it; not factual verification") }
                    }
                }
            }
        }
    }
}
