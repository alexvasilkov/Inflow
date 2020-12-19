package inflow

import inflow.DataParam.CacheOnly
import inflow.RefreshParam.IfExpiresIn
import inflow.RefreshParam.Repeat
import inflow.internal.InflowImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.experimental.ExperimentalTypeInference

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
 * Refresh state can be observed using [progress] and [error] state flows.
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
 * inflow {
 *     loader = api::loadData
 *     cache = dao::getData
 *     cacheWriter = dao::saveData
 * }
 * ```
 *
 * See [InflowConfig] for all the available configuration options.
 */
interface Inflow<T> {
    /**
     * Cached data collected from original [InflowConfig.cache] flow.
     *
     * Original (cold or hot) flow will be subscribed automatically and shared among all active
     * subscribers. Thus no extra cache readings will be done for all subsequent subscribers as they
     * will immediately receive the most recent cache data.
     * Original cache will be unsubscribed after predefined timeout since last active subscriber is
     * unsubscribed (see [InflowConfig.cacheKeepSubscribedTimeout]).
     *
     * In other words original cache flow will be subscribed only if there is at least one active
     * subscriber and for some time after all subscribers are gone. This "keep subscribed" time
     * can be useful to avoid extra readings from original (cold) cache flow while switching app
     * screens, etc.
     *
     * The cache will be automatically kept fresh using [InflowConfig.loader] while it has at least
     * one subscriber and according to expiration policy set with [InflowConfig.cacheExpiration].
     * This behavior can be disabled by using [CacheOnly] param.
     *
     * @param params Optional parameters, see [DataParam].
     * If parameter of the same type is passed several times then only first parameter will be used.
     */
    fun data(vararg params: DataParam): Flow<T>

    /**
     * Current loading progress.
     * It will emit [Progress.Active] once the loading is started and [Progress.Idle] in the end.
     * Optionally it is also possible to track loading state ([Progress.State]) using
     * [ProgressTracker] object passed to [InflowConfig.loader].
     * Only one request can run at a time.
     *
     * Note: if a new refresh is explicitly requested with [refresh] and [Repeat] param
     * while running another refresh, the new request will run immediately after first request is
     * finished without emitting consequent [Progress.Active] and [Progress.Idle] values.
     */
    fun progress(): StateFlow<Progress>

    /**
     * Recent error caught during refresh requests or `null` if latest request was successful.
     * Will be set back to `null` immediately after new refresh is started.
     *
     * It will always repeat the most recent error when starting collecting so the UI code may want
     * to save latest handled error locally to avoid showing duplicate errors.
     */
    fun error(): StateFlow<Throwable?>

    /**
     * Manually requests data refresh from a remote source. The request will start immediately
     * (unless [IfExpiresIn] param is used) and can be observed using returned deferred object.
     *
     * @param params Optional parameters for refresh request, see [RefreshParam].
     * If parameter of the same type is passed several times then only first parameter will be used.
     *
     * @return Deferred object with [join][InflowDeferred.join] and [await][InflowDeferred.await]
     * methods to **optionally** observe the result of the call in a suspending manner. The result
     * can still be observed with [data], [progress] and [error] flows as usual.
     */
    fun refresh(vararg params: RefreshParam): InflowDeferred<T>
}


/**
 * Creates a new [Inflow] using provided [InflowConfig] configuration.
 */
@OptIn(ExperimentalTypeInference::class)
fun <T> inflow(@BuilderInference block: InflowConfig<T>.() -> Unit): Inflow<T> =
    InflowImpl(InflowConfig<T>().apply(block))


/* ---------------------------------------------------------------------------------------------- */
/* Parameters                                                                                     */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Parameters for [Inflow.data] method: [CacheOnly].
 */
sealed class DataParam {
    /**
     * Returned data flow will not trigger automatic data refresh and will just return the flow of
     * cached data.
     */
    object CacheOnly : DataParam()
}

/**
 * Parameters for [Inflow.refresh] method: [Repeat], [IfExpiresIn].
 */
sealed class RefreshParam {
    /**
     * If set and another refresh is currently in place then extra refresh will be done again right
     * after the current one. No error or progress (except optional [Progress.State]) events will be
     * emitted until this extra request completes.
     *
     * It can be useful in situations when remote data was changed (e.g. because of POST or PUT
     * request) and we need to ensure that newly loaded data reflects that changes. Otherwise
     * previous refresh may return stale data.
     */
    object Repeat : RefreshParam()

    /**
     * The refresh will only be requested if the latest cached value is expiring in less than
     * [expiresIn] milliseconds according to [InflowConfig.cacheExpiration] policy.
     *
     * For example if cached value expires in 5 minutes and [expiresIn] is set to 10 minutes then
     * no refresh will be done and the cached value will be returned as-is. But if [expiresIn] is
     * set to 2 minutes then a new refresh request will be scheduled.
     */
    data class IfExpiresIn(val expiresIn: Long) : RefreshParam() {
        init {
            require(expiresIn >= 0L) { "Value of 'expiresIn' must be >= 0" }
        }
    }
}


/* ---------------------------------------------------------------------------------------------- */
/* Useful extensions                                                                              */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Returns cache flow, shortcut for `data(DataParam.CacheOnly)`
 */
fun <T> Inflow<T>.cache(): Flow<T> = data(CacheOnly)

/**
 * Returns latest cached data without trying to refresh it.
 *
 * Shortcut for `data(DataParam.CacheOnly).first()`.
 */
suspend fun <T> Inflow<T>.cached() = data(CacheOnly).first()

/**
 * If latest cached data is expiring in more than [expiresIn] milliseconds then it will be returned
 * as-is. Otherwise a new request will be triggered and its result will be returned.
 * See [IfExpiresIn].
 *
 * **Important: this method will throw an exception if the request is failed.**
 *
 * Shortcut for `refresh(RefreshParam.IfExpiresIn(expiresIn)).await()`.
 */
suspend fun <T> Inflow<T>.fresh(expiresIn: Long = 0L): T = refresh(IfExpiresIn(expiresIn)).await()

/**
 * If another refresh is currently in place then extra refresh will be done again right after the
 * current one. See [Repeat].
 *
 * Shortcut for `refresh(RefreshParam.Repeat)`.
 */
fun <T> Inflow<T>.forceRefresh(): InflowDeferred<T> = refresh(Repeat)

/**
 * Provides a simple flow of `false` (if [Progress.Idle]) and `true` (otherwise) values.
 */
fun <T> Inflow<T>.loading(): Flow<Boolean> = progress()
    .map { it != Progress.Idle }
    .distinctUntilChanged()
