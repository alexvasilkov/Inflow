package inflow.internal

import inflow.MemoryCache
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription

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
 * Memory cache which will start by emitting initial value provided by `reader` and will
 * write new data using `writer`.
 */
internal class MemoryCacheWrapper<T>(
    private val reader: suspend () -> T,
    private val writer: suspend (T) -> Unit
) : MemoryCache<T> {
    private val cache = MutableSharedFlow<T>(replay = 1, onBufferOverflow = DROP_OLDEST)
    private val initialized = atomic(false)

    override fun read() = cache.onSubscription {
        val set = initialized.compareAndSet(expect = false, update = true)
        if (set) cache.tryEmit(reader())
    }

    override suspend fun write(data: T) {
        initialized.value = true // No need to initialize the cache anymore
        cache.tryEmit(data)
        writer(data)
    }
}
