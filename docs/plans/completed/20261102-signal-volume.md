# Регулировка громкости сигналов

## Overview
- Задача из `docs/tasks/20261101-signal-volume.md`: своя регулировка громкости сигналов в приложении, которая не трогает системную громкость. Значение по умолчанию 100%.
- Сигналы переходят с канала будильника (`USAGE_ALARM`) на канал навигации (`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`): звучат вместе с музыкой, приглушают её на время сигнала и регулируются обычными кнопками громкости. Это выполняет критерии US-5, которых первая версия не закрывала.
- Итоговая громкость равна настройке приложения, умноженной на системную громкость канала. Регулировка только ослабляет звук.
- Решения, принятые при планировании:
  - диапазон ползунка 10–100%, а не 0–100%, как в задаче: состояния «тишина по настройке» нет, поэтому US-5 и US-6 нельзя нарушить случайно;
  - при нулевой системной громкости медиа тренировка запускается, а экран тренировки показывает предупреждение;
  - ползунок стоит на текущей форме, задача не зависит от плана паузы и вибрации.

## Context (from discovery)
- Файлы:
  - `Beeper.kt`: канал, `AudioTrack`, здесь появляются аудиофокус и громкость;
  - `Tones.kt`: длительности сигналов;
  - `WorkoutService.kt`: создаёт `Beeper`, держит `FINISH_TAIL_MS`, выставляет `soundFailed`;
  - `WorkoutState.kt`, `ui/ActiveContent.kt`: готовый путь предупреждения о звуке;
  - `SettingsStore.kt`, `MainActivity.kt`, `ui/WorkoutScreen.kt`, `ui/SettingsContent.kt`, `strings.xml`.
- Паттерны: логика в чистых функциях с unit-тестами, Android-код тонкий, цикл и методы сервиса работают в главном потоке.
- `minSdk = 26`, поэтому `AudioFocusRequest` доступен без веток по версии. Новых зависимостей нет.
- `FakePreferences` в `SettingsStoreTest` уже умеет `Int`, расширять его не нужно.
- План `docs/plans/20261101-pause-and-vibration.md` ещё не реализован и пересекается с этой задачей, см. «Связь с планом паузы».

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
- Код только с Android API (`Beeper`, сервис, Compose) unit-тестами не покрывается. Его логика вынесена в чистые функции с тестами, остальное проверяется сборкой и вручную на телефоне.

