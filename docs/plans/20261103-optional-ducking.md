# Отключаемое приглушение музыки перед сигналом

## Overview
- Сейчас сервис всегда берёт аудиофокус за `DUCK_AHEAD_MS` (1 с) до каждого сигнала: перед сменой отрезка, перед финалом и в конце отсчёта перед стартом. На коротких отрезках музыка из-за этого почти всё время тише, а приложения, которые на запрос фокуса ставят музыку на паузу, молчат лишнюю секунду перед сигналом.
- План добавляет настройку приложения «Приглушать музыку заранее». По умолчанию она включена, то есть приложение работает как сейчас. Выключенная настройка отменяет только приглушение заранее: сигнал по-прежнему берёт фокус в момент звучания (`Beeper.play`).
- Настройка хранится отдельным ключом в `SettingsStore`. Сервис читает её в `startWorkout`, как вибрацию, поэтому она начинает действовать со следующей тренировки.
- Исходная задача: `docs/tasks/20261103-optional-ducking.md`.

## Context (from discovery)
- `SignalSound.kt`: `DUCK_AHEAD_MS`, `shouldDuck(msToSignal, paused = false)`, `shouldDuckAfterResume(workout, elapsedMs)`.
- `WorkoutService.kt`: решения о приглушении принимаются в трёх местах:
  - `runLoop` (`shouldDuck(..., paused = clock.isPaused)` → `duck()`/`stopDucking()`);
  - `runCountdown` (`shouldDuck(startedAt - now)` → `duck()`);
  - `handlePauseAction`, ветка `ACTION_RESUME` (`shouldDuckAfterResume` → `duck()`).
  Последнее место работает вне `runLoop`, поэтому сервису нужно поле с настройкой. В задаче оно не упомянуто.
- Образец сквозного проброса настройки даёт вибрация:
  - `SettingsStore.loadVibration/saveVibration` и `KEY_VIBRATION`, а `DEFAULT_VIBRATION` в `Vibration.kt`;
  - `MainActivity` → `WorkoutScreen` (`loadVibration`, `onVibrationChange`) → `AppSettingsContent`: строка с `toggleable` и `Switch(onCheckedChange = null)`;
  - строка `setting_vibration`;
  - три теста в `SettingsStoreTest`.
- Документация: `README.md:24` (звук), `README.md:27` (настройки), `README.md:154-155` («Известные ограничения»), `USER_STORIES.md:52`, `CLAUDE.md` (список настроек приложения в «Соглашениях», абзац про `duck()` в «Неочевидном»).

## Development Approach
- **testing approach**: Regular (сначала код, потом тесты)
- задачи выполняются по одной, следующая начинается только после завершения предыдущей
- изменения маленькие и сфокусированные
- **CRITICAL: каждая задача с изменением кода включает новые или обновлённые тесты**
- **CRITICAL: все тесты проходят до начала следующей задачи**
- **CRITICAL: при изменении объёма работ план обновляется**
- код с Android API (сервис, Compose) тестами не покрыт, это соответствует соглашению проекта. Его правильность проверяется сборкой, lint и ручной проверкой на телефоне.

## Testing Strategy
- **unit tests**: `SignalSoundTest` (`shouldDuck` и `shouldDuckAfterResume` с выключенной настройкой, обновление существующих вызовов) и `SettingsStoreTest` (значение по умолчанию, запись и чтение, независимость от других настроек).
- **e2e tests**: в проекте их нет.

## Progress Tracking
- выполненный пункт сразу отмечается `[x]`
- новые задачи добавляются с префиксом ➕
- проблемы и блокеры отмечаются префиксом ⚠️

