# Навигация во время тренировки

## Overview
- Во время отсчёта и тренировки можно свернуть экран тренировки и перейти на любую вкладку: «История», «Прогресс», «Настройки».
- «Таймер» во время тренировки показывает экран отсчёта или тренировки во весь экран без нижней панели, слева сверху кнопка «Свернуть» (возврат на прошлую вкладку, по умолчанию «История»).
- На остальных вкладках над панелью полоска тренировки цвета отрезка (отрезок, повтор, оставшееся время, линия «пройдено»); нажатие открывает «Таймер».
- Центральная кнопка панели (место «Старта») во время тренировки ставит на паузу и продолжает, в отсчёте и после финального сигнала недоступна.
- Настройки приложения во время тренировки подписаны «Применится к следующей тренировке», пробного сигнала нет.
- Задача: `docs/tasks/20261105-navigate-during-workout.md`. Макет: ряд «Навигация во время тренировки» (`Nav-*`) в https://claude.ai/artifact/WKnsAn8dCvkNbCqtUVcdik.

## Context (from discovery)
- `ui/WorkoutScreen.kt`: `when (state)`, ветка `Idle` создаёт `rememberNavController()`, `NavHost`, `BottomBar` со слотом-заместителем «Старта» и `StartFab`; `Countdown`/`Active` рисуют `CountdownContent`/`ActiveContent` во весь экран.
- `ui/ActiveContent.kt`: `TimerLayout` сам красит фон под системные панели и поглощает `safeDrawingPadding()`; `pauseButtonRes`, `pauseEnabled` — чистые функции с тестами в `PauseButtonTest`.
- `MainActivity.kt`: `idle` управляет отпусканием пробного `Beeper` и `navigationBarStyle`; `darkStatusIcons(state, darkTheme)` в `ui/Theme.kt` выбирает значки строки состояния по состоянию.
- `ui/BottomTab.kt`: маршруты, `BottomTab`, `startSlotIndex()`, `bottomTabOf()`; `BottomTabTest`.
- `PresetRowText.kt` формирует строку параметров «Бег 1:00 · Шаг 2:00 · ×7»; `formatTime` в `WorkoutState.kt`.
- Соглашения (`CLAUDE.md`): логика в чистых функциях без Android API с unit-тестами, подписи параметрами; Compose-код тонкий и тестами не покрыт; строки и комментарии на русском.

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

## Testing Strategy
- **unit tests**: чистые функции (текст полоски, режим центральной кнопки, признак полноэкранной тренировки, значки строки состояния). Compose-код, `MainActivity` и навигация тестами не покрыты по соглашению проекта и проверяются на телефоне.
- **e2e tests**: в проекте нет.

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview
- **Одна оболочка для всех состояний.** `NavHost`, `BottomBar`, полоска и центральная кнопка больше не живут в ветке `Idle`: оболочка составляется всегда, а маршрут `settings` («Таймер») выбирает содержимое по `state` (форма, `CountdownContent`, `ActiveContent`). Так `NavController` и состояния вкладок переживают старт и финал. Отдельного маршрута для тренировки нет: экран по-прежнему выбирается состоянием сервиса.
- **Полноэкранный режим** — признак `isWorkoutFullscreen(state, route)`: не `Idle` и текущий маршрут `settings`. В нём оболочка не показывает панель, полоску и центральную кнопку и не применяет к `Box` отступы `safeDrawing` (их поглощает `TimerLayout`, фон отрезка уходит под системные панели, как сейчас). Признак нужен и `MainActivity` (стиль системной панели и значки строки состояния), поэтому `rememberNavController()` поднимается в `MainActivity` и передаётся в `WorkoutScreen`.
- **Почему не оверлей поверх оболочки.** Экран тренировки поверх `NavHost` оставил бы под собой составленную форму в дереве доступности и потребовал бы своего `BackHandler`; выбор содержимого маршрута по состоянию проще и оставляет «Назад» стеку.
- **Центральная кнопка** получает режим из чистой функции `centerButtonMode(state)`: `START` в `Idle`, `PAUSE`/`RESUME` в `Active` при `pauseEnabled`, `PAUSE_DISABLED` в `Countdown` и после финального сигнала. Видимая кнопка и заместитель в слоте берут из режима значок, цвета, описание и `enabled`.
- **Полоска** — тонкий Compose-компонент над `BottomBar`; текст и описание для TalkBack собирает чистая `workoutStripText`.
- **Переход на «Таймер» при старте** — только на переходе `Idle → не Idle`, а не при каждом составлении: признак прошлого состояния хранится в `rememberSaveable`, иначе поворот экрана во время тренировки возвращал бы на «Таймер».
- **Форма и экран тренировки делят одну запись стека `settings`.** Поэтому `rememberSaveable` формы (раскрытая карточка, шторка, прокрутка) после тренировки сбрасывается: `SettingsContent` покидает композицию внутри записи, а сохраняется состояние только при уходе самой записи. Это принято, как и сейчас, когда после тренировки форма открывается заново.
- **Параметры тренировки в полоске на отсчёте** берутся из `settings` оболочки: `onStart` сохраняет их до старта, а форма во время тренировки скрыта, поэтому они совпадают с запущенными.

