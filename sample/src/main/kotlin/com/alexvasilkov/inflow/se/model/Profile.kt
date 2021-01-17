package com.alexvasilkov.inflow.se.model

data class Profile(
    val name: String,
    val image: String,
    val reputation: Int,
    val link: String,
    val loadedAt: Long
)
