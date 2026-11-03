# История пробежек

## Overview
- Задача из `docs/tasks/20261102-run-history.md`: приложение сохраняет каждую тренировку на телефоне, а с формы открывается список прошедших пробежек, новые сверху.
- В записи хранятся дата и время старта, бег, ходьба, повторы, фактическая длительность и признак «завершена» или «остановлена».
- Завершённая тренировка сохраняется всегда, остановленная только если длилась не меньше 30 секунд. Запись можно удалить с подтверждением.
- Данные лежат в Room, туда же позже добавятся дистанция и маршрут. Навигация делается на Navigation Compose, новые экраны ещё появятся.
- Статистики, экспорта и графиков в этой задаче нет.
- Решения, принятые при планировании:
  - временем старта считается конец отсчёта, то есть начало самого бега. Задержка не входит в длительность, поэтому начало записи и начало длительности совпадают;
  - длительность берётся из `WorkoutClock.elapsedAt()`, паузы в неё не входят. Правило 30 секунд тоже считается по времени без пауз: 20 секунд бега и 5 минут паузы запись не дают;
  - остановка во время отсчёта не создаёт записи: бег не начался, `clock` ещё `null`;
  - признак «завершена» выводится из длительности: `elapsedMs >= workout.totalMs`. Задача предлагает отметку в момент `tick.finished`, но после финала часы идут дальше, поэтому остановка во время финального сигнала и так даёт «завершена». Отдельное поле не нужно, и пропадает случай, когда «Стоп» пришёл после конца, но до ближайшего тика, и запись с полной длительностью оказалась бы «остановленной»;
  - длительность в списке округляется вниз до секунды: `formatTime` и `formatTotal` округляют вверх, они придуманы для остатка, и 30,2 с превратились бы в 0:31;
  - `RunRecord` одновременно чистая модель и `@Entity`. Отдельная сущность продублировала бы все поля, а аннотации Room не мешают unit-тестам: это единственное отступление от задачи, где таблица описана в `RunDatabase.kt`;
  - пишем в Room только в `onDestroy`, тренировка в процессе в базу не попадает. Гибель процесса теряет запись вместе с тренировкой, это принятое ограничение из-за `START_NOT_STICKY`;
  - маршрутами становятся все три экрана ветки `Idle`: форма (`settings`, стартовый), настройки приложения (`app_settings`) и история (`history`). Флаг `showAppSettings`, `BackHandler` и `SaveableStateHolder` уходят: стек навигации сам хранит состояние формы, пока открыт другой экран;
  - кнопка «История» стоит на форме рядом с «Настройками», такой же `TextButton`: иконок material в зависимостях нет, и ради одной кнопки их не добавляем.

## Context (from discovery)
- Файлы:
  - `WorkoutService.kt`: `runLoop` создаёт `WorkoutClock` после отсчёта, `tick.finished` завершает цикл, `onDestroy` единственная точка завершения;
  - `WorkoutClock.kt`: `elapsedAt(now)` без пауз, на паузе время стоит;
  - `Workout.kt`: `Workout`, `totalMs`;
  - `WorkoutState.kt`: `formatTime`, `formatTotal`;
  - `MainActivity.kt`: `store by lazy`, вызов `WorkoutScreen`, зависимости передаются параметрами;
  - `ui/WorkoutScreen.kt`: ветка `Idle` с `remember { loadSettings() }`, флагом `showAppSettings`, `BackHandler` и `rememberSaveableStateHolder()`;
  - `ui/SettingsContent.kt`: форма на `rememberSaveable`, чипы `PresetChips`, кнопки «Старт» и «Настройки» (`onOpenSettings`);
  - `ui/AppSettingsContent.kt`: громкость, вибрация, задержка старта, кнопка возврата `onBack`. Значения читаются через `remember { load…() }` и пишутся сразу, поэтому повторное чтение при возврате на маршрут безопасно.
- Паттерны:
  - чистая логика без Android API, подписи передаются параметрами, код с Android API тонкий и тестами не покрыт;
  - экраны получают данные и колбэки параметрами и не знают про сервис и хранилище;
  - ViewModel и DI не используются.
