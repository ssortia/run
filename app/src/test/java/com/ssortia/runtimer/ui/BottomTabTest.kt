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

    @Test
    fun `во весь экран только развёрнутый экран отсчёта или тренировки`() {
        for (expanded in listOf(true, false)) {
            assertFalse(isWorkoutFullscreen(WorkoutState.Idle, expanded))
        }
        assertTrue(isWorkoutFullscreen(countdown, expanded = true))
        assertFalse(isWorkoutFullscreen(countdown, expanded = false))
        assertTrue(isWorkoutFullscreen(active, expanded = true))
        assertFalse(isWorkoutFullscreen(active, expanded = false))
    }

    @Test
    fun `новый экземпляр activity во время тренировки открывается с развёрнутым экраном`() {
        assertFalse(initialWorkoutExpanded(WorkoutState.Idle))
        assertTrue(initialWorkoutExpanded(countdown))
        assertTrue(initialWorkoutExpanded(active))
    }

    @Test
    fun `экран тренировки разворачивается только на переходе из Idle`() {
        assertTrue(shouldExpandWorkout(wasIdle = true, idle = false))
        assertFalse(shouldExpandWorkout(wasIdle = true, idle = true))
        // Поворот во время тренировки и activity, открытая уже во время тренировки
        assertFalse(shouldExpandWorkout(wasIdle = false, idle = false))
        // Финал тренировки
        assertFalse(shouldExpandWorkout(wasIdle = false, idle = true))
    }

    private val fling = 300f

    @Test
    fun `без флинга экран сворачивается по смещению от четверти высоты`() {
        assertFalse(shouldCollapseSheet(offsetPx = 100f, heightPx = 1000f, velocityPx = 0f, flingVelocityPx = fling))
        assertTrue(shouldCollapseSheet(offsetPx = 400f, heightPx = 1000f, velocityPx = 0f, flingVelocityPx = fling))
        // Ровно на пороге
        assertTrue(shouldCollapseSheet(offsetPx = 250f, heightPx = 1000f, velocityPx = 0f, flingVelocityPx = fling))
        assertFalse(shouldCollapseSheet(offsetPx = 249f, heightPx = 1000f, velocityPx = 0f, flingVelocityPx = fling))
    }

    @Test
    fun `флинг важнее смещения`() {
        // Взмах вниз при малом смещении сворачивает
        assertTrue(shouldCollapseSheet(offsetPx = 10f, heightPx = 1000f, velocityPx = fling, flingVelocityPx = fling))
        // Взмах вверх при большом смещении возвращает
        assertFalse(shouldCollapseSheet(offsetPx = 900f, heightPx = 1000f, velocityPx = -fling, flingVelocityPx = fling))
    }

    @Test
    fun `скорость чуть ниже порога решает по смещению`() {
        assertFalse(shouldCollapseSheet(offsetPx = 10f, heightPx = 1000f, velocityPx = fling - 1f, flingVelocityPx = fling))
        assertTrue(shouldCollapseSheet(offsetPx = 900f, heightPx = 1000f, velocityPx = -(fling - 1f), flingVelocityPx = fling))
    }

    @Test
    fun `без высоты области экран не сворачивается`() {
        assertFalse(shouldCollapseSheet(offsetPx = 0f, heightPx = 0f, velocityPx = 0f, flingVelocityPx = fling))
        assertFalse(shouldCollapseSheet(offsetPx = 0f, heightPx = 0f, velocityPx = fling * 10, flingVelocityPx = fling))
    }
}
