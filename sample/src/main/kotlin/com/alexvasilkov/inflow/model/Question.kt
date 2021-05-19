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

class QuestionsResult(
    val query: QuestionsQuery,
    override val items: List<Question>,
    override val hasNext: Boolean
) : Paged<Question>
