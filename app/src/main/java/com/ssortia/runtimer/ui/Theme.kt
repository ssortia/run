package com.ssortia.runtimer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import com.ssortia.runtimer.R
import com.ssortia.runtimer.WorkoutState

/**
 * Цвета отрезка: фон и текст экрана тренировки и точка [marker] у отрезка на форме.
 * Точка рисуется на карточке формы, поэтому её цвет подобран под фон карточки, а не под [background].
 */
data class SegmentColors(val background: Color, val content: Color, val marker: Color)

// Изумруд и синий палитры «Изумруд». Фон экрана бега и «Старт» в обеих темах [Emerald] с белым
// текстом. Текст и значки акцента на тёмных карточках рисуются светлым [EmeraldLight]: у [Emerald]
// на них контраст около 3:1, это меньше 4,5:1 для текста
private val Emerald = Color(0xFF047857)
private val EmeraldLight = Color(0xFF34D399)
private val WalkBlue = Color(0xFF0B4F8A)
private val EmeraldContainerLight = Color(0xFFE3EFEA)
private val OnEmeraldContainerLight = Color(0xFF0B2B24)
private val EmeraldContainerDark = Color(0xFF1F3A33)
private val OnEmeraldContainerDark = Color(0xFFD1FAE5)

// Бег зелёный, ходьба синяя: отрезки различаются оттенком и надписью, яркость у них близкая
private val RunLight = SegmentColors(background = Emerald, content = Color.White, marker = Emerald)
private val WalkLight = SegmentColors(background = WalkBlue, content = Color.White, marker = WalkBlue)
private val RunDark = SegmentColors(
    background = Emerald,
    content = Color.White,
    marker = Color(0xFF10B981),
)
private val WalkDark = SegmentColors(
    background = Color(0xFF0F3556),
    content = Color(0xFFD6E8FF),
    marker = Color(0xFF4F8FCC),
)

// surfaceTint прозрачный: иначе диалоги и шторка с tonalElevation подмешивают акцент к фону.
// Фон и surface здесь не задаются, их подставляет RunTimerTheme из colors.xml
private val LightScheme = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    inversePrimary = Color(0xFF6EE7B7),
    secondary = Color(0xFF3F5F55),
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = OnEmeraldContainerLight,
    onBackground = Color(0xFF16181B),
    onSurface = Color(0xFF16181B),
    onSurfaceVariant = Color(0xFF5C6066),
    surfaceVariant = Color(0xFFE9E9E7),
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE2E2E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAF8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F0EE),
    surfaceContainerHighest = Color(0xFFE9E9E7),
    inverseSurface = Color(0xFF2E3033),
    inverseOnSurface = Color(0xFFF0F0EE),
    outline = Color(0xFF8A8D92),
    outlineVariant = Color(0xFFE4E4E1),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

// primary светлый: им M3 рисует текст и значки на карточках (кнопки диалога, Switch, Slider),
// а залитый «Старт» явно берёт цвета бега из segmentColors
private val DarkScheme = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    inversePrimary = Emerald,
    secondary = Color(0xFFA7C9BD),
    onSecondary = Color(0xFF10302A),
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = OnEmeraldContainerDark,
    onBackground = Color(0xFFECECEA),
    onSurface = Color(0xFFECECEA),
    onSurfaceVariant = Color(0xFFA4A7AD),
    surfaceVariant = Color(0xFF303135),
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF37383C),
    surfaceDim = Color(0xFF111214),
    surfaceContainerLowest = Color(0xFF0C0D0F),
    surfaceContainerLow = Color(0xFF17181B),
    surfaceContainer = Color(0xFF1C1D20),
    surfaceContainerHigh = Color(0xFF26272B),
    surfaceContainerHighest = Color(0xFF303135),
    inverseSurface = Color(0xFFECECEA),
    inverseOnSurface = Color(0xFF2E3033),
    outline = Color(0xFF6E7176),
    outlineVariant = Color(0xFF2A2B2F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** Схема цветов темы без фона: фон и surface подставляет [RunTimerTheme] из `colors.xml`. */
internal fun appColorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) DarkScheme else LightScheme

/** Цифры одинаковой ширины: время и счётчики не прыгают при смене значения. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

fun segmentColors(isRun: Boolean, darkTheme: Boolean): SegmentColors = when {
    darkTheme -> if (isRun) RunDark else WalkDark
    else -> if (isRun) RunLight else WalkLight
}

// Незакрашенные части кольца и деления полосы прогресса: тон поля на карточке почти не виден
private val ProgressTrackLight = Color(0xFFDCDCD8)
private val ProgressTrackDark = Color(0xFF3A3B40)
private val ProgressCheckLight = Color.White
private val ProgressCheckDark = Color(0xFF111214)

/** Незакрашенные части кольца недели и деления полосы на экране прогресса. Закрашенные берут `marker` бега. */
fun progressTrackColor(darkTheme: Boolean): Color = if (darkTheme) ProgressTrackDark else ProgressTrackLight

/** Галочка пройденной недели поверх закрашенного круга цвета `marker` бега. */
fun progressCheckColor(darkTheme: Boolean): Color = if (darkTheme) ProgressCheckDark else ProgressCheckLight

/** Отсчёт идёт в цветах бега: бегун сразу видит цвет первого отрезка. */
fun countdownColors(darkTheme: Boolean): SegmentColors = segmentColors(isRun = true, darkTheme)

/** Нужны ли тёмные значки в строке состояния: на экране тренировки они совпадают с цветом текста. */
fun darkStatusIcons(state: WorkoutState, darkTheme: Boolean): Boolean = when (state) {
    WorkoutState.Idle -> !darkTheme
    is WorkoutState.Countdown -> countdownColors(darkTheme).needsDarkIcons()
    is WorkoutState.Active -> segmentColors(state.position.isRun, darkTheme).needsDarkIcons()
}

private fun SegmentColors.needsDarkIcons(): Boolean = content.luminance() < 0.5f

/**
 * Тёмная ли тема с учётом выбора пользователя. Экраны берут признак отсюда, а не из
 * `isSystemInDarkTheme()`: иначе цвета отрезков разошлись бы с выбранной темой.
 */
val LocalDarkTheme = staticCompositionLocalOf<Boolean> { error("LocalDarkTheme задаёт RunTimerTheme") }

/** Фон взят из темы окна, чтобы при запуске не было вспышки. */
@Composable
fun RunTimerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val background = colorResource(
        if (darkTheme) R.color.window_background_dark else R.color.window_background_light,
    )
    val scheme = appColorScheme(darkTheme)
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = scheme.copy(background = background, surface = background)) {
            Surface(Modifier.fillMaxSize(), color = background, content = content)
        }
    }
}
