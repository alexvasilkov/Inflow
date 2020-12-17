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
        loader = Loader(config.logId, loadScope) {
            // Loading data
            val data = loaderFromConfig.invoke()

            // Saving data into cache using cache dispatcher
            cacheScope.launch { cacheWriter.invoke(data) }

            // Assert that loader does not return expired data to prevent endless loading
            val expiresIn = config.cacheExpiration.expiresIn(data)
            if (expiresIn <= 0L) {
                throw AssertionError("Loader must not return expired data to avoid endless loading")
            }

            data
        }

        // Sharing the cache to allow several subscribers
        cache = cacheFromConfig.share(cacheScope, cacheTimeout)

        // Preparing a flow that will emit data expiration duration each time the data is changed
        // or connectivity provider signals about active connection
        val cacheExpiration = config.connectivity.asSignalingFlow()
            .flatMapLatest { cache }
            .map(config.cacheExpiration::expiresIn)

        // Preparing `auto` cache that will schedule data updates whenever it is subscribed and
        // the data is expired
        auto = cache.doWhileSubscribed {
            loadScope.launch {
                scheduleUpdates(config.logId, cacheExpiration, retryTime) {
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
