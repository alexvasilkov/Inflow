package inflow.internal

import inflow.DataLoader
import inflow.LoadTracker
import inflow.State
import inflow.utils.doOnCancel
import inflow.utils.log
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs provided action if it is not running yet and tracks its state.
 */
internal class Loader(
    private val logId: String,
    private val logType: String,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val action: DataLoader<Unit>
) {
    private val _state = MutableStateFlow<State>(State.Idle.Initial)
    val state = _state.asStateFlow()

    private val jobRef = atomic<CompletableDeferred<Unit>?>(null)
    private val mutex = Mutex()

    private val errorId = atomic(0)
    private val errorHandler = ErrorHandler()

    fun load(): CompletableDeferred<Unit> {
        var deferredNullable: CompletableDeferred<Unit>? = null

        while (true) {
            // If another job is started then let's try to return it. Otherwise try to start again.
            val current = jobRef.value
            if (current != null && !current.isCompleted) {
                log(logId) { "$logType is already in progress, skipping" }
                return current
            }

            // Trying to start a new job ourselves
            if (deferredNullable == null) deferredNullable = CompletableDeferred()
            val set = jobRef.compareAndSet(expect = current, update = deferredNullable)
            if (set) break // Hooray, we can start now
        }

        val deferred = deferredNullable!!

        val job = scope.launch(dispatcher) {
            // The deferred can be completed before loadExclusively is finished, need to use mutex
            mutex.withLock {
                loadExclusively(deferred) // Doing the work and notify the deferred job
                jobRef.compareAndSet(expect = deferred, update = null)
            }
        }
        // If scope is cancelled then we need to notify our deferred object
        job.doOnCancel(deferred::completeExceptionally)

        return deferred
    }

    private suspend fun loadExclusively(deferred: CompletableDeferred<Unit>) {
        // We can do the actual work here without worrying about race conditions
        var caughtError: Throwable?

        _state.value = State.Loading.Started

        log(logId) { "$logType loading..." }

        caughtError = null

        try {
            val tracker = Tracker(_state)
            action.load(tracker)
            tracker.disable() // Deactivating to avoid tracking outside of the loader
            log(logId) { "$logType success" }
        } catch (th: Throwable) {
            // Including CancellationException as it can only happen if the scope is cancelled
            // in which case we don't really care of the future progress or error states
            caughtError = th
            log(logId) { "$logType error: ${th::class.simpleName} - ${th.message}" }
        }

        // Completing before setting idle state to ensure that new loading can be started
        // immediately once idle state is observed (needed for RefreshForced behavior)
        when (caughtError) {
            null -> deferred.complete(Unit)
            else -> deferred.completeExceptionally(caughtError)
        }

        _state.value = when (caughtError) {
            null -> State.Idle.Success
            else -> State.Idle.Error(caughtError, errorId.incrementAndGet(), errorHandler::handle)
        }
    }

    private class Tracker(state: MutableStateFlow<State>) : LoadTracker {
        private val state = atomic<MutableStateFlow<State>?>(state)

        fun disable() {
            state.value = null
        }

        override fun progress(current: Double, total: Double) {
            state.value?.tryEmit(State.Loading.Progress(current, total))
        }
    }

    private class ErrorHandler {
        private val handledId = atomic(-1)

        fun handle(error: State.Idle.Error): Boolean {
            val id = handledId.value
            return error.id > id && handledId.compareAndSet(expect = id, update = error.id)
        }
    }

}
