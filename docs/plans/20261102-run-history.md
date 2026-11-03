# История пробежек

## Overview
- Задача из `docs/tasks/20261102-run-history.md`: приложение сохраняет каждую тренировку на телефоне, а с экрана настроек открывается список прошедших пробежек, новые сверху.
- В записи хранятся дата и время старта, бег, ходьба, повторы, фактическая длительность и признак «завершена» или «остановлена».
- Завершённая тренировка сохраняется всегда, остановленная только если длилась не меньше 30 секунд. Запись можно удалить с подтверждением.
- Данные лежат в Room, туда же позже добавятся дистанция и маршрут. Навигация делается на Navigation Compose, новые экраны ещё появятся.
- Статистики, экспорта и графиков в этой задаче нет.
- Решения, принятые при планировании:
  - временем старта считается конец отсчёта, то есть начало самого бега. Задержка не входит в длительность, поэтому начало записи и начало длительности совпадают;
  - остановка во время отсчёта не создаёт записи: бег не начался;
  - `RunRecord` одновременно чистая модель и `@Entity`. Отдельная сущность продублировала бы все поля, а аннотации Room не мешают unit-тестам: это единственное отступление от задачи, где таблица описана в `RunDatabase.kt`;
  - пишем в Room только в `onDestroy`, тренировка в процессе в базу не попадает. Гибель процесса теряет запись вместе с тренировкой, это принятое ограничение из-за `START_NOT_STICKY`.

## Context (from discovery)
- Файлы:
  - `WorkoutService.kt`: `runLoop` с `startedAt` и `tick.finished`, `onDestroy` как единственная точка завершения;
  - `Workout.kt`: `Workout`, `totalMs`;
  - `WorkoutState.kt`: `formatTime`, `formatTotal`;
  - `MainActivity.kt`: `setContent`, вызов `WorkoutScreen`, зависимости передаются параметрами;
  - `ui/WorkoutScreen.kt`: ветка `Idle` с `remember { loadSettings() }` и `remember { loadVolume() }`;
  - `ui/SettingsContent.kt`: форма на `rememberSaveable`, кнопка «Старт».
- Паттерны:
  - чистая логика без Android API, подписи передаются параметрами, код с Android API тонкий и тестами не покрыт;
  - экраны получают данные и колбэки параметрами и не знают про сервис и хранилище;
  - ViewModel и DI не используются.
- Зависимости: Room 2.6.1 (`room-runtime`, `room-ktx`, `room-compiler`), KSP `2.0.21-1.0.28`, `navigation-compose` 2.8.5. `lifecycle-runtime-compose` уже подключён.
- Связанные задачи: план паузы (`docs/plans/20261101-pause-and-vibration.md`) не реализован, `WorkoutClock` в коде нет, поэтому длительность считается от `startedAt`. Если пауза появится раньше, длительность берётся из `WorkoutClock.elapsedAt()`. Удаление последней ходьбы уже в коде, `totalMs` учитывает его.
- Программа по неделям (`docs/plans/completed/20261103-preset-weeks.md`) уже в коде: над полями формы стоит ряд чипов `PresetChips`, при переносе `SettingsContent` в маршрут `settings` он переезжает вместе с формой. Нулевая ходьба теперь допустима при 1 повторе (неделя 11), поэтому строка списка для такой записи не должна показывать ходьбу 0:00. US-13 занята программой, история пробежек получает US-14.

## Development Approach
- **testing approach**: Regular, сначала код, затем тесты в той же задаче.
- complete each task fully before moving to the next
- make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** для кода с чистой логикой. Код с Android API (Room, сервис, Compose) по соглашению проекта тестами не покрыт, для него критерий задачи: сборка и существующие тесты проходят.
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- run tests after each change
- maintain backward compatibility

## Testing Strategy
- **unit tests**: `RunRecordTest` проверяет сборку записи, правило 30 секунд, ограничение длительности и строку списка.
- **e2e tests**: в проекте их нет. Запись, база и экраны проверяются вручную, см. Post-Completion.
- Команды: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug` и `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`.

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview
- `runRecordOrNull` в `RunRecord.kt` решает, нужна ли запись, и собирает её. Сервис только передаёт туда факты о тренировке.
- Сервис запоминает старт бега после отсчёта и отметку о финале в момент `tick.finished`. В `onDestroy` он собирает запись и отдаёт её базе.
- `RunDatabase` хранит единственный экземпляр базы и свою область корутин: `scope` сервиса к моменту записи отменён, а запись и удаление не должны зависеть от жизни сервиса или activity.
- `NavHost` живёт внутри ветки `Idle`, вместе с `rememberNavController()`. При старте тренировки ветка покидает композицию, поэтому стек сбрасывается сам, отдельного кода сброса нет. При повороте экрана `rememberNavController()` восстанавливает открытую историю.
- `HistoryContent` получает готовый список и колбэк удаления, про Room не знает.

## Technical Details
```kotlin
@Entity(tableName = "runs")
data class RunRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,      // System.currentTimeMillis() в конце отсчёта
    val runSec: Int,
    val walkSec: Int,
    val rounds: Int,
    val durationMs: Long,
    val completed: Boolean,
)