- Зависимости: Room 2.6.1 (`room-runtime`, `room-ktx`, `room-compiler`), KSP `2.0.21-1.0.28`, `navigation-compose` 2.8.5 (Compose BOM 2024.12.01 даёт Compose 1.7, это подходит). `lifecycle-runtime-compose` 2.8.7 уже подключён, из него берутся `collectAsStateWithLifecycle` и `dropUnlessResumed`.
- Связанные задачи: пауза и вибрация (`docs/plans/completed/20261101-pause-and-vibration.md`) уже в коде. Настройки приложения вынесены на отдельный экран (`docs/tasks/20261103-review-session-changes.md`), этот план сверен с ним. Удаление последней ходьбы уже в коде, `totalMs` учитывает его.
- Программа по неделям уже в коде: нулевая ходьба допустима при 1 повторе (неделя 11), поэтому строка списка для такой записи не показывает ходьбу 0:00. US-13 занята программой, история пробежек получает US-14.

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
- Сервис запоминает тренировку и время старта по системным часам при создании `WorkoutClock`. В `onDestroy` он берёт длительность из `clock`, собирает запись и отдаёт её базе.
- `RunDatabase` хранит единственный экземпляр базы и свою область корутин: `scope` сервиса к моменту записи отменён, а запись и удаление не должны зависеть от жизни сервиса или activity.
- `NavHost` живёт внутри ветки `Idle`, вместе с `rememberNavController()`. При старте тренировки ветка покидает композицию, поэтому стек сбрасывается сам, отдельного кода сброса нет, и после тренировки открывается форма. При повороте экрана `rememberNavController()` восстанавливает открытый маршрут.
- `HistoryContent` получает готовый список и колбэки, про Room не знает.

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
    startedAtMs: Long,
    elapsedMs: Long,            // WorkoutClock.elapsedAt(), без пауз
): RunRecord?
```

- `runRecordOrNull` возвращает `null`, если тренировка остановлена раньше `MIN_STOPPED_RUN_MS`. `completed = elapsedMs >= workout.totalMs`. Длительность равна `elapsedMs.coerceAtMost(workout.totalMs)`, поэтому хвост после финального сигнала в неё не входит.
- Тренировка короче 30 секунд, дошедшая до финала, сохраняется. Остановка ровно на 30 секундах тоже сохраняется.
- Признак «бег не начался» проверяется только в сервисе: без `clock` функция не вызывается, поэтому `startedAtMs` ненулевой.
- Сервис хранит два новых поля: `workout: Workout?` и `runStartedAtMs: Long?`. `workout` заполняется в `startWorkout`. `runStartedAtMs` заполняется в `runLoop` рядом с созданием `WorkoutClock`, до начала цикла, как `System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startedAt)`: после отсчёта и `play(START)` проходит немного времени, а дата должна совпадать с `startedAt`. В `onDestroy` длительность равна `clock.elapsedAt(SystemClock.elapsedRealtime())`: на паузе `elapsedAt` возвращает время на момент паузы, поэтому остановка с паузы считается верно. Если `workout`, `runStartedAtMs` или `clock` равны `null`, запись не собирается.
- Порядок в `onDestroy`: `scope.cancel()`, сборка записи и вызов сохранения, затем прежние шаги, `Idle` последним. Сборка идёт в главном потоке после отмены цикла, поэтому состояние не изменится.
- Сохранение: `insert` в отдельной области `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. `SQLiteException` ловится и пишется в лог: сбой записи не должен ронять приложение после пробежки.
- Запись защищена отдельным `PARTIAL_WAKE_LOCK` с таймаутом 10 с. После `onDestroy` основной wake lock и foreground уже сняты, и при заблокированном экране процесс мог бы уснуть до конца записи, особенно первой, когда база ещё открывается. Lock создаётся с `setReferenceCounted(false)`, а в `finally` отпускается только при `isHeld`: иначе после срабатывания таймаута `release()` бросил бы `RuntimeException("WakeLock under-locked")` в корутине без обработчика, и процесс упал бы уже после пробежки. `acquire` вызывается в `onDestroy` до снятия основного wake lock, чтобы между ними не было окна, в котором процессор может уснуть.
- Единственный экземпляр базы лежит в `by lazy` внутри `object` (синхронизирован по умолчанию) и берётся в вызывающем потоке до `launch`: `MainActivity` и сервис обращаются к нему из разных потоков, и наивный синглтон мог бы создать две базы.
- Чтение списка: `.catch` на потоке пишет `SQLiteException` в лог и отдаёт пустой список. `IllegalStateException` от Room не ловится, при смене схемы это ошибка разработки.
- DAO: `runs(): Flow<List<RunRecord>>` с `ORDER BY startedAtMs DESC`, `insert`, `delete(id)`.
- База: `RunDatabase` версии 1, `exportSchema = true`, схема в `app/schemas/` через `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` и лежит в git. Gradle-плагин `androidx.room` не подключаем: аргумента KSP достаточно, а плагин добавил бы ещё одну версию, которую надо держать в соответствии с Room. `fallbackToDestructiveMigration` не включаем.
- Строка списка собирается чистой функцией `runRowText(record, zone, locale, labels...)` на `java.time`, он доступен с API 26. `zone` и подписи передаются параметрами, поэтому результат проверяется в тестах. Дата форматируется явным шаблоном `DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale)`, а не `ofLocalizedDateTime`: локализованный формат в JDK берётся из CLDR, а на телефоне из ICU, и тест сверял бы не то, что видит пользователь. В тестах `ZoneOffset.UTC` и `Locale("ru")`.
- Поток объявлен в `MainActivity` как `private val runs by lazy { ... }`, по образцу `store`, база строится от `applicationContext`. Иначе вызов в `setContent` создавал бы новый `Flow` при рекомпозиции.
- Экран: поток собирается в `composable("history")` через `collectAsStateWithLifecycle(initialValue = null)`, а `HistoryContent` получает `List<RunRecord>?`. Пока `null`, список не показывается, иначе при открытии мелькнёт пустое состояние.
- Удаление: в `rememberSaveable` хранится `pendingDeleteId: Long?`, а не `RunRecord`: запись не `Parcelable`, и сохранение состояния при открытом диалоге уронило бы приложение. Данные для диалога берутся из списка по `id`, если записи уже нет, диалог не показывается.
- Маршруты строковые (`"settings"`, `"app_settings"`, `"history"`), константами в `WorkoutScreen.kt`. Типобезопасные маршруты Navigation 2.8 требуют плагина `kotlinx-serialization`, ради трёх маршрутов без аргументов он не нужен.
- `initial = remember { loadSettings() }` переезжает внутрь `composable("settings")`. При возврате с другого маршрута настройки читаются заново, но поле формы восстанавливает `rememberSaveable` записи стека, поэтому несохранённые правки не теряются. После тренировки стека уже нет, и форма читает сохранённые настройки.
- Навигация: все колбэки переходов (`onOpenSettings`, `onOpenHistory`, оба `onBack`) обёрнуты в `dropUnlessResumed`, иначе двойное нажатие оставит пустой `NavHost` или положит два экрана в стек. Возврат идёт через `popBackStack()`. Анимации `NavHost` отключены через `enterTransition` и `exitTransition = None`: во время перехода экран не `RESUMED`, и нажатия пропадали бы.
- `Modifier.safeDrawingPadding()` переносится с обоих экранов на `NavHost` вместе с комментарием про клавиатуру, поэтому отступы действуют на все три маршрута.
- Удаление идёт через ту же область корутин, что и запись: `lifecycleScope` оборвал бы его при закрытии activity.
- `.catch` завершает поток: после ошибки чтения список остаётся пустым до повторного открытия экрана. Для истории это приемлемо.
- Кнопки «История» и «Настройки» стоят в `Row`, у обеих `Modifier.weight(1f)` вместо `fillMaxWidth()`. У кнопки удаления в строке списка `contentDescription` с датой записи, чтобы TalkBack называл, что удаляется.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): изменения кода, тесты, документация.
- **Post-Completion** (no checkboxes): ручная проверка на телефоне.

