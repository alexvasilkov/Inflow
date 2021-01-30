package inflow.internal

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

/**
 * Runs provided action if it is not running yet while keeping execution and error states.
 */
internal class Loader(
    private val logId: String,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val action: suspend (LoadTracker) -> Unit
) {
    private val _state = MutableStateFlow<State>(State.Idle.Initial)
    val state = _state.asStateFlow()

    private val prevJobRef = atomic<DeferredWithState?>(null)
    private val jobRef = atomic<DeferredWithState?>(null)

    private val errorId = atomic(0)
    private val errorIdHandled = atomic(-1)

    fun load(repeatIfRunning: Boolean): CompletableDeferred<Unit> {
        // Fast path to return already running job without extra objects allocation
        getActiveJobAndCleanUp(repeatIfRunning)?.let { return it.deferred }

        // Seems like there is no active job yet, let's try to start a new one
        val job = DeferredWithState()

        while (true) {
            // Trying to start a job ourselves
            val updated = jobRef.compareAndSet(expect = null, update = job)
            if (updated) break // Hooray, we have exclusive rights to start now

            // Another job was started, let's try to return it. Otherwise try to start again.
            getActiveJobAndCleanUp(repeatIfRunning)?.let { return it.deferred }
        }

        val task = scope.launch(dispatcher) {
            // This point can only be reached if previous job is null or in FINISHING state,
            // otherwise our `jobRef` lock cannot be released (see `getActiveJobAndCleanUp`).
            // We need to wait for previous job (`loadExclusively()`) to completely finish.
            // This extra complexity (along with job state) is introduced only because of
            // `repeatIfRunning` flag which forces us to create a new job even if previous job
            // is still running but cannot be repeated anymore.
            prevJobRef.value?.deferred?.join()
            prevJobRef.value = job

            // Doing the work now and notify the deferred job
            val error = loadExclusively(job)
            if (error != null) {
                job.deferred.completeExceptionally(error)
            } else {
                job.deferred.complete(Unit)
            }

            // Trying to clean up, inactive job may be already removed from `getActiveJobAndCleanUp`
            prevJobRef.compareAndSet(expect = job, update = null)
            jobRef.compareAndSet(expect = job, update = null)
        }
        // If scope is cancelled then we need to notify our deferred object
        task.doOnCancel(job.deferred::completeExceptionally)

        return job.deferred
    }

    private fun getActiveJobAndCleanUp(repeatIfRunning: Boolean): DeferredWithState? {
        // Trying to return an active job.
        // It can easily become inactive once we'll return it from here, but that is fine.
        while (true) {
            val currentJob = jobRef.value ?: return null

            if (repeatIfRunning) {
                if (currentJob.repeatIfNotFinishing()) {
                    log(logId) { "Refresh is already in progress, enforcing extra refresh" }
                    return currentJob
                }
            } else {
                if (currentJob.isNotFinishing()) {
                    log(logId) { "Refresh is already in progress, skipping new refresh" }
                    return currentJob
                }
            }

            // Removing inactive job if it is not changed yet, otherwise do the check again
            val removed = jobRef.compareAndSet(expect = currentJob, update = null)
            if (removed) return null
        }
    }

    private suspend fun loadExclusively(job: DeferredWithState): Throwable? {
        // We can do the actual work here without worrying about race conditions
        var caughtError: Throwable?

        _state.value = State.Loading.Started

        while (true) {
            job.skipRepeat()

            log(logId) { "Refreshing..." }

            caughtError = null

            try {
                val tracker = Tracker()
                action(tracker)
                tracker.disable() // Deactivating to avoid tracking outside of the loader
                log(logId) { "Refresh successful" }
            } catch (th: Throwable) {
                // Including CancellationException as it can only happen if the scope is cancelled
                // in which case we don't really care of the future progress or error states
                caughtError = th
                log(logId) { "Refresh error: ${th::class.simpleName} - ${th.message}" }
            }

            // Trying to finish the loading, otherwise we have to repeat the loading again
            if (job.setFinishingIfNoRepeat()) break
        }

        _state.value = when (caughtError) {
            null -> State.Idle.Success
            else -> State.Idle.Error(caughtError, errorId.incrementAndGet(), ::markHandled)
        }

        return caughtError
    }

    private fun markHandled(error: State.Idle.Error): Boolean {
        while (true) {
            val handledId = errorIdHandled.value
            if (error.id > handledId) {
                val set = errorIdHandled.compareAndSet(expect = handledId, update = error.id)
                if (!set) continue // Race condition, trying again
                return true
            }
            break
        }
        return false
    }


    private inner class Tracker : LoadTracker {
        private var isActive = true

        fun disable() {
            isActive = false
        }

        override fun progress(current: Double, total: Double) {
            if (isActive) _state.tryEmit(State.Loading.Progress(current, total))
        }
    }

}


private const val ACTIVE = 0
private const val REPEAT = 1
private const val FINISHING = 2

internal class DeferredWithState {
    val deferred: CompletableDeferred<Unit> = CompletableDeferred()

    private val _state = atomic(ACTIVE)

    fun isNotFinishing(): Boolean = _state.value < FINISHING

    /**
     * Returns `true` if repeat was enforced or `false` if the job is inactive and cannot be
     * repeated anymore.
     */
    fun repeatIfNotFinishing(): Boolean {
        while (true) {
            // Checking that the job is still active
            val state = _state.value
            if (state == FINISHING) return false

            // Trying to set the job into REPEAT state, if the state was changed then we need to
            // re-validate it on the next loop step.
            val set = _state.compareAndSet(expect = state, update = REPEAT)
            if (set) return true
        }
    }

    /**
     * Un-sets REPEAT state, if set.
     */
    fun skipRepeat() {
        _state.compareAndSet(expect = REPEAT, update = ACTIVE)
    }

    /**
     * Tries to set the job into FINISHING state, returns `false` if the job should be repeated.
     */
    fun setFinishingIfNoRepeat(): Boolean {
        return _state.compareAndSet(expect = ACTIVE, update = FINISHING)
    }

}
