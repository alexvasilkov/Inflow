package inflow

import inflow.utils.AtomicBoolean
import inflow.utils.inflowVerbose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

/**
 * Configuration params to create a new [Inflow], with default values.
 *
 * It is required to call one of the variants of [data] method to configure the Inflow, other
 * configuration methods are optional.
 *
 * **Loader and cache**
 *
 * To configure the data source use one of the [data] methods.
 * The basic configuration involves providing the cache flow and the loader. There are other
 * variants of [data] method including ones with separate cache writer parameter, with loader that
 * returns a flow of data and with automatic in-memory cache.
 *
 * The cache flow should emit latest cached data upon subscription and then emit new data each
 * time it was updated in the cache. If no data is cached yet the cache flow is still expected to
 * emit an empty (e.g. `null`) value. This empty value will be a trigger for an immediate refresh
 * once [Inflow.data] is subscribed (also see **Cache expiration and invalidation** section below).
 *
 * The loader should be set to a suspending function that will fetch new data from the network or
 * any other source. The loader is expected to save the data into the cache itself unless dedicated
 * cache writer parameter was passed to [data] method.
 *
 * It is expected that once a new data is saved into the cache it will be emitted by the cache flow
 * shortly. If the data was removed from the cache then cache flow is expected to return an empty
 * value.
 *
 * The cache flow will be shared among all active subscribers. Thus no extra cache readings will be
 * done for subsequent subscribers and they will immediately receive the most recent cache data.
 * Original cache will be unsubscribed after [keepCacheSubscribedTimeout] since last active
 * subscriber is unsubscribed.
 *
 * Cache flow can be defined manually or using external libraries such as Room, DataStore, etc.
 *
 * **Cache expiration and invalidation**
 *
 * Cache expiration is controlled by [expiration] policy which defines how long it is left before
 * the cache should be considered as expired. If cached data is expired then it will be immediately
 * refreshed, otherwise an automatic refresh will be scheduled after the expiration timeout as
 * returned by [expiration] provider.
 *
 * It is important that newly loaded data is not immediately expired, otherwise it can stuck in an
 * endless refresh cycle.
 *
 * The cache can also be completely invalidated using the [invalidation] policy. If the cache is
 * considered invalid then an empty value will be returned instead.
 *
 * The common usage of [expiration] and [invalidation] policies assumes tracking of the time when
 * the data was loaded and then define minimal time when the data is still considered "fresh" using
 * [expiration] and maximal time after which the data should not be used in any circumstances using
 * [invalidation].
 *
 * **Retries and connectivity**
 *
 * If data loading failed (e.g. exception was thrown by the loader) and no new data was stored into
 * the cache then automatic retry will be scheduled after a timeout specified with [retryTime].
 *
 * If [connectivity] is set to a non-null instance then it will be used to detect when the internet
 * connection becomes available to automatically retry failed requests instead of waiting for the
 * next retry time.
 *
 * Automatic retries can be disabled by setting [retryTime] to [Long.MAX_VALUE] and [connectivity]
 * to `null`.
 */
public class InflowConfig<T> internal constructor() {

    @JvmField
    @JvmSynthetic
    internal var data: InflowData<T>? = null

    private var memoryCacheUsed = false

    @JvmField
    @JvmSynthetic
    internal var expiration: ExpirationProvider<T> = ExpiresIfNull()

    @JvmField
    @JvmSynthetic
    internal var invalidation: ExpirationProvider<T>? = null

    @JvmField
    @JvmSynthetic
    internal var invalidationEmpty: T? = null

    @JvmField
    @JvmSynthetic
    internal var keepCacheSubscribedTimeout: Long = 1_000L // 1 sec

    @JvmField
    @JvmSynthetic
    internal var retryTime: Long = 60_000L // 1 min

    @JvmField
    @JvmSynthetic
    internal var connectivity: InflowConnectivity? = InflowConnectivity.Default

    @JvmField
    @JvmSynthetic
    internal var cacheDispatcher: CoroutineDispatcher = Dispatchers.Default

    @JvmField
    @JvmSynthetic
    internal var loadDispatcher: CoroutineDispatcher = Dispatchers.Default

    private var _scope: CoroutineScope? = null

    @get:JvmSynthetic
    internal val scope: CoroutineScope
        get() = _scope ?: CoroutineScope(Job()).apply { _scope = this }

    @JvmField
    @JvmSynthetic
    internal var logId: String = "NO_ID"


