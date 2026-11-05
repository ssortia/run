# Экран тренировки как разворачиваемая шторка

## Overview
- Задача: `docs/tasks/20261105-workout-sheet.md`.
- Сейчас экран отсчёта и тренировки живёт на маршруте `settings`: «Таймер» во время тренировки показывает его во весь экран, форма недоступна до финала, сворачивание — мгновенный переход на вкладку `collapseRoute`, и только кнопкой «Свернуть».
- Нужно: «Таймер» всегда показывает форму; экран тренировки — слой поверх вкладок, который плавно (250–300 мс) выезжает из полоски и уезжает в неё; сворачивается кнопкой «Свернуть», системной «Назад» и свайпом вниз (за пальцем, с порогом по смещению и скорости).
- Сервис, его контракт, `WorkoutState`, схема базы и `RunRecord` не меняются.

## Context (from discovery)
- `ui/WorkoutScreen.kt` — оболочка: `Box` с `windowInsetsPadding` (кроме полноэкранного режима), `Column` с `NavHost`, `WorkoutStrip`, `BottomBar`, поверх `CenterFab`. Маршрут `settings` выбирает `SettingsContent`/`CountdownContent`/`ActiveContent` по `currentState`; `collapseRoute`, `nextCollapseRoute`, `collapseRouteOnStart`, `shouldOpenTimer` + `LaunchedEffect(idle)` с переходом на `settings`.
- `ui/BottomTab.kt` — чистые правила: `isWorkoutFullscreen(state, route)`, `shouldOpenTimer`, `collapseRouteOnStart`, `nextCollapseRoute`, режимы центральной кнопки. Тесты в `app/src/test/java/com/ssortia/runtimer/ui/BottomTabTest.kt`.
- `MainActivity.kt` — `rememberNavController()`, `route`, `fullscreen = isWorkoutFullscreen(state, route)`, по нему `darkStatusIcons` и `navigationBarStyle`; передаёт `route` и `fullscreen` в `WorkoutScreen`.
- `ui/ActiveContent.kt` — `TimerLayout` с кнопкой «Свернуть» (`onCollapse`), фон под системные панели, `safeDrawingPadding()`, центральная колонка с `verticalScroll`.
- `ui/WorkoutStrip.kt`, `WorkoutStripText.kt`, строки `strip_description`, `strip_paused_description`, `strip_countdown_description` («… Открыть таймер»), тесты `WorkoutStripTextTest` читают шаблоны из `strings.xml`.
- Версии: Compose BOM 2024.12.01 (foundation 1.7), `activity-compose` 1.9.3 (`BackHandler`), Navigation Compose 2.8.5.

## Development Approach
- **testing approach**: Regular (сначала код, затем тесты к нему) — по `CLAUDE.md`.
- каждую задачу доводить до конца перед следующей, маленькими шагами.
- **CRITICAL: каждая задача с изменением логики включает новые или обновлённые тесты.** Логика (правила разворачивания, решение свайпа, полноэкранный режим) — чистые функции без Compose и Android API в `ui/BottomTab.kt`, покрываются unit-тестами. Compose-код держим тонким, он тестами не покрыт (соглашение проекта).
- **CRITICAL: все тесты проходят перед следующей задачей.**
- **CRITICAL: план обновляется при изменении объёма.**
- после каждой задачи: `JAVA_HOME=~/.local/jdk-17 ./gradlew test`.

## Testing Strategy
- **unit tests**: `BottomTabTest` (полноэкранный режим, разворачивание при старте, решение свайпа), `WorkoutStripTextTest` (новое описание полоски).
- **e2e/UI tests**: в проекте нет, эмулятора нет. Анимация, свайп, TalkBack, системные панели проверяются только вручную на телефоне (Post-Completion).

## Progress Tracking
- отмечать выполненное `[x]` сразу.
- новые задачи с префиксом ➕, блокеры с ⚠️.
- держать план в соответствии с фактической работой.

