package inflow.paging

import inflow.Inflow
import inflow.LoadTracker
import inflow.paging.PageParams.Next
import inflow.paging.PageParams.Refresh
import inflow.paging.internal.IdentityById
import inflow.paging.internal.IdentityProvider
import inflow.paging.internal.IdentityWithComparator
import inflow.paging.internal.MergeByKeys
import inflow.paging.internal.MergeStrategy
import inflow.paging.internal.MergeWithComparator

/**
 * Configuration for a predefined [Pager] that can handle pagination logic automatically using
 * specified [loader] and/or [cache] provider. Read [Pager] doc for more info about different
 * pagination approaches.
 *
 * Next page load is triggered by [Inflow.loadNext][loadNextState], including initial load of the very
 * first page. The loaded list can be refreshed with [Inflow.refresh].
 *
 * **Loader and cache**
 *
 * The pager created using this configuration will use in-memory cache as a source of items that
 * will be returned by [Inflow.cache]. This in-memory cache will first try to load items from
 * provided [cache], page-by-page. If the cache is not provided or if it cannot provide enough items
 * then the [loader] will be used to load more items from a remote source. If the loader is not
 * provided or if remote source cannot return more items then the end is assumed to be reached.
 *
 * While the in-memory cache stays as the main source of items an extra (optional) [cache] can be
 * provided that will work as a secondary (persistent) cache.
 *
 * **Identity**
 *
 * Local data can eventually become out of sync with a remote source. For example a newly added item
 * or items order change can lead to a situation when a newly loaded page contains items which we
 * already have in the local. The duplicates can be removed if an items identity provider is set
 * using [identifyBy] or [identifyWith].
 *
 * **Merge**
 *
 * If items order is known then we can merge newly loaded pages into the local list, which will help
 * handle some of the edge cases that could happen when local and remote lists become out of sync
 * (for example some items are added or removed from the remote list). The ordering can be
 * configured using [mergeBy] or [mergeWith] methods. It will be applied to both `loadNext` and
 * `refresh` calls.
 *
 * When the next page is loaded we can ensure that items order stays consistent when appending the
 * new page. For example if local list is `[1,2,4]` and the next page is `[3,5,6]` then the
 * resulting list should be `[1,2,3,5,6]`.
 *
 * When the first page is loaded again as part of the `refresh` call then instead of replacing the
 * entire cached list we can seamlessly merge the new page into the local list. For example if local
 * list is `[1,2,3,5,6]` and the first page is loaded as `[0,1,3]` then the resulting list will be
 * `[0,1,3,5,6]`. If replace is required then [PageResult.forceClearCacheOnRefresh] should be set to
 * `true` during the refresh call.
 *
 * *See [Pager] for in-depth explanation.*
 *
 * Note that if merging is enabled then identity provider must also be set with [identifyBy] or
 * [identifyWith], otherwise overlapping items cannot be properly removed.
 */
public class PagerConfig<T, K : Any> internal constructor() {

    @JvmField
    @JvmSynthetic
    internal var pageSize: Int? = null

    @JvmField
    @JvmSynthetic
    internal var cache: PagingCache<T, K>? = null

    @JvmField
    @JvmSynthetic
    internal var loader: PageLoader<T, K>? = null

    @JvmField
    @JvmSynthetic
    internal var identity: IdentityProvider<T>? = null

    @JvmField
    @JvmSynthetic
    internal var merger: MergeStrategy<T, K>? = null

    /**
     * A page size that will be used when loading extra items from the [remote source][loader] and
     * when loading items from the [cache].
     */
    public fun pageSize(size: Int) {
        require(size > 0) { "Page size must be > 0" }
        pageSize = size
    }

    /** Cache implementation to persist paged data. */
    public fun cache(cache: PagingCache<T, K>) {
        this.cache = cache
    }

