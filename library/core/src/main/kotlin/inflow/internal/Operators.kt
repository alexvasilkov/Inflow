package inflow.internal

import inflow.InflowConnectivity
import inflow.utils.log
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart


/**
 * Runs [action] on first subscription and cancels resulting job once no subscribers left.
 */
internal fun <T> Flow<T>.doWhileSubscribed(action: () -> Job): Flow<T> {
    val lock = reentrantLock() // We don't call any suspending functions under lock
    var count = 0
    var job: Job? = null
    return this
        .onStart {
            lock.withLock {
                if (count++ == 0) {
                    job?.cancel()
                    job = action()
                }
            }
        }
        .onCompletion {
            lock.withLock {
                if (--count == 0) {
                    job!!.cancel()
                    job = null
                }
            }
        }
}


/**
 * Uses [cacheExpiration] flow to call [loader] action each time the data should be updated.
 *
 * **Important**: By contract cache (and eventually [cacheExpiration]) flow should be triggered
 * each time the data is successfully loaded and saved into cache, otherwise we will retry loading
 * after retry timeout or each time activation flow will emit `true` (meaning we have active
 * subscribers and/or active internet connection).
 *
 * Also when a new data is successfully loaded and saved the [cacheExpiration] flow should return
 * a new expiration time greater than 0, otherwise we will enter infinite loading cycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun scheduleUpdates(
    logId: String,
    cacheExpiration: Flow<Long>,
    retryTime: Long,
    loader: suspend () -> Unit
) {
    require(retryTime > 0L) { "Retry time ($retryTime) should be > 0" }

    cacheExpiration
        .flatMapLatest { expiration ->
            // Scheduling new periodic update.
            // It should run once the data is expired and retry with provided `retryTime` until
            // the data is finally loaded and cached, which in turn should trigger a new data
            // to be sent to us and this flow will be cancelled and re-scheduled.
            flow {
                if (expiration == Long.MAX_VALUE) {
                    log(logId) { "Cache never expires" }
                    awaitCancellation() // Waiting for cancellation, it won't consume resources
                }

                if (expiration > 0L) {
                    log(logId) { "Cache is expiring in ${expiration}ms" }
                    delay(expiration)
                }

                log(logId) { "Cache is expired" }
                emit(Unit)

                while (true) { // Retrying until new data is loaded and this flow is cancelled
                    emit(null) // Suspend until prev emission is collected ('cause of 0 buffer size)
                    // TODO: Should we check if downstream was successful before repeating again? + update config docs
                    delay(retryTime)
                    log(logId) { "Cache refresh retry after ${retryTime}ms timeout" }
                    emit(Unit)
                }
            }
        }
        // By using 0 buffer with SUSPEND strategy we ensure that each additional `emit(null)` call
        // above will wait for downstream collector (loader) to finish.
        // This way we can ensure that retry period is starting _after_ the loader finished, not in
        // parallel.
        // E.g. if retry time is set to 2 seconds and network request took 3 seconds then without
        // this hack new retry will be called before the flatMapLatest block above is cancelled.
        // It will lead to unnecessary extra loading call and potentially endless retry cycle.
        .buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
        .filterNotNull() // Nulls are only emitted to suspend the upstream
        .collect { loader() }
}

/**
 * Emitting on each "network connected" callback to immediately retry failed requests.
 * We'll force first load even if no connection to ensure an error is propagated.
 */
internal fun InflowConnectivity?.asSignalingFlow(): Flow<Unit> =
    if (this == null) MutableStateFlow(Unit) else
        connected
            .onStart { emit(true) } // Always signaling in the beginning
            .distinctUntilChanged()
            .filter { it }
            .map {}
