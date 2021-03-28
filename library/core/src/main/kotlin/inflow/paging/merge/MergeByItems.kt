package inflow.paging.merge

internal class MergeByItems<T, K : Any>(
    private val comparator: Comparator<in T>,
    private val unique: Boolean
) : MergeComparable<T, K>() {

    override fun prepend(prepend: List<T>, list: List<T>, refreshKey: K?): Int {
        if (list.isEmpty()) return -1 // No local items, clearing the cache to avoid inconsistency

        if (refreshKey == null) {
            // If refresh key is null then we just loaded the first page and want to merge it.

            // No items in the first page (but the next key is not null), nothing we can do
            if (prepend.isEmpty()) return 0

            val lastItem = ItemPoint(prepend.last())

            // We can only merge the first page if it has intersection with the cached list.
            // prepend = [1,2] & list = [3,4,6] -> -1 (remote can be [1,2,2.1,...,2.9,3,4,6])
            if (list.first() > lastItem) return -1 // Clearing the cache to force next page

            return list.prependIndex(lastItem, inclusive = unique)
        } else {
            // If refresh key is not null then we loaded all the items newer than local list, but
            // still with potential overlapping.

            if (prepend.isEmpty()) return 0 // No newer items

            val lastItem = ItemPoint(prepend.last())

            return list.prependIndex(lastItem, inclusive = unique)
        }
    }

    override fun append(list: List<T>, nextPage: List<T>, nextKey: K?): Int {
        // If the very first page is loaded then it should replace current cache, which should
        // actually be empty if we are loading first page.
        if (nextKey == null) return -1

        if (nextPage.isEmpty()) return list.size // No newer items, it's fine

        val firstItem = ItemPoint(nextPage.first())

        return list.appendIndex(firstItem, inclusive = unique)
    }

    private inner class ItemPoint(private val item: T) : Comparable<T> {
        override fun compareTo(other: T): Int = comparator.compare(item, other)
    }

}
