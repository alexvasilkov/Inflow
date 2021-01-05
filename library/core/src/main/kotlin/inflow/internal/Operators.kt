package inflow.internal

import inflow.ExpirationProvider
import inflow.InflowConnectivity
import inflow.utils.log
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shares a flow among several subscribers. The flow is only subscribed if there is at least one
 * downstream subscriber and unsubscribed after [keepSubscribedTimeout] since last subscriber is
 * gone.
 *
 * We could use Kotlin's `.shareIn()` operator but it spins a never ending collecting job
 * while we want to have no hanging jobs once there are no cache subscribers left.
 * Also we want the resulting flow to throw a cancellation exception once the [scope] is cancelled,
 * which is not supported by `.shareIn()` as well (it will just hang forever).
 */
@ExperimentalCoroutinesApi
internal fun <T> Flow<T>.share(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    keepSubscribedTimeout: Long
): Flow<T> {
    val orig = this
    val shared = MutableSharedFlow<Any?>( // T || CancellationException
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val lock = reentrantLock()
    var subscriptionsCount = 0
    var collectJob: Job? = null
    var completeJob: Job? = null
    var generation = 0

    return shared
        .onSubscription {
            lock.withLock {
                // We are only interested in the first subscription
                if (subscriptionsCount++ != 0) return@onSubscription

                // Cancelling completion job, if running
                completeJob?.cancel()
                completeJob = null
                generation++

                // Collector job may still be active if it is not unsubscribed by timeout yet.
                // We'll only start new collect job if there is no other active job.
                if (collectJob == null) {
                    collectJob = scope.launch(dispatcher) {
                        orig.collect(shared::emit)
                    }
                    // Once the scope is cancelled collectors should receive cancellation exception.
                    // Note: `tryEmit` is always successful because of BufferOverflow.DROP_OLDEST
                    collectJob!!.invokeOnCompletion {
                        if (!scope.isActive) {
                            val exception = it as? CancellationException
                                ?: CancellationException("Error during cache read")
                            shared.tryEmit(exception)
                        }
                    }
                }
            }
        }
        .onCompletion {
            lock.withLock {
                // We are only interested in the last un-subscription
                if (--subscriptionsCount != 0) return@onCompletion

                // Scheduling collector job cancellation
                // Using UNDISPATCHED start to immediately cancel the job if timeout is 0
                val currentGeneration = generation
                completeJob = scope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                    delay(keepSubscribedTimeout)

                    // Cancelling latest collector job under lock
                    lock.withLock {
                        if (currentGeneration != generation) return@launch
                        collectJob?.cancel()
                        collectJob = null
                        shared.resetReplayCache() // We cannot keep reply cache anymore
                    }
                }
            }
        }
        .map { data -> // T || CancellationException
            if (data is CancellationException) throw data
            @Suppress("UNCHECKED_CAST")
            data as T
        }
}

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
 * Checks if the data emitted from [this] flow is invalid and returns [emptyValue] instead.
 * Also schedules extra emission of [emptyValue] once the data becomes invalid according to
 * expiration time provided by [invalidIn].
 */
@ExperimentalCoroutinesApi
internal fun <T> Flow<T>.emptyIfInvalid(
    logId: String,
    invalidIn: ExpirationProvider<T>,
    emptyValue: T
): Flow<T> = flatMapLatest { data ->
    flow {
        // Getting invalidation time
        var expiration = invalidIn.expiresIn(data)

        // Returning the data, if valid
        if (expiration > 0L) emit(data)

        // Waiting for expiration timeout and re-checking it again in case it's dynamic,
        // meaning that `expiresIn` may just define the next time to check the expiration
        // while expiration logic itself may not be time-based.
        while (true) {
            if (expiration <= 0L) break

            if (expiration < Long.MAX_VALUE) {
                log(logId) { "Cache will be invalid in ${expiration}ms" }
            }
            // Waiting for the data to become invalid
            delay(expiration)
            // Checking expiration again, in most cases it should be <= 0L by now
            expiration = invalidIn.expiresIn(data)
        }

        log(logId) { "Cache is invalid, returning empty value" }
        emit(emptyValue)
    }
}

/**
 * Uses [cache] flow to call [loader] action each time the data should be updated according to
 * [expiration] policy.
 *
 * **Important**: By contract [cache] flow should be triggered each time the data is successfully
 * loaded and saved into cache, otherwise we will retry loading after the retry timeout.
 *
 * Also when a new data is successfully loaded and saved the [cache] flow should return a new data
 * with expiration timeout greater than 0, otherwise we will enter infinite loading cycle.
 */
@ExperimentalCoroutinesApi
internal suspend fun <T> scheduleUpdates(
    logId: String,
    cache: Flow<T>,
    expiration: ExpirationProvider<T>,
    retryTime: Long,
    loader: suspend () -> Unit
) {
    require(retryTime > 0L) { "Retry time ($retryTime) should be > 0" }

    cache
        .flatMapLatest { data ->
            // Scheduling new periodic update.
            // It should run once the data is expired and retry with provided `retryTime` until
            // the data is finally loaded and cached, which in turn should trigger a new data
            // to be sent to us and this flow will be cancelled and re-scheduled.
            flow {
                // Waiting for expiration timeout and re-checking it again in case it's dynamic,
                // meaning that `expiresIn` may just define the next time to check the expiration
                // while expiration logic itself may not be time-based.
                while (true) {
                    val expiresIn = expiration.expiresIn(data)
                    if (expiresIn <= 0L) break

                    if (expiresIn < Long.MAX_VALUE) {
                        log(logId) { "Cache is expiring in ${expiresIn}ms" }
                    }
                    delay(expiresIn)
                }

                log(logId) { "Cache is expired" }
                emit(Unit)

                while (true) { // Retrying until new data is loaded and this flow is cancelled
                    // Suspend until prev emission is collected (loader is finished).
                    // Works because of 0 buffer size and suspending strategy (see below).
                    emit(null)
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
