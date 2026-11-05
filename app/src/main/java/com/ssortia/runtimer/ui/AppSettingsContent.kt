package com.ssortia.runtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.MAX_VOLUME_PERCENT
import com.ssortia.runtimer.MIN_VOLUME_PERCENT
import com.ssortia.runtimer.R
import com.ssortia.runtimer.START_DELAY_OPTIONS_SEC
import com.ssortia.runtimer.START_DELAY_SLIDER_STEPS
import com.ssortia.runtimer.ThemeMode
import com.ssortia.runtimer.VOLUME_SLIDER_STEPS
import com.ssortia.runtimer.sliderToPercent
import com.ssortia.runtimer.sliderToStartDelay
import com.ssortia.runtimer.startDelayToSlider
import com.ssortia.runtimer.volumeHint

/**
 * Экран настроек приложения. Значения сохраняются сразу: переключатель при переключении,
 * ползунки при отпускании, а не на каждом сдвиге. Поэтому состояние не saveable: прерванный
 * поворотом сдвиг показывал бы несохранённое значение, а после пересоздания экран читает
 * хранилище заново. При отпускании ползунка громкости звучит пробный сигнал. Тему, наоборот,
 * держит activity: она применяется ко всему интерфейсу сразу, поэтому экран получает значение.
 * Во время тренировки ([workoutRunning]) сервис уже прочитал сигналы и задержку старта, поэтому
 * под этими группами подписано, что изменения применятся к следующей тренировке; пробного сигнала
 * тогда нет. Тема применяется сразу, и подписи под ней нет.
 */
@Composable
fun AppSettingsContent(
    loadVolume: () -> Int,
    onVolumeChange: (Int) -> Unit,
    loadVibration: () -> Boolean,
    onVibrationChange: (Boolean) -> Unit,
    loadDuckMusic: () -> Boolean,
    onDuckMusicChange: (Boolean) -> Unit,
    loadStartDelay: () -> Int,
    onStartDelayChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    workoutRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    var volume by remember { mutableFloatStateOf(loadVolume().toFloat()) }
    var vibration by remember { mutableStateOf(loadVibration()) }
    var duckMusic by remember { mutableStateOf(loadDuckMusic()) }
    var startDelay by remember { mutableFloatStateOf(startDelayToSlider(loadStartDelay())) }

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.tab_settings))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = ScrollContentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.group_signals))
            SectionCard {
                SliderSettingRow(
                    label = stringResource(R.string.setting_volume),
                    value = stringResource(R.string.value_percent, sliderToPercent(volume)),
                    sliderValue = volume,
                    onSliderValueChange = { volume = it },
                    valueRange = MIN_VOLUME_PERCENT.toFloat()..MAX_VOLUME_PERCENT.toFloat(),
                    steps = VOLUME_SLIDER_STEPS,
                    onValueChangeFinished = { onVolumeChange(sliderToPercent(volume)) },
                    hint = volumeHint(
                        volumePercent = sliderToPercent(volume),
                        vibration = vibration,
                        vibrationOnly = stringResource(R.string.volume_hint_vibration_only),
                        noSignals = stringResource(R.string.volume_hint_no_signals),
                    ),
                )
                RowDivider()
                SwitchSettingRow(
                    label = stringResource(R.string.setting_vibration),
                    checked = vibration,
                    onCheckedChange = {
                        vibration = it
                        onVibrationChange(it)
                    },
                )
                RowDivider()
                SwitchSettingRow(
                    label = stringResource(R.string.setting_duck_music),
                    checked = duckMusic,
                    onCheckedChange = {
                        duckMusic = it
                        onDuckMusicChange(it)
                    },
                )
            }
            if (workoutRunning) NextWorkoutHint()

            SectionTitle(
                text = stringResource(R.string.group_workout),
                modifier = Modifier.padding(top = SectionGroupGap),
            )
            SectionCard {
                SliderSettingRow(
                    label = stringResource(R.string.setting_start_delay),
                    value = stringResource(R.string.value_seconds, sliderToStartDelay(startDelay)),
                    sliderValue = startDelay,
                    onSliderValueChange = { startDelay = it },
                    // Ползунок ходит по индексам списка: шаг между значениями неравномерный
                    valueRange = 0f..START_DELAY_OPTIONS_SEC.lastIndex.toFloat(),
                    steps = START_DELAY_SLIDER_STEPS,
                    onValueChangeFinished = { onStartDelayChange(sliderToStartDelay(startDelay)) },
                )
            }
            if (workoutRunning) NextWorkoutHint()

            SectionTitle(
                text = stringResource(R.string.group_appearance),
                modifier = Modifier.padding(top = SectionGroupGap),
            )
            SectionCard {
                // Подписи «Тема» нет, группу называет заголовок «Оформление», а TalkBack описание группы
                val themeLabel = stringResource(R.string.setting_theme)
                Column(
                    Modifier
                        .selectableGroup()
                        .semantics { contentDescription = themeLabel },
                ) {
                    for (mode in ThemeMode.entries) {
                        RadioSettingRow(
                            label = stringResource(mode.labelRes()),
                            selected = mode == themeMode,
                            onClick = { if (mode != themeMode) onThemeModeChange(mode) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ползунок с подписью слева и значением справа. Подпись и значение отдельные узлы, поэтому
 * описание ползунка содержит оба: иначе TalkBack назовёт ползунок безымянным или без значения.
 */
@Composable
private fun SliderSettingRow(
    label: String,
    value: String,
    sliderValue: Float,
    onSliderValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: () -> Unit,
    hint: String? = null,
) {
    val description = stringResource(R.string.setting_value_description, label, value)
    Column(modifier = Modifier.padding(top = RowVerticalPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onSliderValueChange,
            modifier = Modifier.semantics { contentDescription = description },
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
        // Не красная и не блокирует старт: ноль выбран намеренно, подсказка лишь напоминает о нём
        if (hint != null) {
            Text(
                text = hint,
                modifier = Modifier.padding(bottom = RowVerticalPadding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Подпись под группой: изменение не затронет идущую тренировку. Значок декоративный. */
@Composable
private fun NextWorkoutHint() {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.hint_next_workout),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Разделитель строк внутри карточки. */
@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// Отступ строк карточки: строка с переключателем 56 dp, как в макете с разделителями
private val RowVerticalPadding = 12.dp
private val RadioRowMinHeight = 48.dp

@Composable
private fun SwitchSettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Строка целиком переключает настройку, а сам Switch без обработчика
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun RadioSettingRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Строка целиком выбирает вариант, а сама RadioButton без обработчика
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = RadioRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
