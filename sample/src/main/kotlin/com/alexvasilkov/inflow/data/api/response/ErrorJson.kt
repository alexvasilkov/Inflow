package com.alexvasilkov.inflow.data.api.response

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class ErrorJson(
    val error_message: String
)