    /**
     * An optional loader that will be asked to fetch the next page (see [PageParams.Next]) or
     * refresh an already loaded list (see [PageParams.Refresh]).
     *
     * The passed [params][PageParams] will contain a [count][PageParams.count] (page size) and a
     * [key][PageParams.key] that should be used to fetch a new page.
     *
     * The [result][PageResult] should contain the newly loaded [items][PageResult.items] and a
     * [key][PageResult.nextKey] that will be used to load the following page (or `null` if end of
     * pagination is reached). It can optionally contain a [refresh key][PageResult.refreshKey]
     * which can be used for the future "refresh" call to load newer items.
     */
    public fun loader(loader: PageLoader<T, K>) {
        this.loader = loader
    }

    /**
     * Identity provider to use for list items deduplication.
     *
     * It uses specified [comparator] to check if two items are the same.
     * It's less efficient than [identifyBy] and should only be used if [identifyBy] cannot be used.
     *
     * Note that if merging is enabled with either [mergeWith] or [mergeBy] then identity provider
     * must also be set, otherwise overlapping items cannot be properly removed.
     */
    public fun identifyWith(comparator: (T, T) -> Boolean) {
        identity = IdentityWithComparator(comparator)
    }

    /**
     * Identity provider to use for list items deduplication.
     *
     * The value returned by [getter] must properly implement [Any.equals] and [Any.hashCode].
     *
     * Note that if merging is enabled with either [mergeWith] or [mergeBy] then identity provider
     * must also be set, otherwise overlapping items cannot be properly removed.
     */
    public fun <Id : Any> identifyBy(getter: (T) -> Id) {
        identity = IdentityById(getter)
    }

    /**
     * Defines merging strategy.
     *
     * If items order is known then we can merge newly loaded pages into the local list.
     * See [PagerConfig] for more info.
     *
     * If set then identity provider must also be set with either [identifyWith] or [identifyBy].
     *
     * @param comparator Defines ordering between the items.
     * @param inverse Whether the order defined by [comparator] should be inverted.
     * Optional, default value is `false`.
     * @param unique Whether the items order is unique, i.e. provided [comparator] only returns `0`
     * when an item is compared to itself (as defined by identity provider).
     * Helps to handle extra edge cases.
     *
     * For example, if first page is `[1,2,3']` and second page is `[3",4,5]` (where `3'` and `3"`
     * are different items with same order, for example items are sorted by title and different
     * items may have same title), then if items order is known to be unique then the merged list
     * will be `[1,2,3",4,5]` and if items order is non-unique then the merged list will be
     * `[1,2,3',3",4,5]`.
     *
     * *See [Pager] for in-depth explanation.*
     */
    public fun mergeWith(
        comparator: Comparator<in T>,
        inverse: Boolean = false,
        unique: Boolean
    ) {
        merger = MergeWithComparator(comparator.order(inverse), unique)
    }

    /**
     * Defines merging strategy. Similar to [mergeWith] but takes into account paging keys
     * (as used by [PageParams]) to
     * better handle different edge cases.
     *
     * If items order is known then we can merge newly loaded pages into the local list.
     * See [PagerConfig] for more info.
     *
     * If set then identity provider must also be set with either [identifyWith] or [identifyBy].
     *
     * @param getter Retrieves paging key from an item. For example if items are sorted and paged
     * by creation date then this getter should return the item's creation date.
     * @param comparator Defines ordering between paging keys.
     * @param inverse Whether the order defined by [comparator] should be inverted.
     * Optional, default value is `false`.
     * @param unique Whether the items order is unique, i.e. all items have different keys as
     * defined by [comparator]. Helps to handle extra edge cases.
     *
     * Note that there is an important difference from [mergeWith] method.
     * If items order is unique then we assume that all items from the next page must have keys
     * greater than requested `nextKey`. If items order is non-unique then the next page must return
     * all items whose keys are equal or greater than `nextKey`.
     * **The specified [loader] must conform to this rule.** It is done to prevent a situation when
     * there can be several items with the same key and the loader should correctly load item to not
     * lose any.
     *
     * For example, if first page is `[1,2,3']`, "next key" is `3` and second page is `[3",4,5]`
     * (where `3'` and `3"` are different items with same key, for example items are sorted by
     * title and different items may have same title), then if items order is known to be unique
     * then we expected no `3` items on second page and the merged list will be `[1,2,3',3",4,5]`,
     * and if items order is non-unique then we expected to get **all** `3` items and the merged
     * list will be `[1,2,3",4,5]` as we didn't receive item `3'`.
     *
     * *See [Pager] for in-depth explanation.*
     * */
    public fun mergeBy(
        getter: (T) -> K,
        comparator: Comparator<in K>,
        inverse: Boolean = false,
        unique: Boolean
    ) {
        merger = MergeByKeys(getter, comparator.order(inverse), unique)
    }

