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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssortia.runtimer.R
import com.ssortia.runtimer.WorkoutState
import com.ssortia.runtimer.formatTime

/** Экран тренировки. Фон уходит под системные панели, отступы применяются к содержимому. */
@Composable
fun ActiveContent(
    state: WorkoutState.Active,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = state.position
    val colors = segmentColors(position.isRun, isSystemInDarkTheme())

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
                    Text(
                        text = stringResource(
                            if (position.isRun) R.string.segment_run else R.string.segment_walk,
                        ),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        text = formatTime(position.remainingMs),
                        // Цифры одной ширины: время не дрожит при смене секунд
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 88.sp,
                            lineHeight = 104.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = stringResource(R.string.round_progress, position.round, state.rounds),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = colors.content,
                trackColor = colors.content.copy(alpha = 0.3f),
            )

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
    }
}
