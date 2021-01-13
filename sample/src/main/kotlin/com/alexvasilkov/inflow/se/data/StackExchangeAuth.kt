package com.alexvasilkov.inflow.se.data

import com.alexvasilkov.inflow.ext.now
import com.alexvasilkov.inflow.se.data.api.StackExchangeApi

class StackExchangeAuth {

    private var auth: Auth? = null // Only keeping it in memory
    val token: String?
        get() = auth?.let { if (it.expiresAt <= now()) null else it.token }

    val authUrl: String = StackExchangeApi.authUrl

    fun handleAuthRedirect(url: String) {
        if (!url.startsWith(StackExchangeApi.authRedirectUrl)) return

        val hashParts = url.split('#')
        require(hashParts.size == 2) { "Does not have url params after #" }

        val params = hashParts[1].split('&')
            .map {
                val parts = it.split('=')
                require(parts.size == 2) { "Invalid param" }
                Pair(parts[0], parts[1])
            }
            .toMap()

        val token = params["access_token"] ?: error("No access token")
        val expiresIn = params["expires"]?.toLong()
        val expiresAt = if (expiresIn == null) Long.MAX_VALUE else now() + expiresIn * 1000L
        auth = Auth(token, expiresAt)
    }

    fun logout() {
        auth = null
    }


    private class Auth(
        val token: String,
        val expiresAt: Long
    )

}
