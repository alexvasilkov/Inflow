package inflow.paging.identity

internal interface IdentityProvider<T> {

    fun distinct(list: List<T>): List<T>

    fun remove(from: List<T>, items: List<T>): List<T>

}
