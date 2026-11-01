package com.ssortia.runtimer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.ssortia.runtimer.R

/** Цвета экрана во время отрезка: фон и текст поверх него. */
data class SegmentColors(val background: Color, val content: Color)

// Бег яркий, ходьба тёмная: отрезки различаются издалека и по оттенку, и по яркости
private val RunLight = SegmentColors(background = Color(0xFFFF7A1A), content = Color.Black)
private val WalkLight = SegmentColors(background = Color(0xFF0B4F8A), content = Color.White)
private val RunDark = SegmentColors(background = Color(0xFFE8631A), content = Color.Black)
private val WalkDark = SegmentColors(background = Color(0xFF0F3556), content = Color(0xFFD6E8FF))

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB83A12),
    onPrimary = Color.White,
    secondary = WalkLight.background,
    onSecondary = WalkLight.content,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5A1B00),
    secondary = Color(0xFF9CCBFF),
    onSecondary = Color(0xFF00325A),
)

fun segmentColors(isRun: Boolean, darkTheme: Boolean): SegmentColors = when {
    darkTheme -> if (isRun) RunDark else WalkDark
    else -> if (isRun) RunLight else WalkLight
}

/** Тема следует за системой. Фон взят из темы окна, чтобы при запуске не было вспышки. */
@Composable
fun RunTimerTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val background = colorResource(
        if (darkTheme) R.color.window_background_dark else R.color.window_background_light,
    )
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme.copy(background = background, surface = background)) {
        Surface(Modifier.fillMaxSize(), color = background, content = content)
    }
}
