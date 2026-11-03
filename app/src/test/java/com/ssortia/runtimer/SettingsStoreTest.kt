package com.ssortia.runtimer

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun `неверные сохранённые значения видны проверке`() {
        prefs.values["rounds"] = 0

        assertNotNull(store.load().validate())
    }

    @Test
    fun `задержка старта по умолчанию 5 секунд`() {
        assertEquals(DEFAULT_START_DELAY_SEC, store.loadStartDelaySec())
        assertEquals(5, DEFAULT_START_DELAY_SEC)
    }

    @Test
    fun `сохранённая задержка читается обратно`() {
        store.saveStartDelaySec(20)

        assertEquals(20, store.loadStartDelaySec())
        assertEquals(20, SettingsStore(prefs).loadStartDelaySec())
    }

    @Test
    fun `задержка, сохранённая прежней версией формы, читается под тем же ключом`() {
        prefs.values["start_delay_sec"] = 20

        assertEquals(20, store.loadStartDelaySec())
    }

    @Test
    fun `задержка вне списка приводится к ближайшей при записи и чтении`() {
        store.saveStartDelaySec(8)
        assertEquals(10, prefs.values["start_delay_sec"])

        // Прежние версии позволяли сохранить 7 и 25
        prefs.values["start_delay_sec"] = 7
        assertEquals(5, store.loadStartDelaySec())
        prefs.values["start_delay_sec"] = 25
        assertEquals(20, store.loadStartDelaySec())
    }

    @Test
    fun `задержка 3 секунды сохраняется как есть`() {
        store.saveStartDelaySec(3)

        assertEquals(3, store.loadStartDelaySec())
    }

    @Test
    fun `задержка прижимается к диапазону при записи и чтении`() {
        store.saveStartDelaySec(-5)
        assertEquals(0, prefs.values["start_delay_sec"])

        store.saveStartDelaySec(90)
        assertEquals(60, prefs.values["start_delay_sec"])

        prefs.values["start_delay_sec"] = 61
        assertEquals(60, store.loadStartDelaySec())
    }

    @Test
    fun `задержка не зависит от настроек тренировки`() {
        val settings = WorkoutSettings(runMin = 3, runSec = 15, walkMin = 4, walkSec = 45, rounds = 9)

        store.saveStartDelaySec(30)
        store.save(settings)

        assertEquals(30, store.loadStartDelaySec())
        assertEquals(settings, store.load())
    }

    @Test
    fun `громкость сигналов по умолчанию 100`() {
        assertEquals(100, store.loadSignalVolume())
    }

    @Test
    fun `сохранённая громкость читается обратно`() {
        store.saveSignalVolume(35)

        assertEquals(35, store.loadSignalVolume())
        assertEquals(35, SettingsStore(prefs).loadSignalVolume())
    }

    @Test
    fun `границы громкости сохраняются без изменений`() {
        store.saveSignalVolume(MIN_VOLUME_PERCENT)
        assertEquals(MIN_VOLUME_PERCENT, store.loadSignalVolume())

        store.saveSignalVolume(MAX_VOLUME_PERCENT)
        assertEquals(MAX_VOLUME_PERCENT, store.loadSignalVolume())
    }

    @Test
    fun `громкость не на шаге ползунка читается как есть`() {
        prefs.values["signal_volume"] = 37

        assertEquals(37, store.loadSignalVolume())
    }

    @Test
    fun `громкость прижимается к диапазону при записи`() {
        store.saveSignalVolume(0)
        assertEquals(10, prefs.values["signal_volume"])

        store.saveSignalVolume(150)
        assertEquals(100, prefs.values["signal_volume"])
    }

    @Test
    fun `повреждённая громкость прижимается при чтении`() {
        prefs.values["signal_volume"] = 0
        assertEquals(10, store.loadSignalVolume())

        prefs.values["signal_volume"] = 150
        assertEquals(100, store.loadSignalVolume())
    }

    @Test
    fun `громкость не зависит от настроек тренировки`() {
        val settings = WorkoutSettings(runMin = 3, runSec = 15, walkMin = 4, walkSec = 45, rounds = 9)

        store.saveSignalVolume(40)
        store.save(settings)

        assertEquals(40, store.loadSignalVolume())
        assertEquals(settings, store.load())

        store.saveSignalVolume(60)

        assertEquals(settings, store.load())
    }

    @Test
    fun `вибрация по умолчанию включена`() {
        assertTrue(DEFAULT_VIBRATION)
        assertEquals(DEFAULT_VIBRATION, store.loadVibration())
    }

    @Test
    fun `сохранённая вибрация читается обратно`() {
        store.saveVibration(false)

        assertFalse(store.loadVibration())
        assertFalse(SettingsStore(prefs).loadVibration())

        store.saveVibration(true)

        assertTrue(store.loadVibration())
    }

    @Test
    fun `вибрация не зависит от остальных настроек`() {
        val settings = WorkoutSettings(runMin = 3, runSec = 15, walkMin = 4, walkSec = 45, rounds = 9)

        store.save(settings)
        store.saveSignalVolume(40)
        store.saveDuckAhead(true)
        store.saveStartDelaySec(20)
        store.saveVibration(false)

        // Запись вибрации не меняет остальные настройки
        assertEquals(settings, store.load())
        assertEquals(40, store.loadSignalVolume())
        assertTrue(store.loadDuckAhead())
        assertEquals(20, store.loadStartDelaySec())

        // Запись остальных настроек не меняет вибрацию
        store.save(WorkoutSettings())
        store.saveSignalVolume(60)
        store.saveDuckAhead(true)
        store.saveStartDelaySec(10)

        assertFalse(store.loadVibration())
    }

    @Test
    fun `приглушение заранее по умолчанию включено`() {
        assertTrue(DEFAULT_DUCK_AHEAD)
        assertEquals(DEFAULT_DUCK_AHEAD, store.loadDuckAhead())
    }

    @Test
    fun `сохранённое приглушение заранее читается обратно`() {
        store.saveDuckAhead(false)

        assertFalse(store.loadDuckAhead())
        assertFalse(SettingsStore(prefs).loadDuckAhead())

        store.saveDuckAhead(true)

        assertTrue(store.loadDuckAhead())
        assertTrue(SettingsStore(prefs).loadDuckAhead())
    }

    @Test
    fun `приглушение заранее не зависит от остальных настроек`() {
        val settings = WorkoutSettings(runMin = 3, runSec = 15, walkMin = 4, walkSec = 45, rounds = 9)

        store.save(settings)
        store.saveSignalVolume(40)
        store.saveVibration(false)
        store.saveStartDelaySec(20)
        store.saveDuckAhead(false)

        // Запись приглушения не меняет остальные настройки
        assertEquals(settings, store.load())
        assertEquals(40, store.loadSignalVolume())
        assertFalse(store.loadVibration())
        assertEquals(20, store.loadStartDelaySec())

        // Запись остальных настроек не меняет приглушение
        store.save(WorkoutSettings())
        store.saveSignalVolume(60)
        // Значение, противоположное приглушению: иначе запись не в тот ключ не была бы видна
        store.saveVibration(true)
        store.saveStartDelaySec(10)

        assertFalse(store.loadDuckAhead())
    }

    /** Хранит значения в памяти, поддерживает только то, что нужно настройкам. */
    private class FakePreferences : SharedPreferences {
        val values = mutableMapOf<String, Any>()

        override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun contains(key: String): Boolean = key in values
        override fun getAll(): Map<String, *> = values
        override fun getString(key: String, defValue: String?): String? = unsupported()
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = unsupported()
        override fun getLong(key: String, defValue: Long): Long = unsupported()
        override fun getFloat(key: String, defValue: Float): Float = unsupported()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ): Unit = unsupported()

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ): Unit = unsupported()

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any>()

            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

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
            override fun putStringSet(
                key: String,
                values: Set<String>?,
            ): SharedPreferences.Editor = unsupported()
        }

        private fun unsupported(): Nothing = throw UnsupportedOperationException()
    }
}
