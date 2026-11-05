package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssortia.runtimer.MAX_MINUTES
import com.ssortia.runtimer.MAX_ROUNDS
import com.ssortia.runtimer.MAX_SECONDS
import com.ssortia.runtimer.R
import com.ssortia.runtimer.SECONDS_WHEEL_STEP
import com.ssortia.runtimer.SettingsError
import com.ssortia.runtimer.SummaryLabels
import com.ssortia.runtimer.WORKOUT_PRESETS
import com.ssortia.runtimer.PresetRowText
import com.ssortia.runtimer.WorkoutPreset
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.WorkoutSummaryText
import com.ssortia.runtimer.workoutSummaryText
import com.ssortia.runtimer.presetRowText
import com.ssortia.runtimer.presetWeekOf
import com.ssortia.runtimer.wheelOptions
import java.util.Locale
import kotlinx.coroutines.launch

/** Повторы, прижатые к 1..[MAX_ROUNDS]: 0 и число вне диапазона из сохранённых настроек. */
private fun clampedRounds(value: Int): Int = value.coerceIn(1, MAX_ROUNDS)

/**
 * Шаг степпера повторов. Значение прижимается до и после прибавления [delta], поэтому
 * переполнения нет, а число вне диапазона сразу возвращается в допустимые пределы.
 */
fun stepRounds(value: Int, delta: Int): Int = (clampedRounds(value) + delta).coerceIn(1, MAX_ROUNDS)

fun canDecreaseRounds(value: Int): Boolean = clampedRounds(value) > 1

fun canIncreaseRounds(value: Int): Boolean = clampedRounds(value) < MAX_ROUNDS

/**
 * Время отрезка в строке карточки, «м:сс». Значение не
 * прижимается к диапазону: неверное сохранённое видно как есть, а ошибку покажет проверка формы.
 */
fun segmentTimeText(minutes: Int, seconds: Int): String =
    "%d:%02d".format(Locale.ROOT, minutes, seconds)

@StringRes
fun SettingsError.messageRes(): Int = when (this) {
    SettingsError.RUN_ZERO -> R.string.error_run_zero
    SettingsError.WALK_ZERO -> R.string.error_walk_zero
    SettingsError.ROUNDS_RANGE -> R.string.error_rounds_range
    SettingsError.SECONDS_RANGE -> R.string.error_seconds_range
    SettingsError.MINUTES_RANGE -> R.string.error_minutes_range
}

/** Аргументы строки из [messageRes]: пределы подставляются из тех же констант, что и в проверке. */
fun SettingsError.messageArgs(): Array<Any> = when (this) {
    SettingsError.RUN_ZERO, SettingsError.WALK_ZERO -> emptyArray()
    SettingsError.ROUNDS_RANGE -> arrayOf(MAX_ROUNDS)
    SettingsError.SECONDS_RANGE -> arrayOf(MAX_SECONDS)
    SettingsError.MINUTES_RANGE -> arrayOf(MAX_MINUTES)
}

/**
 * Несохранённые правки формы. Значения хранятся как есть, без прижатия: неверное сохранённое
 * видно на форме, а ошибку покажет [WorkoutSettings.validate].
 */
internal val WorkoutSettingsSaver = listSaver<WorkoutSettings, Int>(
    save = { listOf(it.runMin, it.runSec, it.walkMin, it.walkSec, it.rounds) },
    restore = { WorkoutSettings(it[0], it[1], it[2], it[3], it[4]) },
)

/** Отрезок, карточка которого раскрыта. */
private enum class Segment { RUN, WALK }

// Высота свёрнутых карточек «Бег», «Шаг» и «Повторы», чтобы карточки формы были одной высоты
private val CardRowHeight = 72.dp
private val PresetCheckSize = 24.dp
private val RoundsValueWidth = 80.dp
private val WeekChipHeight = 40.dp
private val FormTopPadding = 16.dp

