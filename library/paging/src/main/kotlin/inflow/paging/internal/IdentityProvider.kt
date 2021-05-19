package inflow.paging.internal

internal interface IdentityProvider<T> {
    fun delete(from: List<T>, items: List<T>): List<T>
}

internal class IdentityWithComparator<T>(
    private val comparator: (T, T) -> Boolean
) : IdentityProvider<T> {
    override fun delete(from: List<T>, items: List<T>): List<T> {
        return from.filter { item -> items.find { comparator(it, item) } == null }
    }
}

internal class IdentityById<T, ID : Any>(
    private val getter: (T) -> ID
) : IdentityProvider<T> {
    override fun delete(from: List<T>, items: List<T>): List<T> {
        val deletedIds = items.map(getter).toHashSet()
        return from.filter { !deletedIds.contains(getter(it)) }
    }
}
