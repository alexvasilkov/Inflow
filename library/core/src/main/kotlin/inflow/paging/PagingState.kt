package inflow.paging

public class PagingState<K : Any>(
    @JvmField
    public val hasMore: Boolean,

    @JvmField
    public val nextKey: K? = null,

    @JvmField
    public val refreshKey: K? = null
)
