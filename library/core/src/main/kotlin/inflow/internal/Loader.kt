package inflow.internal

import inflow.InflowDeferred
import inflow.Progress
import inflow.ProgressTracker
import inflow.utils.log
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs provided action if it is not running yet while keeping execution and error states.
 */
internal class Loader<T>(
    private val logId: String,
    private val scope: CoroutineScope,
    private val action: suspend (ProgressTracker) -> T
) {
    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress = _progress.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error = _error.asStateFlow()

    private val prevJobRef = atomic<InflowDeferredWithState<T>?>(null)
    private val jobRef = atomic<InflowDeferredWithState<T>?>(null)

    fun load(repeatIfRunning: Boolean): InflowDeferred<T> {
        // Fast path to return already running job without extra objects allocation
        getActiveJobAndCleanUp(repeatIfRunning)?.let { return it }

        // Seems like there is no active job yet, let's try to start a new one
        val job = InflowDeferredWithState<T>()

        while (true) {
            // Trying to start a job ourselves
            val updated = jobRef.compareAndSet(expect = null, update = job)
            if (updated) break // Hooray, we have exclusive rights to start now

            // Another job was started, let's try to return it. Otherwise try to start again.
            getActiveJobAndCleanUp(repeatIfRunning)?.let { return it }
        }

        scope.launch {
            // This point can only be reached if previous job is null or in FINISHING state,
            // otherwise our `jobRef` lock cannot be released (see `getActiveJobAndCleanUp`).
            // We need to wait for previous job (`loadExclusively()`) to completely finish.
            // This extra complexity (along with job state) is introduced only because of
            // `repeatIfRunning` flag which forces us to create a new job even if previous job
            // is still running but cannot be repeated anymore.
            prevJobRef.value?.join()
            prevJobRef.value = job

            // Doing the work now and notify the deferred job
            val (result, error) = loadExclusively(job)
            job.complete(result, error)

            // Trying to clean up, inactive job may be already removed from `getActiveJobAndCleanUp`
            prevJobRef.compareAndSet(expect = job, update = null)
            jobRef.compareAndSet(expect = job, update = null)
        }

        return job
    }

    private fun getActiveJobAndCleanUp(repeatIfRunning: Boolean): InflowDeferred<T>? {
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

    private suspend fun loadExclusively(job: InflowDeferredWithState<T>): Pair<T?, Throwable?> {
        // We can do the actual work here without worrying about race conditions
        var result: T?
        var caughtError: Throwable?

        _progress.value = Progress.Active
        _error.value = null // Clearing any errors before load

        while (true) {
            job.skipRepeat()

            log(logId) { "Refreshing..." }

            result = null
            caughtError = null

            try {
                val tracker = Tracker()
                result = action(tracker)
                tracker.disable() // Deactivating to avoid tracking outside of the loader
                log(logId) { "Refresh successful" }
            } catch (ae: AssertionError) {
                throw ae // Just re-throw to crash
            } catch (th: Throwable) {
                caughtError = th
                log(logId) { "Refresh error: ${th::class.simpleName} - ${th.message}" }
            }

            // Trying to finish the loading, otherwise we have to repeat the loading again
            if (job.setFinishingIfNoRepeat()) break
        }

        _error.value = caughtError
        _progress.value = Progress.Idle

        return Pair(result, caughtError)
    }


    private inner class Tracker : ProgressTracker {
        private var isActive = true

        fun disable() {
            isActive = false
        }

        override fun state(current: Float, total: Float) {
            if (isActive) _progress.tryEmit(Progress.State(current, total))
        }
    }

}
