package inflow

import inflow.internal.InflowImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
 * Refresh state can be observed using [loading] and [error] state flows. The [loading] flow will
 * emit `true` when the loading is started and `false` in the end. The [error] flow will emit
 * the most recent error happened during last refresh or `null` if last request was successful or
 * still in progress.
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
     * subscribers. Thus no extra cache readings are need for all the subsequent subscribers as they
     * will immediately receive the most recent cache data.
     * Original cache will be unsubscribed after predefined timeout since last active subscriber is
     * unsubscribed (see [InflowConfig.cacheKeepSubscribedTimeout]).
     *
     * In other words original cache flow will be subscribed only if there is at least one active
     * subscriber and for some time after all subscribers are gone. This "keep subscribed" time
     * can be useful to avoid extra readings from original (cold) cache flow while switching app
     * screens, etc.
     *
     * @param autoRefresh If set to `true` (which is by default) the cache will be automatically
     * kept fresh while it has at least one subscriber using [InflowConfig.loader] and according to
     * expiration policy set with [InflowConfig.cacheExpiration].
     */
    fun data(autoRefresh: Boolean = true): Flow<T>

    /**
     * Current loading state. Will emit `true` when starting remote data request and `false` in the
     * end. Only one request can run at a time.
     *
     * Note: if a new refresh is explicitly requested with [refresh] and `repeatIfRunning = true`
     * while running another refresh, the new request will run immediately after first request is
     * finished without emitting consequent `false` and `true` values.
     */
    fun loading(): StateFlow<Boolean>

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
     * but can be observed using returned deferred object.
     *
     * @param repeatIfRunning If set to true and another refresh is currently in place then extra
     * refresh will be done again right after the current one. No error or loading events will be
     * emitted until this extra request completes.
     *
     * It can be useful in situations when remote data was changed (e.g. because of POST or PUT
     * request) and we need to ensure that newly loaded data reflects that changes. Otherwise
     * previous refresh may return stale data.
     *
     * @return Deferred object with [join][InflowDeferred.join] and [await][InflowDeferred.await]
     * methods to **optionally** observe the result of the call in a suspending manner. The result
     * can still be observed with [data], [loading] and [error] flows as usual.
     */
    fun refresh(repeatIfRunning: Boolean = false): InflowDeferred<T>

}


/**
 * Creates a new [Inflow] using provided [InflowConfig] configuration.
 */
@OptIn(ExperimentalTypeInference::class)
fun <T> inflow(@BuilderInference block: InflowConfig<T>.() -> Unit): Inflow<T> =
    InflowImpl(InflowConfig<T>().apply(block))


/**
 * Returns latest cached data without trying to refresh it.
 * Shortcut for `data(autoRefresh = false).first()`.
 */
suspend fun <T> Inflow<T>.latest() = data(autoRefresh = false).first()
