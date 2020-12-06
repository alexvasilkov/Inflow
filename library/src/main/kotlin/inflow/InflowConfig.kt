package inflow

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Configuration params to create a new [Inflow], with default values.
 *
 * **Loader and cache**
 *
 * The only required value is [loader] which should be set to a suspending function that will fetch
 * a new data from the network (or any other source).
 *
 * A [cache] flow and a [cacheWriter] function can be defined to read existing cache and store a
 * newly loaded data into the cache. The newly cached data is expected to be observed by [cache]
 * flow shortly after it was saved by [cacheWriter].
 *
 * If [cache] and [cacheWriter] values are set to `null` (which is by default) then a fallback
 * in-memory cache will be used to store a newly loaded data.
 *
 * If non-null [cache] flow is provided then it is expected to always emit an empty value
 * (e.g. `null`) if the cache is currently empty. This empty value will be a trigger for an
 * immediate refresh (also see **Cache expiration** section below).
 *
 * Local cache can be defined manually or using external libraries such as Jetpack Room or
 * Jetpack DataStore.
 *
 * **Cache expiration**
 *
 * Cache expiration can be controlled by [cacheExpiration] object which will define how long it is
 * left before the cache should be considered as expired. If cached data is expired then it will be
 * immediately refreshed using [loader], otherwise an automatic refresh will be scheduled after the
 * expiration timeout as returned by [cacheExpiration].
 *
 * It is important that newly loaded data always has expiration time greater than `0`, otherwise
 * an assertion error will be thrown to prevent endless refresh cycle.
 *
 * **Retries and connectivity**
 *
 * If data loading failed (e.g. exception was thrown by the [loader]) and no new data was stored
 * into the cache then automatic retry will be scheduled after a timeout specified with
 * [loadRetryTime].
 *
 * If [connectivity] is set to a non-null instance then it will be used to detect when the internet
 * connection becomes available to automatically retry failed requests instead of waiting for the
 * next retry time.
 *
 * Automatic retries can be disabled by setting [loadRetryTime] to [Long.MAX_VALUE] and
 * [connectivity] to `null`.
 */
class InflowConfig<T> internal constructor(

    /**
     * Flow of cached data. This flow should always emit `null` (or empty) value if no data is
     * cached yet to trigger the loading process. The cache will be subscribed using
     * [cacheDispatcher] to allow sharing it between several subscribers.
     */
    var cache: Flow<T>? = null,

    /**
     * Suspending function that will be called using [cacheDispatcher] to save newly loaded data
     * into the cache.
     */
    var cacheWriter: (suspend (T) -> Unit)? = null,

    /**
     * Cache expiration provider.
     * Uses [ExpiresIn.IfNull] strategy be default but should be set explicitly (e.g. using
     * [ExpiresIn.Duration]) if a more advanced strategy is needed.
     */
    var cacheExpiration: ExpiresIn<T> = ExpiresIn.IfNull(),

    // TODO
    var cacheInvalidation: ExpiresIn<T> = ExpiresIn.IfNull(),

    /**
     * Time to keep active [cache] subscription even if there are no other subscribers currently.
     * If there are still no subscribers after that time the [cache] will be unsubscribed.
     *
     * It can be useful to avoid extra readings from cold [cache] flow while switching app screens.
     *
     * Set to 1 second by default.
     */
    var cacheKeepSubscribedTimeout: Long = 1_000L,

    /**
     * Coroutine dispatcher that will be used to subscribe to [cache] and save new data using
     * [cacheWriter]. Uses [Dispatchers.IO] by default.
     */
    var cacheDispatcher: CoroutineDispatcher = Dispatchers.IO,

    /**
     * Suspending function that will be called using [loadDispatcher] to load a new data.
     */
    var loader: (suspend () -> T)? = null,

    /**
     * Loading retry time if last attempt was not successful. Set to 1 minute by default.
     */
    var loadRetryTime: Long = 60_000,

    /**
     * Coroutine dispatcher that will be used when calling [loader].
     * Uses [Dispatchers.IO] by default.
     */
    var loadDispatcher: CoroutineDispatcher = Dispatchers.IO,

    /**
     * Connectivity state provider that will be used to automatically retry failed request when
     * internet connection is established.
     *
     * Set to global [InflowConnectivity.Default] provider by default, which in turn is set to
     * `null` unless explicitly initialized with a real provider such as
     * [InflowConnectivity.Network].
     */
    var connectivity: InflowConnectivity? = InflowConnectivity.Default,

    /**
     * Log id to distinguish this `Inflow` from others.
     */
    var logId: String = "NO_ID"

) {

    fun cacheInMemory(initialValue: T) {
        val memoryCache = MutableSharedFlow<T>(replay = 1)
        memoryCache.tryEmit(initialValue) // By contract the cache should always emit
        cache = memoryCache
        cacheWriter = { memoryCache.emit(it) }
    }

}
