package com.ssortia.runtimer.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Развёрнутость и смещение слоя экрана тренировки. Смещение в пикселях, 0 — развёрнут, [heightPx] —
 * свёрнут. Палец меняет его синхронно ([dragBy]): следующее событие того же кадра считается уже от
 * нового смещения, а запущенная анимация при захвате останавливается. Анимация одна на слой, новая
 * отменяет прежнюю.
 *
 * Сохраняется только [expanded]: после поворота развёрнутый слой стоит на месте, а сворачивавшийся
 * в момент поворота снова развёрнут целиком ([expanded] ещё true).
 */
@Stable
class WorkoutSheetState internal constructor(
    initialExpanded: Boolean,
    private val scope: CoroutineScope,
) {
    /** true в начале разворачивания и false только после доезда сворачивания. */
    var expanded by mutableStateOf(initialExpanded)
        private set

    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** Высота области, путь слоя при сворачивании. Задаёт оболочка по своему размеру. */
    var heightPx by mutableFloatStateOf(0f)
        internal set

    internal var flingVelocityPx = 0f

    private var animation: Job? = null

    /**
     * Свёрнутый слой сначала встаёт под нижний край и составляется, затем выезжает. Из сворачивания
     * ([expanded] ещё true) он разворачивается от текущего смещения, без скачка вниз.
     */
    fun expand() {
        if (!expanded) {
            animation?.cancel()
            offsetPx = heightPx
            expanded = true
        }
        animateTo(0f)
    }

    /**
     * [expanded] = false только после доезда. Если сворачивание прервали разворачивание или палец,
     * анимация отменится, и false не выставится. Повторный вызов идемпотентен: цель та же.
     */
    fun collapse() {
        animateTo(heightPx) { expanded = false }
    }

    /** Финал или «Стоп»: слой исчезает сразу, без анимации. */
    fun reset() {
        animation?.cancel()
        expanded = false
    }

    /** Сдвигает слой пальцем на [deltaPx] и возвращает поглощённую часть. */
    fun dragBy(deltaPx: Float): Float {
        animation?.cancel()
        val old = offsetPx
        offsetPx = sheetDragTo(old, deltaPx, heightPx)
        return offsetPx - old
    }

    /** Отпускание: доехать вниз и свернуться или вернуться наверх. */
    fun settle(velocityPx: Float) {
        if (shouldCollapseSheet(offsetPx, heightPx, velocityPx, flingVelocityPx)) {
            collapse()
        } else {
            animateTo(0f)
        }
    }

    private fun animateTo(target: Float, onEnd: () -> Unit = {}) {
        animation?.cancel()
        animation = scope.launch {
            animate(
                initialValue = offsetPx,
                targetValue = target,
                animationSpec = tween(SHEET_ANIMATION_MS, easing = FastOutSlowInEasing),
            ) { value, _ ->
                // Кадр, уже отправленный отменённой анимации, не должен перебить палец или новую анимацию
                if (isActive) offsetPx = value
            }
            onEnd()
        }
    }

    /**
     * Прокручиваемая колонка TimerLayout перехватывает вертикальный жест и в портретной ориентации:
     * в foundation 1.7 verticalScroll начинает перетаскивание, даже когда прокручивать некуда, и
     * непрокрученный остаток уходит только по nested scroll, мимо draggable. Без этого соединения
     * свайп по цифрам времени не сворачивал бы экран. По образцу шторок M3. Решение при отпускании
     * принимается только в onPreFling: onPostFling вызывается после него в том же жесте, и второе
     * решение отменило бы первое.
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (sheetTakesPreScroll(available.y, offsetPx)) Offset(0f, dragBy(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (sheetTakesPostScroll(available.y, fromUser = source == NestedScrollSource.UserInput)) {
                Offset(0f, dragBy(available.y))
            } else {
                Offset.Zero
            }

        override suspend fun onPreFling(available: Velocity): Velocity =
            if (sheetTakesFling(offsetPx)) {
                settle(available.y)
                available
            } else {
                Velocity.Zero
            }
    }
}

/**
 * Состояние слоя тренировки. Живёт в MainActivity: по нему выбираются системные панели и значки
 * строки состояния, и источник должен быть один.
 */
@Composable
fun rememberWorkoutSheetState(initialExpanded: Boolean): WorkoutSheetState {
    val scope = rememberCoroutineScope()
    val sheet = rememberSaveable(
        saver = Saver(save = { it.expanded }, restore = { WorkoutSheetState(it, scope) }),
    ) {
        WorkoutSheetState(initialExpanded, scope)
    }
    val flingVelocityPx = with(LocalDensity.current) { SheetFlingVelocity.toPx() }
    SideEffect { sheet.flingVelocityPx = flingVelocityPx }
    return sheet
}

// Длительность разворачивания и сворачивания экрана тренировки
private const val SHEET_ANIMATION_MS = 280

// Скорость отпускания, с которой свайп считается флингом (в секунду), как у шторок M3
private val SheetFlingVelocity = 125.dp
