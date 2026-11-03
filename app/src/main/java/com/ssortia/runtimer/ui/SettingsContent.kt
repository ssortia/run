package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.MAX_MINUTES
import com.ssortia.runtimer.MAX_ROUNDS
import com.ssortia.runtimer.MAX_SECONDS
import com.ssortia.runtimer.R
import com.ssortia.runtimer.SECONDS_WHEEL_STEP
import com.ssortia.runtimer.SettingsError
import com.ssortia.runtimer.WORKOUT_PRESETS
import com.ssortia.runtimer.WorkoutPreset
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.presetWeekOf
import com.ssortia.runtimer.wheelOptions
import java.util.Locale

/** Текст полей формы. Хранится строками, потому что поле может быть пустым. */
data class SettingsInput(
    val runMin: String,
    val runSec: String,
    val walkMin: String,
    val walkSec: String,
    val rounds: String,
) {
    fun toSettings() = WorkoutSettings(
        runMin = parseField(runMin),
        runSec = parseField(runSec),
        walkMin = parseField(walkMin),
        walkSec = parseField(walkSec),
        rounds = parseField(rounds),
    )

    companion object {
        fun from(settings: WorkoutSettings) = SettingsInput(
            runMin = settings.runMin.toString(),
            runSec = settings.runSec.toString(),
            walkMin = settings.walkMin.toString(),
            walkSec = settings.walkSec.toString(),
            rounds = settings.rounds.toString(),
        )
    }
}

/** Оставляет только цифры ASCII: так вставка из буфера не занесёт в поле минус или пробел. */
fun digitsOnly(text: String): String = text.filter { it in '0'..'9' }

/**
 * Текст приходит из поля, уже прошедшего [digitsOnly]. Пустое поле считается нулём. Число,
 * не вмещающееся в Int, заменяется на [Int.MAX_VALUE]: пользователь получит ошибку диапазона
 * из [WorkoutSettings.validate], а не ноль.
 */
fun parseField(text: String): Int =
    if (text.isEmpty()) 0 else text.toIntOrNull() ?: Int.MAX_VALUE

/** Повторы из поля, прижатые к 1..[MAX_ROUNDS]: пустое поле и 0 считаются одним повтором. */
private fun clampedRounds(text: String): Int = parseField(text).coerceIn(1, MAX_ROUNDS)

/**
 * Шаг степпера повторов. Значение прижимается до и после прибавления [delta], поэтому
 * переполнения нет, а число вне диапазона сразу возвращается в допустимые пределы.
 */
fun stepRounds(text: String, delta: Int): String =
    (clampedRounds(text) + delta).coerceIn(1, MAX_ROUNDS).toString()

fun canDecreaseRounds(text: String): Boolean = clampedRounds(text) > 1

fun canIncreaseRounds(text: String): Boolean = clampedRounds(text) < MAX_ROUNDS

