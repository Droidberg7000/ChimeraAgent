package com.chimera.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoundtableConcurrencyTest {
    @get:Rule val main = MainDispatcherRule()
    private val stores = mutableListOf<ViewModelStore>()
    private val cleanupGates = mutableListOf<CompletableDeferred<Unit>>()
    private val endpoint = "http://127.0.0.1:11434/"
    private val agents = listOf(LocalAgentSpec("Architect", "qwen3:4b"), LocalAgentSpec("Auditor", "qwen3:4b"))

    private class FakeBackend : RoundtableBackend {
        val calls = CopyOnWriteArrayList<String>()
        val validations = CopyOnWriteArrayList<String>()
        var closes = 0
        var cancellations = 0
        var failClose = false
        var validationFailure: Exception? = null
        var answer: suspend (String) -> ModelReply = { ModelReply("raw reply", it) }
        override fun validateEndpoint(value: String) {
            validations.add(value)
            validationFailure?.let { throw it }
        }
        override suspend fun chat(endpoint: String, model: String, topic: String, prior: String): ModelReply {
            calls.add(topic)
            return try { answer(model) } catch (e: CancellationException) {
                cancellations++
                throw e
            }
        }
        override fun close() {
            closes++
            if (failClose) throw IOException("shutdown failed")
        }
    }
    private fun create(backend: FakeBackend, ownership: BackendOwnership = BackendOwnership.OWNED): Pair<RoundtableViewModel, ViewModelStore> {
        val store = ViewModelStore()
        stores.add(store)
        val provider = ViewModelProvider(store, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelClass.cast(RoundtableViewModel(backend, ownership))
        })
        return provider[RoundtableViewModel::class.java] to store
    }
    private fun worker(action: () -> Unit) {
        val failures = ConcurrentLinkedQueue<Throwable>()
        val thread = Thread { try { action() } catch (e: Throwable) { failures.add(e) } }
        thread.isDaemon = true
        thread.start()
        thread.join(5000)
        assertFalse("Worker did not finish", thread.isAlive)
        assertTrue(failures.toString(), failures.isEmpty())
    }
    @After fun tearDown() {
        stores.forEach { it.clear() }
        cleanupGates.forEach { it.complete(Unit) }
        main.dispatcher.scheduler.runCurrent()
    }

    @Test fun storeClearBeforeBodyClosesOwnedBackendWithoutDispatch() = runTest(main.dispatcher) {
        val fake = FakeBackend()
        val (vm, store) = create(fake)
        vm.startAgents(endpoint, agents, "Topic")
        store.clear()
        runCurrent()
        assertTrue(fake.calls.isEmpty())
        assertEquals(1, fake.closes)
        assertFalse(vm.state.value.running)
        assertFalse(vm.state.value.stopping)
        assertNull(vm.state.value.activeAgent)
    }

    @Test fun backgroundStopSafelyCancelsCurrentRun() = runTest(main.dispatcher) {
        val fake = FakeBackend().apply { answer = { awaitCancellation() } }
        val (vm, _) = create(fake)
        vm.startAgents(endpoint, agents, "Topic")
        runCurrent()
        worker { vm.stop() }
        runCurrent()
        assertEquals(1, fake.calls.size)
        assertEquals(1, fake.cancellations)
        assertFalse(vm.state.value.running)
        assertEquals("Cancelled", vm.state.value.status)
    }

    @Test fun simultaneousStartsReserveOnlyOneJob() = runTest(main.dispatcher) {
        val fake = FakeBackend().apply { answer = { awaitCancellation() } }
        val (vm, _) = create(fake)
        val barrier = CyclicBarrier(3)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val threads = (1..2).map { index -> Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                vm.startAgents(endpoint, agents, "Topic $index")
            } catch (e: Throwable) { failures.add(e) }
        }.apply { isDaemon = true; start() } }
        barrier.await(5, TimeUnit.SECONDS)
        threads.forEach { it.join(5000); assertFalse(it.isAlive) }
        assertTrue(failures.toString(), failures.isEmpty())
        runCurrent()
        assertEquals(1, fake.calls.size)
        assertTrue(vm.state.value.running)
        vm.stop()
        runCurrent()
    }

    @Test fun clearedViewModelRejectsSubsequentControlCalls() = runTest(main.dispatcher) {
        val fake = FakeBackend()
        val (vm, store) = create(fake)
        store.clear()
        val before = vm.state.value
        worker {
            vm.startAgents(endpoint, agents, "Late topic")
            vm.start(endpoint, "qwen3:4b", "Late", 0)
            vm.stop()
        }
        runCurrent()
        assertEquals(before, vm.state.value)
        assertTrue(fake.validations.isEmpty())
        assertTrue(fake.calls.isEmpty())
        assertEquals(1, fake.closes)
    }

    @Test fun borrowedBackendIsNotClosedWhenViewModelClears() = runTest(main.dispatcher) {
        val fake = FakeBackend().apply { answer = { awaitCancellation() } }
        val (vm, store) = create(fake, BackendOwnership.BORROWED)
        vm.startAgents(endpoint, agents, "Topic")
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(1, fake.cancellations)
        assertEquals(0, fake.closes)
        assertFalse(vm.state.value.running)
    }

    @Test fun clearingOneBorrowerDoesNotCloseAnotherBorrowersBackend() = runTest(main.dispatcher) {
        val fake = FakeBackend().apply { answer = { awaitCancellation() } }
        val (first, firstStore) = create(fake, BackendOwnership.BORROWED)
        val (second, secondStore) = create(fake, BackendOwnership.BORROWED)
        first.startAgents(endpoint, agents.take(1), "First")
        second.startAgents(endpoint, agents.take(1), "Second")
        runCurrent()
        firstStore.clear()
        runCurrent()
        assertEquals(0, fake.closes)
        assertEquals(1, fake.cancellations)
        assertTrue(second.state.value.running)
        secondStore.clear()
        runCurrent()
        assertEquals(2, fake.cancellations)
        assertEquals(0, fake.closes)
        fake.close() // External owner releases only after both borrowers have completed.
        assertEquals(1, fake.closes)
    }

    @Test fun ownedCloseWaitsForCoroutineCleanup() = runTest(main.dispatcher) {
        val cleanup = CompletableDeferred<Unit>().also { cleanupGates.add(it) }
        val fake = FakeBackend().apply { answer = {
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { cleanup.await() } }
        } }
        val (vm, store) = create(fake)
        vm.startAgents(endpoint, agents, "Topic")
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(0, fake.closes)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(1, fake.closes)
        store.clear()
        assertEquals(1, fake.closes)
    }

    @Test fun validationCancellationPropagatesWithoutChangingIdleState() = runTest(main.dispatcher) {
        val fake = FakeBackend().apply { validationFailure = CancellationException("cancelled preflight") }
        val (vm, _) = create(fake)
        var cancelled = false
        try { vm.startAgents(endpoint, agents, "Topic") }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(DiscussionState(), vm.state.value)
        assertTrue(fake.calls.isEmpty())
    }

    @Test fun cleanupFailureIsRecordedWithoutEscapingCompletionHandler() = runTest(main.dispatcher) {
        val fake = FakeBackend().apply { answer = { awaitCancellation() }; failClose = true }
        val (vm, store) = create(fake)
        vm.startAgents(endpoint, agents, "Topic")
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(1, fake.closes)
        assertEquals("Backend cleanup failed: IOException", vm.state.value.error)
        assertFalse(vm.state.value.running)
    }

    @Test fun alreadyCancelledScopeCanCompleteInlineOnWorkerThread() = runTest(main.dispatcher) {
        val fake = FakeBackend()
        val (vm, _) = create(fake)
        vm.viewModelScope.cancel()
        worker { vm.startAgents(endpoint, agents, "Topic") }
        runCurrent()
        assertTrue(fake.calls.isEmpty())
        assertFalse(vm.state.value.running)
        assertFalse(vm.state.value.stopping)
        assertEquals("Cancelled", vm.state.value.status)
    }

    @Test fun lateReplyAfterStopIsNotPublished() = runTest(main.dispatcher) {
        val release = CompletableDeferred<Unit>().also { cleanupGates.add(it) }
        val fake = FakeBackend().apply { answer = { model ->
            withContext(NonCancellable) { release.await(); ModelReply("late reply", model) }
        } }
        val (vm, _) = create(fake)
        vm.startAgents(endpoint, agents, "Topic")
        runCurrent()
        vm.stop()
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.state.value.turns.isEmpty())
        assertEquals(1, fake.calls.size)
        assertEquals("Cancelled", vm.state.value.status)
    }

    @Test fun defaultConstructorStillExistsForAndroidFactory() {
        // Reflection inspection does not instantiate an OkHttp client or make a request.
        assertNotNull(RoundtableViewModel::class.java.getConstructor())
    }
}
