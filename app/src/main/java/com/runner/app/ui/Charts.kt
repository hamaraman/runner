package com.runner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runner.core.Pace

private val BarTop = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
private val BarEnd = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)

/**
 * 요일별 거리 막대(월~일). 한 가지 색(크기), 오늘만 진하게, 값 라벨은 오늘·최대일에만.
 * 거리 0인 날은 막대 대신 기준선만 남는다.
 */
@Composable
fun WeekBars(days: List<String>, km: List<Double>, todayIndex: Int, modifier: Modifier = Modifier) {
    val max = km.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val maxIndex = km.indexOf(km.max())
    val bar = MaterialTheme.colorScheme.primary
    val desc = days.zip(km).joinToString { (d, v) -> "$d %.1fkm".format(v) }
    Column(modifier.semantics { contentDescription = "요일별 거리: $desc" }) {
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            km.forEachIndexed { i, v ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (v > 0 && (i == todayIndex || i == maxIndex)) {
                        Text("%.1f".format(v), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                    }
                    if (v > 0) {
                        Box(
                            Modifier.fillMaxWidth(0.62f)
                                .fillMaxHeight((v / max).toFloat().coerceIn(0.04f, 0.78f))
                                .background(if (i == todayIndex) bar else bar.copy(alpha = 0.38f), BarTop),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, d ->
                Text(
                    d,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == todayIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == todayIndex) FontWeight.Bold else null,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** km 구간 페이스. 막대 길이 = 속도(빠를수록 길다), 가장 빠른 구간만 강조색. */
@Composable
fun SplitBars(splitsSec: List<Long>, modifier: Modifier = Modifier) {
    val fastest = splitsSec.min()
    val slowest = splitsSec.max()
    val bar = MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        splitsSec.forEachIndexed { i, sec ->
            val best = sec == fastest && splitsSec.size > 1
            // 가장 느린 구간도 45% 길이는 유지해서 차이가 과장되지 않게
            val frac = if (slowest == fastest) 1f else 0.45f + 0.55f * (slowest - sec).toFloat() / (slowest - fastest)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).height(20.dp)) {
                    Box(
                        Modifier.fillMaxWidth(frac).fillMaxHeight()
                            .background(if (best) bar else bar.copy(alpha = 0.30f), BarEnd),
                    )
                }
                Text(
                    Pace.format(sec.toDouble()),
                    Modifier.width(64.dp).padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (best) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
