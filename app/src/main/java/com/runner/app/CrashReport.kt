package com.runner.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File

// ponytail: 사용자가 "보내기"를 눌러야만 모인다. 자동 수집이 필요해지면 Firebase Crashlytics로 교체.
private const val NEW_ISSUE = "https://github.com/hamaraman/runner/issues/new"

fun crashFile(context: Context) = File(context.filesDir, "last_crash.txt")

fun crashText(context: Context, e: Throwable): String {
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    return "앱 $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n\n${e.stackTraceToString()}"
}

/** 지난 실행이 비정상 종료됐으면 GitHub 이슈로 오류 보고를 보낼지 묻는다. 어느 쪽을 골라도 기록은 지운다. */
@Composable
fun CrashReport() {
    val context = LocalContext.current
    val file = remember { crashFile(context) }
    var text by remember { mutableStateOf(runCatching { file.readText() }.getOrNull()) }
    val report = text ?: return
    val close = { file.delete(); text = null }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("앱이 예기치 않게 종료됐어요") },
        text = { Text("오류 내용을 보내주시면 고치는 데 큰 도움이 돼요. GitHub 이슈 작성 화면이 열려요.") },
        confirmButton = {
            TextButton(onClick = {
                // URL 길이 제한 때문에 스택은 앞부분만 보낸다
                val body = "무엇을 하다가 종료됐는지 적어주세요:\n\n\n```\n${report.take(5000)}\n```"
                val uri = Uri.parse(NEW_ISSUE).buildUpon()
                    .appendQueryParameter("title", "[크래시] " + report.lineSequence().drop(2).firstOrNull().orEmpty().take(80))
                    .appendQueryParameter("body", body)
                    .build()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                close()
            }) { Text("보내기") }
        },
        dismissButton = { TextButton(onClick = close) { Text("안 보내기") } },
    )
}
