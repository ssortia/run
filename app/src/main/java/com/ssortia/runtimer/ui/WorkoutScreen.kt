package com.ssortia.runtimer.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ssortia.runtimer.RunRecord
import com.ssortia.runtimer.ThemeMode
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.WorkoutState
import com.ssortia.runtimer.programProgress
import kotlinx.coroutines.flow.Flow

@Composable
fun WorkoutScreen(
    state: WorkoutState,
    loadSettings: () -> WorkoutSettings,
    loadVolume: () -> Int,
    loadVibration: () -> Boolean,
    loadDuckMusic: () -> Boolean,
    loadStartDelay: () -> Int,
    runs: Flow<List<RunRecord>>,
    showBackgroundWarning: Boolean,
    onStart: (WorkoutSettings) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onDuckMusicChange: (Boolean) -> Unit,
    onStartDelayChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDeleteRun: (Long) -> Unit,
    onOpenAppDetails: () -> Unit,
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
                screen(ROUTE_SETTINGS) {
                    // Настройки читаются при создании маршрута: после тренировки стека нет, и форма
                    // берёт сохранённые значения. Несохранённые правки, пока открыт другой экран,
                    // хранит rememberSaveable записи стека
                    val initial = remember { loadSettings() }
                    SettingsContent(
                        initial = initial,
                        showBackgroundWarning = showBackgroundWarning,
                        onStart = onStart,
                        onOpenHistory = dropUnlessResumed {
                            navController.navigate(ROUTE_HISTORY)
                        },
                        onOpenProgress = dropUnlessResumed {
                            navController.navigate(ROUTE_PROGRESS)
                        },
                        onOpenSettings = dropUnlessResumed {
                            navController.navigate(ROUTE_APP_SETTINGS)
                        },
                        onOpenAppDetails = dropUnlessResumed { onOpenAppDetails() },
                    )
                }
                screen(ROUTE_APP_SETTINGS) {
                    AppSettingsContent(
                        loadVolume = loadVolume,
                        onVolumeChange = onVolumeChange,
                        loadVibration = loadVibration,
                        onVibrationChange = onVibrationChange,
                        loadDuckMusic = loadDuckMusic,
                        onDuckMusicChange = onDuckMusicChange,
                        loadStartDelay = loadStartDelay,
                        onStartDelayChange = onStartDelayChange,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onBack = dropUnlessResumed { navController.popBackStack() },
                    )
                }
                screen(ROUTE_HISTORY) {
                    // null до первого ответа базы, иначе при открытии мелькнёт пустое состояние
                    val records by runs.collectAsStateWithLifecycle(initialValue = null)
                    HistoryContent(
                        records = records,
                        onDelete = onDeleteRun,
                        onBack = dropUnlessResumed { navController.popBackStack() },
                    )
                }
                screen(ROUTE_PROGRESS) {
                    // Прогресс не хранится, а считается из истории: удаление записи сразу меняет его
                    val records by runs.collectAsStateWithLifecycle(initialValue = null)
                    val progress = remember(records) { records?.let(::programProgress) }
                    ProgressContent(
                        progress = progress,
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

/**
 * Маршрут с непрозрачным фоном. Даже без анимаций NavHost держит уходящий экран в композиции
 * ещё кадр-другой, и без фона экраны просвечивали бы друг сквозь друга. Новый экран лежит над
 * старым, а при возврате уходящий лежит над тем, к которому возвращаются, поэтому фон закрывает
 * нижний целиком.
 */
private fun NavGraphBuilder.screen(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route) { entry ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content(entry)
        }
    }
}
