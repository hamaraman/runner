package com.runner.app.ui.run

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runner.app.tracking.RunTrackingService
import com.runner.app.tracking.TrackingState
import com.runner.app.tracking.TrackingStatus
import com.runner.app.ui.StatBlock
import com.runner.core.Pace

// 플립 커버 화면처럼 작은 창: 거리·시간·페이스와 조작 버튼만 크게 보여준다
@Composable
fun CoverRunScreen() {
    val context = LocalContext.current
    val tracking by TrackingState.state.collectAsStateWithLifecycle()
    // 커버 화면엔 확인 창이 비좁아서 종료는 두 번 눌러 확정한다
    var armedStop by remember { mutableStateOf(false) }
    fun send(action: String) = RunTrackingService.send(context, action)

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                when {
                    tracking.waitingForGps -> "GPS 찾는 중…"
                    tracking.autoPaused -> "자동 일시정지"
                    tracking.status == TrackingStatus.PAUSED -> "일시정지"
                    tracking.status == TrackingStatus.RUNNING -> "● 기록 중"
                    else -> "준비"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("%.2f km".format(tracking.distanceM / 1000), style = MaterialTheme.typography.displayMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBlock("시간", Pace.formatDuration(tracking.elapsedSec))
                StatBlock("페이스", Pace.format(tracking.currentPaceSec))
            }
            tracking.intervalText?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tracking.status) {
                    TrackingStatus.IDLE -> Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            // 권한 창은 커버 화면에서 제대로 안 보여서 펼친 화면으로 넘긴다
                            if (granted) send(RunTrackingService.ACTION_START)
                            else Toast.makeText(context, "폰을 펼쳐서 위치 권한을 허용해 주세요", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Icon(Icons.Filled.PlayArrow, "러닝 시작") }
                    else -> {
                        val running = tracking.status == TrackingStatus.RUNNING
                        FilledTonalButton(
                            onClick = { armedStop = false; send(if (running) RunTrackingService.ACTION_PAUSE else RunTrackingService.ACTION_RESUME) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (running) "일시정지" else "재개") }
                        Button(
                            onClick = { if (armedStop) { armedStop = false; send(RunTrackingService.ACTION_STOP) } else armedStop = true },
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { if (armedStop) Text("한 번 더") else Icon(Icons.Filled.Stop, "종료") }
                    }
                }
            }
        }
    }
}
