package com.ssortia.runtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.R
import com.ssortia.runtimer.RunRecord
import com.ssortia.runtimer.RunRowText
import com.ssortia.runtimer.groupRunsByMonth
import com.ssortia.runtimer.runFormatters
import com.ssortia.runtimer.runMonthTitle
import com.ssortia.runtimer.runRowText
import java.time.ZoneId

/**
 * Экран истории. [records] равен null, пока база не ответила или если чтение не удалось: тогда
 * экран пустой, иначе мелькнуло бы пустое состояние. В диалоге удаления хранится только `id`:
 * запись не `Parcelable`, а данные для диалога берутся из списка. Если записи в полученном
 * списке уже нет, `id` сбрасывается, чтобы диалог не всплыл позже.
 */
@Composable
fun HistoryContent(
    records: List<RunRecord>?,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val formatters = remember(zone, locale) { runFormatters(zone, locale) }
    val months = remember(records, zone) { records?.let { groupRunsByMonth(it, zone) } }

    LaunchedEffect(records) {
        val id = pendingDeleteId ?: return@LaunchedEffect
        if (records != null && records.none { it.id == id }) pendingDeleteId = null
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.history_title))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                months == null -> Unit
                months.isEmpty() -> EmptyHistory(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((index, month) in months.withIndex()) {
                        item(key = "month-${month.yearMonth}") {
                            SectionTitle(
                                text = runMonthTitle(month.yearMonth, formatters),
                                modifier = if (index > 0) Modifier.padding(top = SectionGroupGap) else Modifier,
                            )
                        }
                        items(month.records, key = { it.id }) { record ->
                            RunCard(
                                text = runRowText(record, formatters),
                                onDeleteClick = { pendingDeleteId = record.id },
                            )
                        }
                    }
                }
            }
        }
    }

    val pending = pendingDeleteId?.let { id -> records?.firstOrNull { it.id == id } }
    if (pending != null) {
        DeleteDialog(
            date = runRowText(pending, formatters).date,
            onConfirm = {
                pendingDeleteId = null
                onDelete(pending.id)
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun DeleteDialog(date: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_delete_title)) },
        text = { Text(stringResource(R.string.history_delete_text, date)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.button_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        },
    )
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            // Иконка украшение: текст ниже говорит то же самое
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val DayColumnWidth = 40.dp

/**
 * Карточка записи: день и день недели слева, бег и ходьба в две колонки, справа длительность
 * со значком статуса и корзина. Время в колонке выровнено по правому краю с моноширинными
 * цифрами, чтобы «2:00» и «13:00» стояли друг под другом. На узком экране или с крупным шрифтом
 * первыми сокращаются подписи «Бег» и «Ходьба» с многоточием, время остаётся целым.
 */
@Composable
private fun RunCard(text: RunRowText, onDeleteClick: () -> Unit) {
    // TalkBack должен назвать, какая запись удаляется, а не просто «Удалить»
    val deleteDescription = stringResource(R.string.history_delete_description, text.date)
    val numbers = MaterialTheme.typography.bodyLarge.merge(TabularNumbers)
    SectionCard(contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(DayColumnWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = text.day,
                    style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = text.weekday,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Колонка подписей измеряется после колонки времени и сжимается первой
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        SegmentLabel(stringResource(R.string.segment_run))
                        if (text.walkTime != null) SegmentLabel(stringResource(R.string.segment_walk))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = text.runTime, style = numbers, maxLines = 1, softWrap = false)
                        if (text.walkTime != null) {
                            Text(text = text.walkTime, style = numbers, maxLines = 1, softWrap = false)
                        }
                    }
                }
                Text(
                    text = text.meta,
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text.duration,
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                    maxLines = 1,
                    softWrap = false,
                )
                Icon(
                    imageVector = if (text.completed) Icons.Outlined.Check else Icons.Outlined.Close,
                    contentDescription = stringResource(
                        if (text.completed) R.string.history_completed else R.string.history_stopped,
                    ),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp),
                    tint = if (text.completed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = deleteDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
