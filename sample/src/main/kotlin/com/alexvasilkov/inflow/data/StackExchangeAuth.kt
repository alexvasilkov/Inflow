package com.alexvasilkov.inflow.data

import com.alexvasilkov.inflow.data.api.StackExchangeApi
import com.alexvasilkov.inflow.data.ext.now
import inflow.Expires
import inflow.Inflow
import inflow.cache
import inflow.cached
import inflow.inflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class StackExchangeAuth {

    val authUrl: String = StackExchangeApi.authUrl

    private val authCache = MutableStateFlow<Auth?>(null) // Only keeping it in memory

    // Using an Inflow for auth data to automatically emit `null` when token becomes invalid
    private val auth: Inflow<Auth?> = inflow {
        data(cache = authCache, loader = {})
        expiration(Expires.never()) // No way we can refresh it automatically
        invalidation(emptyValue = null, Expires.at { it?.expiresAt ?: Long.MAX_VALUE })
    }

    val authState: Flow<Boolean> = auth.cache().map { it != null }

    val token: String?; get() = runBlocking { auth.cached()?.token } // Should never block

    /** Parses access token out of callback url. */
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
        authCache.value = Auth(token, expiresAt)
    }

    fun logout() {
        authCache.value = null
    }


    private class Auth(
        val token: String,
        val expiresAt: Long
    )

}
