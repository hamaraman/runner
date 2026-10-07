import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(::load) }

android {
    namespace = "com.runner.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.runner.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        // 네이버 지도 Client ID는 local.properties의 naverMapClientId (커밋 금지). 없으면 지도만 인증 실패로 안 뜬다.
        manifestPlaceholders["naverMapClientId"] = localProps.getProperty("naverMapClientId", "")
        // AdMob App ID는 local.properties의 admobAppId. 없으면 구글 공식 테스트 ID로 동작한다.
        manifestPlaceholders["admobAppId"] = localProps.getProperty("admobAppId", "ca-app-pub-3940256099942544~3347511713")
        resValue("string", "admob_banner_id", "ca-app-pub-3940256099942544/9214589741")
    }

    // 서명 키는 local.properties의 releaseStoreFile 등 (커밋 금지). 없으면 unsigned로 빌드된다.
    val releaseSigning = localProps.getProperty("releaseStoreFile")?.let { path ->
        signingConfigs.create("release") {
            storeFile = file(path)
            storePassword = localProps.getProperty("releaseStorePassword")
            keyAlias = localProps.getProperty("releaseKeyAlias")
            keyPassword = localProps.getProperty("releaseKeyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // 실 배너 ID는 릴리스에서만 (local.properties의 admobBannerId). 디버그는 테스트 광고 — 내 광고 클릭으로 계정 정지 방지.
            localProps.getProperty("admobBannerId")?.let { resValue("string", "admob_banner_id", it) }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.location)
    implementation(libs.naver.map)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
