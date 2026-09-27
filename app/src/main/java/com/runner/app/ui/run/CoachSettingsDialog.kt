package com.runner.app.ui.run

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.runner.app.ui.TextInput
import com.runner.core.CoachSettings
import com.runner.core.Pace

@Composable
fun CoachSettingsDialog(initial: CoachSettings, onDismiss: () -> Unit, onSave: (CoachSettings) -> Unit) {
    val context = LocalContext.current
    var voice by remember { mutableStateOf(initial.voiceEnabled) }
    var km by remember { mutableStateOf(initial.kmAnnounce) }
    var paceAlert by remember { mutableStateOf(initial.paceAlert) }
    var autoPause by remember { mutableStateOf(initial.autoPause) }
    var manualPace by remember {
        mutableStateOf(initial.manualTargetPaceSec?.let { "%d:%02d".format(it / 60, it % 60) }.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("코칭 설정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow("음성 안내", "이어폰으로 안내를 들려줘요", voice) { voice = it }
                SwitchRow("1km마다 안내", "구간 기록과 평균 페이스", km, enabled = voice) { km = it }
                SwitchRow("목표 페이스 알림", "빠르거나 느리면 알려줘요 (시작 2분 후부터)", paceAlert, enabled = voice) { paceAlert = it }
                SwitchRow("자동 일시정지", "신호 대기 등으로 멈추면 기록을 잠시 멈춰요", autoPause) { autoPause = it }
                TextInput("기본 목표 페이스 (선택)", manualPace, { manualPace = it }, placeholder = "예: 6:00")
                Text(
                    "오늘 훈련표에 목표 페이스가 있으면 그걸 먼저 써요. 인터벌 날에는 페이스 알림을 하지 않아요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = save@{
                val paceSec = if (manualPace.isBlank()) null else Pace.parseDuration(manualPace)?.toInt()
                if (manualPace.isNotBlank() && (paceSec == null || paceSec !in 150..1200)) {
                    Toast.makeText(context, "페이스는 6:00처럼 입력해주세요 (2:30~20:00)", Toast.LENGTH_SHORT).show()
                    return@save
                }
                onSave(CoachSettings(voice, km, paceAlert, autoPause, paceSec))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}