## Testing Strategy
- **unit tests**: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug` после каждой задачи.
- **e2e tests**: в проекте их нет, эмулятора и устройства в окружении нет.
- Звук, приглушение музыки и маршрут в наушники подтверждаются только на телефоне, см. Post-Completion. Сборка и тесты их не подтверждают.

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview

### Громкость
- Новый чистый файл `SignalVolume.kt`: пределы, прижатие к диапазону и перевод процентов в коэффициент для `AudioTrack.setVolume()`.
- Значение хранится отдельным ключом в `SettingsStore`, а не в `WorkoutSettings`: это настройка приложения, а не параметр тренировки, и план паузы так же хранит вибрацию. `validate()` и интент сервиса не меняются.
- Прижатие выполняется и при чтении, и при записи, поэтому повреждённое значение в хранилище не даст тишину.
- Сервис читает значение в `startWorkout`. Во время тренировки форма скрыта, поэтому менять громкость на лету не нужно.
- Шкала линейная: 10% соответствует примерно −20 дБ, этого достаточно, логарифмическая шкала не нужна.

### Канал и аудиофокус
- Фокусом владеет `Beeper`, потому что только он знает, когда звучит сигнал. Сервис и форма фокус не трогают, а «Стоп» уже проходит через `onDestroy` и `release()`.
- `play()` запрашивает фокус `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` и ставит отложенный возврат на длительность сигнала плюс `FINISH_TAIL_MS`. Новый `play()` снимает прежний таймер, поэтому запоздавший возврат не отпустит фокус посреди сигнала.
- На отрезках около 1 с музыка будет приглушаться и возвращаться каждую секунду: удержание `INTERVAL` равно 700 мс, и фокус отпускается раньше следующего сигнала. Это принято как крайний случай, более долгое удержание ухудшило бы обычные отрезки.
- Фокус запрашивается после проверки, что сигнал подготовлен, и перед `track.play()`: неподготовленный сигнал не должен приглушать музыку впустую.
- Отказ в фокусе не отменяет сигнал: потерянный сигнал опаснее неприглушённой музыки. Случай фиксируется в логе.
- `release()` снимает таймер и отпускает фокус сразу, поэтому музыка не остаётся приглушённой после «Стоп». Повторный вызов безопасен.
- Контракт сервиса не меняется: между сигналами `Beeper` сам возвращает фокус по таймеру, а окончательно ресурсы и фокус освобождает только `onDestroy`.

### Нулевая громкость медиа
- Цикл на каждом шаге читает `getStreamVolume(STREAM_MUSIC)`. Проверка не разовая: предупреждение исчезает, как только пользователь прибавит звук кнопками, и появляется, если звук убрали уже на бегу.
- Новое поле `WorkoutState.Active.soundMuted` идёт тем же путём, что `soundFailed`. Если сигналы не подготовлены, показывается `warning_no_sound`, потому что прибавление громкости тут не поможет.
- `STREAM_MUSIC` предполагает, что канал навигации следует громкости медиа. На большинстве телефонов это так, точное поведение проверяется в Post-Completion.
- Между сигналами ничего не играет, и на части устройств кнопки меняли бы громкость звонка. Поэтому `MainActivity.onCreate` задаёт `volumeControlStream = AudioManager.STREAM_MUSIC`. Настройка действует, пока activity на экране, поведение на заблокированном экране проверяется на телефоне.
- Предупреждение, появившееся посреди тренировки, сдвигает таймер в центрированной колонке. Случай редкий, место под строку не резервируем.

### Ползунок и пробный сигнал
- Ползунок стоит на форме под полем кругов: диапазон 10–100%, шаг 5%, подпись с текущим значением. Положение не переживает поворот: после пересоздания ползунок читает хранилище, иначе прерванный сдвиг показывал бы несохранённое значение. ➕ Ревью: заменено `rememberSaveable` на `remember`.
- Отпускание ползунка сохраняет значение сразу, не дожидаясь «Старт», и проигрывает `Signal.INTERVAL` с новой громкостью.
- Пробным сигналом управляет `MainActivity`: перед новым пробным сигналом отпускает прежний `Beeper`, создаёт новый с нужной громкостью, при нажатии «Старт» и в `onStop` отпускает последний. Так у `Beeper` остаётся один способ задать громкость, через конструктор, а лишние дорожки не живут всю тренировку.

### Связь с планом паузы
- План паузы тоже добавляет параметр в `tickAt` и строки в `SettingsContent`. Какая задача пойдёт второй, та учтёт изменения первой.
- У `tickAt` станет три булевых параметра подряд, поэтому `runLoop` передаёт `soundMuted` и `paused` по имени.
- План паузы оставляет `warning_no_sound` на месте, а здесь проверка заменяется на `soundWarningRes()`: сдвиг при реализации учитывается.
- Оба плана добавляют истории в `USER_STORIES.md`: каждый занимает следующие свободные номера, вторая задача учитывает номера первой.
- Если план паузы уже перенесён в `docs/plans/completed/`, Task 6 правит его там.
- Строка «Атрибуты `USAGE_ALARM`, как у звука» в его Technical Details устареет: у звука атрибуты станут другими. Вибрация остаётся на `USAGE_ALARM`, правится только обоснование.
- Перенести ползунок на отдельный экран можно будет заменой места вызова: значение лежит в `SettingsStore`, а не в `SettingsInput`.

### Связь с планом задержки старта
Задержка старта (`docs/plans/completed/20261102-start-delay.md`) реализована раньше этого плана.

- `play()` общий, поэтому аудиофокус и громкость `START` получает без изменений. Тест `focusHoldMs` «для обоих сигналов» проверяет три сигнала.
- Проверка нулевой громкости нужна и в `runCountdown`: в `Countdown` добавляется `soundMuted`, а `soundWarningRes()` работает для обоих состояний. Иначе бегун увидит предупреждение уже после неслышного `START`, с телефоном в кармане.
- Ползунок и новое поле добавляются в одно место формы.
- Номер US-9 занят задержкой старта, история о громкости получает следующий свободный номер.

## Technical Details

`SignalVolume.kt`:
```kotlin
const val MIN_VOLUME_PERCENT = 10
const val MAX_VOLUME_PERCENT = 100
const val DEFAULT_VOLUME_PERCENT = MAX_VOLUME_PERCENT
const val VOLUME_STEP_PERCENT = 5

