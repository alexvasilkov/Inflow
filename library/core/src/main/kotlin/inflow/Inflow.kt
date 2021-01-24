package inflow

import inflow.DataParam.CacheOnly
import inflow.ErrorParam.SkipIfCollected
import inflow.RefreshParam.IfExpiresIn
import inflow.RefreshParam.Repeat
import inflow.internal.InflowImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * An `Inflow` is a way to use cached data more effectively, automatically keep it up to date by
 * refreshing from a remote source and to observe refresh state.
 *
 * **Cache**
 *
 * Cached data can be observed with [data] flow. If [data] flow has at least one subscriber the
 * `Inflow` will make sure to keep the cache up to date and automatically refresh when it expires.
 * See [data] method for more details.
 *
 * Refresh can also be started manually by calling [refresh] method, as an option it can be done in
 * a suspending manner using [join][InflowDeferred.join] or [await][InflowDeferred.await] on the
 * result of the [refresh] call.
 *
 * **State**
 *
 * Refresh state can be observed using [progress] and [error] flows.
 *
 * The [progress] flow will emit each time the loading is started or finished and can optionally
 * emit an intermediate progress state. Also see [loading] extension.
 *
 * The [error] flow will emit the most recent error happened during last refresh or `null` if last
 * request was successful or still in progress.
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
 * companies.loading()
 *     .onEach(::showLoading)
 *     .launchIn(lifecycleScope)
 * ```
 *
 * **See [InflowConfig] for all the available configuration options.**
 */
public interface Inflow<T> {
    /**
     * Cached data collected from original cache flow provided with [InflowConfig.data].
     *
     * Original (cold or hot) flow will be subscribed automatically and shared among all active
     * subscribers. Thus no extra cache readings will be done for all subsequent subscribers and
     * they will immediately receive the most recent cache data.
     * Original cache will be unsubscribed after predefined timeout since last active subscriber is
     * unsubscribed (see [InflowConfig.keepCacheSubscribedTimeout]).
     *
     * In other words original cache flow will be subscribed only if there is at least one active
     * subscriber and for some time after all subscribers are gone. This "keep subscribed" time
     * can be useful to avoid extra readings from original (cold) cache flow while switching app
     * screens, etc.
     *
     * The cache will be automatically kept fresh using the loader provided with [InflowConfig.data]
     * while it has at least one subscriber and according to expiration policy set with
     * [InflowConfig.expiration]. This behavior can be disabled by using [CacheOnly] param.
     *
     * See [cache()][cache] and [cached()][cached] extensions.
     *
     * @param params Optional parameters, see [DataParam].
     * If parameter of the same type is passed several times then only first parameter will be used.
     */
    public fun data(vararg params: DataParam): Flow<T>

    /**
     * Current loading progress.
     * It will emit [Progress.Active] once the loading is started and [Progress.Idle] in the end.
     * Optionally it is also possible to track loading state ([Progress.State]) using
     * [LoadTracker] object passed to the loader set in [InflowConfig.data].
     * Only one request can run at a time.
     *
     * It will always repeat the most recent state when starting collecting.
     *
     * Note: if a new refresh is explicitly requested with [refresh] and [Repeat] param
     * while running another refresh, the new request will run immediately after first request is
     * finished without emitting consequent [Progress.Active] and [Progress.Idle] values.
     *
     * See [loading()][loading] extension.
     */
    public fun progress(): Flow<Progress>

    /**
     * Recent error caught during refresh requests or `null` if latest request was successful.
     * Will be set back to `null` immediately after new refresh is started.
     *
     * It will always repeat the most recent error when starting collecting.
     * Use [unhandledError()][unhandledError] extension to avoid showing duplicate errors.
     */
    public fun error(vararg params: ErrorParam): Flow<Throwable?>

    /**
     * Manually requests data refresh from a remote source. The request will start immediately
     * (unless [IfExpiresIn] param is used) and can be observed using returned deferred object.
     *
     * See [refreshIfExpired()][refreshIfExpired], [fresh()][fresh], [forceRefresh()][forceRefresh]
     * extensions.
     *
     * @param params Optional parameters for refresh request, see [RefreshParam].
     * If parameter of the same type is passed several times then only first parameter will be used.
     *
     * @return Deferred object with [join][InflowDeferred.join] and [await][InflowDeferred.await]
     * methods to **optionally** observe the result of the call in a suspending manner. The result
     * can still be observed with [data], [progress] and [error] flows as usual.
     */
    public fun refresh(vararg params: RefreshParam): InflowDeferred<T>
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


@ExperimentalCoroutinesApi
private val emptyInflow: Inflow<Any?> by lazy { emptyInflow(null) }

/**
 * Creates an [Inflow] that emits `null` and does not load any extra data.
 */
@Suppress("UNCHECKED_CAST")
@ExperimentalCoroutinesApi
public fun <T> emptyInflow(): Inflow<T?> = emptyInflow as Inflow<T?>

/**
 * Creates an [Inflow] that emits [initial] value (by contract an Inflow should always emit) and
 * does not load any extra data.
 */
@ExperimentalCoroutinesApi
public fun <T> emptyInflow(initial: T): Inflow<T> = inflow {
    data(cache = flowOf(initial), loader = {})
    expiration(Expires.never())
    keepCacheSubscribedTimeout(0L)
    retryTime(Long.MAX_VALUE)
    connectivity(null)
    cacheDispatcher(Dispatchers.Unconfined)
    loadDispatcher(Dispatchers.Unconfined)
    scope(GlobalScope)
}


/* ---------------------------------------------------------------------------------------------- */
/* Parameters                                                                                     */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Parameters for [Inflow.data] method: [CacheOnly].
 */
public sealed class DataParam {
    /**
     * Returned data flow will not trigger automatic data refresh and will just return the flow of
     * cached data.
     *
     * See [cache()][cache] and [cached()][cached] extensions.
     */
    public object CacheOnly : DataParam()
}

/**
 * Parameters for [Inflow.error] method: [SkipIfCollected].
 */
public sealed class ErrorParam {
    /**
     * If an error was already collected once then all other collectors will not receive it anymore
     * (they will get `null` instead).
     *
     * Can be used to ensure that each error is shown to the user only once.
     *
     * Note that errors collected without this parameter (i.e. just collecting `error()` flow) will
     * not be considered as handled.
     *
     * See [unhandledError()][unhandledError] extension.
     */
    public object SkipIfCollected : ErrorParam()
}

/**
 * Parameters for [Inflow.refresh] method: [Repeat], [IfExpiresIn].
 */
public sealed class RefreshParam {
    /**
     * If set and another refresh is currently in place then extra refresh will be done again right
     * after the current one. No error or progress (except optional [Progress.State]) events will be
     * emitted until this extra request completes.
     *
     * It can be useful in situations when remote data was changed (e.g. because of POST or PUT
     * request) and we need to ensure that newly loaded data reflects that changes. Otherwise
     * previous refresh may return stale data.
     *
     * See [forceRefresh()][forceRefresh] extension.
     */
    public object Repeat : RefreshParam()

    /**
     * The refresh will only be requested if the latest cached value is expiring in less than
     * [expiresIn] milliseconds according to [InflowConfig.expiration] policy. In other words
     * if [expiresIn] is > 0 then it will allow to refresh a not-yet-expired value which will expire
     * sooner than we want. If [expiresIn] is set to 0 then only expired values will be refreshed.
     *
     * For example if cached value expires in 5 minutes and [expiresIn] is set to 2 minutes then
     * no refresh will be done and the cached value will be returned as-is. But if [expiresIn] is
     * set to 10 minutes then a new refresh request will be triggered.
     *
     * See [refreshIfExpired()][refreshIfExpired] and [fresh()][fresh] extensions.
     */
    public data class IfExpiresIn(val expiresIn: Long) : RefreshParam() {
        init {
            require(expiresIn >= 0L) { "Value of 'expiresIn' must be >= 0" }
        }
    }
}


/* ---------------------------------------------------------------------------------------------- */
/* Extensions                                                                                     */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Returns cache flow, shortcut for `data(DataParam.CacheOnly)`
 */
public fun <T> Inflow<T>.cache(): Flow<T> = data(CacheOnly)

/**
 * Returns latest cached data without trying to refresh it.
 *
 * Shortcut for `data(DataParam.CacheOnly).first()`.
 */
public suspend fun <T> Inflow<T>.cached(): T = data(CacheOnly).first()

/**
 * If latest cached data is expiring in more than [expiresIn] milliseconds then it will be returned
 * as-is. Otherwise a new request will be triggered and its result will be returned.
 * See [IfExpiresIn].
 *
 * **Important: this method will throw an exception if the request is failed.**
 *
 * Shortcut for `refresh(RefreshParam.IfExpiresIn(expiresIn)).await()`.
 */
public suspend fun <T> Inflow<T>.fresh(expiresIn: Long = 0L): T =
    refresh(IfExpiresIn(expiresIn)).await()

/**
 * If another refresh is currently in place then extra refresh will be done again right after the
 * current one. See [Repeat].
 *
 * Shortcut for `refresh(RefreshParam.Repeat)`.
 */
public fun <T> Inflow<T>.forceRefresh(): InflowDeferred<T> = refresh(Repeat)

/**
 * If latest cached data is expiring in more than [expiresIn] milliseconds then it will be returned
 * as-is. Otherwise a new request will be triggered. See [IfExpiresIn].
 *
 * Shortcut for `refresh(RefreshParam.IfExpiresIn(expiresIn))`.
 */
public fun <T> Inflow<T>.refreshIfExpired(expiresIn: Long = 0L): InflowDeferred<T> =
    refresh(IfExpiresIn(expiresIn))

/**
 * If an error was already collected once then all other collectors will not receive it anymore.
 * See [SkipIfCollected].
 *
 * Shortcut for `error(ErrorParam.SkipIfCollected).filterNotNull()`.
 */
public fun <T> Inflow<T>.unhandledError(): Flow<Throwable> = error(SkipIfCollected).filterNotNull()

/**
 * Similar to [progress][Inflow.progress], but provides a simple flow of `false`
 * (if [Progress.Idle]) and `true` (otherwise) values.
 */
public fun <T> Inflow<T>.loading(): Flow<Boolean> = progress()
    .map { it != Progress.Idle }
    .distinctUntilChanged()
