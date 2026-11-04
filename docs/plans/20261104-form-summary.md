# Итог тренировки на форме, «Старт» в центре нижней панели

## Overview
- Задача: `docs/tasks/20261104-form-summary.md`, макет `Nav-center-start.dc.html` в https://claude.ai/artifact/WKnsAn8dCvkNbCqtUVcdik#artboard-86e27bab5c36.
- Сейчас карточки «Бег», «Шаг» и «Повторы» стоят наверху формы, ниже до закреплённого «Старта» пусто. Свободное место займёт итог тренировки (общее время и состав), карточки уйдут вниз к большому пальцу, а «Старт» станет круглой кнопкой в центре нижней панели и будет виден на всех вкладках.
- Нового управления нет: итог только показывает то, что уже задано карточками и шторкой программы.

## Context (from discovery)
- `ui/SettingsContent.kt`: `rememberSaveable` с `WorkoutSettingsSaver` хранит `WorkoutSettings`, колонка карточек с `verticalScroll`, закреплённый нижний блок (предупреждение о фоне, ошибка, `Button` «Старт» в цветах бега).
- `ui/WorkoutScreen.kt`: ветка `Idle` с `rememberNavController()`, `NavHost`, маршрут `settings` вызывает `remember { loadSettings() }`; `BottomBar` на `NavigationBar` + `NavigationBarItem` по `BottomTab.entries` (WORKOUT, HISTORY, PROGRESS, SETTINGS).
- `WorkoutState.kt`: `formatTotal(totalMs)` («22:00», «1:05:00»). `Workout(runSec, walkSec, rounds)` с `totalMs`. `WorkoutSettings.validate()` и `toWorkout()`.
- Образец чистой функции подписей: `ProgressRowText.kt` (подписи классом `ProgressLabels`, склонение готовой строкой из `<plurals>`), тесты в `app/src/test/java/com/ssortia/runtimer/`.
- `strings.xml`: `segment_run` «Бег», `segment_walk` «Шаг», `button_start` «Старт», `<plurals name="progress_runs">` как образец.
- `MainActivity.startWorkout()` читает настройки из `SettingsStore`, `onStart(settings)` идёт через существующий путь с запросом разрешения: не меняется.

## Development Approach
- **testing approach**: Regular (сначала код, затем тесты)
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
- run tests after each change: `JAVA_HOME=~/.local/jdk-17 ./gradlew test`
- maintain backward compatibility
- по соглашению проекта код с Compose тонкий и тестами не покрыт: логика (строка состава, раздача места) выносится в чистые функции с тестами

## Testing Strategy
- **unit tests**: `WorkoutSummaryTest` (строка состава), `SummaryPlacementTest` (раздача свободного места и выбор размера времени). Существующие `BottomTabTest`, `SettingsFormTest`, `ThemeTest` должны остаться зелёными.
- **e2e tests**: UI-тестов и эмулятора в проекте нет, раскладка, кнопка в панели и TalkBack проверяются вручную на телефоне (Post-Completion).

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview
- **Строка состава** собирается чистой функцией `workoutSummaryParts` в `WorkoutSummary.kt`, подписи и склонённое «N отрезков» приходят параметрами.
- **Состояние формы поднято** в ветку `Idle` у `WorkoutScreen`: `var settings by rememberSaveable(stateSaver = WorkoutSettingsSaver) { mutableStateOf(loadSettings()) }`. `SettingsContent` получает `settings` и `onSettingsChange`, кнопки «Старт» в нём больше нет. Ветка `Idle` по-прежнему покидает композицию при старте, поэтому после тренировки настройки читаются заново из `SettingsStore`, как сейчас.
- **«Старт» в панели.** Кнопка `StartFab` рисуется поверх панели в общем `Box` ветки `Idle`, выровненная по центру снизу. Внутри `NavigationBar` её не сдвинуть: `Surface` панели обрезает содержимое по своей форме (`clip`), а обрезка отсекает и отрисовку, и попадание нажатий. Отступы `safeDrawing.only(Top + Horizontal)` переезжают с `Column` на общий `Box`, иначе с боковой системной панелью кнопка встанет по центру экрана, а не панели. Отступ кнопки снизу: `WindowInsets.navigationBars` снизу + `BottomBarHeight` (80 dp, своя константа, повторяет внутреннее значение M3, в комментарии это сказано) − (размер кнопки − выступ).
- **Доступность «Старта».** `Surface` в M3 сам ставит `isTraversalGroup = true`, поэтому вкладки читаются подряд, а соседа панели в `Box` `traversalIndex` между ними не поставит. Поэтому центральный слот панели — не пустой `Spacer`, а доступный заместитель: `Box(Modifier.weight(1f).fillMaxHeight().semantics { role = Role.Button; contentDescription = «Старт»; onClick { onStart(settings); true }; if (!enabled) disabled() })`. У видимой `StartFab` `clearAndSetSemantics {}`. Заместитель внутри группы панели, и обход по геометрии сам даст «История, Старт, Прогресс».
- **Выступ на всех вкладках.** Кнопка выступает над панелью около 40 dp на каждой вкладке, поэтому общий нижний отступ `StartFabOverlap` добавляется в прокручиваемые колонки всех четырёх экранов (`contentPadding` у `LazyColumn`, `padding(bottom = …)` у `verticalScroll`), иначе кнопка закроет последний элемент истории, прогресса и настроек.
- **Раскладка формы.** Колонка с `verticalScroll` внутри `BoxWithConstraints` получает высоту области: `viewport = maxHeight − вертикальные отступы колонки` (внешний `padding(16.dp)` иначе даст лишние 32 dp и постоянную прокрутку). Итог и нижняя часть (карточки, предупреждение, ошибка, отступ под выступ кнопки) раскладываются `SubcomposeLayout`: сначала меряется нижняя часть, затем итог с крупным временем; если он не помещается в оставшееся место, итог меряется заново с меньшим стилем. Решение принимает чистая функция `summaryPlacement`, итог ставится по центру своего слота, высота всей раскладки не меньше области, иначе колонка прокручивается. Размещается только выбранный вариант итога: неразмещённый не должен попасть в дерево доступности, иначе TalkBack найдёт два итога (проверить вручную).

