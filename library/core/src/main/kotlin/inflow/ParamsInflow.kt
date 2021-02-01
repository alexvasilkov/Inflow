package inflow

import inflow.internal.ParamsInflowImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Treats [this] flow as a flow of parameters and builds [Inflow]s for each parameter using
 * [ParamsInflowConfig.factory]. Resulting flow of [Inflow]s is then combined into a single
 * [Inflow] which dynamically switches to the data loaded for each new emitted parameter.
 * This is somewhat similar to [flatMapLatest] operator.
 *
 * *For example parameters flow can represent user's search query changes ("A", "An", "And", ..,
 * "Android"). For each search query we'll create a new [Inflow] which will actually load the search
 * results (with progress and errors tracking). When new search query arrives previous request will
 * be skipped and the new one will be tracked instead.*
 *
 * **Important**: parameters flow is expected to emit initial value immediately upon subscription,
 * otherwise resulting Inflow will not return any [data][Inflow.data] or [state][Inflow.state]
 * until first parameter is emitted. Use empty parameter as initial value along with [emptyInflow]
 * if there is no parameter expected in the beginning.
 *
 * [StateFlow] and [Inflow.data] are good candidates for parameters providers.
 *
 * **Important**: parameters of type `P` should provide a correct implementation of
 * [equals][Any.equals] and [hashCode][Any.hashCode] since they will be used as [Map] keys.
 * Primitive types and data classes are the best candidates.
 */
@ExperimentalCoroutinesApi
public fun <P, T> Flow<P>.toInflow(block: ParamsInflowConfig<P, T>.() -> Unit): Inflow<T> =
    ParamsInflowImpl(this, ParamsInflowConfig<P, T>().apply(block))


/**
 * Configuration params to create a new parametrized [Inflow] instance (see [toInflow]).
 *
 * It is required to provide a factory to build new [Inflow] instances either using [factory] or
 * [builder] function.
 *
 * Optional [cache] implementation can be provided to control memory usage.
 *
 * Coroutine dispatcher and coroutine scope can be optionally set with [dispatcher] and [scope].
 */
public class ParamsInflowConfig<P, T> internal constructor() {

    @JvmField
    @JvmSynthetic
    internal var factory: ((P) -> Inflow<T>)? = null

    @JvmField
    @JvmSynthetic
    internal var cache: InflowsCache<P, Inflow<T>>? = null

    @JvmField
    @JvmSynthetic
    internal var dispatcher: CoroutineDispatcher = Dispatchers.Default

    @JvmField
    @JvmSynthetic
    internal var scope: CoroutineScope? = null

    /**
     * A factory to build a new [Inflow] for particular parameter on demand.
     */
    public fun factory(factory: (P) -> Inflow<T>) {
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
    @ExperimentalCoroutinesApi
    public inline fun builder(crossinline block: InflowConfig<T>.(P) -> Unit) {
        factory { params -> inflow { block(params) } }
    }

    /**
     * Cache implementation to control how many [Inflow] instances can be stored in memory for
     * faster access.
     * Default implementation keeps up to 10 Inflow instances in memory, see [InflowsCache.create].
     */
    public fun cache(cache: InflowsCache<P, Inflow<T>>) {
        this.cache = cache
    }

    /**
     * Coroutine dispatcher that will be used to subscribe to parameters flow.
     *
     * Well written suspend functions should be safe to call from any thread ("main-safety"
     * principle). Thus you should rarely care about the dispatcher used to collect the params.
     *
     * Uses [Dispatchers.Default] by default.
     */
    public fun dispatcher(dispatcher: CoroutineDispatcher) {
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
    public fun scope(scope: CoroutineScope) {
        this.scope = scope
    }

}
