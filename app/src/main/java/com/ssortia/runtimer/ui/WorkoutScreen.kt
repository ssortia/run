package com.ssortia.runtimer.ui

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.WorkoutState

@Composable
fun WorkoutScreen(
    state: WorkoutState,
    loadSettings: () -> WorkoutSettings,
    onStart: (WorkoutSettings) -> Unit,
    onStop: () -> Unit,
) {
    when (state) {
        WorkoutState.Idle -> {
            // remember внутри ветки: при каждом возврате в Idle настройки читаются заново
            val initial = remember { loadSettings() }
            SettingsContent(
                initial = initial,
                onStart = onStart,
                // safeDrawing включает клавиатуру, поэтому отдельный imePadding() не нужен
                modifier = Modifier.safeDrawingPadding(),
            )
        }

        is WorkoutState.Countdown -> CountdownContent(state = state, onStop = onStop)

        is WorkoutState.Active -> ActiveContent(state = state, onStop = onStop)
    }
}