fun clampVolume(percent: Int): Int = percent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
fun volumeGain(percent: Int): Float = percent / 100f

// Slider отдаёт Float: 34.999996 через toInt() дало бы 34 и разошлось с шагом
fun sliderToPercent(value: Float): Int = clampVolume(value.roundToInt())
```

`SettingsStore`:
- `loadSignalVolume(): Int` читает ключ `signal_volume`, по умолчанию `DEFAULT_VOLUME_PERCENT`, результат проходит `clampVolume`;
- `saveSignalVolume(percent: Int)` сохраняет `clampVolume(percent)`.

`Tones.kt`:
- сюда переезжает `FINISH_TAIL_MS` из `WorkoutService`: запас нужен и сервису, и `Beeper`;
- `fun focusHoldMs(signal: Signal): Long = soundDurationMs(signal) + FINISH_TAIL_MS`.

`Beeper`:
- конструктор `Beeper(audioManager: AudioManager, volumePercent: Int)`;
- атрибуты `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, `CONTENT_TYPE_SONIFICATION`, один экземпляр на дорожки и `AudioFocusRequest`;
- атрибуты объявляются в `companion object`: `tracks` заполняется при инициализации класса, и поле ниже него было бы ещё `null`. `AudioTrack.Builder` бросил бы исключение, `catch` в `createTrack` его поглотил бы, и сигналы пропали бы при зелёных тестах;
- `AudioFocusRequest` без слушателя: реагировать на смену фокуса незачем, сигнал длится доли секунды, а слушатель обязателен только для отложенного фокуса и `setWillPauseWhenDucked`;
- `track.setVolume(volumeGain(volumePercent))` в `createTrack` внутри существующего `try`, код возврата не `SUCCESS` пишется в лог;
- таймер на `Handler(Looper.getMainLooper())`, `play()` и `release()` вызываются только из главного потока.

`tickAt` и `countdownTickAt` получают параметр `soundMuted: Boolean = false` и передают его в `WorkoutState.Active` и `WorkoutState.Countdown`.

`ui/ActiveContent.kt`:
```kotlin
@StringRes
fun soundWarningRes(soundFailed: Boolean, soundMuted: Boolean): Int? = when {
    soundFailed -> R.string.warning_no_sound
    soundMuted -> R.string.warning_muted
    else -> null
}
```

Новые строки: `signal_volume` («Громкость сигналов: %1$d%%»), `warning_muted` («Громкость медиа на нуле, сигналов не будет слышно»).

Поток данных:
1. Форма: отпускание ползунка → `onVolumeChange(percent)` → `MainActivity` сохраняет значение и играет пробный сигнал.
2. Старт: `WorkoutService.startWorkout` → `Beeper(audioManager, store.loadSignalVolume())`.
3. Шаг цикла и отсчёта: `getStreamVolume(STREAM_MUSIC) == 0` → `tickAt(..., soundMuted)` или `countdownTickAt(..., soundMuted)` → `ActiveContent` или `CountdownContent`.
4. Завершение: `onDestroy` → `beeper.release()` → фокус отпущен. Между сигналами фокус возвращает таймер `Beeper`.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): изменения кода, тесты, документация.
- **Post-Completion** (no checkboxes): проверка на телефоне.

## Implementation Steps

