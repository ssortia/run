package com.ssortia.runtimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.WorkoutState

@Composable
fun WorkoutScreen(
    state: WorkoutState,
    loadSettings: () -> WorkoutSettings,
    loadVolume: () -> Int,
    loadVibration: () -> Boolean,
    loadStartDelay: () -> Int,
    onStart: (WorkoutSettings) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onStartDelayChange: (Int) -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    when (state) {
        WorkoutState.Idle -> {
            // remember внутри ветки: при каждом возврате в Idle настройки читаются заново
            val initial = remember { loadSettings() }
            // Переход без библиотеки навигации: флаг переживает поворот, но не тренировку
            var showAppSettings by rememberSaveable { mutableStateOf(false) }
            // Без держателя rememberSaveable формы теряет несохранённые правки, пока открыт
            // экран настроек. Создаётся в ветке Idle: после тренировки форма читается заново
            val formState = rememberSaveableStateHolder()

            if (showAppSettings) {
                BackHandler { showAppSettings = false }
                AppSettingsContent(
                    loadVolume = loadVolume,
                    onVolumeChange = onVolumeChange,
                    loadVibration = loadVibration,
                    onVibrationChange = onVibrationChange,
                    loadStartDelay = loadStartDelay,
                    onStartDelayChange = onStartDelayChange,
                    onBack = { showAppSettings = false },
                    modifier = Modifier.safeDrawingPadding(),
                )
            } else {
                formState.SaveableStateProvider("form") {
                    SettingsContent(
                        initial = initial,
                        onStart = onStart,
                        onOpenSettings = { showAppSettings = true },
                        // safeDrawing включает клавиатуру, поэтому отдельный imePadding() не нужен
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }

        is WorkoutState.Countdown -> CountdownContent(state = state, onStop = onStop)

        is WorkoutState.Active -> ActiveContent(
            state = state,
            onStop = onStop,
            onPause = onPause,
            onResume = onResume,
        )
    }
}
