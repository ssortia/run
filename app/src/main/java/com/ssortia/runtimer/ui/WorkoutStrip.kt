package com.ssortia.runtimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.R
import com.ssortia.runtimer.Workout
import com.ssortia.runtimer.WorkoutState
import com.ssortia.runtimer.WorkoutStripLabels
import com.ssortia.runtimer.workoutStripText

// Промежуток между частями полоски под центральной кнопкой панели: кнопка выступает над панелью
// и лежит поверх полоски, а текст под ней не прячется
private val StripCenterGap = 88.dp
private val StripMinHeight = 56.dp
private val StripProgressHeight = 3.dp

/**
 * Полоска тренировки над нижней панелью на всех вкладках, пока экран тренировки свёрнут. Цвет отрезка, слева отрезок
 * и повтор (на отсчёте «Старт через» и параметры [workout]), справа оставшееся время, снизу линия
 * «пройдено». Нажатие разворачивает экран тренировки ([onOpen]). Вне тренировки ничего не рисует.
 *
 * Части по [StripCenterGap] с weight(1f) каждая: промежуток всегда по центру, под кнопкой, как бы ни
 * менялась ширина текста. Для TalkBack полоска одна кнопка с описанием одной фразой: clearAndSetSemantics
 * стоит снаружи clickable и стирает его действие, поэтому onClick задан в нём же, а clickable остаётся
 * для касания. Без liveRegion: время меняется каждую секунду.
 */
@Composable
fun WorkoutStrip(
    state: WorkoutState,
    workout: Workout,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val labels = WorkoutStripLabels(
        run = stringResource(R.string.segment_run),
        walk = stringResource(R.string.segment_walk),
        runLower = stringResource(R.string.summary_run),
        walkLower = stringResource(R.string.summary_walk),
        paused = stringResource(R.string.segment_paused),
        countdown = stringResource(R.string.countdown_label),
        round = stringResource(R.string.round_progress),
        roundLower = stringResource(R.string.round_progress_lower),
        description = stringResource(R.string.strip_description),
        pausedDescription = stringResource(R.string.strip_paused_description),
        countdownDescription = stringResource(R.string.strip_countdown_description),
        minutes = { resources.getQuantityString(R.plurals.strip_minutes, it, it) },
        seconds = { resources.getQuantityString(R.plurals.strip_seconds, it, it) },
        minutesAccusative = { resources.getQuantityString(R.plurals.strip_minutes_accusative, it, it) },
        secondsAccusative = { resources.getQuantityString(R.plurals.strip_seconds_accusative, it, it) },
    )
    // Единственная проверка состояния: вне тренировки текста нет, и полоска не рисуется
    val text = workoutStripText(state, workout, labels) ?: return
    val colors = workoutColors(state, LocalDarkTheme.current)
    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Column(
            modifier
                .fillMaxWidth()
                .background(colors.background)
                .clearAndSetSemantics {
                    contentDescription = text.description
                    role = Role.Button
                    onClick(label = null) {
                        onOpen()
                        true
                    }
                }
                .clickable(onClick = onOpen),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = StripMinHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(R.drawable.ic_timer), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = text.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = text.subtitle,
                            style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(StripCenterGap))
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = text.time,
                        style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                        maxLines = 1,
                    )
                    Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = null)
                }
            }
            text.progress?.let { progress ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(StripProgressHeight)
                        .background(colors.content.copy(alpha = ProgressTrackAlpha)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(colors.content),
                    )
                }
            }
        }
    }
}
