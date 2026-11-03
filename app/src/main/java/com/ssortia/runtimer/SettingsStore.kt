package com.ssortia.runtimer

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    /** Возвращает сохранённое как есть: проверка значений остаётся за [WorkoutSettings.validate]. */
    fun load(): WorkoutSettings {
        val defaults = WorkoutSettings()
        return WorkoutSettings(
            runMin = prefs.getInt(KEY_RUN_MIN, defaults.runMin),
            runSec = prefs.getInt(KEY_RUN_SEC, defaults.runSec),
            walkMin = prefs.getInt(KEY_WALK_MIN, defaults.walkMin),
            walkSec = prefs.getInt(KEY_WALK_SEC, defaults.walkSec),
            rounds = prefs.getInt(KEY_ROUNDS, defaults.rounds),
        )
    }

    fun save(settings: WorkoutSettings) {
        prefs.edit()
            .putInt(KEY_RUN_MIN, settings.runMin)
            .putInt(KEY_RUN_SEC, settings.runSec)
            .putInt(KEY_WALK_MIN, settings.walkMin)
            .putInt(KEY_WALK_SEC, settings.walkSec)
            .putInt(KEY_ROUNDS, settings.rounds)
            .apply()
    }

    /**
     * Громкость сигналов хранится отдельно от тренировки. Значение вне диапазона прижимается к нему,
     * значение внутри диапазона, но не на шаге ползунка, возвращается как есть.
     */
    fun loadSignalVolume(): Int = clampVolume(prefs.getInt(KEY_SIGNAL_VOLUME, DEFAULT_VOLUME_PERCENT))

    fun saveSignalVolume(percent: Int) {
        prefs.edit()
            .putInt(KEY_SIGNAL_VOLUME, clampVolume(percent))
            .apply()
    }

    /**
     * Задержка старта хранится отдельно от тренировки под прежним ключом, поэтому сохранённое
     * раньше значение сохраняется. Значение приводится к ближайшему допустимому, см. `snapStartDelay`.
     */
    fun loadStartDelaySec(): Int =
        snapStartDelay(prefs.getInt(KEY_START_DELAY_SEC, DEFAULT_START_DELAY_SEC))

    fun saveStartDelaySec(sec: Int) {
        prefs.edit()
            .putInt(KEY_START_DELAY_SEC, snapStartDelay(sec))
            .apply()
    }

    /** Вибрация хранится отдельно от тренировки и по умолчанию включена. */
    fun loadVibration(): Boolean = prefs.getBoolean(KEY_VIBRATION, DEFAULT_VIBRATION)

    fun saveVibration(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_VIBRATION, enabled)
            .apply()
    }

    /** Приглушение музыки заранее хранится отдельно от тренировки и по умолчанию включено. */
    fun loadDuckAhead(): Boolean = prefs.getBoolean(KEY_DUCK_AHEAD, DEFAULT_DUCK_AHEAD)

    fun saveDuckAhead(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DUCK_AHEAD, enabled)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "workout_settings"
        const val KEY_RUN_MIN = "run_min"
        const val KEY_RUN_SEC = "run_sec"
        const val KEY_WALK_MIN = "walk_min"
        const val KEY_WALK_SEC = "walk_sec"
        const val KEY_ROUNDS = "rounds"
        const val KEY_START_DELAY_SEC = "start_delay_sec"
        const val KEY_SIGNAL_VOLUME = "signal_volume"
        const val KEY_VIBRATION = "vibration"
        const val KEY_DUCK_AHEAD = "duck_ahead"
    }
}
