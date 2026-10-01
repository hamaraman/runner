package com.runner.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// 트랙(육상 경기장 우레탄) 오렌지 + 살짝 따뜻한 쪽으로 기운 중립색.
private val LightColors = lightColorScheme(
    primary = Color(0xFFD9480F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D6),
    onPrimaryContainer = Color(0xFF3A1100),
    secondary = Color(0xFF2A78D6),
    secondaryContainer = Color(0xFFDDE9F8),
    onSecondaryContainer = Color(0xFF0B2745),
    background = Color(0xFFFAF8F6),
    surface = Color(0xFFFAF8F6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F2EF),
    surfaceContainer = Color(0xFFF0ECE8),
    surfaceContainerHigh = Color(0xFFEAE5E1),
    surfaceContainerHighest = Color(0xFFE4DEDA),
    onSurface = Color(0xFF1C1917),
    onSurfaceVariant = Color(0xFF605852),
    outline = Color(0xFF8E857E),
    outlineVariant = Color(0xFFDCD5CF),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A57),
    onPrimary = Color(0xFF3A1100),
    primaryContainer = Color(0xFF5C2206),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFF7FB2F0),
    secondaryContainer = Color(0xFF1D3A5C),
    onSecondaryContainer = Color(0xFFD6E6FA),
    background = Color(0xFF141211),
    surface = Color(0xFF141211),
    surfaceContainerLowest = Color(0xFF0F0D0C),
    surfaceContainerLow = Color(0xFF1C1A18),
    surfaceContainer = Color(0xFF211E1C),
    surfaceContainerHigh = Color(0xFF2B2826),
    surfaceContainerHighest = Color(0xFF363230),
    onSurface = Color(0xFFEDE7E3),
    onSurfaceVariant = Color(0xFFB9B0A9),
    outline = Color(0xFF837A74),
    outlineVariant = Color(0xFF3D3835),
    error = Color(0xFFFF8A80),
)

/** 숫자(거리·페이스·시간)가 흔들리지 않도록 고정폭 숫자. */
private val tnum = "tnum"

private val RunnerType = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.03).em, fontFeatureSettings = tnum),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.03).em, fontFeatureSettings = tnum),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, fontFeatureSettings = tnum),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = tnum),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = tnum),
        labelMedium = labelMedium.copy(letterSpacing = 0.06.em),
        labelSmall = labelSmall.copy(letterSpacing = 0.08.em),
    )
}

/** 대시보드 캡션: 작은 대문자형 라벨. */
val Eyebrow = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)

private val RunnerShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/**
 * 운동 종류별 강조 색 — dataviz 검증기(라이트·다크 모두 PASS)를 통과한 순서/단계.
 * 밝은 화면에서 쉬움·템포는 3:1 미만이라 항상 텍스트 라벨과 함께 쓴다.
 */
object WorkoutColors {
    val rest = Color(0xFF9A928C)
    val easy: Color @Composable @ReadOnlyComposable get() = pick(0xFF1BAF7A, 0xFF199E70)
    val tempo: Color @Composable @ReadOnlyComposable get() = pick(0xFFEDA100, 0xFFC98500)
    val long: Color @Composable @ReadOnlyComposable get() = pick(0xFF2A78D6, 0xFF3987E5)
    val interval: Color @Composable @ReadOnlyComposable get() = pick(0xFFE34948, 0xFFE66767)
    val race: Color @Composable @ReadOnlyComposable get() = pick(0xFF4A3AA7, 0xFF9085E9)

    @Composable @ReadOnlyComposable
    private fun pick(light: Long, dark: Long) =
        Color(if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) dark else light)
}

@Composable
fun RunnerTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = RunnerType, shapes = RunnerShapes, content = content)
}