## Technical Details
- `workoutSummaryParts(workout: Workout, segmentsText: String, labels: SummaryLabels): List<String>`: части состава («11 отрезков», «бег 12 мин», «шаг 10 мин»). Видимая строка — `joinToString(" · ")`.
- `summaryDescription(title: String, time: String, parts: List<String>): String` там же: описание для TalkBack «Вся тренировка, 22:00, 11 отрезков, бег 12 мин, шаг 10 мин», покрыто тестом, как `progressRowText`.
  - `SummaryLabels(run: String, walk: String, min: String)`: «бег», «шаг» (строчные, новые ресурсы `summary_run`, `summary_walk`) и «мин» из существующего `minutes_short`.
  - `segmentsText` — готовое «11 отрезков» из `<plurals name="summary_segments">` (one «%d отрезок», few «%d отрезка», many «%d отрезков», other «%d отрезка»), число отрезков `segmentCount(workout) = 2 * rounds - 1`.
  - Бег `runSec * rounds`, шаг `walkSec * (rounds - 1)`. Длительность: при `sec % 60 == 0` «N мин», иначе `formatTotal(sec * 1000L)` («13:30», «1:02:30»). Целые часы: «60 мин», «90 мин» (без перевода в часы, проще и однозначно; при желании поправить в тесте).
  - При `rounds == 1` часть про шаг не добавляется: «1 отрезок · бег 30 мин».
- `summaryPlacement(viewport: Int, bottom: Int, largeSummary: Int, smallSummary: Int): SummaryPlacement` в `ui/SummaryPlacement.kt` (чистый Kotlin, пиксели):
  - `free = viewport - bottom`; `useLarge = largeSummary <= free`; `summaryHeight = if (useLarge) large else small`; `slot = max(free, summaryHeight)`; `total = slot + bottom`.
  - Возвращает `SummaryPlacement(useLarge, slotHeight, totalHeight)`. `SubcomposeLayout` меряет малый вариант только когда крупный не влез.
- Стили времени: крупный `displayLarge` (57 sp), малый `displayMedium` (45 sp), оба с `TabularNumbers`.
- Неверные настройки: `validate() != null` → `toWorkout()` не вызывается, итог показывает «—» вместо времени и пустой состав с альфой 0,38, блок занимает тот же слот свободного места. Описание для TalkBack — отдельная строка `summary_invalid_description` «Вся тренировка, время не задано», а не «—».
- `StartFab`: `Surface(shape = CircleShape, border = BorderStroke(4.dp, MaterialTheme.colorScheme.background), shadowElevation = …)` размером 72 dp: заливка 64 dp плюс кольцо 4 dp (`BorderStroke` рисует внутри границ), тень только у доступной, цвета `segmentColors(isRun = true, LocalDarkTheme.current)`, недоступная `onSurface` 12 % / 38 % как у M3. Значок `Icons.Filled.PlayArrow`, `contentDescription = stringResource(R.string.button_start)`. Выступ над панелью около 40 dp, отступ снизу колонки формы под выступ около 44 dp.
- `onClick` кнопки: `onStart(settings)`, `enabled = settings.validate() == null`. Обёртки `dropUnlessResumed` нет: кнопка вне `NavHost`, как вкладки; двойное нажатие уже защищено (`permissionPending`, отклонение повторного старта сервисом).
- Порядок TalkBack «Тренировка», «История», «Старт», «Прогресс», «Настройки» даёт заместитель в центральном слоте панели (Solution Overview), проверить вручную.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): код, unit-тесты, документация в репозитории.
- **Post-Completion** (no checkboxes): проверка на телефоне в обеих темах, TalkBack, крупный шрифт.

