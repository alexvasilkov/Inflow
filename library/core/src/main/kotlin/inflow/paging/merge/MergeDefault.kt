package inflow.paging.merge

internal class MergeDefault<T, K : Any> : MergeStrategy<T, K> {

    override fun prepend(prepend: List<T>, list: List<T>, refreshKey: K?): Int = when {
        // No local items, clearing the cache to avoid inconsistency
        list.isEmpty() -> -1
        // If refresh key is null then we just loaded the first page and want to merge it.
        // But we cannot safely prepend items if no comparator defined.
        // E.g. prepend = [6,1,2] & list = [4,5,6,7] -> ???
        // The remote list can be [6,1,2,2.1,...,3.9,4,5,7] and we'll lose too many items.
        refreshKey == null -> -1
        // If refresh key is not null then we loaded all the items newer than the local list.
        // We can safely prepend new items as-is (especially if de-duplication is done later).
        else -> 0
    }

    override fun append(list: List<T>, nextPage: List<T>, nextKey: K?): Int = when {
        // No local items, clearing the cache to avoid inconsistency
        list.isEmpty() -> -1
        // If next key is null then we loaded the first page and it should replace the cache
        nextKey == null -> -1
        // If next key is not null then we loaded the next (older) items.
        // We can append new items as-is (especially if de-duplication is done later).
        else -> list.size
    }

}
