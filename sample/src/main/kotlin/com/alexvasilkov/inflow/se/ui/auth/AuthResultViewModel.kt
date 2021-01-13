package com.alexvasilkov.inflow.se.ui.auth

import androidx.lifecycle.ViewModel
import com.alexvasilkov.inflow.se.data.StackExchangeAuth

class AuthResultViewModel(
    private val auth: StackExchangeAuth
) : ViewModel() {

    fun handlerRedirectUrl(url: String) = auth.handleAuthRedirect(url)

}
