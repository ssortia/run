package com.ssortia.runtimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssortia.runtimer.wheelIndexOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val WheelRowHeight = 44.dp
private const val VISIBLE_ROWS = 3

/**
 * Барабан выбора числа: видны три строки, выбранная стоит в середине на подложке. Значение
 * фиксируется через [onSelect], когда прокрутка остановилась, а не на каждом кадре: иначе
 * форма пересчитывалась бы на лету и сохраняла промежуточные значения. Нажатие на соседнюю
 * строку прокручивает к ней. Барабан не закольцован, отступы сверху и снизу на одну строку
 * дают крайним значениям встать в середину.
 *
 * [value] может не совпадать ни с одним из [options] (неверное сохранённое значение): тогда
 * показывается ближайшее по [wheelIndexOf], а настройка не меняется, пока барабан не прокрутят.
 */
@Composable
fun TimeWheel(
    options: List<Int>,
    value: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    unitLabel: String,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
) {
    val selectedIndex = wheelIndexOf(options, value)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { WheelRowHeight.toPx() }
    // С отступом в одну строку строка с индексом firstVisibleItemIndex стоит в середине.
    val centerIndex by remember(listState, options) {
        derivedStateOf {
            val shift = if (listState.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
            (listState.firstVisibleItemIndex + shift).coerceIn(options.indices)
        }
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentValue by rememberUpdatedState(value)

    LaunchedEffect(listState, options) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            // Первое значение — состояние покоя при запуске, прокрутки ещё не было.
            .drop(1)
            .filter { !it }
            .collect {
                val picked = options[centerIndex]
                if (picked != options[wheelIndexOf(options, currentValue)]) currentOnSelect(picked)
            }
    }
    // Значение сменилось снаружи (выбрана неделя программы) или список перестроился после
    // выбора: барабан встаёт на выбранное, но не мешает идущей прокрутке.
    LaunchedEffect(selectedIndex, options) {
        if (!listState.isScrollInProgress && centerIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelRowHeight * VISIBLE_ROWS)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                    ),
            )
            LazyColumn(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                contentPadding = PaddingValues(vertical = WheelRowHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(options, key = { _, option -> option }) { index, option ->
                    val isCenter = index == centerIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WheelRowHeight)
                            .semantics { selected = isCenter }
                            .clickable(role = Role.Button) {
                                scope.launch { listState.animateScrollToItem(index) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = format(option),
                            style = (if (isCenter) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.bodyLarge).merge(TabularNumbers),
                            color = if (isCenter) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            text = unitLabel,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
