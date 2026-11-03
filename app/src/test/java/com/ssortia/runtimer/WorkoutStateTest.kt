package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WorkoutStateTest {
    private val run = Position(index = 0, isRun = true, round = 1, remainingMs = 10_000)
    private val walk = Position(index = 1, isRun = false, round = 1, remainingMs = 10_000)

    // 10 с бега, 10 с ходьбы, 2 повтора: всего 30 с и 3 отрезка
    private val workout = Workout(runSec = 10, walkSec = 10, rounds = 2)

    @Test
    fun `старт не даёт сигнала`() {
        assertNull(signalFor(null, run))
    }

    @Test
    fun `внутри отрезка сигнала нет`() {
        assertNull(signalFor(1, walk))
    }

    @Test
    fun `смена отрезка даёт обычный сигнал`() {
        assertEquals(Signal.INTERVAL, signalFor(0, walk))
    }

    @Test
    fun `конец тренировки даёт финальный сигнал`() {
        assertEquals(Signal.FINISH, signalFor(1, null))
    }

    @Test
    fun `конец тренировки на первом шаге даёт финальный сигнал`() {
        assertEquals(Signal.FINISH, signalFor(null, null))
    }

    @Test
    fun `пропущенный отрезок даёт обычный сигнал`() {
        assertEquals(Signal.INTERVAL, signalFor(0, run.copy(index = 2, round = 2)))
    }

    @Test
    fun `время показывает минуты и секунды с ведущим нулём`() {
        assertEquals("2:00", formatTime(120_000))
        assertEquals("1:05", formatTime(65_000))
        assertEquals("99:59", formatTime(5_999_000))
    }

    @Test
    fun `минуты больше 99 показываются полностью`() {
        assertEquals("100:00", formatTime(6_000_000))
        assertEquals("396:00", formatTime(23_760_000))
    }

    @Test
    fun `цифры времени не зависят от языка системы`() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-u-nu-arab"))
        try {
            assertEquals("2:00", formatTime(120_000))
            assertEquals("1:00:00", formatTotal(3_600_000))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `итог меньше часа показывается как минуты и секунды`() {
        assertEquals("26:00", formatTotal(1_560_000))
        assertEquals("59:59", formatTotal(3_599_000))
        assertEquals("0:00", formatTotal(0))
    }

    @Test
    fun `итог от часа показывается с часами`() {
        assertEquals("1:00:00", formatTotal(3_600_000))
        assertEquals("1:00:00", formatTotal(3_599_001))
        assertEquals("6:36:00", formatTotal(23_760_000))
    }

    @Test
    fun `итог максимальной тренировки читается`() {
        assertEquals("328:16:43", formatTotal(WorkoutSettings(99, 59, 99, 59, 99).toWorkout().totalMs))
    }

    @Test
    fun `секунды округляются вверх`() {
        assertEquals("2:00", formatTime(119_001))
        assertEquals("1:59", formatTime(119_000))
        assertEquals("0:02", formatTime(1_250))
    }

    @Test
    fun `вблизи нуля время не обнуляется раньше конца`() {
        assertEquals("0:01", formatTime(1_000))
        assertEquals("0:01", formatTime(1))
        assertEquals("0:00", formatTime(0))
        assertEquals("0:00", formatTime(-1))
    }

    @Test
    fun `отсчёт показывает целые секунды`() {
        assertEquals(5L, wholeSeconds(5_000))
    }

    @Test
    fun `секунды отсчёта округляются вверх`() {
        assertEquals(5L, wholeSeconds(4_001))
        assertEquals(4L, wholeSeconds(4_000))
    }

    @Test
    fun `в конце отсчёта остаётся единица, а не ноль`() {
        assertEquals(1L, wholeSeconds(1))
    }

    // Отсчёт с произвольным началом: значения elapsedRealtime не обязаны начинаться с нуля
    private fun countdownTick(
        remainingMs: Long,
        soundFailed: Boolean = false,
        soundMuted: Boolean = false,
    ) = countdownTickAt(
        startedAt = 100_000 + remainingMs,
        now = 100_000,
        soundFailed = soundFailed,
        soundMuted = soundMuted,
    )

    @Test
    fun `отсчёт заканчивается, когда остаток дошёл до нуля`() {
        assertNull(countdownTick(remainingMs = 0))
    }

    @Test
    fun `при задержке 0 отсчёта нет`() {
        val now = 100_000L

        assertNull(countdownTickAt(startedAt = now + 0, now = now))
    }

    @Test
    fun `опоздавший шаг тоже заканчивает отсчёт`() {
        assertNull(countdownTick(remainingMs = -1))
    }

    @Test
    fun `ожидание не длиннее остатка`() {
        assertEquals(1L, countdownTick(remainingMs = 1)?.waitMs)
        assertEquals(249L, countdownTick(remainingMs = 249)?.waitMs)
    }

    @Test
    fun `ожидание не длиннее шага цикла`() {
        assertEquals(TICK_MS, countdownTick(remainingMs = 250)?.waitMs)
        assertEquals(TICK_MS, countdownTick(remainingMs = 251)?.waitMs)
    }

    @Test
    fun `шаг отсчёта публикует секунды с округлением вверх`() {
        assertEquals(1L, countdownTick(remainingMs = 1)?.state?.seconds)
        assertEquals(4L, countdownTick(remainingMs = 4_000)?.state?.seconds)
        assertEquals(5L, countdownTick(remainingMs = 4_001)?.state?.seconds)
    }

    @Test
    fun `внутри одной секунды состояние отсчёта не меняется`() {
        assertEquals(countdownTick(remainingMs = 4_750)?.state, countdownTick(remainingMs = 4_001)?.state)
    }

    @Test
    fun `сбой звука доходит до состояния отсчёта`() {
        assertTrue(countdownTick(remainingMs = 1_000, soundFailed = true)!!.state.soundFailed)
        assertFalse(countdownTick(remainingMs = 1_000)!!.state.soundFailed)
    }

    @Test
    fun `нулевая громкость доходит до состояния отсчёта`() {
        assertTrue(countdownTick(remainingMs = 1_000, soundMuted = true)!!.state.soundMuted)
        assertFalse(countdownTick(remainingMs = 1_000)!!.state.soundMuted)
        assertFalse(countdownTick(remainingMs = 1_000, soundFailed = true)!!.state.soundMuted)
    }

    @Test
    fun `общая длительность равна сумме всех отрезков`() {
        assertEquals(30_000L, workout.totalMs)
        assertEquals(40_000L, Workout(10, 5, 3).totalMs)
        assertEquals(3_000L, Workout(3, 2, 1).totalMs)
        assertEquals(1_560_000L, WorkoutSettings().toWorkout().totalMs)
    }

    @Test
    fun `прогресс растёт от нуля до единицы`() {
        assertEquals(0f, workout.progressAt(0), 0f)
        assertEquals(1f / 3, workout.progressAt(10_000), 1e-6f)
        assertEquals(2f / 3, workout.progressAt(20_000), 1e-6f)
        assertEquals(1f, workout.progressAt(30_000), 0f)
    }

    @Test
    fun `прогресс вблизи нуля положительный и мал`() {
        assertEquals(1f / 30_000, workout.progressAt(1), 1e-7f)
    }

    @Test
    fun `прогресс не выходит за границы`() {
        assertEquals(0f, workout.progressAt(-1), 0f)
        assertEquals(1f, workout.progressAt(30_001), 0f)
        assertEquals(1f, workout.progressAt(Long.MAX_VALUE), 0f)
    }

    /** Гоняет цикл как сервис: шаги берутся по кругу, пока [tickAt] не сообщит о конце. */
    private fun simulate(workout: Workout, vararg stepsMs: Long): List<WorkoutTick> {
        val ticks = mutableListOf<WorkoutTick>()
        var previousIndex: Int? = null
        var elapsed = 0L
        var step = 0

        while (true) {
            val tick = tickAt(workout, elapsed, previousIndex)
            ticks += tick
            if (tick.finished) return ticks
            previousIndex = tick.state.position.index
            elapsed += stepsMs[step++ % stepsMs.size]
        }
    }

    private val twoIntervalsAndFinish = listOf(Signal.INTERVAL, Signal.INTERVAL, Signal.FINISH)

    @Test
    fun `цикл с шагом 250 мс даёт два обычных сигнала и один финальный`() {
        assertEquals(twoIntervalsAndFinish, simulate(workout, 250).mapNotNull { it.signal })
    }

    @Test
    fun `шаги не попадают на границы отрезков, а сигналы те же`() {
        assertEquals(twoIntervalsAndFinish, simulate(workout, 253).mapNotNull { it.signal })
    }

    @Test
    fun `неровные шаги дают те же сигналы`() {
        val ticks = simulate(workout, 137, 251, 499, 73, 1_210)

        assertEquals(twoIntervalsAndFinish, ticks.mapNotNull { it.signal })
    }

    @Test
    fun `шаг через целый отрезок даёт один обычный сигнал`() {
        // Отрезки по 1 с: 0 -> 2500 попадает сразу в третий отрезок, 2750 -> 5250 за конец
        val ticks = simulate(Workout(runSec = 1, walkSec = 1, rounds = 2), 2_500, 250)

        assertEquals(2, ticks[1].state.position.index)
        assertEquals(Signal.INTERVAL, ticks[1].signal)
        assertEquals(listOf(Signal.INTERVAL, Signal.FINISH), ticks.mapNotNull { it.signal })
    }

    @Test
    fun `зависание через конец тренировки даёт только один финальный сигнал`() {
        val ticks = simulate(workout, 250, 60_000)

        assertEquals(listOf(Signal.FINISH), ticks.mapNotNull { it.signal })
        assertEquals(1, ticks.count { it.finished })
        assertTrue(ticks.last().finished)
    }

    @Test
    fun `шаг внутри тренировки публикует позицию и прогресс`() {
        val tick = tickAt(workout, 12_500, previousIndex = 0)

        assertEquals(
            WorkoutState.Active(
                position = Position(index = 1, isRun = false, round = 1, remainingMs = 7_500),
                rounds = 2,
                progress = 12_500f / 30_000,
            ),
            tick.state,
        )
        assertEquals(Signal.INTERVAL, tick.signal)
        assertFalse(tick.finished)
    }

    @Test
    fun `финал показывает последний отрезок с нулевым остатком`() {
        // Предыдущий шаг был в ходьбе: запоздавший финал всё равно показывает последний бег
        val tick = tickAt(workout, 45_000, previousIndex = 1)

        assertEquals(
            WorkoutState.Active(
                position = Position(index = 2, isRun = true, round = 2, remainingMs = 0),
                rounds = 2,
                progress = 1f,
            ),
            tick.state,
        )
        assertEquals(Signal.FINISH, tick.signal)
        assertTrue(tick.finished)
    }

    @Test
    fun `при одном повторе звучит только финальный сигнал и показан бег с нулём`() {
        val ticks = simulate(Workout(runSec = 3, walkSec = 2, rounds = 1), 250)

        assertEquals(listOf(Signal.FINISH), ticks.mapNotNull { it.signal })
        assertEquals(
            Position(index = 0, isRun = true, round = 1, remainingMs = 0),
            ticks.last().state.position,
        )
        assertEquals(0.5f, ticks[6].state.progress, 0f)
        assertEquals(1f, ticks.last().state.progress, 0f)
    }

    @Test
    fun `первый шаг уже после конца тренировки сразу завершает её`() {
        val tick = tickAt(workout, 50_000, previousIndex = null)

        assertEquals(2, tick.state.position.index)
        assertEquals(Signal.FINISH, tick.signal)
        assertTrue(tick.finished)
    }

    @Test
    fun `признак сбоя звука доходит до состояния`() {
        assertFalse(tickAt(workout, 0, null).state.soundFailed)
        assertTrue(tickAt(workout, 0, null, soundFailed = true).state.soundFailed)
        assertTrue(tickAt(workout, 30_000, 2, soundFailed = true).state.soundFailed)
    }

    @Test
    fun `нулевая громкость доходит до состояния`() {
        assertFalse(tickAt(workout, 0, null).state.soundMuted)
        assertFalse(tickAt(workout, 0, null, soundFailed = true).state.soundMuted)
        assertTrue(tickAt(workout, 0, null, soundMuted = true).state.soundMuted)
        assertTrue(tickAt(workout, 30_000, 2, soundMuted = true).state.soundMuted)
    }

    @Test
    fun `смена громкости между шагами не даёт лишнего сигнала`() {
        val muted = tickAt(workout, 2_000, previousIndex = 0, soundMuted = true)
        val unmuted = tickAt(workout, 2_250, previousIndex = 0, soundMuted = false)

        assertTrue(muted.state.soundMuted)
        assertFalse(unmuted.state.soundMuted)
        assertNull(muted.signal)
        assertNull(unmuted.signal)
    }

    @Test
    fun `нулевая громкость не отменяет сигналы`() {
        // Сигнал подаётся всё равно: громкость могут прибавить в любой момент
        assertEquals(Signal.INTERVAL, tickAt(workout, 10_000, previousIndex = 0, soundMuted = true).signal)
        assertEquals(Signal.FINISH, tickAt(workout, 30_000, previousIndex = 2, soundMuted = true).signal)
    }

    @Test
    fun `признак паузы доходит до состояния`() {
        assertFalse(tickAt(workout, 5_000, 0).state.paused)
        assertTrue(tickAt(workout, 5_000, 0, paused = true).state.paused)
        assertTrue(tickAt(workout, 30_000, 2, paused = true).state.paused)
    }

    @Test
    fun `на паузе в том же отрезке сигнала нет`() {
        // Время замерло, поэтому шаги на паузе повторяют один и тот же момент
        val ticks = List(3) { tickAt(workout, 9_900, previousIndex = 0, paused = true) }

        ticks.forEach {
            assertNull(it.signal)
            assertFalse(it.finished)
            assertEquals(0, it.state.position.index)
        }
    }

    @Test
    fun `граница, пройденная до паузы, даёт обычный сигнал на паузе`() {
        val tick = tickAt(workout, 10_100, previousIndex = 0, paused = true)

        assertEquals(Signal.INTERVAL, tick.signal)
        assertEquals(1, tick.state.position.index)
        assertTrue(tick.state.paused)
    }
}
