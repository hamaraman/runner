package com.runner.app.ui.run

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.runner.core.TrackPoint
import kotlin.math.cos
import kotlin.math.max

/**
 * 지도 없이 경로 모양만 그리는 캔버스(지도 API 키 불필요).
 * 위경도를 간단한 평면 투영(경도 × cos 위도)해 화면에 맞춘다.
 */
@Composable
fun RouteCanvas(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("경로를 기록하는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Canvas(modifier) {
        val k = cos(Math.toRadians(points.first().lat))
        val xs = points.map { it.lng * k }
        val ys = points.map { it.lat }
        val minX = xs.min(); val maxX = xs.max()
        val minY = ys.min(); val maxY = ys.max()
        val pad = 24f
        val spanX = max(maxX - minX, 1e-6)
        val spanY = max(maxY - minY, 1e-6)
        val scale = minOf((size.width - pad * 2) / spanX, (size.height - pad * 2) / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun toOffset(i: Int) = Offset(
            (offX + (xs[i] - minX) * scale).toFloat(),
            (offY + (maxY - ys[i]) * scale).toFloat(), // 북쪽이 위
        )

        // 일시정지 구간끼리는 선을 잇지 않는다
        val path = Path()
        points.forEachIndexed { i, p ->
            val o = toOffset(i)
            if (i == 0 || points[i - 1].segment != p.segment) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, color, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color(0xFF4CAF50), radius = 12f, center = toOffset(0))
        drawCircle(Color(0xFFE53935), radius = 12f, center = toOffset(points.lastIndex))
    }
}
