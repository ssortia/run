package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Test

class PresetRowTextTest {
    private fun row(week: Int): PresetRowText {
        val preset = WORKOUT_PRESETS.first { it.week == week }
        return presetRowText(preset, "Неделя $week", "Бег", "Ходьба", "мин", "с")
    }

    @Test
    fun `неделя 2`() {
        assertEquals(PresetRowText("Неделя 2", "Бег 2:00 · Ходьба 2:00 · ×7", "26 мин"), row(2))
    }

    @Test
    fun `ходьба с секундами`() {
        assertEquals("Бег 8:00 · Ходьба 1:30 · ×3", row(6).details)
    }

    @Test
    fun `неделя 11 без ходьбы`() {
        assertEquals("Бег 30:00 · ×1", row(11).details)
        assertEquals("30 мин", row(11).total)
    }

    @Test
    fun `общее время без последней ходьбы`() {
        assertEquals("23 мин", row(3).total)
        assertEquals("19 мин 30 с", row(7).total)
    }

    @Test
    fun `общее время целыми минутами`() {
        assertEquals("5 мин", formatPresetTotal(300_000, "мин", "с"))
    }

    @Test
    fun `общее время с секундами`() {
        assertEquals("2 мин 5 с", formatPresetTotal(125_000, "мин", "с"))
    }

    @Test
    fun `меньше минуты только секунды`() {
        assertEquals("45 с", formatPresetTotal(45_000, "мин", "с"))
    }

    @Test
    fun `час и больше без часов`() {
        assertEquals("60 мин", formatPresetTotal(3_600_000, "мин", "с"))
        assertEquals("125 мин 10 с", formatPresetTotal(7_510_000, "мин", "с"))
    }

    @Test
    fun `ноль даёт ноль минут`() {
        assertEquals("0 мин", formatPresetTotal(0, "мин", "с"))
    }

    @Test
    fun `отрицательное время прижимается к нулю`() {
        assertEquals("0 мин", formatPresetTotal(-5_000, "мин", "с"))
    }
}
