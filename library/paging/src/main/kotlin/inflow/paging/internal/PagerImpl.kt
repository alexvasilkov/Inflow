package inflow.paging.internal

import inflow.LoadTracker
import inflow.paging.PageParams
import inflow.paging.PageResult
import inflow.paging.Paged
import inflow.paging.Pager
import inflow.paging.PagerConfig
import inflow.paging.PagingCache.Cache
import inflow.paging.PagingState
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

internal class PagerImpl<T, K : Any>(config: PagerConfig<T, K>) : Pager<T> {

    private val pageSize = requireNotNull(config.pageSize) { "Page size is required" }
    private val loader = config.loader
    private val identity = config.identity
    private val merger = config.merger
    private val cacheProvider = config.cache

    private val actionsMutex = Mutex()
    private val actionsGeneration = atomic(0)

    private val state = LazySharedFlow { withCache(readOnly = true, block = ::readInitialState) }
    override val display = state.flow
        .map { PagedImpl(it.local.items, it.local.hasNext || it.remote.hasNext) }


    init {
        // If merger is set we'll also need an identity provider
        if (merger != null) requireNotNull(identity) {
            """
            Identity provider is required when using 'mergeWith()' or 'mergeBy()'.
            Use 'identifyWith()' or 'identifyBy()' to set the identity provider.
            """.trimIndent()
        }

        // Register listener to be called when the cache is invalidated to re-read the data
        cacheProvider?.onInvalidate { cache ->
            val current = state.flow.first()
            rereadState(cache, current)
        }
    }


    override suspend fun refresh(tracker: LoadTracker) = callSafe {
        if (loader != null) {
            val state = withCache(readOnly = true) {
                state.flow.first() // Read state exclusively within cache to avoid races
            }
            val key = state.remote.refreshKey
            val params = PageParams.Refresh(key, pageSize)
            val result = loader.load(tracker, params)
            write(result, params)
        }
    }

    override suspend fun loadNext(tracker: LoadTracker) = callSafe {
        val state = withCache(readOnly = true) { cache ->
            val current = state.flow.first()
            when {
                current.local.hasNext -> {
                    requireNotNull(cache) { "Cannot read more from cache: no cache configured" }

                    // Trying to read more items from cache
                    val newSize = current.local.items.size + pageSize
                    val newLocal = cache.read(newSize)
                    val newState = State(newLocal, current.remote).also { state.emit(it) }
                    if (newLocal.items.size >= newSize) null else newState // Null if we have enough
                }
                else -> current
            }
        }

        // If there are no more items in the cache then we should get more items from the remote
        // source if it's specified and end of pagination isn't reached there yet
        if (state != null && loader != null && state.remote.hasNext) {
            val params = PageParams.Next(state.remote.nextKey, pageSize)
            val result = loader.load(tracker, params)
            write(result, params)
        }
    }

    /** Making sure that refresh and loadNext are never called concurrently. */
    private suspend fun callSafe(action: suspend () -> Unit) {
        val current = actionsGeneration.value
        actionsMutex.withLock { if (current == actionsGeneration.value) action() }
    }

    // Note: it will be called before the UI update is propagated, so extra refresh / next call is
    // still possible, but it is not critical.
    private fun skipPendingActions() = actionsGeneration.incrementAndGet()

    private suspend fun <R> withCache(readOnly: Boolean, block: suspend (Cache<T, K>?) -> R): R =
        cacheProvider?.exclusive(readOnly, block) ?: block(null)


    /** Saves remote call result. */
    private suspend fun write(result: PageResult<T, K>, params: PageParams<K>) {
        withCache(readOnly = false) { cache ->
            val current = state.flow.first()
            val new = when (params) {
                is PageParams.Refresh -> prepend(cache, current, result, params.key)
                is PageParams.Next -> append(cache, current, result, params.key)
            }
            state.emit(new)
        }
    }


    private suspend fun readInitialState(cache: Cache<T, K>?): State<T, K> {
        val local = cache?.read(pageSize) ?: PagedImpl(items = emptyList(), hasNext = false)
        val remote = readRemoteState(cache)
        return State(local, remote)
    }

    private suspend fun rereadState(cache: Cache<T, K>, state: State<T, K>): State<T, K> {
        val toRead = when {
            !state.local.hasNext -> Int.MAX_VALUE // We need to re-read everything
            else -> max(state.local.items.size, pageSize) // Read same amount but at least one page
        }
        val local = cache.read(toRead)
        val remote = readRemoteState(cache)
        return State(local, remote)
    }

