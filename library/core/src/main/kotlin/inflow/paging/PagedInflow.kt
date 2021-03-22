package inflow.paging

import inflow.CacheWriter
import inflow.DataLoader
import inflow.Inflow
import inflow.InflowConfig
import inflow.InflowDeferred
import inflow.LoadParam
import inflow.MemoryCache
import inflow.State
import inflow.StateParam
import inflow.internal.InflowImpl
import inflow.internal.Loader
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class PagedInflowConfig<T, P : Paged<T>> internal constructor() : InflowConfig<P>() {

    @JvmField
    @JvmSynthetic
    internal var loadNext: DataLoader<Unit>? = null

    /**
     * Paging logic implementation, see [Pager] for more details.
     */
    public fun pager(pager: Pager<T, P>) {
        super.data(pager.cache()) { pager.callSafe(pager::refresh) }
        loadNext = { pager.callSafe(pager::loadNext) }
    }

    @Deprecated(message = "Use pager() instead", level = DeprecationLevel.HIDDEN)
    override fun data(cache: Flow<P>, loader: DataLoader<Unit>) {
        throw UnsupportedOperationException("Use pager() instead")
    }

    @Deprecated(message = "Use pager() instead", level = DeprecationLevel.HIDDEN)
    override fun <R> data(cache: Flow<P>, writer: CacheWriter<R>, loader: DataLoader<R>) {
        throw UnsupportedOperationException("Use pager() instead")
    }

    @Deprecated(message = "Use pager() instead", level = DeprecationLevel.HIDDEN)
    override fun data(cache: MemoryCache<P>, loader: DataLoader<P>) {
        throw UnsupportedOperationException("Use pager() instead")
    }

    @Deprecated(message = "Use pager() instead", level = DeprecationLevel.HIDDEN)
    override fun data(initial: P, loader: DataLoader<P>) {
        throw UnsupportedOperationException("Use pager() instead")
    }
}

@ExperimentalCoroutinesApi
public fun <T, P : Paged<T>> pagedInflow(block: PagedInflowConfig<T, P>.() -> Unit): Inflow<P> =
    PagedInflowImpl(PagedInflowConfig<T, P>().apply(block))


/**
 * Requests next page load from a remote source using the loader configured with
 * [PagedInflowConfig.pager]. The request will start immediately and can be observed using
 * [loadNextState] flow.
 *
 * Only one refresh or "load next" call can run at a time. If another refresh request is already
 * running then this "load next" call will wait until it finishes. If another "load next" request
 * is already running the no extra "load next" calls will be made until it finishes.
 *
 * @return Deferred object to **optionally** observe the result of the call in a suspending manner.
 */
public fun <T, P : Paged<T>> Inflow<P>.loadNext(): InflowDeferred<P> =
    loadInternal(LoadParam.LoadNext)

/**
 * State of the "load next page" process, similar to [Inflow.refreshState] but it's tracked
 * separately from refresh calls.
 */
public fun <T, P : Paged<T>> Inflow<P>.loadNextState(): Flow<State> =
    stateInternal(StateParam.LoadNextState)


@ExperimentalCoroutinesApi
internal class PagedInflowImpl<T, P : Paged<T>>(config: PagedInflowConfig<T, P>) :
    InflowImpl<P>(config) {
    private val loaderNext = Loader(logId, scope, loadDispatcher, config.loadNext!!)

    override fun stateInternal(param: StateParam) = when (param) {
        StateParam.LoadNextState -> loaderNext.state
        else -> super.stateInternal(param)
    }

    override fun loadInternal(param: LoadParam) = when (param) {
        LoadParam.LoadNext -> DeferredLoad(loaderNext.load())
        else -> super.loadInternal(param)
    }
}

