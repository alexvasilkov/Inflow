package com.alexvasilkov.inflow.di

import com.alexvasilkov.inflow.data.StackExchangeAuth
import com.alexvasilkov.inflow.data.StackExchangeRepo
import com.alexvasilkov.inflow.data.api.StackExchangeApiProvider
import com.alexvasilkov.inflow.ui.profile.ProfileViewModel
import com.alexvasilkov.inflow.ui.profile.auth.AuthResultViewModel
import com.alexvasilkov.inflow.ui.questions.QuestionsListViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

private val dataModule = module {
    single { StackExchangeAuth() }
    single { StackExchangeApiProvider(get()).create() }
    single { StackExchangeRepo(get(), get()) }
}

private val viewModelsModule = module {
    viewModel { AuthResultViewModel(get()) }
    viewModel { QuestionsListViewModel(get()) }
    viewModel { ProfileViewModel(get(), get()) }
}


fun initDi() {
    startKoin {
        modules(dataModule, viewModelsModule)
    }
}
