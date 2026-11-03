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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.R

/**
 * Экран настроек приложения. Значение сохраняется сразу при переключении, поэтому
 * состояние не saveable: после пересоздания переключатель читает хранилище заново.
 */
@Composable
fun AppSettingsContent(
    loadVibration: () -> Boolean,
    onVibrationChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var vibration by remember { mutableStateOf(loadVibration()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
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

        TextButton(onClick = onBack) {
            Text(
                text = stringResource(R.string.button_back),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
