# Пауза тренировки и вибрация

## Overview
- Два пункта из раздела «Вне первой версии» в `USER_STORIES.md`: пауза тренировки и вибрация вместе со звуковым сигналом.
- Пауза позволяет остановиться на светофоре или перевести дух, не теряя тренировку. Таймер замирает и продолжает с того же места.
- Вибрация дублирует сигнал там, где его не слышно: в наушниках, в шумном месте. Её можно выключить на новом отдельном экране настроек приложения.
- Решения, принятые при планировании:
  - пауза управляется только с экрана тренировки, в уведомлении кнопок нет (остаётся как в первой версии);
  - автоостановки долгой паузы нет, пауза длится до «Продолжить» или «Стоп»;
  - вибрация включена по умолчанию, переключатель находится на отдельном экране.

## Context (from discovery)
- Файлы:
  - `WorkoutService.kt`: цикл, wake lock, контракт сервиса;
  - `Workout.kt`, `WorkoutState.kt` (шаг цикла `tickAt`), `NotificationText.kt`: чистая логика;
  - `Beeper.kt`, `Tones.kt`: образец для разделения `Buzzer` и чистых паттернов;
  - `SettingsStore.kt`, `MainActivity.kt`, `ui/WorkoutScreen.kt`, `ui/ActiveContent.kt`, `ui/SettingsContent.kt`;
  - `AndroidManifest.xml`, `strings.xml`.
- Паттерны: логика в чистых функциях с unit-тестами, Android-код тонкий, подписи передаются параметрами. Цикл и методы сервиса работают в главном потоке, поэтому синхронизация не нужна.
- Новых зависимостей нет. `BackHandler` есть в `activity-compose`. Значки не нужны: переходы сделаны текстовыми кнопками, `material-icons` не подключаем.
- `SettingsStoreTest` использует фейк `FakePreferences`. Он хранит только `Int`, а `getBoolean` и `putBoolean` бросают исключение, поэтому фейк нужно расширить.
- `CLAUDE.md` (раздел «Архитектура») говорит, что навигация не используется, потому что экран один. Отдельный экран настроек это меняет: библиотеку навигации по-прежнему не подключаем, переход делается флагом, а строку в `CLAUDE.md` обновляем в Task 9.
- Завершение: ресурсы освобождает только `onDestroy`, финал вызывает `stopSelf()`, `job` после финала не обнуляется.
- Задача `docs/tasks/20261101-remove-last-walk.md` выполнена и изменила `totalMs`. С этим планом она не конфликтует: `WorkoutClock` получает `totalMs` параметром.

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
- Задачи только с Android API (сервис, Compose, `Vibrator`) не покрываются unit-тестами, как и в первой версии. Их логика вынесена в чистые классы с тестами, остальное проверяется сборкой и вручную на телефоне.

## Testing Strategy
- **unit tests**: `./gradlew test assembleDebug` после каждой задачи. `java` нет в `PATH`, на этой машине нужен `JAVA_HOME=~/.local/jdk-17`.
- **e2e tests**: в проекте их нет, эмулятора и устройства в окружении нет.
- Звук, вибрацию, паузу при выключенном экране и экран настроек можно подтвердить только на телефоне, см. Post-Completion.

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview

### Пауза
- Новый чистый класс `WorkoutClock` считает время тренировки за вычетом пауз. Время по-прежнему вычисляется от старта, а не по тикам, поэтому ошибка не накапливается (US-5).
- Цикл `runLoop` на паузе не останавливается, а продолжает тикать с замершим временем. Индекс отрезка не меняется, поэтому лишних сигналов нет, а граница отрезка обрабатывается после продолжения обычным образом. Исключение: если граница пройдена между последним тиком и командой паузы, первый тик с `paused = true` подаст `INTERVAL`. Это верно, граница действительно пройдена.
- Команды идут обычными интентами `ACTION_PAUSE` и `ACTION_RESUME` через `startService()`. Это не нарушает контракт: `startForeground` остаётся первым в `onStartCommand`, а остановка по-прежнему идёт только через `stopService()`.
- Команды явные, а не переключатель, и повторяться могут без вреда. Кнопка обновится на следующем тике, до 250 мс. Двойной тап внутри этого окна даст две одинаковые команды, а тап после него уже нажмёт «Продолжить» и снимет паузу. Блокировку кнопки заранее не добавляем: сначала проверяем на телефоне, см. Post-Completion.

