@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.runner.app.ui.plan

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextDecoration
import com.runner.app.ui.SoftCard
import com.runner.app.ui.StatusPill
import com.runner.app.ui.theme.Eyebrow
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runner.app.ui.DateField
import com.runner.app.ui.DateFmt
import com.runner.app.ui.FullDateFmt
import com.runner.app.ui.InfoCard
import com.runner.app.ui.NumberField
import com.runner.app.ui.SectionTitle
import com.runner.app.ui.TextInput
import com.runner.app.ui.dDay
import com.runner.app.ui.epochDay
import com.runner.app.ui.rememberContainer
import com.runner.app.ui.theme.WorkoutColors
import com.runner.core.Pace
import com.runner.core.RaceDistance
import com.runner.core.TrainingPlan
import com.runner.core.TrainingPlanGenerator
import com.runner.core.Workout
import com.runner.core.WorkoutType
import java.time.LocalDate

fun Workout.label(): String = if (distanceKm > 0) "%.1f km".format(distanceKm) else ""

@Composable
fun WorkoutType.color(): Color = when (this) {
    WorkoutType.REST -> WorkoutColors.rest
    WorkoutType.EASY -> WorkoutColors.easy
    WorkoutType.LONG -> WorkoutColors.long
    WorkoutType.TEMPO -> WorkoutColors.tempo
    WorkoutType.INTERVAL -> WorkoutColors.interval
    WorkoutType.RACE -> WorkoutColors.race
}

@Composable
fun PlanScreen(raceId: String?) {
    val container = rememberContainer()
    val plan by container.plan.state.collectAsStateWithLifecycle()
    var forceForm by rememberSaveable(raceId) { mutableStateOf(raceId != null) }

    val current = plan
    if (current == null || forceForm) {
        PlanForm(
            raceId = raceId,
            hasExisting = current != null,
            onCancel = { forceForm = false },
            onCreated = { forceForm = false },
        )
    } else {
        PlanView(current, onNewPlan = { forceForm = true })
    }
}

