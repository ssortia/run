package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RunRecordTest {
    // 8 повторов по 1:00 бега и 1:30 шага, всего 18:30
    private val workout = Workout(runSec = 60, walkSec = 90, rounds = 8)
    private val startedAt = Instant.parse("2026-11-03T07:30:00Z").toEpochMilli()

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

        assertEquals(false, record?.completed)
        assertEquals(300_000L, record?.durationMs)
        assertEquals(startedAt, record?.startedAtMs)
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
    fun `20 секунд бега и 5 минут паузы не дают записи`() {
        val clock = WorkoutClock(workout.totalMs, startedAt = 0)
        clock.pause(20_000)

        assertNull(runRecordOrNull(workout, startedAt, clock.elapsedAt(320_000)))

        // После продолжения время паузы тоже не засчитывается
        clock.resume(320_000)
        assertNull(runRecordOrNull(workout, startedAt, clock.elapsedAt(325_000)))
        assertEquals(30_000L, runRecordOrNull(workout, startedAt, clock.elapsedAt(330_000))?.durationMs)
    }

    @Test
    fun `короткая тренировка, дошедшая до финала, сохраняется`() {
        val short = Workout(runSec = 10, walkSec = 0, rounds = 1)

        val record = runRecordOrNull(short, startedAt, 10_000)

        assertEquals(10_000L, record?.durationMs)
        assertEquals(true, record?.completed)
    }

    @Test
    fun `короткая тренировка, остановленная до финала, не сохраняется`() {
        val short = Workout(runSec = 10, walkSec = 0, rounds = 1)

        assertNull(runRecordOrNull(short, startedAt, 5_000))
        assertNull(runRecordOrNull(short, startedAt, short.totalMs - 1))
    }

    @Test
    fun `длительность, равная общей, означает завершённую тренировку`() {
        assertEquals(true, runRecordOrNull(workout, startedAt, workout.totalMs)?.completed)
        assertEquals(false, runRecordOrNull(workout, startedAt, workout.totalMs - 1)?.completed)
    }

    @Test
    fun `остановка на финальном сигнале даёт завершённую запись без хвоста`() {
        val record = runRecordOrNull(workout, startedAt, workout.totalMs + 1_500)

        assertEquals(true, record?.completed)
        assertEquals(workout.totalMs, record?.durationMs)
    }

    @Test
    fun `системное время старта пересчитывается к моменту по elapsedRealtime`() {
        // Сейчас 10:00:05 по системным часам и 50 000 по elapsedRealtime, бег начался на 45 000
        val now = Instant.parse("2026-11-03T10:00:05Z").toEpochMilli()

        assertEquals(
            Instant.parse("2026-11-03T10:00:00Z").toEpochMilli(),
            wallTimeAt(nowWallMs = now, nowElapsedMs = 50_000, elapsedAt = 45_000),
        )
        assertEquals(now, wallTimeAt(nowWallMs = now, nowElapsedMs = 50_000, elapsedAt = 50_000))
    }
}
