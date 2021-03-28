package inflow.paging.identity

internal class IdentityById<T, ID : Any>(
    private val getter: (T) -> ID
) : IdentityProvider<T> {

    override fun distinct(list: List<T>): List<T> {
        return list.distinctBy(getter)
    }

    override fun remove(from: List<T>, items: List<T>): List<T> {
        val deletedIds = items.map(getter).toHashSet()
        return from.filter { !deletedIds.contains(getter(it)) }
    }

}