## Solution Overview
- **Слой, а не маршрут.** Экран тренировки уходит из `NavHost` в слой оболочки поверх `Column` с вкладками, полоской и панелью. Маршрут `settings` всегда показывает форму. Отдельный маршрут `workout` отвергнут: экран, идущий за пальцем, и доезд от текущего смещения естественно делаются одним `Animatable` смещения, а переход `NavHost` начинает анимацию с нуля; анимации `NavHost` к тому же отключены намеренно (`dropUnlessResumed`).
- **Причины, по которым в `CLAUDE.md` оверлей был запрещён, закрываются явно:** содержимое под развёрнутым слоем скрыто от TalkBack (`clearAndSetSemantics {}` на `Column` вкладок и на `CenterFab` уже скрыта), «Назад» обрабатывает свой `BackHandler(enabled = expanded)`.
- **Состояние.** Признак `workoutExpanded` в `rememberSaveable` в `MainActivity`: от него зависят системные панели, и источник должен быть один. Начальное значение `initialWorkoutExpanded(state)` (`state !is Idle`): новый экземпляр activity во время тренировки открывается с развёрнутым экраном. `MainActivity` считает `fullscreen = isWorkoutFullscreen(state, workoutExpanded)` один раз и передаёт в `WorkoutScreen` и `fullscreen`, и сырой `expanded`, и `onExpandedChange`. Оболочка по `fullscreen` решает, составлять ли слой, включать ли `BackHandler` и скрывать ли вкладки от TalkBack, а сырой `expanded` читает только `expand()` (откуда начинать анимацию); формулу `!idle && expanded` оболочка не повторяет. Смещение — `Animatable<Float>` в пикселях в `WorkoutScreen` (`0` — развёрнут, высота области — свёрнут), не сохраняется: после поворота развёрнутый слой стоит на месте, а слой, сворачивавшийся в момент поворота, снова развёрнут целиком (`expanded` ещё `true`) — это допустимо.
- **`rememberNavController()` возвращается в `WorkoutScreen`.** Его поднимали в `MainActivity` ради выбора системных панелей по маршруту; теперь панели зависят от `expanded`, маршрут в `MainActivity` не нужен. Параметры `navController` и `route` у `WorkoutScreen` убираются, маршрут читается в оболочке через `currentBackStackEntryAsState()`.
- **Полноэкранный режим** = `!Idle && expanded`. `expanded` становится `true` в начале разворачивания и `false` в конце сворачивания, поэтому системные панели и значки переключаются в начале разворачивания и в конце сворачивания, пока слой закрывает экран.
- **Куда сворачивается:** на ту вкладку, что под слоем. Стек при развороте и сворачивании не трогается, правила `collapseRoute` удаляются.

## Technical Details
- **Чистые правила в `ui/BottomTab.kt`:**
  - `isWorkoutFullscreen(state: WorkoutState, expanded: Boolean): Boolean = state !is Idle && expanded` (вместо версии с `route`).
  - `initialWorkoutExpanded(state: WorkoutState): Boolean = state !is Idle` — начальное значение `workoutExpanded` у нового экземпляра activity.
  - `shouldOpenTimer` → `shouldExpandWorkout(wasIdle, idle)`: та же формула `wasIdle && !idle`, новое имя и KDoc.
  - `shouldCollapseSheet(offsetPx: Float, heightPx: Float, velocityPx: Float, flingVelocityPx: Float): Boolean` — при отпускании: флинг вниз (`velocityPx >= flingVelocityPx`) → свернуть; флинг вверх (`velocityPx <= -flingVelocityPx`) → вернуть; иначе по смещению `offsetPx >= heightPx * SHEET_COLLAPSE_FRACTION` (0,25). При `heightPx <= 0` — не сворачивать.
  - удалить `collapseRouteOnStart`, `nextCollapseRoute`.
