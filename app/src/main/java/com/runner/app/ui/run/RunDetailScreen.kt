@file:OptIn(ExperimentalMaterial3Api::class)

package com.runner.app.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runner.app.ui.DateTimeFmt
import com.runner.app.ui.SectionTitle
import com.runner.app.ui.StatBlock
import com.runner.app.ui.millisToLocal
import com.runner.app.ui.rememberContainer
import com.runner.app.ui.shareText
import com.runner.core.Geo
import com.runner.core.Pace
import com.runner.core.RunRecord

@Composable
fun RunDetailScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val runs by container.runs.state.collectAsStateWithLifecycle()
    val run = runs.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(run?.let { millisToLocal(it.startedAtMs).format(DateTimeFmt) } ?: "기록") },
            windowInsets = WindowInsets(0),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            actions = {
                if (run != null) {
                    IconButton(onClick = {
                        shareText(
                            context,
                            "오늘 %.2fkm 달렸어요! 🏃 %s · 평균 %s/km".format(
                                run.distanceM / 1000, Pace.formatDuration(run.durationSec), Pace.format(run.paceSecPerKm),
                            ),
                        )
                    }) { Icon(Icons.Filled.Share, "공유") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "삭제") }
                }
            },
        )
        if (run == null) {
            Text("기록을 찾을 수 없어요.", Modifier.padding(16.dp))
        } else {
            RunDetailBody(run)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 기록을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    container.deleteRun(id)
                    onBack()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun RunDetailBody(run: RunRecord) {
    val splits = remember(run.id) { Geo.splitsSec(run.points) }
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                RouteCanvas(run.points, Modifier.fillMaxWidth().height(260.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBlock("거리", "%.2f km".format(run.distanceM / 1000))
                StatBlock("시간", Pace.formatDuration(run.durationSec))
                StatBlock("평균 페이스", Pace.format(run.paceSecPerKm))
            }
        }
        if (splits.isNotEmpty()) {
            item { SectionTitle("구간 기록") }
            itemsIndexed(splits) { i, sec ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${i + 1} km")
                    Text("${Pace.format(sec.toDouble())}/km", style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
            }
        }
    }
}