### Task 1: Настройка громкости в хранилище

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/SignalVolume.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/SignalVolumeTest.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/SettingsStore.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/SettingsStoreTest.kt`

- [x] создать `SignalVolume.kt` с константами, `clampVolume`, `volumeGain` и `sliderToPercent`
- [x] написать тесты `sliderToPercent`: `34.999996f` и `35.00001f` дают 35, значения за границами прижимаются
- [x] добавить `loadSignalVolume()` и `saveSignalVolume()` в `SettingsStore`
- [x] написать тесты `clampVolume`: границы, значения за границами, `Int.MIN_VALUE` и `Int.MAX_VALUE`
- [x] написать тесты `volumeGain`: 10% и 100%
- [x] написать тесты `SettingsStore`: значение по умолчанию 100, сохранение и чтение, прижатие при записи, прижатие сохранённых 0 и 150 при чтении, независимость от `load()` и `save()`
- [x] запустить тесты, они должны пройти

### Task 2: Канал навигации, громкость и аудиофокус в Beeper

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/Tones.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/Beeper.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/TonesTest.kt`

- [x] перенести `FINISH_TAIL_MS` в `Tones.kt`, добавить `focusHoldMs`
- [x] перевести `Beeper` на `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, объявить атрибуты в `companion object`, обновить KDoc класса
- [x] применять `volumeGain` в `createTrack` через `AudioTrack.setVolume()`
- [x] запрашивать фокус в `play()`, возвращать его по таймеру, отпускать в `release()`
- [x] создавать `Beeper` в `WorkoutService.startWorkout` с `AudioManager` и значением из `SettingsStore`
- [x] написать тесты `focusHoldMs` для обоих сигналов: удержание длиннее звука на `FINISH_TAIL_MS`
- [x] запустить тесты, они должны пройти

### Task 3: Предупреждение о нулевой громкости медиа

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutState.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ActiveContent.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutStateTest.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/ui/SoundWarningTest.kt`

- [x] добавить `soundMuted` в `WorkoutState.Active` и параметр в `tickAt`
- [x] добавить `soundMuted` в `WorkoutState.Countdown` и параметр в `countdownTickAt`
- [x] читать `getStreamVolume(STREAM_MUSIC)` на каждом шаге `runLoop`, передавать `soundMuted` в `tickAt` по имени
- [x] читать `getStreamVolume(STREAM_MUSIC)` на каждом шаге `runCountdown`, передавать `soundMuted` в `countdownTickAt` по имени
- [x] добавить `soundWarningRes()`, вывести предупреждение в `ActiveContent` и `CountdownContent` вместо прямой проверки `soundFailed`, добавить строку `warning_muted`
- [x] написать тесты `tickAt`: `soundMuted` доходит до состояния, по умолчанию `false`, меняется между шагами без лишнего сигнала
- [x] написать тесты `countdownTickAt`: `soundMuted` доходит до состояния, по умолчанию `false`
- [x] написать тесты `soundWarningRes()`: нет предупреждения, каждое по отдельности, `soundFailed` важнее `soundMuted`
- [x] запустить тесты, они должны пройти

### Task 4: Ползунок и пробный сигнал

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/SignalVolume.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ssortia/runtimer/SignalVolumeTest.kt`

- [x] добавить в `SignalVolume.kt` чистую функцию `volumeSliderSteps()`: число промежуточных шагов `Slider` из констант
- [x] добавить в `SettingsContent` ползунок и подпись, параметры `initialVolume` и `onVolumeChange`, значение переводить через `sliderToPercent` и отдавать из `onValueChangeFinished`
- [x] передать `loadVolume` и `onVolumeChange` через `WorkoutScreen`, читать значение в ветке `Idle` рядом с `loadSettings`
- [x] в `MainActivity` сохранять значение и проигрывать пробный сигнал, отпускать `Beeper` при «Старт» и в `onStop`
- [x] задать `volumeControlStream = AudioManager.STREAM_MUSIC` в `MainActivity.onCreate`
- [x] написать тесты `volumeSliderSteps()`: для 10–100% с шагом 5 получается 17, значения ползунка совпадают с шагом
- [x] запустить тесты, они должны пройти

### Task 5: Проверка критериев приёмки
- [x] проверить по коду: все создания `Beeper` передают громкость, `USAGE_ALARM` не осталось в `Beeper.kt`
- [x] проверить по коду: контракт сервиса из `CLAUDE.md` не нарушен, окончательно ресурсы и фокус освобождает только `onDestroy`, между сигналами `Beeper` сам возвращает фокус по таймеру
- [x] запустить `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [x] запустить `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`

