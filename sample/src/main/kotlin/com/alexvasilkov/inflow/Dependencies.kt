package com.alexvasilkov.inflow

import com.alexvasilkov.inflow.data.StackExchangeAuth
import com.alexvasilkov.inflow.data.StackExchangeRepo
import com.alexvasilkov.inflow.data.api.StackExchangeApiProvider

class Dependencies {

    val auth by lazy { StackExchangeAuth() }

    private val api by lazy { StackExchangeApiProvider(auth).create() }

    val repo by lazy { StackExchangeRepo(api, auth) }

}
