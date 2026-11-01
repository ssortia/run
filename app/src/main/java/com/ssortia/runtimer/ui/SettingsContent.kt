package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.R
import com.ssortia.runtimer.SettingsError
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.formatTime

/** Текст полей формы. Хранится строками, потому что поле может быть пустым. */
data class SettingsInput(
    val runMin: String,
    val runSec: String,
    val walkMin: String,
    val walkSec: String,
    val rounds: String,
) {
    fun toSettings() = WorkoutSettings(
        runMin = parseField(runMin),
        runSec = parseField(runSec),
        walkMin = parseField(walkMin),
        walkSec = parseField(walkSec),
        rounds = parseField(rounds),
    )

    companion object {
        fun from(settings: WorkoutSettings) = SettingsInput(
            runMin = settings.runMin.toString(),
            runSec = settings.runSec.toString(),
            walkMin = settings.walkMin.toString(),
            walkSec = settings.walkSec.toString(),
            rounds = settings.rounds.toString(),
        )
    }
}

/** Оставляет только цифры ASCII: так вставка из буфера не занесёт в поле минус или пробел. */
fun digitsOnly(text: String): String = text.filter { it in '0'..'9' }

/**
 * Пустое поле считается нулём. Число, не вмещающееся в Int, заменяется на [Int.MAX_VALUE]:
 * пользователь получит ошибку диапазона из [WorkoutSettings.validate], а не ноль.
 */
fun parseField(text: String): Int {
    val digits = digitsOnly(text)
    return if (digits.isEmpty()) 0 else digits.toIntOrNull() ?: Int.MAX_VALUE
}

@StringRes
fun SettingsError.messageRes(): Int = when (this) {
    SettingsError.RUN_ZERO -> R.string.error_run_zero
    SettingsError.WALK_ZERO -> R.string.error_walk_zero
    SettingsError.ROUNDS_RANGE -> R.string.error_rounds_range
    SettingsError.SECONDS_RANGE -> R.string.error_seconds_range
    SettingsError.MINUTES_RANGE -> R.string.error_minutes_range
}

private val SettingsInputSaver = listSaver<SettingsInput, String>(
    save = { listOf(it.runMin, it.runSec, it.walkMin, it.walkSec, it.rounds) },
    restore = { SettingsInput(it[0], it[1], it[2], it[3], it[4]) },
)

/** Форма настроек. [onStart] вызывается только с настройками, прошедшими проверку. */
@Composable
fun SettingsContent(
    initial: WorkoutSettings,
    onStart: (WorkoutSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable(initial, stateSaver = SettingsInputSaver) {
        mutableStateOf(SettingsInput.from(initial))
    }
    val settings = input.toSettings()
    val error = settings.validate()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SegmentFields(
            label = stringResource(R.string.segment_run),
            isRun = true,
            minutes = input.runMin,
            seconds = input.runSec,
            onMinutesChange = { input = input.copy(runMin = it) },
            onSecondsChange = { input = input.copy(runSec = it) },
        )
        SegmentFields(
            label = stringResource(R.string.segment_walk),
            isRun = false,
            minutes = input.walkMin,
            seconds = input.walkSec,
            onMinutesChange = { input = input.copy(walkMin = it) },
            onSecondsChange = { input = input.copy(walkSec = it) },
        )
        NumberField(
            label = stringResource(R.string.field_rounds),
            value = input.rounds,
            onValueChange = { input = input.copy(rounds = it) },
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )

        // Ошибка и итог занимают одно место: итог неверных настроек не имеет смысла
        if (error != null) {
            Text(
                text = stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.total_duration,
                    formatTime(settings.toWorkout().totalMs),
                ),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Button(
            onClick = { onStart(settings) },
            enabled = error == null,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                text = stringResource(R.string.button_start),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun SegmentFields(
    label: String,
    isRun: Boolean,
    minutes: String,
    seconds: String,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(
                        color = segmentColors(isRun, isSystemInDarkTheme()).background,
                        shape = CircleShape,
                    ),
            )
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = stringResource(R.string.field_minutes),
                value = minutes,
                onValueChange = onMinutesChange,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = stringResource(R.string.field_seconds),
                value = seconds,
                onValueChange = onSecondsChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(digitsOnly(it)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction,
        ),
        modifier = modifier,
    )
}
