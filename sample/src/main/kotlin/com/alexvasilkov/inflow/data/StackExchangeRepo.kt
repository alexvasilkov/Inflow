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
import inflow.MemoryCache
import inflow.inflow
import inflow.inflowPaged
import inflow.inflows
import inflow.map
import inflow.merge
import inflow.paging.PageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class StackExchangeRepo(
    private val api: StackExchangeApi,
    private val auth: StackExchangeAuth
) {

    private val isLoggedIn: Boolean; get() = auth.isLoggedIn

    init {
        CoroutineScope(Dispatchers.Default).launch {
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
            if (isLoggedIn) profile else null // Do not save profile if not logged-in after the call
        }

        // Refresh every minute. Cannot refresh profile if not logged-in.
        expiration(Expires.after(1.min) { if (isLoggedIn) it?.loadedAt ?: 0L else Long.MAX_VALUE })
    }


    // Keeping search requests cache globally
    private val searchRequests = inflows(factory = ::searchByQuery)

    fun searchQuestions(params: Flow<QuestionsQuery>): Inflow<QuestionsData> =
        searchRequests.merge(params)

    private fun searchByQuery(query: QuestionsQuery) = inflowPaged<Question> {
        var refreshedAt = Long.MAX_VALUE
        expiration(Expires.after(1.min) { refreshedAt }) // Automatic refresh in 1 minute

        pager<Long> {
            pageSize(20)
            loader { _, params ->
                delay(500L) // Make it noticeable

                val items = api.search(query.search, query.tag, params.count, params.key)
                    .items.map(QuestionJson::convert)

                if (params.key == null) refreshedAt = now() // Tracking last refresh time

                // Next key should be last item's activity + 1 sec. It will force the API to
                // return that last item again as well as any other items that changed at the
                // same second, otherwise we may lose items with the same activity time.
                PageResult(items, nextKey = items.lastOrNull()?.let { it.lastActivity + 1L })
            }
            identifyBy(Question::id)
            mergeBy(Question::lastActivity, Long::compareTo, inverse = true)
        }
    }.map {
        QuestionsData(query, it.items, it.hasNext)
    }


    /** Converts a number of minutes to milliseconds. */
    private val Int.min; get() = this * 60_000L

}
