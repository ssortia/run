package com.ssortia.runtimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ssortia.runtimer.R
import com.ssortia.runtimer.RunRecord
import com.ssortia.runtimer.ThemeMode
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.WorkoutState
import com.ssortia.runtimer.programProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Оболочка приложения: NavHost с вкладками, нижняя панель, полоска тренировки и центральная
 * кнопка. Составляется во всех состояниях, поэтому стек и состояния вкладок переживают старт и
 * финал тренировки. «Таймер» всегда показывает форму, а экран отсчёта и тренировки — слой поверх
 * вкладок, полоски и панели, составляемый только во весь экран ([fullscreen]). Сворачивается он на ту
 * вкладку, что под ним, стек при этом не трогается. [fullscreen] и развёрнутость [expanded] хранит и
 * считает MainActivity: по ним же выбираются системные панели, и второй источник мог бы с ними разойтись.
 */
@Composable
fun WorkoutScreen(
    state: WorkoutState,
    fullscreen: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
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
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // Настройки формы живут здесь, а не в записи маршрута, чтобы «Старт» вне NavHost запускал и
    // несохранённые правки. Оболочка не покидает композицию при старте, поэтому после тренировки
    // они не перечитываются: onStart сохранил их до старта, а во время тренировки «Старта» нет
    var settings by rememberSaveable(stateSaver = WorkoutSettingsSaver) {
        mutableStateOf(loadSettings())
    }
    // Проверка формы считается один раз и для формы, и для «Старта». Через State, а не значением:
    // захваченное значение пересобирало бы граф NavHost на каждой правке
    val settingsError by remember { derivedStateOf { settings.validate() } }

    // Высота области и смещение слоя вниз в пикселях: 0 — развёрнут, heightPx — свёрнут. Смещение
    // не сохраняется: после поворота развёрнутый слой стоит на месте, а сворачивавшийся в момент
    // поворота снова развёрнут целиком (expanded ещё true)
    var heightPx by remember { mutableFloatStateOf(0f) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Колбэки и эффект читают текущее значение, а не захваченное при их создании
    val currentExpanded by rememberUpdatedState(expanded)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val sheetSpec = tween<Float>(SheetAnimationMs, easing = FastOutSlowInEasing)

    // Свёрнутый слой сначала встаёт под нижний край и составляется (expanded = true), затем выезжает.
    // Из сворачивания (expanded ещё true) он разворачивается от текущего смещения, без скачка вниз
    val expand: () -> Unit = {
        scope.launch {
            if (!currentExpanded) {
                offset.snapTo(heightPx)
                currentOnExpandedChange(true)
            }
            offset.animateTo(0f, sheetSpec)
        }
    }
    // expanded = false только после доезда: системные панели меняются, пока слой закрывает экран.
    // Если сворачивание прервал expand(), animateTo бросит CancellationException, и false не
    // выставится. Повторный вызов идемпотентен: цель та же
    val collapse: () -> Unit = {
        scope.launch {
            offset.animateTo(heightPx, sheetSpec)
            currentOnExpandedChange(false)
        }
    }

    // При старте экран тренировки выезжает поверх текущей вкладки, после финала или «Стоп» слой
    // исчезает без анимации: он составляется только вне Idle. Прошлое значение сохраняется при
    // повороте, поэтому поворот свёрнутый экран не разворачивает
    val idle = state is WorkoutState.Idle
    var wasIdle by rememberSaveable { mutableStateOf(idle) }
    LaunchedEffect(idle) {
        if (shouldExpandWorkout(wasIdle, idle)) {
            expand()
        } else if (idle) {
            offset.snapTo(0f)
            currentOnExpandedChange(false)
        }
        wasIdle = idle
    }

    // Запущенная тренировка для полоски на отсчёте. Читается из SettingsStore, откуда её взял сервис,
    // один раз на уходе из Idle, а не из формы: форму можно успеть поправить до публикации отсчёта
    val startedWorkout = remember(idle) { if (idle) null else loadSettings().toWorkout() }

    // NavHost пересобирает граф при каждой смене лямбды-построителя, поэтому состояние сервиса,
    // которое меняется каждую секунду, читается внутри маршрутов через State, а не захватывается
    val currentState by rememberUpdatedState(state)

    // Внешний Box без отступов: слой тренировки сам поглощает их в TimerLayout, и его фон уходит
    // под системные панели. Его высота — путь слоя при сворачивании. onSizeChanged, а не
    // BoxWithConstraints: лишняя субкомпозиция над NavHost не нужна
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() },
    ) {
        // Отступы сверху и по бокам поглощаются общим Box, а не Column: тогда «Старт» встанет по
        // центру панели, а не экрана, и при боковой системной панели. NavigationBar не добавит
        // боковые второй раз, а снизу системную панель он учитывает сам, и его фон уходит под
        // жестовую полосу. Клавиатуры в приложении нет, отдельный учёт ime не нужен. Под
        // развёрнутым слоем тренировки вкладки скрыты от TalkBack
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .then(if (fullscreen) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            // Центральная кнопка: «Старт» вне тренировки, пауза и продолжение во время неё
            val centerMode = centerButtonMode(state)
            val centerEnabled = centerButtonEnabled(centerMode, settingsValid = settingsError == null)
            val onCenterClick = {
                when (centerMode) {
                    CenterButtonMode.START -> onStart(settings)
                    CenterButtonMode.PAUSE -> onPause()
                    CenterButtonMode.RESUME -> onResume()
                    // Недоступная кнопка нажатий не получает, ветка нужна только для полноты when
                    CenterButtonMode.PAUSE_DISABLED -> Unit
                }
            }
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
                        // Настройки приходят из оболочки и переживают переход на другую вкладку.
                        // Форма доступна и во время тренировки: экран тренировки лежит слоем поверх
                        SettingsContent(
                            settings = settings,
                            error = settingsError,
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
                            workoutRunning = currentState !is WorkoutState.Idle,
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
                // Тренировка есть только вне Idle, поэтому вне Idle и полоска
                startedWorkout?.let { workout ->
                    WorkoutStrip(
                        state = state,
                        workout = workout,
                        onOpen = expand,
                    )
                }
                BottomBar(
                    current = bottomTabOf(route),
                    onTabClick = { tab -> navController.navigateToTab(tab.route) },
                    centerMode = centerMode,
                    centerEnabled = centerEnabled,
                    onCenterClick = onCenterClick,
                )
            }
            // Поверх панели, а не внутри NavigationBar: Surface панели обрезает содержимое по
            // своей форме, и выступающая часть не рисовалась бы и не нажималась. Для TalkBack
            // кнопку заменяет заместитель в центральном слоте панели
            CenterFab(
                state = state,
                mode = centerMode,
                enabled = centerEnabled,
                onClick = onCenterClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom))
                    .padding(bottom = BottomBarHeight - (CenterFabSize - CenterFabOverlap)),
            )
        }
        if (fullscreen) {
            WorkoutLayer(
                state = state,
                onCollapse = collapse,
                onStop = onStop,
                onPause = onPause,
                onResume = onResume,
                // Первым модификатором: область касаний едет вместе со слоем. Смещение читается
                // при размещении, поэтому анимация не перекомпоновывает слой
                modifier = Modifier.offset { IntOffset(0, offset.value.roundToInt()) },
            )
        }
    }
}

