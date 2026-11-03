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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.MAX_MINUTES
import com.ssortia.runtimer.MAX_ROUNDS
import com.ssortia.runtimer.MAX_SECONDS
import com.ssortia.runtimer.MAX_START_DELAY_SEC
import com.ssortia.runtimer.MAX_VOLUME_PERCENT
import com.ssortia.runtimer.MIN_VOLUME_PERCENT
import com.ssortia.runtimer.R
import com.ssortia.runtimer.SettingsError
import com.ssortia.runtimer.VOLUME_SLIDER_STEPS
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.formatTotal
import com.ssortia.runtimer.sliderToPercent

/** Текст полей формы. Хранится строками, потому что поле может быть пустым. */
data class SettingsInput(
    val runMin: String,
    val runSec: String,
    val walkMin: String,
    val walkSec: String,
    val rounds: String,
    val startDelaySec: String,
) {
    fun toSettings() = WorkoutSettings(
        runMin = parseField(runMin),
        runSec = parseField(runSec),
        walkMin = parseField(walkMin),
        walkSec = parseField(walkSec),
        rounds = parseField(rounds),
        startDelaySec = parseField(startDelaySec),
    )

    companion object {
        fun from(settings: WorkoutSettings) = SettingsInput(
            runMin = settings.runMin.toString(),
            runSec = settings.runSec.toString(),
            walkMin = settings.walkMin.toString(),
            walkSec = settings.walkSec.toString(),
            rounds = settings.rounds.toString(),
            startDelaySec = settings.startDelaySec.toString(),
        )
    }
}

/** Оставляет только цифры ASCII: так вставка из буфера не занесёт в поле минус или пробел. */
fun digitsOnly(text: String): String = text.filter { it in '0'..'9' }

/**
 * Текст приходит из поля, уже прошедшего [digitsOnly]. Пустое поле считается нулём. Число,
 * не вмещающееся в Int, заменяется на [Int.MAX_VALUE]: пользователь получит ошибку диапазона
 * из [WorkoutSettings.validate], а не ноль.
 */
fun parseField(text: String): Int =
    if (text.isEmpty()) 0 else text.toIntOrNull() ?: Int.MAX_VALUE

@StringRes
fun SettingsError.messageRes(): Int = when (this) {
    SettingsError.RUN_ZERO -> R.string.error_run_zero
    SettingsError.WALK_ZERO -> R.string.error_walk_zero
    SettingsError.ROUNDS_RANGE -> R.string.error_rounds_range
    SettingsError.SECONDS_RANGE -> R.string.error_seconds_range
    SettingsError.MINUTES_RANGE -> R.string.error_minutes_range
    SettingsError.START_DELAY_RANGE -> R.string.error_start_delay_range
}

/** Аргументы строки из [messageRes]: пределы подставляются из тех же констант, что и в проверке. */
fun SettingsError.messageArgs(): Array<Any> = when (this) {
    SettingsError.RUN_ZERO, SettingsError.WALK_ZERO -> emptyArray()
    SettingsError.ROUNDS_RANGE -> arrayOf(MAX_ROUNDS)
    SettingsError.SECONDS_RANGE -> arrayOf(MAX_SECONDS)
    SettingsError.MINUTES_RANGE -> arrayOf(MAX_MINUTES)
    SettingsError.START_DELAY_RANGE -> arrayOf(MAX_START_DELAY_SEC)
}

internal val SettingsInputSaver = listSaver<SettingsInput, String>(
    save = { listOf(it.runMin, it.runSec, it.walkMin, it.walkSec, it.rounds, it.startDelaySec) },
    restore = { SettingsInput(it[0], it[1], it[2], it[3], it[4], it[5]) },
)

/**
 * Форма настроек. [onStart] вызывается только с настройками, прошедшими проверку.
 * [onVolumeChange] вызывается при отпускании ползунка, а не на каждом его сдвиге.
 */
@Composable
fun SettingsContent(
    initial: WorkoutSettings,
    initialVolume: Int,
    onStart: (WorkoutSettings) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable(stateSaver = SettingsInputSaver) {
        mutableStateOf(SettingsInput.from(initial))
    }
    // Не rememberSaveable: значение сохраняется только при отпускании, и прерванный поворотом сдвиг
    // показывал бы несохранённую громкость. После пересоздания ползунок читает хранилище заново
    var volume by remember { mutableFloatStateOf(initialVolume.toFloat()) }
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
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            label = stringResource(R.string.field_start_delay),
            value = input.startDelaySec,
            onValueChange = { input = input.copy(startDelaySec = it) },
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val volumeLabel = stringResource(R.string.signal_volume, sliderToPercent(volume))
            Text(text = volumeLabel, style = MaterialTheme.typography.titleMedium)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                // Подпись над ползунком отдельный узел, без описания TalkBack назовёт ползунок безымянным
                modifier = Modifier.semantics { contentDescription = volumeLabel },
                valueRange = MIN_VOLUME_PERCENT.toFloat()..MAX_VOLUME_PERCENT.toFloat(),
                steps = VOLUME_SLIDER_STEPS,
                onValueChangeFinished = { onVolumeChange(sliderToPercent(volume)) },
            )
        }

        // Ошибка и итог занимают одно место: итог неверных настроек не имеет смысла
        if (error != null) {
            Text(
                text = stringResource(error.messageRes(), *error.messageArgs()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.total_duration,
                    formatTotal(settings.toWorkout().totalMs),
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

        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.button_settings),
                style = MaterialTheme.typography.titleMedium,
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
