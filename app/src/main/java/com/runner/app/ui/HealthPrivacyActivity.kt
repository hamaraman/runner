package com.runner.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Health Connect가 권한 설명을 요청하면 개인정보처리방침 웹페이지를 열고 바로 닫힌다. */
class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://hamaraman.github.io/runner/privacy.html"))) }
        finish()
    }
}
