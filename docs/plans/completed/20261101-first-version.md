# Первая версия RunTimer

## Overview
- Android-приложение: интервальный таймер для бега с чередованием бега и ходьбы.
- Решает задачу тренировки без взгляда на телефон: сигнал при смене отрезка и отдельный сигнал в конце, в том числе при заблокированном экране.
- Длительность отрезков и число повторов настраиваются.
- Объём версии: US-1…US-8 из `USER_STORIES.md`. Пауза, вибрация, шаблоны, GPS и история в план не входят.

## Context (from discovery)
- Проект: Kotlin, Jetpack Compose, minSdk 26, targetSdk 35. Сборка из командной строки, Android Studio не нужна.
- Уже есть: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `AndroidManifest.xml`, `Workout.kt`, `README.md`, `USER_STORIES.md`.
- Паттерны: логика в чистых Kotlin-классах без Android API, комментарии на русском, KDoc только там, где поведение неочевидно.
- На машине нет JDK и Android SDK, папка не под git.
- ⚠️ Строка выше описывает состояние до реализации. JDK и SDK установлены в Task 1, репозиторий к тому моменту уже был, поэтому `git init` пропущен.
- Зависимости: Compose BOM, Material 3, `activity-compose`, `lifecycle-runtime-compose`, JUnit 4.

## Development Approach
- **testing approach**: Regular, сначала код, затем тесты в той же задаче.
- complete each task fully before moving to the next
- make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
  - tests are not optional - they are a required part of the checklist
  - write unit tests for new functions/methods
  - write unit tests for modified functions/methods
  - add new test cases for new code paths
  - update existing test cases if behavior changes
  - tests cover both success and error scenarios
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- run tests after each change
- Задачи только с Android API (сервис, Compose, `AudioTrack`) не покрываются unit-тестами. Их логика выносится в чистые функции с тестами, остальное проверяется сборкой и вручную на телефоне.

## Testing Strategy
- **unit tests**: JUnit 4, `./gradlew test`. Покрывают `Workout`, настройки и валидацию, `SettingsStore` через фейк `SharedPreferences`, шаг цикла `tickAt()`, проверку параметров сервиса `workoutOrNull()`, форматирование времени, выбор сигнала, синтез тонов, расчёт прогресса.
- **e2e tests**: не используются. Эмулятора и устройства в окружении нет, а главные риски (звук, работа при выключенном экране) инструментальные тесты всё равно не закрывают. Вместо них есть ручной сценарий в Post-Completion.

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview
Три слоя с однонаправленным потоком данных:

- **UI**: `MainActivity` и Compose-экраны отправляют команды старт/стоп и отображают состояние.
- **Сервис**: `WorkoutService` (foreground service + partial wake lock) ведёт тренировку и публикует `StateFlow<WorkoutState>`.
- **Логика и звук**: `Workout` считает позицию, `Beeper` проигрывает сигналы.

Ключевые решения:
- Нативное приложение, а не PWA: при выключенном экране браузер усыпляет JS-таймеры.
- Позиция вычисляется из времени от старта (`SystemClock.elapsedRealtime`), поэтому ошибка не накапливается.
- Сигналы генерируются в коде и идут через канал будильника (`USAGE_ALARM`), поэтому не зависят от громкости медиа.
- Состояние живёт в сервисе, поэтому экран можно закрыть и открыть заново.
- ViewModel, DI, навигация и модули не вводятся: экран один, сервис один.

Оформление: цвет фона зависит от отрезка (бег и ходьба различаются издалека), общий прогресс тренировки, своя иконка, светлая и тёмная темы.

## Technical Details

