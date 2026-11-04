package com.ssortia.runtimer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomTabTest {
    @Test
    fun `вкладки идут в порядке панели, первая на стартовом маршруте формы`() {
        assertEquals(
            listOf(BottomTab.WORKOUT, BottomTab.HISTORY, BottomTab.PROGRESS, BottomTab.SETTINGS),
            BottomTab.entries,
        )
        assertEquals(ROUTE_SETTINGS, BottomTab.entries.first().route)
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
    fun `без маршрута и для неизвестного маршрута вкладки нет`() {
        assertNull(bottomTabOf(null))
        assertNull(bottomTabOf("countdown"))
        assertNull(bottomTabOf(""))
    }
}