const val MIN_STOPPED_RUN_MS = 30_000L

fun runRecordOrNull(
    workout: Workout,
    startedAtMs: Long?,         // null: бег не начался
    elapsedMs: Long,
    finished: Boolean,
): RunRecord?
```

- `runRecordOrNull` возвращает `null`, если `startedAtMs == null` или тренировка остановлена раньше `MIN_STOPPED_RUN_MS`. Длительность равна `elapsedMs.coerceAtMost(workout.totalMs)`, поэтому хвост после финального сигнала в неё не входит.
- Тренировка короче 30 секунд, дошедшая до финала, сохраняется. Остановка ровно на 30 секундах тоже сохраняется.
- Остановка во время финального сигнала даёт `completed = true`, потому что `finished` уже выставлен.
- Сервис хранит четыре новых поля: `workout`, `runStartedAtMs: Long?` (системные часы, для даты записи), `runStartedRealtime: Long` (`elapsedRealtime`, для длительности) и `finished`. `runStartedAtMs` заполняется в `runLoop` после `runCountdown`, до начала цикла, а `runStartedRealtime` равен `startedAt`, часы второй раз не читаются. `workout` в сервисе nullable, поэтому в `onDestroy` запись собирается через `workout?.let`.
- Порядок в `onDestroy`: `scope.cancel()`, сборка записи и вызов сохранения, затем прежние шаги, `Idle` последним. Сборка идёт в главном потоке после отмены цикла, поэтому состояние не изменится.
- Сохранение: `insert` в отдельной области `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. `SQLiteException` ловится и пишется в лог: сбой записи не должен ронять приложение после пробежки.
- Запись защищена отдельным `PARTIAL_WAKE_LOCK` с таймаутом 10 с, он отпускается в `finally` после `insert`. После `onDestroy` основной wake lock и foreground уже сняты, и при заблокированном экране процесс мог бы уснуть до конца записи, особенно первой, когда база ещё открывается.
- Чтение списка: `.catch` на потоке пишет `SQLiteException` в лог и отдаёт пустой список. `IllegalStateException` от Room не ловится, при смене схемы это ошибка разработки.
- DAO: `runs(): Flow<List<RunRecord>>` с `ORDER BY startedAtMs DESC`, `insert`, `delete(id)`.
- База: `RunDatabase` версии 1, `exportSchema = true`, схема в `app/schemas/` через `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` и лежит в git. `fallbackToDestructiveMigration` не включаем.
- Строка списка собирается чистой функцией `runRowText(record, zone, locale, labels...)` на `java.time`, он доступен с API 26. `zone` и подписи передаются параметрами, поэтому результат проверяется в тестах.
- Поток объявлен в `MainActivity` как `private val runs by lazy { ... }`, по образцу `store`, база строится от `applicationContext`. Иначе вызов в `setContent` создавал бы новый `Flow` при рекомпозиции.
- Экран: поток собирается в `composable("history")` через `collectAsStateWithLifecycle(initialValue = null)`, а `HistoryContent` получает `List<RunRecord>?`. Пока `null`, список не показывается, иначе при открытии мелькнёт пустое состояние.
- Удаление: в `rememberSaveable` хранится `pendingDeleteId: Long?`, а не `RunRecord`: запись не `Parcelable`, и сохранение состояния при открытом диалоге уронило бы приложение. Данные для диалога берутся из списка по `id`, если записи уже нет, диалог не показывается.
- Навигация: оба колбэка обёрнуты в `dropUnlessResumed`, иначе двойное нажатие оставит пустой `NavHost` или положит две истории в стек. Анимации `NavHost` отключены через `enterTransition` и `exitTransition = None`: во время перехода экран не `RESUMED`, и нажатия пропадали бы.
- `Modifier.safeDrawingPadding()` переносится с `SettingsContent` на `NavHost`, поэтому отступы действуют на оба экрана.
- Удаление идёт через ту же область корутин, что и запись: `lifecycleScope` оборвал бы его при закрытии activity.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): изменения кода, тесты, документация.
- **Post-Completion** (no checkboxes): ручная проверка на телефоне.

## Implementation Steps

### Task 1: Подключить Room, KSP и Navigation Compose

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`

- [ ] добавить плагин KSP `2.0.21-1.0.28` и зависимости Room 2.6.1 и `navigation-compose` 2.8.5
- [ ] настроить `room.schemaLocation` на `app/schemas`
- [ ] проверить через `./gradlew :app:dependencyInsight --dependency androidx.core:core --configuration debugRuntimeClasspath`, что `androidx.core` остался на 1.13.1. Если версия выросла, перепроверить предупреждение `InlinedApi` из `CLAUDE.md` и обновить его
- [ ] run tests - must pass before next task

### Task 2: Логика записи и строки списка

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/RunRecord.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/RunRecordTest.kt`