- **Константы Compose в `WorkoutScreen.kt`:** `SheetAnimationMs = 280`, кривая `FastOutSlowInEasing`; порог флинга `SheetFlingVelocity = 125.dp` в секунду (как у шторок M3), переводится в пиксели через `LocalDensity`.
- **Раскладка оболочки:**
  ```
  Box(fillMaxSize, onSizeChanged { heightPx = it.height })   // высота области для смещения
  ├─ Box(fillMaxSize, windowInsetsPadding(Top+Horizontal), clearAndSetSemantics{} при fullscreen)
  │   ├─ Column: NavHost(weight 1f) / WorkoutStrip (вне Idle) / BottomBar
  │   └─ CenterFab
  └─ (если fullscreen) слой тренировки, порядок модификаторов важен:
        offset { IntOffset(0, offset) }      // первым: область касаний едет вместе со слоем
        .nestedScroll(connection)            // свайп поверх прокручиваемой колонки (Task 3)
        .draggable(Vertical)                 // свайп вне колонки (Task 3)
        .pointerInput(Unit) {}               // Task 1: касания не проходят сквозь слой во вкладки
        .semantics { paneTitle = «Тренировка» }
        BackHandler(enabled = true) внутри слоя; CountdownContent / ActiveContent
  ```
  Отступы `safeDrawing` у внешнего `Box` больше не зависят от полноэкранного режима: слой тренировки лежит вне внутреннего `Box` и сам поглощает их в `TimerLayout`. `onSizeChanged`, а не `BoxWithConstraints`: высота нужна только для смещения, лишняя субкомпозиция над `NavHost` не нужна.
- **`BackHandler` внутри слоя**, то есть в порядке композиции после `NavHost`. Navigation Compose 2.8 регистрирует свой обработчик «Назад» при стеке больше одной записи, а диспетчер вызывает последний добавленный включённый; обработчик, составленный раньше `NavHost`, на «Истории» проиграл бы ему: «Назад» сняла бы запись стека вместо сворачивания. Причину оставить комментарием в коде.
- **Пустой `pointerInput`** на корне слоя с Task 1: у `TimerLayout` только `background`, и без обработчика указателя касание пустого места слоя дошло бы до формы под ним. Комментарий в коде, чтобы его не сочли лишним после появления `draggable`.
- **`paneTitle`** у слоя (строка `pane_workout` «Тренировка»): TalkBack объявляет появление панели и переводит фокус, иначе после разворачивания фокус остаётся на скрытом узле полоски.
- **Операции (в `WorkoutScreen`, `rememberCoroutineScope`), анимация везде `tween(SheetAnimationMs, FastOutSlowInEasing)`:**
  - `expand()`: если `!expanded` — `offset.snapTo(heightPx)` и `onExpandedChange(true)`; затем `offset.animateTo(0f)`. Из сворачивания (`expanded` ещё `true`) слой разворачивается от текущего смещения, без скачка вниз.
  - `collapse()`: `offset.animateTo(heightPx)`, затем `onExpandedChange(false)`. Если анимацию прервал новый `expand()`, `CancellationException` не даст выставить `false`. Повторный вызов идемпотентен: та же цель.
  - `settle(velocity)` после отпускания: `shouldCollapseSheet(...)` → `collapse()`, иначе `offset.animateTo(0f)` тем же `tween`.
  - перетаскивание: `offset.snapTo((offset.value + delta).coerceIn(0f, heightPx))` через `rememberDraggableState`.
  - `NestedScrollConnection` слоя (по образцу `ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection` из M3): `onPreScroll` — при `available.y < 0` и смещении > 0 поднимает слой и поглощает дельту; `onPostScroll` — при `available.y > 0` и `source == NestedScrollSource.UserInput` опускает слой; `onPreFling` — если смещение > 0, `settle(available.y)` и поглощает скорость; `onPostFling` — `settle(available.y)`, если смещение > 0. В foundation 1.7 `verticalScroll` начинает перетаскивание и тогда, когда прокручивать некуда, и непрокрученный остаток уходит только по nested scroll, мимо `draggable`: без соединения свайп по цифрам времени не сворачивал бы экран и в портретной ориентации.
