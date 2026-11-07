package com.chimera.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoundtableViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private lateinit var store: ViewModelStore
    private lateinit var fake: FakeBackend
    private lateinit var vm: RoundtableViewModel
    private val endpoint = "http://127.0.0.1:11434/"
    private val agents = listOf(LocalAgentSpec("Architect", "qwen3:4b"),
        LocalAgentSpec("Auditor", "llama3.2:latest"), LocalAgentSpec("Synthesizer", "qwen3:4b"))

    data class Call(val endpoint: String, val model: String, val topic: String, val prior: String)
    private class FakeBackend : RoundtableBackend {
        val calls = mutableListOf<Call>()
        val validations = mutableListOf<String>()
        var validationError: IllegalArgumentException? = null
        var cancellations = 0
        var closes = 0
        var respond: suspend (Call) -> ModelReply = { ModelReply("Raw reply", it.model) }
        override fun validateEndpoint(value: String) {
            validations.add(value)
            validationError?.let { throw it }
        }
        override suspend fun chat(endpoint: String, model: String, topic: String, prior: String): ModelReply {
            val call = Call(endpoint, model, topic, prior)
            calls.add(call)
            return try { respond(call) } catch (e: CancellationException) {
                cancellations++
                throw e
            }
        }
        override fun close() { closes++ }
    }

    @Before fun setUp() {
        fake = FakeBackend()
        store = ViewModelStore()
        val provider = ViewModelProvider(store, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelClass.cast(RoundtableViewModel(fake))
        })
        vm = provider[RoundtableViewModel::class.java]
    }
    @After fun tearDown() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }
    private fun ssp(text: String, score: Double = 0.5): String = JSONObject()
        .put("protocol_version", "SSP-V1").put("response_payload", text)
        .put("consensus_score", score).toString()

    @Test fun initialStateIsIdleWithoutRequests() = runTest(main.dispatcher) {
        assertEquals(DiscussionState(), vm.state.value)
        assertTrue(fake.calls.isEmpty())
        assertTrue(fake.validations.isEmpty())
    }

    @Test fun blankAndOversizedTopicsNeverDispatch() = runTest(main.dispatcher) {
        for (topic in listOf("", "   ", "x".repeat(16001))) {
            vm.startAgents(endpoint, agents, topic)
            advanceUntilIdle()
            assertFalse(vm.state.value.running)
            assertNotNull(vm.state.value.error)
        }
        assertTrue(fake.calls.isEmpty())
    }

    @Test fun invalidRostersNeverDispatch() = runTest(main.dispatcher) {
        val invalid = listOf(emptyList(), List(6) { LocalAgentSpec("Agent$it", "qwen3:4b") },
            listOf(LocalAgentSpec("Same", "qwen3:4b"), LocalAgentSpec("same", "qwen3:4b")),
            listOf(LocalAgentSpec("Cloud", "model:cloud")), listOf(LocalAgentSpec("Route", "host/model")))
        for (roster in invalid) {
            vm.startAgents(endpoint, roster, "Topic")
            advanceUntilIdle()
            assertFalse(vm.state.value.running)
            assertNotNull(vm.state.value.error)
        }
        assertTrue(fake.calls.isEmpty())
    }

    @Test fun rejectedEndpointNeverDispatches() = runTest(main.dispatcher) {
        fake.validationError = IllegalArgumentException("Endpoint blocked")
        vm.startAgents("https://public.invalid/", agents, "Topic")
        advanceUntilIdle()
        assertEquals("Endpoint blocked", vm.state.value.error)
        assertFalse(vm.state.value.running)
        assertTrue(fake.calls.isEmpty())
    }

    @Test fun compatibilityApiRejectsInvalidTurnCount() = runTest(main.dispatcher) {
        for (rounds in listOf(0, 6)) {
            vm.start(endpoint, "qwen3:4b", "Topic", rounds)
            advanceUntilIdle()
            assertNotNull(vm.state.value.error)
        }
        assertTrue(fake.calls.isEmpty())
    }

    @Test fun successfulSequenceUsesRealParserAndActualModelLabels() = runTest(main.dispatcher) {
        fake.respond = { call -> ModelReply(ssp("Reply ${fake.calls.size}", 0.75), "actual-${call.model}") }
        vm.startAgents("  $endpoint  ", agents, "  Design review  ")
        assertTrue(vm.state.value.running)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(agents.map { it.model }, fake.calls.map { it.model })
        assertEquals(listOf("", "Reply 1", "Reply 2"), fake.calls.map { it.prior })
        assertTrue(fake.calls.all { it.endpoint == endpoint })
        assertEquals("Discussion role: Architect\nTopic: Design review", fake.calls.first().topic)
        assertEquals(agents.map { it.name }, state.turns.map { it.agentName })
        assertEquals("actual-qwen3:4b", state.turns.first().model)
        assertEquals("qwen3:4b", state.turns.first().requestedModel)
        assertTrue(state.turns.all { it.structured && it.isSuccess })
        assertEquals(0.75, state.turns.first().agreement ?: -1.0, 0.0)
        assertFalse(state.running)
        assertFalse(state.stopping)
        assertNull(state.activeAgent)
        assertTrue(state.status.contains("0 failed"))
    }

    @Test fun malformedStructuredOutputRemainsRawWithoutScore() = runTest(main.dispatcher) {
        val raw = "{not valid JSON"
        fake.respond = { ModelReply(raw, "reported-model") }
        vm.startAgents(endpoint, agents.take(1), "Topic")
        advanceUntilIdle()
        val turn = vm.state.value.turns.single()
        assertEquals(raw, turn.text)
        assertFalse(turn.structured)
        assertNull(turn.agreement)
        assertTrue(turn.isSuccess)
    }

    @Test fun failedPeerIsVisibleButNeverForwardedAndLaterAgentsContinue() = runTest(main.dispatcher) {
        fake.respond = { call ->
            if (fake.calls.size == 2) throw IOException("offline")
            ModelReply(ssp(if (fake.calls.size == 1) "Good evidence" else "Final reply"), call.model)
        }
        vm.startAgents(endpoint, agents, "Topic")
        advanceUntilIdle()
        val turns = vm.state.value.turns
        assertEquals(3, fake.calls.size)
        assertEquals(listOf("", "Good evidence", "Good evidence"), fake.calls.map { it.prior })
        assertFalse(turns[1].isSuccess)
        assertEquals("", turns[1].model)
        assertEquals(agents[1].model, turns[1].requestedModel)
        assertNull(turns[1].agreement)
        assertTrue(turns[1].text.contains("offline"))
        assertTrue(turns[2].isSuccess)
        assertTrue(vm.state.value.status.contains("1 failed"))
    }

    @Test fun secondStartWhileRunningIsIgnored() = runTest(main.dispatcher) {
        fake.respond = { awaitCancellation() }
        vm.startAgents(endpoint, agents, "First topic")
        runCurrent()
        val validations = fake.validations.size
        vm.startAgents(endpoint, agents.take(1), "Second topic")
        runCurrent()
        assertEquals(1, fake.calls.size)
        assertEquals(validations, fake.validations.size)
        assertTrue(fake.calls.single().topic.contains("First topic"))
        vm.stop()
        runCurrent()
    }

    @Test fun stopBeforeQueuedBodyStartsResetsStateWithoutDispatch() = runTest(main.dispatcher) {
        vm.startAgents(endpoint, agents, "Topic")
        vm.stop()
        runCurrent()
        assertTrue(fake.calls.isEmpty())
        assertFalse(vm.state.value.running)
        assertFalse(vm.state.value.stopping)
        assertNull(vm.state.value.activeAgent)
        assertEquals("Cancelled", vm.state.value.status)
    }

    @Test fun stopCancelsInflightRequestAndPreventsFollowingAgents() = runTest(main.dispatcher) {
        fake.respond = { awaitCancellation() }
        vm.startAgents(endpoint, agents, "Topic")
        runCurrent()
        assertEquals("Architect", vm.state.value.activeAgent)
        vm.stop()
        runCurrent()
        assertEquals(1, fake.calls.size)
        assertEquals(1, fake.cancellations)
        assertTrue(vm.state.value.turns.isEmpty())
        assertFalse(vm.state.value.running)
        assertNull(vm.state.value.activeAgent)
        assertEquals("Cancelled", vm.state.value.status)
    }

    @Test fun stopRetainsCompletedTurnButAddsNoFakeFailure() = runTest(main.dispatcher) {
        fake.respond = { call ->
            if (fake.calls.size == 2) awaitCancellation()
            ModelReply(ssp("Completed first reply"), call.model)
        }
        vm.startAgents(endpoint, agents, "Topic")
        runCurrent()
        assertEquals(2, fake.calls.size)
        vm.stop()
        runCurrent()
        assertEquals(1, vm.state.value.turns.size)
        assertEquals("Completed first reply", vm.state.value.turns.single().text)
        assertTrue(vm.state.value.turns.single().isSuccess)
        assertEquals(2, fake.calls.size)
    }

    @Test fun backendCancellationIsNotConvertedIntoPeerFailure() = runTest(main.dispatcher) {
        fake.respond = { throw CancellationException("Cancelled upstream") }
        vm.startAgents(endpoint, agents, "Topic")
        advanceUntilIdle()
        assertEquals(1, fake.calls.size)
        assertTrue(vm.state.value.turns.isEmpty())
        assertNull(vm.state.value.error)
        assertEquals("Cancelled", vm.state.value.status)
        assertFalse(vm.state.value.running)
    }

    @Test fun runCanRestartAfterCancellation() = runTest(main.dispatcher) {
        fake.respond = { awaitCancellation() }
        vm.startAgents(endpoint, agents, "First")
        runCurrent()
        vm.stop()
        runCurrent()
        fake.respond = { ModelReply(ssp("Fresh result"), it.model) }
        vm.startAgents(endpoint, agents.take(1), "Second")
        advanceUntilIdle()
        assertEquals(2, fake.calls.size)
        assertEquals("Fresh result", vm.state.value.turns.single().text)
        assertFalse(vm.state.value.running)
    }

    @Test fun rosterIsSnapshottedBeforeCoroutineRuns() = runTest(main.dispatcher) {
        val mutableRoster = agents.toMutableList()
        vm.startAgents(endpoint, mutableRoster, "Topic")
        mutableRoster.clear()
        advanceUntilIdle()
        assertEquals(agents.map { it.model }, fake.calls.map { it.model })
        assertEquals(3, vm.state.value.turns.size)
    }

    @Test fun clearingViewModelStoreCancelsRequestAndClosesBackendOnce() = runTest(main.dispatcher) {
        fake.respond = { awaitCancellation() }
        vm.startAgents(endpoint, agents, "Topic")
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(1, fake.closes)
        assertEquals(1, fake.cancellations)
        assertEquals(1, fake.calls.size)
        assertFalse(vm.state.value.running)
        assertNull(vm.state.value.activeAgent)
        store.clear()
        assertEquals(1, fake.closes)
    }

    @Test fun compatibilityApiUsesRequestedNumberOfTurns() = runTest(main.dispatcher) {
        vm.start(endpoint, "qwen3:4b", "Topic", 2)
        advanceUntilIdle()
        assertEquals(2, fake.calls.size)
        assertEquals(listOf("Agent 1", "Agent 2"), vm.state.value.turns.map { it.agentName })
    }

    @Test fun newRunClearsOldErrorsAndResults() = runTest(main.dispatcher) {
        fake.validationError = IllegalArgumentException("Blocked")
        vm.startAgents(endpoint, agents, "Topic")
        assertNotNull(vm.state.value.error)
        fake.validationError = null
        vm.startAgents(endpoint, agents.take(1), "Valid")
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.turns.isEmpty())
        advanceUntilIdle()
        fake.respond = { ModelReply(ssp("New run"), it.model) }
        vm.startAgents(endpoint, agents.take(1), "Again")
        assertTrue(vm.state.value.turns.isEmpty())
        advanceUntilIdle()
        assertEquals("New run", vm.state.value.turns.single().text)
    }

    @Test fun activeSpeakerAndRunningStateAreObservableBeforeCompletion() = runTest(main.dispatcher) {
        val reply = CompletableDeferred<ModelReply>()
        fake.respond = { reply.await() }
        vm.startAgents(endpoint, agents.take(1), "Topic")
        runCurrent()
        assertTrue(vm.state.value.running)
        assertEquals("Architect", vm.state.value.activeAgent)
        reply.complete(ModelReply(ssp("Done"), "actual-model"))
        advanceUntilIdle()
        assertFalse(vm.state.value.running)
        assertNull(vm.state.value.activeAgent)
        assertEquals("actual-model", vm.state.value.turns.single().model)
    }

    @Test fun stopWhileIdleDoesNothing() = runTest(main.dispatcher) {
        vm.stop()
        runCurrent()
        assertEquals(DiscussionState(), vm.state.value)
        assertTrue(fake.calls.isEmpty())
    }
}