## Technical Details
- `ui/WorkoutStrip.kt` (чистая часть, без Android API):
  ```kotlin
  data class WorkoutStripLabels(
      val run: String, val walk: String,             // «Бег», «Шаг» для видимой строки
      val runLower: String, val walkLower: String,   // «бег», «шаг» для описания
      val paused: String,            // шаблон «%s · пауза» (segment_paused)
      val countdown: String,         // «Старт через»
      val round: String,             // шаблон «Повтор %1$d из %2$d» (round_progress)
      val roundLower: String,        // шаблон «повтор %1$d из %2$d» для описания
      val description: String,       // «Тренировка: %1$s, осталось %2$s, %3$s. Открыть таймер»
      val pausedDescription: String, // «Тренировка на паузе: %1$s, осталось %2$s, %3$s. Открыть таймер»
      val countdownDescription: String, // «Тренировка: старт через %s. Открыть таймер»
      val minutes: (Int) -> String,  // склонение из <plurals>: «1 минута», «2 минуты»
      val seconds: (Int) -> String,
  )
  data class WorkoutStripText(
      val title: String, val subtitle: String, val time: String,
      val progress: Float?,          // null во время отсчёта: линии нет
      val description: String,
  )
  fun workoutStripText(state: WorkoutState, details: String, labels: WorkoutStripLabels): WorkoutStripText?
  ```
  `null` для `Idle`. `details` — строка параметров для отсчёта из `workoutDetailsText` (ниже), в `Countdown` позиции ещё нет; при неверных настройках (`settings.validate() != null`, во время тренировки не бывает) передаётся пустая строка. Время отрезка `formatTime(position.remainingMs)`, время отсчёта `formatTime(seconds * 1000)` («0:03»). В описании секунды округляются так же, как на экране (`wholeSeconds`, вверх), ноль минут и ноль секунд не произносятся («осталось 45 секунд», «осталось 2 минуты»).
