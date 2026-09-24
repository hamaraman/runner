@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.runner.app.ui.race

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
import com.runner.core.Pace
import com.runner.core.Race
import com.runner.core.RaceDistance
import java.time.LocalDate
import java.util.UUID

/** 전국 마라톤 대회 일정을 모아 보여주는 외부 사이트 */
private const val RACE_CALENDAR_URL = "http://www.roadrun.co.kr/schedule/list.php"

@Composable
fun RaceScreen(onMakePlan: (String) -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val races by container.races.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Race?>(null) }

    val today = LocalDate.now().toEpochDay()
    val (upcoming, past) = races.sortedBy { it.dateEpochDay }.partition { it.dateEpochDay >= today }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                InfoCard("나가고 싶은 대회를 등록하면 D-day를 보여주고, 그 날짜에 맞춘 훈련 계획을 바로 만들 수 있어요. 일정과 접수 기간은 반드시 주최 측 공지로 확인하세요.")
            }
            item {
                OutlinedButton(onClick = { openUrl(context, RACE_CALENDAR_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Link, null)
                    Text("  전국 마라톤 일정 보기 (마라톤온라인)")
                }
            }
            item { SectionTitle("다가오는 대회") }
            if (upcoming.isEmpty()) {
                item { Text("등록된 대회가 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
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
        RaceDialog(onDismiss = { adding = false }) { race ->
            container.races.update { it + race }
            adding = false
            Toast.makeText(context, "대회를 추가했어요", Toast.LENGTH_SHORT).show()
        }
    }
    deleting?.let { race ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("'${race.name}'을(를) 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    container.races.update { list -> list.filterNot { it.id == race.id } }
                    deleting = null
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(dDay(date), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text("${date.format(FullDateFmt)} · ${r.distance.label}" + if (r.location.isNotBlank()) " · ${r.location}" else "")
            r.goalTimeSec?.let { Text("목표 기록 ${Pace.formatDuration(it)}", style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                if (onToggleRegistered != null) {
                    FilterChip(
                        selected = r.registered,
                        onClick = onToggleRegistered,
                        label = { Text(if (r.registered) "신청 완료" else "신청 전") },
                    )
                }
                if (onMakePlan != null) AssistChip(onClick = onMakePlan, label = { Text("훈련 계획 만들기") })
                if (r.url.isNotBlank()) AssistChip(onClick = { openUrl(context, r.url) }, label = { Text("대회 페이지") })
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "삭제") }
            }
        }
    }
}

@Composable
private fun RaceDialog(onDismiss: () -> Unit, onSave: (Race) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusWeeks(8)) }
    var distance by remember { mutableStateOf(RaceDistance.TEN_K) }
    var location by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
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
