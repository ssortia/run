package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class RunRecordTest {
    // 8 повторов по 1:00 бега и 1:30 ходьбы, всего 18:30
    private val workout = Workout(runSec = 60, walkSec = 90, rounds = 8)
    private val startedAt = Instant.parse("2026-11-03T07:30:00Z").toEpochMilli()

    private fun row(record: RunRecord) = runRowText(
        record,
        zone = ZoneOffset.UTC,
        locale = Locale("ru"),
        runLabel = "Бег",
        walkLabel = "Ходьба",
        roundsLabel = "Повторы",
        completedLabel = "завершена",
        stoppedLabel = "остановлена",
    )

    private fun record(
        durationMs: Long,
        completed: Boolean,
        runSec: Int = 60,
        walkSec: Int = 90,
        rounds: Int = 8,
    ) = RunRecord(
        startedAtMs = startedAt,
        runSec = runSec,
        walkSec = walkSec,
        rounds = rounds,
        durationMs = durationMs,
        completed = completed,
    )

    @Test
    fun `завершённая тренировка даёт запись с полной длительностью`() {
        val record = runRecordOrNull(workout, startedAt, workout.totalMs)

        assertEquals(
            RunRecord(
                startedAtMs = startedAt,
                runSec = 60,
                walkSec = 90,
                rounds = 8,
                durationMs = 1_110_000,
                completed = true,
            ),
            record,
        )
    }

    @Test
    fun `остановленная тренировка даёт запись с фактической длительностью`() {
        val record = runRecordOrNull(workout, startedAt, 300_000)

        assertNotNull(record)
        assertFalse(record!!.completed)
        assertEquals(300_000, record.durationMs)
        assertEquals(startedAt, record.startedAtMs)
    }

    @Test
    fun `остановка раньше 30 секунд не даёт записи`() {
        assertNull(runRecordOrNull(workout, startedAt, 29_999))
        assertNull(runRecordOrNull(workout, startedAt, 0))
    }

    @Test
    fun `остановка ровно на 30 секундах даёт запись`() {
        val record = runRecordOrNull(workout, startedAt, MIN_STOPPED_RUN_MS)

        assertEquals(30_000L, record?.durationMs)
        assertEquals(false, record?.completed)
    }

    @Test
    fun `короткая тренировка, дошедшая до финала, сохраняется`() {
        val short = Workout(runSec = 10, walkSec = 0, rounds = 1)

        val record = runRecordOrNull(short, startedAt, 10_000)

        assertEquals(10_000L, record?.durationMs)
        assertEquals(true, record?.completed)
    }

    @Test
    fun `длительность, равная общей, означает завершённую тренировку`() {
        assertTrue(runRecordOrNull(workout, startedAt, workout.totalMs)!!.completed)
        assertFalse(runRecordOrNull(workout, startedAt, workout.totalMs - 1)!!.completed)
    }

    @Test
    fun `остановка на финальном сигнале даёт завершённую запись без хвоста`() {
        val record = runRecordOrNull(workout, startedAt, workout.totalMs + 1_500)

        assertEquals(true, record?.completed)
        assertEquals(workout.totalMs, record?.durationMs)
    }

    @Test
    fun `строка завершённой записи`() {
        assertEquals(
            RunRowText(
                date = "3 ноября 2026, 07:30",
                details = "Бег 1:00 · Ходьба 1:30 · Повторы 8 · 18:30 · завершена",
            ),
            row(record(1_110_000, completed = true)),
        )
    }

    @Test
    fun `строка остановленной записи`() {
        assertEquals(
            "Бег 1:00 · Ходьба 1:30 · Повторы 8 · 5:00 · остановлена",
            row(record(300_000, completed = false)).details,
        )
    }

    @Test
    fun `строка записи без ходьбы не показывает ходьбу`() {
        assertEquals(
            "Бег 30:00 · Повторы 1 · 30:00 · завершена",
            row(record(1_800_000, completed = true, runSec = 1_800, walkSec = 0, rounds = 1)).details,
        )
    }

    @Test
    fun `длительность в строке округляется вниз до секунды`() {
        assertEquals(
            "Бег 1:00 · Ходьба 1:30 · Повторы 8 · 0:30 · остановлена",
            row(record(30_200, completed = false)).details,
        )
    }

    @Test
    fun `длительность от часа показывается с часами`() {
        assertEquals(
            "Бег 1:00 · Ходьба 1:30 · Повторы 8 · 1:00:00 · остановлена",
            row(record(3_600_999, completed = false)).details,
        )
    }

    @Test
    fun `дата строки берётся в переданной зоне`() {
        val row = runRowText(
            record(300_000, completed = false),
            zone = ZoneOffset.ofHours(3),
            locale = Locale("ru"),
            runLabel = "Бег",
            walkLabel = "Ходьба",
            roundsLabel = "Повторы",
            completedLabel = "завершена",
            stoppedLabel = "остановлена",
        )

        assertEquals("3 ноября 2026, 10:30", row.date)
    }
}
