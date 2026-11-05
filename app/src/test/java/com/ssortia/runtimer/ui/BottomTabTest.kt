package com.ssortia.runtimer.ui

import com.ssortia.runtimer.Position
import com.ssortia.runtimer.WorkoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `«Старт» стоит между «Историей» и «Прогрессом»`() {
        assertEquals(BottomTab.HISTORY, BottomTab.entries[startSlotIndex() - 1])
        assertEquals(BottomTab.PROGRESS, BottomTab.entries[startSlotIndex()])
    }

    @Test
    fun `без маршрута и для неизвестного маршрута вкладки нет`() {
        assertNull(bottomTabOf(null))
        assertNull(bottomTabOf("countdown"))
        assertNull(bottomTabOf(""))
    }

    @Test
    fun `во весь экран только тренировка на «Таймере»`() {
        val active = WorkoutState.Active(
            position = Position(index = 0, isRun = true, round = 1, remainingMs = 1_000),
            rounds = 1,
            progress = 0f,
        )
        val routes = listOf(ROUTE_SETTINGS, ROUTE_HISTORY, ROUTE_PROGRESS, ROUTE_APP_SETTINGS, null)
        for (state in listOf(WorkoutState.Idle, WorkoutState.Countdown(seconds = 3), active)) {
            for (route in routes) {
                val expected = state !is WorkoutState.Idle && (route == ROUTE_SETTINGS || route == null)
                assertEquals("$state на $route", expected, isWorkoutFullscreen(state, route))
            }
        }
        assertFalse(isWorkoutFullscreen(WorkoutState.Idle, null))
    }
}
