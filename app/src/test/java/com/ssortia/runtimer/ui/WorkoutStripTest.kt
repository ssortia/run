package com.ssortia.runtimer.ui

import com.ssortia.runtimer.Workout
import com.ssortia.runtimer.WorkoutState
import com.ssortia.runtimer.tickAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutStripTest {
    private val labels = WorkoutStripLabels(
        run = "Бег",
        walk = "Шаг",
        runLower = "бег",
        walkLower = "шаг",
        paused = "%1\$s · пауза",
        countdown = "Старт через",
        round = "Повтор %1\$d из %2\$d",
        roundLower = "повтор %1\$d из %2\$d",
        description = "Тренировка: %1\$s, осталось %2\$s, %3\$s. Открыть таймер",
        pausedDescription = "Тренировка на паузе: %1\$s, осталось %2\$s, %3\$s. Открыть таймер",
        countdownDescription = "Тренировка: старт через %1\$s. Открыть таймер",
        minutes = { n -> "$n " + plural(n, "минута", "минуты", "минут") },
        seconds = { n -> "$n " + plural(n, "секунда", "секунды", "секунд") },
    )

    private fun plural(n: Int, one: String, few: String, many: String): String = when {
        n % 10 == 1 && n % 100 != 11 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }

    // Бег 2:00, шаг 1:30, шесть повторов: повтор длится 210 с
    private val workout = Workout(runSec = 120, walkSec = 90, rounds = 6)

    private fun text(elapsedMs: Long, paused: Boolean = false) =
        checkNotNull(workoutStripText(tickAt(workout, elapsedMs, 0, paused = paused).state, "", labels))

    @Test
    fun `вне тренировки полоски нет`() {
        assertNull(workoutStripText(WorkoutState.Idle, "Бег 2:00", labels))
    }

    @Test
    fun `бег`() {
        // Повтор 3, бег: прошло 36 с из 120
        val text = text(2 * 210_000L + 36_000)
        assertEquals("Бег", text.title)
        assertEquals("Повтор 3 из 6", text.subtitle)
        assertEquals("1:24", text.time)
        assertEquals(workout.progressAt(2 * 210_000L + 36_000), text.progress)
    }

    @Test
    fun `шаг`() {
        // Повтор 2, шаг: прошло 30 с из 90
        val text = text(210_000L + 150_000)
        assertEquals("Шаг", text.title)
        assertEquals("Повтор 2 из 6", text.subtitle)
        assertEquals("1:00", text.time)
    }

    @Test
    fun `описание одной фразой со строчными отрезком и повтором`() {
        assertEquals(
            "Тренировка: бег, осталось 1 минута 24 секунды, повтор 3 из 6. Открыть таймер",
            text(2 * 210_000L + 36_000).description,
        )
        assertEquals(
            "Тренировка: шаг, осталось 1 минута, повтор 2 из 6. Открыть таймер",
            text(210_000L + 150_000).description,
        )
    }

    @Test
    fun `пауза`() {
        val text = text(2 * 210_000L + 36_000, paused = true)
        assertEquals("Бег · пауза", text.title)
        assertEquals(
            "Тренировка на паузе: бег, осталось 1 минута 24 секунды, повтор 3 из 6. Открыть таймер",
            text.description,
        )
    }

    @Test
    fun `ровно 2 минуты без секунд`() {
        assertEquals(
            "Тренировка: бег, осталось 2 минуты, повтор 1 из 6. Открыть таймер",
            text(0).description,
        )
    }

    @Test
    fun `45 секунд без минут`() {
        assertEquals(
            "Тренировка: бег, осталось 45 секунд, повтор 1 из 6. Открыть таймер",
            text(75_000).description,
        )
    }

    @Test
    fun `остаток меньше секунды округляется вверх, как на экране`() {
        val text = text(119_500)
        assertEquals("0:01", text.time)
        assertEquals("Тренировка: бег, осталось 1 секунда, повтор 1 из 6. Открыть таймер", text.description)
    }

    @Test
    fun `отсчёт`() {
        val text = checkNotNull(workoutStripText(WorkoutState.Countdown(seconds = 3), "Бег 2:00 · Шаг 1:30 · ×6", labels))
        assertEquals("Старт через", text.title)
        assertEquals("Бег 2:00 · Шаг 1:30 · ×6", text.subtitle)
        assertEquals("0:03", text.time)
        assertNull(text.progress)
        assertEquals("Тренировка: старт через 3 секунды. Открыть таймер", text.description)
    }
}
