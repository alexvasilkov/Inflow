package inflow.paging.identity

internal class IdentityByComparator<T>(
    private val comparator: (T, T) -> Boolean
) : IdentityProvider<T> {

    override fun distinct(list: List<T>): List<T> {
        val result = mutableListOf<T>()
        return list.filterTo(result) { item -> result.find { comparator(it, item) } == null }
    }

    override fun remove(from: List<T>, items: List<T>): List<T> {
        return from.filter { item -> items.find { comparator(it, item) } == null }
    }

}