    private fun <T> Comparator<T>.order(inverseOrder: Boolean) =
        if (inverseOrder) Comparator { t1, t2 -> -compare(t1, t2) } else this

}


/** Loads next page or refreshes the list. */
public fun interface PageLoader<T, K : Any> {
    /**
     * Loads paged items according to specified [params] (either [Next][PageParams.Next] or
     * [Refresh][PageParams.Refresh]) and returns [PageResult].
     */
    public suspend fun load(tracker: LoadTracker, params: PageParams<K>): PageResult<T, K>
}

/** The param passed to a new page request. See [PagerConfig.loader]. */
public sealed class PageParams<K : Any>(
    /**
     * For [Next] param: a key from which to load a new page, or `null` to load the very first page.
     *
     * For [Refresh] param: a key which can be used to load newer items, or `null` to load the very
     * first page again.
     */
    @JvmField
    public val key: K?,

    /** Number of items to load, in other words a page size. */
    @JvmField
    public val count: Int
) {
    /** Represents a "refresh" call. See [key] and [count]. */
    public class Refresh<K : Any> internal constructor(key: K?, count: Int) :
        PageParams<K>(key, count)

    /** Represents a "load next page" call. See [key] and [count]. */
    public class Next<K : Any> internal constructor(key: K?, count: Int) :
        PageParams<K>(key, count)
}


/** The result of a new page request. See [PagerConfig.loader]. */
public class PageResult<T, K : Any>(
    /** Items for the requested page. */
    @JvmField
    public val items: List<T>,

    /** A key to request the next page. **Set to `null` to designate the end of the pagination.** */
    @JvmField
    public val nextKey: K?,

    /**
     * A key to refresh **current** page.
     *
     * Simple refresh can be made by loading the first page again, in which case just set the
     * refresh key to `null` (default value).
     *
     * Another option is to load and prepend items newer than the first (or n-th) item currently in
     * the cache. In this case appropriate refresh key should be set (for example creation date, id,
     * etc). Note that a new non-null refresh key should be provided for all [PageParams.Refresh]
     * actions as well as for [PageParams.Next] actions (at least if the next key is `null`).
     *
     * There can be too many newer items that cannot be loaded at once, in which case we should
     * prefer to reload entire list from scratch. If the loaded list of newer items is incomplete
     * (there are other items which we didn't receive) we can request the first page again by doing
     * a regular refresh request with `null` key, and also set [forceClearCacheOnRefresh] to `true`.
     */
    @JvmField
    public val refreshKey: K? = null,

    /**
     * A flag that tells the pager to avoid prepending items returned during a refresh call, instead
     * the entire cached list will be replaced with [items]. Default value is `false`.
     *
     * This can be used to totally avoid merging during refresh or only to avoid it for some of the
     * responses, see [refreshKey] for more info.
     *
     * Only applicable to [PageParams.Refresh], has no effect when used with [PageParams.Next].
     */
    @JvmField
    public val forceClearCacheOnRefresh: Boolean = false
)