- **Старт и финал:** `LaunchedEffect(idle)` — при `shouldExpandWorkout(wasIdle, idle)` вызывает `expand()`; при `idle` (финал или «Стоп») `onExpandedChange(false)` и `offset.snapTo(0f)` без анимации: слоя уже нет, он составляется только вне `Idle`. Новый экземпляр activity во время тренировки получает `expanded = true` из начального значения в `MainActivity`, `offset` = 0.
- **Колбэки слоя** («Свернуть», «Стоп», «Пауза») больше не внутри `NavHost`, `dropUnlessResumed` у «Свернуть» убирается; повтор безопасен (сворачивание идемпотентно, команды сервиса явные).
- **Полоска:** `onOpen` → `expand()`. Описания «… Открыть таймер» → «… Открыть тренировку». На «Таймере» во время тренировки полоска теперь видна: `ScrollContentBottomPadding` не меняется, `FormLayout` раскладывает итог по меньшей высоте сам.

## What Goes Where
- **Implementation Steps**: код, тесты, документация в репозитории.
- **Post-Completion**: проверка на телефоне.

## Implementation Steps

### Task 1: Экран тренировки отдельным слоем поверх вкладок (без анимации)

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/BottomTab.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ActiveContent.kt` (KDoc `TimerLayout` про «Свернуть»)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/BottomTabTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutStripTextTest.kt`

- [x] `BottomTab.kt`: `isWorkoutFullscreen(state, expanded)`, `initialWorkoutExpanded(state)`, переименовать `shouldOpenTimer` в `shouldExpandWorkout`, удалить `collapseRouteOnStart` и `nextCollapseRoute`, поправить комментарий к `ROUTE_SETTINGS` (всегда форма)
- [x] `MainActivity.kt`: `rememberNavController()`, `route` и `currentBackStackEntryAsState` убрать (контроллер возвращается в `WorkoutScreen`); `workoutExpanded` в `rememberSaveable { mutableStateOf(initialWorkoutExpanded(state)) }`, `fullscreen = isWorkoutFullscreen(state, workoutExpanded)`; передать в `WorkoutScreen` `fullscreen`, `expanded` и `onExpandedChange`; поправить комментарий у бывшего `navController` (строки ~82–85)
- [x] `WorkoutScreen.kt`: `rememberNavController()` и маршрут через `currentBackStackEntryAsState()` внутри оболочки, параметры `navController`/`route` убрать; маршрут `settings` всегда `SettingsContent`; убрать `collapseRoute` и его `LaunchedEffect`; раскладка по Technical Details (внешний `Box` без отступов → внутренний `Box` с `windowInsetsPadding`: вкладки, полоска вне `Idle`, панель, `CenterFab` всегда → слой при `fullscreen`); «Свернуть» = `onExpandedChange(false)` без `dropUnlessResumed`; полоска `onOpen` = `onExpandedChange(true)`; при старте `shouldExpandWorkout` → `onExpandedChange(true)`, при `idle` → `false`
- [x] `WorkoutScreen.kt`: `BackHandler { onExpandedChange(false) }` внутри слоя (после `NavHost` в композиции) с комментарием о порядке; пустой `pointerInput(Unit) {}` на корне слоя с комментарием; `semantics { paneTitle }` у слоя (строка `pane_workout`); внутренний `Box` получает `clearAndSetSemantics {}` при `fullscreen`
- [x] устаревшие комментарии: KDoc `WorkoutScreen` (оболочка, «Таймер» показывает форму или тренировку), комментарий о настройках формы («форма во время тренировки скрыта»), комментарий у маршрута `settings` (общая запись формы и тренировки), KDoc `BottomBar` («Назад» ведёт на «Таймер (форму или экран тренировки)»), KDoc `WorkoutStrip` («открывает „Таймер“»), KDoc `TimerLayout` в `ActiveContent.kt` («возвращает на прошлую вкладку»)
- [x] `strings.xml`: в `strip_description`, `strip_paused_description`, `strip_countdown_description` «Открыть таймер» → «Открыть тренировку»; новая `pane_workout` «Тренировка»
- [x] тесты `BottomTabTest`: `isWorkoutFullscreen` (Idle при любом `expanded` — нет; Countdown/Active с `expanded` true/false), `initialWorkoutExpanded` (Idle, Countdown, Active), `shouldExpandWorkout` (переход из Idle, поворот без перехода, финал); удалить тесты `collapseRouteOnStart`/`nextCollapseRoute`
- [x] тесты `WorkoutStripTextTest`: обновить ожидаемые описания
- [x] `JAVA_HOME=~/.local/jdk-17 ./gradlew test` — должны пройти

