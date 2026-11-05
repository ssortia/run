package com.ssortia.runtimer.ui

import com.ssortia.runtimer.Position
import com.ssortia.runtimer.WorkoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `центральная кнопка стоит между «Историей» и «Прогрессом»`() {
        assertEquals(BottomTab.HISTORY, BottomTab.entries[startSlotIndex() - 1])
        assertEquals(BottomTab.PROGRESS, BottomTab.entries[startSlotIndex()])
    }

    @Test
    fun `без маршрута и для неизвестного маршрута вкладки нет`() {
        assertNull(bottomTabOf(null))
        assertNull(bottomTabOf("countdown"))
        assertNull(bottomTabOf(""))
    }

    private val active = WorkoutState.Active(
        position = Position(index = 0, isRun = true, round = 1, remainingMs = 1_000),
        rounds = 1,
        progress = 0f,
    )
    private val countdown = WorkoutState.Countdown(seconds = 3)
    private val tabRoutes = listOf(ROUTE_SETTINGS, ROUTE_HISTORY, ROUTE_PROGRESS, ROUTE_APP_SETTINGS)

    @Test
    fun `во весь экран только тренировка на «Таймере»`() {
        // Ожидания перечислены явно: true только у отсчёта и тренировки на «Таймере» или без маршрута
        val fullscreen = setOf(
            countdown to ROUTE_SETTINGS,
            countdown to null,
            active to ROUTE_SETTINGS,
            active to null,
        )
        for (state in listOf(WorkoutState.Idle, countdown, active)) {
            for (route in tabRoutes + null) {
                assertEquals("$state на $route", (state to route) in fullscreen, isWorkoutFullscreen(state, route))
            }
        }
    }

    @Test
    fun `«Таймер» открывается только на переходе из Idle`() {
        assertTrue(shouldOpenTimer(wasIdle = true, idle = false))
        assertFalse(shouldOpenTimer(wasIdle = true, idle = true))
        // Поворот во время тренировки и activity, открытая уже во время тренировки
        assertFalse(shouldOpenTimer(wasIdle = false, idle = false))
        // Финал тренировки
        assertFalse(shouldOpenTimer(wasIdle = false, idle = true))
    }

    @Test
    fun `«Свернуть» сразу после старта ведёт на вкладку старта, с «Таймера» на «Историю»`() {
        assertEquals(ROUTE_PROGRESS, collapseRouteOnStart(ROUTE_PROGRESS))
        assertEquals(ROUTE_APP_SETTINGS, collapseRouteOnStart(ROUTE_APP_SETTINGS))
        assertEquals(ROUTE_HISTORY, collapseRouteOnStart(ROUTE_HISTORY))
        assertEquals(ROUTE_HISTORY, collapseRouteOnStart(ROUTE_SETTINGS))
        assertEquals(ROUTE_HISTORY, collapseRouteOnStart(null))
        assertEquals(ROUTE_HISTORY, collapseRouteOnStart("countdown"))
    }

    @Test
    fun `«Свернуть» запоминает последнюю вкладку, кроме «Таймера»`() {
        assertEquals(ROUTE_PROGRESS, nextCollapseRoute(ROUTE_HISTORY, ROUTE_PROGRESS))
        assertEquals(ROUTE_APP_SETTINGS, nextCollapseRoute(ROUTE_PROGRESS, ROUTE_APP_SETTINGS))
        assertEquals(ROUTE_PROGRESS, nextCollapseRoute(ROUTE_PROGRESS, ROUTE_SETTINGS))
        assertEquals(ROUTE_PROGRESS, nextCollapseRoute(ROUTE_PROGRESS, null))
        assertEquals(ROUTE_PROGRESS, nextCollapseRoute(ROUTE_PROGRESS, "countdown"))
    }
}
