package com.ssortia.runtimer.ui

import com.ssortia.runtimer.Position
import com.ssortia.runtimer.WorkoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSheetTest {
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
    fun `строку состояния закрывает только развёрнутый слой у самого верха`() {
        assertTrue(sheetCoversStatusBar(expanded = true, offsetPx = 0f, statusBarPx = 60f))
        assertTrue(sheetCoversStatusBar(expanded = true, offsetPx = 59f, statusBarPx = 60f))
        assertFalse(sheetCoversStatusBar(expanded = true, offsetPx = 60f, statusBarPx = 60f))
        assertFalse(sheetCoversStatusBar(expanded = true, offsetPx = 500f, statusBarPx = 60f))
        assertFalse(sheetCoversStatusBar(expanded = false, offsetPx = 0f, statusBarPx = 60f))
        // Без строки состояния — только в самом верху
        assertTrue(sheetCoversStatusBar(expanded = true, offsetPx = 0f, statusBarPx = 0f))
        assertFalse(sheetCoversStatusBar(expanded = true, offsetPx = 1f, statusBarPx = 0f))
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
        // У самого порога смещения скорость ниже порога флинга не меняет решения
        assertFalse(shouldCollapseSheet(offsetPx = 249f, heightPx = 1000f, velocityPx = fling - 1f, flingVelocityPx = fling))
        assertTrue(shouldCollapseSheet(offsetPx = 250f, heightPx = 1000f, velocityPx = -(fling - 1f), flingVelocityPx = fling))
    }

    @Test
    fun `без высоты области экран не сворачивается`() {
        assertFalse(shouldCollapseSheet(offsetPx = 0f, heightPx = 0f, velocityPx = 0f, flingVelocityPx = fling))
        assertFalse(shouldCollapseSheet(offsetPx = 0f, heightPx = 0f, velocityPx = fling * 10, flingVelocityPx = fling))
        assertFalse(shouldCollapseSheet(offsetPx = 10f, heightPx = -1f, velocityPx = fling * 10, flingVelocityPx = fling))
    }

    @Test
    fun `палец двигает слой в пределах области`() {
        assertEquals(300f, sheetDragTo(offsetPx = 100f, deltaPx = 200f, heightPx = 1000f))
        assertEquals(50f, sheetDragTo(offsetPx = 100f, deltaPx = -50f, heightPx = 1000f))
        // Выше верха и ниже низа не уходит
        assertEquals(0f, sheetDragTo(offsetPx = 100f, deltaPx = -500f, heightPx = 1000f))
        assertEquals(1000f, sheetDragTo(offsetPx = 900f, deltaPx = 500f, heightPx = 1000f))
        // Без высоты области слой стоит наверху
        assertEquals(0f, sheetDragTo(offsetPx = 0f, deltaPx = 100f, heightPx = 0f))
        assertEquals(0f, sheetDragTo(offsetPx = 0f, deltaPx = 100f, heightPx = -1f))
    }

    @Test
    fun `прокрутка вверх сначала поднимает опущенный слой`() {
        assertTrue(sheetTakesPreScroll(deltaPx = -10f, offsetPx = 100f))
        assertFalse(sheetTakesPreScroll(deltaPx = -10f, offsetPx = 0f))
        assertFalse(sheetTakesPreScroll(deltaPx = 10f, offsetPx = 100f))
        assertFalse(sheetTakesPreScroll(deltaPx = 0f, offsetPx = 100f))
    }

    @Test
    fun `остаток прокрутки вниз опускает слой только от пальца`() {
        assertTrue(sheetTakesPostScroll(deltaPx = 10f, fromUser = true))
        assertFalse(sheetTakesPostScroll(deltaPx = 10f, fromUser = false))
        assertFalse(sheetTakesPostScroll(deltaPx = -10f, fromUser = true))
        assertFalse(sheetTakesPostScroll(deltaPx = 0f, fromUser = true))
    }

    @Test
    fun `отпускание забирает только опущенный слой`() {
        assertTrue(sheetTakesFling(offsetPx = 1f))
        assertFalse(sheetTakesFling(offsetPx = 0f))
    }
}
