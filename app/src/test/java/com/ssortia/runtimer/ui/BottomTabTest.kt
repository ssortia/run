package com.ssortia.runtimer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomTabTest {
    @Test
    fun `вкладки идут в порядке панели`() {
        assertEquals(
            listOf(BottomTab.HISTORY, BottomTab.WORKOUT, BottomTab.PROGRESS, BottomTab.SETTINGS),
            BottomTab.entries,
        )
    }

    @Test
    fun `у каждого маршрута своя вкладка`() {
        assertEquals(
            setOf(ROUTE_SETTINGS, ROUTE_HISTORY, ROUTE_PROGRESS, ROUTE_APP_SETTINGS),
            BottomTab.entries.map { it.route }.toSet(),
        )
        BottomTab.entries.forEach { assertEquals(it, bottomTabOf(it.route)) }
    }

    @Test
    fun `центральная кнопка стоит между «Таймером» и «Прогрессом»`() {
        assertEquals(BottomTab.WORKOUT, BottomTab.entries[startSlotIndex() - 1])
        assertEquals(BottomTab.PROGRESS, BottomTab.entries[startSlotIndex()])
    }

    @Test
    fun `без маршрута и для неизвестного маршрута вкладки нет`() {
        assertNull(bottomTabOf(null))
        assertNull(bottomTabOf("countdown"))
        assertNull(bottomTabOf(""))
    }
}
