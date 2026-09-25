package com.runner.app

import android.app.Application
import com.runner.app.data.AppContainer

class RunnerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
