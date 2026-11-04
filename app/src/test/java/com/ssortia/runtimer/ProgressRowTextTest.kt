package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class ProgressRowTextTest {
    private fun text(iso: String, zone: ZoneId = ZoneOffset.UTC) =
        progressDateText(Instant.parse(iso).toEpochMilli(), progressDateFormatter(zone, Locale("ru")))

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
}
