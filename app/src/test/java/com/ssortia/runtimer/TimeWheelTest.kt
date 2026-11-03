package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeWheelTest {
    @Test
    fun `минуты от 0 до 99 с шагом 1`() {
        assertEquals((0..99).toList(), wheelOptions(MAX_MINUTES, 1, 3))
    }

    @Test
    fun `секунды от 0 до 55 с шагом 5`() {
        assertEquals(
            listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55),
            wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, 30),
        )
    }

    @Test
    fun `значение не на шаге вставляется на своё место`() {
        val options = wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, 7)
        assertEquals(listOf(0, 5, 7, 10, 15), options.take(5))
        assertEquals(13, options.size)
    }

    @Test
    fun `допустимое значение после последнего шага вставляется в конец`() {
        assertEquals(listOf(50, 55, 59), wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, 59).takeLast(3))
    }

    @Test
    fun `значение на шаге не дублируется`() {
        assertEquals(12, wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, 0).size)
        assertEquals(12, wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, 55).size)
    }

    @Test
    fun `значение больше max прижимается`() {
        assertEquals((0..99).toList(), wheelOptions(MAX_MINUTES, 1, 150))
        assertEquals(listOf(55, 59), wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, 70).takeLast(2))
    }

    @Test
    fun `отрицательное значение прижимается к нулю`() {
        assertEquals(12, wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, -3).size)
    }

    @Test
    fun `индекс значения в списке`() {
        val options = wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, 30)
        assertEquals(6, wheelIndexOf(options, 30))
    }

    @Test
    fun `индекс вставленного значения`() {
        val options = wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, 7)
        assertEquals(2, wheelIndexOf(options, 7))
        assertEquals(3, wheelIndexOf(options, 10))
    }

    @Test
    fun `крайние значения`() {
        val options = wheelOptions(MAX_MINUTES, 1, 0)
        assertEquals(0, wheelIndexOf(options, 0))
        assertEquals(99, wheelIndexOf(options, 99))
        assertEquals(99, wheelIndexOf(options, 150))
        assertEquals(0, wheelIndexOf(options, -1))
    }
}
