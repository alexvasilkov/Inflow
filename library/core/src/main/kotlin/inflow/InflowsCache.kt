package inflow

import inflow.internal.InflowsCacheImpl

/**
 * Cache provider used to keep entries in memory to avoid frequent initializations.
 *
 * Implementations are required to be thread-safe because [get] and [clear] methods can be
 * potentially called from different threads.
 */
public interface InflowsCache<K, V> {
    /**
     * Gets a value for the [key] or creates a new one using [provider] if no cached value is found.
     * It will also trigger a cache clean up to remove old or expired items.
     */
    public fun get(key: K, provider: (K) -> V): V

    /**
     * Sets optional removal listener to perform final clean up.
     */
    public fun doOnRemove(action: (V) -> Unit)

    /**
     * Removes all cached entries.
     */
    public fun clear()


    public companion object {
        /**
         * Creates an instance of [InflowsCache] which keeps up to [maxSize] entries (defaults to
         * `10`) and optionally removes all items that were accessed more than [expireAfterAccess]
         * milliseconds ago (defaults to `0` meaning that no time-based expiration will be used).
         */
        public fun <P, T> create(
            maxSize: Int = 10,
            expireAfterAccess: Long = 0L
        ): InflowsCache<P, Inflow<T>> = InflowsCacheImpl(maxSize, expireAfterAccess)
    }

}
