package inflow.internal

import inflow.MemoryCache
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Memory cache which will start by emitting initial value.
 */
internal class MemoryCacheImpl<T>(initial: T) : MemoryCache<T> {
    private val cache = MutableSharedFlow<T>(replay = 1, onBufferOverflow = DROP_OLDEST)
        .apply { tryEmit(initial) }

    override fun read(): Flow<T> = cache

    override suspend fun write(data: T) {
        cache.tryEmit(data)
    }
}

/**
 * Memory cache which will start by emitting initial value provided by `reader` and will both store
 * the new data in the memory cache and write it using `writer`.
 */
internal class MemoryCacheWrapper<T>(
    reader: suspend () -> T,
    private val writer: suspend (T) -> Unit
) : MemoryCache<T> {
    private val lazyFlow = LazySharedFlow(reader)

    override fun read() = lazyFlow.flow

    override suspend fun write(data: T) {
        lazyFlow.emit(data)
        writer(data)
    }
}
