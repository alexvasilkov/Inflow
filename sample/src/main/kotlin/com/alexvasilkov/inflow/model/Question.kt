package com.alexvasilkov.inflow.model

import inflow.paging.Paged

data class Question(
    val id: Long,
    val link: String,
    val title: String,
    val tags: List<String>,
    val score: Int,
    val views: Long,
    val answers: Int,
    val isAnswered: Boolean,
    val lastActivity: Long,
)

data class QuestionsQuery(
    val search: String,
    val tag: String
)

class QuestionsData(
    val query: QuestionsQuery,
    val refreshedAt: Long,
    items: List<Question>,
    hasNext: Boolean
) : Paged<Question>(items, hasNext)
