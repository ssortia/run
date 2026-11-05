package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class ProgressRowTextTest {
    private fun formatter(zone: ZoneId = ZoneOffset.UTC) = progressDateFormatter(zone, Locale("ru"))

    private fun text(iso: String, zone: ZoneId = ZoneOffset.UTC) =
        progressDateText(Instant.parse(iso).toEpochMilli(), formatter(zone))

    private val labels = ProgressLabels(
        run = "Бег",
        walk = "Шаг",
        min = "мин",
        sec = "с",
        ringPassed = "Неделя пройдена",
        ringCount = "Пробежек %1${'$'}d из %2${'$'}d",
    )

    private fun row(week: Int, completedRuns: Int, lastCompletedAtMs: Long?) = progressRowText(
        week = WeekProgress(
            preset = WORKOUT_PRESETS.first { it.week == week },
            completedRuns = completedRuns,
            lastCompletedAtMs = lastCompletedAtMs,
            current = false,
            beforeCurrent = false,
        ),
        weekTitle = "Неделя $week",
        runsText = "$completedRuns раз",
        labels = labels,
        formatter = formatter(),
    )

    @Test
    fun `дата в родительном падеже без года`() {
        assertEquals("20 октября", text("2026-10-20T07:30:00Z"))
        assertEquals("3 ноября", text("2026-11-03T18:00:00Z"))
    }

    @Test
    fun `граница суток зависит от зоны`() {
        val iso = "2026-10-20T22:30:00Z"

        assertEquals("20 октября", text(iso, ZoneOffset.UTC))
        assertEquals("21 октября", text(iso, ZoneId.of("Europe/Moscow")))
        assertEquals("20 октября", text("2026-10-21T01:30:00Z", ZoneOffset.ofHours(-5)))
    }

    @Test
    fun `граница года зависит от зоны`() {
        val iso = "2026-12-31T22:30:00Z"

        assertEquals("31 декабря", text(iso, ZoneOffset.UTC))
        assertEquals("1 января", text(iso, ZoneId.of("Europe/Moscow")))
    }

    @Test
    fun `неделя без пробежек без даты, число читает кольцо`() {
        val row = row(week = 3, completedRuns = 0, lastCompletedAtMs = null)

        assertEquals(
            ProgressRowText(
                title = "Неделя 3",
                details = "Бег 3:00 · Шаг 2:00 · ×5",
                runs = "0 раз",
                runsSpoken = false,
                date = null,
                ringDescription = "Пробежек 0 из 4",
            ),
            row,
        )
    }

    @Test
    fun `непройденная неделя с датой последней пробежки, число читает кольцо`() {
        val row = row(week = 5, completedRuns = 3, lastCompletedAtMs = Instant.parse("2026-10-20T07:30:00Z").toEpochMilli())

        assertEquals("Бег 7:00 · Шаг 1:30 · ×3", row.details)
        assertEquals("3 раз", row.runs)
        assertEquals("20 октября", row.date)
        assertEquals("Пробежек 3 из 4", row.ringDescription)
        assertFalse(row.runsSpoken)
    }

    @Test
    fun `кольцо называет каждое число до порога`() {
        for (runs in 0 until RUNS_TO_PASS_WEEK) {
            assertEquals("Пробежек $runs из 4", row(week = 1, completedRuns = runs, lastCompletedAtMs = 0L).ringDescription)
        }
    }

    @Test
    fun `пройденная неделя описывает круг и читает число`() {
        val row = row(week = 10, completedRuns = RUNS_TO_PASS_WEEK, lastCompletedAtMs = 0L)

        assertEquals("Бег 30:00 · ×1", row.details)
        assertEquals("Неделя пройдена", row.ringDescription)
        assertTrue(row.runsSpoken)
    }

    @Test
    fun `больше порога тоже круг, число пробежек читается`() {
        val row = row(week = 2, completedRuns = 6, lastCompletedAtMs = 0L)

        assertEquals("Неделя пройдена", row.ringDescription)
        assertEquals("6 раз", row.runs)
        assertTrue(row.runsSpoken)
    }
}
