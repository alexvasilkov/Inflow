package inflow.utils

import inflow.InflowConnectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
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
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext


/**
 * Shares this flow among several subscribers. This flow is only subscribed if there is at least
 * one downstream subscriber and unsubscribed after [keepSubscribedTimeout] since last subscriber
 * is gone.
 *
 * We could use Kotlin's `.shareIn()` operator but it spins a never ending collecting job
 * while we want to have no hanging jobs once there are no cache subscribers left.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<T>.share(
    scope: CoroutineScope,
    keepSubscribedTimeout: Long
): Flow<T> {
    val shared = MutableSharedFlow<T>(replay = 1)

    val subscriptionsCount = AtomicInt()
    val collectJobRef = AtomicRef<Job>()
    val completeJobRef = AtomicRef<Job>()

    return shared
        .onSubscription {
            // We are only interested in the first subscription
//            log("TEST")  { "Starting0..." }
            if (subscriptionsCount.getAndIncrement() != 0) return@onSubscription
//            log("TEST")  { "Starting..." }

            // Cancelling completion job, if running
            val completeJob = completeJobRef.getAndSet(null)
            completeJob?.cancel()
            completeJob?.join()

            // Collector job may still be active if it is not unsubscribed by timeout yet.
            // We'll only start new collect job if there is no other active job.
            val collectJob = Job()
            if (collectJobRef.compareAndSet(expect = null, update = collectJob)) {
//                log("TEST")  { "Starting: " + System.identityHashCode(collectJob) }
                scope.launch(collectJob) {
                    collect(shared::emit)
                    awaitCancellation() // Waiting forever in case if flow is finite
                }
            }
        }
        .onCompletion {
            // We are only interested in the last un-subscription
//            log("TEST")  { "Stopping0..." }
            if (subscriptionsCount.decrementAndGet() != 0) return@onCompletion
//            log("TEST")  { "Stopping..." }

            // Starting completion job, if not running yet
            val completeJob = Job()
            if (completeJobRef.compareAndSet(expect = null, update = completeJob)) {
                // Scheduling collector job cancellation
                scope.launch(completeJob) {
                    delay(keepSubscribedTimeout)

                    // Cancelling latest collector job
                    val collectJob = collectJobRef.getAndSet(null)
                    if (collectJob != null) {
//                        log("TEST")  { "Stopping: " + System.identityHashCode(collectJob) }
                        collectJob.cancel()
                        shared.resetReplayCache() // We cannot keep reply cache anymore
                    }
                }
            }
        }
}


/**
 * Runs [action] on first subscription and cancels resulting job once no subscribers left.
 */
internal fun <T> Flow<T>.doWhileSubscribed(action: () -> Job): Flow<T> {
    val count = AtomicInt()
    val job = AtomicRef<Job>()
    return this
        .onStart { if (count.getAndIncrement() == 0) job.getAndSet(action())?.cancel() }
        .onCompletion { if (count.decrementAndGet() == 0) job.getAndSet(null)!!.cancel() }
}


/**
 * Runs provided action if it is not running yet and keeps execution and error states.
 */
internal suspend fun runWithState(
    logId: String,
    loading: MutableStateFlow<Boolean>,
    error: MutableStateFlow<Throwable?>,
    retryForced: MutableStateFlow<Boolean>,
    force: Boolean,
    action: suspend () -> Unit
): Boolean {
    // Ensuring we are only running single action at a time
    val canRun = loading.compareAndSet(expect = false, update = true)
    if (!canRun) {
        if (force) {
            val wasForced = retryForced.compareAndSet(expect = false, update = true)
            if (wasForced) {
                log(logId) { "Refresh is already in progress, scheduling extra refresh" }
            } else {
                log(logId) { "Refresh is already in progress, extra refresh is already scheduled" }
            }
        } else {
            log(logId) { "Refresh is already in progress, skipping refresh" }
        }

        return false
    }

    log(logId) { "Refreshing..." }

    error.emit(null) // Clearing old error before start
    var caughtError: Throwable? = null

    while (coroutineContext.isActive) { // Allowing suspend function cancellation
        retryForced.emit(false) // Clearing forced retry

        withContext(NonCancellable) {
            try {
                action()
                log(logId) { "Refresh successful" }
            } catch (ae: AssertionError) {
                throw ae // Just throw as is
            } catch (th: Throwable) { // Catching inside withContext to avoid exception cloning
                log(logId) { "Refresh error: ${th::class.java.simpleName} - ${th.message}" }
                caughtError = th // We'll only emit latest error if extra refresh is scheduled
            }
        }

        if (!retryForced.value) break
    }

    if (caughtError != null) error.emit(caughtError)

    loading.emit(false)
    return true
}


/**
 * Uses [cacheExpiration] flow along with [activation] state to call [loader] action each time
 * the data should be updated.
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
    activation: Flow<Unit>,
    retryTime: Long,
    loader: suspend (Boolean) -> Boolean
) {
    require(retryTime > 0L) { "Retry time ($retryTime) should be > 0" }

    activation
        .flatMapLatest { cacheExpiration }
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
        .collect { loader(false) }
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
