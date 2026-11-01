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

    // 10 с бега, 10 с ходьбы, 2 повтора: всего 40 с и 4 отрезка
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
        assertEquals("28:00", formatTotal(1_680_000))
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
        assertEquals("329:56:42", formatTotal(WorkoutSettings(99, 59, 99, 59, 99).toWorkout().totalMs))
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
    fun `общая длительность равна сумме всех отрезков`() {
        assertEquals(40_000L, workout.totalMs)
        assertEquals(1_680_000L, WorkoutSettings().toWorkout().totalMs)
    }

    @Test
    fun `прогресс растёт от нуля до единицы`() {
        assertEquals(0f, workout.progressAt(0), 0f)
        assertEquals(0.25f, workout.progressAt(10_000), 0f)
        assertEquals(0.5f, workout.progressAt(20_000), 0f)
        assertEquals(1f, workout.progressAt(40_000), 0f)
    }

    @Test
    fun `прогресс вблизи нуля положительный и мал`() {
        assertEquals(0.000025f, workout.progressAt(1), 1e-7f)
    }

    @Test
    fun `прогресс не выходит за границы`() {
        assertEquals(0f, workout.progressAt(-1), 0f)
        assertEquals(1f, workout.progressAt(40_001), 0f)
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

    private val threeIntervalsAndFinish =
        listOf(Signal.INTERVAL, Signal.INTERVAL, Signal.INTERVAL, Signal.FINISH)

    @Test
    fun `цикл с шагом 250 мс даёт три обычных сигнала и один финальный`() {
        assertEquals(threeIntervalsAndFinish, simulate(workout, 250).mapNotNull { it.signal })
    }

    @Test
    fun `шаги не попадают на границы отрезков, а сигналы те же`() {
        assertEquals(threeIntervalsAndFinish, simulate(workout, 253).mapNotNull { it.signal })
    }

    @Test
    fun `неровные шаги дают те же сигналы`() {
        val ticks = simulate(workout, 137, 251, 499, 73, 1_210)

        assertEquals(threeIntervalsAndFinish, ticks.mapNotNull { it.signal })
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
                progress = 0.3125f,
            ),
            tick.state,
        )
        assertEquals(Signal.INTERVAL, tick.signal)
        assertFalse(tick.finished)
    }

    @Test
    fun `финал показывает последний отрезок с нулевым остатком`() {
        // Предыдущий шаг был в беге: запоздавший финал всё равно показывает последнюю ходьбу
        val tick = tickAt(workout, 45_000, previousIndex = 2)

        assertEquals(
            WorkoutState.Active(
                position = Position(index = 3, isRun = false, round = 2, remainingMs = 0),
                rounds = 2,
                progress = 1f,
            ),
            tick.state,
        )
        assertEquals(Signal.FINISH, tick.signal)
        assertTrue(tick.finished)
    }

    @Test
    fun `первый шаг уже после конца тренировки сразу завершает её`() {
        val tick = tickAt(workout, 50_000, previousIndex = null)

        assertEquals(3, tick.state.position.index)
        assertEquals(Signal.FINISH, tick.signal)
        assertTrue(tick.finished)
    }

    @Test
    fun `признак сбоя звука доходит до состояния`() {
        assertFalse(tickAt(workout, 0, null).state.soundFailed)
        assertTrue(tickAt(workout, 0, null, soundFailed = true).state.soundFailed)
        assertTrue(tickAt(workout, 40_000, 3, soundFailed = true).state.soundFailed)
    }
}
