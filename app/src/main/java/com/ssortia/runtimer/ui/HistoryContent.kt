package com.ssortia.runtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.R
import com.ssortia.runtimer.RunRecord
import com.ssortia.runtimer.RunRowText
import com.ssortia.runtimer.runDateFormatter
import com.ssortia.runtimer.runDateText
import com.ssortia.runtimer.runRowText
import java.time.ZoneId

/**
 * Экран истории. [records] равен null, пока база не ответила или если чтение не удалось: тогда
 * список не показывается, иначе мелькнуло бы пустое состояние. В диалоге удаления хранится
 * только `id`: запись не `Parcelable`, а данные для диалога берутся из списка. Если записи
 * в полученном списке уже нет, `id` сбрасывается, чтобы диалог не всплыл позже.
 */
@Composable
fun HistoryContent(
    records: List<RunRecord>?,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val dateFormatter = remember(zone, locale) { runDateFormatter(zone, locale) }
    val runLabel = stringResource(R.string.segment_run)
    val walkLabel = stringResource(R.string.segment_walk)
    val roundsLabel = stringResource(R.string.field_rounds)
    val completedLabel = stringResource(R.string.history_completed)
    val stoppedLabel = stringResource(R.string.history_stopped)
    val rowText = { record: RunRecord ->
        runRowText(record, dateFormatter, runLabel, walkLabel, roundsLabel, completedLabel, stoppedLabel)
    }

    LaunchedEffect(records) {
        val id = pendingDeleteId ?: return@LaunchedEffect
        if (records != null && records.none { it.id == id }) pendingDeleteId = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                records == null -> Unit
                records.isEmpty() -> Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(records, key = { it.id }) { record ->
                        RunRow(
                            text = rowText(record),
                            onDeleteClick = { pendingDeleteId = record.id },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        TextButton(onClick = onBack) {
            Text(
                text = stringResource(R.string.button_back),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    val pending = pendingDeleteId?.let { id -> records?.firstOrNull { it.id == id } }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_text, runDateText(pending.startedAtMs, dateFormatter))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        onDelete(pending.id)
                    },
                ) { Text(stringResource(R.string.button_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.button_cancel))
                }
            },
        )
    }
}

@Composable
private fun RunRow(text: RunRowText, onDeleteClick: () -> Unit) {
    // TalkBack должен назвать, какая запись удаляется, а не просто «Удалить»
    val deleteDescription = stringResource(R.string.history_delete_description, text.date)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = text.date, style = MaterialTheme.typography.titleMedium)
            Text(text = text.details, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(
            onClick = onDeleteClick,
            modifier = Modifier.semantics { contentDescription = deleteDescription },
        ) { Text(stringResource(R.string.button_delete)) }
    }
}
