package com.runner.app.ui.run

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runner.app.ui.NumberField
import com.runner.app.ui.TextInput
import com.runner.app.ui.rememberContainer
import com.runner.core.Shoe
import com.runner.core.totalKm
import java.util.UUID

/** 보통 600~800km에서 교체를 권한다. */
private const val REPLACE_KM = 700.0

/** 러닝화 관리: 맨 위 현역 신발이 새 러닝에 자동으로 붙는다. */
@Composable
fun ShoesDialog(onDismiss: () -> Unit) {
    val container = rememberContainer()
    val shoes by container.shoes.state.collectAsStateWithLifecycle()
    val runs by container.runs.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var baseKm by remember { mutableStateOf("") }
    val current = shoes.firstOrNull { !it.retired }?.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("러닝화") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shoes.forEach { shoe ->
                    val km = shoe.totalKm(runs)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = shoe.id == current, onClick = { container.selectShoe(shoe.id) }, enabled = !shoe.retired)
                        Column(Modifier.weight(1f)) {
                            Text(shoe.name, color = if (shoe.retired) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                            Text(
                                "%.1f km".format(km) + if (!shoe.retired && km >= REPLACE_KM) " · 교체할 때가 됐어요" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!shoe.retired && km >= REPLACE_KM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            container.shoes.update { list -> list.map { if (it.id == shoe.id) it.copy(retired = !it.retired) else it } }
                        }) { Text(if (shoe.retired) "복귀" else "은퇴") }
                        IconButton(onClick = { container.deleteShoe(shoe.id) }) { Icon(Icons.Filled.Delete, "삭제") }
                    }
                }
                TextInput("새 러닝화", name, { name = it }, placeholder = "예: 페가수스 41")
                NumberField("이미 달린 거리 (선택)", baseKm, { baseKm = it }, suffix = "km")
                TextButton(
                    onClick = {
                        val shoe = Shoe(UUID.randomUUID().toString(), name.trim(), baseKm.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0)
                        container.shoes.update { listOf(shoe) + it }
                        name = ""
                        baseKm = ""
                    },
                    enabled = name.isNotBlank(),
                ) { Text("추가") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 기록에 붙은 신발을 바꾸는 줄. 누르면 다음 신발(마지막 다음은 없음)로 넘어간다. */
@Composable
fun RunShoeRow(runId: String, shoeId: String?, modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val shoes by container.shoes.state.collectAsStateWithLifecycle()
    if (shoes.isEmpty()) return
    val options = listOf<Shoe?>(null) + shoes
    val idx = options.indexOfFirst { it?.id == shoeId }.coerceAtLeast(0)
    Text(
        "러닝화: ${options[idx]?.name ?: "선택 안 함"}  (눌러서 변경)",
        modifier.clickable { container.setRunShoe(runId, options[(idx + 1) % options.size]?.id) }.padding(vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
