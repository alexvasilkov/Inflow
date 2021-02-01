package com.alexvasilkov.inflow.data

import com.alexvasilkov.inflow.data.api.StackExchangeApi
import com.alexvasilkov.inflow.data.api.response.QuestionJson
import com.alexvasilkov.inflow.data.ext.now
import com.alexvasilkov.inflow.model.Profile
import com.alexvasilkov.inflow.model.Question
import com.alexvasilkov.inflow.model.QuestionsQuery
import inflow.Expires
import inflow.Inflow
import inflow.InflowsCache
import inflow.MemoryCache
import inflow.asInflow
import inflow.inflow
import inflow.map
import inflow.refreshIfExpired
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
                if (authorized) profile.refreshIfExpired() // Requesting profile update on login
                else profileCache.write(null) // Clearing profile cache on logout
            }
        }
    }

    private val profileCache = MemoryCache.create<Profile?>(null)

    val profile: Inflow<Profile?> = inflow {
        data(profileCache) { api.profile().items.first().convert() }

        expiration(
            Expires.after(60_000L) {
                if (auth.token == null) Long.MAX_VALUE // Cannot refresh profile if not authorized
                else it?.loadedAt ?: 0L
            }
        )
    }


    private val pageSize = 20

    // Keeping search requests cache globally
    private val searchByTagCache = InflowsCache.create<QuestionsQuery, QuestionsList?>()

    fun searchQuestions(params: Flow<QuestionsQuery>): Inflow<List<Question>?> = params
        .asInflow<QuestionsQuery, QuestionsList?> {
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
        .map { it?.items }

    private class QuestionsList(
        val items: List<Question>,
        val loadedAt: Long
    )

}
