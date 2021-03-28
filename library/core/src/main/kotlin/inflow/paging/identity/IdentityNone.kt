package inflow.paging.identity

internal class IdentityNone<T> : IdentityProvider<T> {

    override fun distinct(list: List<T>) = list

    override fun remove(from: List<T>, items: List<T>) = from

}
