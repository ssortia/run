package com.ssortia.runtimer.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ssortia.runtimer.R
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
            // Отступы сверху и по бокам поглощаются здесь, поэтому NavigationBar не добавит боковые
            // второй раз, а снизу системную панель он учитывает сам, и его фон уходит под жестовую
            // полосу. Клавиатуры вне тренировки нет, отдельный учёт ime не нужен
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_SETTINGS,
                    // Без анимаций: во время перехода экран не RESUMED, и dropUnlessResumed
                    // глотал бы нажатия
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    modifier = Modifier.weight(1f),
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
                        )
                    }
                    screen(ROUTE_HISTORY) {
                        // null до первого ответа базы, иначе при открытии мелькнёт пустое состояние
                        val records by runs.collectAsStateWithLifecycle(initialValue = null)
                        HistoryContent(
                            records = records,
                            onDelete = onDeleteRun,
                        )
                    }
                    screen(ROUTE_PROGRESS) {
                        // Прогресс не хранится, а считается из истории: удаление записи сразу меняет его
                        val records by runs.collectAsStateWithLifecycle(initialValue = null)
                        val progress = remember(records) { records?.let(::programProgress) }
                        ProgressContent(
                            progress = progress,
                        )
                    }
                }
                BottomBar(navController)
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
 * Нижняя панель вкладок. Выбранная вкладка берётся из маршрута текущей записи стека. Переход
 * сбрасывает стек до формы с сохранением состояния вкладок, поэтому в стеке не больше двух
 * записей: «Назад» с любой вкладки ведёт на форму, а запись формы не пересоздаётся.
 *
 * Без dropUnlessResumed: панель стоит вне NavHost, её LifecycleOwner — activity, которая всегда
 * RESUMED, и обёртка ничего бы не защищала. Двойное нажатие безопасно и так: переход идемпотентен
 * благодаря popUpTo и launchSingleTop, нажатие на текущую вкладку ничего не делает, а popBackStack
 * вкладки не вызывают, так что пустого NavHost не будет.
 */
@Composable
private fun BottomBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val current = bottomTabOf(entry?.destination?.route)
    // Индикатор M3 по умолчанию в светлой теме почти не виден, поэтому выбранную вкладку
    // выделяет цвет primary у иконки и подписи
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
    )
    NavigationBar {
        BottomTab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) navController.navigate(tab.route) {
                        popUpTo(ROUTE_SETTINGS) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(painter = tabIcon(tab), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
                colors = itemColors,
            )
        }
    }
}

@Composable
private fun tabIcon(tab: BottomTab): Painter = when (tab) {
    BottomTab.WORKOUT -> painterResource(R.drawable.ic_timer)
    BottomTab.HISTORY -> painterResource(R.drawable.ic_history)
    BottomTab.PROGRESS -> painterResource(R.drawable.ic_flag)
    BottomTab.SETTINGS -> rememberVectorPainter(Icons.Outlined.Settings)
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
