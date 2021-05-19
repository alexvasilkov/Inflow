package inflow.paging

// TODO: Documentation
public interface PagingCache<T, K : Any> {
    public suspend fun <R> exclusive(readOnly: Boolean, block: suspend (Cache<T, K>) -> R): R

    public fun onInvalidate(action: suspend (Cache<T, K>) -> Unit)

    public interface Cache<T, K : Any> {
        public suspend fun read(count: Int): Paged<T>
        public suspend fun append(items: List<T>)
        public suspend fun prepend(items: List<T>)
        public suspend fun delete(items: List<T>)
        public suspend fun deleteAll()
        public suspend fun readState(): PagingState<K>?
        public suspend fun writeState(state: PagingState<K>)
    }
}

public class PagingState<K : Any>(
    @JvmField
    public val hasNext: Boolean,

    @JvmField
    public val nextKey: K? = null,

    @JvmField
    public val refreshKey: K? = null
)
