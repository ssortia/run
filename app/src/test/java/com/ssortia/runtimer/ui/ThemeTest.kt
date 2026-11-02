package com.ssortia.runtimer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ssortia.runtimer.Position
import com.ssortia.runtimer.WorkoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `бег и ходьба различаются по яркости в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            val run = segmentColors(isRun = true, darkTheme).background
            val walk = segmentColors(isRun = false, darkTheme).background

            assertTrue("контраст отрезков: тёмная тема $darkTheme", contrast(run, walk) >= 3f)
        }
    }

    @Test
    fun `значки на форме следуют за темой`() {
        assertTrue(darkStatusIcons(WorkoutState.Idle, darkTheme = false))
        assertFalse(darkStatusIcons(WorkoutState.Idle, darkTheme = true))
    }

    @Test
    fun `на тёмном фоне ходьбы в светлой теме значки светлые`() {
        assertFalse(darkStatusIcons(active(isRun = false), darkTheme = false))
    }

    @Test
    fun `на ярком фоне бега значки тёмные в обеих темах`() {
        assertTrue(darkStatusIcons(active(isRun = true), darkTheme = false))
        assertTrue(darkStatusIcons(active(isRun = true), darkTheme = true))
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
