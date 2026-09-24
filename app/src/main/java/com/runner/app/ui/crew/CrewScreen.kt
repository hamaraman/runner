@file:OptIn(ExperimentalMaterial3Api::class)

package com.runner.app.ui.crew

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.runner.app.ui.DateTimeFmt
import com.runner.app.ui.InfoCard
import com.runner.app.ui.NumberField
import com.runner.app.ui.SectionTitle
import com.runner.app.ui.TextInput
import com.runner.app.ui.millisToLocal
import com.runner.app.ui.rememberContainer
import com.runner.app.ui.shareText
import com.runner.core.Crew
import com.runner.core.CrewEvent
import com.runner.core.Pace
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Composable
fun CrewScreen() {
    val context = LocalContext.current
    val container = rememberContainer()
    val crews by container.crews.state.collectAsStateWithLifecycle()
    val events by container.events.state.collectAsStateWithLifecycle()

    var creatingCrew by remember { mutableStateOf(false) }
    var eventFor by remember { mutableStateOf<Crew?>(null) }
    var deleting by remember { mutableStateOf<Crew?>(null) }

    val now = System.currentTimeMillis() - 3 * 60 * 60 * 1000L // 시작 3시간 후까지는 '예정'으로 표시
    val upcoming = events.filter { it.dateTimeMs >= now }.sortedBy { it.dateTimeMs }
    val crewName = crews.associate { it.id to it.name }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                InfoCard("크루와 모임은 현재 이 기기에만 저장돼요. 모임을 만들고 공유 버튼으로 카카오톡 등에 초대 메시지를 보내보세요. (서버 연동은 다음 단계)")
            }
            item { SectionTitle("다가오는 모임") }
            if (upcoming.isEmpty()) {
                item { Text("예정된 모임이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(upcoming, key = { it.id }) { e ->
                EventCard(
                    e,
                    crewName[e.crewId].orEmpty(),
                    onToggle = {
                        container.events.update { list -> list.map { if (it.id == e.id) it.copy(attending = !it.attending) else it } }
                    },
                    onShare = { shareText(context, inviteText(e, crewName[e.crewId].orEmpty())) },
                    onDelete = { container.events.update { list -> list.filterNot { it.id == e.id } } },
                )
            }
            item { SectionTitle("내 크루") }
            if (crews.isEmpty()) {
                item { Text("아래 버튼으로 첫 크루를 만들어보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(crews, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(c.area, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { deleting = c }) { Icon(Icons.Filled.Delete, "크루 삭제") }
                        }
                        if (c.description.isNotBlank()) Text(c.description, style = MaterialTheme.typography.bodyMedium)
                        val count = events.count { it.crewId == c.id }
                        Text("모임 ${count}개", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(onClick = { eventFor = c }, modifier = Modifier.padding(top = 8.dp)) { Text("번개/정기런 만들기") }
                    }
                }
            }
            item { Box(Modifier.padding(40.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { creatingCrew = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("크루 만들기") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creatingCrew) {
        CrewDialog(onDismiss = { creatingCrew = false }) { crew ->
            container.crews.update { it + crew }
            creatingCrew = false
        }
    }
    eventFor?.let { crew ->
        EventDialog(crew, onDismiss = { eventFor = null }) { e ->
            container.events.update { it + e }
            eventFor = null
            Toast.makeText(context, "모임을 만들었어요. 공유 버튼으로 초대해보세요!", Toast.LENGTH_SHORT).show()
        }
    }
    deleting?.let { crew ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("'${crew.name}' 크루를 삭제할까요?") },
            text = { Text("이 크루의 모임도 함께 삭제돼요.") },
            confirmButton = {
                TextButton(onClick = {
                    container.deleteCrew(crew.id)
                    deleting = null
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

private fun inviteText(e: CrewEvent, crewName: String): String = buildString {
    appendLine("🏃 [$crewName] ${e.title}")
    appendLine("📅 ${millisToLocal(e.dateTimeMs).format(DateTimeFmt)}")
    appendLine("📍 ${e.place}")
    append("🛣️ %.1fkm".format(e.distanceKm))
    e.paceSecPerKm?.let { append(" · 페이스 ${Pace.format(it.toDouble())}/km") }
    appendLine()
    append("함께 달려요!")
}

@Composable
private fun EventCard(e: CrewEvent, crewName: String, onToggle: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(crewName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(millisToLocal(e.dateTimeMs).format(DateTimeFmt))
            Text(
                "${e.place} · %.1fkm".format(e.distanceKm) +
                    (e.paceSecPerKm?.let { " · ${Pace.format(it.toDouble())}/km" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.attending) {
                    OutlinedButton(onClick = onToggle) { Text("참석 취소") }
                } else {
                    Button(onClick = onToggle) { Text("참석하기") }
                }
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "초대 공유") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "모임 삭제") }
            }
        }
    }
}

@Composable
private fun CrewDialog(onDismiss: () -> Unit, onSave: (Crew) -> Unit) {
    var name by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("크루 만들기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("크루 이름", name, { name = it })
                TextInput("활동 지역", area, { area = it }, placeholder = "예: 여의도 한강공원")
                TextInput("소개 (선택)", desc, { desc = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(Crew(UUID.randomUUID().toString(), name.trim(), area.trim(), desc.trim()))
            }) { Text("만들기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun EventDialog(crew: Crew, onDismiss: () -> Unit, onSave: (CrewEvent) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf("19:30") }
    var place by remember { mutableStateOf(crew.area) }
    var distance by remember { mutableStateOf("5") }
    var pace by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${crew.name} 모임 만들기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("제목", title, { title = it }, placeholder = "예: 목요 번개런")
                DateField("날짜", date, { date = it })
                TextInput("시간", time, { time = it }, placeholder = "19:30")
                TextInput("모이는 곳", place, { place = it })
                NumberField("거리", distance, { distance = it }, suffix = "km")
                TextInput("페이스 (선택)", pace, { pace = it }, placeholder = "예: 6:00")
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = save@{
                val t = runCatching { LocalTime.parse(time.trim().padStart(5, '0')) }.getOrNull()
                if (t == null) {
                    Toast.makeText(context, "시간은 19:30 형식으로 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@save
                }
                val paceSec = pace.takeIf { it.isNotBlank() }?.let { Pace.parseDuration(it)?.toInt() }
                onSave(
                    CrewEvent(
                        id = UUID.randomUUID().toString(),
                        crewId = crew.id,
                        title = title.trim(),
                        dateTimeMs = date.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        place = place.trim(),
                        distanceKm = distance.toDoubleOrNull() ?: 0.0,
                        paceSecPerKm = paceSec,
                        attending = true,
                    ),
                )
            }) { Text("만들기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