/**
 * Paged data controller.
 *
 * It is similar to `cache` and `loader` (refresh) providers defined in [InflowConfig.data] but it
 * also introduces a `loadNext` action.
 *
 * **Overview**
 *
 * The [cache] flow represents the part of the data that is meant to be shown.
 * It either starts by emitting the first page from a cache (if any) or with empty/unfinished state
 * ([`Paged(items = emptyList(), hasNext = true)`][Paged]).
 *
 * The [refresh] function is used whenever [Inflow.refresh] is called, including automatic
 * refresh if the paged data was identified as expired (see [InflowConfig.expiration]).
 * The state of the `refresh` call can be observed with [Inflow.refreshState].
 *
 * The [loadNext] function is used whenever [Inflow.loadNext][loadNext] extension is called.
 * The state of the `loadNext` call can be observed with [Inflow.loadNextState][loadNextState].
 *
 * The states of `refresh` and `loadNext` actions are tracked separately.
 *
 * **Execution**
 *
 * The actions will not be executed concurrently. For example, if `refresh` was called first and
 * then `loadNext` was called while the refresh is still in progress then the `loadNext` action will
 * wait for the `refresh` action to finish before it can start.
 *
 * If it's not desirable to run pending actions once current action is finished then they can be
 * skipped with [skipPendingActions]. This can be useful if `loadNext` is called while `refresh`
 * is still in progress and we know that `refresh` process will completely replace current data and
 * the `loadNext` action will not make sense anymore as it was requested for the old data.
 *
 * **Paged data**
 *
 * The paged data from the [cache] flow represents entire list of items loaded to that moment, from
 * the very beginning and up to some intermediate or end point. It can consist of several pages and
 * should correctly define [Paged.hasNext] which is the only way for the UI to trigger the next page
 * loading (regardless whether it will be loaded from a local cache or from a remote source).
 *
 * Caching and loading logic should be implemented by subclasses of the [Pager].
 *
 * **Caching strategies**
 *
 * // TODO: It should be clear from this section that memory cache is always needed.
 *
 * The simplest cache option is an in-memory cache. The data from a remote source is merged into a
 * local list and then the entire list can be displayed. Usually there is no need to split cached
 * items into separate pages to feed the UI, unless we want to try loading fresh data from the
 * remote source before falling back to the cached items (see *Loading strategies*).
 * // TODO: Review the last sentence
 *
 * The things become more complicated when we want to use a persistent cache, like DB. Such caches
 * are usually quite slow and we should prefer to avoid excessive reads.
 * At the same time we have to return the entire list from the very beginning and up to some
 * intermediate point, and if we want to use our cache as a single source of truth then we have to
 * re-read the entire sublist whenever any cache changes are detected.
 *
 * For example, if we already display 2 pages of data and the 3rd page is just arrived and saved
 * into the cache then we have to re-read all 3 pages from the cache to ensure the displayed list is
 * consistent with the cache. Generally, we cannot guarantee that items order will stay unchanged
 * between different page loads, so it can happen that the new page contains items that were already
 * loaded before and when saved into a DB the duplicates will be removed while the UI will still
 * display them unless we'll re-read the entire list.
 *
 * Another example is when one of the items was modified (e.g. marked as favorite), in this case we
 * have to reload entire list from the cache just to catch up this small change. It is not a unique
 * problem for the paging but a potentially big amount of data makes it much more noticeable.
 *
 * A possible solution can be to only read enough data to fill the UI and then load more data from
 * the cache on demand (e.g. when the list is scrolled).
 * But it implies a rather complex interaction and coupling between the UI, the cache and the remote
 * source and cannot be universally applied to arbitrary UI systems. `Android Paging 3` library is
 * an example of such approach.
 *
 * So, if the cache performance becomes important then intermediate in-memory cache has to be
 * introduced. It will require extra effort to keep it synchronized with the persistent cache and
 * the persistent cache can't be considered as a single source of truth anymore. Basically all the
 * operations with the persistent cache have to be carefully re-applied to the in-memory cache.
 *
 * **Paging keys**
 *
 * To start loading the next page we should know a some kind of key of this page. It can be a simple
 * page number, a starting value (like "created at") or just a generic page token. If persistent
 * cache is used to store paged data then the next page key should also use persistent storage so
 * that we can access it when everything from the local cache is shown and we need to ask remote
 * source for more data.
 *
 * When the end of pagination is reached within the remote source then we need to explicitly save
 * this info locally using a single boolean flag. Using `null` next key as an indicator is not
 * enough as it can just mean that no pages were loaded yet and we need to load the first page.
 *
 * **Loading strategies**
 *
 * Newly loaded page should be appended (or somehow merged) into the already loaded data. In most
 * cases we cannot guarantee that the remote list wasn't changed after we loaded the first page,
 * various changes can happen in the remote list after we loaded it locally:
 *
 * * Item is updated
 * * Item is deleted
 * * New item is added (in the beginning, in the middle)
 * * Items order is changed
 *
 * All of these changes make it harder to keep local cache consistent with it's remote counterpart.
 *
 * Let's have an overview of a common pagination approaches:
 *
 * **By page number**
 *
 * In this approach you provide a page number or a skip/offset count so that the API knows how many
 * items to omit. That's a very simple but pretty limited approach, it works fine for a simple
 * one-page-per-screen pagination (like on many websites) but it does not work well for endless
 * loading lists common for mobile apps.
 *
 * Any changes of the remote list can break the local cache consistency. For example if we loaded
 * first page as "1,2,...,10" and item "1" was removed then when the next page is loaded we'll see
 * "12,13,...,21" instead of expected "11,12,...,20" list, so we effectively lost "11" item here.
 * Similarly when a new "0" item is added then when the next page is loaded we'll see "10,11,...,19"
 * list and then the "10" item will be duplicated in the local cache because it was already loaded
 * before. Even more confusing things happen when the order of items changes in the remote list.
 *
 * Overall this type of pagination is mostly suitable for rarely changing lists and when the risk of
 * loosing an item is not very high. De-duplication can be done locally if the items have unique ids.
 *
 * **By ordered field value**
 *
 * If the list is sorted by a known item's field (e.g. by a timestamp like "created" or "updated",
 * by id, etc) then we can use this field for pagination. All we need is to pass the value from the
 * last known item and the remote source should return the next items. It's not important if the
 * list is sorted in ascending or descending order, what matters though is uniqueness of the values.
 *
 * If the values are unique then the remote source can just return all the values that are smaller
 * (or greater) than the one we provide. For example if sorting by id and the last loaded item has
 * id "12" then the remote source should return items "11,10,...".
 *
 * If we cannot guarantee uniqueness then the remote source should better include all the items with
 * the same value as from the last loaded item, otherwise we can lose other items with the same
 * value. For example when items are sorted by a timestamp, especially if the time does not include
 * milliseconds, then there is a non zero chance that two items has the same timestamp. Consider
 * that the remote source has items "...,12,13,13,14,15,..." and we already loaded items "...,12,13".
 * Now if we'll ask for all items greater than 13 we'll get "14,15,..." and we'll lose the second
 * "13" item. Instead the remote source should return all the items greater or equal to "13", i.e.
 * "13,13,14,15,...". Locally we have to drop the duplicates when appending the newly loaded page.
 * Note that in this case the actual amount of new items will always be smaller than requested
 * as we'll always get at least one duplicate item. It is also important to ensure that page size is
 * bigger than the longest possible sequence of items with identical values, otherwise the loading
 * may never end in case the new page consists fully of items with the same value.
 *
 * **By generic token**
 *
 * The remote source can return a specific token needed to load the following page, this approach is
 * used by Twitter, Facebook and many others. The remote pagination logic can be pretty complicated,
 * for example a list can be sorted by "popularity" or "relevance" and the remote source can somehow
 * remember the context of the initial (first page) load.
 *
 * If the sorting can be replicated locally then the pagination logic can be implemented as
 * described in **By ordered field value** section, the only difference is that we'll send the next
 * page token instead of the last known item's value.
 *
 * If the sorting order is unknown then the pagination logic becomes similar to "By page number"
 * approach, and we have to rely on the remote source to ensure no items are lost. We can still
 * apply the items de-duplication locally, just in case.
 *
 * **Refresh**
 *
 * The locally cached data may need to be refreshed and we can use different approaches to do that.
 *
 * The easiest option is just to load the first page again and then completely replace cached data
 * with this first page. But doing so can disrupt the user especially if they were reading the
 * following pages and the refresh was triggered automatically. Instead we can try to merge the
 * first page into the cached list. This can be done if the sorting order is know to us or if we
 * know that the sorting of the items will never change.
 *
 * If the sorting logic is known then we can check if the new first page has intersection with the
 * locally cached data. If yes then we can delete intersecting items locally and prepend the first
 * page to the cache. If the new first page has no intersection with the local cache then we cannot
 * guarantee that there are no extra items between the first page and whatever we have in the cache
 * thus the local cache have to be completely cleared and the first page will replace it.
 *
 * If the sorting order is know to be stable then we can check for the intersection between the new
 * first page and the cached data as well, but we can use a simple id equality check for that.
 *
 * It is important to note that in both case when the new first page is prepended to the local cache
 * we need to ensure that there are no duplicates are left in the cache. E.g. if item's order
 * changed and it used to be the second page but it's now in the first page then we have to
 * explicitly delete it from the cache before appending the first page.
 *
 * If the remote source allows loading the items in upward direction then we can ask it to only
 * provide newer items that can be simply appended to the locally cached list, we may still need to
 * apply de-duplication logic though. If the remote source indicates that there are too many newer
 * items and not all of them are returned then we have to reload the first page explicitly and clear
 * the local cache before saving the first page. If we want to refresh the few first items already
 * stored in the cache then we can ask the remote source to load items newer than the Nth item from
 * the cache. The requested page size should be increased respectively.
 *
 * The above "prepend" approach will help to catch up the newly added items and probably even
 * refresh the few cached items along the way. In the end we'll have a fresh first page in the local
 * cache but the second and further pages will stay stale. Refreshing items from the next pages is
 * trickier.
 *
 * One option can be to only save the first page in the persistent cache, in this case the next time
 * we'll load the data from cache we won't have the second page and will have to request it from the
 * remote source.
 *
 * If we still want to keep all the loaded pages in the cache and show them while the user is
 * offline then we can implement our pagination logic in such a way that we always try to load the
 * next page from the remote source and only if the loading failed we'll fallback to the locally
 * cached data.
 *
 * A more complicated solution is to use data synchronization but it's outside of the scope of this
 * documentation.
 */
public abstract class Pager<T, P : Paged<T>> {
    private val mutex = Mutex()
    private val generation = atomic(0)

    internal suspend fun callSafe(action: suspend () -> Unit) {
        val current = generation.value
        mutex.withLock { if (current == generation.value) action() }
    }

    protected fun skipPendingActions() {
        generation.incrementAndGet()
    }

    public abstract fun cache(): Flow<P>

    public abstract suspend fun refresh()

    public abstract suspend fun loadNext()
}
