package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramProgressTest {
    private fun record(
        runSec: Int,
        walkSec: Int,
        rounds: Int,
        startedAtMs: Long = 0L,
        completed: Boolean = true,
    ) = RunRecord(
        startedAtMs = startedAtMs,
        runSec = runSec,
        walkSec = walkSec,
        rounds = rounds,
        durationMs = 0L,
        completed = completed,
    )

    // Бег, шаг и повторы недель программы в секундах, записаны вручную, а не через toWorkout()
    private val weekParams = mapOf(
        1 to Triple(60, 120, 7),
        2 to Triple(120, 120, 6),
        3 to Triple(180, 120, 5),
        4 to Triple(270, 120, 4),
        5 to Triple(420, 90, 3),
        6 to Triple(480, 90, 3),
        7 to Triple(780, 60, 2),
        8 to Triple(840, 60, 2),
        9 to Triple(900, 60, 2),
        10 to Triple(1800, 0, 1),
    )

    private fun weekRecord(week: Int, startedAtMs: Long = 0L, completed: Boolean = true): RunRecord {
        val (runSec, walkSec, rounds) = weekParams.getValue(week)
        return record(runSec, walkSec, rounds, startedAtMs, completed)
    }

    private fun List<WeekProgress>.week(week: Int) = first { it.week == week }

    @Test
    fun `пустая история даёт 10 недель без пробежек`() {
        val progress = programProgress(emptyList())
        assertEquals((1..10).toList(), progress.map { it.week })
        progress.forEach {
            assertEquals(0, it.completedRuns)
            assertNull(it.lastCompletedAtMs)
            assertFalse(it.passed)
        }
    }

    @Test
    fun `остановленные пробежки не считаются`() {
        val progress = programProgress(List(5) { weekRecord(1, startedAtMs = it.toLong(), completed = false) })
        assertEquals(0, progress.week(1).completedRuns)
        assertNull(progress.week(1).lastCompletedAtMs)
    }

    @Test
    fun `число и последняя пробежка не зависят от порядка списка`() {
        val runs = listOf(
            weekRecord(2, startedAtMs = 200L),
            weekRecord(2, startedAtMs = 500L),
            weekRecord(2, startedAtMs = 900L, completed = false),
            weekRecord(2, startedAtMs = 100L),
        )
        listOf(runs, runs.reversed(), runs.shuffled(java.util.Random(1))).forEach {
            val week = programProgress(it).week(2)
            assertEquals(3, week.completedRuns)
            assertEquals(500L, week.lastCompletedAtMs)
        }
    }

    @Test
    fun `пробежка не из программы не учитывается`() {
        val progress = programProgress(listOf(record(runSec = 50, walkSec = 70, rounds = 3)))
        assertEquals(0, progress.sumOf { it.completedRuns })
    }

    @Test
    fun `три пробежки не проходят неделю`() {
        val week = programProgress(List(3) { weekRecord(1) }).week(1)
        assertFalse(week.passed)
        assertEquals(3, week.completedRuns)
    }

    @Test
    fun `четыре пробежки проходят неделю`() {
        val week = programProgress(List(4) { weekRecord(1) }).week(1)
        assertTrue(week.passed)
        assertEquals(4, week.completedRuns)
    }

    @Test
    fun `пробежки сверх четырёх считаются`() {
        val week = programProgress(List(6) { weekRecord(1) }).week(1)
        assertTrue(week.passed)
        assertEquals(6, week.completedRuns)
    }

    @Test
    fun `пройденные недели не по порядку`() {
        val progress = programProgress(List(4) { weekRecord(1) } + List(4) { weekRecord(3) })
        assertEquals(listOf(1, 3), progress.filter { it.passed }.map { it.week })
        assertEquals(listOf(4, 0, 4, 0, 0, 0, 0, 0, 0, 0), progress.map { it.completedRuns })
        assertNull(progress.week(2).lastCompletedAtMs)
    }

    @Test
    fun `остановленные пробежки другой недели не смешиваются с завершёнными`() {
        val progress = programProgress(
            List(2) { weekRecord(1, startedAtMs = 100L) } +
                List(3) { weekRecord(2, startedAtMs = 900L, completed = false) },
        )
        assertEquals(2, progress.week(1).completedRuns)
        assertEquals(0, progress.week(2).completedRuns)
        assertNull(progress.week(2).lastCompletedAtMs)
    }

    @Test
    fun `строка недели совпадает с неделей из своей записи`() {
        programProgress(emptyList()).forEach { assertEquals(it.week, it.preset.week) }
    }

    @Test
    fun `шаг 90 секунд совпадает с неделями 5 и 6`() {
        assertEquals(5, presetWeekOf(record(runSec = 420, walkSec = 90, rounds = 3)))
        assertEquals(6, presetWeekOf(record(runSec = 480, walkSec = 90, rounds = 3)))
    }

    @Test
    fun `неделя 10 без шага`() {
        assertEquals(10, presetWeekOf(record(runSec = 1800, walkSec = 0, rounds = 1)))
    }

    @Test
    fun `шаг при одном повторе не мешает найти неделю 10`() {
        assertEquals(10, presetWeekOf(record(runSec = 1800, walkSec = 120, rounds = 1)))
    }

    @Test
    fun `шаг при нескольких повторах учитывается`() {
        assertNull(presetWeekOf(record(runSec = 60, walkSec = 90, rounds = 7)))
    }

    @Test
    fun `соседние параметры недель не засчитываются`() {
        // Бег недели 7 без шага: при двух повторах шаг выполняется и должен совпасть
        assertNull(presetWeekOf(record(runSec = 780, walkSec = 0, rounds = 2)))
        // Один повтор с бегом не 30 минут не неделя 10, даже без учёта шага
        assertNull(presetWeekOf(record(runSec = 600, walkSec = 120, rounds = 1)))
    }

    @Test
    fun `каждая неделя программы находит себя`() {
        WORKOUT_PRESETS.forEach {
            assertEquals("неделя ${it.week}", it.week, presetWeekOf(weekRecord(it.week)))
        }
    }

    // Путь сервиса: настройки недели → workoutOrNull → runRecordOrNull в onDestroy → прогресс
    @Test
    fun `запись сервиса засчитывается своей неделе только при завершении`() {
        WORKOUT_PRESETS.forEach { preset ->
            val settings = preset.settings
            val workout = requireNotNull(
                workoutOrNull(
                    runSec = settings.runMin * 60 + settings.runSec,
                    walkSec = settings.walkMin * 60 + settings.walkSec,
                    rounds = settings.rounds,
                ),
            ) { "неделя ${preset.week}" }
            val finished = requireNotNull(runRecordOrNull(workout, 1_000L, elapsedMs = workout.totalMs))
            val stopped = requireNotNull(runRecordOrNull(workout, 2_000L, elapsedMs = workout.totalMs - 1))

            val week = programProgress(listOf(finished, stopped)).week(preset.week)
            assertEquals("неделя ${preset.week}", 1, week.completedRuns)
            assertEquals("неделя ${preset.week}", 1_000L, week.lastCompletedAtMs)
        }
    }
}
