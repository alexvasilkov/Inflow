package inflow.internal

import inflow.InflowDeferred
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred

private const val ACTIVE = 0
private const val REPEAT = 1
private const val FINISHING = 2

internal class InflowDeferredImpl<T> : InflowDeferred<T> {

    private val deferred = CompletableDeferred<T>()
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


    /**
     * Completes normally unless exception.
     */
    fun complete(value: T?, exception: Throwable?) {
        if (exception == null) {
            @Suppress("UNCHECKED_CAST") // Result must not be null here if T is not nullable
            deferred.complete(value as T)
        } else {
            deferred.completeExceptionally(exception)
        }
    }


    override suspend fun await() = deferred.await()

    override suspend fun join() = deferred.join()

}
