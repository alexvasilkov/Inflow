package inflow.paging

import inflow.LoadTracker
import inflow.internal.LazySharedFlow
import inflow.paging.identity.IdentityNone
import inflow.paging.merge.MergeDefault
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PagerImpl<T, K : Any>(config: PagerConfig<T, K>) : Pager<T>() {

    private val pageSize = requireNotNull(config.pageSize) { "Page size is required" } // TODO: >0
    private val loader = config.loader
    private val hasLoader = config.loader != null
    private val cacheDelegate = config.cache
    private val identity = config.identity ?: IdentityNone()
    private val merger = config.merger ?: MergeDefault()

    private val actionsMutex = Mutex()
    private val actionsGeneration = atomic(0)
    private val cacheAccessMutex = Mutex()

    private val state = LazySharedFlow(::readFirstPage)


    override val cache = state.flow.map { Paged(it.items, it.hasMore || it.source.hasMore) }

    override suspend fun refresh(tracker: LoadTracker): Unit = callSafe(tracker) {
        val key = state.flow.first().source.refreshKey
        val params = PageParams.Refresh(key, pageSize)
        write(loader!!.invoke(tracker, params), params)
    }

    override suspend fun loadNext(tracker: LoadTracker): Unit = callSafe(tracker) {
        val readResult = readMore()

        if (!readResult) {
            val key = state.flow.first().source.nextKey
            val params = PageParams.Next(key, pageSize)
            write(loader!!.invoke(tracker, params), params)
        }
    }

    private suspend fun callSafe(tracker: LoadTracker, action: suspend (LoadTracker) -> Unit) {
        val current = actionsGeneration.value
        actionsMutex.withLock { if (current == actionsGeneration.value) action(tracker) }
    }

    // Note: it will be called before the UI update is propagated, so extra refresh / next call is
    // still possible, but it is not critical.
    private fun skipPendingActions() = actionsGeneration.incrementAndGet()


    /**
     * Reading more items from the cache if possible.
     * Returns `false` if we need to ask remote source for more items, `true` otherwise.
     */
    private suspend fun readMore(): Boolean =
        cacheAccessMutex.withLock {
            val current = state.flow.first()

            if (current.hasMore) {
                val more = readNext(current)
                state.emit(more)
                more.hasMore || !(hasLoader && more.source.hasMore)
            } else {
                !(hasLoader && current.source.hasMore)
            }
        }

    private suspend fun readFirstPage(): State<T, K> {
        val items = cacheDelegate?.read(0, pageSize) ?: emptyList()

        val sourceState = when {
            hasLoader -> cacheDelegate?.readState() ?: PagingState(hasMore = true)
            else -> PagingState(hasMore = false)
        }

        return State(items = items, hasMore = items.size >= pageSize, source = sourceState)
    }

    private suspend fun readNext(current: State<T, K>): State<T, K> {
        val offset = current.items.size
        val extraItems = cacheDelegate?.read(offset, pageSize) ?: emptyList()

        return State(
            items = current.items + extraItems,
            hasMore = extraItems.size >= pageSize,
            source = current.source
        )
    }


    /**
     * Saves remote call result.
     */
    private suspend fun write(result: PageResult<T, K>, params: PageParams<K>) =
        cacheAccessMutex.withLock {
            val current = state.flow.first()
            // TODO: Loaded refresh may cover more than we have in memory, in which case we need
            // to read more items from cache until it is exhausted or we cover the loaded list...
            val new = write(result, params, current)
            state.emit(new)
        }

    private suspend fun write(
        result: PageResult<T, K>,
        params: PageParams<K>,
        current: State<T, K>
    ): State<T, K> = when (params) {
        is PageParams.Refresh -> prepend(result, params.key, current)
        is PageParams.Next -> append(result, params.key, current)
    }

    private suspend fun prepend(
        result: PageResult<T, K>,
        refreshKey: K?,
        current: State<T, K>
    ): State<T, K> {
        if (result.forceClearCacheOnRefresh) return replace(result)

        // No way we can properly prepend items with no identity
        if (identity is IdentityNone) return replace(result)

        // If we loaded entire remote list while trying to refresh it then replace the entire cache
        if (refreshKey == null && result.nextKey == null) return replace(result)

        val mergeIndex = merger.prepend(result.items, current.items, refreshKey)

        // Replace the entire cache if merger asked to do so
        if (mergeIndex == -1) return replace(result)

        // All newly loaded items should be removed from the cache before added again, also
        // we should remove a part of the cached list as defined by merger.
        val toDelete = identity.distinct(result.items + current.items.subList(0, mergeIndex))
        val clearedItems = identity.remove(current.items, toDelete)

        // If cached list is fully replaced then replace the entire cache instead
        if (clearedItems.isEmpty() && !current.hasMore) return replace(result)

        val items = result.items + clearedItems
        val sourceState = PagingState(
            hasMore = current.source.hasMore,
            nextKey = current.source.nextKey,
            refreshKey = result.refreshKey
        )

        if (cacheDelegate != null) {
            cacheDelegate.delete(toDelete)
            cacheDelegate.insert(result.items)
            cacheDelegate.saveState(sourceState)
        }

        return State(items = items, hasMore = current.hasMore, source = sourceState)
    }

    private suspend fun append(
        result: PageResult<T, K>,
        nextKey: K?,
        current: State<T, K>
    ): State<T, K> {
        val mergeIndex = merger.append(current.items, result.items, nextKey)

        // Replace the entire cache if merger asked to do so
        if (mergeIndex == -1) return replace(result)

        // All newly loaded items should be removed from the cache before added again, also
        // we should remove a part of the cached list as defined by merger.
        val deletedPart = current.items.subList(mergeIndex, current.items.size)
        val toDelete = identity.distinct(deletedPart + result.items)
        val clearedItems = identity.remove(current.items, toDelete)

        val items = clearedItems + result.items
        val sourceState = PagingState(
            hasMore = result.nextKey != null,
            nextKey = result.nextKey,
            refreshKey = current.source.refreshKey
        )

        if (cacheDelegate != null) {
            cacheDelegate.delete(toDelete)
            cacheDelegate.insert(result.items)
            cacheDelegate.saveState(sourceState)
        }

        return State(items = items, hasMore = false, source = sourceState)
    }

    private suspend fun replace(result: PageResult<T, K>): State<T, K> {
        cacheDelegate?.deleteAll()
        if (result.items.isNotEmpty()) cacheDelegate?.insert(result.items)

        val sourceState = PagingState(
            hasMore = result.nextKey != null,
            nextKey = result.nextKey,
            refreshKey = result.refreshKey
        )

        // Avoiding pending refresh/loadNext actions as they were triggered for a now outdated data
        skipPendingActions()

        return State(items = result.items, hasMore = false, source = sourceState)
    }


    private class State<T, K : Any>(
        @JvmField
        val items: List<T>,

        @JvmField
        val hasMore: Boolean,

        @JvmField
        val source: PagingState<K>
    )

}
