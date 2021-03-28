package inflow.internal

import inflow.DataParam
import inflow.Inflow
import inflow.InflowConfig
import inflow.InflowDeferred
import inflow.InflowPagedData
import inflow.LoadParam
import inflow.State
import inflow.StateParam
import inflow.utils.doOnCancel
import inflow.utils.log
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
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

@ExperimentalCoroutinesApi
internal open class InflowImpl<T>(config: InflowConfig<T>) : Inflow<T>() {

    private val cache: Flow<T>
    private val auto: Flow<T>
    private val refresh: Loader
    private val loadNext: Loader?

    private val fromCacheDirectly: suspend () -> T

    protected val logId = config.logId
    protected val scope = config.scope ?: CoroutineScope(Job())
    protected val dispatcher = config.dispatcher
    private val cacheExpiration = config.expiration

    init {
        val data = requireNotNull(config.data) { "`data` is required" }
        val cacheTimeout = config.keepCacheSubscribedTimeout
        val retryTime = config.retryTime
        val cacheInvalidation = config.invalidation
        val cacheInvalidationEmpty = config.invalidationEmpty
        val connectivity = config.connectivity

        // Preparing the loaders that will track their `progress` and `error` states
        refresh = Loader(logId, scope, dispatcher, data::refresh)
        loadNext = when (data) {
            is InflowPagedData<*> -> Loader(logId, scope, dispatcher, data::loadNext)
            else -> null
        }

        // Checking for cached data invalidation if configured
        val cacheWithInvalidation = if (cacheInvalidation != null) {
            // If invalidation provider is set then empty value will never be null for non-null `T`
            @Suppress("UNCHECKED_CAST")
            val emptyValue = cacheInvalidationEmpty as T

            val expirationOfEmptyValue = cacheExpiration.expiresIn(emptyValue)
            if (expirationOfEmptyValue > 0L) {
                log(logId) { "Warning: Empty value for invalidation policy is not expired, automatic refresh may not work as expected" }
            }

            data.cache.emptyIfInvalid(logId, cacheInvalidation, emptyValue)
        } else {
            data.cache
        }

        // Preparing an action that can load data directly from cache
        fromCacheDirectly = { cacheWithInvalidation.first() }

        // Sharing the cache to allow several subscribers
        cache = cacheWithInvalidation.share(scope, dispatcher, cacheTimeout)

        // Preparing a flow that will emit cached data each time the data is changed
        // or connectivity provider signals about active connection
        val cacheRepeated = connectivity.asSignalingFlow().flatMapLatest { cache }

        // Preparing `auto` cache that will schedule data updates whenever it is subscribed and
        // cached data is expired
        auto = cache.doWhileSubscribed {
            scope.launch(dispatcher) {
                scheduleUpdates(logId, cacheRepeated, cacheExpiration, retryTime) {
                    refresh.load().join()
                }
            }
        }
    }

    override fun dataInternal(param: DataParam) = when (param) {
        DataParam.AutoRefresh -> auto
        DataParam.CacheOnly -> cache
    }

    override fun stateInternal(param: StateParam) = when (param) {
        StateParam.RefreshState -> refresh.state
        StateParam.LoadNextState ->
            loadNext?.state ?: throw UnsupportedOperationException("$param is not supported")
    }

    override fun loadInternal(param: LoadParam): InflowDeferred<T> {
        return when (param) {
            LoadParam.Refresh -> DeferredLoad(refresh.load())

            LoadParam.RefreshForced -> {
                val result = DeferredSelector<T>()
                val job = scope.launch(Dispatchers.Unconfined) {
                    refreshState().first { it is State.Idle } // Waiting for current load to finish
                    result.delegate(DeferredLoad(refresh.load()))
                }
                job.doOnCancel(result::cancel)
                result
            }

            is LoadParam.RefreshIfExpired -> {
                val result = DeferredSelector<T>()
                // We need to request latest cached value first to check its expiration
                val job = scope.launch(dispatcher) {
                    // Getting cached value, it won't trigger cache read if already subscribed.
                    // If scope is cancelled while we're waiting for the cache then shared cache
                    // should throw cancellation exception.
                    val cached = cache.first()

                    if (cacheExpiration.expiresIn(cached) > param.expiresIn) {
                        // Not expired, returning cached value as is
                        result.value(cached)
                    } else {
                        // Expired, requesting refresh
                        result.delegate(DeferredLoad(refresh.load()))
                    }
                }
                // If scope is cancelled then we need to notify our deferred object
                job.doOnCancel(result::cancel)
                result
            }

            LoadParam.LoadNext -> when {
                loadNext != null -> DeferredLoad(loadNext.load())
                else -> throw UnsupportedOperationException("$param is not supported")
            }
        }
    }


    // Deferred that can delegate either to actual value / error or to another deferred.
    private class DeferredSelector<T> : InflowDeferred<T> {
        private val notifier = Job()
        private val delegate = atomic<InflowDeferred<T>?>(null)
        private val cancelled = atomic<CancellationException?>(null)
        private val value = atomic<T?>(null)

        fun delegate(other: InflowDeferred<T>) {
            delegate.value = other
            notifier.complete()
        }

        fun value(data: T) {
            value.value = data
            notifier.complete()
        }

        fun cancel(exception: CancellationException) {
            cancelled.value = exception
            notifier.complete()
        }

        override suspend fun join() {
            notifier.join() // Waiting for the delegates

            delegate.value?.apply { return join() }
            cancelled.value?.apply { throw this }
        }

        override suspend fun await(): T {
            notifier.join() // Waiting for the delegates

            delegate.value?.apply { return await() }
            cancelled.value?.apply { throw this }
            @Suppress("UNCHECKED_CAST") // The value must be correct
            return value.value as T
        }
    }

    // Deferred that delegates to the loader's deferred result,
    // the actual result will be requested from the cache explicitly.
    protected inner class DeferredLoad(
        private val delegate: CompletableDeferred<Unit>
    ) : InflowDeferred<T> {
        override suspend fun join() = delegate.join()

        override suspend fun await(): T {
            // Awaiting the result (we are only interested in a thrown exception here)
            delegate.await()
            // Reading latest value directly from the original cache as the shared cache may not
            // have the latest version yet. For example the loader completed and saved the data into
            // the cache but it was not observed by the shared cache yet.
            return withContext(dispatcher) {
                // Cache errors are not expected, they should crash the scope (and the app)
                try {
                    fromCacheDirectly()
                } catch (th: Throwable) {
                    scope.launch(dispatcher) { throw th }.join()
                    throw th
                }
            }
        }
    }

}
