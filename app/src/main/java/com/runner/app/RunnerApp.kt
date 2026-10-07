package com.runner.app

import android.app.Application
import com.runner.app.data.AppContainer
import com.google.android.gms.ads.MobileAds

class RunnerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MobileAds.initialize(this)
    }
}