- [ ] добавить `RunRecord` и `runRecordOrNull`
- [ ] добавить `runRowText`, подписи, зону и локаль передавать параметрами
- [ ] написать тесты: завершённая запись, остановленная запись, `startedAtMs == null`
- [ ] написать тесты границ: 29 999 и 30 000 мс, короткая завершённая тренировка, `elapsedMs` больше `totalMs`, остановка на финальном сигнале
- [ ] написать тесты строки списка для завершённой и остановленной записи и для записи без ходьбы (1 повтор, ходьба 0)
- [ ] run tests - must pass before next task

### Task 3: База данных

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/RunDatabase.kt`
- Create: `app/schemas/com.ssortia.runtimer.RunDatabase/1.json`

- [ ] описать DAO и `RunDatabase` с единственным экземпляром
- [ ] добавить сохранение и удаление в собственной области корутин, `SQLiteException` писать в лог
- [ ] защитить сохранение отдельным wake lock с таймаутом 10 с, отпускать в `finally`
- [ ] добавить `.catch` с логом на поток записей
- [ ] собрать проект и добавить в git сгенерированную схему
- [ ] run tests - must pass before next task

### Task 4: Сохранение тренировки в сервисе

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`

- [ ] запоминать тренировку, а в `runLoop` после отсчёта `System.currentTimeMillis()` и `startedAt`
- [ ] выставлять `finished` в момент `tick.finished`, до ожидания конца финального сигнала
- [ ] собирать и сохранять запись в `onDestroy`, остальной порядок из контракта сервиса не менять: `Idle` остаётся последним
- [ ] убедиться, что отклонённый запрос и неверные параметры не создают записи: `runStartedAtMs` остаётся `null`
- [ ] run tests - must pass before next task

### Task 5: Экран истории

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ui/HistoryContent.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] добавить `HistoryContent`: список на `LazyColumn`, пустое состояние, кнопка возврата
- [ ] добавить диалог подтверждения удаления, в `rememberSaveable` хранить `pendingDeleteId`, данные брать из списка по `id`
- [ ] добавить строки на русском
- [ ] run tests - must pass before next task

### Task 6: Навигация и кнопка «История»

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`

- [ ] добавить в ветку `Idle` `rememberNavController()` и `NavHost` с маршрутами `settings` и `history`, `settings` стартовый
- [ ] отключить анимации `NavHost`, перенести на него `Modifier.safeDrawingPadding()` вместе с комментарием про клавиатуру
- [ ] перенести `SettingsContent` в маршрут `settings` вместе с `remember { loadSettings() }` и `remember { loadVolume() }`, передать `initialVolume` и `onVolumeChange`, обновить комментарий: настройки читаются при создании маршрута
- [ ] добавить на форму кнопку «История», переход и возврат только через `NavController`, без своего `BackHandler`, оба колбэка обернуть в `dropUnlessResumed`
- [ ] объявить в `MainActivity` `runs by lazy` и передать в `WorkoutScreen` поток записей и колбэк удаления
- [ ] run tests - must pass before next task

### Task 7: Verify acceptance criteria
- [ ] verify all requirements from Overview are implemented
- [ ] проверить по коду контракт сервиса: `startForeground` первым, `START_NOT_STICKY`, `Idle` последним в `onDestroy`, `job` не обнуляется
- [ ] run full test suite: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [ ] run lint: `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`

### Task 8: [Final] Update documentation

**Files:**
- Modify: `USER_STORIES.md`
- Modify: `README.md`
- Modify: `CLAUDE.md`

- [ ] перенести «История пробежек» из раздела «Вне первой версии» в истории `USER_STORIES.md` как US-14, добавить критерии приёмки
- [ ] обновить `README.md`: описание, структуру проекта, раздел «Расширение», ограничение про завершение процесса
- [ ] обновить `CLAUDE.md`: раздел «Архитектура» сейчас говорит, что экран один и навигация не используется; добавить правило, что `NavHost` работает только внутри `Idle`, а экраном таймера управляет состояние сервиса; в контракте сервиса указать сохранение записи в `onDestroy` и отдельный wake lock на запись
- [ ] move this plan to `docs/plans/completed/`

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification** на телефоне, запуск через `./run-device.sh`:
- запись появляется после финала и после «Стоп», время старта совпадает с концом отсчёта;
- остановка раньше 30 секунд и остановка во время отсчёта запись не создают;
- остановка во время финального сигнала даёт запись «завершена»;
- история сохраняется после перезапуска приложения, новые записи сверху;
- удаление спрашивает подтверждение, отмена оставляет запись;
- «Назад» с истории возвращает на настройки, несохранённые значения формы при этом не пропадают;
- двойное нажатие на «История» и на кнопку возврата не даёт пустого экрана и двух историй в стеке;
- заголовок и последняя запись истории не уходят под системные панели;
- поворот экрана не сбрасывает открытую историю и диалог удаления;
- после тренировки приложение открывается на настройках;
- запись сохраняется после тренировки с заблокированным экраном.