/**
 * Время отрезка в строке карточки, «м:сс», из уже разобранных полей формы. Значение не
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

internal val SettingsInputSaver = listSaver<SettingsInput, String>(
    save = { listOf(it.runMin, it.runSec, it.walkMin, it.walkSec, it.rounds) },
    restore = { SettingsInput(it[0], it[1], it[2], it[3], it[4]) },
)


private const val EXPANDED_RUN = "run"
private const val EXPANDED_WALK = "walk"

/** Форма настроек. [onStart] вызывается только с настройками, прошедшими проверку. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    initial: WorkoutSettings,
    showBackgroundWarning: Boolean,
    onStart: (WorkoutSettings) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable(stateSaver = SettingsInputSaver) {
        mutableStateOf(SettingsInput.from(initial))
    }
    // Раскрыта не больше одной карточки отрезка: null, EXPANDED_RUN или EXPANDED_WALK
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val settings = input.toSettings()
    val error = settings.validate()
    val selectedWeek = presetWeekOf(settings)

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.app_name),
            actions = {
                // Шторка программы подключается вместе с её появлением, пока кнопка ничего не делает
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.DateRange,
                        contentDescription = stringResource(R.string.preset_title),
                    )
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        painter = painterResource(R.drawable.ic_history),
                        contentDescription = stringResource(R.string.button_history),
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.button_settings),
                    )
                }
            },
        )
        if (selectedWeek != null) {
            Text(
                text = stringResource(R.string.preset_week_match, selectedWeek),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PresetChips(
                selectedWeek = selectedWeek,
                onSelect = { input = SettingsInput.from(it.settings) },
            )
            SegmentCard(
                label = stringResource(R.string.segment_run),
                isRun = true,
                minutes = input.runMin,
                seconds = input.runSec,
                expanded = expanded == EXPANDED_RUN,
                onToggle = { expanded = if (expanded == EXPANDED_RUN) null else EXPANDED_RUN },
                onMinutesChange = { input = input.copy(runMin = it.toString()) },
                onSecondsChange = { input = input.copy(runSec = it.toString()) },
            )
            SegmentCard(
                label = stringResource(R.string.segment_walk),
                isRun = false,
                minutes = input.walkMin,
                seconds = input.walkSec,
                expanded = expanded == EXPANDED_WALK,
                onToggle = { expanded = if (expanded == EXPANDED_WALK) null else EXPANDED_WALK },
                onMinutesChange = { input = input.copy(walkMin = it.toString()) },
                onSecondsChange = { input = input.copy(walkSec = it.toString()) },
            )
            RoundsCard(
                value = input.rounds,
                onValueChange = { input = input.copy(rounds = it) },
            )
        }

        // Нижний блок закреплён: поля прокручиваются, а «Старт» всегда виден и поднимается
        // над клавиатурой вместе с отступом safeDrawing у NavHost
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // При открытой клавиатуре баннер отнял бы у полей почти всё место
            if (showBackgroundWarning && !WindowInsets.isImeVisible) {
                BackgroundWarning(onOpenAppDetails = onOpenAppDetails)
            }
            if (error != null) {
                Text(
                    text = stringResource(error.messageRes(), *error.messageArgs()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = { onStart(settings) },
                enabled = error == null,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.button_start),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/**
 * Ряд недель программы. На экран помещается 3–4 чипа, поэтому при первом показе формы ряд
 * один раз прокручивается к подсвеченному чипу. Флаг в [rememberSaveable] не даёт повторить
 * прокрутку после поворота и возврата с других экранов, иначе она перебила бы позицию,
 * которую выбрал пользователь. При ручной правке и выборе ряд не прокручивается.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChips(
    selectedWeek: Int?,
    onSelect: (WorkoutPreset) -> Unit,
) {
    val selectedRequester = remember { BringIntoViewRequester() }
    var scrolledToSelected by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (scrolledToSelected) return@LaunchedEffect
        scrolledToSelected = true
        if (selectedWeek == null) return@LaunchedEffect
        // Ждём следующий кадр: прокрутить можно только к уже размещённому чипу
        withFrameNanos { }
        selectedRequester.bringIntoView()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.preset_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WORKOUT_PRESETS.forEach { preset ->
                val selected = selectedWeek == preset.week
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(preset) },
                    label = { Text(stringResource(R.string.preset_week, preset.week)) },
                    modifier = if (selected) {
                        Modifier.bringIntoViewRequester(selectedRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * Карточка отрезка: в свёрнутом виде точка, подпись и время, нажатие раскрывает барабаны минут
 * и секунд. Барабаны пишут в те же строковые поля формы, поэтому проверка и сохранение прежние.
 * Значение не на шаге барабана (7 секунд из прежней версии) остаётся в списке, пока его не
 * прокрутили.
 */
@Composable
private fun SegmentCard(
    label: String,
    isRun: Boolean,
    minutes: String,
    seconds: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    val minutesValue = parseField(minutes)
    val secondsValue = parseField(seconds)
    val stateText = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)

    SectionCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText }
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                text = segmentTimeText(minutesValue, secondsValue),
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
                    options = wheelOptions(MAX_MINUTES, 1, minutesValue),
                    value = minutesValue,
                    onSelect = onMinutesChange,
                    contentDescription = stringResource(R.string.wheel_minutes, label),
                    unitLabel = stringResource(R.string.minutes_short),
                    modifier = Modifier.weight(1f),
                )
                TimeWheel(
                    options = wheelOptions(MAX_SECONDS, SECONDS_WHEEL_STEP, secondsValue),
                    value = secondsValue,
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

/** Степпер повторов. Поле остаётся редактируемым, чтобы 20 повторов не набирать нажатиями. */
@Composable
private fun RoundsCard(value: String, onValueChange: (String) -> Unit) {
    val label = stringResource(R.string.field_rounds)
    SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            NumberField(
                label = label,
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(80.dp),
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

/** Поле повторов: только цифры, число по центру. Подпись стоит слева в строке и задана для TalkBack. */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(digitsOnly(it)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge
            .merge(TabularNumbers)
            .merge(TextStyle(textAlign = TextAlign.Center)),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier.semantics { contentDescription = label },
    )
}
