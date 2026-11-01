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

    private companion object {
        const val PREFS_NAME = "workout_settings"
        const val KEY_RUN_MIN = "run_min"
        const val KEY_RUN_SEC = "run_sec"
        const val KEY_WALK_MIN = "walk_min"
        const val KEY_WALK_SEC = "walk_sec"
        const val KEY_ROUNDS = "rounds"
    }
}