/**
 * Форма настроек. Состояние [settings] хранит оболочка WorkoutScreen, форма меняет его только
 * через [onSettingsChange]. «Старт» не на форме, а в центре нижней панели: он запускает эти же
 * настройки с любой вкладки. Предупреждение о фоне и ошибка проверки стоят под карточками.
 * [error] — результат `settings.validate()`, оболочка считает его один раз и для «Старта».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: WorkoutSettings,
    error: SettingsError?,
    onSettingsChange: (WorkoutSettings) -> Unit,
    showBackgroundWarning: Boolean,
    onOpenAppDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Раскрыта не больше одной карточки отрезка, null — обе свёрнуты
    var expanded by rememberSaveable { mutableStateOf<Segment?>(null) }
    // Шторка программы не маршрут, а состояние формы
    var showProgram by rememberSaveable { mutableStateOf(false) }
    val selectedWeek = presetWeekOf(settings)

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.tab_workout),
            actions = {
                WeekChip(
                    text = if (selectedWeek != null) {
                        stringResource(R.string.preset_week, selectedWeek)
                    } else {
                        stringResource(R.string.preset_custom)
                    },
                    onClick = { showProgram = true },
                )
            },
        )

        val resources = LocalContext.current.resources
        val labels = SummaryLabels(
            run = stringResource(R.string.summary_run),
            walk = stringResource(R.string.summary_walk),
            min = stringResource(R.string.minutes_short),
        )
        val summary = workoutSummaryText(settings, error, labels) { count ->
            resources.getQuantityString(R.plurals.summary_segments, count, count)
        }
        // Последний верный состав держит высоту строки при ошибке, чтобы карточки не прыгали.
        // Обычное поле, а не состояние: оно нужно только для высоты и рекомпозицию не вызывает
        val lastParts = remember { LastParts() }
        if (summary != null) lastParts.text = summary.text
        val time = summary?.time ?: stringResource(R.string.summary_none)
        val largeTimeStyle = summaryTimeStyle(large = true)
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current

        // Высота области прокрутки за вычетом вертикальных отступов колонки: иначе раскладка
        // была бы выше области на эти отступы и колонка прокручивалась бы всегда. Отступы
        // округляются до пикселей так же, как их округляет колонка
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val viewport = with(density) {
                constraints.maxHeight - FormTopPadding.roundToPx() - ScrollContentBottomPadding.roundToPx()
            }.coerceAtLeast(0)
            // Обратная прокрутка отсчитывает смещение от нижнего края: по умолчанию видны
            // карточки у панели, а при смене высоты (раскрытие карточки, предупреждение, шрифт)
            // смещение от низа сохраняется, и колонка растёт вверх, унося итог из вида
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState(), reverseScrolling = true)
                    .padding(
                        start = 16.dp,
                        top = FormTopPadding,
                        end = 16.dp,
                        bottom = ScrollContentBottomPadding,
                    ),
            ) {
                FormLayout(
                    viewport = viewport,
                    cardExpanded = expanded != null,
                    largeTimeWidth = {
                        textMeasurer.measure(time, largeTimeStyle, maxLines = 1, softWrap = false).size.width
                    },
                    summary = { large ->
                        WorkoutSummary(summary = summary, time = time, placeholderParts = lastParts.text, large = large)
                    },
                    bottom = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SegmentCard(
                                label = stringResource(R.string.segment_run),
                                isRun = true,
                                minutes = settings.runMin,
                                seconds = settings.runSec,
                                expanded = expanded == Segment.RUN,
                                onToggle = { expanded = if (expanded == Segment.RUN) null else Segment.RUN },
                                onMinutesChange = { onSettingsChange(settings.copy(runMin = it)) },
                                onSecondsChange = { onSettingsChange(settings.copy(runSec = it)) },
                            )
                            SegmentCard(
                                label = stringResource(R.string.segment_walk),
                                isRun = false,
                                minutes = settings.walkMin,
                                seconds = settings.walkSec,
                                expanded = expanded == Segment.WALK,
                                onToggle = { expanded = if (expanded == Segment.WALK) null else Segment.WALK },
                                onMinutesChange = { onSettingsChange(settings.copy(walkMin = it)) },
                                onSecondsChange = { onSettingsChange(settings.copy(walkSec = it)) },
                            )
                            RoundsCard(
                                value = settings.rounds,
                                onValueChange = { onSettingsChange(settings.copy(rounds = it)) },
                            )
                            if (showBackgroundWarning) {
                                BackgroundWarning(onOpenAppDetails = onOpenAppDetails)
                            }
                            if (error != null) {
                                Text(
                                    text = stringResource(error.messageRes(), *error.messageArgs()),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (showProgram) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { showProgram = false },
            sheetState = sheetState,
        ) {
            ProgramSheetContent(
                selectedWeek = selectedWeek,
                onSelect = { preset ->
                    onSettingsChange(preset.settings)
                    // Флаг снимается только после скрытия, чтобы не разойтись с состоянием шторки
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showProgram = false
                    }
                },
            )
        }
    }
}

/**
 * Раскладка формы: итог по центру свободного места над нижней частью (карточки, предупреждение,
 * ошибка). Сначала меряется нижняя часть, затем итог с крупным временем; малый итог составляется
 * и меряется, только если [summaryPlacement] отказал крупному по высоте свободного места или по
 * ширине времени [largeTimeWidth]. Решение принимает только [summaryPlacement]. Размещается
 * только выбранный вариант: неразмещённый не попадает в дерево доступности, и TalkBack не находит
 * два итога. Высота раскладки не меньше [viewport] (в пикселях), а когда итог не помещается и в
 * малом виде, колонка вокруг прокручивается от нижнего края, и итог уходит вверх.
 */
