package com.alexvasilkov.inflow

import android.app.Application
import com.alexvasilkov.inflow.se.di.stackExchangeModules
import inflow.android.InflowAndroid
import org.koin.core.context.startKoin

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        InflowAndroid.init(applicationContext) // TODO: Implement auto loading

        startKoin {
            modules(stackExchangeModules)
        }
    }

}
