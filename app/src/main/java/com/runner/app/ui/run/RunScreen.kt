package com.runner.app.ui.run

import com.runner.app.ui.StatusPill
import com.runner.app.ui.EmptyState
import com.runner.app.ui.coachMarkTarget
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.runBlocking
import com.runner.app.data.HealthSync
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.runner.app.ui.WeekBars
import com.runner.app.ui.plan.color
import com.runner.app.ui.theme.Eyebrow
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
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import com.runner.app.ui.NativeAdCard
import com.runner.app.ui.SectionTitle
import com.runner.app.ui.StatBlock
import com.runner.app.ui.millisToLocal
import com.runner.app.ui.plan.label
import com.runner.app.ui.rememberContainer
import com.runner.core.Pace
import com.runner.core.Records
import com.runner.core.PersonalBests
import androidx.compose.runtime.produceState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.runner.core.RunRecord
import com.runner.core.Shoe
import com.runner.core.totalKm
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextOverflow
import com.runner.core.WorkoutType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun RunScreen(onOpenRun: (String) -> Unit, onOpenAllRoutes: () -> Unit, onOpenShoes: () -> Unit, onPrivacyOptions: (() -> Unit)? = null, ad: com.google.android.gms.ads.nativead.NativeAd? = null) {
    val context = LocalContext.current
    val container = rememberContainer()
    val tracking by TrackingState.state.collectAsStateWithLifecycle()
    val runs by container.runs.state.collectAsStateWithLifecycle()
    val plan by container.plan.state.collectAsStateWithLifecycle()
    val shoes by container.shoes.state.collectAsStateWithLifecycle()
    val draft by container.draft.state.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }
    var showCoachSettings by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var pickingShoe by remember { mutableStateOf(false) }
    val justSaved by container.justSaved.collectAsStateWithLifecycle()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    // 전체 백업: 시스템 저장 창으로 zip을 내보내고, 고른 zip으로 통째로 되돌린다
    fun backupJob(ok: String, fail: String, block: () -> Unit) = scope.launch {
        val success = withContext(Dispatchers.IO) { runCatching(block).isSuccess }
        Toast.makeText(context, if (success) ok else fail, Toast.LENGTH_SHORT).show()
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) backupJob("백업을 저장했어요", "백업에 실패했어요") {
            context.contentResolver.openOutputStream(uri)!!.use(container::exportBackup)
        }
    }
    // Health Connect: 권한을 받으면 지금까지 기록도 한 번에 보낸다 (이후엔 저장할 때마다 자동)
    var healthLinked by remember { mutableStateOf(false) }
    // PackageManager 조회라 매 recomposition마다 부르면 탭 전환이 끊긴다
    val healthAvailable = remember { HealthSync.available(context) }
    LaunchedEffect(Unit) { healthLinked = HealthSync.granted(context) }
    val healthPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        healthLinked = granted.containsAll(HealthSync.permissions)
        if (healthLinked && container.runs.value.isEmpty()) Toast.makeText(context, "연동됐어요. 보낼 기록은 아직 없어요", Toast.LENGTH_SHORT).show()
        else if (healthLinked) backupJob("기록 ${container.runs.value.size}개를 Health Connect로 보냈어요", "보내지 못했어요") {
            check(runBlocking { HealthSync.write(context, container.runs.value) } == 0)
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }

    // 권한 요청을 거쳐도 훈련 모드로 시작하도록 기억해 둔다
    var pendingGuidedDay by remember { mutableStateOf<Long?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val guidedDay = pendingGuidedDay.also { pendingGuidedDay = null }
        when {
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ->
                RunTrackingService.send(context, RunTrackingService.ACTION_START, guidedDay)
            // 대략적 위치는 오차가 수백 m라 경로를 기록할 수 없다
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ->
                Toast.makeText(context, "러닝 기록에는 '정확한 위치'가 필요해요. 설정 > 앱 > 위치에서 켜주세요", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(context, "러닝 기록에는 위치 권한이 필요해요", Toast.LENGTH_LONG).show()
        }
    }

    fun startRun(guidedDay: Long? = null) {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (hasLocation && !needsNotification) {
            RunTrackingService.send(context, RunTrackingService.ACTION_START, guidedDay)
        } else {
            pendingGuidedDay = guidedDay
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
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(today.type.color(), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("오늘의 훈련", style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (today.type == WorkoutType.REST) "휴식일" else "${today.type.label} ${today.label()}",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(today.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (today.done) StatusPill("완료", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    if (today.type != WorkoutType.REST) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { startRun(today.dateEpochDay) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("훈련 시작 · 음성 안내")
                        }
                    }
                    }
                }
            }
        }
        item {
            val heroColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = heroColor)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        tracking.autoPaused -> StatusPill("자동 일시정지", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                        tracking.status == TrackingStatus.PAUSED -> StatusPill("일시정지", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                        tracking.status == TrackingStatus.RUNNING -> StatusPill("● 기록 중", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        else -> Text("준비", style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.2f".format(tracking.distanceM / 1000), style = MaterialTheme.typography.displayLarge)
                        Text(" km", Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatBlock("시간", Pace.formatDuration(tracking.elapsedSec))
                        if (active) StatBlock("현재 페이스", Pace.format(tracking.currentPaceSec))
                        StatBlock("평균 페이스", Pace.format(Pace.secPerKm(tracking.distanceM, tracking.elapsedSec)))
                    }
                    tracking.intervalText?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    tracking.targetPace?.let { target ->
                        Spacer(Modifier.height(8.dp))
                        Text("목표 페이스 ${Pace.formatRange(target)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (tracking.autoPaused) {
                        Spacer(Modifier.height(8.dp))
                        Text("다시 달리면 이어서 기록해요", style = MaterialTheme.typography.bodySmall)
                    }
                    if (tracking.waitingForGps) {
                        Spacer(Modifier.height(8.dp))
                        Text("GPS 신호를 찾는 중… 하늘이 트인 곳에서 더 빨라요", style = MaterialTheme.typography.bodySmall)
                    }
                    if (active) {
                        RouteMap(tracking.points, Modifier.padding(top = 16.dp).fillMaxWidth().height(260.dp), follow = true)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!active) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Tile(Modifier.weight(1f).fillMaxHeight().coachMarkTarget("coach_coaching"), onClick = { showCoachSettings = true }) {
                                Icon(Icons.Filled.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text("코칭 설정", style = MaterialTheme.typography.titleMedium)
                            }
                            ShoeTile(shoes.firstOrNull { !it.retired }, runs, Modifier.weight(1f).fillMaxHeight().coachMarkTarget("coach_shoe")) { pickingShoe = true }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
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
            item {
                val goal by container.weeklyGoalKm.state.collectAsStateWithLifecycle()
                WeeklySummary(runs, goal) { showGoal = true }
            }
            if (runs.isNotEmpty()) item { BestsCard(runs) }
            if (ad != null) item(key = "ad") { NativeAdCard(ad) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("기록")
                    if (runs.any { it.points.size > 1 }) TextButton(onClick = onOpenAllRoutes) { Text("전체 경로 지도") }
                }
            }
            if (runs.isEmpty()) {
                item { EmptyState("아직 기록이 없어요. 첫 러닝을 시작해보세요!") }
            }
            items(runs, key = { it.id }) { run -> RunRow(run) { onOpenRun(run.id) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { saveBackup.launch("runner-backup-${LocalDate.now()}.zip") }) { Text("기록 백업") }
                    TextButton(onClick = { pickBackup.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("백업 불러오기") }
                }
            }
            if (healthAvailable) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // 이미 연동됐으면 권한 창 없이 바로 결과가 와서 전체 기록을 다시 보낸다
                        TextButton(onClick = { healthPermission.launch(HealthSync.permissions) }) {
                            Text(if (healthLinked) "Health Connect로 기록 다시 보내기" else "Health Connect 연동")
                        }
                    }
                }
            }
            if (onPrivacyOptions != null) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onPrivacyOptions) { Text("개인정보 설정") }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showCoachSettings) {
        CoachSettingsDialog(
            initial = container.coach.value,
            onDismiss = { showCoachSettings = false },
            onSave = { updated ->
                container.coach.update { updated }
                showCoachSettings = false
            },
        )
    }

    if (pickingShoe) {
        ShoePickerDialog(
            title = "러닝화 선택",
            selectedId = shoes.firstOrNull { !it.retired }?.id,
            allowNone = false,
            onPick = { shoe -> shoe?.let { container.selectShoe(it.id) }; pickingShoe = false },
            onRegister = { pickingShoe = false; onOpenShoes() },
            onDismiss = { pickingShoe = false },
        )
    }
    // 러닝을 마치면 신은 러닝화를 확인받는다. 시작 전에 깜빡 못 골랐어도 여기서 바로잡는다.
    justSaved?.takeIf { !active }?.let { runId ->
        ShoePickerDialog(
            title = "오늘 신은 러닝화",
            message = "이 러닝에 신은 러닝화를 골라주세요.",
            selectedId = runs.firstOrNull { it.id == runId }?.shoeId,
            allowNone = true,
            onPick = { shoe -> container.setRunShoe(runId, shoe?.id); container.justSaved.value = null },
            onRegister = { container.justSaved.value = null; onOpenShoes() },
            onDismiss = { container.justSaved.value = null },
        )
    }

    if (showGoal) {
        var text by remember { mutableStateOf(container.weeklyGoalKm.value.takeIf { it > 0 }?.let { "%.0f".format(it) } ?: "") }
        AlertDialog(
            onDismissRequest = { showGoal = false },
            title = { Text("주간 목표 거리") },
            text = {
                OutlinedTextField(
                    text, { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("km (비우면 목표 없음)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    container.weeklyGoalKm.update { text.toDoubleOrNull() ?: 0.0 }
                    showGoal = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showGoal = false }) { Text("취소") } },
        )
    }

    // 러닝 중 앱이 강제 종료돼 남은 임시 기록. 서비스가 돌고 있으면 진행 중인 기록이라 건드리지 않는다.
    draft?.takeIf { !active }?.let { d ->
        if (d.durationSec < 60 && d.distanceM < 50) {
            container.draft.update { null }
            return@let
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("중단된 러닝이 있어요") },
            text = { Text("앱이 종료되기 전까지의 기록(%.2f km · %s)을 저장할까요?".format(d.distanceM / 1000, Pace.formatDuration(d.durationSec))) },
            confirmButton = {
                TextButton(onClick = {
                    container.saveRun(d)
                    container.draft.update { null }
                }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { container.draft.update { null } }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("백업을 불러올까요?") },
            text = { Text("지금 있는 기록·훈련·신발 정보가 백업 내용으로 모두 바뀌어요.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    backupJob("백업을 불러왔어요", "불러오지 못했어요. 러너 백업 파일인지 확인해 주세요") {
                        context.contentResolver.openInputStream(uri)!!.use(container::importBackup)
                    }
                }) { Text("불러오기") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("취소") } },
        )
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
            TrackingStatus.IDLE -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp).coachMarkTarget("coach_start")) {
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
private fun WeeklySummary(runs: List<RunRecord>, goalKm: Double, onSetGoal: () -> Unit) {
    val today = LocalDate.now()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisWeek = runs.filter { !millisToLocal(it.startedAtMs).toLocalDate().isBefore(monday) }
    val km = thisWeek.sumOf { it.distanceM } / 1000
    val sec = thisWeek.sumOf { it.durationSec }
    val perDay = (0..6).map { d ->
        thisWeek.filter { millisToLocal(it.startedAtMs).toLocalDate() == monday.plusDays(d.toLong()) }.sumOf { it.distanceM } / 1000
    }
    val streak = Records.weekStreak(runs.map { millisToLocal(it.startedAtMs).toLocalDate() }, today)
    Card(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onSetGoal),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이번 주", style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (streak >= 2) Text("🔥 ${streak}주 연속", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.1f".format(km), style = MaterialTheme.typography.displaySmall)
                Text(" km", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${thisWeek.size}회", style = MaterialTheme.typography.titleMedium)
                    Text(Pace.formatDuration(sec), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (goalKm > 0) {
                val done = km >= goalKm
                LinearProgressIndicator(
                    progress = { (km / goalKm).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (done) "목표 %.0f km 달성! 🎉".format(goalKm) else "목표 %.0f km까지 %.1f km 남았어요".format(goalKm, goalKm - km),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("눌러서 주간 목표를 정해보세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            WeekBars(listOf("월", "화", "수", "목", "금", "토", "일"), perDay, today.dayOfWeek.value - 1)
        }
    }
}

/** 개인 최고 기록. GPS 경로가 없는 기록은 거리만 반영된다. */
@Composable
private fun BestsCard(runs: List<RunRecord>) {
    // 모든 GPS 포인트를 훑는 계산이라 탭 전환 때 끊기지 않게 백그라운드에서 한다
    val bests = produceState<PersonalBests?>(null, runs) { value = withContext(Dispatchers.Default) { Records.bests(runs) } }.value ?: return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text("개인 최고 기록", style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBlock("최장", bests.longestM?.let { "%.1fkm".format(it / 1000) } ?: "-")
                StatBlock("1km", bests.best1kSec?.let(Pace::formatDuration) ?: "-")
                StatBlock("5km", bests.best5kSec?.let(Pace::formatDuration) ?: "-")
                StatBlock("10km", bests.best10kSec?.let(Pace::formatDuration) ?: "-")
            }
        }
    }
}

/** 히어로 카드 안의 작은 버튼 칸. */
@Composable
private fun Tile(modifier: Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
    }
}

/** 지금 신는 러닝화의 누적 거리와 교체 시점까지의 진행도. */
@Composable
private fun ShoeTile(shoe: Shoe?, runs: List<RunRecord>, modifier: Modifier, onClick: () -> Unit) {
    Tile(modifier, onClick) {
        if (shoe == null) {
            Text("러닝화", style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("선택하기", style = MaterialTheme.typography.titleMedium)
            return@Tile
        }
        val km = shoe.totalKm(runs)
        val accent = if (km >= shoe.limitKm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        val photo = rememberContainer().shoePhoto(shoe)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                ShoePhoto(photo, 40.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(horizontalAlignment = if (photo != null) Alignment.Start else Alignment.CenterHorizontally) {
                Text(shoe.name, style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.1f".format(km), style = MaterialTheme.typography.titleLarge, color = if (km >= shoe.limitKm) accent else MaterialTheme.colorScheme.onSurface)
                    Text(" km", Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (km / shoe.limitKm).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun RunRow(run: RunRecord, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("%.2f km".format(run.distanceM / 1000), style = MaterialTheme.typography.titleLarge)
                Text(millisToLocal(run.startedAtMs).format(DateTimeFmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${Pace.format(run.paceSecPerKm)} /km", style = MaterialTheme.typography.titleMedium)
                Text(Pace.formatDuration(run.durationSec), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