@Composable
private fun FormLayout(
    viewport: Int,
    cardExpanded: Boolean,
    largeTimeWidth: () -> Int,
    summary: @Composable (large: Boolean) -> Unit,
    bottom: @Composable () -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        // Колонка с прокруткой даёт бесконечную высоту, итог и нижняя часть меряются без предела
        val free = constraints.copy(minWidth = constraints.maxWidth, minHeight = 0, maxHeight = Constraints.Infinity)
        val bottomPlaceable = subcompose("bottom", bottom).first().measure(free)
        val large = subcompose("largeSummary") { summary(true) }.first().measure(free)
        var small: Placeable? = null
        val placement = summaryPlacement(
            viewport = viewport,
            bottom = bottomPlaceable.height,
            largeSummary = large.height,
            largeFitsWidth = largeTimeWidth() <= constraints.maxWidth,
            cardExpanded = cardExpanded,
        ) {
            subcompose("smallSummary") { summary(false) }.first().measure(free).also { small = it }.height
        }
        val chosen = if (placement.useLarge) large else checkNotNull(small)
        layout(constraints.maxWidth, placement.totalHeight) {
            chosen.placeRelative(
                x = (constraints.maxWidth - chosen.width) / 2,
                y = (placement.slotHeight - chosen.height) / 2,
            )
            bottomPlaceable.placeRelative(x = 0, y = placement.slotHeight)
        }
    }
}

/** Последняя верная строка состава, см. [WorkoutSummary]. */
private class LastParts(var text: String = "")

// Размеры времени итога из макета: 80 при свёрнутых карточках, 56 с раскрытым барабаном.
// Межстрочие 1,05, как в макете, иначе крупные цифры занимали бы лишнюю высоту
@Composable
private fun summaryTimeStyle(large: Boolean): TextStyle {
    val size = if (large) 80.sp else 56.sp
    return MaterialTheme.typography.displayLarge
        .copy(fontSize = size, lineHeight = size * 1.05f, letterSpacing = (-1).sp)
        .merge(TabularNumbers)
}

/**
 * Итог тренировки над карточками: общее время и состав. При неверных настройках [summary] null:
 * вместо времени [time] «—», весь блок приглушён, а строка состава невидима и держит высоту
 * последнего верного состава [placeholderParts]. Для TalkBack итог читается одной фразой, без
 * liveRegion: время меняется вместе с карточками, а их изменения объявляются сами.
 */
@Composable
private fun WorkoutSummary(summary: WorkoutSummaryText?, time: String, placeholderParts: String, large: Boolean) {
    val title = stringResource(R.string.summary_title)
    val description = summary?.description(title) ?: stringResource(R.string.summary_invalid_description)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .alpha(if (summary != null) 1f else DisabledContentAlpha)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = time,
            style = summaryTimeStyle(large),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = summary?.text ?: placeholderParts,
            style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = if (summary != null) Modifier else Modifier.alpha(0f),
        )
    }
}

/**
 * Плашка недели в шапке формы, открывает шторку программы. Рамка и лёгкая тень подсказывают,
 * что её можно нажать. `Surface(onClick)` сам задаёт `Role.Button`.
 */
@Composable
private fun WeekChip(text: String, onClick: () -> Unit) {
    val description = stringResource(R.string.preset_chip_description, text)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(1.dp, weekChipBorderColor(LocalDarkTheme.current)),
        shadowElevation = 1.dp,
        // Слот actions даёт справа только 4 dp
        modifier = Modifier
            .padding(end = 12.dp)
            .heightIn(min = WeekChipHeight)
            .semantics { contentDescription = description },
    ) {
        // Текст плашки уже вошёл в описание, иначе TalkBack прочитал бы его дважды
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Список недель программы в шторке. Выбранная неделя на фоне `secondaryContainer` с галочкой. */
@Composable
private fun ProgramSheetContent(
    selectedWeek: Int?,
    onSelect: (WorkoutPreset) -> Unit,
) {
    val runLabel = stringResource(R.string.segment_run)
    val walkLabel = stringResource(R.string.segment_walk)
    val minLabel = stringResource(R.string.minutes_short)
    val secLabel = stringResource(R.string.seconds_short)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.preset_title),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        WORKOUT_PRESETS.forEach { preset ->
            val text = presetRowText(
                preset = preset,
                weekTitle = stringResource(R.string.preset_week, preset.week),
                runLabel = runLabel,
                walkLabel = walkLabel,
                minLabel = minLabel,
                secLabel = secLabel,
            )
            PresetRow(
                text = text,
                selected = selectedWeek == preset.week,
                onClick = { onSelect(preset) },
            )
        }
    }
}

