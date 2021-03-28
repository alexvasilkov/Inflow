package inflow.paging

public interface PagingCache<T, K : Any> {

    public suspend fun read(offset: Int, count: Int): List<T>

    public suspend fun insert(items: List<T>)

    public suspend fun delete(items: List<T>)

    public suspend fun deleteAll()

    public suspend fun update(item: T) {}

    // TODO: Makes no sense if no source is set
    public suspend fun readState(): PagingState<K>

    public suspend fun saveState(state: PagingState<K>)

}
