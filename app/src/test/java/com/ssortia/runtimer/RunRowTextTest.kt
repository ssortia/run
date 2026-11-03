package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class RunRowTextTest {
    private val startedAt = Instant.parse("2026-11-03T07:30:00Z").toEpochMilli()

    private fun row(record: RunRecord, zone: ZoneId = ZoneOffset.UTC) = runRowText(
        record,
        dateFormatter = runDateFormatter(zone, Locale("ru")),
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
        startedAtMs: Long = startedAt,
    ) = RunRecord(
        startedAtMs = startedAtMs,
        runSec = runSec,
        walkSec = walkSec,
        rounds = rounds,
        durationMs = durationMs,
        completed = completed,
    )

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
    fun `дата строки берётся в переданной зоне и может сменить день`() {
        val record = record(
            300_000,
            completed = false,
            startedAtMs = Instant.parse("2026-11-02T22:30:00Z").toEpochMilli(),
        )

        assertEquals("3 ноября 2026, 01:30", row(record, zone = ZoneOffset.ofHours(3)).date)
    }

    @Test
    fun `двузначные день и час, месяц в родительном падеже`() {
        val record = record(
            300_000,
            completed = false,
            startedAtMs = Instant.parse("2026-11-23T21:05:00Z").toEpochMilli(),
        )

        assertEquals("23 ноября 2026, 21:05", row(record).date)
    }

    @Test
    fun `дата для диалога совпадает с датой строки`() {
        val formatter = runDateFormatter(ZoneOffset.UTC, Locale("ru"))

        assertEquals("3 ноября 2026, 07:30", runDateText(startedAt, formatter))
    }
}
