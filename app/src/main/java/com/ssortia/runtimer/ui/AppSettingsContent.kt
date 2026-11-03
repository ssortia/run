package com.ssortia.runtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
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

/**
 * Экран настроек приложения. Значения сохраняются сразу: переключатель при переключении,
 * ползунки при отпускании, а не на каждом сдвиге. Поэтому состояние не saveable: прерванный
 * поворотом сдвиг показывал бы несохранённое значение, а после пересоздания экран читает
 * хранилище заново. При отпускании ползунка громкости звучит пробный сигнал. Тему, наоборот,
 * держит activity: она применяется ко всему интерфейсу сразу, поэтому экран получает значение.
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var volume by remember { mutableFloatStateOf(loadVolume().toFloat()) }
    var vibration by remember { mutableStateOf(loadVibration()) }
    var duckMusic by remember { mutableStateOf(loadDuckMusic()) }
    var startDelay by remember { mutableFloatStateOf(startDelayToSlider(loadStartDelay())) }

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.button_settings), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.group_signals))
            SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                SliderSettingRow(
                    label = stringResource(R.string.setting_volume),
                    value = stringResource(R.string.value_percent, sliderToPercent(volume)),
                    sliderValue = volume,
                    onSliderValueChange = { volume = it },
                    valueRange = MIN_VOLUME_PERCENT.toFloat()..MAX_VOLUME_PERCENT.toFloat(),
                    steps = VOLUME_SLIDER_STEPS,
                    onValueChangeFinished = { onVolumeChange(sliderToPercent(volume)) },
                )
                SwitchSettingRow(
                    label = stringResource(R.string.setting_vibration),
                    checked = vibration,
                    onCheckedChange = {
                        vibration = it
                        onVibrationChange(it)
                    },
                )
                SwitchSettingRow(
                    label = stringResource(R.string.setting_duck_music),
                    checked = duckMusic,
                    onCheckedChange = {
                        duckMusic = it
                        onDuckMusicChange(it)
                    },
                )
            }

            SectionTitle(
                text = stringResource(R.string.group_workout),
                modifier = Modifier.padding(top = 16.dp),
            )
            SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
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

            SectionTitle(
                text = stringResource(R.string.group_appearance),
                modifier = Modifier.padding(top = 16.dp),
            )
            SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.selectableGroup()) {
                    Text(
                        text = stringResource(R.string.setting_theme),
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
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
) {
    val description = stringResource(R.string.setting_value_description, label, value)
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}

@Composable
private fun SwitchSettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Строка целиком переключает настройку, а сам Switch без обработчика
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
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
            .padding(vertical = 8.dp),
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
