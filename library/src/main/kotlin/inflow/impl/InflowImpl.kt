package inflow.impl

import inflow.Inflow
import inflow.InflowConfig
import inflow.utils.log
import inflow.utils.repeatWhenConnected
import inflow.utils.runWithState
import inflow.utils.scheduleUpdates
import inflow.utils.trackSubscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

internal class InflowImpl<T> : Inflow<T> {

    private val logId: String

    private val cache: Flow<T>
    private val auto: Flow<T>

    private val loading: StateFlow<Boolean>
    private val error: StateFlow<Throwable?>
    private val loadAction: suspend (Boolean) -> Boolean

    private val cacheScope: CoroutineScope
    private val loadScope: CoroutineScope

    internal var closed = false


    constructor(config: InflowConfig<T>) {
        logId = config.logId

        /*
           Config validations.
        */
        val cacheFromConfig = requireNotNull(config.cache) { "`cache` is required" }
        val cacheWriter = requireNotNull(config.cacheWriter) { "`cacheWriter` is required" }

        val cacheTimeout = config.cacheKeepSubscribedTimeout
        require(cacheTimeout >= 0L) { "`cacheKeepSubscribedTimeout` cannot be negative" }

        val loaderFromConfig = requireNotNull(config.loader) { "`loader` is required" }

        val retryTime = config.loadRetryTime
        require(retryTime > 0L) { "`loadRetryTime` should be positive" }

        /*
           Defining scopes.
        */
        cacheScope = CoroutineScope(config.cacheDispatcher)
        loadScope = CoroutineScope(config.loadDispatcher)

        /*
           Sharing the cache to allow several subscribers.
           Preparing `auto` cache that will track its subscribers.
        */
        cache = cacheFromConfig
            .shareIn(
                scope = cacheScope,
                started = SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = cacheTimeout,
                    // We cannot guarantee the data wasn't changed after we unsubscribed
                    replayExpirationMillis = 0L
                ),
                replay = 1
            )

        val cacheSubscribed = MutableStateFlow(false)
        auto = cache.trackSubscriptions(cacheScope, cacheSubscribed)

        /*
           Preparing `loading` and `error` state flows.
        */
        val loadingInternal = MutableStateFlow(false)
        loading = loadingInternal.asStateFlow()

        val errorInternal = MutableStateFlow<Throwable?>(null)
        error = errorInternal.asStateFlow()

        /*
           Preparing loading action that will keep its state in `loading` and `error` flows.
        */
        val loaderInternal: suspend () -> Unit = {
            // Loading data
            val data = loaderFromConfig.invoke()
            // Saving into cache using cache dispatcher
            cacheScope.launch { cacheWriter.invoke(data) }

            // Assert that loader does not return expired data to prevent endless loading
            val expiresIn = config.cacheExpiration.expiresIn(data)
            if (expiresIn <= 0L) {
                throw AssertionError(
                    "Inflow loader must not return expired data to avoid endless refresh cycle"
                )
            }
        }

        val retryForced = MutableStateFlow(false)
        loadAction = { force ->
            runWithState(logId, loadingInternal, errorInternal, retryForced, force, loaderInternal)
        }

        /*
           Scheduling loader action to run when `auto` cache is subscribed and the data is expired.
        */
        val cacheExpiration = cache.map(config.cacheExpiration::expiresIn)
        val activation = cacheSubscribed.repeatWhenConnected(config.connectivity)

        loadScope.launch {
            scheduleUpdates(logId, cacheExpiration, activation, retryTime, loadAction)
        }
    }

    private constructor(
        logId: String,
        cache: Flow<T>,
        auto: Flow<T>,
        loading: StateFlow<Boolean>,
        error: StateFlow<Throwable?>,
        loadAction: suspend (Boolean) -> Boolean,
        cacheScope: CoroutineScope,
        loadScope: CoroutineScope
    ) {
        this.logId = logId
        this.cache = cache
        this.auto = auto
        this.loading = loading
        this.error = error
        this.loadAction = loadAction
        this.cacheScope = cacheScope
        this.loadScope = loadScope
    }


    override fun data(autoRefresh: Boolean): Flow<T> {
        ensureNotClosed()
        return if (autoRefresh) auto else cache
    }

    override fun loading(): StateFlow<Boolean> {
        ensureNotClosed()
        return loading
    }

    override fun error(): StateFlow<Throwable?> {
        ensureNotClosed()
        return error
    }

    override fun refresh(repeatIfRunning: Boolean) {
        ensureNotClosed()
        loadScope.launch { loadAction(repeatIfRunning) }
    }

    // TODO: Return result / throw exception as if it was a real network call
    suspend fun refreshBlockingInternal(repeatIfRunning: Boolean) {
        ensureNotClosed()
        val called = loadAction(repeatIfRunning)

        if (!called) {
            // The loadAction itself didn't suspend because another refresh is currently in place.
            // We'll suspend by waiting for the refresh to finish.
            // Very unlikely but it can happen that loading state was already concurrently set to
            // `false`, we are fine with it. It can also happen that it was both set to `false` and
            // to `true` again, then we'll just wait for this new request to finish, not a big deal.
            loading.first { !it }
        }
    }

    override fun close() = closeInternal(byUser = true)

    // TODO: Remove? Because it can cause unwanted side effects e.g. when mapping requests, etc
    // Not guaranteed at all, but worth trying (?)
    protected fun finalize() = closeInternal(byUser = false)

    private fun closeInternal(byUser: Boolean) {
        if (!closed) {
            closed = true
            log(logId) { if (byUser) "Inflow is closed" else "Inflow is garbage-collected" }
            cacheScope.cancel("Inflow is closed")
            loadScope.cancel("Inflow is closed")
        }
    }

    private fun ensureNotClosed() {
        if (closed) throw IllegalStateException("Inflow is closed")
    }


    fun <R> mapInternal(mapper: suspend (T) -> R): Inflow<R> {
        ensureNotClosed()
        return InflowImpl(
            logId = "$logId-mapped",
            cache = cache.map(mapper),
            auto = auto.map(mapper),
            loading = loading,
            error = error,
            loadAction = loadAction,
            cacheScope = cacheScope, // TODO: copy scopes to avoid closing?
            loadScope = loadScope
        )
    }

}
