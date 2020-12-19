package inflow

import kotlinx.coroutines.Deferred

/**
 * Subset of [Deferred] methods to avoid external cancellation.
 *
 * **Not stable for inheritance.**
 */
interface InflowDeferred<T> {

    /**
     * @see Deferred.await
     */
    suspend fun await(): T

    /**
     * @see Deferred.join
     */
    suspend fun join()

}
