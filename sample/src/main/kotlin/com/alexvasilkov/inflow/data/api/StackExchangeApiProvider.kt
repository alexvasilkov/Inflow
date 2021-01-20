package com.alexvasilkov.inflow.data.api

import com.alexvasilkov.inflow.BuildConfig
import com.alexvasilkov.inflow.data.StackExchangeAuth
import com.alexvasilkov.inflow.data.api.response.ErrorJson
import com.alexvasilkov.inflow.model.StackExchangeException
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import retrofit2.Invocation
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class StackExchangeApiProvider(
    private val auth: StackExchangeAuth
) {

    fun create(): StackExchangeApi {
        val moshi = Moshi.Builder().build()

        val client = OkHttpClient.Builder()
            .handleErrors(moshi)
            .setApiKey()
            .setAuth()
            .setDebugLogger(Level.BODY)
            .build()

        return Retrofit.Builder()
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .baseUrl(StackExchangeApi.apiUrl)
            .build()
            .create(StackExchangeApi::class.java)
    }


    private fun OkHttpClient.Builder.setDebugLogger(logLevel: Level): OkHttpClient.Builder {
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(if (BuildConfig.DEBUG) logLevel else Level.NONE)
        return addInterceptor(interceptor)
    }

    private fun OkHttpClient.Builder.setApiKey() = addInterceptor { chain ->
        val url = chain.request().url
        val newUrl = url.newBuilder().addQueryParameter("key", StackExchangeApi.apiKey).build()
        val newRequest = chain.request().newBuilder().url(newUrl).build()
        chain.proceed(newRequest)
    }

    private fun OkHttpClient.Builder.setAuth() = addInterceptor { chain ->
        val request = if (chain.request().isAuthRequired()) {
            val token = auth.token ?: throw StackExchangeException("Not authenticated")
            val url = chain.request().url
            val newUrl = url.newBuilder().addQueryParameter("access_token", token).build()
            chain.request().newBuilder().url(newUrl).build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private fun Request.isAuthRequired(): Boolean = annotation(AuthRequired::class.java) != null

    // Retrofit conveniently saves original method info, so that we can check its annotations
    private fun <T : Annotation> Request.annotation(annotationClass: Class<T>): T? =
        tag(Invocation::class.java)?.method()?.getAnnotation(annotationClass)

    private fun OkHttpClient.Builder.handleErrors(moshi: Moshi) = addInterceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return@addInterceptor response

        if (response.code == 401) auth.logout()

        throw try {
            response.body?.source()?.use { source ->
                val error = moshi.adapter(ErrorJson::class.java).fromJson(source)
                StackExchangeException(error?.error_message ?: "Empty error body")
            } ?: StackExchangeException("Null error body")
        } catch (th: Throwable) {
            StackExchangeException("Cannot parse error response: ${th.message}")
        }
    }

}
