package com.alexvasilkov.inflow.se.data.api.response

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class ResponseJson<T>(
    val items: List<T>
)
