package com.alexvasilkov.inflow.model

data class Question(
    val link: String,
    val title: String,
    val tags: List<String>,
    val score: Int,
    val views: Long,
    val answers: Int,
    val isAnswered: Boolean
)
