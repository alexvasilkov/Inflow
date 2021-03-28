package inflow.internal

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared flow that is initialized on first subscription.
 */
internal class LazySharedFlow<T>(initializer: suspend () -> T) {

    private val internal = MutableSharedFlow<T>(replay = 1, onBufferOverflow = DROP_OLDEST)
    private val initialized = atomic(false)
    private val mutex = Mutex()

    @JvmField
    val flow = internal.onSubscription {
        if (!initialized.value) {
            mutex.withLock {
                if (!initialized.value) {
                    initialized.value = true
                    internal.tryEmit(initializer())
                }
            }
        }
    }

    suspend fun emit(data: T) {
        mutex.withLock {
            initialized.value = true // No need to initialize the cache anymore
            internal.tryEmit(data)
        }
    }

}
