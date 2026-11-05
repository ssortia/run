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
import com.ssortia.runtimer.WorkoutState
import com.ssortia.runtimer.formatTime
import com.ssortia.runtimer.wholeSeconds
import java.util.Locale

/**
 * Подписи полоски тренировки. Строчные варианты нужны для описания TalkBack, где отрезок и повтор
 * стоят внутри фразы. [minutes] и [seconds] склоняют число из `<plurals>`: «1 минута», «2 минуты».
 */
data class WorkoutStripLabels(
    val run: String,
    val walk: String,
    val runLower: String,
    val walkLower: String,
    val paused: String,
    val countdown: String,
    val round: String,
    val roundLower: String,
    val description: String,
    val pausedDescription: String,
    val countdownDescription: String,
    val minutes: (Int) -> String,
    val seconds: (Int) -> String,
)

/** Текст полоски тренировки над нижней панелью. [progress] равен null на отсчёте: линии нет. */
data class WorkoutStripText(
    val title: String,
    val subtitle: String,
    val time: String,
    val progress: Float?,
    val description: String,
)

/**
 * Текст полоски для [state] или null вне тренировки. [details] — параметры тренировки для отсчёта
 * (`workoutDetailsText`): позиции на отсчёте ещё нет.
 */
fun workoutStripText(state: WorkoutState, details: String, labels: WorkoutStripLabels): WorkoutStripText? =
    when (state) {
        WorkoutState.Idle -> null
        is WorkoutState.Countdown -> {
            val remainingMs = state.seconds * 1000
            WorkoutStripText(
                title = labels.countdown,
                subtitle = details,
                time = formatTime(remainingMs),
                progress = null,
                description = labels.countdownDescription.format(Locale.ROOT, spokenTime(remainingMs, labels)),
            )
        }
        is WorkoutState.Active -> {
            val position = state.position
            val segment = if (position.isRun) labels.run else labels.walk
            val segmentLower = if (position.isRun) labels.runLower else labels.walkLower
            val template = if (state.paused) labels.pausedDescription else labels.description
            WorkoutStripText(
                title = if (state.paused) labels.paused.format(Locale.ROOT, segment) else segment,
                subtitle = labels.round.format(Locale.ROOT, position.round, state.rounds),
                time = formatTime(position.remainingMs),
                progress = state.progress,
                description = template.format(
                    Locale.ROOT,
                    segmentLower,
                    spokenTime(position.remainingMs, labels),
                    labels.roundLower.format(Locale.ROOT, position.round, state.rounds),
                ),
            )
        }
    }

/**
 * Остаток словами для TalkBack: секунды округляются вверх, как на экране, нулевые минуты и секунды
 * не произносятся («45 секунд», «2 минуты»). Нулевой остаток читается как «0 секунд».
 */
private fun spokenTime(remainingMs: Long, labels: WorkoutStripLabels): String {
    val totalSec = wholeSeconds(remainingMs)
    val minutes = (totalSec / 60).toInt()
    val seconds = (totalSec % 60).toInt()
    return buildList {
        if (minutes > 0) add(labels.minutes(minutes))
        if (seconds > 0 || minutes == 0) add(labels.seconds(seconds))
    }.joinToString(" ")
}

// Промежуток между частями полоски под центральной кнопкой панели: кнопка выступает над панелью
// и лежит поверх полоски, а текст под ней не прячется
private val StripCenterGap = 88.dp
private val StripMinHeight = 56.dp
private val StripProgressHeight = 3.dp

/**
 * Полоска тренировки над нижней панелью на вкладках, кроме «Таймера». Цвет отрезка, слева отрезок
 * и повтор (на отсчёте «Старт через» и параметры), справа оставшееся время, снизу линия «пройдено».
 * Нажатие открывает «Таймер» ([onOpen]). Вне тренировки ничего не рисует.
 *
 * Части по [StripCenterGap] с weight(1f) каждая: промежуток всегда по центру, под кнопкой, как бы ни
 * менялась ширина текста. Для TalkBack полоска одна кнопка с описанием одной фразой: clearAndSetSemantics
 * стоит снаружи clickable и стирает его действие, поэтому onClick задан в нём же, а clickable остаётся
 * для касания. Без liveRegion: время меняется каждую секунду.
 */
@Composable
fun WorkoutStrip(
    state: WorkoutState,
    details: String,
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
    )
    val text = workoutStripText(state, details, labels) ?: return
    val dark = LocalDarkTheme.current
    val colors = when (state) {
        is WorkoutState.Active -> segmentColors(state.position.isRun, dark)
        else -> countdownColors(dark)
    }
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
                        .background(colors.content.copy(alpha = 0.3f)),
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
