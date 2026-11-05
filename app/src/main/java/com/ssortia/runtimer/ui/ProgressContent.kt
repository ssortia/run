package com.ssortia.runtimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.ssortia.runtimer.LineSegments
import com.ssortia.runtimer.ProgressLabels
import com.ssortia.runtimer.ProgressRowText
import com.ssortia.runtimer.R
import com.ssortia.runtimer.RUNS_TO_PASS_WEEK
import com.ssortia.runtimer.WeekMark
import com.ssortia.runtimer.WeekProgress
import com.ssortia.runtimer.lineSegments
import com.ssortia.runtimer.progressDateFormatter
import com.ssortia.runtimer.progressRowText
import java.time.ZoneId

// Поля ленты
private val ListHorizontalPadding = 16.dp
private val ListTopPadding = 8.dp

// Колонка узла слева от карточек и линия по её центру
private val NodeColumnWidth = 28.dp
private val NodeGap = 10.dp
private val LineWidth = 2.dp

/** Промежуток между карточками недель: линия в нём прерывается. */
private val WeekGap = 8.dp

// Узлы: пройденная и пропущенная неделя — круг с кольцом цвета фона, текущая — кольцо из частей,
// неделя впереди — маленькое полое кольцо
private val FilledNodeSize = 22.dp
private val NodeBackgroundRing = 3.dp
private val CurrentNodeSize = 28.dp
private val CurrentRingDiameter = 21.dp
private val CurrentRingStroke = 2.5.dp
private val CurrentDotRadius = 3.dp
private val AheadNodeSize = 12.dp
private val AheadNodeStroke = 2.dp
private val CheckIconSize = 14.dp

// Прочерк пропущенной недели: отрезок посередине холста от четверти до трёх четвертей ширины
private val DashCanvasSize = 12.dp
private val DashStroke = 1.6.dp
private const val DASH_START_FRACTION = 0.25f
private const val DASH_END_FRACTION = 0.75f

/** Зазор между частями кольца текущей недели в градусах. */
private const val RING_GAP_DEG = 12f

// Карточки: у «Сейчас» отступы и скругление крупнее, как в макете. Скругления 20 и 16 dp
// взяты из макета ленты, поэтому карточки не `SectionCard` (`MaterialTheme.shapes.large`),
// а фон `surfaceContainer` с цветом текста у каждого `Text`
private val CurrentCardTopPadding = 14.dp
private val CurrentCardBottomPadding = 16.dp
private val CurrentCardSpacing = 10.dp
private val CurrentCardShape = RoundedCornerShape(20.dp)
private val PlainCardVerticalPadding = 12.dp
private val PlainCardShape = RoundedCornerShape(16.dp)
private val PlainCardSpacing = 12.dp
private val PlainCardLineSpacing = 2.dp
private val CardHorizontalPadding = 16.dp
private val TitleChipSpacing = 8.dp
private val StatusDateSpacing = 8.dp
private val NowChipHeight = 24.dp
private val NowChipHorizontalPadding = 10.dp
private val PipHeight = 6.dp
private val PipSpacing = 6.dp

/** Цвета ленты: считаются один раз на экран по выбранной теме. */
private data class TimelineColors(val marker: Color, val track: Color, val check: Color)

/** Размер узла недели: по нему узел ставится центром на линию. */
private fun nodeSize(mark: WeekMark): Dp = when (mark) {
    WeekMark.PASSED, WeekMark.SKIPPED -> FilledNodeSize + NodeBackgroundRing * 2
    WeekMark.CURRENT -> CurrentNodeSize
    WeekMark.AHEAD -> AheadNodeSize
}

/**
 * Экран прогресса по программе: лента из 10 недель, у каждой узел на линии слева и своя
 * карточка, текущая неделя крупнее, с делениями. [progress] равен null, пока база не ответила
 * или если чтение не удалось: тогда под панелью ничего нет, иначе мелькнули бы нули. Экран
 * только показывает подсчитанное из истории и ничего не меняет.
 */