/**
 * Слой экрана отсчёта или тренировки поверх вкладок. Колбэки без dropUnlessResumed: слой вне
 * NavHost, а повтор безопасен — сворачивание идемпотентно, команды сервиса явные.
 */
@Composable
private fun WorkoutLayer(
    state: WorkoutState,
    onCollapse: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Внутри слоя, то есть в композиции после NavHost: Navigation Compose регистрирует свой
    // обработчик «Назад» при стеке больше одной записи, а диспетчер вызывает последний добавленный.
    // Обработчик, составленный раньше NavHost, на «Истории» проиграл бы ему, и «Назад» сняла бы
    // запись стека вместо сворачивания
    BackHandler(onBack = onCollapse)
    val paneTitle = stringResource(R.string.pane_workout)
    Box(
        modifier
            .fillMaxSize()
            // Не лишний: у TimerLayout только фон, и без обработчика указателя касание пустого
            // места слоя дошло бы до вкладки под ним
            .pointerInput(Unit) {}
            // TalkBack объявляет панель и переводит в неё фокус, иначе он остался бы на скрытой полоске
            .semantics { this.paneTitle = paneTitle },
    ) {
        when (state) {
            // Слой составляется только вне Idle
            WorkoutState.Idle -> Unit
            is WorkoutState.Countdown -> CountdownContent(
                state = state,
                onStop = onStop,
                onCollapse = onCollapse,
            )
            is WorkoutState.Active -> ActiveContent(
                state = state,
                onStop = onStop,
                onPause = onPause,
                onResume = onResume,
                onCollapse = onCollapse,
            )
        }
    }
}

