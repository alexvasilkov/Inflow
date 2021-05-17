package inflow

import inflow.internal.InflowImpl
import inflow.internal.MergedInflowImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Creates a new [Inflow] using provided [InflowConfig] configuration.
 *
 * **Usage**
 *
 * ```
 * val companies = inflow<List<Companies>?> {
 *     data(
 *         cache = dao.getCompaniesList()
 *         writer = dao::saveCompaniesList
 *         loader = { api.loadCompaniesList() }
 *     )
 * }
 *
 * // By subscribing to companies data it will be automatically loaded and cached
 * companies.data().collect { list -> showCompanies(list) }
 *
 * // Observing companies loading state
 * companies.refreshing().collect { isLoading -> showLoadingState(isLoading) }
 * ```
 *
 * **See [InflowConfig] for available configuration options.**
 */
@ExperimentalCoroutinesApi
public fun <T> inflow(
    config: InflowConfig<T>.() -> Unit
): Inflow<T> = InflowImpl(InflowConfig<T>().apply(config))

/**
 * Creates a new [Inflows] instance which will build new [Inflow]s for specific parameters on
 * demand using [factory], and will cache them using provided [cache] implementation.
 *
 * Also see [mergeBy] function that allows converting a flow of parameters into a single [Inflow].
 *
 * **Usage**
 *
 * ```
 * fun getCompany(id: String) = inflow<Company?> {
 *     // Loading a company by ID and caching it in memory
 *     data(initial = null) { api.loadCompany(id) }
 * }
 *
 * val companies = inflows(factory = ::getCompany)
 *
 * // Requesting a company with ID "42" and observing the result
 * companies["42"].data().collect { company -> showCompany(company) }
 * ```
 */
public fun <P, T> inflows(
    factory: (P) -> Inflow<T>,
    cache: InflowsCache<P, Inflow<T>> = InflowsCache.create()
): Inflows<P, T> = InflowsImpl(factory, cache)

/**
 * Uses current [Inflows] instance as a source of [Inflow] items for each parameter. Provided
 * [params] flow is transformed into a flow of [Inflow]s and then merged into a single [Inflow]
 * that emits data corresponding to the latest emitted parameter.
 * This is somewhat similar to [flatMapLatest] operator. See usage section.
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
 *
 *  **Usage**
 *
 * ```
 * fun searchCompanies(query: String) = inflow<List<Company>?> {
 *     // Searching companies and caching the result in memory
 *     data(initial = null) { api.searchCompanies(query) }
 * }
 *
 * val searchRequests = inflows(factory = ::searchCompanies)
 *
 * val query = MutableStateFlow("")
 * // Set user's search query while typing, for example:
 * // query.value = "Goo"
 * // query.value = "Goog"
 * // query.value = "Google"
 *
 * // Combining different requests into a single Inflow and observing the result
 * searchRequests.mergeBy(query).data().collect { list -> showSearchResults(list) }
 * ```
 */
@ExperimentalCoroutinesApi
public fun <P, T> Inflows<P, T>.mergeBy(
    params: Flow<P>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    scope: CoroutineScope = CoroutineScope(Job())
): Inflow<T> = MergedInflowImpl(this, params, dispatcher, scope)


@Suppress("UNCHECKED_CAST")
public fun <T> emptyInflow(): Inflow<T?> = emptyInflow(null)

/**
 * Creates an [Inflow] that emits [initial] value (by contract an Inflow should always emit) and
 * does not load any extra data.
 */
public fun <T> emptyInflow(initial: T): Inflow<T> = object : Inflow<T>() {
    override fun dataInternal(param: DataParam) = flow {
        emit(initial)
        awaitCancellation()
    }

    override fun stateInternal(param: StateParam) = flow {
        emit(State.Idle.Initial)
        awaitCancellation()
    }

    override fun loadInternal(param: LoadParam) = object : InflowDeferred<T> {
        override suspend fun await() = initial
        override suspend fun join() = Unit
    }

    override fun combineInternal(param: DataParam) = flow {
        emit(InflowCombined(initial, State.Idle.Initial, State.Idle.Initial))
        awaitCancellation()
    }
}
