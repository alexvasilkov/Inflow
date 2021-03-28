package inflow.paging

import inflow.LoadTracker
import inflow.paging.identity.IdentityByComparator
import inflow.paging.identity.IdentityById
import inflow.paging.identity.IdentityProvider
import inflow.paging.merge.MergeByItems
import inflow.paging.merge.MergeByKeys
import inflow.paging.merge.MergeStrategy

public class PagerConfig<T, K : Any> internal constructor() {

    @JvmField
    @JvmSynthetic
    internal var loader: PageLoader<T, K>? = null

    @JvmField
    @JvmSynthetic
    internal var cache: PagingCache<T, K>? = null

    @JvmField
    @JvmSynthetic
    internal var pageSize: Int? = null

    @JvmField
    @JvmSynthetic
    internal var identity: IdentityProvider<T>? = null

    @JvmField
    @JvmSynthetic
    internal var merger: MergeStrategy<T, K>? = null

    public fun loader(loader: PageLoader<T, K>) {
        this.loader = loader
    }

    public fun cache(cache: PagingCache<T, K>) {
        this.cache = cache
    }

    public fun pageSize(size: Int) {
        pageSize = size
    }

    public fun identifyWith(comparator: (T, T) -> Boolean) {
        identity = IdentityByComparator(comparator)
    }

    public fun <Id : Any> identifyBy(getter: (T) -> Id) {
        identity = IdentityById(getter)
    }

    public fun mergeWith(
        comparator: Comparator<in T>,
        inverse: Boolean = false,
        unique: Boolean = false
    ) {
        merger = MergeByItems(comparator.order(inverse), unique)
    }

    public fun mergeBy(
        getter: (T) -> K,
        comparator: Comparator<in K>,
        inverse: Boolean = false,
        unique: Boolean = false
    ) {
        merger = MergeByKeys(getter, comparator.order(inverse), unique)
    }

    private fun <T> Comparator<T>.order(inverseOrder: Boolean) =
        if (inverseOrder) Comparator { t1, t2 -> -compare(t1, t2) } else this

}


internal typealias PageLoader<T, K> = suspend (LoadTracker, PageParams<K>) -> PageResult<T, K>

public sealed class PageParams<K : Any>(
    @JvmField
    public val key: K?,

    @JvmField
    public val count: Int
) {
    public class Refresh<K : Any> internal constructor(key: K?, count: Int) :
        PageParams<K>(key, count)

    public class Next<K : Any> internal constructor(key: K?, count: Int) :
        PageParams<K>(key, count)
}

public class PageResult<T, K : Any>(
    /**
     * Items from the requested page.
     */
    @JvmField
    public val items: List<T>,

    /**
     * A key to request the next page.
     */
    @JvmField
    public val nextKey: K?,

    /**
     * A key to refresh **current** page.
     *
     * Simple refresh can be made by loading the first page again, in which case just set
     * refresh key to `null` (default value).
     *
     * Another option is to load and prepend items newer than the first (or n-th) item currently in
     * the cache. In this case appropriate refresh key should be set (e.g. creation date, id, etc).
     * Note that a new non-null refresh key should be provided for all [PageParams.Refresh] actions
     * as well as for [PageParams.Next] actions (at least if the next key is `null`).
     *
     * There can be too many newer items that cannot be loaded at once, in which case we should
     * prefer to reload entire list from scratch. If the loaded list of newer items is incomplete
     * (there are other items which we didn't receive) then request the first page by doing a
     * regular refresh request with `null` key, and also set [forceClearCacheOnRefresh] to `true`.
     */
    @JvmField
    public val refreshKey: K? = null,

    /**
     * A flag that tells the pager to avoid prepending items returned during a refresh call, instead
     * the entire cached list will be replaced with [items].
     */
    @JvmField
    public val forceClearCacheOnRefresh: Boolean = false
)