- `fun workoutDetailsText(workout: Workout, runLabel: String, walkLabel: String): String` в `PresetRowText.kt` — «Бег 2:00 · Шаг 2:00 · ×6», шаг не показывается при одном повторе, как сейчас. `presetRowText` вызывает её, а не повторяет формат. В Compose: `settings.toWorkout()` из оболочки.
- `enum class CenterButtonMode { START, PAUSE, RESUME, PAUSE_DISABLED }`, `fun centerButtonMode(state: WorkoutState): CenterButtonMode` и `@StringRes fun centerButtonLabelRes(mode)` («Старт», «Пауза», «Продолжить»; у `PAUSE_DISABLED` «Пауза») рядом с `pauseEnabled` в `ActiveContent.kt`, тесты в `PauseButtonTest`.
- `fun isWorkoutFullscreen(state: WorkoutState, route: String?): Boolean` в `ui/BottomTab.kt`. `route == null` (стек ещё не готов) считается «Таймером»: стартовый маршрут — `settings`.
- `darkStatusIcons(state, darkTheme, fullscreen)`: вне полноэкранной тренировки значки как в `Idle` (`!darkTheme`), в полноэкранной — по цвету отрезка, как сейчас.
- Последняя вкладка: `rememberSaveable { mutableStateOf(ROUTE_HISTORY) }` в оболочке, обновляется `LaunchedEffect(route)` при маршруте, отличном от `settings`. «Свернуть» — `navigate(lastRoute) { popUpTo(ROUTE_SETTINGS) { saveState = true }; launchSingleTop = true; restoreState = true }`, колбэк обёрнут в `dropUnlessResumed` (экран внутри `NavHost`).
- Полоска: `Modifier.heightIn(min = 56.dp)` (крупный системный шрифт не обрезает текст), строки `maxLines = 1` с многоточием. Левая и правая части `weight(1f)` каждая, между ними `Spacer(width = 88.dp)`: промежуток всегда по центру, под центральной кнопкой, как бы ни менялась ширина текста. Семантика: `Modifier.clearAndSetSemantics { contentDescription = …; role = Role.Button; onClick(label = null) { onOpen(); true } }` и отдельно `clickable` для касания — `clearAndSetSemantics` снаружи `clickable` стёр бы действие нажатия, и двойное касание TalkBack не открыло бы «Таймер» (у `StartFab` пустой `clearAndSetSemantics {}` стоит намеренно, полоске этот шаблон не подходит).
- Значки: в `material-icons-core` паузы нет, создать `res/drawable/ic_pause.xml` (Material Symbols); стрелки `Icons.Filled.KeyboardArrowDown`/`KeyboardArrowUp` и `Icons.Outlined.Info` из набора.
- Новые строки: `button_collapse` «Свернуть», `hint_next_workout` «Применится к следующей тренировке», шаблоны описаний полоски, строчный шаблон повтора, `<plurals>` минут и секунд для описания.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): код, unit-тесты, документация в репозитории.
- **Post-Completion** (no checkboxes): проверка на телефоне — навигация, звук и сигналы при выключенном экране, TalkBack, системные панели.

## Implementation Steps