@Composable
fun ProgressContent(
    progress: List<WeekProgress>?,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val dateFormatter = remember(zone, locale) { progressDateFormatter(zone, locale) }
    val darkTheme = LocalDarkTheme.current
    val colors = remember(darkTheme) {
        TimelineColors(
            marker = segmentColors(isRun = true, darkTheme).marker,
            track = progressTrackColor(darkTheme),
            check = progressCheckColor(darkTheme),
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.progress_title))

        if (progress != null) {
            val labels = ProgressLabels(
                run = stringResource(R.string.segment_run),
                walk = stringResource(R.string.segment_walk),
                min = stringResource(R.string.minutes_short),
                sec = stringResource(R.string.seconds_short),
                statusCount = stringResource(R.string.progress_status_count),
                statusPassed = stringResource(R.string.progress_status_passed),
                none = stringResource(R.string.progress_no_runs),
                now = stringResource(R.string.progress_description_now),
                passed = stringResource(R.string.progress_description_passed),
                last = stringResource(R.string.progress_description_last),
                noRuns = stringResource(R.string.progress_description_no_runs),
                count = stringResource(R.string.progress_description_count),
            )
            val segments = remember(progress) { lineSegments(progress) }
            // Промежуток между строками задаёт колонка, а не строки: линия рисуется на высоту
            // строки и в промежутке прерывается
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ListHorizontalPadding,
                        top = ListTopPadding,
                        end = ListHorizontalPadding,
                        bottom = ScrollContentBottomPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(WeekGap),
            ) {
                progress.forEachIndexed { index, week ->
                    val text = progressRowText(
                        week = week,
                        weekTitle = stringResource(R.string.preset_week, week.week),
                        runsText = pluralStringResource(R.plurals.progress_runs, week.completedRuns, week.completedRuns),
                        labels = labels,
                        formatter = dateFormatter,
                    )
                    WeekRow(
                        week = week,
                        text = text,
                        segments = segments[index],
                        colors = colors,
                    )
                }
            }
        }
    }
}

/**
 * Строка ленты: колонка узла и карточка. [segments] — цвет отрезков линии выше и ниже узла.
 * Строка читается TalkBack одной фразой [ProgressRowText.description], узел, линия и деления
 * без семантики.
 */