### Task 6: [Final] Обновить документацию

**Files:**
- Modify: `USER_STORIES.md`
- Modify: `README.md`
- Modify: `CLAUDE.md`
- Modify: `docs/plans/completed/20261101-first-version.md`
- Modify: `docs/plans/20261101-pause-and-vibration.md`
- Modify: `docs/tasks/20261101-signal-volume.md`

- [x] `USER_STORIES.md`: добавить историю о регулировке со следующим номером, критерии: диапазон 10–100%, пробный сигнал, системная громкость не меняется
- [x] `README.md`: обновить абзац «Звук» (канал навигации, приглушение, настройка в приложении, предупреждение), заменить ограничения про будильник, «Не беспокоить» и музыку, добавить ограничение про отрезки около 1 с, дополнить абзац «Настройки» (громкость сохраняется сразу) и таблицу файлов (`SignalVolume.kt`)
- [x] `CLAUDE.md`: добавить в «Неочевидное» про `Beeper`: между сигналами фокус возвращается по таймеру, окончательно его отпускает `release()` из `onDestroy`; атрибуты объявлены до `tracks`; итоговая громкость равна произведению настройки и системной громкости
- [x] `docs/plans/completed/20261101-first-version.md`: обновить решение о канале, строку US-5 в таблице и пункты ручной проверки про выключенный звук медиа, наушники и «Не беспокоить»
- [x] `docs/plans/20261101-pause-and-vibration.md`: исправить обоснование `USAGE_ALARM` для вибрации
- [x] `docs/tasks/20261101-signal-volume.md`: заменить диапазон на 10–100%, записать решения по тишине, отметить выполненные пункты
- [x] перенести этот план в `docs/plans/completed/`

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Проверка на телефоне:**
- Передвинуть ползунок: при отпускании звучит пробный сигнал с новой громкостью, системная громкость остаётся прежней.
- Выставить 10% и запустить тренировку: сигналы тише, но слышны. После перезапуска приложения значение сохранилось, а новая установка даёт 100%.
- Повторный сигнал на пониженной громкости звучит так же тихо, как первый: `reloadStaticData()` не сбрасывает `setVolume()`.
- Сигналы меняются кнопками громкости во время тренировки и следуют громкости медиа. Если на прошивке это не так, проверка `STREAM_MUSIC` указывает не на тот канал, и предупреждение нужно пересмотреть.
- Убрать громкость медиа на ноль: появляется предупреждение. Прибавить звук: предупреждение исчезает.
- В наушниках с музыкой: сигнал идёт только в наушники, музыка приглушается и возвращается к прежней громкости. На отрезках в 1 с она будет дёргаться раз в секунду: оценить, насколько это мешает.
- Bluetooth-наушники с музыкой: музыка не возвращается к прежней громкости до конца `INTERVAL` и `START`, запаса 300 мс хватает на задержку вывода. Результат записать в ограничения README.
- Bluetooth-наушники без музыки: `INTERVAL` слышен целиком. Первый тон длится 150 мс, а наушники могут просыпаться с задержкой, динамик телефона сигнал больше не дублирует. Результат записать в ограничения README.
- Кнопки громкости на заблокированном экране во время тренировки меняют громкость медиа.
- Нажать «Стоп» во время финального сигнала с музыкой: музыка сразу возвращается к прежней громкости.
- Проверить приложения, которые на запрос фокуса ставят музыку на паузу, а не приглушают: длительность паузы приемлема.
- Выключенный экран: сигналы звучат вовремя, фокус работает.
- Входящий звонок и режим «Не беспокоить»: записать фактическое поведение в ограничения README.
