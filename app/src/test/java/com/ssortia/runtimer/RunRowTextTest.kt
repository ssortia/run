package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class RunRowTextTest {
    private val startedAt = Instant.parse("2026-11-03T07:30:00Z").toEpochMilli()

    private fun formatters(zone: ZoneId = ZoneOffset.UTC) = runFormatters(zone, Locale("ru"))

    private fun row(record: RunRecord, zone: ZoneId = ZoneOffset.UTC) = runRowText(record, formatters(zone))

    private fun record(
        durationMs: Long = 300_000,
        completed: Boolean = false,
        runSec: Int = 60,
        walkSec: Int = 90,
        rounds: Int = 8,
        startedAtMs: Long = startedAt,
        id: Long = 0,
    ) = RunRecord(
        id = id,
        startedAtMs = startedAtMs,
        runSec = runSec,
        walkSec = walkSec,
        rounds = rounds,
        durationMs = durationMs,
        completed = completed,
    )

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `карточка завершённой записи`() {
        assertEquals(
            RunRowText(
                day = "3",
                weekday = "вт",
                runTime = "1:00",
                walkTime = "1:30",
                meta = "×8 · 07:30",
                duration = "18:30",
                completed = true,
                date = "3 ноября 2026, 07:30",
            ),
            row(record(1_110_000, completed = true)),
        )
    }

    @Test
    fun `карточка остановленной записи`() {
        val text = row(record(300_000, completed = false))

        assertEquals(false, text.completed)
        assertEquals("5:00", text.duration)
    }

    @Test
    fun `без ходьбы время ходьбы не показывается`() {
        val text = row(record(1_800_000, completed = true, runSec = 1_800, walkSec = 0, rounds = 1))

        assertNull(text.walkTime)
        assertEquals("30:00", text.runTime)
        assertEquals("×1 · 07:30", text.meta)
    }

    @Test
    fun `при одном повторе ходьба не показывается, даже если задана`() {
        val text = row(record(runSec = 1_800, walkSec = 90, rounds = 1))

        assertNull(text.walkTime)
        assertEquals("×1 · 07:30", text.meta)
    }

    @Test
    fun `meta содержит повторы и время старта`() {
        assertEquals("×7 · 18:42", row(record(rounds = 7, startedAtMs = at("2026-11-03T18:42:00Z"))).meta)
    }

    @Test
    fun `длительность округляется вниз до секунды`() {
        assertEquals("0:30", row(record(30_200)).duration)
    }

    @Test
    fun `длительность от часа показывается с часами`() {
        assertEquals("1:00:00", row(record(3_600_999)).duration)
    }

    @Test
    fun `день и время берутся в переданной зоне и могут сменить день`() {
        val text = row(record(startedAtMs = at("2026-11-02T22:30:00Z")), zone = ZoneOffset.ofHours(3))

        assertEquals("3", text.day)
        assertEquals("вт", text.weekday)
        assertEquals("×8 · 01:30", text.meta)
        assertEquals("3 ноября 2026, 01:30", text.date)
    }

    @Test
    fun `двузначные день и час, месяц в родительном падеже`() {
        val text = row(record(startedAtMs = at("2026-11-23T21:05:00Z")))

        assertEquals("23", text.day)
        assertEquals("23 ноября 2026, 21:05", text.date)
    }

    @Test
    fun `заголовок месяца в именительном падеже прописными`() {
        assertEquals("НОЯБРЬ 2026", runMonthTitle(YearMonth.of(2026, 11), formatters()))
    }

    @Test
    fun `пустой список даёт пустые группы`() {
        assertEquals(emptyList<RunMonth>(), groupRunsByMonth(emptyList(), ZoneOffset.UTC))
    }

    @Test
    fun `записи группируются по месяцам с сохранением порядка`() {
        val a = record(id = 4, startedAtMs = at("2026-11-20T10:00:00Z"))
        val b = record(id = 3, startedAtMs = at("2026-11-03T10:00:00Z"))
        val c = record(id = 2, startedAtMs = at("2026-10-15T10:00:00Z"))
        val d = record(id = 1, startedAtMs = at("2025-11-10T10:00:00Z"))

        assertEquals(
            listOf(
                RunMonth(YearMonth.of(2026, 11), listOf(a, b)),
                RunMonth(YearMonth.of(2026, 10), listOf(c)),
                RunMonth(YearMonth.of(2025, 11), listOf(d)),
            ),
            groupRunsByMonth(listOf(a, b, c, d), ZoneOffset.UTC),
        )
    }

    @Test
    fun `запись на границе месяцев попадает в месяц по зоне`() {
        // 31 октября 22:30 по UTC — это уже 1 ноября по Москве
        val boundary = record(id = 2, startedAtMs = at("2026-10-31T22:30:00Z"))
        val october = record(id = 1, startedAtMs = at("2026-10-20T10:00:00Z"))
        val records = listOf(boundary, october)

        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)),
            groupRunsByMonth(records, ZoneOffset.ofHours(3)).map { it.yearMonth },
        )
        assertEquals(
            listOf(RunMonth(YearMonth.of(2026, 10), records)),
            groupRunsByMonth(records, ZoneOffset.UTC),
        )
    }

    @Test
    fun `записи месяца не подряд попадают в одну группу`() {
        val a = record(id = 3, startedAtMs = at("2026-11-20T10:00:00Z"))
        val b = record(id = 2, startedAtMs = at("2026-10-15T10:00:00Z"))
        val c = record(id = 1, startedAtMs = at("2026-11-03T10:00:00Z"))

        assertEquals(
            listOf(
                RunMonth(YearMonth.of(2026, 11), listOf(a, c)),
                RunMonth(YearMonth.of(2026, 10), listOf(b)),
            ),
            groupRunsByMonth(listOf(a, b, c), ZoneOffset.UTC),
        )
    }
}
