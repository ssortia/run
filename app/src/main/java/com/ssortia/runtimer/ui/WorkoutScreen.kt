package com.ssortia.runtimer.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ssortia.runtimer.RunRecord
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.WorkoutState
import kotlinx.coroutines.flow.Flow

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_APP_SETTINGS = "app_settings"
private const val ROUTE_HISTORY = "history"

@Composable
fun WorkoutScreen(
    state: WorkoutState,
    loadSettings: () -> WorkoutSettings,
    loadVolume: () -> Int,
    loadVibration: () -> Boolean,
    loadDuckAhead: () -> Boolean,
    loadStartDelay: () -> Int,
    runs: Flow<List<RunRecord>>,
    onStart: (WorkoutSettings) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onDuckAheadChange: (Boolean) -> Unit,
    onStartDelayChange: (Int) -> Unit,
    onDeleteRun: (Long) -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    when (state) {
        WorkoutState.Idle -> {
            // Контроллер создаётся в ветке Idle: при старте тренировки ветка покидает композицию,
            // стек сбрасывается, и после тренировки открывается форма. Хранилища записей старого
            // контроллера остаются в activity до её уничтожения, но ViewModel в них нет, и это
            // несколько пустых объектов за тренировку
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = ROUTE_SETTINGS,
                // Без анимаций: во время перехода экран не RESUMED, и dropUnlessResumed
                // глотал бы нажатия
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                // safeDrawing включает клавиатуру, поэтому отдельный imePadding() не нужен
                modifier = Modifier.safeDrawingPadding(),
            ) {
                composable(ROUTE_SETTINGS) {
                    // Настройки читаются при создании маршрута: после тренировки стека нет, и форма
                    // берёт сохранённые значения. Несохранённые правки, пока открыт другой экран,
                    // хранит rememberSaveable записи стека
                    val initial = remember { loadSettings() }
                    SettingsContent(
                        initial = initial,
                        onStart = onStart,
                        onOpenHistory = dropUnlessResumed {
                            navController.navigate(ROUTE_HISTORY)
                        },
                        onOpenSettings = dropUnlessResumed {
                            navController.navigate(ROUTE_APP_SETTINGS)
                        },
                    )
                }
                composable(ROUTE_APP_SETTINGS) {
                    AppSettingsContent(
                        loadVolume = loadVolume,
                        onVolumeChange = onVolumeChange,
                        loadVibration = loadVibration,
                        onVibrationChange = onVibrationChange,
                        loadDuckAhead = loadDuckAhead,
                        onDuckAheadChange = onDuckAheadChange,
                        loadStartDelay = loadStartDelay,
                        onStartDelayChange = onStartDelayChange,
                        onBack = dropUnlessResumed { navController.popBackStack() },
                    )
                }
                composable(ROUTE_HISTORY) {
                    // null до первого ответа базы, иначе при открытии мелькнёт пустое состояние
                    val records by runs.collectAsStateWithLifecycle(initialValue = null)
                    HistoryContent(
                        records = records,
                        onDelete = onDeleteRun,
                        onBack = dropUnlessResumed { navController.popBackStack() },
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
