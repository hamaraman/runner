package com.runner.app

import android.app.Application
import com.runner.app.data.AppContainer
import java.io.File
import org.osmdroid.config.Configuration

class RunnerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // OpenStreetMap 타일 서버 정책상 앱 식별용 User-Agent 필수. 타일 캐시는 앱 전용 캐시 폴더에 둔다.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