### Вибрация
- `Buzzer` повторяет устройство `Beeper`: Android-обёртка над `Vibrator`, рядом чистые функции `vibrationFor(signal)` и `vibrationDurationMs(signal)`.
- Паттерны свои, а не производные от тонов: импульсы по 150 мс плохо чувствуются в кармане на бегу.
- Отдельный экран `AppSettingsContent` доступен только с формы, то есть вне тренировки. Поэтому значение читается один раз при старте.

## Technical Details

### WorkoutClock (`WorkoutClock.kt`)
```kotlin
class WorkoutClock(private val totalMs: Long, private val startedAt: Long) {
    val isPaused: Boolean
    fun elapsedAt(now: Long): Long       // время тренировки без пауз
    fun remainingAt(now: Long): Long     // не меньше нуля, нужно для таймаута wake lock
    fun pause(now: Long)                 // повторный вызов и вызов при elapsed >= totalMs ничего не меняют
    fun resume(now: Long)                // без паузы ничего не меняет
}
```
Время `now` приходит параметром, поэтому класс не зависит от `SystemClock`.

### Сервис
- `onStartCommand`: после `startForeground` ветка `if (job != null)` выполняет `handlePauseAction(intent?.action)`. Без активной тренировки команда не содержит параметров, `toWorkout()` вернёт null, и сработает существующий `stopSelf(startId)`. Новый путь завершения не нужен.
- `startForeground` сейчас всегда ставит заголовок `app_name`. Для команд паузы это нужно исправить: цикл обновляет уведомление только при смене текста, а на паузе текст не меняется, поэтому после повторной команды в шторке навсегда останется «RunTimer». В `onStartCommand` заголовок берётся из текущего состояния: `(state.value as? WorkoutState.Active)?.let(::textFor) ?: getString(R.string.app_name)`.
- `WorkoutClock` создаётся в `runLoop` после отсчёта, от `startedAt`, и хранится в поле `clock`. Команда может прийти раньше первого шага цикла, поэтому обращение идёт через `clock?.`.
- Пауза: `clock.pause(now)`, затем снятие wake lock, если часы действительно на паузе.
- Продолжение: если `clock.isPaused`, то `clock.resume(now)` и `wakeLock.acquire(clock.remainingAt(now) + WAKE_LOCK_MARGIN_MS)`.
- Почему нужен wake lock заново: таймаут рассчитан на длительность тренировки и истёк бы во время долгой паузы, а после неё сигналы стали бы опаздывать. На паузе процессору не над чем работать, замершему времени сон не вредит, а `delay` после пробуждения продолжится сам.
- Пауза в финальном хвосте игнорируется: часы уже дошли до `totalMs`, а `job` до `onDestroy` не обнуляется, поэтому команда попадёт в сервис. Иначе снятие wake lock могло бы растянуть ожидание конца сигнала.
- `onDestroy` дополняется вызовом `buzzer?.release()`. Окончательно ресурсы по-прежнему освобождает только он, единственное исключение: на паузе wake lock снимается временно. Это отражается в KDoc сервиса и в `CLAUDE.md`. Поле `clock` сбрасывать не нужно: экземпляр сервиса уничтожается.

### Состояние и уведомление
- `WorkoutState.Active` получает `paused: Boolean = false`, `tickAt` получает параметр `paused: Boolean = false` и передаёт его в состояние. Значения по умолчанию сохраняют существующие тесты.
- `notificationText` получает `paused` и `pausedLabel`, текст становится «Пауза · Бег · 1:23 · Повтор 1/7». Цикл сравнивает текст целиком, поэтому уведомление обновится само.

### Вибрация
```kotlin
fun vibrationFor(signal: Signal): LongArray = when (signal) {
    Signal.INTERVAL -> longArrayOf(0, 300, 150, 300)
    Signal.FINISH -> longArrayOf(0, 300, 100, 300, 100, 800)
}
```
- Формат `VibrationEffect.createWaveform(timings, -1)`: чётные элементы это ожидание, нечётные это вибрация.
- Атрибуты `USAGE_ALARM`. Звук с плана громкости сигналов идёт через канал навигации, но вибрации нужен канал будильника: с другими атрибутами система может глушить вибрацию при выключенном экране или в беззвучном режиме. Перегрузка `vibrate(VibrationEffect, AudioAttributes)` есть с API 26, на API 33 она устарела, но работает. Ветку с `VibrationAttributes` не добавляем.
- `Buzzer.play(signal)` ничего не возвращает, как и `Beeper.play`. Без вибромотора вызов ничего не делает.
- Цикл сейчас ждёт в конце `soundDurationMs(Signal.FINISH) + FINISH_TAIL_MS`. Финальная вибрация длиннее сигнала (1600 мс против 1260 мс), и `onDestroy` оборвала бы её, поэтому ждём `maxOf(soundDurationMs(Signal.FINISH), vibrationDurationMs(Signal.FINISH)) + FINISH_TAIL_MS`. При выключенной вибрации финал затянется на 340 мс, это незаметно и не стоит отдельной ветки.
- `Buzzer.release()` вызывает `cancel()`: «Стоп» прекращает вибрацию сразу (US-4).
- Сервис читает настройку из `SettingsStore` в `startWorkout` и при выключенной вибрации не создаёт `Buzzer`. Интент не меняется.