## Implementation Steps

### Task 1: Строка состава тренировки

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/WorkoutSummary.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/WorkoutSummaryTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] `SummaryLabels`, `segmentCount(workout)`, `workoutSummaryParts(workout, segmentsText, labels)` и `summaryDescription(title, time, parts)` в `WorkoutSummary.kt`
- [x] ресурсы `summary_title` «Вся тренировка», `summary_run`, `summary_walk`, `<plurals name="summary_segments">`, `summary_none` «—», `summary_invalid_description` «Вся тренировка, время не задано»; «мин» берётся из `minutes_short`
- [x] тест `summaryDescription`: «Вся тренировка, 22:00, 11 отрезков, бег 12 мин, шаг 10 мин»
- [x] тесты: целые минуты (неделя 2 → «11 отрезков · бег 12 мин · шаг 10 мин»), «м:сс» (4:30 × 3 → «бег 13:30»), 4:30 × 4 → «бег 18 мин»
- [x] тесты: один повтор без шага (неделя 10 → «1 отрезок · бег 30 мин»), час и больше («1:02:30» и «90 мин»), `segmentCount`
- [x] run tests - must pass before next task

### Task 2: Раздача свободного места

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ui/SummaryPlacement.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/ui/SummaryPlacementTest.kt`

- [x] `SummaryPlacement` и `summaryPlacement(viewport, bottom, largeSummary, smallSummary)` по правилу из Technical Details
- [x] тесты: места хватает (крупный, слот = свободное место, total = viewport)
- [x] тесты: крупный не влез, малый влез (раскрытая карточка); не влез и малый (total > viewport, прокрутка); граница `large == free`
- [x] run tests - must pass before next task

### Task 3: Состояние формы в ветке Idle

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`

- [x] перенести `rememberSaveable(stateSaver = WorkoutSettingsSaver)` из `SettingsContent` в ветку `Idle` с начальным `loadSettings()`, убрать `remember { loadSettings() }` из маршрута `settings`
- [x] `SettingsContent(settings, onSettingsChange, showBackgroundWarning, onOpenAppDetails)`: карточки и шторка программы пишут через `onSettingsChange`; `expanded` и `showProgram` остаются в записи стека
- [x] временно оставить кнопку «Старт» в форме с `onStart(settings)`, чтобы задача собиралась и работала (убирается в Task 4)
- [x] обновить комментарий маршрута `settings` в `WorkoutScreen.kt` (настройки больше не читаются при создании маршрута)
- [x] убедиться, что `SettingsFormTest` и остальные тесты не опираются на старую сигнатуру, поправить при необходимости (не опираются: `SettingsFormTest` проверяет только `WorkoutSettingsSaver`, он остался в `SettingsContent.kt`)
- [x] run tests - must pass before next task

### Task 4: «Старт» в центре нижней панели

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/HistoryContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ProgressContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/AppSettingsContent.kt`

- [x] общий `Box` ветки `Idle`, отступы `safeDrawing.only(Top + Horizontal)` перенесены на него с `Column`
- [x] `BottomBar`: в центральном слоте между «Историей» и «Прогрессом» доступный заместитель «Старта» (роль кнопки, описание, `onClick`, `disabled()`), вкладки по-прежнему по `BottomTab.entries`; обновить KDoc `BottomBar`
- [x] `StartFab` по Technical Details поверх панели с `clearAndSetSemantics {}`, `enabled = settings.validate() == null`, `onClick = { onStart(settings) }`, отступ снизу из `navigationBars` и `BottomBarHeight` (взяты `NavigationBarDefaults.windowInsets` снизу, те же отступы, что у самой панели)
- [x] удалить из `SettingsContent` закреплённый нижний блок, кнопку «Старт» и `StartButtonHeight`, поправить KDoc про `onStart`; предупреждение о фоне и ошибку перенести под карточки в прокручиваемую колонку
- [x] константа `StartFabOverlap` и нижний отступ под выступ в прокручиваемых колонках формы, истории, прогресса и настроек (общая `ScrollContentBottomPadding` = 16 dp + `StartFabOverlap`)
- [x] `BottomTabTest` без изменений, тесты зелёные (новой логики в задаче нет, она в Task 2)
- [x] run tests - must pass before next task

### Task 5: Блок итога и раскладка формы

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`