    private suspend fun readRemoteState(cache: Cache<T, K>?): PagingState<K> = when {
        loader != null -> cache?.readState() ?: PagingState(hasNext = true)
        else -> PagingState(hasNext = false)
    }

    /** Prepending first page to the local cache. */
    private suspend fun prepend(
        cache: Cache<T, K>?,
        state: State<T, K>,
        result: PageResult<T, K>,
        refreshKey: K?
    ): State<T, K> {
        val localItems = state.local.items

        val mergeIndex = when {
            result.forceClearCacheOnRefresh -> -1
            // If first page is loaded and there are no more items - completely replace the cache
            // E.g. we have [1, 2, 3] in cache and first page is [2] -> we want the result to be [2]
            refreshKey == null && result.nextKey == null -> -1
            // If first page is loaded -> save it as-is (replace the cache)
            // If prepend list is loaded -> prepend it as-is (no merge possible)
            merger == null -> if (refreshKey == null) -1 else 0
            // Asking merger to provide correct prepend position
            else -> merger.findPrependIndex(result.items, localItems, refreshKey)
        }

        if (mergeIndex == -1) return replace(cache, result) // Replace the entire cache

        var newLocalItems = localItems

        // All newly loaded items should be removed from the cache before added again, also
        // we should remove a part of the cached list as defined by merger.
        val toDelete = result.items + localItems.subList(0, mergeIndex)
        if (toDelete.isNotEmpty()) {
            cache?.delete(toDelete)
            newLocalItems = identity?.delete(newLocalItems, toDelete) ?: newLocalItems
        }

        if (result.items.isNotEmpty()) {
            cache?.prepend(result.items)
            newLocalItems = result.items + newLocalItems
        }

        val local = PagedImpl(items = newLocalItems, hasNext = state.local.hasNext)

        val remote = PagingState(state.remote.hasNext, state.remote.nextKey, result.refreshKey)
        cache?.writeState(remote)

        return State(local, remote)
    }

    /** Appending new page to the local cache. */
    private suspend fun append(
        cache: Cache<T, K>?,
        state: State<T, K>,
        result: PageResult<T, K>,
        nextKey: K?
    ): State<T, K> {
        require(!state.local.hasNext) {
            """
            Cannot append new page: the local cache is not fully read.
            If you see this error please report it.
            """.trimIndent()
        }

        val localItems = state.local.items

        val mergeIndex = when {
            // No merger and loading the first page - no append
            nextKey == null && merger == null -> -1
            // No merger - just append to the end
            merger == null -> localItems.size
            // Asking merger to provide correct append position
            else -> merger.findAppendIndex(localItems, result.items, nextKey)
        }

        if (mergeIndex == -1) return replace(cache, result) // Replace the entire cache

        var newLocalItems = localItems

        // All newly loaded items should be removed from the cache before added again, also
        // we should remove a part of the cached list as defined by the merger.
        val toDelete = localItems.subList(mergeIndex, localItems.size) + result.items
        if (toDelete.isNotEmpty()) {
            cache?.delete(toDelete)
            newLocalItems = identity?.delete(newLocalItems, toDelete) ?: newLocalItems
        }

        if (result.items.isNotEmpty()) {
            cache?.append(result.items)
            newLocalItems = newLocalItems + result.items
        }

        val local = PagedImpl(items = newLocalItems, hasNext = false)

        val hasNext = result.nextKey != null
        val remote = PagingState(hasNext, result.nextKey, state.remote.refreshKey)
        cache?.writeState(remote)

        return State(local, remote)
    }

    /** Replacing local cache with the loaded data. */
    private suspend fun replace(
        cache: Cache<T, K>?,
        result: PageResult<T, K>
    ): State<T, K> {
        cache?.deleteAll()
        cache?.append(result.items)
        val local = PagedImpl(items = result.items, hasNext = false)

        val hasNext = result.nextKey != null
        val remote = PagingState(hasNext, result.nextKey, result.refreshKey)
        cache?.writeState(remote)

        // Avoiding pending refresh/loadNext actions as they were triggered for a now outdated data
        skipPendingActions()

        return State(local, remote)
    }


    private class State<T, K : Any>(
        @JvmField
        val local: Paged<T>,
        @JvmField
        val remote: PagingState<K>
    )
}