### Экран настроек и навигация
- `SettingsStore`: `loadVibration(): Boolean` (по умолчанию `true`) и `saveVibration(enabled)`. Ключ отдельный, в `WorkoutSettings` поле не добавляем: это настройка приложения, а не параметр тренировки.
- `ui/AppSettingsContent.kt`: строка «Вибрация» с `Switch` на всю ширину (`Modifier.toggleable`) и кнопка «Назад». Значение сохраняется сразу при переключении.
- `WorkoutScreen`, ветка `Idle`: флаг `showAppSettings` в `rememberSaveable`, чтобы экран пережил поворот, и `BackHandler` для системной кнопки «Назад». `AppSettingsContent` получает `Modifier.safeDrawingPadding()`, как и форма: приложение работает edge-to-edge.
- Форму нужно обернуть в `rememberSaveableStateHolder().SaveableStateProvider("form")`. Без него `rememberSaveable` в `SettingsContent` теряет значения при уходе из композиции, и несохранённые правки в полях пропадут при возврате с экрана настроек. Держатель создаётся внутри ветки `Idle`: после тренировки форма должна читаться из хранилища заново.
- Вход на экран: текстовая кнопка «Настройки» под кнопкой «Старт» в `SettingsContent`, новый параметр `onOpenSettings`.

### Экран тренировки
- Кнопки в `Row` равной ширины: «Стоп» слева, «Пауза»/«Продолжить» справа.
- `titleLarge` не подходит: в половине экрана шириной 360 dp при стандартных отступах кнопки «Продолжить» не помещается. Берём `titleMedium`, уменьшенный горизонтальный `contentPadding` и `maxLines = 1`.
- На паузе подпись отрезка меняется на «Бег · пауза», строка `segment_paused`. Новая строка текста сдвигала бы таймер.
- `ActiveContent` получает `onPause` и `onResume`, `MainActivity` отправляет интенты `WorkoutService.pauseIntent(context)` и `resumeIntent(context)` через `startService()` с тем же перехватом `IllegalStateException`, что и в `startWorkout()`.
- «Стоп» работает и на паузе: `stopService()` приводит к `onDestroy`, а `wakeLock.isHeld` там уже проверяется.
- Предупреждение `warning_no_sound` остаётся на месте: вибрация не заменяет сигнал.

### Связь с планом задержки старта
Задержка старта (`docs/plans/completed/20261102-start-delay.md`) реализована раньше этого плана. Заметка уточняет разделы выше, где они расходятся с ней.

- `vibrationFor` нужна ветка `Signal.START`: без неё `when` по `Signal` не скомпилируется.
- `START` подаётся отдельным вызовом, а не через `tick.signal`, поэтому рядом с `beeper?.play(Signal.START)` нужен и `buzzer?.play(Signal.START)`. Компилятор этого не поймает.
- Паузы на экране отсчёта нет, отсчёт останавливается кнопкой «Стоп». Ряд кнопок паузы меняет только нижний слот `TimerLayout` на `Active`.
- `WorkoutClock` создаётся после отсчёта, от `startedAt`, а не в начале `runLoop`: иначе прошедшее время до старта будет отрицательным.
- Номер US-9 занят задержкой старта, поэтому пауза и вибрация получают следующие свободные номера, а не US-9 и US-10.

### Связь с планом громкости сигналов
Громкость сигналов (`docs/plans/completed/20261102-signal-volume.md`) реализована раньше этого плана. Заметка уточняет разделы выше, где они расходятся с ней.

- `tickAt` уже получает `soundMuted`. С `paused` у него станет три булевых параметра подряд, поэтому `runLoop` передаёт `soundMuted` и `paused` по имени.
- Предупреждение выбирает `soundWarningRes(soundFailed, soundMuted)`, а не прямая проверка `warning_no_sound`. Его не трогаем: вибрация не заменяет сигнал.
- Ожидание после финала теперь `soundWithTailMs(Signal.FINISH)`. Большую из длительностей звука и вибрации плюс `FINISH_TAIL_MS` нужно собрать из `soundDurationMs` и `vibrationDurationMs`, как описано выше.
- Ползунок громкости стоит на форме, значение лежит в `SettingsStore` отдельным ключом. При появлении `AppSettingsContent` его можно перенести туда заменой места вызова.
- Номер US-10 занят громкостью сигналов.

