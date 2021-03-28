package inflow.paging.merge

internal class MergeByKeys<T, K : Any>(
    private val getter: (T) -> K,
    private val comparator: Comparator<in K>,
    private val unique: Boolean
) : MergeComparable<T, K>() {

    override fun prepend(prepend: List<T>, list: List<T>, refreshKey: K?): Int {
        if (list.isEmpty()) return -1 // No local items, clearing the cache to avoid inconsistency

        if (refreshKey == null) {
            // If refresh key is null then we just loaded the first page and want to merge it.

            // No items in the first page (but the next key is not null), nothing we can do
            if (prepend.isEmpty()) return 0

            val lastKey = KeyPoint(getter(prepend.last()))

            // We can only merge the first page if it has intersection with the cached list.
            // prepend = [1,2] & list = [3,4,6] -> -1 (remote can be [1,2,2.1,...,2.9,3,4,6])
            if (list.first() > lastKey) return -1 // Clearing the cache to force next page

            // If the newly loaded list covers more than we have locally then we have to replace all
            if (list.last() < lastKey) return -1

            return list.prependIndex(lastKey, inclusive = unique)
        } else {
            // If refresh key is not null then we loaded all the items newer than local list, but
            // still with potential overlapping.

            // If keys are not unique (have duplicates) then all items with `key == refreshKey` are
            // required to be returned in the prepend list, otherwise we can lose some items.
            // E.g. for list = [3,4,5] & refreshKey = 3 the remote list can be [1,2,3,3,4,5],
            // if we won't return all existing 3s in prepend list ([1,2]) then the local merged
            // list will be [1,2,3,4,5] and we'll lose single 3. So we assume the prepend list
            // must be [1,2,3,3] in this case, and then we have to remove local duplicates.

            // If keys are unique then there is no need to return items with `key == refreshKey`,
            // so we'll assume such items are not part of prepend list and they should not be
            // removed from the local cached list.

            return list.prependIndex(KeyPoint(refreshKey), inclusive = !unique)
        }
    }

    override fun append(list: List<T>, nextPage: List<T>, nextKey: K?): Int {
        // If the very first page is loaded then it should replace current cache, which should
        // actually be empty if we are loading first page.
        if (nextKey == null) return -1

        // If keys are not unique (have duplicates) then all items with `key == nextKey` are
        // required to be returned in the next page, otherwise we can lose some items.
        // E.g. for list = [1,2,3] & nextKey = 3 the remote list can be [1,2,3,3,4,5],
        // if we won't return all existing 3s in the next page ([4,5]) then the local merged
        // list will be [1,2,3,4,5] and we'll lose single 3. So we assume the net page must be
        // [3,3,4,5] in this case, and then we have to remove local duplicates.

        // If keys are unique then there is no need to return items with `key == nextKey`,
        // so we'll assume such items are not part of the next page and they should not be
        // removed from the local cached list.

        return list.appendIndex(KeyPoint(nextKey), inclusive = !unique)
    }

    private inner class KeyPoint(private val key: K) : Comparable<T> {
        override fun compareTo(other: T): Int = comparator.compare(key, getter(other))
    }

}