### Task 1: Чистая логика полоски, центральной кнопки и полноэкранного режима

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutStrip.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ActiveContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/BottomTab.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/Theme.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/PresetRowText.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt` (только новый аргумент `darkStatusIcons`)
- Create: `app/src/test/java/com/ssortia/runtimer/ui/WorkoutStripTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/PresetRowTextTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/PauseButtonTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/BottomTabTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/ThemeTest.kt` (если там проверяется `darkStatusIcons`)

- [x] `workoutDetailsText(workout, runLabel, walkLabel)` в `PresetRowText.kt`, `presetRowText` вызывает её
- [x] `WorkoutStripLabels`, `WorkoutStripText` и `workoutStripText` в `ui/WorkoutStrip.kt` по Technical Details
- [x] `CenterButtonMode`, `centerButtonMode(state)` с учётом `pauseEnabled` и `centerButtonLabelRes(mode)` в `ActiveContent.kt`
- [x] `isWorkoutFullscreen(state, route)` в `BottomTab.kt`
- [x] третий параметр `fullscreen` у `darkStatusIcons`, вызов в `MainActivity` пока передаёт `state !is Idle` (поведение не меняется до Task 2)
- [x] тесты `workoutDetailsText`: с шагом, один повтор без шага; `presetRowText` не изменился
- [x] тесты `workoutStripText`: `Idle` → `null`; бег и шаг (заголовок, «Повтор N из M», время, прогресс); пауза («Бег · пауза», точная фраза «Тренировка на паузе: бег, осталось …, повтор 3 из 6. Открыть таймер»); отсчёт («Старт через», параметры, «0:03», без прогресса); точная фраза описания со строчными «бег» и «повтор»; «1 минута 24 секунды», ровно «2 минуты» без секунд, «45 секунд» без минут, остаток меньше секунды округляется вверх как у `formatTime`
- [x] тесты `centerButtonMode` и `centerButtonLabelRes`: `Idle` → `START`, `Active` → `PAUSE`/`RESUME`, отсчёт и нулевой остаток → `PAUSE_DISABLED`
- [x] тесты `isWorkoutFullscreen` (все состояния × маршруты, `null`) и `darkStatusIcons` с новым параметром
- [x] run tests - must pass before next task

### Task 2: Общая оболочка с навигацией для всех состояний

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ActiveContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] поднять `rememberNavController()` в `MainActivity`, передать в `WorkoutScreen` параметром
- [x] `MainActivity` считает `fullscreen = isWorkoutFullscreen(state, route)` по `navController.currentBackStackEntryAsState()`; ключ `LaunchedEffect` системных панелей `fullscreen` вместо `idle`: `navigationBarStyle` прозрачный `light`/`dark` вне полноэкранного режима, `auto` с подложкой в нём; `darkStatusIcons(state, darkTheme, fullscreen)`. В этой же задаче, иначе на вкладках во время тренировки под `NavigationBar` была бы подложка и двухцветная полоса
- [x] убрать `when (state)` верхнего уровня: оболочка (`Box`, `Column` с `NavHost` и `BottomBar`, `StartFab`) составляется всегда; `settings` (`WorkoutSettingsSaver`) остаётся в оболочке и после тренировки не перечитывается
- [x] маршрут `settings` выбирает содержимое по `state`: `SettingsContent`, `CountdownContent`, `ActiveContent`
- [x] при `isWorkoutFullscreen` не показывать `BottomBar` и `StartFab`, а у `Box` не применять `windowInsetsPadding(safeDrawing...)`
- [x] кнопка «Свернуть» (`TextButton` цветом `colors.content`, стрелка вниз) слева сверху в `TimerLayout` через новый параметр `onCollapse` у `ActiveContent` и `CountdownContent`; колбэк в `dropUnlessResumed`
- [x] последняя вкладка в `rememberSaveable`, «Свернуть» переходит на неё по схеме панели
- [x] переход на «Таймер» только на переходе из `Idle` (прошлое значение в `rememberSaveable`), не при повороте экрана
- [x] строка `button_collapse`
- [x] обновить устаревшие комментарии: в `WorkoutScreen.kt` («Контроллер создаётся в ветке Idle…», «Ветка покидает композицию при старте…», про отступы общего `Box`), в `MainActivity` («На отсчёте и тренировке фон цветной…», отпускание пробного сигнала по уходу из `Idle`)
- [x] тестов нет (Compose-код), запустить тесты и `lintDebug` — должны пройти

### Task 3: Центральная кнопка-пауза и полоска тренировки

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutStrip.kt`
- Create: `app/src/main/res/drawable/ic_pause.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [x] `res/drawable/ic_pause.xml` (Material Symbols, в `material-icons-core` паузы нет)
- [x] `StartFab` принимает `CenterButtonMode`: значок `PlayArrow` или `ic_pause`, цвета `segmentColors` текущего отрезка во время тренировки, недоступная как недоступный «Старт» (38 %, заливка `compositeOver` фона)
- [x] заместитель в центральном слоте `BottomBar` берёт описание из `centerButtonLabelRes(mode)` и `enabled`; `onClick` вызывает `onStart`, `onPause` или `onResume`; KDoc `BottomBar` и `StartFab` обновить: заместитель уже не только «Старта»
- [x] Composable `WorkoutStrip` в `WorkoutStrip.kt` по Technical Details: `heightIn(min = 56.dp)`, фон и текст `segmentColors` (отсчёт `countdownColors`), слева значок `ic_timer` и две строки `maxLines = 1`, промежуток 88 dp между частями с `weight(1f)`, справа время `TabularNumbers` и `KeyboardArrowUp`, линия «пройдено» 3 dp снизу; семантика через `clearAndSetSemantics` с описанием, ролью и `onClick`, без `liveRegion`
- [x] полоска в `Column` между `NavHost` и `BottomBar`, только вне полноэкранного режима и не в `Idle`; нажатие — переход на `settings` по схеме панели
- [x] строки шаблонов описания, строчные подписи и `<plurals>` минут и секунд; `WorkoutStripLabels` собирается в Compose из ресурсов, `details` — `workoutDetailsText(settings.toWorkout(), …)` при `settings.validate() == null`
- [x] тесты на изменения чистой части, если появились (чистая часть не менялась, новых тестов нет); запустить тесты и `lintDebug` — должны пройти

### Task 4: Настройки приложения и пробный сигнал во время тренировки

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/AppSettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] пробный сигнал: `saveVolumeAndPreview` сохраняет громкость, но при `WorkoutService.state.value !is WorkoutState.Idle` не играет `Beeper` и не показывает `Toast` `warning_muted` (одно сравнение, без отдельной функции); отпускание по уходу из `Idle` остаётся
- [x] `AppSettingsContent` получает `workoutRunning: Boolean`, под группами «Сигналы» и «Тренировка» подпись `hint_next_workout` (`bodySmall`, `onSurfaceVariant`, значок `Icons.Outlined.Info` скрыт от TalkBack)
- [x] строка `hint_next_workout`
- [x] тестов нет (Compose и Activity), запустить тесты и `lintDebug` — должны пройти

### Task 5: Verify acceptance criteria
- [ ] verify all requirements from Overview are implemented
- [ ] verify edge cases: поворот экрана во время тренировки не меняет вкладку; финал на чужой вкладке оставляет её, в центре снова «Старт»; «Стоп» на «Таймере» показывает форму; повторный старт с «Истории» открывает «Таймер»
- [ ] run full test suite: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [ ] run lint: `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`

### Task 6: [Final] Update documentation
- [ ] `USER_STORIES.md`: новая история «Свернуть тренировку» (вкладки, полоска, «Свернуть», пауза в центре, настройки к следующей тренировке); в US-11 пауза доступна и из центральной кнопки
- [ ] `README.md`: описание навигации и таблица файлов (`WorkoutStrip.kt`, `ic_pause.xml`)
- [ ] `CLAUDE.md`: снять «экраны отсчёта и тренировки маршрутами не становятся… ветка `Idle` покидает композицию, стек сбрасывается сам», «после тренировки контроллер создаётся заново», правило стиля системной панели по `Idle`, «настройки читаются заново из `SettingsStore`»; пройти поиском «ветк» и «Idle» (в том числе «общий `Box` ветки `Idle`» у `StartFab` и «Настройки формы хранит ветка `Idle`»); описать оболочку, `isWorkoutFullscreen`, контроллер в `MainActivity`, «Свернуть», полоску (порядок семантики, промежуток по центру), режимы центральной кнопки, переход на «Таймер» только на переходе из `Idle`, сброс `rememberSaveable` формы после тренировки; в абзац о `material-icons-core` добавить `ic_pause.xml`; в ограничение доступности «Старта» добавить, что над выступом кнопки исследование касанием находит полоску
- [ ] перенести задачу в `docs/tasks/completed/`
- [ ] move this plan to `docs/plans/completed/`

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification** на телефоне в обеих темах:
- старт с «Истории» открывает «Таймер» во весь экран; «Свернуть» ведёт на прошлую вкладку;
- на вкладках полоска показывает отрезок, повтор и время, линия «пройдено» растёт, нажатие ведёт на «Таймер»;
- центральная кнопка ставит на паузу и продолжает, в отсчёте и после финального сигнала недоступна;
- «Назад» с вкладки ведёт на тренировку, с тренировки сворачивает приложение;
- поворот экрана во время тренировки на чужой вкладке её не меняет;
- после финала вкладка не меняется, в центре снова «Старт», новая запись в истории;
- смена громкости во время тренировки не играет пробный сигнал и не меняет сигналы идущей;
- при выключенном экране сигналы звучат вовремя, пока открыта другая вкладка;
- строка состояния и системная панель читаемы на вкладках и на цветном экране тренировки;
- TalkBack читает полоску одной фразой и открывает «Таймер» двойным касанием, центральную кнопку как «Пауза» / «Продолжить», «Свернуть» как кнопку; исследование касанием над выступом центральной кнопки находит полоску;
- при «Свернуть» и нажатии на полоску нет скачка вёрстки из-за переключения отступов `safeDrawing`;
- полоска с крупным системным шрифтом не обрезается; полоска в альбомной ориентации;
- «Назад» с «Таймера» во время тренировки закрывает activity, открытие из уведомления показывает «Таймер» во весь экран.
