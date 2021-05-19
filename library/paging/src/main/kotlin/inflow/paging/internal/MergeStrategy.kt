package inflow.paging.internal

internal interface MergeStrategy<T, K : Any> {

    fun findPrependIndex(prepend: List<T>, list: List<T>, forRefreshKey: K?): Int

    fun findAppendIndex(list: List<T>, nextPage: List<T>, forNextKey: K?): Int

}


internal class MergeWithComparator<T, K : Any>(
    private val comparator: Comparator<in T>,
    private val unique: Boolean
) : MergeComparable<T, K>() {

    override fun findPrependIndex(prepend: List<T>, list: List<T>, forRefreshKey: K?): Int {
        if (list.isEmpty()) return -1 // No local items -> clearing the cache

        if (forRefreshKey == null) { // Entire first page is loaded
            if (prepend.isEmpty()) return -1 // No items on the first page -> clearing the cache

            val lastItem = ItemPoint(prepend.last())

            // We can only merge the first page if it has intersection with the cached list.
            // prepend = [1,2] & list = [3,4,6] -> -1 (remote can be [1,2,2.1,...,2.9,3,4,6])
            if (list.first() > lastItem) return -1 // Clearing the cache to force next page

            // If the newly loaded list covers more than we have locally then we have to replace all
            if (list.last() < lastItem) return -1

            return list.prependIndex(lastItem, inclusive = unique)
        } else {
            // If refresh key is not null then we loaded all the items newer than local list, but
            // still with potential overlapping.

            if (prepend.isEmpty()) return 0 // No newer items

            val lastItem = ItemPoint(prepend.last())

            return list.prependIndex(lastItem, inclusive = unique)
        }
    }

    override fun findAppendIndex(list: List<T>, nextPage: List<T>, forNextKey: K?): Int {
        // If the very first page is loaded then it should replace current cache, which should
        // actually be empty if we are loading first page.
        if (forNextKey == null) return -1

        if (nextPage.isEmpty()) return list.size // No newer items, it's fine

        val firstItem = ItemPoint(nextPage.first())

        // If items order is unique (there are no two items with same order) then we know for sure
        // that local list must not include any items same or greater than first item.
        // If items order is non-unique then we can only rely on ID check to remove duplicates.
        return list.appendIndex(firstItem, inclusive = unique)
    }

    private inner class ItemPoint(private val item: T) : Comparable<T> {
        override fun compareTo(other: T): Int = comparator.compare(item, other)
    }

}


internal class MergeByKeys<T, K : Any>(
    private val getter: (T) -> K,
    private val comparator: Comparator<in K>,
    private val unique: Boolean
) : MergeComparable<T, K>() {

    override fun findPrependIndex(prepend: List<T>, list: List<T>, forRefreshKey: K?): Int {
        if (list.isEmpty()) return -1 // No local items -> clearing the cache

        if (forRefreshKey == null) { // Entire first page is loaded
            if (prepend.isEmpty()) return -1 // No items on the first page -> clearing the cache

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

            // If keys order is unique (there are no two keys with same order) then we'll assume
            // that all items from prepend list have keys less than `refreshKey` and local items
            // with `refreshKey` should not be removed from the cached list.

            // If keys order is non-unique then all items with `refreshKey` are required to be
            // returned in prepend list, otherwise we can miss some items.
            // E.g. for list = [3,4] & refreshKey = 3 the remote list can be [1,2,3,3,4], if we
            // won't return all existing 3s in the prepend list ([1,2]) then the local merged list
            // will be [1,2,3,4] and we'll lose single 3. So we assume the prepend list must be
            // [1,2,3,3] and we'll have to remove all local items with 3 key before prepend.

            return list.prependIndex(KeyPoint(forRefreshKey), inclusive = !unique)
        }
    }

    override fun findAppendIndex(list: List<T>, nextPage: List<T>, forNextKey: K?): Int {
        // If the very first page is loaded then it should replace current cache, which should
        // actually be empty if we are loading first page.
        if (forNextKey == null) return -1

        // If keys order is unique (there are no two keys with same order) then we'll assume that
        // all items from the next page have keys greater than `nextKey` and local items with
        // `nextKey` should not be removed from the cached list.

        // If keys order is non-unique then all items with `nextKey` are required to be returned in
        // the next page, otherwise we can miss some items.
        // E.g. for list = [1,2,3] & nextKey = 3 the remote list can be [1,2,3,3,4], if we won't
        // return all existing 3s in the next page ([4]) then the local merged list will be
        // [1,2,3,4] and we'll lose single 3. So we assume the next page must be [3,3,4] and we'll
        // have to remove all local items with 3 key before append.

        return list.appendIndex(KeyPoint(forNextKey), inclusive = !unique)
    }

    private inner class KeyPoint(private val key: K) : Comparable<T> {
        override fun compareTo(other: T): Int = comparator.compare(key, getter(other))
    }

}


internal abstract class MergeComparable<T, K : Any> : MergeStrategy<T, K> {

    protected operator fun T.compareTo(other: Comparable<T>) = -other.compareTo(this)

    /**
     * Finds insertion index for the given point when prepending.
     */
    protected fun List<T>.prependIndex(point: Comparable<T>, inclusive: Boolean): Int {
        for ((index, item) in withIndex()) {
            if (inclusive) {
                // point = 2 & list = [3,4,6] -> 0
                // point = 3 & list = [3,4,6] -> 1
                // point = 4 & list = [4,4,6] -> 2
                // point = 5 & list = [3,4,6] -> 2
                // point = 6 & list = [3,4,6] -> 3
                // point = 7 & list = [3,4,6] -> 3
                if (item > point) return index
            } else {
                // point = 2 & list = [3,4,6] -> 0
                // point = 3¹ & list = [3²,4,6] -> 0 (remote can be [...,3¹,3³,3²,4,6])
                // point = 4¹ & list = [3,4¹,6] -> 1 (remote can be [...,4¹,4²,6])
                // point = 5 & list = [3,4,6] -> 2
                // point = 7 & list = [3,4,6] -> 3
                if (item >= point) return index // p=1 & list = [.1]
            }
        }
        return size
    }

    /**
     * Finds insertion index for the given point when appending.
     */
    protected fun List<T>.appendIndex(point: Comparable<T>, inclusive: Boolean): Int {
        for ((index, item) in asReversed().withIndex()) {
            if (inclusive) {
                // point = 2 & list = [3,4,6] -> 0
                // point = 3 & list = [3,4,6] -> 0
                // point = 4 & list = [3,4,6] -> 1
                // point = 5 & list = [3,4,6] -> 2
                // point = 6 & list = [3,4,6] -> 2
                // point = 7 & list = [3,4,6] -> 3
                if (item < point) return size - index
            } else {
                // point = 2 & list = [3,4,6] -> 0
                // point = 3¹ & list = [3²,4,6] -> 1 (remote can be [...,3²,3³,3¹,...])
                // point = 4¹ & list = [3,4¹,6] -> 2 (remote can be [...,4¹,4²,...])
                // point = 5 & list = [3,4,6] -> 2
                // point = 7 & list = [3,4,6] -> 3
                if (item <= point) return size - index
            }
        }
        return 0
    }

}