### Данные
```kotlin
// Настройки как их вводит пользователь
data class WorkoutSettings(
    val runMin: Int = 2, val runSec: Int = 0,
    val walkMin: Int = 2, val walkSec: Int = 0,
    val rounds: Int = 7,
) {
    fun validate(): SettingsError?   // null, если всё верно
    fun toWorkout(): Workout         // вызывается только после успешной validate()
}

enum class SettingsError { RUN_ZERO, WALK_ZERO, ROUNDS_RANGE, SECONDS_RANGE, MINUTES_RANGE }

sealed interface WorkoutState {
    data object Idle : WorkoutState
    data class Active(
        val position: Position,
        val rounds: Int,
        val progress: Float,
        val soundFailed: Boolean = false,   // сигналы не подготовлены, экран предупреждает
    ) : WorkoutState
}

enum class Signal { INTERVAL, FINISH }
```

### Цикл сервиса
Шаг цикла считает чистая функция `tickAt(workout, elapsedMs, previousIndex, soundFailed)`, в сервисе остаются только побочные эффекты.

1. Каждые 250 мс: `elapsed = now - startedAt`, `tickAt()` вызывает `workout.at(elapsed)`.
2. `signalFor(previousIndex, position)` решает, какой сигнал подать. Первый отрезок сигнала не даёт.
3. Состояние публикуется в `StateFlow`, уведомление обновляется только при смене текста уведомления.
4. Если `position == null`: публикуется `Active` с последним отрезком тренировки, `remainingMs = 0` и `progress = 1f`, звучит `FINISH`, сервис ждёт окончания звука с запасом 300 мс на задержку вывода и вызывает `stopSelf()`.

Цикл и финал выполняются одной отменяемой корутиной. Сервис владеет своим `MainScope()` и отменяет его в `onDestroy`.

### Контракт сервиса
- **Доступ к состоянию:** `companion object` с `private val _state` и публичным `state: StateFlow<WorkoutState>`. Экран читает его через `collectAsStateWithLifecycle`, привязка к сервису не нужна.
- **Параметры:** длительности и число повторов передаются через extras интента.
- **Старт:** UI вызывает `startForegroundService()`. Первым действием в ветке старта сервис вызывает `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`.
- **Повторный старт:** проверка стоит после `startForeground`. Если тренировка уже идёт, новый запрос игнорируется.
- **Порядок:** `Active` публикуется только после `startForeground`, поэтому кнопка «Стоп» не может появиться раньше.
- **Стоп:** UI вызывает `stopService()`, а не `startForegroundService()`. Иначе возможен крэш.
- **Завершение:** единая точка `onDestroy` для «Стоп» и финала: «Стоп» приходит через `stopService()`, финал через `stopSelf()`. `onDestroy` отменяет цикл, освобождает `Beeper`, снимает wake lock (проверка `isHeld`), снимает уведомление и последним ставит `Idle`. Шаг цикла из очереди мог вернуть уведомление после `stopService()`, поэтому оно снимается явно. До `onDestroy` `job` остаётся заполненным, и новый старт отклоняется.
- **Перезапуск:** `onStartCommand` возвращает `START_NOT_STICKY`. Если система убьёт процесс, тренировка теряется. Для первой версии это принято.

### Wake lock
`setReferenceCounted(false)`, `acquire(totalMs + 1 минута)` при старте. Таймаут страхует от разряда батареи при ошибке в логике.

### Уведомление
- Канал `IMPORTANCE_LOW`, чтобы обновления не издавали звука.
- `setOngoing(true)`, `setOnlyAlertOnce(true)`, `setShowWhen(false)`, `setVisibility(VISIBILITY_PUBLIC)`, `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)`.
- Нажатие открывает приложение через `packageManager.getLaunchIntentForPackage(packageName)` и `PendingIntent.FLAG_IMMUTABLE`. Ссылка на `MainActivity` не нужна, поэтому Task 5 собирается раньше Task 7.
- Обновление через платформенный `NotificationManager`.