/**
 * Переход на вкладку по схеме панели: стек сбрасывается до «Таймера» с сохранением состояния
 * вкладок, поэтому в нём не больше двух записей. Переход на текущую вкладку ничего не делает,
 * это единственная такая проверка для панели.
 */
private fun NavHostController.navigateToTab(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        popUpTo(ROUTE_SETTINGS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// Длительность разворачивания и сворачивания экрана тренировки
private const val SheetAnimationMs = 280

// Высота NavigationBar без системной панели. Повторяет внутреннее значение M3 (NavigationBarHeight),
// которое наружу не открыто: по нему центральная кнопка выступает над панелью на CenterFabOverlap
private val BottomBarHeight = 80.dp
// Отступ крайних вкладок от краёв экрана. Симметричный, поэтому центральный слот остаётся под кнопкой
private val BottomBarEdgePadding = 16.dp
// Заливка 64 dp и кольцо цвета фона по 4 dp с каждой стороны: BorderStroke рисуется внутри границ
private val CenterFabSize = 72.dp
private val CenterFabRing = 4.dp
private val CenterFabIconSize = 36.dp
private val CenterFabElevation = 6.dp
// Сильно скруглённый квадрат, но не круг: круг дал бы радиус CenterFabSize / 2 (36 dp)
private val CenterFabCorner = 28.dp

/**
 * На сколько центральная кнопка выступает над нижней панелью: на треть высоты, две трети кнопки лежат на
 * панели. Прокручиваемые колонки всех вкладок добавляют его к нижнему отступу, иначе кнопка
 * закроет последний элемент.
 */
internal val CenterFabOverlap = CenterFabSize / 3

/** Нижний отступ прокручиваемой колонки вкладки: обычные 16 dp плюс выступ центральной кнопки. */
internal val ScrollContentBottomPadding = 16.dp + CenterFabOverlap

/**
 * Нижняя панель вкладок. Выбранная вкладка [current] берётся из маршрута текущей записи стека.
 * Переход ([onTabClick]) сбрасывает стек до «Таймера» с сохранением состояния вкладок, поэтому в
 * стеке не больше двух записей: «Назад» с любой вкладки ведёт на «Таймер» (форму), а его запись
 * не пересоздаётся. Развёрнутый экран тренировки «Назад» сначала сворачивает.
 *
 * Центральный слот между «Историей» и «Прогрессом» занят не вкладкой, а заместителем центральной
 * кнопки для TalkBack: «Старт» вне тренировки, «Пауза» или «Продолжить» во время неё ([centerMode]).
 * Видимая кнопка лежит поверх панели в общем Box и скрыта от него, а Surface панели — группа
 * обхода, и соседа вне неё между вкладками не поставить. Заместитель внутри группы, поэтому обход
 * по геометрии читает «История, Старт, Прогресс». Заместитель ещё и нажимается без ряби: нижняя
 * часть слота под видимой кнопкой выглядит её продолжением. Выступающую над панелью часть кнопки
 * заместитель не покрывает (границы узла обрезаются по Surface панели), поэтому исследование
 * касанием там находит элемент экрана под кнопкой, во время тренировки — полоску.
 *
 * Без dropUnlessResumed: панель стоит вне NavHost, её LifecycleOwner — activity, а не запись
 * стека, и обёртка не следила бы за переходом между экранами. Двойное нажатие безопасно и так:
 * переход идемпотентен благодаря popUpTo и launchSingleTop, нажатие на текущую вкладку ничего не
 * делает, а popBackStack вкладки не вызывают, так что пустого NavHost не будет. Повторный «Старт»
 * отсекают permissionPending и сервис, отклоняющий второй запрос на старт, а команды паузы явные и
 * безопасны при повторе.
 */
@Composable
private fun BottomBar(
    current: BottomTab?,
    onTabClick: (BottomTab) -> Unit,
    centerMode: CenterButtonMode,
    centerEnabled: Boolean,
    onCenterClick: () -> Unit,
) {
    // Индикатор M3 по умолчанию в светлой теме почти не виден, поэтому выбранную вкладку
    // выделяет цвет primary у иконки и подписи
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
    )
    val centerLabel = stringResource(centerButtonLabelRes(centerMode))
    val startSlot = startSlotIndex()
    NavigationBar {
        // Отступы внутри ряда, а не modifier панели: фон панели остаётся во всю ширину экрана
        Spacer(Modifier.width(BottomBarEdgePadding))
        BottomTab.entries.forEachIndexed { index, tab ->
            if (index == startSlot) {
                // clickable сам даёт роль, onClick и disabled() при недоступной кнопке
                Box(
                    Modifier
                        .weight(1f)
                        .height(BottomBarHeight)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = centerEnabled,
                            role = Role.Button,
                            onClick = onCenterClick,
                        )
                        .semantics { contentDescription = centerLabel },
                )
            }
            NavigationBarItem(
                selected = tab == current,
                onClick = { onTabClick(tab) },
                icon = { Icon(painter = tabIcon(tab), contentDescription = null) },
                // В одну строку: перенос сделал бы панель выше BottomBarHeight, и кнопка закрыла бы подписи
                label = { Text(stringResource(tab.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = itemColors,
            )
        }
        Spacer(Modifier.width(BottomBarEdgePadding))
    }
}

/**
 * Центральная кнопка скруглённым квадратом: «Старт» вне тренировки, пауза и продолжение во время неё
 * ([mode]). Цвета бега, а не primary: в тёмной теме primary светлее, а «Старт» в обеих темах
 * изумрудный с белым значком; во время тренировки цвета текущего отрезка, как у полоски. Кольцо
 * цвета фона отделяет кнопку от панели. Для TalkBack кнопка скрыта, её читает заместитель в [BottomBar].
 */
@Composable
private fun CenterFab(
    state: WorkoutState,
    mode: CenterButtonMode,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = workoutColors(state, LocalDarkTheme.current)
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
        shape = RoundedCornerShape(CenterFabCorner),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(CenterFabRing, background),
        shadowElevation = if (enabled) CenterFabElevation else 0.dp,
        modifier = modifier
            .size(CenterFabSize)
            .clearAndSetSemantics {},
    ) {
        Box(contentAlignment = Alignment.Center) {
            val iconModifier = Modifier.size(CenterFabIconSize)
            when (mode) {
                CenterButtonMode.START, CenterButtonMode.RESUME -> Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = iconModifier,
                )
                CenterButtonMode.PAUSE, CenterButtonMode.PAUSE_DISABLED -> Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = null,
                    modifier = iconModifier,
                )
            }
        }
    }
}

@Composable
private fun tabIcon(tab: BottomTab): Painter = when (tab) {
    BottomTab.WORKOUT -> painterResource(R.drawable.ic_timer)
    BottomTab.HISTORY -> painterResource(R.drawable.ic_history)
    BottomTab.PROGRESS -> painterResource(R.drawable.ic_flag)
    BottomTab.SETTINGS -> painterResource(R.drawable.ic_settings)
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
