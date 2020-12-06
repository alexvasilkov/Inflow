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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

private val NULL = Any()

internal class InflowImpl<T> : Inflow<T> {

    private val tag = "NO_ID" // TODO: Should we allow the user to set log tag, or?

    private val cache: Flow<T>
    private val auto: Flow<T>

    private val loading: StateFlow<Boolean>
    private val error: StateFlow<Throwable?>
    private val loadAction: suspend (Boolean) -> Boolean

    private val cacheScope: CoroutineScope
    private val loadScope: CoroutineScope

    internal var closed = false


    constructor(config: InflowConfig<T>) {
        /*
           Config validations
        */
        val configLoader = requireNotNull(config.loader) { "Loader is required" }

        val cacheTimeout = config.cacheKeepSubscribedTimeout
        require(cacheTimeout >= 0L) {
            "Cache subscription timeout (${cacheTimeout}ms) cannot be negative"
        }

        val retryTime = config.loadRetryTime
        require(retryTime > 0L) { "Loading retry time (${retryTime}ms) should be positive" }

        /*
           Defining scopes
        */
        cacheScope = CoroutineScope(config.cacheDispatcher)
        loadScope = CoroutineScope(config.loadDispatcher)

        /*
           If cache is not provided then use custom in-memory cache
        */
        val cacheTmp: Flow<Any?> // T || NULL
        val cacheWriterInternal: suspend (T) -> Unit

        if (config.cache != null) {
            cacheTmp = config.cache!!
            cacheWriterInternal = requireNotNull(config.cacheWriter) {
                "Cache writer must be provided along with cache flow"
            }
        } else {
            val memoryCache = MutableSharedFlow<Any?>(replay = 1)
            memoryCache.tryEmit(NULL) // Start with NULL, by contract the cache should always emit
            cacheTmp = memoryCache
            cacheWriterInternal = { memoryCache.emit(it) }
            require(config.cacheWriter == null) {
                "Cache writer must not be set if cache flow is not provided as well"
            }
        }

        /*
           Sharing the cache to allow several subscribers
        */
        val cacheInternal: SharedFlow<Any?> = cacheTmp // T || NULL
            .shareIn(
                scope = cacheScope,
                started = SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = cacheTimeout,
                    // We cannot guarantee the data wasn't changed after we unsubscribed
                    replayExpirationMillis = 0L
                ),
                replay = 1
            )

        @Suppress("UNCHECKED_CAST")
        cache = cacheInternal
            .filter { it !== NULL } as Flow<T> // If not NULL then only T

        /*
           Preparing `auto` cache that will track its subscribers
        */
        val cacheSubscribed = MutableStateFlow(false)
        @Suppress("UNCHECKED_CAST")
        auto = cacheInternal
            .trackSubscriptions(cacheScope, cacheSubscribed)
            .filter { it !== NULL } as Flow<T> // If not NULL then only T

        /*
           Preparing `loading` and `error` state flows
        */
        val loadingInternal = MutableStateFlow(false)
        loading = loadingInternal.asStateFlow()

        val errorInternal = MutableStateFlow<Throwable?>(null)
        error = errorInternal.asStateFlow()

        /*
           Preparing loading action that will keep its state in `loading` and `error` flows
        */
        val loaderInternal: suspend () -> Unit = {
            // Loading data
            val data = configLoader.invoke()
            // Saving into cache using cache dispatcher
            cacheScope.launch { cacheWriterInternal.invoke(data) }

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
            runWithState(tag, loadingInternal, errorInternal, retryForced, force, loaderInternal)
        }

        /*
           Scheduling loader action to run when `auto` cache is subscribed and the data is expired
        */
        @Suppress("UNCHECKED_CAST")
        val cacheExpiration = cacheInternal
            .map { if (it === NULL) 0L else config.cacheExpiration.expiresIn(it as T) }
        val activation = cacheSubscribed.repeatWhenConnected(config.connectivity)

        loadScope.launch {
            scheduleUpdates(tag, cacheExpiration, activation, retryTime, loadAction)
        }
    }

    private constructor(
        cache: Flow<T>,
        auto: Flow<T>,
        loading: StateFlow<Boolean>,
        error: StateFlow<Throwable?>,
        loadAction: suspend (Boolean) -> Boolean,
        cacheScope: CoroutineScope,
        loadScope: CoroutineScope
    ) {
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

    suspend fun refreshBlockingInternal(repeatIfRunning: Boolean) {
        ensureNotClosed()
        val called = loadAction(repeatIfRunning)

        if (!called) {
            // The loadAction itself didn't block because another refresh is currently in place.
            // We'll block by waiting for the refresh to finish.
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
            log(tag) { if (byUser) "Inflow is closed" else "Inflow is garbage-collected" }
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
