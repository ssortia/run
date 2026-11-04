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

    private fun weekRecord(week: Int, startedAtMs: Long = 0L, completed: Boolean = true): RunRecord {
        val workout = WORKOUT_PRESETS.first { it.week == week }.settings.toWorkout()
        return record(workout.runSec, workout.walkSec, workout.rounds, startedAtMs, completed)
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
            assertEquals(0, it.filledQuarters)
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
        assertEquals(3, week.filledQuarters)
    }

    @Test
    fun `четыре пробежки проходят неделю`() {
        val week = programProgress(List(4) { weekRecord(1) }).week(1)
        assertTrue(week.passed)
        assertEquals(4, week.filledQuarters)
    }

    @Test
    fun `пробежки сверх четырёх считаются, а кольцо не переполняется`() {
        val week = programProgress(List(6) { weekRecord(1) }).week(1)
        assertTrue(week.passed)
        assertEquals(4, week.filledQuarters)
        assertEquals(6, week.completedRuns)
    }

    @Test
    fun `пройденные недели не по порядку`() {
        val progress = programProgress(List(4) { weekRecord(1) } + List(4) { weekRecord(3) })
        assertEquals(listOf(1, 3), progress.filter { it.passed }.map { it.week })
    }

    @Test
    fun `ходьба 90 секунд совпадает с неделями 5 и 6`() {
        assertEquals(5, presetWeekOf(record(runSec = 420, walkSec = 90, rounds = 3)))
        assertEquals(6, presetWeekOf(record(runSec = 480, walkSec = 90, rounds = 3)))
    }

    @Test
    fun `неделя 10 без ходьбы`() {
        assertEquals(10, presetWeekOf(record(runSec = 1800, walkSec = 0, rounds = 1)))
    }

    @Test
    fun `ходьба при одном повторе не мешает найти неделю 10`() {
        assertEquals(10, presetWeekOf(record(runSec = 1800, walkSec = 120, rounds = 1)))
    }

    @Test
    fun `ходьба при нескольких повторах учитывается`() {
        assertNull(presetWeekOf(record(runSec = 60, walkSec = 90, rounds = 7)))
    }

    @Test
    fun `каждая неделя программы находит себя`() {
        WORKOUT_PRESETS.forEach {
            assertEquals("неделя ${it.week}", it.week, presetWeekOf(weekRecord(it.week)))
        }
    }
}
