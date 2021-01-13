package com.alexvasilkov.inflow.se.model

data class Question(
    val link: String,
    val title: String,
    val tags: List<String>,
    val score: Int,
    val views: Long,
    val answers: Int,
    val isAnswered: Boolean
)
