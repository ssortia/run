# Задержка перед стартом тренировки

## Overview
- Задача из `docs/tasks/20261101-start-delay.md`: на форме можно указать, через сколько секунд после «Старт» начнётся таймер. За это время бегун убирает телефон в карман и готовится.
- Диапазон 0–60 секунд, по умолчанию 5. При 0 тренировка начинается сразу, как сейчас, без сигнала `START`.
- Обратный отсчёт идёт в `WorkoutService`, поэтому работает при заблокированном экране. Экран и уведомление показывают оставшиеся секунды, «Стоп» доступен сразу.
- Задержка не входит в итоговую длительность на форме и в прогресс тренировки.
- Конец задержки слышно: звучит новый сигнал `START`.
- Решения, принятые при планировании:
  - за всю задержку звучит только `START`, сигналов на последних секундах нет;
  - экран отсчёта использует цвета бега: новых цветов и проверок контраста не нужно, а бегун сразу видит цвет первого отрезка;
  - отсчёт описывается отдельным состоянием `WorkoutState.Countdown`, а не полями внутри `Active`. У `Active` всегда есть позиция и прогресс, у отсчёта их нет.

## Context (from discovery)
- Файлы:
  - `WorkoutSettings.kt`, `SettingsStore.kt`: настройки, проверка и хранение;
  - `ui/SettingsContent.kt`: `SettingsInput`, форма и сообщения об ошибках;
  - `WorkoutState.kt`: `WorkoutState`, `Signal`, `wholeSeconds`;
  - `Tones.kt`: состав сигналов;
  - `NotificationText.kt`: текст уведомления;
  - `WorkoutService.kt`: цикл, wake lock, разбор интента;
  - `MainActivity.kt`: `startWorkout()` собирает интент;
  - `ui/WorkoutScreen.kt`, `ui/ActiveContent.kt`, `ui/Theme.kt`: экраны и цвет значков строки состояния.
- В списке файлов задачи не хватает `ui/Theme.kt` с `ThemeTest.kt`: `when` в `darkStatusIcons` перебирает все состояния и без новой ветки не скомпилируется. Также не хватает `NotificationText.kt` с `NotificationTextTest.kt`, `Workout.kt` с `WorkoutTest.kt`, `SettingsInputTest.kt` и `CLAUDE.md`. В плане они учтены.
- `Beeper` создаёт дорожки по `Signal.entries`, поэтому новый сигнал подхватывается без изменений в нём. Если дорожка `START` не создастся, `isReady` станет false и сработает готовое предупреждение о звуке. Оно появится и при задержке 0, когда `START` не нужен: сбой редкий, отдельная ветка того не стоит.
- Паттерны: логика в чистых функциях с unit-тестами, подписи приходят параметрами, Android-код тонкий, цикл и методы сервиса работают в главном потоке.
- Новых зависимостей нет.
- Планы `20261101-pause-and-vibration.md` и `20261102-signal-volume.md` ещё не реализованы, см. «Связь с другими планами». План `20261102-remove-last-walk.md` уже реализован, этот план опирается на текущий код.

## Development Approach
- **testing approach**: Regular, сначала код, затем тесты в той же задаче.
- complete each task fully before moving to the next
- make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
  - write unit tests for new functions/methods
  - write unit tests for modified functions/methods
  - add new test cases for new code paths
  - update existing test cases if behavior changes
  - tests cover both success and error scenarios
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- run tests after each change
- Код только с Android API (сервис, Compose) unit-тестами не покрывается. Его логика вынесена в чистые функции с тестами, остальное проверяется сборкой и вручную на телефоне.