    /**
     * Sets local and remote data sources.
     *
     * @param cache Flow of cached data. This flow should always emit some empty value (e.g. `null`)
     * if no data is cached yet to trigger the loading process. The cache will be subscribed and
     * un-subscribed on demand using [cacheDispatcher] to allow sharing it between several
     * downstream subscribers. See [Inflow.data]. Any errors thrown by the cache will propagate into
     * the [scope] and eventually crash the app if no custom error handler was provided.
     *
     * @param loader Suspending function that will be called using [loadDispatcher] to load a new
     * data. **It is loader's responsibility to save the data into the cache** and it is expected
     * that once the loader is executed the new data will be emitted by the `cache` shortly.
     * Only one loader request will run at a time, meaning there will never be parallel requests
     * of the same data.
     *
     * **Important:** The newly loaded data should not be expired according to [expiration] policy
     * to avoid endless loadings.
     */
    public fun data(cache: Flow<T>, loader: suspend (LoadTracker) -> Unit) {
        require(data == null) { "Data is already set, cannot call `data()` again" }
        data = InflowData(cache, loader)
    }

    /**
     * Variant of [data] function that allows providing a cache [writer] as a separate parameter.
     *
     * In this case the [loader] should just return the newly loaded data and the [writer] will be
     * responsible to actually save it into the cache (using [cacheDispatcher]).
     */
    public fun <R> data(
        cache: Flow<T>,
        writer: suspend (R) -> Unit,
        loader: suspend (LoadTracker) -> R
    ) {
        data(cache) {
            val result = loader(it)
            // Calling from scope to propagate exceptions and crash the scope.
            // If scope is cancelled then cache writes will be skipped.
            scope.launch(cacheDispatcher) { writer(result) }.join()
        }
    }

    /**
     * Variant of [data] function that allows providing the [loader][loaderFlow] (as a flow) and
     * cache [writer] as separate parameters.
     *
     * In this case the [loaderFlow] can emit several loaded values and the [writer] will be
     * responsible to actually save them into the cache (using [cacheDispatcher]).
     */
    @JvmName("dataFlow")
    public fun <R> data(
        cache: Flow<T>,
        writer: suspend (R) -> Unit,
        loaderFlow: suspend (LoadTracker) -> Flow<R>
    ) {
        data(cache) {
            loaderFlow(it).collect { result ->
                // Calling from scope to propagate exceptions and crash the scope.
                // If scope is cancelled then cache writes will be skipped.
                scope.launch(cacheDispatcher) { writer(result) }.join()
            }
        }
    }

    /**
     * Variant of [data] function that will create and use in-memory cache for all loaded values.
     * By contract the cache should always emit an empty value in the beginning thus an extra
     * [initial] value is required as well.
     *
     * In this case the [loader] should just return the newly loaded data and it will be
     * automatically saved into memory cache.
     *
     * Note that [keepCacheSubscribedTimeout] will be automatically set to 0L and attempt to set
     * it to anything greater than 0L will throw [IllegalArgumentException].
     *
     * @return Writer that can be used to update the in-memory cache from the outside.
     */
    public fun data(
        initial: T,
        loader: suspend (LoadTracker) -> T
    ): MemoryCacheWriter<T> {
        val memory = MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        memory.tryEmit(initial)
        data(memory) { memory.tryEmit(loader(it)) }
        setMemoryCacheUsed()
        return MemoryCacheWriter(memory::tryEmit)
    }

    /**
     * Variant of [data] function that will create and use in-memory cache for all loaded values.
     * By contract the cache should always emit an empty value in the beginning thus an extra
     * [initial] value is required as well. The deferred [initial] value will be only called once
     * on first cache access.
     *
     * In this case the [loader] should just return the newly loaded data and it will be
     * automatically saved into memory cache.
     *
     * Note that [keepCacheSubscribedTimeout] will be automatically set to 0L and attempt to set
     * it to anything greater than 0L will throw [IllegalArgumentException].
     *
     * @return Writer that can be used to update the in-memory cache from the outside.
     */
    public fun data(
        initial: suspend () -> T,
        loader: suspend (LoadTracker) -> T
    ): MemoryCacheWriter<T> {
        val memory = MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        // By contract the cache should always emit, we need to initialize it on first start
        val initialized = AtomicBoolean()
        val cache = memory.onSubscription {
            if (initialized.compareAndSet(expect = false, update = true)) {
                memory.tryEmit(initial())
            }
        }

        data(cache) { memory.tryEmit(loader(it)) }
        setMemoryCacheUsed()

        return MemoryCacheWriter {
            initialized.set(true) // No need to initialize the cache anymore
            memory.tryEmit(it)
        }
    }

    private fun setMemoryCacheUsed() {
        memoryCacheUsed = true
        keepCacheSubscribedTimeout = 0L // No need to keep subscription to fast in-memory cache
        cacheDispatcher = Dispatchers.Unconfined // In-memory cache don't need real dispatcher
    }

    /**
     * Cache expiration policy, see [ExpirationProvider]. Uses [ExpiresIfNull] policy by default.
     */
    public fun expiration(provider: ExpirationProvider<T>) {
        expiration = provider
    }

