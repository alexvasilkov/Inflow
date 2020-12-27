package inflow

import kotlinx.coroutines.Deferred

/**
 * Subset of [Deferred] methods to avoid external cancellation.
 */
interface InflowDeferred<T> {

    /**
     * Awaits for completion of the loading and resumes when the loading is complete, returning the
     * data or throwing an exception.
     *
     * Note that explicit call to the underlying cache will be made (bypassing the shared cache)
     * right after the loading completes to ensure all data is only coming from the cache as a
     * single source of truth.
     *
     * @see Deferred.await
     */
    suspend fun await(): T

    /**
     * Suspends until the loading is complete.
     * This invocation resumes normally regardless of the loading result (error or success).
     *
     * @see Deferred.join
     */
    suspend fun join()

}
