package com.alexvasilkov.inflow.data.api.response

import com.alexvasilkov.inflow.data.api.StackExchangeApi
import com.alexvasilkov.inflow.model.Question
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class QuestionJson(
    val question_id: Long,
    val title: String,
    val tags: List<String>,
    val score: Int,
    val view_count: Long,
    val answer_count: Int,
    val is_answered: Boolean
) {

    fun convert() = Question(
        link = StackExchangeApi.questionUrl + question_id,
        title = title,
        tags = tags,
        score = score,
        views = view_count,
        answers = answer_count,
        isAnswered = is_answered
    )

}
