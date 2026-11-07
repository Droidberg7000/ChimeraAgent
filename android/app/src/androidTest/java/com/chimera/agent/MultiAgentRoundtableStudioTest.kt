package com.chimera.agent

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose UI, fake callbacks/state: no ViewModel, Ollama, API keys or network. */
@RunWith(AndroidJUnit4::class)
class MultiAgentRoundtableStudioTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        state: DiscussionState = DiscussionState(),
        topic: String? = null,
        onStart: (String, List<LocalAgentSpec>, String) -> Unit = { _, _, _ -> },
        onStop: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MultiAgentRoundtableContent(state, onStart, onStop, onBack, topic)
            }
        }
    }

    private fun node(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("multi_agent_roundtable_screen")
            .performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    @Test fun blankTopicDisablesStartAndShowsEmptyState() {
        show()
        node("roundtable_start_stop").assertIsNotEnabled()
        node("roundtable_empty").assertIsDisplayed()
        node("roundtable_endpoint").assertTextContains("http://127.0.0.1:11434/")
    }

    @Test fun prefilledTopicDoesNotAutoStart() {
        var starts = 0
        show(topic = "Review architecture", onStart = { _, _, _ -> starts++ })
        node("roundtable_topic").assertTextContains("Review architecture")
        node("roundtable_start_stop").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, starts) }
    }

    @Test fun validRosterDispatchesExactValuesOnce() {
        var captured: Triple<String, List<LocalAgentSpec>, String>? = null
        var starts = 0
        show(onStart = { endpoint, agents, topic ->
            starts++
            captured = Triple(endpoint, agents, topic)
        })
        node("roundtable_endpoint").performTextReplacement("http://10.0.2.2:11434/")
        node("roundtable_topic").performTextReplacement("Inspect a design")
        node("roundtable_roster").performTextReplacement("Architect=qwen3:4b\nAuditor=llama3.2:latest")
        node("roundtable_start_stop").performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals("http://10.0.2.2:11434/", captured?.first)
            assertEquals(listOf(LocalAgentSpec("Architect", "qwen3:4b"),
                LocalAgentSpec("Auditor", "llama3.2:latest")), captured?.second)
            assertEquals("Inspect a design", captured?.third)
        }
    }

    @Test fun malformedRosterShowsErrorWithoutDispatch() {
        var starts = 0
        show(topic = "Topic", onStart = { _, _, _ -> starts++ })
        node("roundtable_roster").performTextReplacement("MissingEquals")
        node("roundtable_start_stop").performClick()
        node("roundtable_error").assertTextContains("Each line must be Agent name=model:tag")
        compose.runOnIdle { assertEquals(0, starts) }
    }

    @Test fun cloudRosterIsRejectedWithoutDispatch() {
        var starts = 0
        show(topic = "Topic", onStart = { _, _, _ -> starts++ })
        node("roundtable_roster").performTextReplacement("Agent=test:cloud")
        node("roundtable_start_stop").performClick()
        node("roundtable_error").assertTextContains(
            "Use downloaded local model tags; public routing and cloud tags are disabled")
        compose.runOnIdle { assertEquals(0, starts) }
    }

    @Test fun runningLocksInputsAndStopCallsOnlyStop() {
        var starts = 0
        var stops = 0
        show(state = DiscussionState(running = true, activeAgent = "Auditor"),
            onStart = { _, _, _ -> starts++ }, onStop = { stops++ })
        node("roundtable_endpoint").assertIsNotEnabled()
        node("roundtable_topic").assertIsNotEnabled()
        node("roundtable_roster").assertIsNotEnabled()
        node("roundtable_start_stop").assertTextContains("STOP DEBATE").performClick()
        node("roundtable_speaker").assertTextContains("Current speaker: Auditor")
        compose.runOnIdle { assertEquals(0, starts); assertEquals(1, stops) }
    }

    @Test fun stoppingDisablesRepeatedStop() {
        show(state = DiscussionState(running = true, stopping = true))
        node("roundtable_start_stop").assertTextContains("Stopping...").assertIsNotEnabled()
    }

    @Test fun backRequestsStopBeforeNavigation() {
        val calls = mutableListOf<String>()
        show(onStop = { calls.add("stop") }, onBack = { calls.add("back") })
        node("roundtable_back").performClick()
        compose.runOnIdle { assertEquals(listOf("stop", "back"), calls) }
    }

    @Test fun parsedRawAndFailedResponsesAreDistinguished() {
        show(state = DiscussionState(turns = listOf(
            DiscussionTurn(1, "actual-model", "Parsed reply", 0.75, true, "Architect", "requested-model"),
            DiscussionTurn(2, "qwen3:4b", "{not valid JSON", null, false, "Auditor"),
            DiscussionTurn(3, "", "Request failed: unavailable", null, false, "Synthesizer", "missing-model", false)
        )))
        node("roundtable_turn_1").assertIsDisplayed()
        compose.onNodeWithText("Requested local model: requested-model").assertExists()
        compose.onNodeWithText("Responded: Ollama / actual-model").assertExists()
        compose.onNodeWithText("Model-reported agreement: 0.75 (unverified)").assertExists()
        node("roundtable_turn_2").assertIsDisplayed()
        compose.onNodeWithText("{not valid JSON").assertExists()
        compose.onNodeWithText("Unstructured response shown without alteration").assertExists()
        node("roundtable_turn_3").assertIsDisplayed()
        compose.onNodeWithText("FAILED - no fallback model was used").assertExists()
        compose.onNodeWithText("Responded: Ollama / ").assertDoesNotExist()
    }

    @Test fun suppliedSessionErrorIsVisible() {
        show(state = DiscussionState(error = "Connection refused"))
        node("roundtable_error").assertTextContains("Connection refused").assertIsDisplayed()
    }

    @Test fun completedStateReenablesStartWithoutRedispatch() {
        val state = mutableStateOf(DiscussionState(running = true))
        var starts = 0
        compose.setContent {
            MaterialTheme {
                MultiAgentRoundtableContent(state.value, { _, _, _ -> starts++ }, {}, initialTopic = "Topic")
            }
        }
        node("roundtable_start_stop").assertTextContains("STOP DEBATE")
        compose.runOnIdle { state.value = DiscussionState(status = "Cancelled") }
        node("roundtable_start_stop").assertTextContains("START ROUNDTABLE").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, starts) }
    }

    @Test fun formValuesSurviveSaveableStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var starts = 0
        restoration.setContent {
            MaterialTheme {
                MultiAgentRoundtableContent(DiscussionState(), { _, _, _ -> starts++ }, {})
            }
        }
        node("roundtable_endpoint").performTextReplacement("http://10.0.2.2:11434/")
        node("roundtable_topic").performTextReplacement("Saved topic")
        node("roundtable_roster").performTextReplacement("Auditor=qwen3:4b")
        restoration.emulateSavedInstanceStateRestore()
        node("roundtable_endpoint").assertTextContains("http://10.0.2.2:11434/")
        node("roundtable_topic").assertTextContains("Saved topic")
        node("roundtable_roster").assertTextContains("Auditor=qwen3:4b")
        compose.runOnIdle { assertEquals(0, starts) }
    }
}
