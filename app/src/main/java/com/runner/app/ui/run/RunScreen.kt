package com.runner.app.ui.run

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runner.app.tracking.RunTrackingService
import com.runner.app.tracking.TrackingState
import com.runner.app.tracking.TrackingStatus
import com.runner.app.ui.DateTimeFmt
import com.runner.app.ui.SectionTitle
import com.runner.app.ui.StatBlock
import com.runner.app.ui.millisToLocal
import com.runner.app.ui.plan.label
import com.runner.app.ui.rememberContainer
import com.runner.core.Pace
import com.runner.core.RunRecord
import com.runner.core.WorkoutType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun RunScreen(onOpenRun: (String) -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val tracking by TrackingState.state.collectAsStateWithLifecycle()
    val runs by container.runs.state.collectAsStateWithLifecycle()
    val plan by container.plan.state.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            RunTrackingService.send(context, RunTrackingService.ACTION_START)
        } else {
            Toast.makeText(context, "러닝 기록에는 위치 권한이 필요해요", Toast.LENGTH_LONG).show()
        }
    }

    fun startRun() {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (hasLocation && !needsNotification) {
            RunTrackingService.send(context, RunTrackingService.ACTION_START)
        } else {
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    val active = tracking.status != TrackingStatus.IDLE
    val today = container.todayWorkout(plan)

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        if (!active && today != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("오늘의 훈련", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (today.type == WorkoutType.REST) "휴식일" else "${today.type.label} ${today.label()}" +
                                if (today.done) "  ✓ 완료" else "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(today.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StatBlock("킬로미터", "%.2f".format(tracking.distanceM / 1000), big = true)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatBlock("시간", Pace.formatDuration(tracking.elapsedSec))
                        StatBlock("평균 페이스", Pace.format(Pace.secPerKm(tracking.distanceM, tracking.elapsedSec)))
                    }
                    if (tracking.waitingForGps) {
                        Spacer(Modifier.height(8.dp))
                        Text("GPS 신호를 찾는 중… 하늘이 트인 곳에서 더 빨라요", style = MaterialTheme.typography.bodySmall)
                    }
                    if (active) {
                        RouteCanvas(tracking.points, Modifier.fillMaxWidth().height(200.dp).padding(top = 12.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    ControlButtons(
                        status = tracking.status,
                        onStart = { startRun() },
                        onPause = { RunTrackingService.send(context, RunTrackingService.ACTION_PAUSE) },
                        onResume = { RunTrackingService.send(context, RunTrackingService.ACTION_RESUME) },
                        onStop = { confirmStop = true },
                    )
                }
            }
        }
        if (!active) {
            item { WeeklySummary(runs) }
            item { SectionTitle("기록") }
            if (runs.isEmpty()) {
                item { Text("아직 기록이 없어요. 첫 러닝을 시작해보세요!", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(runs, key = { it.id }) { run -> RunRow(run) { onOpenRun(run.id) } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("러닝을 종료할까요?") },
            text = { Text("1분 미만이면서 50m 미만인 기록은 저장되지 않아요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    RunTrackingService.send(context, RunTrackingService.ACTION_STOP)
                }) { Text("저장하고 종료") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        confirmStop = false
                        RunTrackingService.send(context, RunTrackingService.ACTION_DISCARD)
                    }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmStop = false }) { Text("계속 달리기") }
                }
            },
        )
    }
}

@Composable
private fun ControlButtons(
    status: TrackingStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (status) {
            TrackingStatus.IDLE -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("러닝 시작", style = MaterialTheme.typography.titleMedium)
            }
            TrackingStatus.RUNNING, TrackingStatus.PAUSED -> {
                val running = status == TrackingStatus.RUNNING
                OutlinedButton(onClick = if (running) onPause else onResume, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "일시정지" else "재개")
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("종료")
                }
            }
        }
    }
}

@Composable
private fun WeeklySummary(runs: List<RunRecord>) {
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisWeek = runs.filter { !millisToLocal(it.startedAtMs).toLocalDate().isBefore(monday) }
    val km = thisWeek.sumOf { it.distanceM } / 1000
    val sec = thisWeek.sumOf { it.durationSec }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("이번 주", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBlock("거리", "%.1f km".format(km))
                StatBlock("횟수", "${thisWeek.size}회")
                StatBlock("시간", Pace.formatDuration(sec))
            }
        }
    }
}

@Composable
private fun RunRow(run: RunRecord, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(millisToLocal(run.startedAtMs).format(DateTimeFmt), style = MaterialTheme.typography.labelMedium)
                Text("%.2f km".format(run.distanceM / 1000), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Pace.formatDuration(run.durationSec))
                Text("${Pace.format(run.paceSecPerKm)}/km", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
