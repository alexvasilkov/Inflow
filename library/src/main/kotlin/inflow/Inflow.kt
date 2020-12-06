package inflow

import inflow.impl.InflowImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable

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
 * Refresh can also be started manually by calling [refresh] or [refreshBlocking] methods.
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
interface Inflow<T> : Closeable {

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
     * Manually requests data refresh from a remote source.
     *
     * @param repeatIfRunning If set to true and another refresh is currently in place then extra
     * refresh will be done again right after the current one. No error or loading events will be
     * emitted until this extra request completes.
     *
     * It can be useful in situations when remote data was changed (e.g. because of POST or PUT
     * request) and we need to ensure that newly loaded data reflects that changes. Otherwise
     * previous refresh may return stale data.
     */
    fun refresh(repeatIfRunning: Boolean = false)

    /**
     * Closes and cleans up internal resources (coroutine scopes) that are used to subscribe to
     * cache or request data from remote source.
     *
     * Once closed all flows will stop emit new values and any operations on closed [Inflow] will
     * throw [IllegalStateException].
     *
     * Note that it is usually safe to keep global [Inflow] instance as it will not consume much
     * resources if it has no active subscribers.
     *
     * @see [closeWithScope]
     */
    override fun close()
}


/**
 * Creates a new [Inflow] using provided [InflowConfig] configuration.
 */
fun <T> inflow(block: InflowConfig<T>.() -> Unit): Inflow<T> =
    InflowImpl(InflowConfig<T>().apply(block))


/**
 * Similar to [refresh][Inflow.refresh] but will suspend until loading is finished.
 */
suspend fun <T> Inflow<T>.refreshBlocking(repeatIfRunning: Boolean = false) =
    (this as InflowImpl).refreshBlockingInternal(repeatIfRunning)

/**
 * Returns latest cached data. Shortcut for `data(autoRefresh = false).first()`.
 *
 * Note that there is no guarantee that this method will return newly cached item right after
 * [refreshBlocking] because it will take time for the new data to propagate through the cache,
 * even if it is in-memory cache.
 */
suspend fun <T> Inflow<T>.get() = data(autoRefresh = false).first()

/**
 * Ensure that current [Inflow] is closed once specified [scope] is cancelled.
 *
 * @see [Inflow.close]
 */
fun <T> Inflow<T>.closeWithScope(scope: CoroutineScope) {
    val data = this
    scope.launch {
        // Suspending inside the scope, awaiting scope cancellation to clean up
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                data.close()
            }
        }
    }
}

fun <T, R> Inflow<T>.map(mapper: (T) -> R): Inflow<R> =
    (this as InflowImpl).mapInternal { mapper(it) }
