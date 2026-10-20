package com.runner.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 코치마크가 가리킬 대상의 화면상 위치. id로 등록해두면 CoachMarkOverlay가 그 위치에 스포트라이트를 그린다. */
private object CoachMarkTargets {
    val bounds = mutableStateMapOf<String, Rect>()
}

fun Modifier.coachMarkTarget(id: String): Modifier = onGloballyPositioned { coords ->
    CoachMarkTargets.bounds[id] = coords.boundsInRoot()
}

private data class CoachStep(val targetId: String, val text: String)

/** 탭(페이지 인덱스)마다 보여줄 코치마크 단계. 0=러닝, 1=훈련, 2=러닝화, 3=대회. */
private val coachStepsByPage: Map<Int, List<CoachStep>> = mapOf(
    0 to listOf(
        CoachStep("coach_start", "여기를 눌러 러닝을 시작해요"),
        CoachStep("coach_coaching", "음성 코칭·인터벌 신호는 여기서 설정해요"),
        CoachStep("coach_shoe", "오늘 신을 러닝화를 여기서 골라요"),
        CoachStep("coach_tabs", "훈련표 · 러닝화 관리 · 대회 일정은 아래 탭에서 확인해요"),
    ),
    1 to listOf(
        CoachStep("coach_plan_create", "목표 대회와 주간 거리를 정하면 훈련표를 자동으로 만들어줘요"),
    ),
    2 to listOf(
        CoachStep("coach_shoes_add", "러닝화를 등록하면 달린 거리가 자동으로 쌓이고 교체 시기를 알려줘요"),
    ),
    3 to listOf(
        CoachStep("coach_race_add", "여기를 눌러 대회를 추가하면 훈련표도 함께 만들어줘요"),
    ),
)

private const val PREFS_NAME = "coach_mark"

fun Context.coachMarkDone(page: Int): Boolean = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("done_$page", false)
private fun Context.markCoachMarkDone(page: Int) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("done_$page", true).apply()

/** 처음 쓰는 사람에게 그 탭의 핵심 버튼을 가리키며 안내하는 코치마크. 탭마다 한 번씩만, 끝까지 보거나 건너뛰면 그 탭에서는 다시 뜨지 않는다. */
@Composable
fun CoachMarkOverlay(page: Int, onFinished: () -> Unit) {
    val context = LocalContext.current
    val coachSteps = coachStepsByPage[page] ?: return
    var step by remember(page) { mutableIntStateOf(0) }
    val target = CoachMarkTargets.bounds[coachSteps[step].targetId] ?: return
    // 대상이 바뀌면 스포트라이트도 바로 옮겨간다
    val spot = target
    // 테두리가 은은하게 숨쉬며 시선을 끈다
    val pulse by rememberInfiniteTransition(label = "coachMarkPulse").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val primary = MaterialTheme.colorScheme.primary
    val onInverse = MaterialTheme.colorScheme.inverseOnSurface
    fun finish() {
        context.markCoachMarkDone(page)
        onFinished()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val gapPx = with(density) { 16.dp.toPx() }
        val edgePx = with(density) { 24.dp.toPx() }
        // 설명 카드 높이를 재서, 대상이 화면 위쪽이면 아래에 아래쪽이면 위에 붙인다
        var cardHeightPx by remember { mutableFloatStateOf(0f) }
        val below = spot.center.y < screenHeightPx / 2f
        val rawTop = if (below) spot.bottom + gapPx else spot.top - gapPx - cardHeightPx
        val cardTop = rawTop.coerceIn(edgePx, (screenHeightPx - cardHeightPx - edgePx).coerceAtLeast(edgePx))

        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} }, // 뒤쪽 화면 터치를 막아 버튼으로만 넘기게 한다
        ) {
            val pad = 10.dp.toPx()
            val radius = CornerRadius(18.dp.toPx())
            val hole = Rect(spot.left - pad, spot.top - pad, spot.right + pad, spot.bottom + pad)
            val fullPath = Path().apply { addRect(Rect(Offset.Zero, size)) }
            val holePath = Path().apply { addRoundRect(RoundRect(hole, radius)) }
            val scrimPath = Path().apply { op(fullPath, holePath, PathOperation.Difference) }
            drawPath(scrimPath, Color.Black.copy(alpha = 0.7f))
            drawRoundRect(primary, topLeft = hole.topLeft, size = hole.size, cornerRadius = radius, style = Stroke(2.5.dp.toPx()))
            val glowPad = (4 + pulse * 7).dp.toPx()
            drawRoundRect(
                primary.copy(alpha = (1f - pulse) * 0.45f),
                topLeft = Offset(hole.left - glowPad, hole.top - glowPad),
                size = Size(hole.width + glowPad * 2, hole.height + glowPad * 2),
                cornerRadius = CornerRadius(radius.x + glowPad),
                style = Stroke(2.dp.toPx()),
            )
        }
        Card(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 16.dp)
                .offset { IntOffset(0, cardTop.roundToInt()) }
                .onGloballyPositioned { cardHeightPx = it.size.height.toFloat() },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    coachSteps.indices.forEach { i ->
                        Box(
                            Modifier.size(if (i == step) 8.dp else 6.dp).clip(CircleShape)
                                .background(onInverse.copy(alpha = if (i == step) 1f else 0.35f)),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                AnimatedContent(
                    targetState = step,
                    label = "coachMarkText",
                    transitionSpec = { (fadeIn(tween(200))) togetherWith (fadeOut(tween(120))) },
                ) { i ->
                    Text(coachSteps[i].text, style = MaterialTheme.typography.bodyLarge, color = onInverse)
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::finish) { Text("건너뛰기", color = onInverse.copy(alpha = 0.75f)) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { if (step == coachSteps.lastIndex) finish() else step++ }, colors = ButtonDefaults.buttonColors(containerColor = primary)) {
                        Text(if (step == coachSteps.lastIndex) "확인" else "다음")
                    }
                }
            }
        }
    }
}