## Testing Strategy
- **unit tests**: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug` после каждой задачи.
- **e2e tests**: в проекте их нет, эмулятора и устройства в окружении нет.
- Звук `START`, отсчёт при выключенном экране и уведомление подтверждаются только на телефоне, см. Post-Completion. Сборка и тесты их не подтверждают.

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview

### Настройка
- `startDelaySec` лежит в `WorkoutSettings`: это параметр конкретного запуска, он проверяется `validate()` и сохраняется вместе с остальными полями.
- `Workout` не меняется. Он описывает только отрезки, поэтому `totalMs`, `at` и `progressAt` остаются прежними, и задержка не может попасть в итог или прогресс.
- Задержка идёт в сервис отдельным аргументом `startIntent`. Отсутствующий ключ в `SharedPreferences` даёт значение по умолчанию, то есть после обновления приложения задержка будет 5 секунд.
- Пустое поле считается нулём, как и остальные поля формы.

### Цикл сервиса
- Момент старта вычисляется один раз: `startedAt = now + delayMs`. И отсчёт, и тренировка считаются от него, поэтому шаги цикла и задержка не накапливают ошибку.
- Пока до старта остаётся время, цикл публикует `Countdown`. Ожидание равно `minOf(TICK_MS, остаток)`, поэтому `START` не опоздает на шаг цикла.
- После отсчёта звучит `START`, затем обычный цикл начинает с нулевого прошедшего времени. `tickAt` и `signalFor` не меняются: первый шаг сигнала не даёт, а при задержке 0 весь блок отсчёта пропускается.
- Завершение прежнее: `stopService()` приводит к `onDestroy`, который отменяет цикл, в том числе `delay` отсчёта. `job` заполнен с начала отсчёта, поэтому повторный старт отклоняется.
- Таймаут wake lock равен `workout.totalMs + delayMs + WAKE_LOCK_MARGIN_MS`.

### Интерфейс
- Поле «Задержка старта» стоит после повторов. `ImeAction.Done` переходит на него, поле повторов получает `Next`.
- Экран отсчёта: подпись «Старт через», крупные секунды, предупреждение о звуке при необходимости и «Стоп». Полосы прогресса нет, тренировка ещё не началась.
- Общая часть выносится в приватный `TimerLayout(colors, modifier, center, bottom)`: он задаёт фон, отступы и центрированную прокручиваемую область, а содержимое центра и нижней части передаётся слотами. Полоса прогресса и кнопка остаются у каждого экрана свои, поэтому план паузы сможет заменить кнопки только на `Active`.
- Правило «`START` только при задержке больше нуля» остаётся в `runLoop`, это одна проверка в Android-коде. Подтверждается чтением кода в Task 5 и на телефоне.

## Technical Details

`WorkoutSettings.kt`:
```kotlin
const val MAX_START_DELAY_SEC = 60

// поле WorkoutSettings
val startDelaySec: Int = 5

// в validate() рядом с другими диапазонами, раньше проверок на ноль
startDelaySec !in 0..MAX_START_DELAY_SEC -> SettingsError.START_DELAY_RANGE
```

`WorkoutState.kt`:
```kotlin
data class Countdown(val remainingMs: Long, val soundFailed: Boolean = false) : WorkoutState

enum class Signal { START, INTERVAL, FINISH }

/** Секунды округляются вверх: отсчёт идёт 5, 4, 3, 2, 1 и не показывает 0. */
fun countdownSeconds(remainingMs: Long): Long = wholeSeconds(remainingMs)
```

`Workout.kt`, рядом с `workoutOrNull`:
```kotlin
/** Возвращает null для отрицательной задержки: сервис такой запрос отклоняет. */
fun startDelayMsOrNull(delaySec: Int): Long? =
    if (delaySec >= 0) delaySec * 1_000L else null
```

`Tones.kt`: один длинный высокий тон, в отличие от двух коротких `INTERVAL` и трёх восходящих `FINISH`.
```kotlin
Signal.START -> listOf(Tone(1320, 600))
```

`NotificationText.kt`:
```kotlin
fun countdownNotificationText(
    remainingMs: Long,
    countdownLabel: String,
    secondsLabel: String,
): String = "$countdownLabel ${countdownSeconds(remainingMs)} $secondsLabel"
```

`WorkoutService.kt`:
```kotlin
private suspend fun runLoop(workout: Workout, delayMs: Long) {
    val startedAt = SystemClock.elapsedRealtime() + delayMs
    val soundFailed = beeper?.isReady != true
    if (delayMs > 0) {
        runCountdown(startedAt, soundFailed)
        beeper?.play(Signal.START)
    }
    // дальше прежний цикл
}

