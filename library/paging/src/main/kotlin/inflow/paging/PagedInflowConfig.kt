package inflow.paging

import inflow.CacheWriter
import inflow.DataLoader
import inflow.InflowConfig
import inflow.MemoryCache
import inflow.internal.InternalAccess
import inflow.paging.internal.PagerImpl
import kotlinx.coroutines.flow.Flow

private const val dataUnsupportedError = "Use pager() instead"

/**
 * Same as [InflowConfig] but requires a [Pager] that will be responsible for pagination logic.
 */
public class PagedInflowConfig<T> internal constructor() : InflowConfig<Paged<T>>() {

    /**
     * A custom [Pager] implementation that should handle the entire pagination logic.
     */
    public fun pager(pager: Pager<T>) {
        InternalAccess.setData(this, pager.display, { pager.refresh(it) }, { pager.loadNext(it) })
    }

    /**
     * Configures a predefined [Pager] that will handle pagination logic automatically using
     * specified [loader][PagerConfig.loader] and/or [cache][PagerConfig.cache] provider, including
     * a few extra options, see [PagerConfig].
     *
     * Returns [PagingCache] instance that can be used to consistently update the cache, both
     * pager's primary in-memory cache and optional cache set with [PagerConfig.cache].
     */
    public fun <K : Any> pager(config: (PagerConfig<T, K>.() -> Unit)) {
        pager(PagerImpl(PagerConfig<T, K>().apply(config)))
    }


    // Hiding regular `data()` methods from being accidentally used

    @Deprecated(message = dataUnsupportedError, level = DeprecationLevel.HIDDEN)
    override fun data(cache: Flow<Paged<T>>, loader: DataLoader<Unit>) {
        throw UnsupportedOperationException(dataUnsupportedError)
    }

    @Deprecated(message = dataUnsupportedError, level = DeprecationLevel.HIDDEN)
    override fun <R> data(cache: Flow<Paged<T>>, writer: CacheWriter<R>, loader: DataLoader<R>) {
        throw UnsupportedOperationException(dataUnsupportedError)
    }

    @Deprecated(message = dataUnsupportedError, level = DeprecationLevel.HIDDEN)
    override fun data(cache: MemoryCache<Paged<T>>, loader: DataLoader<Paged<T>>) {
        throw UnsupportedOperationException(dataUnsupportedError)
    }

    @Deprecated(message = dataUnsupportedError, level = DeprecationLevel.HIDDEN)
    override fun data(initial: Paged<T>, loader: DataLoader<Paged<T>>) {
        throw UnsupportedOperationException(dataUnsupportedError)
    }

}