## Implementation Steps

### Task 1: Подключить Room, KSP и Navigation Compose

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`

- [x] добавить плагин KSP `2.0.21-1.0.28` и зависимости Room 2.6.1 и `navigation-compose` 2.8.5
- [x] настроить `room.schemaLocation` на `app/schemas`
- [x] проверить через `./gradlew :app:dependencyInsight --dependency androidx.core:core --configuration debugRuntimeClasspath`, что `androidx.core` остался на 1.13.1. Если версия выросла, перепроверить предупреждение `InlinedApi` из `CLAUDE.md` и обновить его
- [x] run tests - must pass before next task

### Task 2: Логика записи и строки списка

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/RunRecord.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/RunRecordTest.kt`

- [ ] добавить `RunRecord` и `runRecordOrNull`
- [ ] добавить `runRowText`, подписи, зону и локаль передавать параметрами
- [ ] написать тесты: завершённая запись, остановленная запись
- [ ] написать тесты границ: 29 999 и 30 000 мс, короткая завершённая тренировка, `elapsedMs` равен `totalMs` (завершена), `elapsedMs` больше `totalMs` (остановка на финальном сигнале: завершена, длительность `totalMs`)
- [ ] написать тесты строки списка с `ZoneOffset.UTC` и `Locale("ru")` для завершённой и остановленной записи, для записи без ходьбы (1 повтор, ходьба 0) и для округления длительности вниз (30 200 мс даёт 0:30)
- [ ] run tests - must pass before next task

