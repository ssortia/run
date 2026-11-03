package com.ssortia.runtimer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentTimeTextTest {
    @Test
    fun `минуты без ведущего нуля, секунды двумя цифрами`() {
        assertEquals("2:00", segmentTimeText(2, 0))
        assertEquals("0:05", segmentTimeText(0, 5))
        assertEquals("13:30", segmentTimeText(13, 30))
    }

    @Test
    fun `сохранённое значение не на шаге барабана показывается как есть`() {
        assertEquals("1:07", segmentTimeText(1, 7))
    }

    @Test
    fun `неверное значение не прижимается, ошибку показывает проверка формы`() {
        assertEquals("120:75", segmentTimeText(120, 75))
    }
}