    /**
     * Cache invalidation policy, see [ExpirationProvider].
     * By default the cache is considered to be valid all the time.
     *
     * Provided [emptyValue] will be emitted each time invalid data is emitted by original cache
     * and automatically after the expiration time defined by [provider] policy.
     *
     * *For example if an item emitted by the cache will be invalid in 1 minute then once 1 minute
     * is passed all active subscribers will receive [emptyValue] even if no extra items were
     * emitted by cache.*
     *
     * **Important:** [emptyValue] should be 'expired' according to [expiration] policy, otherwise
     * invalid data will not be automatically refreshed.
     */
    public fun invalidation(emptyValue: T, provider: ExpirationProvider<T>) {
        invalidation = provider
        invalidationEmpty = emptyValue
    }

    /**
     * Time to keep active cache subscription even if there are no other subscribers currently.
     * If there are still no subscribers after that time the cache will be unsubscribed.
     * See [Inflow.data].
     *
     * It can be useful to avoid extra readings from slow cold cache flow while e.g. switching app's
     * screens.
     *
     * If set to `0` then the cache will be immediately unsubscribed once last [Inflow.data]
     * subscriber is gone. If set to `Long.MAX_VALUE` then the cache will be kept subscribed
     * forever (until [scope] is cancelled), it means that any changes to the cache will be
     * immediately observed and cached by the shared cache used for [Inflow.data] and all new
     * subscribers won't have to wait for cache reads.
     *
     * Setting a big timeout should be avoided unless a proper cancellation strategy is implemented.
     *
     * Must be >= 0. Set to 1 second by default.
     */
    public fun keepCacheSubscribedTimeout(timeoutMillis: Long) {
        require(timeoutMillis >= 0L) { "`keepCacheSubscribedTimeout` cannot be negative" }
        if (memoryCacheUsed) {
            require(timeoutMillis == 0L) { "`keepCacheSubscribedTimeout` should be 0 when using in-memory cache" }
        }
        keepCacheSubscribedTimeout = timeoutMillis
    }

    /**
     * The time to wait before repeating unsuccessful loading call.
     * Set to 1 minute by default.
     *
     * Note that retry time should be greater than the time needed for the cache to propagate from
     * "save to cache" call to the emission from cache flow. Otherwise the loading call will be
     * retried even if the data was already successfully loaded which can lead to an infinite cycle.
     *
     * **It is advised to set retry time at least to a few seconds. Cannot be <= 0.**
     */
    public fun retryTime(retryTimeMillis: Long) {
        require(retryTimeMillis > 0L) { "`retryTime` should be positive" }
        retryTime = retryTimeMillis
    }

    /**
     * Connectivity state provider that will be used to automatically retry failed requests when
     * internet connection is established.
     *
     * Set to global [InflowConnectivity.Default] provider by default, which in turn is set to
     * `null` unless initialized with a real provider.
     *
     * Android library provides a default implementation for the network connectivity.
     */
    public fun connectivity(provider: InflowConnectivity?) {
        connectivity = provider
    }

    /**
     * Coroutine dispatcher that will be used to subscribe to the cache and to save new data using
     * cache writers which can be found in some of the [data] method variants.
     *
     * Well written suspend functions should be safe to call from any thread ("main-safety"
     * principle). Thus you should rarely care about the dispatcher used to access the cache.
     *
     * Uses [Dispatchers.Default] by default.
     */
    public fun cacheDispatcher(dispatcher: CoroutineDispatcher) {
        cacheDispatcher = dispatcher
    }

    /**
     * Coroutine dispatcher that will be used when calling the loader.
     *
     * Well written suspend functions should be safe to call from any thread ("main-safety"
     * principle). Thus you should rarely care about the dispatcher used for the loader.
     *
     * Uses [Dispatchers.Default] by default.
     */
    public fun loadDispatcher(dispatcher: CoroutineDispatcher) {
        loadDispatcher = dispatcher
    }


    /**
     * Coroutine scope used for shared cache subscription and for the loader.
     * Can be used to cancel cache subscription and loader execution, or handle uncaught errors.
     *
     * When the scope is cancelled the [Inflow] will unsubscribe from the cache (all current and
     * future subscribers will get [CancellationException]) and will cancel the loader and automatic
     * refresh.
     * Note that the loader needs to manually check if its current coroutine context is active with
     * `coroutineContext.isActive` and stop loading / saving into cache otherwise.
     *
     * It is generally unnecessary to manually cancel the Inflow as it will not have any hanging
     * jobs once it has no active subscribers and the cache is unsubscribed after
     * [keepCacheSubscribedTimeout]. If this timeout is big or if no loading and cache writing is
     * desirable anymore then the Inflow's scope can be cancelled explicitly.
     */
    public fun scope(scope: CoroutineScope) {
        _scope = scope
    }

    /**
     * Log id to distinguish this `Inflow` from others when in verbose mode ([inflowVerbose]).
     */
    public fun logId(logId: String) {
        this.logId = logId
    }

}


internal class InflowData<T>(
    val cache: Flow<T>,
    val loader: suspend (LoadTracker) -> Unit
)

/**
 * Allows updating in-memory cache created by some of the [InflowConfig.data] methods variants.
 */
public fun interface MemoryCacheWriter<T> {
    public operator fun invoke(data: T)
}
