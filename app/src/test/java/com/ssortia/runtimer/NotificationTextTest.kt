package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {
    private fun text(position: Position, rounds: Int = 7) =
        notificationText(position, rounds, runLabel = "Бег", walkLabel = "Ходьба", roundLabel = "Повтор")

    @Test
    fun `отсчёт показывает оставшиеся секунды`() {
        assertEquals(
            "Старт через 5 с",
            countdownNotificationText(5, countdownLabel = "Старт через", secondsLabel = "с"),
        )
    }

    @Test
    fun `отрезок бега`() {
        val position = Position(index = 0, isRun = true, round = 1, remainingMs = 120_000)

        assertEquals("Бег · 2:00 · Повтор 1/7", text(position))
    }

    @Test
    fun `отрезок ходьбы`() {
        val position = Position(index = 1, isRun = false, round = 1, remainingMs = 90_000)

        assertEquals("Ходьба · 1:30 · Повтор 1/7", text(position))
    }

    @Test
    fun `номер повтора и их общее число`() {
        val position = Position(index = 16, isRun = true, round = 9, remainingMs = 60_000)

        assertEquals("Бег · 1:00 · Повтор 9/12", text(position, rounds = 12))
    }

    @Test
    fun `остаток округляется вверх`() {
        val position = Position(index = 0, isRun = true, round = 1, remainingMs = 1)

        assertEquals("Бег · 0:01 · Повтор 1/7", text(position))
    }

    @Test
    fun `позиция из тренировки`() {
        val position = Workout(runSec = 10, walkSec = 20, rounds = 3).at(45_000)!!

        assertEquals("Ходьба · 0:15 · Повтор 2/3", text(position, rounds = 3))
    }

    @Test
    fun `финальная позиция показывает ноль`() {
        val position = tickAt(WorkoutSettings().toWorkout(), 1_560_000, 12).state.position

        assertEquals("Бег · 0:00 · Повтор 7/7", text(position))
    }
}
