package com.ssortia.runtimer

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettingsStoreTest {
    private val prefs = FakePreferences()
    private val store = SettingsStore(prefs)

    @Test
    fun `при первом запуске настройки равны значениям по умолчанию`() {
        assertEquals(WorkoutSettings(2, 0, 2, 0, 7), store.load())
    }

    @Test
    fun `сохранённые настройки читаются без перестановки полей`() {
        // Все значения разные: перепутанный ключ сразу виден
        val settings = WorkoutSettings(
            runMin = 3,
            runSec = 15,
            walkMin = 4,
            walkSec = 45,
            rounds = 9,
            startDelaySec = 20,
        )

        store.save(settings)

        assertEquals(settings, store.load())
    }

    @Test
    fun `новый экземпляр читает то же хранилище`() {
        val settings = WorkoutSettings(runMin = 1, runSec = 2, walkMin = 3, walkSec = 4, rounds = 5)

        store.save(settings)

        assertEquals(settings, SettingsStore(prefs).load())
    }

    @Test
    fun `отсутствующий ключ заменяется значением по умолчанию`() {
        prefs.values["run_min"] = 5

        assertEquals(WorkoutSettings(runMin = 5), store.load())
    }

    @Test
    fun `без сохранённой задержки возвращается 5 секунд`() {
        prefs.values["rounds"] = 9

        assertEquals(5, store.load().startDelaySec)
    }

    @Test
    fun `неверные сохранённые значения видны проверке`() {
        prefs.values["rounds"] = 0

        assertNotNull(store.load().validate())
    }

    @Test
    fun `неверная сохранённая задержка видна проверке`() {
        prefs.values["start_delay_sec"] = 61

        assertEquals(61, store.load().startDelaySec)
        assertEquals(SettingsError.START_DELAY_RANGE, store.load().validate())
    }

    /** Хранит значения в памяти, поддерживает только то, что нужно настройкам. */
    private class FakePreferences : SharedPreferences {
        val values = mutableMapOf<String, Int>()

        override fun getInt(key: String, defValue: Int): Int = values[key] ?: defValue

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun contains(key: String): Boolean = key in values
        override fun getAll(): Map<String, *> = values
        override fun getString(key: String, defValue: String?): String? = unsupported()
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = unsupported()
        override fun getLong(key: String, defValue: Long): Long = unsupported()
        override fun getFloat(key: String, defValue: Float): Float = unsupported()
        override fun getBoolean(key: String, defValue: Boolean): Boolean = unsupported()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ): Unit = unsupported()

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ): Unit = unsupported()

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Int>()

            override fun putInt(key: String, value: Int) = apply { pending[key] = value }

            override fun apply() {
                values += pending
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun clear(): SharedPreferences.Editor = unsupported()
            override fun remove(key: String): SharedPreferences.Editor = unsupported()
            override fun putString(key: String, value: String?): SharedPreferences.Editor = unsupported()
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = unsupported()
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = unsupported()
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = unsupported()
            override fun putStringSet(
                key: String,
                values: Set<String>?,
            ): SharedPreferences.Editor = unsupported()
        }

        private fun unsupported(): Nothing = throw UnsupportedOperationException()
    }
}
