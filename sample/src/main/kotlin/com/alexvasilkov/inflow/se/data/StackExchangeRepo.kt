package com.alexvasilkov.inflow.se.data

import com.alexvasilkov.inflow.ext.now
import com.alexvasilkov.inflow.se.data.api.StackExchangeApi
import com.alexvasilkov.inflow.se.data.api.response.QuestionJson
import com.alexvasilkov.inflow.se.model.Question
import com.alexvasilkov.inflow.se.model.QuestionsQuery
import inflow.ExpiresIn
import inflow.Inflow
import inflow.asInflow
import inflow.inflowsCache
import inflow.map
import kotlinx.coroutines.flow.Flow

class StackExchangeRepo(
    private val api: StackExchangeApi
) {

    private val pageSize = 20

    // Keeping search requests cache globally to survive the screen close
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
                expiration(ExpiresIn(60_000L) { it?.loadedAt ?: 0L })
                // Do not show cached data older than 3 minutes
                invalidation(ExpiresIn(3 * 60_000L) { it?.loadedAt ?: 0L }, null)
            }
            cache(searchByTagCache)
        }
        .map { it?.items }


    private class QuestionsList(
        val items: List<Question>,
        val loadedAt: Long
    )

}
