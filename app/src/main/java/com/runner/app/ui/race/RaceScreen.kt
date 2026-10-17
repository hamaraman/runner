@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.runner.app.ui.race

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import com.runner.app.ui.DateTile
import com.runner.app.ui.EmptyState
import com.runner.app.ui.SoftCard
import com.runner.app.ui.StatusPill
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runner.app.ui.DateField
import com.runner.app.ui.FullDateFmt
import com.runner.app.ui.InfoCard
import com.runner.app.ui.SectionTitle
import com.runner.app.ui.TextInput
import com.runner.app.ui.dDay
import com.runner.app.ui.epochDay
import com.runner.app.ui.openUrl
import com.runner.app.ui.rememberContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.produceState
import com.runner.core.CalendarRace
import com.runner.core.Pace
import com.runner.core.RaceCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.runner.core.Race
import com.runner.core.RaceDistance
import java.time.LocalDate
import java.util.UUID

@Composable
fun RaceScreen(onMakePlan: (String) -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val races by container.races.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    // 탭 화면이 계속 살아 있어서 앱을 켠 동안 한 번만 불러온다
    var calendarTry by remember { mutableStateOf(0) }
    val calendar by produceState<Result<List<CalendarRace>>?>(null, calendarTry) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { RaceCalendar.fetch() } }
    }
    var query by remember { mutableStateOf("") }
    var distanceFilter by remember { mutableStateOf<RaceDistance?>(null) }
    var prefill by remember { mutableStateOf<CalendarRace?>(null) }
    var deleting by remember { mutableStateOf<Race?>(null) }

    val today = LocalDate.now().toEpochDay()
    val (upcoming, past) = races.sortedBy { it.dateEpochDay }.partition { it.dateEpochDay >= today }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (upcoming.isNotEmpty()) item { SectionTitle("내 대회") }
            items(upcoming, key = { it.id }) { r ->
                RaceCard(
                    r,
                    onMakePlan = { onMakePlan(r.id) },
                    onToggleRegistered = {
                        container.races.update { list -> list.map { if (it.id == r.id) it.copy(registered = !it.registered) else it } }
                    },
                    onDelete = { deleting = r },
                )
            }
            if (past.isNotEmpty()) {
                item { SectionTitle("지난 대회") }
                items(past.reversed(), key = { it.id }) { r ->
                    RaceCard(r, onMakePlan = null, onToggleRegistered = null, onDelete = { deleting = r })
                }
            }
            item { SectionTitle("전국 대회 일정") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput("대회 이름·장소 검색", query, { query = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(distanceFilter == null, { distanceFilter = null }, { Text("전체") })
                        RaceDistance.entries.forEach { d ->
                            FilterChip(distanceFilter == d, { distanceFilter = if (distanceFilter == d) null else d }, { Text(d.label) })
                        }
                    }
                }
            }
            val list = calendar?.getOrNull()
            when {
                calendar == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() } }
                list.isNullOrEmpty() -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("일정을 불러오지 못했어요", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { calendarTry++ }) { Text("다시 시도") }
                            OutlinedButton(onClick = { openUrl(context, RaceCalendar.URL) }) { Text("저스트런 열기") }
                        }
                    }
                }
                else -> {
                    val added = races.map { it.name to it.dateEpochDay }.toSet()
                    val shown = list.filter {
                        (query.isBlank() || query in it.name || query in it.place) && (distanceFilter == null || it.distance == distanceFilter)
                    }
                    if (shown.isEmpty()) item { EmptyState("조건에 맞는 대회가 없어요") }
                    items(shown, key = { "cal-" + it.no }) { r ->
                        CalendarRow(r, added = (r.name to r.date.toEpochDay()) in added) { prefill = r; adding = true }
                    }
                    item {
                        Text(
                            "일정 제공: 저스트런 (justrun.co.kr)",
                            Modifier.fillMaxWidth().clickable { openUrl(context, RaceCalendar.URL) }.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Box(Modifier.padding(40.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("대회 추가") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        RaceDialog(prefill, onDismiss = { adding = false; prefill = null }) { race ->
            val planned = container.addRace(race)
            adding = false
            prefill = null
            val msg = if (planned != null) "대회를 추가하고 '${planned.name}' 훈련 계획을 만들었어요" else "대회를 추가했어요"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    deleting?.let { race ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("'${race.name}'을(를) 삭제할까요?") },
            text = container.plan.value?.takeIf { it.raceDateEpochDay == race.dateEpochDay && it.raceName == race.name }
                ?.let { { Text("이 대회로 만든 훈련 계획도 함께 삭제돼요.") } },
            confirmButton = {
                TextButton(onClick = {
                    val planned = container.deleteRace(race)
                    deleting = null
                    if (planned != null) Toast.makeText(context, "훈련 계획을 '${planned.name}'에 맞춰 다시 만들었어요", Toast.LENGTH_LONG).show()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun RaceCard(r: Race, onMakePlan: (() -> Unit)?, onToggleRegistered: (() -> Unit)?, onDelete: () -> Unit) {
    val context = LocalContext.current
    val date = epochDay(r.dateEpochDay)
    val past = onToggleRegistered == null
    val dd = dDay(date)
    SoftCard {
        Column(Modifier.padding(16.dp)) {
            Row {
                DateTile(if (dd.startsWith("D+")) "완료" else "D-DAY", dd.removePrefix("D-").removePrefix("D+").let { if (it == "DAY") "오늘" else it }, accent = !past)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, "삭제", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        date.format(FullDateFmt) + if (r.location.isNotBlank()) " · ${r.location}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(r.distance.label, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                        if (!past) {
                            if (r.registered) {
                                StatusPill("신청 완료", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                            } else {
                                StatusPill("신청 전", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        r.goalTimeSec?.let {
                            Text("목표 ${Pace.formatDuration(it)}", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                        }
                    }
                }
            }
            if (!past || r.url.isNotBlank()) {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onToggleRegistered != null) {
                        FilterChip(
                            selected = r.registered,
                            onClick = onToggleRegistered,
                            label = { Text(if (r.registered) "신청함" else "신청 표시") },
                        )
                    }
                    if (onMakePlan != null) AssistChip(onClick = onMakePlan, label = { Text("훈련 계획 만들기") })
                    if (r.url.isNotBlank()) AssistChip(onClick = { openUrl(context, r.url) }, label = { Text("대회 페이지") })
                }
            }
        }
    }
}

@Composable
private fun RaceDialog(initial: CalendarRace?, onDismiss: () -> Unit, onSave: (Race) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().plusWeeks(8)) }
    var distance by remember { mutableStateOf(initial?.distance ?: RaceDistance.TEN_K) }
    var location by remember { mutableStateOf(initial?.place.orEmpty()) }
    var url by remember { mutableStateOf(initial?.homepage.orEmpty()) }
    var registered by remember { mutableStateOf(false) }
    var goal by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대회 추가") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("대회 이름", name, { name = it })
                DateField("날짜", date, { date = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RaceDistance.entries.forEach { d ->
                        FilterChip(selected = distance == d, onClick = { distance = d }, label = { Text(d.label) })
                    }
                }
                TextInput("장소 (선택)", location, { location = it })
                TextInput("대회 페이지 링크 (선택)", url, { url = it })
                TextInput("목표 기록 (선택)", goal, { goal = it }, placeholder = "예: 1:59:00")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = registered, onCheckedChange = { registered = it })
                    Text("이미 신청했어요")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = save@{
                val goalSec = if (goal.isBlank()) null else Pace.parseDuration(goal) ?: run {
                    Toast.makeText(context, "목표 기록 형식을 확인해주세요 (예: 1:59:00)", Toast.LENGTH_SHORT).show()
                    return@save
                }
                onSave(
                    Race(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        dateEpochDay = date.toEpochDay(),
                        distance = distance,
                        location = location.trim(),
                        url = url.trim(),
                        registered = registered,
                        goalTimeSec = goalSec,
                    ),
                )
            }) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 전국 대회 일정의 한 줄. 누르면 내용이 채워진 대회 추가 창이 뜬다. */
@Composable
private fun CalendarRow(r: CalendarRace, added: Boolean, onClick: () -> Unit) {
    SoftCard(Modifier.clickable(enabled = !added, onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    r.date.format(FullDateFmt) + if (r.place.isNotBlank()) " · ${r.place}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (r.courses.isNotBlank()) Text(r.courses, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (added) StatusPill("추가됨", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            else Icon(Icons.Filled.Add, "추가", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