## What Goes Where
- **Implementation Steps**: код, тесты и документация в этом репозитории.
- **Post-Completion**: ручная проверка на телефоне.

## Implementation Steps

### Task 1: Часы тренировки с паузой

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/WorkoutClock.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/WorkoutClockTest.kt`

- [x] создать `WorkoutClock` с `elapsedAt`, `remainingAt`, `pause`, `resume`, `isPaused`
- [x] написать тесты: время без пауз, замирание на паузе, продолжение с того же места, несколько пауз подряд
- [x] написать тесты граничных случаев: повторные `pause` и `resume`, `resume` без паузы, `pause` ровно при `elapsed == totalMs` и после конца тренировки, `remainingAt` не уходит ниже нуля
- [x] запустить тесты, они должны пройти перед следующей задачей

### Task 2: Пауза в состоянии и тексте уведомления

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutState.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/NotificationText.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ssortia/runtimer/NotificationTextTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutStateTest.kt`

- [x] добавить `paused: Boolean = false` в `WorkoutState.Active` и параметр `paused` в `tickAt`
- [x] добавить параметры `paused` и `pausedLabel` в `notificationText`, строку `notification_paused` и обновить `textFor` в сервисе (`paused = active.paused`), иначе проект не соберётся
- [x] обновить вызовы в существующих тестах и добавить тест текста на паузе
- [x] написать тесты `tickAt`: `paused` попадает в состояние, на паузе в том же отрезке нет сигнала, при смене индекса на паузе подаётся `INTERVAL`
- [x] запустить тесты, они должны пройти перед следующей задачей

### Task 3: Пауза в сервисе

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`

- [x] добавить `ACTION_PAUSE`, `ACTION_RESUME`, `pauseIntent()`, `resumeIntent()`
- [x] перевести `runLoop` на `WorkoutClock`, созданный после отсчёта от `startedAt`, и передавать `paused` в `tickAt`
- [x] добавить `handlePauseAction` в ветку `job != null`, не меняя порядок с `startForeground`
- [x] снимать wake lock на паузе и брать заново на `remainingAt + WAKE_LOCK_MARGIN_MS` при продолжении
- [x] брать заголовок для `startForeground` в `onStartCommand` из текущего состояния
- [x] обновить KDoc сервиса: на паузе wake lock снимается временно
- [x] тестов нет, код только с Android API: логика покрыта в Task 1 и Task 2
- [x] запустить `./gradlew test assembleDebug`, должно пройти перед следующей задачей

### Task 4: Кнопки паузы на экране тренировки

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ActiveContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] добавить строки `button_pause`, `button_resume`, `segment_paused`
- [x] сделать ряд из кнопок «Стоп» и «Пауза»/«Продолжить» по разделу «Экран тренировки»
- [x] показать «Бег · пауза» или «Ходьба · пауза» на паузе
- [x] передать `onPause` и `onResume` через `WorkoutScreen` в `MainActivity`, отправлять интенты через `startService()` с перехватом `IllegalStateException`
- [x] тестов нет, код только с Compose: контраст кнопок берётся из пары цветов, уже проверенной в `ThemeTest`
- [x] запустить `./gradlew test assembleDebug`, должно пройти перед следующей задачей

### Task 5: Паттерны вибрации и настройка

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/Vibration.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/VibrationTest.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/SettingsStore.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/SettingsStoreTest.kt`

- [x] создать `vibrationFor(signal)` с веткой `Signal.START` и `vibrationDurationMs(signal)`
- [x] добавить `loadVibration()` и `saveVibration()` в `SettingsStore`, по умолчанию `true`
- [x] написать тесты: паттерны начинаются без задержки, имеют чётную длину и заканчиваются вибрацией
- [x] написать тесты: финальный паттерн отличается от обычного, длительность считается верно
- [x] расширить `FakePreferences` на `Boolean` и написать тесты: по умолчанию `true`, сохранённое значение читается, настройка не задевает параметры тренировки
- [x] запустить тесты, они должны пройти перед следующей задачей