### Сигналы
- `INTERVAL`: два коротких тона 880 Гц.
- `FINISH`: три восходящих тона 660, 880 и 1100 Гц, последний длинный.
- Синтез: моно PCM 16 бит, 44100 Гц, с короткой огибающей против щелчков.
- Проигрывание: один `AudioTrack` в `MODE_STATIC` на сигнал, размер буфера равен размеру PCM, `write()` до `play()`.
- Перед повторным проигрыванием обязательны `stop()` и `reloadStaticData()`, иначе второй сигнал будет беззвучным.
- `IllegalStateException` перехватывается: потерянный сигнал не должен останавливать тренировку.
- Если дорожку не удалось подготовить, тренировка идёт, а экран предупреждает об этом (`Beeper.isReady`, `Active.soundFailed`).
- `play()` ничего не возвращает: длительность финала считается из состава сигнала, `soundDurationMs()`.
- `release()` сразу прекращает звук (US-4).

### Настройки
Хранятся в `SharedPreferences`, сохраняются при успешном нажатии «Старт». Значения по умолчанию 2 мин / 2 мин / 7 повторов.

Ограничения: минуты 0–99, секунды 0–59, повторы 1–99, время бега и ходьбы больше нуля. Пустое поле считается нулём, поэтому пустые минуты и 30 секунд дают верное значение. Верхние пределы также исключают переполнение `Int` в `Workout`.

### Разрешения
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `WAKE_LOCK` объявлены в манифесте.

Сценарий «Старт»: проверить настройки и сохранить их. На Android 13+ без разрешения запустить запрос через `rememberLauncherForActivityResult`, иначе сразу запустить сервис. В обработчике ответа сервис запускается при любом результате: если пользователь откажет, таймер работает, но уведомление не видно. Исключение: пользователь ушёл из приложения при открытом запросе, тогда тренировка не запускается и «Старт» нажимается заново. Уход фиксируется в `ON_STOP`, кроме поворота, в сохраняемом флаге `requestAbandoned`, а обработчик ответа сбрасывает флаг: проверить состояние activity при получении ответа нельзя, потому что ответ приходит при возвращении, после `onStart`, а отмена выглядит как отказ. Это поведение платформы известно по памяти об AOSP и не наблюдалось, поэтому проверяется на телефоне. Пока запрос открыт, флаг `permissionPending` не даёт запустить второй: система отклонила бы его сразу с пустым ответом, и тренировка началась бы за диалогом. Отказ системы в запуске сервиса перехватывается, форма остаётся на экране.

### Экран
- `enableEdgeToEdge()` до `setContent`, `safeDrawingPadding()` на экранах.
- Значки строки состояния выбирает `darkStatusIcons()` по состоянию и теме: на тренировке они совпадают с цветом текста поверх фона отрезка.
- `verticalScroll` на форме, `android:windowSoftInputMode="adjustResize"` в манифесте, чтобы клавиатура не закрывала кнопку. `safeDrawingPadding()` уже включает отступ клавиатуры, отдельный `imePadding()` не нужен.
- `formatTime()` округляет секунды вверх: отображение начинается с `2:00` и не показывает `0:00` внутри отрезка. Итог на форме выводит `formatTotal()`: начиная с часа в виде `ч:мм:сс`.
- Темы окна в `values/themes.xml` и `values-night/themes.xml`, чтобы в тёмной теме не было белой вспышки при запуске.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): tasks achievable within this codebase - code changes, tests, documentation updates
- **Post-Completion** (no checkboxes): items requiring external action - manual testing, changes in consuming projects, deployment configs, third-party verifications

## Implementation Steps

### Task 1: Подготовить окружение и сборку

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `local.properties` (не в git)
- Modify: `.gitignore`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/res/drawable/ic_notification.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/ssortia/runtimer/WorkoutTest.kt`

- [x] выполнить `git init`, если репозиторий ещё не создан, и добавить `.kotlin/` в `.gitignore`
- [x] установить JDK 17 в домашнюю папку и задать `JAVA_HOME`
- [x] установить `cmdline-tools` в `~/Android/Sdk`, затем `sdkmanager "platforms;android-35" "build-tools;34.0.0" "platform-tools"` и `sdkmanager --licenses`
- [x] создать `local.properties` с путём к SDK: это нужно сделать до генерации wrapper
- [x] скачать Gradle 8.11.1 во временную папку и выполнить `gradle wrapper --gradle-version 8.11.1`
- [x] добавить `strings.xml`, `colors.xml`, темы окна, значок уведомления и адаптивную иконку, указать тему в манифесте
- [x] убедиться, что `./gradlew assembleDebug` проходит (APK пока не запускается, это ожидаемо до Task 7)
- [x] написать тесты `Workout`: первый отрезок, переход бег → ходьба, переход между повторами, номер повтора, остаток времени
- [x] написать тесты граничных случаев: последняя миллисекунда, момент окончания, время после окончания, один повтор
- [x] run tests - must pass before next task

### Task 2: Добавить настройки тренировки и их валидацию

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/WorkoutSettings.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/WorkoutSettingsTest.kt`
- Create: `app/src/main/java/com/ssortia/runtimer/SettingsStore.kt`

