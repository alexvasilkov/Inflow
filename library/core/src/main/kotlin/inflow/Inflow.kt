package inflow

import inflow.DataParam.AutoRefresh
import inflow.DataParam.CacheOnly
import inflow.LoadParam.Refresh
import inflow.LoadParam.RefreshForced
import inflow.LoadParam.RefreshIfExpired
import inflow.paging.Paged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * An `Inflow` is a way to use cached data more effectively, automatically keep it up to date by
 * refreshing from a remote source and to observe refresh state.
 *
 * **Cache**
 *
 * Cached data can be observed with [cache] or [data] flows.
 * If [data] flow has at least one subscriber then this `Inflow` will make sure to automatically
 * refresh the cache when it expires.
 *
 * Refresh can also be started manually by calling [refresh] method, as an option it can be done in
 * a suspending manner using [join()][InflowDeferred.join] or [await()][InflowDeferred.await] on the
 * result of the [refresh] call.
 *
 * **State**
 *
 * Refresh state can be observed using [refreshState] flow.
 *
 * The [refreshState] flow will emit each time the loading is started or finished and can optionally
 * emit an intermediate progress state. It will also emit the most recent error happened during the
 * last loading request (see [State.Idle.Error]).
 */
public abstract class Inflow<T> {

    /**
     * Cached data collected from the original cache flow configured with [InflowConfig.data].
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
     * screens or frequently accessing the cache.
     *
     * Also see [cached] extension.
     */
    public fun cache(): Flow<T> = dataInternal(CacheOnly)

    /**
     * Similar to [cache] but the cache will be automatically updated while this flow has at least
     * one subscriber. It will use the loader configured with [InflowConfig.data] and will use the
     * expiration policy set with [InflowConfig.expiration] to schedule automatic refresh.
     */
    public fun data(): Flow<T> = dataInternal(AutoRefresh)

    /**
     * Manually requests data load from a remote source using the loader configured with
     * [InflowConfig.data]. The request will start immediately and can be observed using [cache] and
     * [refreshState] flows or using the returned deferred object.
     *
     * Only one refresh can run at a time, including automatic refresh triggered by subscription to
     * [data] flow. If another request is already running then no new refresh will be triggered and
     * returned deferred object will represent the currently running refresh. Extra refresh can be
     * enforced using [force] param.
     *
     * @param force If set to true and another refresh is currently in place then extra refresh will
     * be done again right after the current one.
     *
     * It can be useful in situations when remote data was changed (e.g. because of POST or PUT
     * request) and we need to ensure that newly loaded data reflects that changes. Otherwise
     * currently running refresh may return stale data.
     *
     * Default value is `false`.
     *
     * @return Deferred object with [join][InflowDeferred.join] and [await][InflowDeferred.await]
     * methods to **optionally** observe the result of the call in a suspending manner.
     */
    public fun refresh(force: Boolean = false): InflowDeferred<T> =
        loadInternal(if (force) RefreshForced else Refresh)

    /**
     * Similar to [refresh] but the refresh will only be requested if the latest cached value is
     * expiring in less than [expiresIn] milliseconds according to [InflowConfig.expiration] policy.
     *
     * In other words if [expiresIn] is > 0 then a not-yet-expired data will be refreshed anyway if
     * it expires sooner the requested [expiresIn] time.
     * If [expiresIn] is set to 0 then refresh will only be called if cached data is expired.
     *
     * For example, if cached data expires in 5 minutes and [expiresIn] is set to 2 minutes then
     * no refresh will be done and the cached data will be returned as-is. But if [expiresIn] is
     * set to 10 minutes then a new refresh request will be triggered because cached data will be
     * expired after 10 minutes.
     *
     * Also see [fresh] extension.
     */
    public fun refreshIfExpired(expiresIn: Long = 0L): InflowDeferred<T> =
        loadInternal(RefreshIfExpired(expiresIn))

    /**
     * State of the refresh process. It will emit [State.Loading] once the loading is started and
     * [State.Idle] in the end. Both loading and idle states have "sub-states".
     *
     * The very first state is guaranteed to be [State.Idle.Initial].
     *
     * An error is represented by [State.Idle.Error].
     *
     * Optionally it is possible to track the loading progress with [State.Loading.Progress] using
     * [LoadTracker] object passed to the loader.
     *
     * Returned flow will always emit the most recent state when starting collecting.
     *
     * Also see [refreshing] and [refreshError] extensions.
     */
    public fun refreshState(): Flow<State> = stateInternal(StateParam.RefreshState)


