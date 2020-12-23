package inflow.internal

import inflow.DataParam
import inflow.DataParam.CacheOnly
import inflow.Inflow
import inflow.InflowConfig
import inflow.InflowDeferred
import inflow.RefreshParam
import inflow.RefreshParam.IfExpiresIn
import inflow.utils.log
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class) // It is our internal details, no need to enforce it
internal class InflowImpl<T>(config: InflowConfig<T>) : Inflow<T> {

    private val cache: Flow<T>
    private val auto: Flow<T>
    private val loader: Loader

    private val fromCacheDirectly: suspend () -> T

    private val logId = config.logId
    private val cacheScope = CoroutineScope(config.cacheDispatcher)
    private val loadScope = CoroutineScope(config.loadDispatcher)

    private val cacheExpiration = config.expiration

    init {
        // Config validation
        val dataFromConfig = requireNotNull(config.data) { "`data` (cache and loader) is required" }
        val loaderFromConfig = dataFromConfig.loader
        val cacheFromConfig = dataFromConfig.cache

        val cacheTimeout = config.keepCacheSubscribedTimeout
        require(cacheTimeout >= 0L) { "`cacheKeepSubscribedTimeout` cannot be negative" }

        val retryTime = config.retryTime
        require(retryTime > 0L) { "`loadRetryTime` should be positive" }

        // Preparing the loader that will track its `progress` and `error` state
        loader = Loader(logId, loadScope, loaderFromConfig)

        // Checking for cached data invalidation if configured
        val cacheInvalidation = config.invalidation
        val cacheWithInvalidation = if (cacheInvalidation != null) {
            // If invalidation provider is set then empty value will never be null for non-null `T`
            @Suppress("UNCHECKED_CAST")
            val emptyValue = config.invalidationEmpty as T

            val expirationOfEmptyValue = cacheExpiration.expiresIn(emptyValue)
            if (expirationOfEmptyValue > 0L) {
                log(logId) { "Warning: Empty value for invalidation policy is not expired, automatic refresh may not work as expected" }
            }

            cacheFromConfig.emptyIfInvalid(logId, cacheInvalidation, emptyValue)
        } else {
            cacheFromConfig
        }

        // Preparing an action that can load data directly from cache
        fromCacheDirectly = { cacheWithInvalidation.first() }

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

    override fun progress() = loader.progress

    override fun error() = loader.error

    override fun refresh(vararg params: RefreshParam): InflowDeferred<T> {
        val ifExpiresIn = params.find { it is IfExpiresIn } as IfExpiresIn?
        val repeatIfRunning = params.contains(RefreshParam.Repeat)

        val deferred = DeferredDelegate()
        if (ifExpiresIn != null) {
            // We need to request latest cached value first to check its expiration.
            // We don't care about threading and can wait for the cache in any dispatcher.
            cacheScope.launch(Dispatchers.Unconfined) {
                val cached = cache.first() // Won't trigger extra cache read if already subscribed
                if (cacheExpiration.expiresIn(cached) > ifExpiresIn.expiresIn) {
                    // Not expired, returning cached value as is
                    deferred.delegateToData(CompletableDeferred(cached))
                } else {
                    // Expired, requesting refresh
                    deferred.delegateToLoader(loader.load(repeatIfRunning))
                }
            }
        } else {
            deferred.delegateToLoader(loader.load(repeatIfRunning))
        }
        return deferred
    }


    // A deferred implementation that can delegate either to actual CompletableDeferred or to
    // loader's deferred result (in which case the result will be requested from cache explicitly)
    private inner class DeferredDelegate : InflowDeferred<T> {
        private val delegateLoader = atomic<CompletableDeferred<Unit>?>(null)
        private val delegateData = atomic<CompletableDeferred<T>?>(null)
        private val notifier = Job()

        fun delegateToLoader(loader: CompletableDeferred<Unit>) {
            delegateLoader.value = loader
            notifier.complete()
        }

        fun delegateToData(loader: CompletableDeferred<T>) {
            delegateData.value = loader
            notifier.complete()
        }

        override suspend fun await(): T {
            // Waiting for the delegates
            notifier.join()

            // If data delegate is provided then we'll use it
            delegateData.value?.apply { return await() }

            // Loading and awaiting the result (we are only interested in a thrown exception here)
            delegateLoader.value!!.await()
            // Reading latest value directly from original cache as shared cache may not have the
            // latest version yet. I.e. the loader completed and saved the data into the cache but
            // it was not observed by the shared cache yet.
            return withContext(cacheScope.coroutineContext) { fromCacheDirectly() }
        }

        override suspend fun join() {
            // Waiting for the delegates
            notifier.join()

            // If data delegate is provided then we'll use it first
            delegateData.value?.apply { return join() }

            // Else joining loader's delegate
            delegateLoader.value!!.join()
        }
    }

}
