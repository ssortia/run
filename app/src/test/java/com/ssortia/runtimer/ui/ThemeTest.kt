package com.ssortia.runtimer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
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

                assertTrue(contrast(colors.background, colors.content) >= 4.5f)
            }
        }
    }

    @Test
    fun `бег и ходьба различаются по яркости в обеих темах`() {
        for (darkTheme in listOf(false, true)) {
            val run = segmentColors(isRun = true, darkTheme).background
            val walk = segmentColors(isRun = false, darkTheme).background

            assertTrue(contrast(run, walk) >= 3f)
        }
    }
}