### Task 6: Buzzer и подключение к циклу

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/Buzzer.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [x] добавить разрешение `android.permission.VIBRATE`
- [x] создать `Buzzer` с `play(signal)` и `release()`, атрибуты `USAGE_ALARM`, без вибромотора `play` ничего не делает
- [x] создавать `Buzzer` в `startWorkout` только при включённой настройке, хранить в поле рядом с `beeper`
- [x] подавать вибрацию вместе со звуком, в конце ждать большую из длительностей плюс `FINISH_TAIL_MS`
- [x] добавить `buzzer?.play(Signal.START)` рядом с `beeper?.play(Signal.START)`: `START` идёт мимо `tick.signal`
- [x] вызывать `buzzer?.release()` в `onDestroy`
- [x] тестов нет, код только с Android API: логика покрыта в Task 5
- [x] запустить `./gradlew test assembleDebug`, должно пройти перед следующей задачей

### Task 7: Отдельный экран настроек

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ui/AppSettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] добавить строки `button_settings`, `button_back`, `setting_vibration`
- [x] создать `AppSettingsContent` со строкой-переключателем и кнопкой «Назад», с `safeDrawingPadding()`
- [x] добавить кнопку «Настройки» и параметр `onOpenSettings` в `SettingsContent`
- [x] добавить в ветку `Idle` флаг `showAppSettings`, `BackHandler` и `SaveableStateHolder` для формы
- [x] передать `loadVibration` и `saveVibration` из `MainActivity`
- [x] тестов нет, код только с Compose: сохранение значений формы при переходе проверяется вручную
- [x] запустить `./gradlew test assembleDebug`, должно пройти перед следующей задачей

### Task 8: Проверка критериев приёмки
- [x] проверить по коду каждый пункт Overview и раздел «Контракт сервиса» в `CLAUDE.md`
- [x] проверить граничные случаи: пауза в финальном хвосте, пауза на границе отрезка, «Стоп» на паузе, команда паузы без активной тренировки
- [x] запустить `./gradlew test assembleDebug`
- [x] запустить `./gradlew lintDebug`, новых предупреждений быть не должно, кроме устаревшей перегрузки `vibrate` и описанного в `CLAUDE.md` `InlinedApi`

### Task 9: [Final] Документация
- [ ] `USER_STORIES.md`: добавить истории о паузе и вибрации на следующие свободные номера с критериями приёмки, убрать оба пункта из «Вне первой версии»
- [ ] `README.md`: обновить абзацы «Звук», «Сон процессора», «Финал» (ожидание по большей из длительностей), «Настройки», раздел статуса, известные ограничения и таблицу файлов (`WorkoutClock.kt`, `Vibration.kt`, `Buzzer.kt`, `ui/AppSettingsContent.kt`), добавить паузу, вибрацию и экран настроек
- [ ] `CLAUDE.md`: переписать правило об освобождении ресурсов: окончательно освобождает только `onDestroy` (цикл, `Beeper`, `Buzzer`, wake lock), на паузе wake lock снимается временно
- [ ] `CLAUDE.md`: дополнить контракт сервиса: команды паузы идут через `startService()` и безопасны при повторе, заголовок `startForeground` берётся из состояния
- [ ] `CLAUDE.md`: обновить раздел «Архитектура»: добавить `WorkoutClock` и `Buzzer`, заменить довод «экран один» на описание перехода флагом без библиотеки навигации
- [ ] перенести этот план в `docs/plans/completed/`

## Post-Completion
*Проверка на телефоне, без чекбоксов: по сборке и тестам это подтвердить нельзя.*

**Пауза:**
- Пауза на 10 минут с заблокированным экраном: таймер не сдвинулся, после «Продолжить» сигналы идут вовремя до конца тренировки.
- Пауза дольше длительности тренировки: после продолжения wake lock работает, сигналы при выключенном экране не опаздывают.
- Прошивки с агрессивной экономией батареи могут убить процесс на долгой паузе. Тогда тренировка потеряется: это согласуется с принятым `START_NOT_STICKY`, но поведение стоит увидеть своими глазами.
- Текст уведомления меняется на паузе и после неё.
- Двойной тап по «Пауза» не снимает паузу случайно. Если снимает, добавляем в `ActiveContent` короткую блокировку кнопки после нажатия.

**Вибрация:**
- Вибрация ощущается в кармане на бегу и при заблокированном экране. Если импульсы слабые, меняем паттерны в `vibrationFor`.
- Работает в беззвучном режиме. На некоторых прошивках вибрацию будильника можно отключить в системных настройках, тогда приложение на это не повлияет.
- Финальная вибрация не обрезается. «Стоп» прекращает её сразу.
- Выключенный переключатель действует на следующую тренировку и сохраняется после перезапуска.

**Экран настроек:**
- Значения в полях формы сохраняются после перехода на экран настроек и обратно, в том числе с поворотом телефона.
- Системная кнопка «Назад» возвращает к форме, а не закрывает приложение.
- Кнопки «Стоп» и «Продолжить» помещаются на узком экране и при увеличенном шрифте.
