package com.alexvasilkov.inflow.data

import com.alexvasilkov.inflow.data.api.StackExchangeApi
import com.alexvasilkov.inflow.data.api.response.QuestionJson
import com.alexvasilkov.inflow.data.ext.now
import com.alexvasilkov.inflow.model.Profile
import com.alexvasilkov.inflow.model.Question
import com.alexvasilkov.inflow.model.QuestionsQuery
import inflow.Expires
import inflow.Inflow
import inflow.MemoryCacheWriter
import inflow.asInflow
import inflow.inflow
import inflow.inflowsCache
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
                else profileCacheWriter(null) // Clearing profile cache on logout
            }
        }
    }

    private lateinit var profileCacheWriter: MemoryCacheWriter<Profile?>

    val profile: Inflow<Profile?> = inflow {
        profileCacheWriter = data(initial = null) { api.profile().items.first().convert() }

        val expirationIfAuthorized = Expires.after<Profile?>(60_000L) { it?.loadedAt ?: 0L }
        expiration {
            if (auth.token == null) Long.MAX_VALUE // Cannot refresh profile if not authorized
            else expirationIfAuthorized.expiresIn(it)
        }
    }


    private val pageSize = 20

    // Keeping search requests cache globally
    private val searchByTagCache = inflowsCache<QuestionsQuery, Inflow<QuestionsList?>>()

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
