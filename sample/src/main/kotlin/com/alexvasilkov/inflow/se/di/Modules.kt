package com.alexvasilkov.inflow.se.di

import com.alexvasilkov.inflow.se.data.StackExchangeAuth
import com.alexvasilkov.inflow.se.data.StackExchangeRepo
import com.alexvasilkov.inflow.se.data.api.StackExchangeApiProvider
import com.alexvasilkov.inflow.se.ui.auth.AuthResultViewModel
import com.alexvasilkov.inflow.se.ui.list.QuestionsListViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

private val dataModule = module {
    single { StackExchangeAuth() }
    single { StackExchangeApiProvider(get()).create() }
    single { StackExchangeRepo(get()) }
}

private val viewModelsModule = module {
    viewModel { AuthResultViewModel(get()) }
    viewModel { QuestionsListViewModel(get()) }
}

val stackExchangeModules = listOf(dataModule, viewModelsModule)