- [x] реализовать `WorkoutSettings` со значениями по умолчанию, `validate()`, `toWorkout()` и `SettingsError`
- [x] реализовать `SettingsStore`: чтение и запись через `SharedPreferences`
- [x] написать тесты верных настроек, включая пример 2/2/7: `at(1_679_999)?.index == 13` и `at(1_680_000) == null`, то есть 14 отрезков и 28 минут
- [x] написать тест каждого значения `SettingsError`, включая граничные: 99 минут, 100 минут, 59 и 60 секунд, 99 и 100 повторов
- [x] run tests - must pass before next task

### Task 3: Добавить состояние тренировки и выбор сигнала

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/WorkoutState.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/WorkoutStateTest.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/Workout.kt`

- [x] добавить в `Workout` общую длительность и расчёт прогресса
- [x] реализовать `WorkoutState`, `Signal`, `signalFor()` и `formatTime()`
- [x] написать тесты `signalFor()`: старт без сигнала, тот же отрезок, смена отрезка, финал
- [x] написать тесты `formatTime()` и прогресса: начало, середина, значения вблизи нуля, округление секунд вверх
- [x] написать тест, который гоняет `tickAt()` с шагом 250 мс для 10/10/2 и получает ровно 3 `INTERVAL`, а затем 1 `FINISH`, а также прогоны с неровными шагами и зависанием
- [x] run tests - must pass before next task

### Task 4: Реализовать звуковые сигналы

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/Tones.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/TonesTest.kt`
- Create: `app/src/main/java/com/ssortia/runtimer/Beeper.kt`

- [x] реализовать чистый синтез PCM в `Tones.kt`
- [x] реализовать `Beeper` по разделу «Сигналы»: повторное проигрывание, перехват ошибок, `isReady`, `release()`
- [x] написать тесты синтеза: длина буфера, нули в паузе, плавное начало и конец
- [x] написать тест, что `INTERVAL` и `FINISH` различаются и финальный сигнал длиннее
- [x] run tests - must pass before next task

### Task 5: Реализовать фоновый сервис тренировки

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`
- Create: `app/src/main/java/com/ssortia/runtimer/NotificationText.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/NotificationTextTest.kt`

- [x] реализовать старт и повторный старт строго по разделу «Контракт сервиса»
- [x] реализовать канал и уведомление по разделу «Уведомление», включая открытие приложения по нажатию
- [x] реализовать wake lock с таймаутом
- [x] реализовать цикл времени, сигналы и публикацию `StateFlow` через `companion object`
- [x] реализовать финал и единое завершение в `onDestroy` для всех путей
- [x] вынести текст уведомления в чистую функцию верхнего уровня, подписи передавать параметрами
- [x] написать тесты текста уведомления: бег, ходьба, номер повтора, остаток времени
- [x] убедиться, что `./gradlew assembleDebug` проходит
- [x] run tests - must pass before next task

### Task 6: Сделать тему и экран настроек

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ui/Theme.kt`
- Create: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/ui/SettingsInputTest.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/ui/ThemeTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] реализовать светлую и тёмную тему, цвета бега и ходьбы
- [x] реализовать форму: начальные значения из параметра, бег, ходьба, повторы, кнопка «Старт», итоговая длительность
- [x] вынести разбор ввода из текстовых полей в чистую функцию: пустое поле считается нулём, в поле можно ввести только цифры
- [x] написать тесты разбора: пустое поле, отсев нецифровых символов в `digitsOnly()`, слишком большое значение даёт ошибку `validate()`
- [x] сопоставить каждому `SettingsError` строку и показывать её при неверных значениях (US-2), написать тест, что строка есть у каждого значения
- [x] run tests - must pass before next task

