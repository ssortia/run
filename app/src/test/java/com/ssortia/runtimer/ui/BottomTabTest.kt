package com.ssortia.runtimer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomTabTest {
    @Test
    fun `первая вкладка стоит на стартовом маршруте формы`() {
        assertEquals(BottomTab.WORKOUT, BottomTab.entries.first())
        assertEquals("settings", BottomTab.entries.first().route)
        assertEquals(ROUTE_SETTINGS, BottomTab.WORKOUT.route)
    }

    @Test
    fun `вкладки идут в порядке панели`() {
        assertEquals(
            listOf(BottomTab.WORKOUT, BottomTab.HISTORY, BottomTab.PROGRESS, BottomTab.SETTINGS),
            BottomTab.entries,
        )
    }

    @Test
    fun `вкладка определяется по маршруту`() {
        assertEquals(BottomTab.WORKOUT, bottomTabOf(ROUTE_SETTINGS))
        assertEquals(BottomTab.HISTORY, bottomTabOf(ROUTE_HISTORY))
        assertEquals(BottomTab.PROGRESS, bottomTabOf(ROUTE_PROGRESS))
        assertEquals(BottomTab.SETTINGS, bottomTabOf(ROUTE_APP_SETTINGS))
    }

    @Test
    fun `у каждой вкладки свой маршрут`() {
        BottomTab.entries.forEach { assertEquals(it, bottomTabOf(it.route)) }
    }

    @Test
    fun `без маршрута и для неизвестного маршрута вкладки нет`() {
        assertNull(bottomTabOf(null))
        assertNull(bottomTabOf("countdown"))
        assertNull(bottomTabOf(""))
    }
}
