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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.ProgressLabels
import com.ssortia.runtimer.ProgressRowText
import com.ssortia.runtimer.R
import com.ssortia.runtimer.RUNS_TO_PASS_WEEK
import com.ssortia.runtimer.WeekProgress
import com.ssortia.runtimer.progressDateFormatter
import com.ssortia.runtimer.progressRowText
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
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val dateFormatter = remember(zone, locale) { progressDateFormatter(zone, locale) }

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.progress_title))

        if (progress != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = ScrollContentBottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WeeksCard(progress, dateFormatter)
            }
        }
    }
}

@Composable
private fun WeeksCard(progress: List<WeekProgress>, dateFormatter: DateTimeFormatter) {
    val labels = ProgressLabels(
        run = stringResource(R.string.segment_run),
        walk = stringResource(R.string.segment_walk),
        min = stringResource(R.string.minutes_short),
        sec = stringResource(R.string.seconds_short),
        statusCount = stringResource(R.string.progress_ring_count),
        statusPassed = stringResource(R.string.progress_status_passed),
        now = stringResource(R.string.progress_description_now),
        passed = stringResource(R.string.progress_description_passed),
        last = stringResource(R.string.progress_description_last),
        noRuns = stringResource(R.string.progress_description_no_runs),
        count = stringResource(R.string.progress_description_count),
    )

    SectionCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
        progress.forEachIndexed { index, week ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val text = progressRowText(
                week = week,
                weekTitle = stringResource(R.string.preset_week, week.week),
                runsText = pluralStringResource(R.plurals.progress_runs, week.completedRuns, week.completedRuns),
                labels = labels,
                formatter = dateFormatter,
            )
            WeekRow(text, week)
        }
    }
}

@Composable
private fun WeekRow(text: ProgressRowText, week: WeekProgress) {
    val numbers = MaterialTheme.typography.bodyMedium.merge(TabularNumbers)
    // Строка читается TalkBack одной фразой, видимые элементы без своей семантики
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clearAndSetSemantics { contentDescription = text.description }
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
            text.status?.let { Text(text = it, style = numbers) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = text.runs,
                style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = text.date ?: stringResource(R.string.progress_no_runs),
                style = numbers,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Кольцо недели: [RUNS_TO_PASS_WEEK] частей по числу пробежек до порога, закрашиваются по часовой
 * стрелке сверху. Пройденная неделя показывается закрашенным кругом с галочкой. Своей семантики
 * нет: строку читает описание [ProgressRowText.description].
 */
@Composable
private fun WeekRing(week: WeekProgress) {
    val darkTheme = LocalDarkTheme.current
    val filled = segmentColors(isRun = true, darkTheme).marker
    val track = progressTrackColor(darkTheme)

    if (week.passed) {
        Box(
            modifier = Modifier
                .size(RingSize)
                .background(filled, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = progressCheckColor(darkTheme),
            )
        }
    } else {
        Canvas(Modifier.size(RingSize)) {
            val strokePx = RingStroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val sweep = 360f / RUNS_TO_PASS_WEEK - RING_GAP_DEG
            for (part in 0 until RUNS_TO_PASS_WEEK) {
                drawArc(
                    color = if (part < week.completedRuns) filled else track,
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
