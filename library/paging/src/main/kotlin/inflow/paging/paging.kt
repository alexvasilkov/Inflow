package inflow.paging

import inflow.Inflow
import inflow.InflowCombined
import inflow.InflowDeferred
import inflow.State
import inflow.internal.InternalAccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow


/**
 * Creates a new paged [Inflow] using provided [PagedInflowConfig] configuration.
 *
 * **Usage**
 *
 * ```
 * val companies = inflowPaged<Company> {
 *     pager<String> {
 *         pageSize(20)
 *         loader { _, params ->
 *             // Loaded list must include *all* companies with name == params.key to not lose any
 *             val items = api.loadCompanies(params.count, params.key)
 *             PageResult(items, nextKey = items.lastOrNull()?.name)
 *         }
 *         identifyBy(Company::id)
 *         mergeBy(Company::name, String::compareTo, unique = false)
 *     }
 * }
 *
 * // Collect paged data and observe "refresh" and "next page load" states
 * companies.data().collect { paged -> showCompanies(paged.items, paged.hasNext) }
 * companies.refreshState().collect { state -> showRefreshState(state) }
 * companies.loadNextState().collect { state -> showLoadNextState(state) }
 *
 * // Request next page
 * companies.loadNext()
 *
 * // Refresh first page manually
 * companies.refresh()
 * ```
 *
 * **See [PagedInflowConfig] and [PagerConfig] for available configuration options.**
 */
@ExperimentalCoroutinesApi
public fun <T> inflowPaged(
    config: PagedInflowConfig<T>.() -> Unit
): Inflow<Paged<T>> = InternalAccess.createInflow(PagedInflowConfig<T>().apply(config))


/**
 * Requests next page load from a remote source.
 * The request will start immediately and can be observed using [loadNextState] flow.
 *
 * When using predefined pager (configured with [PagedInflowConfig.pager]) then only one refresh or
 * "load next" call can run at a time. If another refresh request is already running then this
 * "load next" call will wait until it finishes. If another "load next" request is already running
 * then no extra "load next" calls will be made until it finishes.
 *
 * @return Deferred object to **optionally** observe the result of the call in a suspending manner.
 */
public fun <T, P : Paged<T>> Inflow<P>.loadNext(): InflowDeferred<P> =
    InternalAccess.loadNext(this)

/**
 * State of the "load next page" process, similar to [Inflow.refreshState] but it's tracked
 * separately from refresh process.
 */
public fun <T, P : Paged<T>> Inflow<P>.loadNextState(): Flow<State> =
    InternalAccess.loadNextState(this)

/** LoadNext state as emitted by [loadNextState] flow. */
public val <T, P : Paged<T>> InflowCombined<P>.loadNext: State
    get() = InternalAccess.loadNextState(this)!! // Just making it public and non-null
