package inflow.internal

import inflow.Inflow
import inflow.InflowConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class) // It is our internal details, no need to enforce it
internal class InflowImpl<T>(config: InflowConfig<T>) : Inflow<T> {

    private val cache: Flow<T>
    private val auto: Flow<T>
    private val loader: Loader<T>

    init {
        val logId = config.logId

        // Config validation
        val cacheFromConfig = requireNotNull(config.cache) { "`cache` is required" }
        val cacheWriter = requireNotNull(config.cacheWriter) { "`cacheWriter` is required" }

        val cacheTimeout = config.cacheKeepSubscribedTimeout
        require(cacheTimeout >= 0L) { "`cacheKeepSubscribedTimeout` cannot be negative" }

        val loaderFromConfig = requireNotNull(config.loader) { "`loader` is required" }

        val retryTime = config.loadRetryTime
        require(retryTime > 0L) { "`loadRetryTime` should be positive" }

        // Defining scopes
        val cacheScope = CoroutineScope(config.cacheDispatcher)
        val loadScope = CoroutineScope(config.loadDispatcher)

        // Preparing the loader that will track its `loading` and `error` state
        loader = Loader(logId, loadScope) {
            // Loading data
            val data = loaderFromConfig.invoke()

            // TODO: Do not save data to cache if "repeatIfRunning"
            // Saving data into cache using cache dispatcher
            cacheScope.launch { cacheWriter.invoke(data) }

            // Assert that loader does not return expired data to prevent endless loading
            val expiresIn = config.cacheExpiration.expiresIn(data)
            if (expiresIn <= 0L) {
                throw AssertionError("Loader must not return expired data to avoid endless loading")
            }

            data
        }

        // Checking for cached data invalidation if configured
        val cacheWithInvalidation = if (config.cacheInvalidation != null) {
            val invalidIn = config.cacheInvalidation!!

            // If invalidation provider is set then empty value will never be null for non-null `T`
            @Suppress("UNCHECKED_CAST")
            val emptyValue = config.cacheInvalidationEmpty as T

            cacheFromConfig.emptyIfInvalid(logId, invalidIn, emptyValue)
        } else {
            cacheFromConfig
        }

        // Sharing the cache to allow several subscribers
        cache = cacheWithInvalidation.share(cacheScope, cacheTimeout)

        // Preparing a flow that will emit data expiration duration each time the data is changed
        // or connectivity provider signals about active connection
        val cacheExpiration = config.connectivity.asSignalingFlow()
            .flatMapLatest { cache }
            .map(config.cacheExpiration::expiresIn)

        // Preparing `auto` cache that will schedule data updates whenever it is subscribed and
        // the data is expired
        auto = cache.doWhileSubscribed {
            loadScope.launch {
                scheduleUpdates(logId, cacheExpiration, retryTime) {
                    loader.load(repeatIfRunning = false).join()
                }
            }
        }
    }

    override fun data(autoRefresh: Boolean): Flow<T> = if (autoRefresh) auto else cache

    override fun loading() = loader.loading

    override fun error() = loader.error

    override fun refresh(repeatIfRunning: Boolean) = loader.load(repeatIfRunning)

}
