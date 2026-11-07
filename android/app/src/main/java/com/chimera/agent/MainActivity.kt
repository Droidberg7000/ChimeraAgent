package com.chimera.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface { MultiAgentRoundtableStudio(onBack = { finish() }) }
            }
        }
    }
}

/** Compatibility entry point; the actual screen lives in its named source file. */
@Composable
fun RoundtableScreen(vm: RoundtableViewModel = viewModel()) {
    MultiAgentRoundtableStudio(vm = vm)
}
