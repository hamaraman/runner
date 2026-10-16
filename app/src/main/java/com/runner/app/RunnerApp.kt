package com.runner.app

import android.app.Application
import com.runner.app.data.AppContainer

class RunnerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 죽기 직전 스택을 파일에 남기고 기본 처리(앱 종료)로 넘긴다. 다음 실행 때 CrashReport가 보고를 권한다.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { crashFile(this).writeText(crashText(this, e)) }
            previous?.uncaughtException(thread, e)
        }
        container = AppContainer(this)
    }
}