@Composable
private fun WeekRow(
    week: WeekProgress,
    text: ProgressRowText,
    segments: LineSegments,
    colors: TimelineColors,
) {
    val current = week.current
    val titleStyle = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall
    val chipStyle = MaterialTheme.typography.labelMedium
    // Центр узла на середине строки заголовка: lineHeight в sp растёт с системным шрифтом.
    // Заголовок в одну строку, у «Сейчас» строка с плашкой высотой не меньше её текста
    val density = LocalDensity.current
    val titleLineHeight = with(density) { titleStyle.lineHeight.toDp() }
    val nodeCenter = if (current) {
        val chipHeight = max(NowChipHeight, with(density) { chipStyle.lineHeight.toDp() })
        CurrentCardTopPadding + max(titleLineHeight, chipHeight) / 2
    } else {
        PlainCardVerticalPadding + titleLineHeight / 2
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clearAndSetSemantics { contentDescription = text.description },
        horizontalArrangement = Arrangement.spacedBy(NodeGap),
    ) {
        NodeColumn(week, nodeCenter, segments, colors)
        if (current) {
            CurrentWeekCard(week, text, titleStyle, chipStyle, colors, Modifier.weight(1f))
        } else {
            PlainWeekCard(week, text, titleStyle, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NodeColumn(
    week: WeekProgress,
    nodeCenter: Dp,
    segments: LineSegments,
    colors: TimelineColors,
) {
    fun lineColor(filled: Boolean) = if (filled) colors.marker else colors.track

    Box(
        modifier = Modifier
            .width(NodeColumnWidth)
            .fillMaxHeight()
            .drawBehind {
                val x = size.width / 2
                val centerY = nodeCenter.toPx()
                val stroke = LineWidth.toPx()
                segments.aboveFilled?.let {
                    drawLine(lineColor(it), Offset(x, 0f), Offset(x, centerY), stroke)
                }
                segments.belowFilled?.let {
                    drawLine(lineColor(it), Offset(x, centerY), Offset(x, size.height), stroke)
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.padding(top = (nodeCenter - nodeSize(week.mark) / 2).coerceAtLeast(0.dp))) {
            WeekNode(week, colors)
        }
    }
}

/** Узел недели по [WeekMark]. Фон узла цвета фона окна закрывает линию под ним. */
@Composable
private fun WeekNode(week: WeekProgress, colors: TimelineColors) {
    val background = MaterialTheme.colorScheme.background
    val mark = week.mark

    when (mark) {
        WeekMark.PASSED, WeekMark.SKIPPED -> Box(
            modifier = Modifier
                .size(nodeSize(mark))
                .background(background, CircleShape)
                .padding(NodeBackgroundRing)
                .background(colors.marker, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (mark == WeekMark.PASSED) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(CheckIconSize),
                    tint = colors.check,
                )
            } else {
                // Прочерк пропущенной недели: минуса в material-icons-core нет
                Canvas(Modifier.size(DashCanvasSize)) {
                    drawLine(
                        color = colors.check,
                        start = Offset(size.width * DASH_START_FRACTION, size.height / 2),
                        end = Offset(size.width * DASH_END_FRACTION, size.height / 2),
                        strokeWidth = DashStroke.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        WeekMark.CURRENT -> CurrentNode(week.completedRuns, colors.marker, colors.track, background)
        WeekMark.AHEAD -> Box(
            modifier = Modifier
                .size(nodeSize(mark))
                .background(background, CircleShape)
                .border(AheadNodeStroke, if (week.completedRuns > 0) colors.marker else colors.track, CircleShape),
        )
    }
}

/**
 * Узел текущей недели: [RUNS_TO_PASS_WEEK] частей кольца закрашиваются по часовой стрелке сверху
 * по числу пробежек, дальше порога не растут; в центре точка.
 */
@Composable
private fun CurrentNode(completedRuns: Int, marker: Color, track: Color, background: Color) {
    Canvas(Modifier.size(nodeSize(WeekMark.CURRENT))) {
        drawCircle(background)
        val strokePx = CurrentRingStroke.toPx()
        val diameter = CurrentRingDiameter.toPx()
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val sweep = 360f / RUNS_TO_PASS_WEEK - RING_GAP_DEG
        for (part in 0 until RUNS_TO_PASS_WEEK) {
            drawArc(
                color = if (part < completedRuns) marker else track,
                startAngle = -90f + RING_GAP_DEG / 2 + part * 360f / RUNS_TO_PASS_WEEK,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = strokePx, cap = StrokeCap.Butt),
            )
        }
        drawCircle(marker, radius = CurrentDotRadius.toPx())
    }
}

/** Карточка «Сейчас»: заголовок с плашкой, параметры, деления, строка состояния и дата. */
@Composable
private fun CurrentWeekCard(
    week: WeekProgress,
    text: ProgressRowText,
    titleStyle: TextStyle,
    chipStyle: TextStyle,
    colors: TimelineColors,
    modifier: Modifier,
) {
    val status = checkNotNull(text.status) { "строка состояния есть у текущей недели" }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, CurrentCardShape)
            .padding(
                start = CardHorizontalPadding,
                top = CurrentCardTopPadding,
                end = CardHorizontalPadding,
                bottom = CurrentCardBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(CurrentCardSpacing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TitleChipSpacing),
        ) {
            Text(
                text = text.title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .heightIn(min = NowChipHeight)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .padding(horizontal = NowChipHorizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.progress_now),
                    style = chipStyle,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Text(
            text = text.details,
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            color = muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PipSpacing)) {
            for (part in 0 until RUNS_TO_PASS_WEEK) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(PipHeight)
                        .background(if (part < week.completedRuns) colors.marker else colors.track, CircleShape),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StatusDateSpacing)) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
            )
            text.date?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    color = muted,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/** Карточка остальных недель: заголовок и параметры слева, число пробежек или «—» и дата справа. */
@Composable
private fun PlainWeekCard(
    week: WeekProgress,
    text: ProgressRowText,
    titleStyle: TextStyle,
    modifier: Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val hasRuns = week.completedRuns > 0

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, PlainCardShape)
            .padding(horizontal = CardHorizontalPadding, vertical = PlainCardVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainCardSpacing),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PlainCardLineSpacing),
        ) {
            Text(
                text = text.title,
                style = titleStyle,
                color = if (week.passed) onSurface else muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = text.details,
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(PlainCardLineSpacing),
        ) {
            Text(
                text = text.runs,
                style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                color = if (hasRuns) onSurface else muted,
                maxLines = 1,
                softWrap = false,
            )
            text.date?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    color = muted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
