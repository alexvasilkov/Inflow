package inflow

import inflow.internal.share
import inflow.utils.doOnCancel
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Treats [this] flow as a flow of parameters and builds [Inflow]s for each parameter using
 * [InflowsCombinedConfig.factory]. Resulting flow of [Inflow]s is then combined into a single
 * [Inflow] which dynamically switches to the data loaded for each new emitted parameter.
 * This is somewhat similar to [flatMapLatest] operator.
 *
 * *For example parameters flow can represent user's search query changes ("A", "An", "And", ..,
 * "Android"). For each search query we'll create a new [Inflow] which will actually load the search
 * results (with progress and errors tracking). When new search query arrives previous request will
 * be skipped and the new one will be tracked instead.*
 *
 * **Important**: Parameters flow is expected to emit initial value immediately upon subscription,
 * otherwise resulting Inflow will not return any [data][Inflow.data], [progress][Inflow.progress]
 * or [error][Inflow.error] until first parameter is emitted. Use empty parameter as initial value
 * along with [emptyInflow] if there is no parameter expected in the beginning.
 *
 * [StateFlow] or [Inflow.data] are best candidates for parameters providers.
 */
fun <P, T> Flow<P>.asInflow(block: InflowsCombinedConfig<P, T>.() -> Unit): Inflow<T> =
    InflowsCombined(this, InflowsCombinedConfig<P, T>().apply(block))


/**
 * Configuration params to create a new combined [Inflow] instance (see [asInflow]).
 *
 * It is required to provide a factory to build new [Inflow] instances either using [factory] or
 * [builder] function.
 *
 * Optional [cache] implementation can be provided to control memory usage.
 *
 * Coroutine dispatcher and coroutine scope can be optionally set with [dispatcher] and [scope].
 */
class InflowsCombinedConfig<P, T> internal constructor() {

    @JvmField
    @JvmSynthetic
    internal var factory: ((P) -> Inflow<T>)? = null

    @JvmField
    @JvmSynthetic
    internal var cache: InflowsCache<P, Inflow<T>>? = null

    @JvmField
    @JvmSynthetic
    internal var dispatcher: CoroutineDispatcher = Dispatchers.IO

    @JvmField
    @JvmSynthetic
    internal var scope: CoroutineScope? = null

    /**
     * A factory to build a new [Inflow] for particular parameter on demand.
     */
    fun factory(factory: (P) -> Inflow<T>) {
        this.factory = factory
    }

    /**
     * [Inflow] builder which configures new instance for a specific parameter on demand.
     * Has similar signature to [inflow] method except that it will also accept a parameter.
     *
     * It's a syntactic sugar for [factory], the next configurations are equivalent:
     *
     * ```
     * builder { param ->
     *     ...
     * }
     * ```
     * ```
     * factory { param ->
     *     inflow {
     *         ...
     *     }
     * }
     * ```
     */
    @JvmSynthetic // Avoiding coverage report issues
    inline fun builder(crossinline block: InflowConfig<T>.(P) -> Unit) {
        factory { params -> inflow { block(params) } }
    }

    /**
     * Cache implementation to control how many [Inflow] instances can be stored in memory for
     * faster access.
     * Default implementation keeps up to 10 Inflow instances in memory, see [inflowsCache].
     */
    fun cache(cache: InflowsCache<P, Inflow<T>>) {
        this.cache = cache
    }

    /**
     * Coroutine dispatcher that will be used to subscribe to parameters flow.
     *
     * Uses [Dispatchers.Unconfined] by default, assuming that parameters flow is non-blocking and
     * because the actual requests dispatching is done inside the [Inflow]s.
     *
     * Any suspending code used in parameters flow will enforce [Dispatchers.Default] usage.
     */
    fun dispatcher(dispatcher: CoroutineDispatcher) {
        this.dispatcher = dispatcher
    }

    /**
     * Coroutine scope used for parameters flow subscription.
     * Can be used to cancel parameters subscription or handle uncaught errors.
     *
     * It is generally unnecessary to manually cancel the resulting Inflow as it will not have any
     * hanging jobs once it has no active subscribers.
     *
     * Also note that this scope is different from [InflowConfig.scope] and cannot be used to
     * cancel actual loading or cache subscription.
     */
    fun scope(scope: CoroutineScope) {
        this.scope = scope
    }

}


/**
 * [Inflow] implementation that delegates to other Inflows created dynamically for each param from
 * params flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class InflowsCombined<P, T>(
    params: Flow<P>,
    config: InflowsCombinedConfig<P, T>
) : Inflow<T> {

    private val scope = config.scope ?: CoroutineScope(Job())
    private val dispatcher = config.dispatcher
    private val inflows = inflows<P, T> {
        config.factory?.let(::factory)
        config.cache?.let(::cache)
    }

    private val shared = params
        .map(inflows::get) // Creating a new Inflow for each parameter or using cached one
        .share(scope, dispatcher, 0L) // Sharing the flow to reuse params subscription

    override fun data(vararg params: DataParam) = shared
        .flatMapLatest { it.data(*params) }

    override fun progress() = shared
        .flatMapLatest(Inflow<T>::progress)
        .distinctUntilChanged() // Avoiding [Idle, Idle] sequences

    override fun error() = shared
        .flatMapLatest(Inflow<T>::error)
        .distinctUntilChanged { old, new -> old == null && new == null } // No subsequent nulls

    override fun refresh(vararg params: RefreshParam): InflowDeferred<T> {
        val deferred = DeferredDelegate<T>()
        val job = scope.launch(dispatcher) {
            deferred.delegateTo(shared.first().refresh(*params))
        }
        // If scope is cancelled then we need to notify our deferred object
        job.doOnCancel(deferred::onCancelled)
        return deferred
    }

}

/**
 * Waits for the delegate and then delegates the actual [await] and [join] to it.
 */
private class DeferredDelegate<T> : InflowDeferred<T> {

    private val delegate = atomic<InflowDeferred<T>?>(null)
    private val cancellationException = atomic<CancellationException?>(null)
    private val notifier = Job()

    fun delegateTo(other: InflowDeferred<T>) {
        delegate.value = other
        notifier.complete()
    }

    fun onCancelled(cause: CancellationException) {
        cancellationException.value = cause
        notifier.cancel(cause)
    }

    override suspend fun await(): T {
        notifier.join()
        cancellationException.value?.let { throw it }
        return delegate.value!!.await()
    }

    override suspend fun join() {
        notifier.join()
        delegate.value!!.join()
    }

}
