package com.ssortia.runtimer

const val MAX_MINUTES = 99
const val MAX_SECONDS = 59
const val MAX_ROUNDS = 99

/** Настройки в том виде, в каком их вводит пользователь. */
data class WorkoutSettings(
    val runMin: Int = 2,
    val runSec: Int = 0,
    val walkMin: Int = 2,
    val walkSec: Int = 0,
    val rounds: Int = 7,
) {
    private val runTotalSec get() = runMin * 60 + runSec
    private val walkTotalSec get() = walkMin * 60 + walkSec

    /**
     * Возвращает первую ошибку или null, если всё верно.
     * Диапазоны проверяются раньше нуля, потому что сумма минут и секунд имеет смысл
     * только для значений в допустимых пределах.
     */
    fun validate(): SettingsError? = when {
        runMin !in 0..MAX_MINUTES || walkMin !in 0..MAX_MINUTES -> SettingsError.MINUTES_RANGE
        runSec !in 0..MAX_SECONDS || walkSec !in 0..MAX_SECONDS -> SettingsError.SECONDS_RANGE
        rounds !in 1..MAX_ROUNDS -> SettingsError.ROUNDS_RANGE
        runTotalSec == 0 -> SettingsError.RUN_ZERO
        walkTotalSec == 0 && rounds > 1 -> SettingsError.WALK_ZERO
        else -> null
    }

    /** Вызывается только после успешной [validate]. */
    fun toWorkout(): Workout = Workout(runTotalSec, walkTotalSec, rounds)
}

enum class SettingsError {
    RUN_ZERO,
    WALK_ZERO,
    ROUNDS_RANGE,
    SECONDS_RANGE,
    MINUTES_RANGE,
}
