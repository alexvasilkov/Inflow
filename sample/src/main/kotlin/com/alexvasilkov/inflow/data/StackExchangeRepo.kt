package com.alexvasilkov.inflow.data

import com.alexvasilkov.inflow.data.api.StackExchangeApi
import com.alexvasilkov.inflow.data.api.response.QuestionJson
import com.alexvasilkov.inflow.data.ext.now
import com.alexvasilkov.inflow.model.Profile
import com.alexvasilkov.inflow.model.QuestionsList
import com.alexvasilkov.inflow.model.QuestionsQuery
import inflow.Expires
import inflow.Inflow
import inflow.InflowsCache
import inflow.MemoryCache
import inflow.inflow
import inflow.refreshIfExpired
import inflow.toInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
            if (auth.token == null) null else profile
        }

        expiration(
            Expires.after(60_000L) {
                // Cannot refresh profile if not logged-in
                if (auth.token == null) Long.MAX_VALUE else it?.loadedAt ?: 0L
            }
        )
    }


    private val pageSize = 20

    // Keeping search requests cache globally
    private val searchByTagCache = InflowsCache.create<QuestionsQuery, QuestionsList?>()

    fun searchQuestions(params: Flow<QuestionsQuery>): Inflow<QuestionsList?> = params.toInflow {
        builder { query ->
            data(initial = null) {
                val items = api.search(query.search, query.tag, pageSize).items
                    .map(QuestionJson::convert)
                QuestionsList(items, now())
            }
            // Automatic refresh in 1 minute
            expiration(Expires.after(60_000L) { it?.loadedAt ?: 0L })
            // Consider cached data older than 3 minutes as invalid and never show it
            invalidation(emptyValue = null, Expires.after(3 * 60_000L) { it?.loadedAt ?: 0L })
        }
        cache(searchByTagCache)
    }

}
