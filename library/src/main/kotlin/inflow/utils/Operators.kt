package inflow.utils

import inflow.InflowConnectivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext


internal fun <T> SharedFlow<T>.trackSubscriptions(
    scope: CoroutineScope,
    state: MutableStateFlow<Boolean>
): SharedFlow<T> {
    // SharedFlow does not provide access to MutableSharedFlow.subscriptionCount directly.
    // But we can still access it through SharingStarted, which is even better as we can do our
    // onStart / onStop logic at the same time as upstream flow is started or stopped to be
    // collected according to SharingStarted.WhileSubscribed logic.
    val startedOrig = SharingStarted.WhileSubscribed() // Default params works just fine
    val started = object : SharingStarted {
        override fun command(subscriptionCount: StateFlow<Int>): Flow<SharingCommand> {
            return startedOrig.command(subscriptionCount)
                .onEach {
                    when (it) {
                        SharingCommand.START -> state.emit(true)
                        SharingCommand.STOP -> state.emit(false)
                        SharingCommand.STOP_AND_RESET_REPLAY_CACHE ->
                            throw IllegalStateException("Unexpected replay cache reset")
                    }
                }
        }
    }

    // We won't replay any values, we'll rely on original shared flow to do so instead
    return shareIn(scope, started, replay = 0)
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

        try {
            action()
            log(logId) { "Refresh successful" }
        } catch (ae: AssertionError) {
            throw ae // Just throw as is
        } catch (ce: CancellationException) {
            log(logId) { "Refresh is cancelled: ${ce.message}" }
            throw ce // Rethrowing cancellation
        } catch (th: Throwable) {
            log(logId) { "Refresh error: ${th::class.java.simpleName} - ${th.message}" }
            caughtError = th // We'll only emit latest error if extra refresh is scheduled
        }

        if (!retryForced.value) break
    }

    // Handling coroutine cancellation before emitting results
    if (!coroutineContext.isActive) {
        log(logId) { "Refresh was cancelled" }
        throw CancellationException("Loader scope is not active")
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
    activation: Flow<Boolean>,
    retryTime: Long,
    loader: suspend (Boolean) -> Boolean
) {
    require(retryTime > 0L) { "Retry time ($retryTime) should be > 0" }

    activation
        // Small optimization: filtering out consequent `false` events as they will do nothing.
        .distinctUntilChanged { old, new -> !old && !new }
        .flatMapLatest { active ->
            // Wrapping data as Result to distinguish active and inactive states in downstream
            if (active) cacheExpiration.map { Result.success(it) } else flowOf(null)
        }
        .flatMapLatest { result ->
            if (result == null) return@flatMapLatest emptyFlow() // No loadings in inactive state

            val expiration = result.getOrThrow()

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
        // that hack new retry will be called before the flatMapLatest block above is cancelled.
        // It will lead to unnecessary extra loading call and potentially endless retry cycle.
        .buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
        .filterNotNull() // Nulls are only emitted to suspend the upstream
        .collect { loader(false) }
}

/**
 * Repeating the data on each "network connected" callback to immediately retry failed requests.
 * We'll force first load even if no connection to ensure an error is propagated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<T>.repeatWhenConnected(
    connectivity: InflowConnectivity?
): Flow<T> = if (connectivity == null) this else flatMapLatest { data ->
    connectivity
        .connected
        .onStart { emit(true) }
        .distinctUntilChanged()
        .filter { it }
        .map { data }
}