### Task 7: Сделать экран тренировки и подключить интерфейс

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ui/ActiveContent.kt`
- Create: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Create: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/NotificationPermissionTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [x] реализовать экран тренировки: отрезок, крупное время, повтор, прогресс, цвет фона, кнопка «Стоп»
- [x] реализовать `WorkoutScreen`: выбор экрана по `WorkoutState`
- [x] реализовать `MainActivity`: Compose, сценарий «Старт» из раздела «Разрешения», стоп сервиса
- [x] загружать настройки в форму из `SettingsStore`, в том числе при возврате в `Idle`, и сохранять их при успешном старте (US-3)
- [x] применить отступы из раздела «Экран» и добавить `windowSoftInputMode="adjustResize"` в манифест
- [x] покрыть новую чистую логику экрана тестами, если она появится
- [x] убедиться, что `./gradlew assembleDebug` проходит
- [x] run tests - must pass before next task

### Task 8: Verify acceptance criteria
- [x] проверить по коду, что US-1…US-8 реализованы, и отметить критерии, требующие проверки на телефоне
- [x] сверить код с разделом «Контракт сервиса»: стоп сразу после старта, стоп во время финального сигнала, повторный старт, повторное открытие экрана
- [x] run full test suite: `./gradlew test`
- [x] run lint: `./gradlew lintDebug`
- [x] собрать APK: `./gradlew assembleDebug`

Результат проверки. Запусков на устройстве не было: «по коду» значит прочитано в коде, поведение на телефоне не наблюдалось.

| Критерий | Проверено тестом или кодом | Только на телефоне |
|---|---|---|
| US-1 | тесты: минуты и секунды, 2/2/7 = 14 отрезков и 28 минут | ввод в форме |
| US-2 | тесты всех `SettingsError`; по коду: кнопка «Старт» выключена при ошибке, текст ошибки виден | вид сообщения |
| US-3 | тесты `SettingsStore`: сохранение и чтение, значения по умолчанию; по коду: сохранение при старте, чтение при открытии формы и при возврате в `Idle` | перезапуск приложения |
| US-4 | по коду: начало с бега, выбор экрана по `WorkoutState`, `onDestroy` при «Стоп» | мгновенное прекращение звука |
| US-5 | тесты `tickAt()`: смена отрезка, 3 `INTERVAL` для 10/10/2, неровные шаги, зависание; по коду: `USAGE_ALARM`, время считается от старта | слышимость при выключенном звуке медиа, точность сигналов |
| US-6 | тесты: только `FINISH` в конце, сигналы различаются; по коду: `stopSelf()` и `onDestroy` | различие на слух, полнота финального сигнала, исчезновение уведомления |
| US-7 | по коду: foreground service и wake lock | весь критерий, включая экономию батареи на прошивке |
| US-8 | тесты текста уведомления; по коду: экран тренировки, `PendingIntent`, состояние в `companion object` | открытие из уведомления, повторное открытие экрана |

Сверка с разделом «Контракт сервиса» (по коду, расхождений нет):
- **Стоп сразу после старта:** «Стоп» появляется только после `Active`, то есть после `startForeground`. Цикл и `onDestroy` идут в главном потоке, поэтому после `onDestroy` цикл не публикует состояние и не подаёт сигналов.
- **Стоп во время финала:** `onDestroy` отменяет `delay`, `release()` прекращает звук, `stopSelf()` не вызывается.
- **Повторный старт:** `startForeground` вызывается всегда, затем `job != null` отклоняет запрос, в том числе между `stopSelf()` и `onDestroy`. Настройки идущей тренировки не меняются.
- **Повторное открытие экрана:** состояние читается из `companion object`, форма не показывается, пока идёт тренировка. `stopWithTask` не задан, поэтому закрытие задачи сервис не останавливает.
- На API 29–33 `ServiceCompat` из core 1.13.1 обнуляет тип `specialUse` маской, поэтому `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` там не приводит к ошибке. Предупреждения lint `InlinedApi` безопасны.

➕ Исправлено в этой задаче:
- В светлой теме значки строки состояния были тёмными на синем фоне ходьбы, а в тёмной светлыми на оранжевом фоне бега. Добавлена `darkStatusIcons()` с тестами, стиль применяется повторным `enableEdgeToEdge`.
- Сервис освобождал `AudioTrack` ровно через длительность буфера после `play()`, не учитывая задержку вывода, поэтому конец финального сигнала мог обрезаться. Добавлен запас 300 мс. Достаточность запаса проверяется только на телефоне.

⚠️ Lint без ошибок, 8 предупреждений: 3 `InlinedApi` (безопасны), 3 `GradleDependency`, `ObsoleteSdkInt`, `MonochromeLauncherIcon`. На работу версии не влияют, оставлены.

### Task 9: [Final] Update documentation
- [x] обновить статус, структуру проекта и инструкции в `README.md`
- [x] create `CLAUDE.md` с командами сборки и принятыми соглашениями, если появились неочевидные
- [ ] move this plan to docs/plans/completed/
  - ⚠️ Отложено: выполняется после ревью, пока не сделано.

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification** на реальном телефоне:
- Установить APK и разрешить уведомления.
- Короткая тренировка 10 с / 10 с / 2 повтора при открытом экране: 2 обычных сигнала и 1 финальный.
- Та же тренировка при заблокированном экране и при свёрнутом приложении.
- Тренировка 2 / 2 / 7 целиком при заблокированном экране, телефон в кармане: сигналы вовремя, финальный звучит на 26-й минуте.
- Нажать «Стоп» посреди тренировки и во время финального сигнала: звук сразу прекращается, новых сигналов нет, уведомление исчезает.
- Ввести неверные значения: появляется понятное сообщение, тренировка не запускается.
- Изменить настройки, запустить тренировку, закрыть и снова открыть приложение: значения сохранились.
- Выключить звук медиа и проверить, что сигналы слышно.
- Проверить в наушниках с играющей музыкой: сигнал слышно, музыка не прерывается надолго, при необходимости решить, нужно ли приглушать её. Заметить, идёт ли сигнал ещё и через динамик (`USAGE_ALARM` обычно так и делает).
- Включить «Не беспокоить» с блокировкой будильников: сигналы должны затихать, это описано в ограничениях README.
- Переключиться на другое приложение во время тренировки: сигналы продолжают звучать вовремя.
- Закрыть экран приложения во время тренировки, открыть из уведомления: виден текущий ход.
- Запретить уведомления: таймер всё равно работает.
- Заблокировать экран во время тренировки: в уведомлении виден отрезок, время и повтор.
- На Android 13+ нажать «Старт» дважды быстро: один запрос разрешения, тренировка начинается только после ответа.
- На Android 13+ выйти из приложения при открытом запросе разрешения: тренировка не начинается сама ни при возвращении, ни после ответа в диалоге, крэша нет, повторное нажатие «Старт» работает.
- На Android 13+ ответить в диалоге разрешения, не выходя из приложения, с разрешением и с отказом: тренировка начинается сразу. Если для этого нужно второе нажатие «Старт», значит диалог останавливает activity на этой прошивке.
- На Android 13+ повернуть телефон при открытом запросе и ответить: тренировка начинается.
- Повернуть экран на форме и во время тренировки: ничего не теряется и не обрывается.
- Открыть клавиатуру на форме: кнопка «Старт» доступна, содержимое не заходит под системные панели.
- Проверить светлую и тёмную тему системы, включая значки строки состояния на фоне бега и ходьбы.
- Если таймер останавливается при выключенном экране, отключить для приложения экономию батареи и зафиксировать это для прошивки телефона.
