package com.alexvasilkov.inflow.ui.profile.auth

import androidx.lifecycle.ViewModel
import com.alexvasilkov.inflow.data.StackExchangeAuth

class AuthResultViewModel(
    private val auth: StackExchangeAuth
) : ViewModel() {

    fun handlerRedirectUrl(url: String) = auth.handleAuthRedirect(url)

}
