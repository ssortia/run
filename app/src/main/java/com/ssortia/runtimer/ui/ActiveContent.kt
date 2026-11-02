package com.ssortia.runtimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    modifier: Modifier = Modifier,
) {
    val position = state.position
    val colors = segmentColors(position.isRun, isSystemInDarkTheme())

    TimerLayout(
        colors = colors,
        modifier = modifier,
        center = {
            Text(
                text = stringResource(
                    if (position.isRun) R.string.segment_run else R.string.segment_walk,
                ),
                style = MaterialTheme.typography.displaySmall,
            )
            TimerText(formatTime(position.remainingMs))
            Text(
                text = stringResource(R.string.round_progress, position.round, state.rounds),
                style = MaterialTheme.typography.titleLarge,
            )
            if (state.soundFailed) SoundWarning()
        },
        bottom = {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = colors.content,
                trackColor = colors.content.copy(alpha = 0.3f),
            )
            StopButton(colors, onStop)
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
            if (state.soundFailed) SoundWarning()
        },
        bottom = { StopButton(colors, onStop) },
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

@Composable
private fun SoundWarning() {
    Text(
        text = stringResource(R.string.warning_no_sound),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StopButton(colors: SegmentColors, onStop: () -> Unit) {
    // Цвета поменяны местами: контраст этой пары проверен в ThemeTest
    Button(
        onClick = onStop,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.content,
            contentColor = colors.background,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Text(
            text = stringResource(R.string.button_stop),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
