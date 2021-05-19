package inflow.paging.internal

import inflow.paging.Paged

internal class PagedImpl<T>(
    override val items: List<T>,
    override val hasNext: Boolean
) : Paged<T>
