package com.runner.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.runner.core.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE = "https://api.github.com/repos/hamaraman/runner/releases/latest"

private data class Release(val tag: String, val url: String)

/** 앱 시작 시 GitHub 최신 릴리스를 확인해 새 버전이면 다이얼로그를 띄운다. Play 스토어 설치본은 대신 Play 인앱 업데이트(즉시)를 띄운다. "나중에"는 하루 동안 다시 안 띄운다. 오프라인·실패는 조용히 무시. */
@Composable
fun UpdateCheck() {
    val context = LocalContext.current
    var release by remember { mutableStateOf<Release?>(null) }
    val prefs = remember { context.getSharedPreferences("update_check", Context.MODE_PRIVATE) }
    val later = { prefs.edit().putLong("laterAt", System.currentTimeMillis()).apply(); release = null }
    val playLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    LaunchedEffect(Unit) {
        if (context.installedFromPlay()) {
            val manager = AppUpdateManagerFactory.create(context)
            manager.appUpdateInfo.addOnSuccessListener { info ->
                val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                if (available && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    manager.startUpdateFlowForResult(info, playLauncher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
                }
            }
            return@LaunchedEffect
        }
        if (System.currentTimeMillis() - prefs.getLong("laterAt", 0) < 24 * 60 * 60 * 1000L) return@LaunchedEffect
        val latest = runCatching { fetchLatest() }.getOrNull() ?: return@LaunchedEffect
        if (isNewerVersion(latest.tag, context.versionName())) release = latest
    }
    val r = release ?: return
    AlertDialog(
        onDismissRequest = later,
        title = { Text("새 버전 ${r.tag}") },
        text = { Text("새 버전이 나왔어요. 받아서 설치하면 기록은 그대로 두고 업데이트돼요.") },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)))
                release = null
            }) { Text("받으러 가기") }
        },
        dismissButton = { TextButton(onClick = later) { Text("나중에") } },
    )
}

@Suppress("DEPRECATION")
private fun Context.installedFromPlay(): Boolean {
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= 30) packageManager.getInstallSourceInfo(packageName).installingPackageName
        else packageManager.getInstallerPackageName(packageName)
    }.getOrNull()
    return installer == "com.android.vending"
}

private fun Context.versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

private suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
    val conn = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    try {
        val json = Json.parseToJsonElement(conn.inputStream.bufferedReader().use { it.readText() }).jsonObject
        Release(json["tag_name"]!!.jsonPrimitive.content, json["html_url"]!!.jsonPrimitive.content)
    } finally {
        conn.disconnect()
    }
}
