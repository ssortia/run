package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Шаблоны и формы `<plurals>` берутся из `strings.xml`, поэтому смена порядка подстановок в нём
 * ломает тест. Правило выбора формы `<plurals>` на телефоне даёт ICU, а здесь его повторяет
 * [plural]: склонение на телефоне проверяется только вручную.
 */
class WorkoutStripTextTest {
    // Unit-тесты запускаются из каталога модуля app
    private val strings = File("src/main/res/values/strings.xml").readText()

    private fun string(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""").find(strings)?.groupValues?.get(1)
            ?: error("нет строки $name в strings.xml")

    private fun plurals(name: String): (Int) -> String {
        val body = Regex("""<plurals name="$name">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings)?.groupValues?.get(1) ?: error("нет plurals $name в strings.xml")
        val items = Regex("""<item quantity="(\w+)">(.*?)</item>""").findAll(body)
            .associate { it.groupValues[1] to it.groupValues[2] }
        return { n -> String.format(checkNotNull(items[plural(n)]) { "нет формы ${plural(n)} у $name" }, n) }
    }

    // Правило CLDR для русского языка, целые числа
    private fun plural(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "one"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "few"
        else -> "many"
    }

    private val labels = WorkoutStripLabels(
        run = string("segment_run"),
        walk = string("segment_walk"),
        runLower = string("summary_run"),
        walkLower = string("summary_walk"),
        paused = string("segment_paused"),
        countdown = string("countdown_label"),
        round = string("round_progress"),
        roundLower = string("round_progress_lower"),
        description = string("strip_description"),
        pausedDescription = string("strip_paused_description"),
        countdownDescription = string("strip_countdown_description"),
        minutes = plurals("strip_minutes"),
        seconds = plurals("strip_seconds"),
        minutesAccusative = plurals("strip_minutes_accusative"),
        secondsAccusative = plurals("strip_seconds_accusative"),
    )

    // Бег 2:00, шаг 1:30, шесть повторов: повтор длится 210 с
    private val workout = Workout(runSec = 120, walkSec = 90, rounds = 6)

    private fun text(elapsedMs: Long, paused: Boolean = false) =
        checkNotNull(workoutStripText(tickAt(workout, elapsedMs, 0, paused = paused).state, workout, labels))

    private fun countdown(seconds: Long) =
        checkNotNull(workoutStripText(WorkoutState.Countdown(seconds = seconds), workout, labels))

    @Test
    fun `вне тренировки полоски нет`() {
        assertNull(workoutStripText(WorkoutState.Idle, workout, labels))
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
        assertEquals(workout.progressAt(210_000L + 150_000), text.progress)
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
    fun `пауза на беге`() {
        val text = text(2 * 210_000L + 36_000, paused = true)
        assertEquals("Бег · пауза", text.title)
        assertEquals("Повтор 3 из 6", text.subtitle)
        assertEquals("1:24", text.time)
        assertEquals(workout.progressAt(2 * 210_000L + 36_000), text.progress)
        assertEquals(
            "Тренировка на паузе: бег, осталось 1 минута 24 секунды, повтор 3 из 6. Открыть таймер",
            text.description,
        )
    }

    @Test
    fun `пауза на шаге`() {
        val text = text(210_000L + 150_000, paused = true)
        assertEquals("Шаг · пауза", text.title)
        assertEquals("Повтор 2 из 6", text.subtitle)
        assertEquals("1:00", text.time)
        assertEquals(workout.progressAt(210_000L + 150_000), text.progress)
        assertEquals(
            "Тренировка на паузе: шаг, осталось 1 минута, повтор 2 из 6. Открыть таймер",
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
    fun `после финального сигнала остаток читается как 0 секунд`() {
        val text = text(workout.totalMs)
        assertEquals("0:00", text.time)
        assertEquals(1f, text.progress)
        assertEquals("Тренировка: бег, осталось 0 секунд, повтор 6 из 6. Открыть таймер", text.description)
    }

    @Test
    fun `отсчёт показывает параметры запущенной тренировки`() {
        val text = countdown(3)
        assertEquals("Старт через", text.title)
        assertEquals("Бег 2:00 · Шаг 1:30 · ×6", text.subtitle)
        assertEquals("0:03", text.time)
        assertNull(text.progress)
        assertEquals("Тренировка: старт через 3 секунды. Открыть таймер", text.description)
    }

    @Test
    fun `отсчёт без шага при одном повторе`() {
        val single = Workout(runSec = 1800, walkSec = 0, rounds = 1)
        val text = checkNotNull(workoutStripText(WorkoutState.Countdown(seconds = 3), single, labels))
        assertEquals("Бег 30:00 · ×1", text.subtitle)
    }

    @Test
    fun `отсчёт в минуту и в 45 секунд, время в винительном падеже`() {
        assertEquals("1:00", countdown(60).time)
        assertEquals("Тренировка: старт через 1 минуту. Открыть таймер", countdown(60).description)
        assertEquals("0:45", countdown(45).time)
        assertEquals("Тренировка: старт через 45 секунд. Открыть таймер", countdown(45).description)
        assertEquals("Тренировка: старт через 1 секунду. Открыть таймер", countdown(1).description)
        assertEquals("Тренировка: старт через 21 секунду. Открыть таймер", countdown(21).description)
    }
}