### Task 3: База данных

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/RunDatabase.kt`
- Create: `app/schemas/com.ssortia.runtimer.RunDatabase/1.json`

- [ ] описать DAO и `RunDatabase` с единственным экземпляром в `by lazy` внутри `object`
- [ ] добавить сохранение и удаление в собственной области корутин, экземпляр базы брать до `launch`, `SQLiteException` писать в лог
- [ ] защитить сохранение отдельным wake lock с `setReferenceCounted(false)` и таймаутом 10 с, в `finally` отпускать только при `isHeld`
- [ ] добавить `.catch` с логом на поток записей
- [ ] собрать проект и добавить в git сгенерированную схему
- [ ] run tests - must pass before next task

### Task 4: Сохранение тренировки в сервисе

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`

- [ ] запоминать `workout` в `startWorkout`, а `runStartedAtMs` в `runLoop` рядом с созданием `WorkoutClock`, пересчитанный к моменту `startedAt`
- [ ] собирать запись в `onDestroy` по `clock.elapsedAt(...)` и сохранять её, wake lock записи брать до снятия основного; остальной порядок из контракта сервиса не менять: `Idle` остаётся последним
- [ ] убедиться, что отклонённый запрос, неверные параметры, команда паузы без тренировки и «Стоп» во время отсчёта не создают записи: `clock` и `runStartedAtMs` остаются `null`
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
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/AppSettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`

- [ ] заменить в ветке `Idle` флаг `showAppSettings`, `BackHandler` и `rememberSaveableStateHolder()` на `rememberNavController()` и `NavHost` с маршрутами `settings` (стартовый), `app_settings` и `history`
- [ ] отключить анимации `NavHost`, перенести на него `Modifier.safeDrawingPadding()` вместе с комментарием про клавиатуру, убрать модификатор с экранов
- [ ] перенести `SettingsContent` в маршрут `settings` вместе с `remember { loadSettings() }`, обновить комментарий: настройки читаются при создании маршрута, правки формы хранит стек навигации
- [ ] перенести `AppSettingsContent` в маршрут `app_settings` без изменения параметров, `onBack` вести на `popBackStack()`
- [ ] добавить на форму кнопку «История» в одной строке с «Настройками» (`onOpenHistory`, обе кнопки с `weight(1f)`), все колбэки переходов и возврата обернуть в `dropUnlessResumed`
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
- [ ] обновить `README.md`: описание, структуру проекта, раздел «Расширение» (Room уже подключён, осталось добавить дистанцию и точки маршрута), ограничение про завершение процесса
- [ ] обновить `CLAUDE.md`, раздел «Архитектура»: переписать фразу «Библиотеки навигации тоже нет», вместо флага `showAppSettings` и `SaveableStateHolder` описать `NavHost` с тремя маршрутами, который работает только внутри `Idle`, а экраном таймера управляет состояние сервиса; переходы только через `NavController` и `dropUnlessResumed`, без своего `BackHandler`
- [ ] обновить `CLAUDE.md`, контракт сервиса: запись собирается в `onDestroy` из `clock`, сохраняется в отдельной области корутин под отдельным wake lock
- [ ] обновить `CLAUDE.md`, раздел «Неочевидное»: анимации `NavHost` отключены, потому что `dropUnlessResumed` глотает нажатия во время перехода; wake lock записи без подсчёта ссылок и с проверкой `isHeld`; `.catch` завершает поток записей; «завершена» выводится из `elapsedMs >= totalMs`; дата строки списка форматируется явным шаблоном
- [ ] move this plan to `docs/plans/completed/`

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification** на телефоне, запуск через `./run-device.sh`:
- запись появляется после финала и после «Стоп», время старта совпадает с концом отсчёта;
- остановка раньше 30 секунд и остановка во время отсчёта запись не создают;
- остановка во время финального сигнала даёт запись «завершена»;
- тренировка с паузой: длительность в записи не включает паузу, остановка во время паузы сохраняет время до паузы;
- история сохраняется после перезапуска приложения, новые записи сверху;
- удаление спрашивает подтверждение, отмена оставляет запись;
- «Назад» с истории и с настроек приложения возвращает на форму, несохранённые значения формы при этом не пропадают;
- двойное нажатие на «История», «Настройки» и кнопки возврата не даёт пустого экрана и двух одинаковых экранов в стеке;
- заголовок и последняя запись истории не уходят под системные панели, поле формы не закрывается клавиатурой;
- поворот экрана не сбрасывает открытую историю, настройки приложения и диалог удаления;
- после тренировки приложение открывается на форме;
- запись сохраняется после тренировки с заблокированным экраном.
