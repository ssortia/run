package com.ssortia.runtimer.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            // Настройки формы живут здесь, а не в записи маршрута, чтобы «Старт» вне NavHost
            // запускал и несохранённые правки. Ветка покидает композицию при старте, поэтому после
            // тренировки они читаются заново из SettingsStore
            var settings by rememberSaveable(stateSaver = WorkoutSettingsSaver) {
                mutableStateOf(loadSettings())
            }
            // Отступы сверху и по бокам поглощаются общим Box, а не Column: тогда «Старт» встанет по
            // центру панели, а не экрана, и при боковой системной панели. NavigationBar не добавит
            // боковые второй раз, а снизу системную панель он учитывает сам, и его фон уходит под
            // жестовую полосу. Клавиатуры вне тренировки нет, отдельный учёт ime не нужен
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
            ) {
                val error = settings.validate()
                val startEnabled = error == null
                val start = { onStart(settings) }
                Column(Modifier.fillMaxSize()) {
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
                            // Настройки приходят из ветки Idle и переживают переход на другую вкладку.
                            // Раскрытая карточка и шторка программы хранятся в записи стека
                            SettingsContent(
                                settings = settings,
                                error = error,
                                onSettingsChange = { settings = it },
                                showBackgroundWarning = showBackgroundWarning,
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
                    BottomBar(
                        navController = navController,
                        startEnabled = startEnabled,
                        onStart = start,
                    )
                }
                // Поверх панели, а не внутри NavigationBar: Surface панели обрезает содержимое по
                // своей форме, и выступающая часть не рисовалась бы и не нажималась. Для TalkBack
                // кнопку заменяет заместитель в центральном слоте панели
                StartFab(
                    enabled = startEnabled,
                    onClick = start,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom))
                        .padding(bottom = BottomBarHeight - (StartFabSize - StartFabOverlap)),
                )
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

// Высота NavigationBar без системной панели. Повторяет внутреннее значение M3 (NavigationBarHeight),
// которое наружу не открыто: по нему «Старт» выступает над панелью на StartFabOverlap
private val BottomBarHeight = 80.dp
// Отступ крайних вкладок от краёв экрана. Симметричный, поэтому центральный слот остаётся под «Стартом»
private val BottomBarEdgePadding = 16.dp
// Заливка 64 dp и кольцо цвета фона по 4 dp с каждой стороны: BorderStroke рисуется внутри границ
private val StartFabSize = 72.dp
private val StartFabRing = 4.dp
private val StartFabIconSize = 36.dp
private val StartFabElevation = 6.dp
// Сильно скруглённый квадрат, но не круг: круг дал бы радиус StartFabSize / 2 (36 dp)
private val StartFabCorner = 28.dp

/**
 * На сколько «Старт» выступает над нижней панелью: на треть высоты, две трети кнопки лежат на
 * панели. Прокручиваемые колонки всех вкладок добавляют его к нижнему отступу, иначе кнопка
 * закроет последний элемент.
 */
internal val StartFabOverlap = StartFabSize / 3

/** Нижний отступ прокручиваемой колонки вкладки: обычные 16 dp плюс выступ «Старта». */
internal val ScrollContentBottomPadding = 16.dp + StartFabOverlap

/**
 * Нижняя панель вкладок. Выбранная вкладка берётся из маршрута текущей записи стека. Переход
 * сбрасывает стек до формы с сохранением состояния вкладок, поэтому в стеке не больше двух
 * записей: «Назад» с любой вкладки ведёт на форму, а запись формы не пересоздаётся.
 *
 * Центральный слот между «Историей» и «Прогрессом» занят не вкладкой, а заместителем «Старта» для
 * TalkBack: видимая кнопка лежит поверх панели в общем Box и скрыта от него, а Surface панели —
 * группа обхода, и соседа вне неё между вкладками не поставить. Заместитель внутри группы, поэтому
 * обход по геометрии читает «История, Старт, Прогресс». Заместитель ещё и нажимается без ряби: нижняя
 * часть слота под видимой кнопкой выглядит её продолжением. Выступающую над панелью часть кнопки
 * заместитель не покрывает (границы узла обрезаются по Surface панели), поэтому исследование
 * касанием там находит элемент экрана под кнопкой.
 *
 * Без dropUnlessResumed: панель стоит вне NavHost, её LifecycleOwner — activity, а не запись
 * стека, и обёртка не следила бы за переходом между экранами. Двойное нажатие безопасно и так:
 * переход идемпотентен благодаря popUpTo и launchSingleTop, нажатие на текущую вкладку ничего не
 * делает, а popBackStack вкладки не вызывают, так что пустого NavHost не будет. Повторный «Старт»
 * отсекают permissionPending и сервис, отклоняющий второй запрос на старт.
 */
@Composable
private fun BottomBar(
    navController: NavHostController,
    startEnabled: Boolean,
    onStart: () -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val current = bottomTabOf(entry?.destination?.route)
    // Индикатор M3 по умолчанию в светлой теме почти не виден, поэтому выбранную вкладку
    // выделяет цвет primary у иконки и подписи
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
    )
    val startLabel = stringResource(R.string.button_start)
    val startSlot = startSlotIndex()
    NavigationBar {
        // Отступы внутри ряда, а не modifier панели: фон панели остаётся во всю ширину экрана
        Spacer(Modifier.width(BottomBarEdgePadding))
        BottomTab.entries.forEachIndexed { index, tab ->
            if (index == startSlot) {
                // clickable сам даёт роль, onClick и disabled() при недоступном «Старте»
                Box(
                    Modifier
                        .weight(1f)
                        .height(BottomBarHeight)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = startEnabled,
                            role = Role.Button,
                            onClick = onStart,
                        )
                        .semantics { contentDescription = startLabel },
                )
            }
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
                // В одну строку: перенос сделал бы панель выше BottomBarHeight, и «Старт» закрыл бы подписи
                label = { Text(stringResource(tab.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = itemColors,
            )
        }
        Spacer(Modifier.width(BottomBarEdgePadding))
    }
}

/**
 * «Старт» скруглённым квадратом в цветах бега, а не primary: в тёмной теме primary светлее, а «Старт» в обеих
 * темах изумрудный с белым значком. Кольцо цвета фона отделяет кнопку от панели. Для TalkBack
 * кнопка скрыта, её читает заместитель в [BottomBar].
 */
@Composable
private fun StartFab(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = segmentColors(isRun = true, LocalDarkTheme.current)
    val background = MaterialTheme.colorScheme.background
    val onSurface = MaterialTheme.colorScheme.onSurface
    // Недоступная как у M3 (12 % и 38 % onSurface), но заливка смешана с фоном, иначе сквозь
    // полупрозрачную кнопку проступала бы граница панели
    val containerColor = if (enabled) {
        colors.background
    } else {
        onSurface.copy(alpha = DisabledContainerAlpha).compositeOver(background)
    }
    val contentColor = if (enabled) colors.content else onSurface.copy(alpha = DisabledContentAlpha)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(StartFabCorner),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(StartFabRing, background),
        shadowElevation = if (enabled) StartFabElevation else 0.dp,
        modifier = modifier
            .size(StartFabSize)
            .clearAndSetSemantics {},
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(StartFabIconSize),
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
