package inflow

import inflow.internal.InflowImpl
import inflow.internal.MergedInflowImpl
import inflow.paging.Paged
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Creates a new [Inflow] using provided [InflowConfig] configuration.
 */
@ExperimentalCoroutinesApi
public fun <T> inflow(
    block: InflowConfig<T>.() -> Unit
): Inflow<T> = InflowImpl(InflowConfig<T>().apply(block))


private val emptyInflow: Inflow<Any?> by lazy { emptyInflow(null) }

/**
 * Creates an [Inflow] that emits `null` and does not load any extra data.
 */
@Suppress("UNCHECKED_CAST")
public fun <T> emptyInflow(): Inflow<T?> = emptyInflow as Inflow<T?>

/**
 * Creates an [Inflow] that emits [initial] value (by contract an Inflow should always emit) and
 * does not load any extra data.
 */
public fun <T> emptyInflow(initial: T): Inflow<T> = object : Inflow<T>() {
    override fun dataInternal(param: DataParam) = flowOf(initial)
    override fun stateInternal(param: StateParam) = flowOf(State.Idle.Initial)
    override fun loadInternal(param: LoadParam) = object : InflowDeferred<T> {
        override suspend fun await() = initial
        override suspend fun join() = Unit
    }
}


// TODO: Add info
@ExperimentalCoroutinesApi
public fun <T> inflowPaged(
    block: PagedInflowConfig<T>.() -> Unit
): Inflow<Paged<T>> = InflowImpl(PagedInflowConfig<T>().apply(block))


/**
 * Creates a new [Inflows] instance.
 * // TODO: Add more info
 */
public fun <P, T> inflows(
    factory: (P) -> Inflow<T>,
    cache: InflowsCache<P, Inflow<T>> = InflowsCache.create()
): Inflows<P, T> = InflowsImpl(factory, cache)

/**
 * Uses current [Inflows] instance as a source of [Inflow] items for each parameter. Resulting
 * sequence of [Inflow]s is then merged into a single [Inflow] which dynamically switches to the
 * data loaded for each new parameter. This is somewhat similar to [flatMapLatest] operator.
 *
 * *For example parameters flow can represent user's search query changes ("A", "An", "And", ..,
 * "Android"). For each search query we'll create a new [Inflow] which will actually load the search
 * results (with dedicated progress tracking). When new search query arrives previous request will
 * be unsubscribed and the new one will be subscribed instead.*
 *
 * **Important**: parameters flow is expected to emit initial value immediately upon subscription,
 * otherwise resulting Inflow will not return anything from its [cache][Inflow.cache] and
 * [refreshState][Inflow.refreshState] flows until first parameter is emitted. Use empty parameter
 * as initial value along with [emptyInflow] if there is no parameter expected in the beginning.
 *
 * [StateFlow] and [Inflow.cache]/[Inflow.data] are good candidates for parameters providers.
 *
 * **Important**: parameters of type `P` should provide a correct implementation of
 * [equals][Any.equals] and [hashCode][Any.hashCode] since they will be used as [Map] keys.
 * Primitive types and data classes are the best candidates.
 */
@ExperimentalCoroutinesApi
public fun <P, T> Inflows<P, T>.merge(
    params: Flow<P>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    scope: CoroutineScope = CoroutineScope(Job())
): Inflow<T> = MergedInflowImpl(this, params, dispatcher, scope)
