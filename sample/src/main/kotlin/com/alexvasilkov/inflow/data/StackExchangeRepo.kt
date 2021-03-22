package com.alexvasilkov.inflow.data

import com.alexvasilkov.inflow.data.api.StackExchangeApi
import com.alexvasilkov.inflow.data.api.response.QuestionJson
import com.alexvasilkov.inflow.data.ext.now
import com.alexvasilkov.inflow.model.Profile
import com.alexvasilkov.inflow.model.Question
import com.alexvasilkov.inflow.model.QuestionsData
import com.alexvasilkov.inflow.model.QuestionsQuery
import inflow.Expires
import inflow.Inflow
import inflow.InflowsCache
import inflow.MemoryCache
import inflow.inflow
import inflow.paging.Pager
import inflow.paging.pagedInflow
import inflow.toInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class StackExchangeRepo(
    private val api: StackExchangeApi,
    private val auth: StackExchangeAuth
) {

    init {
        GlobalScope.launch {
            auth.authState.collect { authorized ->
                // Requesting profile update on login. Clearing profile cache on logout.
                if (authorized) profile.refreshIfExpired() else profileCache.write(null)
            }
        }
    }

    private val profileCache = MemoryCache.create<Profile?>(null)

    val profile: Inflow<Profile?> = inflow {
        data(profileCache) {
            val profile = api.profile().items.first().convert()
            // Do not save profile if not logged-in after the call
            if (auth.isLoggedIn) profile else null
        }

        expiration(
            // Refresh every minute. Cannot refresh profile if not logged-in.
            Expires.after(60_000L) { if (auth.isLoggedIn) it?.loadedAt ?: 0L else Long.MAX_VALUE }
        )
    }


    // Keeping search requests cache globally
    private val searchByTagCache = InflowsCache.create<QuestionsQuery, QuestionsData>()

    fun searchQuestions(params: Flow<QuestionsQuery>): Inflow<QuestionsData> = params.toInflow {
        factory { query ->
            pagedInflow {
                // Initial empty value is pretending to be fresh to avoid automatic refresh
                val initial = QuestionsData(query, now(), emptyList(), hasNext = true)
                // Setting up paging logic
                pager(SearchPager(api, query, initial))
                // Automatic refresh in 1 minute
                expiration(Expires.after(60_000L, QuestionsData::refreshedAt))
            }
        }
        cache(searchByTagCache)
    }


    private class SearchPager(
        private val api: StackExchangeApi,
        private val query: QuestionsQuery,
        initial: QuestionsData
    ) : Pager<Question, QuestionsData>() {
        private val pageSize = 20
        private val cache = MutableStateFlow(initial)
        private val nextKey = AtomicReference<Long?>(null)

        override fun cache() = cache

        override suspend fun refresh() = load(fromKey = null)

        override suspend fun loadNext() = load(fromKey = nextKey.get())

        private suspend fun load(fromKey: Long?) {
            delay(1500L) // Make it noticeable
            val page = api.search(query.search, query.tag, pageSize, fromKey)
                .items.map(QuestionJson::convert)

            val current = cache.value
            val items = current.items

            val newItems: List<Question>
            val newKey: Long?
            val refreshedAt: Long

            if (fromKey == null) {
                if (page.isEmpty()) {
                    newItems = emptyList()
                    newKey = null
                } else {
                    val lastOnPage = page.last().lastActivity

                    // Getting first item that is same as page's last item or further
                    val index = items.indexOfFirst { it.lastActivity <= lastOnPage }

                    if (index < 0) {
                        // No intersection, have to clear other pages
                        newItems = page
                        newKey = lastOnPage
                        // Avoiding pending refresh/loadNext actions as they were triggered for a
                        // now outdated data
                        skipPendingActions()
                    } else {
                        // Merging first page into cached list
                        val ids = page.map(Question::id).toHashSet()
                        val rest = items.subList(index, items.size).filter { !ids.contains(it.id) }

                        newItems = page + rest
                        newKey = nextKey.get()
                    }
                }
                refreshedAt = now()
            } else {
                // Merging new page into existing list
                val ids = page.map(Question::id).toHashSet()
                val first = items.filter { !ids.contains(it.id) }

                newItems = first + page
                newKey = newItems.last().lastActivity
                refreshedAt = current.refreshedAt
            }

            nextKey.set(newKey)
            cache.value = QuestionsData(query, refreshedAt, newItems, hasNext = page.isNotEmpty())
        }
    }

}
