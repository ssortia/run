package com.ssortia.runtimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.R
import com.ssortia.runtimer.RUNS_TO_PASS_WEEK
import com.ssortia.runtimer.WORKOUT_PRESETS
import com.ssortia.runtimer.WeekProgress
import com.ssortia.runtimer.WorkoutPreset
import com.ssortia.runtimer.presetRowText
import com.ssortia.runtimer.progressDateFormatter
import com.ssortia.runtimer.progressDateText
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RingSize = 24.dp
private val RingStroke = 2.dp

/** Зазор между частями кольца в градусах. */
private const val RING_GAP_DEG = 8f

/**
 * Экран прогресса по программе. [progress] равен null, пока база не ответила или если чтение
 * не удалось: тогда под панелью ничего нет, иначе мелькнули бы нули. Экран только показывает
 * подсчитанное из истории и ничего не меняет.
 */
@Composable
fun ProgressContent(
    progress: List<WeekProgress>?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val dateFormatter = remember(zone, locale) { progressDateFormatter(zone, locale) }

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.progress_title), onBack = onBack)

        if (progress != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryCard(progress)
                WeeksCard(progress, dateFormatter)
            }
        }
    }
}

@Composable
private fun SummaryCard(progress: List<WeekProgress>) {
    val passed = progress.count { it.passed }
    val darkTheme = LocalDarkTheme.current
    val filled = segmentColors(isRun = true, darkTheme).marker
    val track = progressTrackColor(darkTheme)
    val barDescription = stringResource(R.string.progress_summary_description, passed, progress.size)

    SectionCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = passed.toString(),
                style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.progress_summary, progress.size),
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Для TalkBack полоса одна с описанием, деления без описаний
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .semantics { contentDescription = barDescription },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            progress.forEach { week ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(if (week.passed) filled else track, RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@Composable
private fun WeeksCard(progress: List<WeekProgress>, dateFormatter: DateTimeFormatter) {
    val runLabel = stringResource(R.string.segment_run)
    val walkLabel = stringResource(R.string.segment_walk)
    val minLabel = stringResource(R.string.minutes_short)
    val secLabel = stringResource(R.string.seconds_short)

    SectionCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
        // Оба списка идут по порядку недель программы
        WORKOUT_PRESETS.zip(progress).forEachIndexed { index, (preset, week) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WeekRow(
                preset = preset,
                week = week,
                weekTitle = stringResource(R.string.preset_week, preset.week),
                runLabel = runLabel,
                walkLabel = walkLabel,
                minLabel = minLabel,
                secLabel = secLabel,
                dateFormatter = dateFormatter,
            )
        }
    }
}

@Composable
private fun WeekRow(
    preset: WorkoutPreset,
    week: WeekProgress,
    weekTitle: String,
    runLabel: String,
    walkLabel: String,
    minLabel: String,
    secLabel: String,
    dateFormatter: DateTimeFormatter,
) {
    val text = presetRowText(preset, weekTitle, runLabel, walkLabel, minLabel, secLabel)
    val numbers = MaterialTheme.typography.bodyMedium.merge(TabularNumbers)
    // Строка читается TalkBack одной фразой вместе с описанием кольца
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WeekRing(week)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = text.details,
                style = numbers,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = pluralStringResource(R.plurals.progress_runs, week.completedRuns, week.completedRuns),
                style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = week.lastCompletedAtMs?.let { progressDateText(it, dateFormatter) } ?: "—",
                style = numbers,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Кольцо недели: четыре части по числу пробежек до порога, закрашиваются по часовой стрелке
 * сверху. Пройденная неделя показывается закрашенным кругом с галочкой.
 */
@Composable
private fun WeekRing(week: WeekProgress) {
    val darkTheme = LocalDarkTheme.current
    val filled = segmentColors(isRun = true, darkTheme).marker
    val track = progressTrackColor(darkTheme)
    val description = if (week.passed) {
        stringResource(R.string.progress_ring_passed)
    } else {
        stringResource(R.string.progress_ring_count, week.completedRuns, RUNS_TO_PASS_WEEK)
    }

    Box(
        modifier = Modifier
            .size(RingSize)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (week.passed) {
            Canvas(Modifier.size(RingSize)) { drawCircle(color = filled) }
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = progressCheckColor(darkTheme),
            )
        } else {
            Canvas(Modifier.size(RingSize)) {
                val strokePx = RingStroke.toPx()
                val inset = strokePx / 2
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val sweep = 360f / RUNS_TO_PASS_WEEK - RING_GAP_DEG
                for (part in 0 until RUNS_TO_PASS_WEEK) {
                    drawArc(
                        color = if (part < week.filledQuarters) filled else track,
                        startAngle = -90f + RING_GAP_DEG / 2 + part * 360f / RUNS_TO_PASS_WEEK,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                    )
                }
            }
        }
    }
}
