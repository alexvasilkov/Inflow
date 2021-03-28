package inflow.paging.merge

internal interface MergeStrategy<T, K : Any> {

    fun prepend(prepend: List<T>, list: List<T>, refreshKey: K?): Int

    fun append(list: List<T>, nextPage: List<T>, nextKey: K?): Int

    // TODO: Validate order?

}
