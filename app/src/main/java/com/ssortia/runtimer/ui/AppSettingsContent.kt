package com.ssortia.runtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ssortia.runtimer.MAX_START_DELAY_SEC
import com.ssortia.runtimer.MAX_VOLUME_PERCENT
import com.ssortia.runtimer.MIN_START_DELAY_SEC
import com.ssortia.runtimer.MIN_VOLUME_PERCENT
import com.ssortia.runtimer.R
import com.ssortia.runtimer.START_DELAY_SLIDER_STEPS
import com.ssortia.runtimer.VOLUME_SLIDER_STEPS
import com.ssortia.runtimer.sliderToPercent
import com.ssortia.runtimer.sliderToStartDelay

/**
 * Экран настроек приложения. Значения сохраняются сразу: переключатель при переключении,
 * ползунки при отпускании, а не на каждом сдвиге. Поэтому состояние не saveable: прерванный
 * поворотом сдвиг показывал бы несохранённое значение, а после пересоздания экран читает
 * хранилище заново. При отпускании ползунка громкости звучит пробный сигнал.
 */
@Composable
fun AppSettingsContent(
    loadVolume: () -> Int,
    onVolumeChange: (Int) -> Unit,
    loadVibration: () -> Boolean,
    onVibrationChange: (Boolean) -> Unit,
    loadStartDelay: () -> Int,
    onStartDelayChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var volume by remember { mutableFloatStateOf(loadVolume().toFloat()) }
    var vibration by remember { mutableStateOf(loadVibration()) }
    var startDelay by remember { mutableFloatStateOf(loadStartDelay().toFloat()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Строка целиком переключает настройку, а сам Switch без обработчика
                .toggleable(
                    value = vibration,
                    role = Role.Switch,
                    onValueChange = {
                        vibration = it
                        onVibrationChange(it)
                    },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.setting_vibration),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = vibration, onCheckedChange = null)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val delayLabel = stringResource(R.string.setting_start_delay, sliderToStartDelay(startDelay))
            Text(text = delayLabel, style = MaterialTheme.typography.titleMedium)
            Slider(
                value = startDelay,
                onValueChange = { startDelay = it },
                // Подпись над ползунком отдельный узел, без описания TalkBack назовёт ползунок безымянным
                modifier = Modifier.semantics { contentDescription = delayLabel },
                valueRange = MIN_START_DELAY_SEC.toFloat()..MAX_START_DELAY_SEC.toFloat(),
                steps = START_DELAY_SLIDER_STEPS,
                onValueChangeFinished = { onStartDelayChange(sliderToStartDelay(startDelay)) },
            )
        }

        TextButton(onClick = onBack) {
            Text(
                text = stringResource(R.string.button_back),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