### Task 2: Анимация разворачивания и сворачивания

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`

- [x] высота области через `onSizeChanged` внешнего `Box`; `Animatable(0f)` смещения в `remember`, `rememberCoroutineScope`
- [x] `expand()` (снап вниз только при `!expanded`) и `collapse()` по Technical Details (`tween(SheetAnimationMs, FastOutSlowInEasing)`, `onExpandedChange(false)` только после доезда), слой со смещением `Modifier.offset { IntOffset(0, offset.value.roundToInt()) }` первым модификатором
- [x] перевести на них «Свернуть», `BackHandler`, полоску и автоматическое разворачивание при старте; при `idle` — `snapTo(0f)` и `onExpandedChange(false)` без анимации
- [x] логики без Compose здесь не добавляется (тесты к Task 2 не требуются: правила в Task 1 и Task 3); убедиться, что существующие тесты проходят
- [x] `JAVA_HOME=~/.local/jdk-17 ./gradlew test` — должны пройти

### Task 3: Свайп вниз

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/BottomTab.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/BottomTabTest.kt`

- [ ] `BottomTab.kt`: `SHEET_COLLAPSE_FRACTION = 0.25f` и `shouldCollapseSheet(offsetPx, heightPx, velocityPx, flingVelocityPx)` с KDoc (флинг важнее смещения)
- [ ] `WorkoutScreen.kt`: `Modifier.draggable(rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, heightPx)) } }, Orientation.Vertical, onDragStopped = { v -> settle(v) })` на слое после `offset`; `SheetFlingVelocity = 125.dp` в пиксели через `LocalDensity`
- [ ] `WorkoutScreen.kt`: `NestedScrollConnection` слоя по Technical Details и `Modifier.nestedScroll(connection)` между `offset` и `draggable`, комментарий: зачем (прокручиваемая колонка `TimerLayout` перехватывает жест и в портретной ориентации)
- [ ] тесты `shouldCollapseSheet`: смещение ниже и выше порога без скорости, ровно на пороге, флинг вниз при малом смещении, флинг вверх при большом смещении, скорость чуть ниже порога, `heightPx = 0`
- [ ] `JAVA_HOME=~/.local/jdk-17 ./gradlew test` — должны пройти

### Task 4: Verify acceptance criteria
- [ ] «Таймер» во время тренировки показывает форму с полоской и панелью; форма правится, правки не влияют на полоску на отсчёте (`startedWorkout` из `SettingsStore`)
- [ ] разворачивание: полоска и старт; сворачивание: «Свернуть», «Назад», свайп — одной анимацией, на вкладку под слоем
- [ ] системные панели и значки по `isWorkoutFullscreen(state, expanded)`; финал и «Стоп» убирают слой
- [ ] в коде не осталось `collapseRoute`, `shouldOpenTimer`, `isWorkoutFullscreen(state, route)`, `navController` в `MainActivity`; формула `!idle && expanded` есть только в `isWorkoutFullscreen`
- [ ] `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug` и `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug` — проходят