## Solution Overview
- Решение о приглушении остаётся в чистых функциях. `shouldDuck` и `shouldDuckAfterResume` получают **обязательный** параметр `duckAhead: Boolean`. При `false` обе функции всегда возвращают `false`. Обязательность выбрана намеренно: компилятор заставит передать настройку во всех трёх местах сервиса, и новый вызов без настройки не пройдёт незамеченным. В `shouldDuck` после параметра идёт ещё один булев `paused`, поэтому `duckAhead` везде, и в сервисе, и в тестах, передаётся именованным аргументом: иначе `shouldDuck(x, clock.isPaused)` скомпилировался бы и молча перепутал смысл параметров.
- Сервис читает настройку в `startWorkout` в поле `duckAhead` рядом с `beeper` и `buzzer`. Это поле используют `runLoop`, `runCountdown` и `handlePauseAction`.
- Остальной код `Beeper` и сервиса не меняется. При выключенной настройке ветка `else` в `runLoop` вызывает `stopDucking()` на каждом шаге, а он без взятого фокуса ничего не делает. Фокус на время сигнала по-прежнему берёт и возвращает `play()`.

## Technical Details
- `SignalSound.kt`:
  - `const val DEFAULT_DUCK_AHEAD = true` рядом с `DUCK_AHEAD_MS`, по образцу `DEFAULT_VIBRATION`;
  - `fun shouldDuck(msToSignal: Long, duckAhead: Boolean, paused: Boolean = false): Boolean = duckAhead && !paused && msToSignal <= DUCK_AHEAD_MS`;
  - `fun shouldDuckAfterResume(workout: Workout, elapsedMs: Long, duckAhead: Boolean): Boolean`, которая передаёт `duckAhead` в `shouldDuck`.
- `SettingsStore`: ключ `KEY_DUCK_AHEAD = "duck_ahead"`, методы `loadDuckAhead(): Boolean` (по умолчанию `DEFAULT_DUCK_AHEAD`) и `saveDuckAhead(enabled: Boolean)`.
- `WorkoutService`: поле `private var duckAhead = DEFAULT_DUCK_AHEAD`, в `startWorkout` к нему добавляется `duckAhead = settings.loadDuckAhead()`. Вызовы:
  - `shouldDuck(remainingMs, duckAhead = duckAhead, paused = clock.isPaused)`;
  - `shouldDuck(startedAt - now, duckAhead = duckAhead)`;
  - `shouldDuckAfterResume(workout, elapsed, duckAhead = duckAhead)`.
- UI:
  - параметры `loadDuckAhead: () -> Boolean` и `onDuckAheadChange: (Boolean) -> Unit` в `WorkoutScreen` и `AppSettingsContent`;
  - в `MainActivity` они подключаются как `store::loadDuckAhead` и `store::saveDuckAhead`;
  - переключатель стоит сразу под вибрацией, это строка с `toggleable` по её образцу;
  - строка `setting_duck_ahead` = «Приглушать музыку заранее».

## What Goes Where
- **Implementation Steps**: код, тесты и документация в репозитории.
- **Post-Completion**: ручная проверка на телефоне.

## Implementation Steps

### Task 1: Хранение настройки в `SettingsStore`

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/SignalSound.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/SettingsStore.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/SettingsStoreTest.kt`

- [x] добавить `DEFAULT_DUCK_AHEAD` в `SignalSound.kt`, а `KEY_DUCK_AHEAD`, `loadDuckAhead()` и `saveDuckAhead()` в `SettingsStore` по образцу вибрации
- [x] написать тест: по умолчанию настройка включена (`DEFAULT_DUCK_AHEAD` и `loadDuckAhead()`)
- [x] написать тест: сохранённое значение читается обратно, в том числе новым экземпляром `SettingsStore`
- [x] написать тест: настройка не зависит от настроек тренировки, громкости, вибрации и задержки старта и сама их не меняет
- [x] запустить `JAVA_HOME=~/.local/jdk-17 ./gradlew testDebugUnitTest`, тесты должны пройти

### Task 2: Признак настройки в чистых функциях приглушения и в сервисе

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/SignalSound.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/SignalSoundTest.kt`

