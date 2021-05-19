package com.alexvasilkov.inflow.ui.ext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.alexvasilkov.inflow.App
import com.alexvasilkov.inflow.Dependencies

inline fun <reified VM : ViewModel> ViewModelStoreOwner.viewModel(
    noinline factory: Dependencies.() -> VM
) = lazy { ViewModelProvider(this, viewModelFactory(factory)).get(VM::class.java) }

fun <VM : ViewModel> viewModelFactory(factory: Dependencies.() -> VM) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(aClass: Class<T>): T = factory(App.dependencies) as T
    }
