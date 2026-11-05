package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        statusCount = "Пробежек %1${'$'}d из %2${'$'}d",
        statusPassed = "Неделя пройдена · %s",
        now = "сейчас",
        passed = "пройдена",
        last = "последняя %s",
        noRuns = "пробежек нет",
        count = "пробежек %1${'$'}d из %2${'$'}d",
    )

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun week(
        week: Int,
        completedRuns: Int,
        lastCompletedAtMs: Long?,
        current: Boolean = false,
        beforeCurrent: Boolean = false,
    ) = WeekProgress(
        preset = WORKOUT_PRESETS.first { it.week == week },
        completedRuns = completedRuns,
        lastCompletedAtMs = lastCompletedAtMs,
        current = current,
        beforeCurrent = beforeCurrent,
    )

    private fun row(
        week: Int,
        completedRuns: Int,
        lastCompletedAtMs: Long?,
        current: Boolean = false,
        beforeCurrent: Boolean = false,
    ) = progressRowText(
        week = week(week, completedRuns, lastCompletedAtMs, current, beforeCurrent),
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
    fun `неделя без пробежек без даты и строки состояния`() {
        val row = row(week = 3, completedRuns = 0, lastCompletedAtMs = null)

        assertEquals(
            ProgressRowText(
                title = "Неделя 3",
                details = "Бег 3:00 · Шаг 2:00 · ×5",
                runs = "0 раз",
                date = null,
                status = null,
                description = "Неделя 3, пробежек нет",
            ),
            row,
        )
    }

    @Test
    fun `параметры и дата прежние`() {
        val row = row(week = 5, completedRuns = 3, lastCompletedAtMs = ms("2026-10-20T07:30:00Z"))

        assertEquals("Бег 7:00 · Шаг 1:30 · ×3", row.details)
        assertEquals("3 раз", row.runs)
        assertEquals("20 октября", row.date)
        assertEquals("Бег 30:00 · ×1", row(week = 10, completedRuns = 4, lastCompletedAtMs = 0L).details)
    }

    @Test
    fun `строка состояния текущей недели до порога называет число из 4`() {
        assertEquals("Пробежек 0 из 4", row(week = 1, completedRuns = 0, lastCompletedAtMs = null, current = true).status)
        assertEquals("Пробежек 3 из 4", row(week = 3, completedRuns = 3, lastCompletedAtMs = 0L, current = true).status)
    }

    @Test
    fun `строка состояния с порога называет настоящее число без из 4`() {
        assertEquals("Неделя пройдена · 4 раз", row(week = 3, completedRuns = 4, lastCompletedAtMs = 0L, current = true).status)
        assertEquals("Неделя пройдена · 6 раз", row(week = 3, completedRuns = 6, lastCompletedAtMs = 0L, current = true).status)
    }

    @Test
    fun `строки состояния нет у остальных недель`() {
        assertNull(row(week = 1, completedRuns = 5, lastCompletedAtMs = 0L).status)
        assertNull(row(week = 2, completedRuns = 2, lastCompletedAtMs = 0L, beforeCurrent = true).status)
        assertNull(row(week = 4, completedRuns = 0, lastCompletedAtMs = null).status)
    }

    @Test
    fun `описание текущей недели`() {
        val row = row(week = 3, completedRuns = 3, lastCompletedAtMs = ms("2026-11-03T18:00:00Z"), current = true)

        assertEquals("Неделя 3, сейчас, пробежек 3 из 4, последняя 3 ноября", row.description)
    }

    @Test
    fun `описание текущей пройденной недели`() {
        val row = row(week = 3, completedRuns = 6, lastCompletedAtMs = ms("2026-11-03T18:00:00Z"), current = true)

        assertEquals("Неделя 3, сейчас, пройдена, 6 раз, последняя 3 ноября", row.description)
    }

    @Test
    fun `описание текущей недели без пробежек`() {
        val row = row(week = 1, completedRuns = 0, lastCompletedAtMs = null, current = true)

        assertEquals("Неделя 1, сейчас, пробежек нет", row.description)
    }

    @Test
    fun `описание пройденной недели`() {
        val row = row(week = 1, completedRuns = 5, lastCompletedAtMs = ms("2026-10-20T07:30:00Z"), beforeCurrent = true)

        assertEquals(WeekMark.PASSED, week(1, 5, 0L, beforeCurrent = true).mark)
        assertEquals("Неделя 1, пройдена, 5 раз, последняя 20 октября", row.description)
    }

    @Test
    fun `описание пропущенной недели с пробежками`() {
        val row = row(week = 2, completedRuns = 2, lastCompletedAtMs = ms("2026-10-29T07:30:00Z"), beforeCurrent = true)

        assertEquals(WeekMark.SKIPPED, week(2, 2, 0L, beforeCurrent = true).mark)
        assertEquals("Неделя 2, пробежек 2 из 4, последняя 29 октября", row.description)
    }

    @Test
    fun `описание недели впереди без пробежек`() {
        val row = row(week = 4, completedRuns = 0, lastCompletedAtMs = null)

        assertEquals(WeekMark.AHEAD, week(4, 0, null).mark)
        assertEquals("Неделя 4, пробежек нет", row.description)
    }
}
