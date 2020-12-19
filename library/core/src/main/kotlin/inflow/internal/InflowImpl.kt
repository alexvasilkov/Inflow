package inflow.internal

import inflow.DataParam
import inflow.DataParam.CacheOnly
import inflow.Inflow
import inflow.InflowConfig
import inflow.InflowDeferred
import inflow.RefreshParam
import inflow.RefreshParam.IfExpiresIn
import inflow.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class) // It is our internal details, no need to enforce it
internal class InflowImpl<T>(config: InflowConfig<T>) : Inflow<T> {

    private val cache: Flow<T>
    private val auto: Flow<T>
    private val loader: Loader<T>

    private val logId = config.logId
    private val cacheScope = CoroutineScope(config.cacheDispatcher)
    private val loadScope = CoroutineScope(config.loadDispatcher)

    private val cacheExpiration = config.cacheExpiration

    init {
        // Config validation
        val cacheFromConfig = requireNotNull(config.cache) { "`cache` is required" }
        val cacheWriter = requireNotNull(config.cacheWriter) { "`cacheWriter` is required" }

        val cacheTimeout = config.cacheKeepSubscribedTimeout
        require(cacheTimeout >= 0L) { "`cacheKeepSubscribedTimeout` cannot be negative" }

        val loaderFromConfig = requireNotNull(config.loader) { "`loader` is required" }

        val retryTime = config.loadRetryTime
        require(retryTime > 0L) { "`loadRetryTime` should be positive" }

        // Preparing the loader that will track its `loading` and `error` state
        loader = Loader(logId, loadScope) {
            // Loading data
            val data = loaderFromConfig.invoke()

            // TODO: Do not save data to cache if "repeatIfRunning"
            // Saving data into cache using cache dispatcher
            cacheScope.launch { cacheWriter.invoke(data) }

            // Assert that loader does not return expired data to prevent endless loading
            val expiresIn = cacheExpiration.expiresIn(data)
            if (expiresIn <= 0L) {
                throw AssertionError("Loader must not return expired data to avoid endless loading")
            }

            data
        }

        // Checking for cached data invalidation if configured
        val cacheInvalidation = config.cacheInvalidation
        val cacheWithInvalidation = if (cacheInvalidation != null) {
            // If invalidation provider is set then empty value will never be null for non-null `T`
            @Suppress("UNCHECKED_CAST")
            val emptyValue = config.cacheInvalidationEmpty as T

            val expirationOfEmptyValue = cacheExpiration.expiresIn(emptyValue)
            if (expirationOfEmptyValue > 0L) {
                log(logId) { "Warning: Empty value for invalidation policy is not expired, automatic refresh may not work as expected" }
            }

            cacheFromConfig.emptyIfInvalid(logId, cacheInvalidation, emptyValue)
        } else {
            cacheFromConfig
        }

        // Sharing the cache to allow several subscribers
        cache = cacheWithInvalidation.share(cacheScope, cacheTimeout)

        // Preparing a flow that will emit cached data each time the data is changed
        // or connectivity provider signals about active connection
        val cacheRepeated = config.connectivity.asSignalingFlow().flatMapLatest { cache }

        // Preparing `auto` cache that will schedule data updates whenever it is subscribed and
        // cached data is expired
        auto = cache.doWhileSubscribed {
            loadScope.launch {
                scheduleUpdates(logId, cacheRepeated, cacheExpiration, retryTime) {
                    loader.load(repeatIfRunning = false).join()
                }
            }
        }
    }

    override fun data(vararg params: DataParam): Flow<T> =
        if (params.contains(CacheOnly)) cache else auto

    override fun loading() = loader.loading

    override fun error() = loader.error

    override fun refresh(vararg params: RefreshParam): InflowDeferred<T> {
        val ifExpiresIn = params.find { it is IfExpiresIn } as IfExpiresIn?

        val repeatIfRunning = params.contains(RefreshParam.Repeat)

        return if (ifExpiresIn != null) {
            val deferred = InflowDeferredWrapper<T>()

            // We need to request latest cached value to check its expiration
            loadScope.launch {
                val cached = cache.first()
                if (cacheExpiration.expiresIn(cached) > ifExpiresIn.expiresIn) {
                    // Not expired, returning cached value as is
                    deferred.complete(cached, null)
                } else {
                    // Expired, requesting refresh
                    try {
                        val result = loader.load(repeatIfRunning).await()
                        deferred.complete(result, null)
                    } catch (th: Throwable) {
                        deferred.complete(null, th)
                    }
                }
            }

            deferred
        } else {
            loader.load(repeatIfRunning)
        }
    }

}
