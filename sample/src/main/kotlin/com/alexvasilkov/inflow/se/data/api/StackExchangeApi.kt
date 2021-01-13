package com.alexvasilkov.inflow.se.data.api

import com.alexvasilkov.inflow.se.data.api.response.QuestionJson
import com.alexvasilkov.inflow.se.data.api.response.ResponseJson
import retrofit2.http.GET
import retrofit2.http.Query

interface StackExchangeApi {

    @GET("search/advanced?order=desc&sort=activity&site=stackoverflow")
    suspend fun search(
        @Query("q") query: String,
        @Query("tagged") tags: String,
        @Query("pagesize") pageSize: Int
    ): ResponseJson<QuestionJson>

    companion object {
        const val apiUrl = "https://api.stackexchange.com/2.2/"

        private const val clientId = 19433
        const val authRedirectUrl = "inflow://auth"
        const val authUrl =
            "https://stackexchange.com/oauth/dialog?client_id=$clientId&redirect_uri=$authRedirectUrl"

        const val questionUrl = "https://stackoverflow.com/questions/"
    }

}
