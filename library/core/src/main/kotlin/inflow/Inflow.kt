package inflow

import inflow.DataParam.AutoRefresh
import inflow.DataParam.CacheOnly
import inflow.LoadParam.Refresh
import inflow.LoadParam.RefreshForced
import inflow.LoadParam.RefreshIfExpired
import inflow.internal.InflowImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * An `Inflow` is a way to use cached data more effectively, automatically keep it up to date by
 * refreshing from a remote source and to observe refresh state.
 *
 * **Cache**
 *
 * Cached data can be observed with [data] flow. If [data] flow has at least one subscriber the
 * `Inflow` will make sure to automatically refresh the cache when it expires.
 * See [data] method for more details.
 *
 * Refresh can also be started manually by calling [load] method, as an option it can be done in
 * a suspending manner using [join()][InflowDeferred.join] or [await()][InflowDeferred.await] on the
 * result of the [load] call.
 *
 * **State**
 *
 * Refresh state can be observed using [state] flow.
 *
 * The [state] flow will emit each time the loading is started or finished and can optionally
 * emit an intermediate progress state. It will also emit the most recent error happened during the
 * last loading request (see [State.Idle.Error]).
 *
 * **Usage**
 *
 * An `Inflow` is created using [inflow][inflow.inflow] method and configured using [InflowConfig].
 *
 * A simple usage can look like this:
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
 * companies.data()
 *     .onEach(::showCompanies)
 *     .launchIn(lifecycleScope)
 *
 * companies.refreshing()
 *     .onEach(::showLoadingState)
 *     .launchIn(lifecycleScope)
 * ```
 *
 * **See [InflowConfig] for all the available configuration options.**
 */
public interface Inflow<T> {
    /**
     * Cached data collected from original cache flow configured with [InflowConfig.data].
     *
     * Original (cold or hot) flow will be subscribed automatically and shared among all active
     * subscribers. Thus no extra cache readings will be done for all subsequent subscribers and
     * they will immediately receive the most recent data.
     *
     * Original cache will be unsubscribed after predefined timeout since last active subscriber is
     * unsubscribed (see [InflowConfig.keepCacheSubscribedTimeout]).
     *
     * In other words original cache flow will be subscribed only if there is at least one active
     * subscriber and for some time after all subscribers are gone. This "keep subscribed" time
     * can be useful to avoid extra readings from original (cold) cache flow while switching app
     * screens, etc.
     *
     * The cache will be automatically updated while it has at least one subscriber using the
     * loader configured with [InflowConfig.data] and according to the expiration policy set
     * with [InflowConfig.expiration].
     * This behavior is controlled by [DataParam.AutoRefresh] and [DataParam.CacheOnly] params.
     *
     * See [data][inflow.data], [cache], [cached] extensions.
     *
     * @param param Controls the behavior of returned flow, see [DataParam].
     */
    public fun data(param: DataParam): Flow<T>

    /**
     * State of the loading process. It will emit [State.Loading] once the loading is started and
     * [State.Idle] in the end. Both loading and idle states have "sub-states".
     *
     * The very first state is guaranteed to be [State.Idle.Initial].
     *
     * An error is represented by [State.Idle.Error].
     *
     * Optionally it is possible to track the loading progress ([State.Loading.Progress]) using
     * [LoadTracker] object passed to the loader.
     *
     * Returned flow will always emit the most recent state when starting collecting.
     *
     * See [refreshState], [refreshing], [refreshError] extensions.
     *
     * @param param Controls the origin of the state events, see [StateParam].
     */
    public fun state(param: StateParam): Flow<State>

    /**
     * Manually requests data load from a remote source. The request will start immediately
     * (unless [RefreshIfExpired] param is used) and can be observed using returned deferred object.
     *
     * Only one request can run at a time, including the one that runs automatically when collecting
     * [data()][data] flow. If another request is already running it will be returned instead.
     *
     * See [refresh], [refreshIfExpired], [fresh], [refreshForced] extensions.
     *
     * @param param Controls the loading process, see [LoadParam].
     *
     * @return Deferred object with [join][InflowDeferred.join] and [await][InflowDeferred.await]
     * methods to **optionally** observe the result of the call in a suspending manner. The result
     * can still be observed with [data], [state] flows as usual.
     */
    public fun load(param: LoadParam): InflowDeferred<T>
}


/* ---------------------------------------------------------------------------------------------- */
/* Parameters                                                                                     */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Parameters for [inflow.data()][Inflow.data] method.
 */
public sealed class DataParam {
    /**
     * Returned data flow will be automatically updated while it has at least one subscriber
     * using the loader configured with [InflowConfig.data] and according to the expiration
     * policy set with [InflowConfig.expiration].
     *
     * See [data][inflow.data] extension.
     */
    public object AutoRefresh : DataParam()

    /**
     * Returned data flow will not trigger automatic update and will just return the flow of
     * cached data.
     *
     * See [cache] and [cached] extensions.
     */
    public object CacheOnly : DataParam()
}

/**
 * Parameters for [inflow.state()][Inflow.state§] method.
 */
public sealed class StateParam {
    /**
     * Returns the state of refresh calls, triggered with
     * [inflow.load(LoadParam.Refresh)][Inflow.load].
     *
     * See [refreshState], [refreshing], [refreshError] extensions.
     */
    public object Refresh : StateParam()
}

/**
 * Parameters for [inflow.load()][Inflow.load] method.
 */
public sealed class LoadParam {
    /**
     * The refresh will be done immediately.
     * If another refresh is currently in progress then no new refresh will be called.
     *
     * See [refresh] extension.
     */
    public object Refresh : LoadParam()

    /**
     * The refresh will only be requested if the latest cached value is expiring in less than
     * [expiresIn] milliseconds according to [InflowConfig.expiration] policy.
     * In other words if [expiresIn] is > 0 then a not-yet-expired data will be updated if it
     * expires sooner than we want.
     * If [expiresIn] is set to 0 then only already expired data will be refreshed.
     *
     * For example, if cached data expires in 5 minutes and [expiresIn] is set to 2 minutes then
     * no refresh will be done and the cached data will be returned as-is. But if [expiresIn] is
     * set to 10 minutes then a new refresh request will be triggered.
     *
     * See [refreshIfExpired] and [fresh] extensions.
     */
    public class RefreshIfExpired(
        @JvmField
        public val expiresIn: Long
    ) : LoadParam() {
        init {
            require(expiresIn >= 0L) { "Value of 'expiresIn' must be >= 0" }
        }
    }

    /**
     * If set and another refresh is currently in place then extra refresh will be done again right
     * after the current one. No extra states will be emitted by [inflow.state()][Inflow.state]
     * until this extra request completes.
     *
     * It can be useful in situations when remote data was changed (e.g. because of POST or PUT
     * request) and we need to ensure that newly loaded data reflects that changes. Otherwise
     * previous refresh may return stale data.
     *
     * See [refreshForced] extension.
     */
    public object RefreshForced : LoadParam()
}


/* ---------------------------------------------------------------------------------------------- */
/* Builders                                                                                       */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Creates a new [Inflow] using provided [InflowConfig] configuration.
 */
@ExperimentalCoroutinesApi
public fun <T> inflow(block: InflowConfig<T>.() -> Unit): Inflow<T> =
    InflowImpl(InflowConfig<T>().apply(block))


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
public fun <T> emptyInflow(initial: T): Inflow<T> = object : Inflow<T> {
    override fun data(param: DataParam) = flowOf(initial)
    override fun state(param: StateParam) = flowOf(State.Idle.Initial)
    override fun load(param: LoadParam) = object : InflowDeferred<T> {
        override suspend fun await() = initial
        override suspend fun join() = Unit
    }
}


/* ---------------------------------------------------------------------------------------------- */
/* Extensions                                                                                     */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Returns cache flow that will be automatically updated while subscribed.
 *
 * Shortcut for `data(DataParam.AutoRefresh)`.
 *
 * See [DataParam.AutoRefresh].
 */
public fun <T> Inflow<T>.data(): Flow<T> = data(AutoRefresh)

/**
 * Returns cache flow that won't be automatically updated.
 *
 * Shortcut for `data(DataParam.CacheOnly)`.
 *
 * See [DataParam.CacheOnly].
 */
public fun <T> Inflow<T>.cache(): Flow<T> = data(CacheOnly)

/**
 * Returns latest cached data without trying to refresh it.
 *
 * Shortcut for `data(DataParam.CacheOnly).first()`.
 */
public suspend fun <T> Inflow<T>.cached(): T = data(CacheOnly).first()


/**
 * Manually requests data refresh.
 *
 * Shortcut for `load(LoadParam.Refresh)`.
 *
 * See [LoadParam.Refresh].
 */
public fun <T> Inflow<T>.refresh(): InflowDeferred<T> = load(Refresh)

/**
 * If that latest cached data is expiring in more than [expiresIn] milliseconds then it won't be
 * refreshed. Otherwise a new request will be triggered.
 *
 * Shortcut for `load(LoadParam.RefreshIfExpired(expiresIn))`.
 *
 * See [LoadParam.RefreshIfExpired].
 */
public fun <T> Inflow<T>.refreshIfExpired(expiresIn: Long = 0L): InflowDeferred<T> =
    load(RefreshIfExpired(expiresIn))

/**
 * If the latest cached data is expiring in more than [expiresIn] milliseconds then it will be
 * returned as-is. Otherwise a new request will be triggered and its result will be returned.
 *
 * **Important: this method will throw an exception if the request fails.**
 *
 * Shortcut for `load(LoadParam.RefreshIfExpired(expiresIn)).await()`.
 *
 * See [LoadParam.RefreshIfExpired].
 */
public suspend fun <T> Inflow<T>.fresh(expiresIn: Long = 0L): T =
    load(RefreshIfExpired(expiresIn)).await()

/**
 * If another refresh is currently in place then extra refresh will be done again right after the
 * current one.
 *
 * Shortcut for `load(LoadParam.RefreshForced)`.
 *
 * See [LoadParam.RefreshForced].
 */
public fun <T> Inflow<T>.refreshForced(): InflowDeferred<T> = load(RefreshForced)


/**
 * Returns the state of refresh process.
 *
 * Shortcut for `state(StateParam.Refresh)`.
 *
 * See [StateParam.Refresh].
 */
public fun <T> Inflow<T>.refreshState(): Flow<State> = state(StateParam.Refresh)

/**
 * Provides a simple flow of `false` (if [State.Idle]) and `true` (if [State.Loading]) values for
 * refreshing process.
 */
public fun <T> Inflow<T>.refreshing(): Flow<Boolean> = state(StateParam.Refresh)
    .map { it !is State.Idle }
    .distinctUntilChanged()

/**
 * Returns a flow of unhandled exceptions. Exception is considered handled if it was collected at
 * least once. If an exception is handled then all other collectors won't receive it anymore.
 *
 * Can be used to ensure that each error is shown to the user only once.
 */
public fun <T> Inflow<T>.refreshError(): Flow<Throwable> = state(StateParam.Refresh).mapNotNull {
    val error = it as? State.Idle.Error
    if (error != null && error.markHandled(error)) error.throwable else null
}