private suspend fun runCountdown(startedAt: Long, soundFailed: Boolean) {
    var shownText: String? = null
    while (true) {
        val remainingMs = startedAt - SystemClock.elapsedRealtime()
        if (remainingMs <= 0) break
        _state.value = WorkoutState.Countdown(remainingMs, soundFailed)
        // тот же приём со сравнением текста, что и в основном цикле
        val text = countdownNotificationText(
            remainingMs = remainingMs,
            countdownLabel = getString(R.string.countdown_label),
            secondsLabel = getString(R.string.seconds_short),
        )
        if (text != shownText) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
            shownText = text
        }
        delay(minOf(TICK_MS, remainingMs))
    }
}
```

Отрицательная задержка в `onStartCommand` обрабатывается как неверная тренировка: `stopSelf(startId)` и `START_NOT_STICKY`. `getIntExtra` читает с умолчанием `-1`, поэтому интент без значения тоже отклоняется. Сервис проверяет только нижнюю границу, как и `workoutOrNull`: верхнюю проверяет `validate()`.

Строки в `strings.xml`:
- `field_start_delay`: «Задержка старта, секунды»;
- `countdown_label`: «Старт через»;
- `seconds_short`: «с»;
- `error_start_delay_range`: «Задержка должна быть от 0 до %1$d секунд».

## Связь с другими планами
Какая задача пойдёт второй, та учтёт изменения первой. Планы паузы и громкости об отсчёте не знают, поэтому Task 6 добавляет в них заметки с пунктами ниже.

- **Пауза и вибрация.**
  - Паттерну вибрации нужна ветка `Signal.START`: без неё `when` по `Signal` не скомпилируется.
  - `START` подаётся отдельным вызовом, а не через `tick.signal`, поэтому рядом с `beeper?.play(Signal.START)` нужен и `buzzer?.play(Signal.START)`. Компилятор этого не поймает.
  - Паузы на экране отсчёта нет, отсчёт останавливается кнопкой «Стоп». Ряд кнопок паузы меняет только нижний слот `TimerLayout` на `Active`.
  - `WorkoutClock` создаётся после отсчёта, от `startedAt`, а не в начале `runLoop`: иначе прошедшее время до старта будет отрицательным.
  - План паузы занимает номера US-9 и US-10. Истории сдвигаются на следующие свободные номера.
- **Громкость сигналов.**
  - `play()` общий, поэтому аудиофокус и громкость `START` получает без изменений. Тест `focusHoldMs` «для обоих сигналов» проверяет три сигнала.
  - Проверка нулевой громкости нужна и в `runCountdown`: в `Countdown` добавляется `soundMuted`, а `soundWarningRes()` работает для обоих состояний. Иначе бегун увидит предупреждение уже после неслышного `START`, с телефоном в кармане.
  - Ползунок и новое поле добавляются в одно место формы.
- **История пробежек** (`docs/tasks/20261102-run-history.md`). «Стоп» во время отсчёта не должен создавать запись, а длительность считается от `startedAt`, который на время отсчёта лежит в будущем.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): tasks achievable within this codebase - code changes, tests, documentation updates
- **Post-Completion** (no checkboxes): items requiring external action - manual testing, changes in consuming projects, deployment configs, third-party verifications

## Implementation Steps

### Task 1: Настройка задержки и её хранение

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutSettings.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/SettingsStore.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutSettingsTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/SettingsStoreTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/SettingsInputTest.kt`

- [x] добавить `MAX_START_DELAY_SEC`, поле `startDelaySec` и ошибку `SettingsError.START_DELAY_RANGE`, проверять диапазон в `validate()` раньше проверок на ноль
- [x] сохранять и читать значение в `SettingsStore` с ключом `start_delay_sec`
- [x] добавить поле в `SettingsInput`, `toSettings`, `from` и `SettingsInputSaver`
- [x] добавить `messageRes` и `messageArgs` для новой ошибки, строки `error_start_delay_range` и `field_start_delay`
- [x] добавить поле на форму после повторов, `ImeAction.Done` перенести на него
- [x] написать тесты `WorkoutSettings`: значение по умолчанию 5, границы 0 и 60 верны, -1 и 61 дают ошибку, задержка не меняет `toWorkout().totalMs`
- [x] написать тесты `SettingsStore`: при отсутствии ключа возвращается 5; в тесте сохранения задать `startDelaySec` со значением, отличным от остальных полей
- [x] написать тесты `SettingsInput`: преобразование в обе стороны, пустое поле даёт 0, число больше `Int` даёт ошибку диапазона
- [x] обновить помощник `input()` и тест Saver в `SettingsInputTest.kt`: Saver сохраняет шестое поле
- [x] написать тесты `messageRes` и `messageArgs` для `START_DELAY_RANGE`
- [x] запустить тесты, они должны пройти

