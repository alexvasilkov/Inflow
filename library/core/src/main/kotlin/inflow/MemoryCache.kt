package inflow

import inflow.internal.MemoryCacheImpl
import inflow.internal.MemoryCacheWrapper
import kotlinx.coroutines.flow.Flow

/**
 * In-memory data cache.
 */
public interface MemoryCache<T> {

    /**
     * Returns cached data changes.
     */
    public fun read(): Flow<T>

    /**
     * Writes new data into the cache.
     */
    public suspend fun write(data: T)

    public companion object {
        /**
         * Memory cache which will start by emitting [initial] value.
         */
        public fun <T> create(initial: T): MemoryCache<T> = MemoryCacheImpl(initial)

        /**
         * Memory cache which will start by emitting initial value provided by [reader] and will
         * write new data elsewhere using [writer]. Basically it acts as a wrapper on top of
         * another (slower) cache.
         */
        public fun <T> create(
            reader: suspend () -> T,
            writer: suspend (T) -> Unit
        ): MemoryCache<T> = MemoryCacheWrapper(reader, writer)
    }

}
