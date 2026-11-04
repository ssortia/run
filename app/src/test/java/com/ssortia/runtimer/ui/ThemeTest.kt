package com.ssortia.runtimer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ssortia.runtimer.Position
import com.ssortia.runtimer.WorkoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ThemeTest {
    private fun active(isRun: Boolean) = WorkoutState.Active(
        position = Position(index = 0, isRun = isRun, round = 1, remainingMs = 1_000),
        rounds = 1,
        progress = 0f,
    )

    private fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    @Test
    fun `текст читается на фоне отрезка в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            for (isRun in listOf(false, true)) {
                val colors = segmentColors(isRun, darkTheme)

                assertTrue(
                    "контраст текста: тёмная тема $darkTheme, бег $isRun",
                    contrast(colors.background, colors.content) >= 4.5f,
                )
            }
        }
    }

    /** Тон цвета в градусах 0…360, как в HSV. У `Color` в Compose его нет. */
    private fun hue(color: Color): Float {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        if (delta == 0f) return 0f
        val h = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return (h + 360f) % 360f
    }

    private fun hueDistance(a: Color, b: Color): Float {
        val d = abs(hue(a) - hue(b))
        return minOf(d, 360f - d)
    }

    // Прежде бег и ходьба различались по яркости (контраст фонов ≥ 3:1). Зелёный и синий палитры
    // «Изумруд» выбрал пользователь, контраст между ними 1,53:1 в светлой теме и 2,3:1 в тёмной,
    // поэтому проверяется оттенок: смену отрезка видно по цвету и по надписи «Бег» / «Ходьба».
    // Разница тонов этих цветов 45°, порог 40° ловит случайный сдвиг одного из них к другому
    @Test
    fun `бег и ходьба различаются оттенком в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            val run = segmentColors(isRun = true, darkTheme).background
            val walk = segmentColors(isRun = false, darkTheme).background

            assertTrue("разница тонов отрезков: тёмная тема $darkTheme", hueDistance(run, walk) >= 40f)
        }
    }

    @Test
    fun `текст на акценте читается в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            val scheme = appColorScheme(darkTheme)

            assertTrue(
                "контраст onPrimary: тёмная тема $darkTheme",
                contrast(scheme.primary, scheme.onPrimary) >= 4.5f,
            )
        }
    }

    // primary — цвет текста и значков акцента на карточках, диалогах и шторке: кнопки диалога
    // удаления, время раскрытой карточки, галочка завершённой пробежки. «Старт» и экран бега
    // залиты цветами бега, их проверяет тест фона отрезка
    @Test
    fun `акцент читается как текст на карточках и диалогах в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            val scheme = appColorScheme(darkTheme)
            val surfaces = mapOf(
                "surfaceContainerLow" to scheme.surfaceContainerLow,
                "surfaceContainer" to scheme.surfaceContainer,
                "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            )
            for ((name, surface) in surfaces) {
                assertTrue(
                    "контраст primary на $name: тёмная тема $darkTheme",
                    contrast(scheme.primary, surface) >= 4.5f,
                )
            }
        }
    }

    @Test
    fun `точка отрезка видна на карточке в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            for (isRun in listOf(false, true)) {
                val marker = segmentColors(isRun, darkTheme).marker

                assertTrue(
                    "контраст точки: тёмная тема $darkTheme, бег $isRun",
                    contrast(marker, appColorScheme(darkTheme).surfaceContainer) >= 3f,
                )
            }
        }
    }

    // Закрашенные части кольца недели и деления полосы рисуются `marker` бега, незакрашенные
    // дорожкой: число пробежек должно читаться по кольцу, а пустое кольцо быть видно на карточке
    @Test
    fun `закрашенная часть кольца отличается от дорожки в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            val marker = segmentColors(isRun = true, darkTheme).marker

            assertTrue(
                "контраст marker и дорожки: тёмная тема $darkTheme",
                contrast(marker, progressTrackColor(darkTheme)) >= 3f,
            )
        }
    }

    @Test
    fun `дорожка кольца видна на карточке в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            assertTrue(
                "контраст дорожки на surfaceContainer: тёмная тема $darkTheme",
                contrast(progressTrackColor(darkTheme), appColorScheme(darkTheme).surfaceContainer) >= 1.3f,
            )
        }
    }

    @Test
    fun `галочка пройденной недели читается на закрашенном круге в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            val marker = segmentColors(isRun = true, darkTheme).marker

            assertTrue(
                "контраст галочки: тёмная тема $darkTheme",
                contrast(progressCheckColor(darkTheme), marker) >= 3f,
            )
        }
    }

    @Test
    fun `значки на форме следуют за темой`() {
        assertTrue(darkStatusIcons(WorkoutState.Idle, darkTheme = false))
        assertFalse(darkStatusIcons(WorkoutState.Idle, darkTheme = true))
    }

    @Test
    fun `на беге и ходьбе значки светлые в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            for (isRun in listOf(false, true)) {
                assertFalse(
                    "значки: тёмная тема $darkTheme, бег $isRun",
                    darkStatusIcons(active(isRun), darkTheme),
                )
            }
        }
    }

    @Test
    fun `на отсчёте значки те же, что на беге, в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            assertEquals(
                "значки отсчёта: тёмная тема $darkTheme",
                darkStatusIcons(active(isRun = true), darkTheme),
                darkStatusIcons(WorkoutState.Countdown(seconds = 5), darkTheme),
            )
        }
    }

    @Test
    fun `значки контрастнее фона отсчёта, чем значки противоположного цвета`() {
        for (darkTheme in listOf(false, true)) {
            val background = countdownColors(darkTheme).background
            val darkIsBetter = contrast(background, Color.Black) > contrast(background, Color.White)

            assertEquals(
                "значки отсчёта: тёмная тема $darkTheme",
                darkIsBetter,
                darkStatusIcons(WorkoutState.Countdown(seconds = 5), darkTheme),
            )
        }
    }

    @Test
    fun `значки контрастнее фона отрезка, чем значки противоположного цвета`() {
        for (darkTheme in listOf(false, true)) {
            for (isRun in listOf(false, true)) {
                val background = segmentColors(isRun, darkTheme).background
                val darkIsBetter = contrast(background, Color.Black) > contrast(background, Color.White)

                assertEquals(
                    "значки: тёмная тема $darkTheme, бег $isRun",
                    darkIsBetter,
                    darkStatusIcons(active(isRun), darkTheme),
                )
            }
        }
    }
}