### Task 5: [Final] Update documentation
- [ ] `USER_STORIES.md`: US-17 (строки ~234–241, в том числе цитаты «Открыть таймер») — форма доступна во время тренировки на «Таймере», экран тренировки открывается из полоски, сворачивается кнопкой, свайпом вниз и «Назад»
- [ ] `README.md`: описание навигации во время тренировки и строка таблицы файлов (~124)
- [ ] `CLAUDE.md`: слой тренировки вместо выбора по `state` на маршруте `settings`; снять «Оверлей экрана тренировки поверх `NavHost` намеренно не используется», «Своего `BackHandler` нет», правила `collapseRoute`/`collapseRouteOnStart`/`nextCollapseRoute`, «При старте открывается „Таймер“», «`rememberNavController()` поднят в `MainActivity`… по текущему маршруту `MainActivity` выбирает системные панели»; описать `workoutExpanded` в `MainActivity` (`initialWorkoutExpanded`, `fullscreen` и `expanded` параметрами), `isWorkoutFullscreen(state, expanded)`, анимацию (`expand()` без скачка из сворачивания) и свайп (`shouldCollapseSheet`, `draggable` плюс `NestedScrollConnection` из-за `verticalScroll` в `TimerLayout`), `BackHandler` внутри слоя после `NavHost`, пустой `pointerInput`, `paneTitle`, скрытие вкладок от TalkBack, отсутствие `dropUnlessResumed` у колбэков слоя, поворот во время сворачивания; поправить абзац про сброс `rememberSaveable` формы после тренировки (форма теперь не покидает композицию); поправить про уведомление (новый экземпляр — развёрнутый слой, живая activity — как оставили)
- [ ] перенести задачу в `docs/tasks/completed/` (осталась только ручная проверка), план — в `docs/plans/completed/`

## Post-Completion
*Ручная проверка на телефоне в обеих темах:*
- «Таймер» во время тренировки — форма с полоской; нажатие на полоску плавно разворачивает тренировку от низа
- «Свернуть», «Назад» и свайп вниз плавно сворачивают на ту же вкладку; короткий медленный свайп возвращает экран наверх; быстрый флинг сворачивает даже с малым смещением; вверх дальше полного разворота экран не уходит
- «Стоп» и «Пауза» срабатывают сразу после анимации; двойное нажатие «Свернуть» не ломает слой
- старт с «Истории» разворачивает экран, после сворачивания снова «История»; старт с «Таймера» — после сворачивания форма
- значки строки состояния и стиль системной панели не мелькают при разворачивании и сворачивании
- TalkBack на развёрнутом экране не находит форму, полоску и панель под ним; после сворачивания находит
- поворот экрана сохраняет развёрнутость; нажатие на уведомление открывает приложение как оставили; после гибели activity — с развёрнутым экраном
- свайп вниз по крупным цифрам времени и подписям сворачивает экран в портретной и альбомной ориентации; в альбомной, если колонка прокручена, она сначала докручивается вверх, потом сворачивается слой
- развернуть слой с «Истории» и нажать «Назад»: слой свёрнут, открыта «История» (не «Таймер»)
- нажать на полоску во время сворачивания: слой разворачивается от текущего места без скачка вниз
- касание пустого места развёрнутого экрана не нажимает ничего на вкладке под ним
- TalkBack после разворачивания объявляет панель «Тренировка» и ставит фокус в неё; после сворачивания фокус не теряется
- на «Таймере» во время тренировки итог формы не налезает на карточки при полоске, крупное время сменяется малым, если не помещается
- после финала слой исчезает, вкладка та же, в центре «Старт»; раскрытая карточка формы не сбрасывается