### Task 2: Сигнал START

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutState.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/Tones.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/TonesTest.kt`

- [x] добавить `Signal.START` и его тоны в `tonesFor`, обновить комментарий к функции
- [x] написать тесты: `START` отличается по составу от `INTERVAL` и `FINISH`, длительность равна сумме тонов и короче минимального отрезка в 1 секунду
- [x] запустить тесты, они должны пройти

### Task 3: Состояние и экран отсчёта

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutState.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/NotificationText.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ActiveContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/Theme.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutStateTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/NotificationTextTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/ThemeTest.kt`

- [x] добавить `WorkoutState.Countdown` и `countdownSeconds`
- [x] добавить `countdownNotificationText` и строки `countdown_label`, `seconds_short`
- [x] добавить ветку `Countdown` в `darkStatusIcons`, она совпадает со значками бега
- [x] вынести из `ActiveContent` приватный `TimerLayout` со слотами, добавить `CountdownContent` с подписью, секундами, предупреждением о звуке и «Стоп»
- [x] добавить ветку `Countdown` в `WorkoutScreen`
- [x] написать тесты `countdownSeconds`: 5000 даёт 5, 4001 даёт 5, 1 даёт 1
- [x] написать тесты текста уведомления и `darkStatusIcons` для `Countdown` в обеих темах
- [x] запустить тесты, они должны пройти

### Task 4: Отсчёт в сервисе

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/Workout.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutTest.kt`

- [x] добавить `startDelayMsOrNull` рядом с `workoutOrNull`
- [x] добавить `EXTRA_START_DELAY_SEC` и третий аргумент `startIntent`, передавать `settings.startDelaySec` из `MainActivity.startWorkout()`
- [x] в `onStartCommand` отклонять запрос с неверной задержкой так же, как неверную тренировку, `startForeground` остаётся первым
- [x] добавить `runCountdown`, считать `startedAt` один раз, подавать `START` только при задержке больше нуля
- [x] увеличить таймаут wake lock на длительность задержки
- [x] обновить KDoc сервиса: «Стоп» завершает его и во время отсчёта
- [x] написать тесты `startDelayMsOrNull`: 0, 60 и -1
- [x] запустить тесты, они должны пройти

### Task 5: Verify acceptance criteria
- [x] проверить по коду, что все требования из Overview выполнены
- [x] проверить по коду, что при «Стоп» во время отсчёта `onDestroy` отменяет цикл, снимает уведомление и новых сигналов нет
- [x] проверить по коду, что при задержке 0 `Countdown` не публикуется и `START` не звучит
- [x] запустить `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [x] запустить `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`

### Task 6: [Final] Обновить документацию

**Files:**
- Modify: `USER_STORIES.md`
- Modify: `README.md`
- Modify: `CLAUDE.md`
- Modify: `docs/tasks/20261101-start-delay.md`
- Modify: `docs/plans/20261101-pause-and-vibration.md`
- Modify: `docs/plans/20261102-signal-volume.md`

- [ ] добавить в `USER_STORIES.md` историю о задержке на следующий свободный номер, дополнить US-2 и US-3 диапазоном и значением по умолчанию, уточнить в US-4, что тренировка начинается после задержки
- [ ] обновить в `README.md` таймаут wake lock, состав сигналов, значения формы и таблицу файлов; незакоммиченную правку про `build-apk.sh` сохранить
- [ ] в контракте сервиса в `CLAUDE.md` распространить правило публикации `Active` на `Countdown`
- [ ] добавить в планы паузы и громкости заметки из раздела «Связь с другими планами»; если какой-то из них уже реализован, вместо заметки внести правки в код в рамках этого плана
- [ ] отметить пункты задачи в `docs/tasks/20261101-start-delay.md`, кроме проверки на телефоне
- [ ] перенести этот план в `docs/plans/completed/` (`mkdir -p`, каталога ещё нет)

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Проверка на телефоне:**
- нажать «Старт», заблокировать экран, убрать телефон в карман: `START` и первая смена отрезка звучат вовремя;
- на слух `START` отличается от `INTERVAL` и `FINISH`, и его слышно в кармане;
- нажать «Стоп» во время отсчёта, в том числе из заблокированного экрана через приложение: сигналов больше нет, уведомление исчезло, форма на экране;
- при задержке 0 тренировка начинается сразу и без сигнала;
- повернуть телефон и закрыть экран приложения во время отсчёта: при возвращении виден текущий отсчёт;
- при задержке 60 секунд отсчёт не останавливается при выключенном экране;
- первое нажатие «Старт» на Android 13+ с запросом разрешения: отсчёт начинается только после ответа в диалоге.
