package com.alexvasilkov.inflow.ui.profile

import androidx.lifecycle.ViewModel
import com.alexvasilkov.inflow.data.StackExchangeAuth
import com.alexvasilkov.inflow.data.StackExchangeRepo
import com.alexvasilkov.inflow.model.Profile
import inflow.Inflow
import inflow.loading
import inflow.unhandledError
import kotlinx.coroutines.flow.Flow

class ProfileViewModel(
    private val auth: StackExchangeAuth,
    repo: StackExchangeRepo
) : ViewModel() {

    val authUrl: String = auth.authUrl

    val authState: Flow<Boolean> = auth.authState

    private val profileInflow: Inflow<Profile?> = repo.profile

    // While collecting this flow the loading and refresh API calls will be made automatically
    val profile: Flow<Profile?> = profileInflow.data()

    val loading: Flow<Boolean> = profileInflow.loading()

    val error: Flow<Throwable> = profileInflow.unhandledError()

    fun logout(): Unit = auth.logout()

}