@Composable
private fun PlanForm(raceId: String?, hasExisting: Boolean, onCancel: () -> Unit, onCreated: () -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val race = remember(raceId) { container.races.value.firstOrNull { it.id == raceId } }

    var goal by rememberSaveable { mutableStateOf(race?.distance ?: RaceDistance.TEN_K) }
    var raceName by rememberSaveable { mutableStateOf(race?.name.orEmpty()) }
    var raceDay by rememberSaveable { mutableStateOf(race?.dateEpochDay ?: LocalDate.now().plusWeeks(10).toEpochDay()) }
    var weeklyKm by rememberSaveable { mutableStateOf("15") }
    var runs by rememberSaveable { mutableStateOf(4) }
    var recentDist by rememberSaveable { mutableStateOf<RaceDistance?>(null) }
    var recentTime by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("훈련 계획 만들기", style = MaterialTheme.typography.headlineSmall)
        Text("목표 대회와 지금 체력에 맞춰 주차별 훈련표를 만들어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("목표 거리")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RaceDistance.entries.forEach { d ->
                FilterChip(selected = goal == d, onClick = { goal = d }, label = { Text(d.label) })
            }
        }
        TextInput("대회 이름 (선택)", raceName, { raceName = it }, placeholder = "예: 가을 하프마라톤")
        DateField("대회 날짜", epochDay(raceDay), { raceDay = it.toEpochDay() })
        NumberField("요즘 주간 러닝 거리", weeklyKm, { weeklyKm = it }, suffix = "km")

        SectionTitle("주 몇 회 달릴 수 있나요?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (3..6).forEach { n -> FilterChip(selected = runs == n, onClick = { runs = n }, label = { Text("${n}회") }) }
        }

        SectionTitle("최근 기록 (선택 · 목표 페이스 계산용)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RaceDistance.entries.forEach { d ->
                FilterChip(
                    selected = recentDist == d,
                    onClick = { recentDist = if (recentDist == d) null else d },
                    label = { Text(d.label) },
                )
            }
        }
        if (recentDist != null) {
            TextInput("기록", recentTime, { recentTime = it }, placeholder = "예: 55:30 또는 1:58:00")
        }
        InfoCard("훈련표는 일반적인 러닝 코칭 원칙(주간 거리 10% 이내 증가, 4주마다 회복 주, 대회 전 테이퍼)으로 생성돼요. 몸 상태에 맞게 조절하고, 통증이 있으면 쉬어주세요.")

        Button(
            onClick = create@{
                val zones = recentDist?.let { d ->
                    val sec = Pace.parseDuration(recentTime) ?: run {
                        Toast.makeText(context, "기록 형식을 확인해주세요 (예: 55:30)", Toast.LENGTH_SHORT).show()
                        return@create
                    }
                    Pace.zones(d.km, sec, goal)
                }
                try {
                    val newPlan = TrainingPlanGenerator.generate(
                        TrainingPlanGenerator.Input(
                            goal = goal,
                            raceDate = epochDay(raceDay),
                            today = LocalDate.now(),
                            currentWeeklyKm = weeklyKm.toDoubleOrNull() ?: 0.0,
                            runsPerWeek = runs,
                            raceName = raceName.trim(),
                            zones = zones,
                        ),
                    )
                    container.setPlan(newPlan)
                    onCreated()
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, e.message ?: "입력값을 확인해주세요", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (hasExisting) "새 계획으로 바꾸기" else "훈련 계획 만들기") }
        if (hasExisting) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("기존 계획 유지") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlanView(plan: TrainingPlan, onNewPlan: () -> Unit) {
    val container = rememberContainer()
    val today = LocalDate.now().toEpochDay()
    val listState = rememberLazyListState()
    var confirmReset by remember { mutableStateOf(false) }

    val allWorkouts = plan.weeks.flatMap { it.workouts }
    val totalRuns = allWorkouts.count { it.type != WorkoutType.REST && it.dateEpochDay <= today }
    val doneRuns = allWorkouts.count { it.type != WorkoutType.REST && it.done }

    // 오늘이 속한 주로 스크롤 (헤더 1개 + 주마다 1개 아이템)
    val currentWeek = plan.weeks.indexOfFirst { w -> w.workouts.any { it.dateEpochDay == today } }
    LaunchedEffect(plan.raceDateEpochDay) {
        if (currentWeek > 0) listState.scrollToItem(currentWeek + 1)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val raceDate = epochDay(plan.raceDateEpochDay)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("목표 대회", style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.raceName.ifBlank { "${plan.goal.label} 대회" }, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${raceDate.format(FullDateFmt)} · ${plan.goal.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(dDay(raceDate), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row {
                        Text("완료한 훈련", style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("$doneRuns / ${totalRuns}회 · ${plan.weeks.size}주 계획", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (totalRuns == 0) 0f else doneRuns.toFloat() / totalRuns },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                        gapSize = 2.dp,
                        drawStopIndicator = {},
                    )
                    plan.zones?.let { z ->
                        Spacer(Modifier.height(16.dp))
                        Text("목표 페이스 (추정, /km)", style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ZoneTile("대회", Pace.format(z.race.toDouble()), WorkoutType.RACE, Modifier.weight(1f))
                            ZoneTile("이지", Pace.formatRange(z.easy), WorkoutType.EASY, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ZoneTile("템포", Pace.formatRange(z.tempo), WorkoutType.TEMPO, Modifier.weight(1f))
                            ZoneTile("인터벌", Pace.formatRange(z.interval), WorkoutType.INTERVAL, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = onNewPlan) { Text("새 계획") }
                        TextButton(onClick = { confirmReset = true }) { Text("계획 삭제", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        plan.weeks.forEach { week ->
            item(key = "w${week.index}") {
                val isCurrent = week.workouts.any { it.dateEpochDay == today }
                SoftCard(highlight = isCurrent) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${week.index + 1}주차", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            StatusPill(week.phase, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text("${week.totalKm} km", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        week.workouts.forEach { w ->
                            WorkoutRow(w, isToday = w.dateEpochDay == today) { checked ->
                                container.setWorkoutDone(w.dateEpochDay, checked)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("훈련 계획을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    container.plan.update { null }
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun ZoneTile(label: String, value: String, type: WorkoutType, modifier: Modifier = Modifier) {
    Row(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(type.color(), CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
        }
    }
}

@Composable
private fun WorkoutRow(w: Workout, isToday: Boolean, onToggle: (Boolean) -> Unit) {
    val rest = w.type == WorkoutType.REST
    val bg = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).background(bg, MaterialTheme.shapes.small)
            .height(IntrinsicSize.Min).padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 종류 색 막대 — 색만으로 구분하지 않도록 옆에 항상 종류 이름을 쓴다
        Box(Modifier.width(4.dp).fillMaxHeight().background(w.type.color(), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${epochDay(w.dateEpochDay).format(DateFmt)}${if (isToday) " · 오늘" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${w.type.label} ${w.label()}",
                style = MaterialTheme.typography.titleSmall,
                color = if (rest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (w.done) TextDecoration.LineThrough else null,
            )
            if (!rest) {
                Text(w.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!rest) {
            Checkbox(checked = w.done, onCheckedChange = onToggle)
        }
    }
}
