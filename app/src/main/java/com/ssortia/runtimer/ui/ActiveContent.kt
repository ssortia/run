package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    modifier: Modifier = Modifier,
) {
    val position = state.position
    val colors = segmentColors(position.isRun, isSystemInDarkTheme())

    TimerLayout(
        colors = colors,
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
                style = MaterialTheme.typography.titleLarge,
            )
            soundWarningRes(state.soundFailed, state.soundMuted)?.let { WarningText(it) }
        },
        bottom = {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = colors.content,
                trackColor = colors.content.copy(alpha = 0.3f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // В половине экрана «Продолжить» не помещается в titleLarge с обычными отступами
                val rowStyle = MaterialTheme.typography.titleMedium
                val rowPadding = PaddingValues(horizontal = 8.dp)
                InvertedButton(
                    text = stringResource(R.string.button_stop),
                    colors = colors,
                    onClick = onStop,
                    textStyle = rowStyle,
                    contentPadding = rowPadding,
                    modifier = Modifier.weight(1f),
                )
                InvertedButton(
                    text = stringResource(pauseButtonRes(state.paused)),
                    colors = colors,
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
    modifier: Modifier = Modifier,
) {
    val colors = countdownColors(isSystemInDarkTheme())

    TimerLayout(
        colors = colors,
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
            InvertedButton(
                text = stringResource(R.string.button_stop),
                colors = colors,
                onClick = onStop,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** Фон уходит под системные панели, отступы применяются к содержимому. */
@Composable
private fun TimerLayout(
    colors: SegmentColors,
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
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

            bottom()
        }
    }
}

@Composable
private fun TimerText(text: String) {
    Text(
        text = text,
        // Цифры одной ширины: время не дрожит при смене секунд
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 88.sp,
            lineHeight = 104.sp,
            fontFeatureSettings = "tnum",
        ),
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

@Composable
private fun InvertedButton(
    text: String,
    colors: SegmentColors,
    onClick: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    enabled: Boolean = true,
) {
    // Цвета поменяны местами: контраст этой пары проверен в ThemeTest
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.content,
            contentColor = colors.background,
            disabledContainerColor = colors.content.copy(alpha = 0.5f),
            disabledContentColor = colors.background,
        ),
        contentPadding = contentPadding,
        modifier = modifier.height(64.dp),
    ) {
        Text(
            text = text,
            style = textStyle,
            maxLines = 1,
        )
    }
}