@Composable
private fun PresetRow(text: PresetRowText, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (selected) Modifier.background(colors.secondaryContainer) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val textColor = if (selected) colors.onSecondaryContainer else colors.onSurface
        val detailsColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text.title, style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(
                text = text.details,
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = detailsColor,
            )
        }
        // Общее время в одну строку и без weight: колонка слева сжимается первой
        Text(
            text = text.total,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
            color = textColor,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(PresetCheckSize),
                tint = colors.onSecondaryContainer,
            )
        } else {
            // Место под галочку держится всегда, чтобы общее время не прыгало
            Box(Modifier.size(PresetCheckSize))
        }
    }
}

/**
 * Карточка отрезка: в свёрнутом виде точка, подпись и время, нажатие раскрывает барабаны минут
 * и секунд. Барабаны пишут в те же поля [WorkoutSettings] формы, поэтому проверка и сохранение
 * от них не зависят.
 * Значение не на шаге барабана (7 секунд из прежней версии) остаётся в списке, пока его не
 * прокрутили.
 */
@Composable
private fun SegmentCard(
    label: String,
    isRun: Boolean,
    minutes: Int,
    seconds: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    val stateText = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)

    SectionCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText }
                .heightIn(min = CardRowHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        color = segmentColors(isRun, LocalDarkTheme.current).marker,
                        shape = CircleShape,
                    ),
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = segmentTimeText(minutes, seconds),
                style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                color = if (expanded) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp
                else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TimeWheel(
                    options = remember(minutes) { wheelOptions(MAX_MINUTES, 1, minutes) },
                    value = minutes,
                    onSelect = onMinutesChange,
                    contentDescription = stringResource(R.string.wheel_minutes, label),
                    unitLabel = stringResource(R.string.minutes_short),
                    modifier = Modifier.weight(1f),
                )
                TimeWheel(
                    options = remember(seconds) {
                        wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, seconds)
                    },
                    value = seconds,
                    onSelect = onSecondsChange,
                    contentDescription = stringResource(R.string.wheel_seconds, label),
                    unitLabel = stringResource(R.string.seconds_short),
                    modifier = Modifier.weight(1f),
                    format = { "%02d".format(Locale.ROOT, it) },
                )
            }
        }
    }
}

/**
 * Степпер повторов. Число меняется только кнопками: повторов в [WORKOUT_PRESETS] немного, а
 * предел [MAX_ROUNDS] достижим и кнопками, поэтому поле ввода с клавиатурой не нужно. Значение
 * показывается как есть, неверное сохранённое видно, а ошибку покажет проверка формы.
 *
 * Объявление liveRegion срабатывает при любой смене числа, в том числе при выборе недели в
 * шторке: тогда TalkBack прочитает «Повторы: N» вместе с закрытием шторки.
 */
@Composable
private fun RoundsCard(value: Int, onValueChange: (Int) -> Unit) {
    val label = stringResource(R.string.field_rounds)
    SectionCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CardRowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            StepButton(
                glyph = "−",
                description = stringResource(R.string.rounds_decrease),
                enabled = canDecreaseRounds(value),
                onClick = { onValueChange(stepRounds(value, -1)) },
            )
            val valueDescription = stringResource(R.string.rounds_value, value)
            Text(
                text = value.toString(),
                modifier = Modifier
                    .width(RoundsValueWidth)
                    // liveRegion объявляет новое значение после «+» и «−»
                    .semantics {
                        contentDescription = valueDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            StepButton(
                glyph = "+",
                description = stringResource(R.string.rounds_increase),
                enabled = canIncreaseRounds(value),
                onClick = { onValueChange(stepRounds(value, 1)) },
            )
        }
    }
}

/**
 * Кнопка степпера со знаком вместо иконки: в `material-icons-core` нет «минуса». Знак скрыт
 * от TalkBack, кнопку называет [description].
 */
@Composable
private fun StepButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = glyph,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * Баннер о фоновой работе. Цвет не красный: старт предупреждение не блокирует. Стрелка
 * открывает страницу приложения в системных настройках.
 */
@Composable
private fun BackgroundWarning(onOpenAppDetails: () -> Unit) {
    SectionCard(contentPadding = PaddingValues(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.warning_background),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onOpenAppDetails) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.button_open_app_details),
                )
            }
        }
    }
}