    /**
     * Internal method to get cached data flow, see [DataParam].
     */
    internal abstract fun dataInternal(param: DataParam): Flow<T>

    /**
     * Internal method to observe loading state, see [StateParam].
     */
    internal abstract fun stateInternal(param: StateParam): Flow<State>

    /**
     * Internal method to trigger data loading, see [LoadParam].
     */
    internal abstract fun loadInternal(param: LoadParam): InflowDeferred<T>

}


/* ---------------------------------------------------------------------------------------------- */
/* Parameters                                                                                     */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Parameters for [Inflow.dataInternal] method.
 */
internal sealed class DataParam {
    /**
     * Returned cache flow should not trigger automatic updates. See [Inflow.cache].
     */
    object CacheOnly : DataParam()

    /**
     * Returned cache flow should be automatically updated while subscribed. See [Inflow.data].
     */
    object AutoRefresh : DataParam()
}

/**
 * Parameters for [Inflow.stateInternal] method.
 */
internal sealed class StateParam {
    /**
     * Returns the state of refresh calls, triggered with [Inflow.refresh].
     */
    object RefreshState : StateParam()

    /**
     * Returns the state of "load next page" calls, triggered with [Inflow.loadNext][loadNext].
     */
    object LoadNextState : StateParam()
}

/**
 * Parameters for [Inflow.loadInternal] method.
 */
internal sealed class LoadParam {
    /**
     * Triggers refresh call. See [Inflow.refresh].
     */
    object Refresh : LoadParam()

    /**
     * Triggers "load next page" call. See [Inflow.loadNext][loadNext].
     */
    object LoadNext : LoadParam()

    /**
     * Triggers extra refresh call even if another refresh is in progress. See [Inflow.refresh].
     */
    object RefreshForced : LoadParam()

    /**
     * Refresh is only triggered if the latest cached value is expiring in less than [expiresIn]
     * milliseconds. See [Inflow.refreshIfExpired].
     */
    class RefreshIfExpired(@JvmField val expiresIn: Long) : LoadParam() {
        init {
            require(expiresIn >= 0L) { "Value of 'expiresIn' must be >= 0" }
        }
    }
}


/* ---------------------------------------------------------------------------------------------- */
/* Extensions                                                                                     */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Returns latest cached data without trying to refresh it.
 * Shortcut for [`cache()`][Inflow.cache]`.first()`.
 */
public suspend fun <T> Inflow<T>.cached(): T = cache().first()

/**
 * If the latest cached data is expiring in more than [expiresIn] milliseconds then it will be
 * returned as-is. Otherwise a new request will be triggered and its result will be returned.
 *
 * **Important: this method will throw an exception if the request fails.**
 *
 * Shortcut for [`refreshIfExpired(expiresIn)`][Inflow.refreshIfExpired]`.await()`.
 */
public suspend fun <T> Inflow<T>.fresh(expiresIn: Long = 0L): T =
    refreshIfExpired(expiresIn).await()

/**
 * Provides a simple flow of `false` (if [State.Idle]) and `true` (if [State.Loading]) values based
 * on [`refreshState()`][Inflow.refreshState].
 */
public fun <T> Inflow<T>.refreshing(): Flow<Boolean> = refreshState()
    .map { it !is State.Idle }
    .distinctUntilChanged()

/**
 * Returns a flow of unhandled exceptions. An exception is considered handled if it was already
 * collected by any subscriber.
 * If an exception is handled then all other subscribers won't receive it anymore.
 *
 * Can be used to ensure that each error is shown to the user only once.
 */
public fun <T> Inflow<T>.refreshError(): Flow<Throwable> = refreshState()
    .mapNotNull {
        val error = it as? State.Idle.Error
        if (error != null && error.markHandled(error)) error.throwable else null
    }


/**
 * Requests next page load from a remote source.
 * The request will start immediately and can be observed using [loadNextState] flow.
 *
 * Only one refresh or "load next" call can run at a time. If another refresh request is already
 * running then this "load next" call will wait until it finishes. If another "load next" request
 * is already running the no extra "load next" calls will be made until it finishes.
 *
 * @return Deferred object to **optionally** observe the result of the call in a suspending manner.
 */
// TODO: Move all public paging API into `paging` package?
public fun <T, P : Paged<T>> Inflow<P>.loadNext(): InflowDeferred<P> =
    loadInternal(LoadParam.LoadNext)

/**
 * State of the "load next page" process, similar to [Inflow.refreshState] but it's tracked
 * separately from refresh process.
 */
public fun <T, P : Paged<T>> Inflow<P>.loadNextState(): Flow<State> =
    stateInternal(StateParam.LoadNextState)
