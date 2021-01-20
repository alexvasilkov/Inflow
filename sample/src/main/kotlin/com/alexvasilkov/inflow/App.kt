package com.alexvasilkov.inflow

import android.app.Application
import com.alexvasilkov.inflow.di.initDi
import inflow.android.InflowAndroid

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        InflowAndroid.init(applicationContext) // TODO: Implement auto loading

        initDi()
    }

}
