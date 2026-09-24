package com.runner.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Orange = Color(0xFFFF6B35)
private val OrangeDark = Color(0xFFFFB59A)

private val LightColors = lightColorScheme(primary = Orange, secondary = Color(0xFF2E86AB))
private val DarkColors = darkColorScheme(primary = OrangeDark, secondary = Color(0xFF8ECAE6))

/** 운동 종류별 강조 색 */
object WorkoutColors {
    val easy = Color(0xFF4CAF50)
    val long = Color(0xFF2E86AB)
    val tempo = Color(0xFFFF9800)
    val interval = Color(0xFFE53935)
    val race = Color(0xFF8E24AA)
    val rest = Color(0xFF9E9E9E)
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
    MaterialTheme(colorScheme = colors, content = content)
}
