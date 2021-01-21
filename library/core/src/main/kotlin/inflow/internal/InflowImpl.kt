package inflow.internal

import inflow.DataParam
import inflow.DataParam.CacheOnly
import inflow.ErrorParam
import inflow.Inflow
import inflow.InflowConfig
import inflow.InflowDeferred
import inflow.RefreshParam
import inflow.RefreshParam.IfExpiresIn
import inflow.utils.doOnCancel
import inflow.utils.log
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ExperimentalCoroutinesApi
internal class InflowImpl<T>(config: InflowConfig<T>) : Inflow<T> {

    private val cache: Flow<T>
    private val auto: Flow<T>
    private val loader: Loader

    private val fromCacheDirectly: suspend () -> T

    private val logId = config.logId
    private val scope = config.scope
    private val cacheDispatcher = config.cacheDispatcher
    private val loadDispatcher = config.loadDispatcher
    private val cacheExpiration = config.expiration

    private val handledError = atomic<Throwable?>(null)

    init {
        val dataFromConfig = requireNotNull(config.data) { "`data` (cache and loader) is required" }
        val loaderFromConfig = dataFromConfig.loader
        val cacheFromConfig = dataFromConfig.cache
        val cacheTimeout = config.keepCacheSubscribedTimeout
        val retryTime = config.retryTime
        val cacheInvalidation = config.invalidation
        val cacheInvalidationEmpty = config.invalidationEmpty
        val connectivity = config.connectivity

        // Preparing the loader that will track its `progress` and `error` state
        loader = Loader(logId, scope, loadDispatcher, loaderFromConfig)

        // Checking for cached data invalidation if configured
        val cacheWithInvalidation = if (cacheInvalidation != null) {
            // If invalidation provider is set then empty value will never be null for non-null `T`
            @Suppress("UNCHECKED_CAST")
            val emptyValue = cacheInvalidationEmpty as T

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
        cache = cacheWithInvalidation.share(scope, cacheDispatcher, cacheTimeout)

        // Preparing a flow that will emit cached data each time the data is changed
        // or connectivity provider signals about active connection
        val cacheRepeated = connectivity.asSignalingFlow().flatMapLatest { cache }

        // Preparing `auto` cache that will schedule data updates whenever it is subscribed and
        // cached data is expired
        auto = cache.doWhileSubscribed {
            scope.launch(loadDispatcher) {
                scheduleUpdates(logId, cacheRepeated, cacheExpiration, retryTime) {
                    loader.load(repeatIfRunning = false).join()
                }
            }
        }
    }

    override fun data(vararg params: DataParam) = if (params.contains(CacheOnly)) cache else auto

    override fun progress() = loader.progress

    override fun error(vararg params: ErrorParam): Flow<Throwable?> {
        val skipIfCollected = params.contains(ErrorParam.SkipIfCollected)
        return if (skipIfCollected) {
            loader.error
                .map(::handleError)
                .distinctUntilChanged { old, new -> old == null && new == null }
        } else {
            loader.error
        }
    }

    private fun handleError(error: Throwable?): Throwable? {
        val handled = handledError.value
        return if (error !== handled && error === loader.error.value) {
            val set = handledError.compareAndSet(expect = handled, update = error)
            if (set) error else null
        } else {
            null
        }
    }

    override fun refresh(vararg params: RefreshParam): InflowDeferred<T> {
        val ifExpiresIn = params.find { it is IfExpiresIn } as IfExpiresIn?
        val repeatIfRunning = params.contains(RefreshParam.Repeat)

        val deferred = DeferredDelegate()
        if (ifExpiresIn != null) {
            // We need to request latest cached value first to check its expiration
            val job = scope.launch(cacheDispatcher) {
                // Getting cached value, it won't trigger extra cache read if already subscribed.
                // If scope is cancelled while we're waiting for the cache then shared cache
                // should throw cancellation exception.
                val cached = cache.first()

                if (cacheExpiration.expiresIn(cached) > ifExpiresIn.expiresIn) {
                    // Not expired, returning cached value as is
                    deferred.onValue(cached)
                } else {
                    // Expired, requesting refresh
                    deferred.delegateToLoader(loader.load(repeatIfRunning))
                }
            }
            // If scope is cancelled then we need to notify our deferred object
            job.doOnCancel(deferred::onCancelled)
        } else {
            deferred.delegateToLoader(loader.load(repeatIfRunning))
        }
        return deferred
    }


    // A deferred implementation that can delegate either to actual value / error or to
    // loader's deferred result (in which case the result will be requested from cache explicitly)
    private inner class DeferredDelegate : InflowDeferred<T> {
        private val delegateLoader = atomic<CompletableDeferred<Unit>?>(null)
        private val delegateData = atomic<CompletableDeferred<T>?>(null)
        private val notifier = Job()

        fun delegateToLoader(loader: CompletableDeferred<Unit>) {
            delegateLoader.value = loader
            notifier.complete()
        }

        fun onValue(value: T) {
            delegateData.value = CompletableDeferred(value)
            notifier.complete()
        }

        fun onCancelled(exception: CancellationException) {
            val result = CompletableDeferred<T>()
            result.completeExceptionally(exception)
            delegateData.value = result
            notifier.complete()
        }

        override suspend fun await(): T {
            // Waiting for the delegates
            notifier.join()

            // If data delegate is provided then we'll just use it as is
            delegateData.value?.apply { return await() }

            // Loading and awaiting the result (we are only interested in a thrown exception here)
            delegateLoader.value!!.await()
            // Reading latest value directly from original cache as shared cache may not have the
            // latest version yet. I.e. the loader completed and saved the data into the cache but
            // it was not observed by the shared cache yet.
            return withContext(cacheDispatcher) {
                // Cache errors are not expected, they should crash the scope (and the app)
                try {
                    fromCacheDirectly()
                } catch (th: Throwable) {
                    scope.launch(cacheDispatcher) { throw th }.join()
                    throw th
                }
            }
        }

        override suspend fun join() {
            // Waiting for the delegates
            notifier.join()

            // If data delegate is provided then we'll just use it as is
            delegateData.value?.apply { return join() }

            // Else joining loader's delegate
            delegateLoader.value!!.join()
        }
    }

}