- [x] `WorkoutSummary` composable: подпись `summary_title` (`onSurfaceVariant`), время `formatTotal(workout.totalMs)` с `TabularNumbers` в крупном или малом стиле, строка `workoutSummaryParts(...).joinToString(" · ")` (`onSurfaceVariant`); при ошибке «—» и альфа 0,38 без вызова `toWorkout()`
- [x] семантика одним узлом: `clearAndSetSemantics { contentDescription = summaryDescription(...) }` (при ошибке `summary_invalid_description`), без `liveRegion`
- [x] `BoxWithConstraints` + `verticalScroll` + `SubcomposeLayout`: нижняя часть (карточки, предупреждение, ошибка, отступ), итог крупный или малый по `summaryPlacement` с `viewport = maxHeight − вертикальные отступы колонки`, итог по центру слота, размещается только выбранный вариант
- [x] тесты новой логики уже в Task 1–2; дополнить их случаями, найденными при сборке раскладки, если такие появятся (новых случаев не нашлось: отрицательное свободное место уже покрыто правилом «малый, даже если не влез»)
- [x] run tests and lint (`./gradlew test assembleDebug lintDebug`) - must pass before next task

### Task 6: Verify acceptance criteria
- [x] verify all requirements from `docs/tasks/20261104-form-summary.md` are implemented (пункты 1–8; пункт 9 — документация, Task 7; TalkBack и раскладка на телефоне — Post-Completion)
- [x] verify edge cases: неверные настройки, один повтор, час и больше, раскрытая карточка (логика покрыта `WorkoutSummaryTest` и `SummaryPlacementTest`, вид на экране — Post-Completion)
- [x] run full test suite: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [x] run lint: `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug` (ошибок нет, предупреждения прежние: GradleDependency, InlinedApi, ObsoleteSdkInt)

### Task 7: [Final] Update documentation
- [x] `README.md`: описание формы (итог над карточками) и нижней панели («Старт» в центре)
- [x] `USER_STORIES.md`: сценарии итога и старта с любой вкладки
- [x] `CLAUDE.md`, «Архитектура»: состояние формы в ветке `Idle` вместо `remember { loadSettings() }` в маршруте, «Старт» в панели не вкладка и без `dropUnlessResumed`, центральный слот панели
- [x] `CLAUDE.md`, «Неочевидное»: почему `StartFab` в общем `Box`, а не внутри `NavigationBar` (`Surface` обрезает и отрисовку, и нажатия); заместитель в центральном слоте ради порядка TalkBack (`Surface` — группа обхода); `StartFabOverlap` на всех вкладках; `BottomBarHeight` повторяет внутреннее значение M3; раздача места через `SubcomposeLayout` и `summaryPlacement`, правило смены размера времени
- [x] перенести задачу в `docs/tasks/completed/`, этот план в `docs/plans/completed/` (задача перенесена; план переносится после ревью)

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification** (на телефоне, в светлой и тёмной теме):
- итог по центру свободного места, карточки у панели; «Старт» выступает над панелью, не обрезан, нажимается по всей площади, в том числе по части над панелью, и не закрывает карточки, предупреждение и текст ошибки
- «+» и «−», барабаны и выбор недели сразу меняют время и состав
- «Старт» с вкладок «История», «Прогресс» и «Настройки» запускает настройки формы, в том числе несохранённые правки; после тренировки форма показывает сохранённые настройки
- неверные настройки: «Старт» серый и недоступен, итог без времени, карточки не прыгают
- раскрытие барабана уменьшает время и не выталкивает карточки под панель; с предупреждением о фоновой работе и текстом ошибки раскладка не ломается
- крупный системный шрифт и маленький экран: всё доступно прокруткой, итог уходит вверх
- на вкладках «История», «Прогресс» и «Настройки» прокрутка до конца выводит последний элемент из-под «Старта», кнопка удаления последней пробежки нажимается
- подписи пяти частей панели на узком экране (360 dp) и с крупным шрифтом не обрезаются
- в альбомной ориентации с боковой системной панелью «Старт» по центру панели
- TalkBack: итог одной фразой (при раскрытой карточке тоже один раз), «Старт» кнопкой между «Историей» и «Прогрессом», недоступный читается недоступным, при неверных настройках «Вся тренировка, время не задано»
