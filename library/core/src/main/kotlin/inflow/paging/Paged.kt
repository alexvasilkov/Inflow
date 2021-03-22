package inflow.paging

public open class Paged<T>(
    @JvmField
    public val items: List<T>,

    @JvmField
    public val hasNext: Boolean
)
