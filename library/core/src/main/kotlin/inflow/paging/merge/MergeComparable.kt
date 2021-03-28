package inflow.paging.merge

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
                if (item >= point) return index
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
