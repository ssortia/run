package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssortia.runtimer.R
import com.ssortia.runtimer.WorkoutState
import com.ssortia.runtimer.formatTime

/** Экран тренировки. */
@Composable
fun ActiveContent(
    state: WorkoutState.Active,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = state.position
    val colors = segmentColors(position.isRun, LocalDarkTheme.current)

    TimerLayout(
        colors = colors,
        onCollapse = onCollapse,
        modifier = modifier,
        center = {
            val segment = stringResource(
                if (position.isRun) R.string.segment_run else R.string.segment_walk,
            )
            // Пауза отмечается в той же строке: новая строка текста сдвигала бы таймер
            Text(
                text = if (state.paused) stringResource(R.string.segment_paused, segment) else segment,
                style = MaterialTheme.typography.displaySmall,
            )
            TimerText(formatTime(position.remainingMs))
            Text(
                text = stringResource(R.string.round_progress, position.round, state.rounds),
                style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
            )
            soundWarningRes(state.soundFailed, state.soundMuted)?.let { WarningText(it) }
        },
        bottom = {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = colors.content,
                trackColor = colors.content.copy(alpha = ProgressTrackAlpha),
                // Без точки на конце дорожки и зазора: иначе пустая полоса выглядит заполненной
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // В половине экрана «Продолжить» не помещается в titleLarge с обычными отступами
                val rowStyle = MaterialTheme.typography.titleMedium
                val rowPadding = PaddingValues(horizontal = 8.dp)
                TimerButton(
                    text = stringResource(R.string.button_stop),
                    colors = colors,
                    filled = false,
                    onClick = onStop,
                    textStyle = rowStyle,
                    contentPadding = rowPadding,
                    modifier = Modifier.weight(1f),
                )
                TimerButton(
                    text = stringResource(pauseButtonRes(state.paused)),
                    colors = colors,
                    filled = true,
                    onClick = if (state.paused) onResume else onPause,
                    textStyle = rowStyle,
                    contentPadding = rowPadding,
                    enabled = pauseEnabled(state),
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

/** Экран отсчёта до старта. Прогресса ещё нет. */
@Composable
fun CountdownContent(
    state: WorkoutState.Countdown,
    onStop: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = countdownColors(LocalDarkTheme.current)

    TimerLayout(
        colors = colors,
        onCollapse = onCollapse,
        modifier = modifier,
        center = {
            Text(
                text = stringResource(R.string.countdown_label),
                style = MaterialTheme.typography.displaySmall,
            )
            TimerText(state.seconds.toString())
            soundWarningRes(state.soundFailed, state.soundMuted)?.let { WarningText(it) }
        },
        bottom = {
            TimerButton(
                text = stringResource(R.string.button_stop),
                colors = colors,
                filled = false,
                onClick = onStop,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/**
 * Фон уходит под системные панели, отступы применяются к содержимому. «Свернуть» слева сверху
 * сворачивает экран на вкладку под ним, тренировка при этом идёт дальше. Кнопка стоит в уменьшенном
 * верхнем отступе и без промежутка после неё (у неё свой внутренний отступ): в альбомной
 * ориентации она не должна отнимать у времени лишнее место и уводить его в прокрутку.
 */
@Composable
private fun TimerLayout(
    colors: SegmentColors,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .safeDrawingPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        ) {
            TextButton(
                onClick = onCollapse,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.content),
            ) {
                Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = null)
                Text(
                    text = stringResource(R.string.button_collapse),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            // Прокручивается только текст: в альбомной ориентации «Стоп» всегда остаётся на экране
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    center()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                bottom()
            }
        }
    }
}

@Composable
private fun TimerText(text: String) {
    Text(
        text = text,
        // Цифры одной ширины: время не дрожит при смене секунд
        style = MaterialTheme.typography.displayLarge
            .copy(fontSize = 88.sp, lineHeight = 104.sp)
            .merge(TabularNumbers),
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * Текст предупреждения о звуке или null, если всё в порядке. Сбой важнее нулевой громкости:
 * если сигналы не подготовлены, прибавление громкости не поможет.
 */
@StringRes
fun soundWarningRes(soundFailed: Boolean, soundMuted: Boolean): Int? = when {
    soundFailed -> R.string.warning_no_sound
    soundMuted -> R.string.warning_muted
    else -> null
}

@StringRes
fun pauseButtonRes(paused: Boolean): Int =
    if (paused) R.string.button_resume else R.string.button_pause

/**
 * После финального сигнала сервис ещё доигрывает его с нулевым остатком, а пауза уже не действует.
 * Нажатие после остановки сервиса создало бы новый экземпляр ради отклонённой команды.
 */
fun pauseEnabled(state: WorkoutState.Active): Boolean = state.position.remainingMs > 0

@Composable
internal fun WarningText(@StringRes textRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** Прозрачность недоступной залитой кнопки: «Пауза» после финального сигнала. */
private const val DISABLED_ALPHA = 0.5f

/**
 * Кнопка на цветном фоне отрезка. Залитая кнопка меняет цвета отрезка местами, обведённая
 * пишет текст цветом отрезка по его фону. Контраст пары симметричен, поэтому проверки
 * в ThemeTest хватает обоим вариантам; рамка того же цвета, что и текст. Недоступной бывает
 * только залитая кнопка, «Стоп» доступен всегда.
 */
@Composable
private fun TimerButton(
    text: String,
    colors: SegmentColors,
    filled: Boolean,
    onClick: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    enabled: Boolean = true,
) {
    val buttonModifier = modifier.height(64.dp)
    val shape = MaterialTheme.shapes.large
    val label: @Composable () -> Unit = {
        Text(
            text = text,
            style = textStyle,
            maxLines = 1,
        )
    }
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.content,
                contentColor = colors.background,
                disabledContainerColor = colors.content.copy(alpha = DISABLED_ALPHA),
                disabledContentColor = colors.background,
            ),
            contentPadding = contentPadding,
            modifier = buttonModifier,
        ) { label() }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = shape,
            border = BorderStroke(2.dp, colors.content),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.content),
            contentPadding = contentPadding,
            modifier = buttonModifier,
        ) { label() }
    }
}