- [x] добавить обязательный параметр `duckAhead` в `shouldDuck` и `shouldDuckAfterResume` и обновить KDoc
- [x] добавить в `WorkoutService` поле `duckAhead` с комментарием, почему это поле, а не локальная переменная (им пользуется `handlePauseAction` вне `runLoop`), и заполнять его в `startWorkout` из `settings.loadDuckAhead()`
- [x] передавать поле в `shouldDuck` в `runLoop` и `runCountdown` и в `shouldDuckAfterResume` в `handlePauseAction`, везде именованным аргументом `duckAhead = duckAhead`
- [x] обновить существующие тесты `SignalSoundTest` под новый параметр (`duckAhead = true`, именованным аргументом)
- [x] написать тесты: при `duckAhead = false` `shouldDuck` возвращает `false` для 0, 1 и 1 000 мс, в том числе без паузы; `shouldDuckAfterResume` возвращает `false` в точках, где при включённой настройке был бы `true`
- [x] запустить `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`, сборка и тесты должны пройти

### Task 3: Переключатель на экране настроек

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/AppSettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] добавить строку `setting_duck_ahead`
- [ ] добавить в `AppSettingsContent` параметры `loadDuckAhead` и `onDuckAheadChange` и строку переключателя под вибрацией (`toggleable`, `Role.Switch`, `Switch(onCheckedChange = null)`)
- [ ] пробросить параметры через `WorkoutScreen` в маршрут `app_settings` и подключить в `MainActivity` к `store::loadDuckAhead` и `store::saveDuckAhead`
- [ ] тестов нет: это Compose, а хранение покрыто в Task 1
- [ ] запустить `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug lintDebug`, всё должно пройти без новых предупреждений

### Task 4: Verify acceptance criteria
- [ ] проверить, что включённая настройка сохраняет текущее поведение во всех трёх местах (цикл, отсчёт, продолжение после паузы)
- [ ] проверить, что выключенная настройка не вызывает `duck()` ни в одном из трёх мест, а `play()` по-прежнему берёт фокус
- [ ] проверить, что настройки нет ни в `WorkoutSettings`, ни в интенте
- [ ] запустить полный набор: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug lintDebug`

### Task 5: [Final] Update documentation
- [ ] `USER_STORIES.md`, строка 52: критерий «Музыка начинает приглушаться раньше, чем начинается звук сигнала» сделать условным («если на экране настроек включено „Приглушать музыку заранее“…»); добавить, что по умолчанию переключатель включён, при выключенном музыка приглушается только на время сигнала, и что он действует со следующей тренировки
- [ ] `README.md`, описание звука: приглушение заранее отключается настройкой
- [ ] `README.md`, таблица файлов: в строке `ui/AppSettingsContent.kt` добавить переключатель приглушения
- [ ] `README.md`, раздел «Настройки»: переключатель, значение по умолчанию, сохранение сразу при переключении
- [ ] `README.md`, «Известные ограничения»:
  - без приглушения заранее первые доли секунды сигнала звучат на фоне почти полной громкости музыки, тишина перед сигналом не добавляется, чтобы не сдвигать его;
  - пункты о приглушении на 1,7 с и о паузе музыки на время сигнала уточнить для выключенной настройки
- [ ] `CLAUDE.md`:
  - добавить настройку в список настроек приложения в «Соглашениях»;
  - в абзаце про `duck()` отметить, что `shouldDuck` и `shouldDuckAfterResume` получают обязательный `duckAhead`, а сервис читает его в `startWorkout` в поле, потому что его использует и `handlePauseAction`
- [ ] перенести `docs/tasks/20261103-optional-ducking.md` в `docs/tasks/completed/` (осталась только ручная проверка)
- [ ] перенести этот план в `docs/plans/completed/`

## Post-Completion
*Требует ручных действий, без чекбоксов*

**Ручная проверка на телефоне** (с музыкой в фоне):
- настройка включена: музыка приглушается за секунду до смены отрезка, до финала и до старта после отсчёта; после паузы перед самым сигналом музыка приглушается сразу при продолжении;
- настройка выключена: музыка приглушается только на время сигнала, в начале сигнала слышен короткий перехлёст с музыкой;
- значение переключателя сохраняется после перезапуска приложения;
- переключение во время тренировки на неё не влияет, новое значение действует со следующей тренировки (переключатель доступен только в `Idle`, поэтому для проверки достаточно двух тренировок подряд).
