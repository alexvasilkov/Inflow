package inflow

import inflow.utils.now
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock


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
}


/**
 * Creates an instance of [InflowsCache] which keeps up to [maxSize] entries (defaults to `10`) and
 * optionally removes all items that were accessed more than [expireAfterAccess] milliseconds ago
 * (defaults to `0` meaning that no time-based expiration will be used).
 */
public fun <K, V> inflowsCache(
    maxSize: Int = 10,
    expireAfterAccess: Long = 0L
): InflowsCache<K, V> = InflowsCacheImpl(maxSize, expireAfterAccess)


internal class InflowsCacheImpl<K, V>(
    private val maxSize: Int,
    private val expireAfterAccess: Long
) : InflowsCache<K, V> {

    private val values = mutableMapOf<K, V>()
    private val access = linkedMapOf<K, Long>()

    private var onRemove: ((V) -> Unit)? = null

    private val lock = reentrantLock()

    init {
        require(maxSize >= 1) { "Max cache size should be >= 1" }
        require(expireAfterAccess >= 0) { "Expire after access timeout should be >= 0" }
    }

    override fun get(key: K, provider: (K) -> V): V = lock.withLock {
        access.remove(key) // Removing the key to later add it back into the end of the list
        val currentValue = values.remove(key)
        val value = currentValue ?: provider(key)

        makeRoom()
        removeExpired()

        access[key] = if (expireAfterAccess == 0L) 0L else now()
        values[key] = value

        return value
    }

    override fun doOnRemove(action: (V) -> Unit) {
        onRemove = action
    }

    private fun makeRoom() {
        // Removing oldest items until we have a room for at least 1 item
        val iterator = access.iterator()
        while (access.size >= maxSize) {
            val key = iterator.next().key
            iterator.remove()
            @Suppress("UNCHECKED_CAST") // The value must exist
            val old = values.remove(key) as V
            onRemove?.invoke(old)
        }
    }

    private fun removeExpired() {
        if (expireAfterAccess == 0L) return // No expiration by timeout

        // Removing expired items
        val iterator = access.iterator()
        val now = now()
        while (iterator.hasNext()) {
            val (key, accessedAt) = iterator.next()
            if (now - accessedAt > expireAfterAccess) {
                iterator.remove()
                @Suppress("UNCHECKED_CAST") // The value must exist
                val old = values.remove(key) as V
                onRemove?.invoke(old)
            } else {
                break // `access` map is ordered by access time
            }
        }
    }

    override fun clear(): Unit = lock.withLock {
        for (old in values.values) onRemove?.invoke(old)
        access.clear()
        values.clear()
    }

}
