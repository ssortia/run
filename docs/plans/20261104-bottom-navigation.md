# Нижняя навигация и новая шапка формы

## Overview
- Сейчас разделы открываются значками в верхней панели формы: «RunTimer» и четыре значка (программа, история, прогресс, настройки). У истории, прогресса и настроек есть стрелка «Назад».
- Новый дизайн взят с экрана «Нижняя навигация: программа сверху, вкладка „Прогресс“» в макете https://claude.ai/artifact/WKnsAn8dCvkNbCqtUVcdik:
  - внизу панель вкладок «Тренировка», «История», «Прогресс», «Настройки»;
  - у формы нет надписи «RunTimer» и значков. Слева заголовок «Тренировка», справа плашка «Неделя 2» со значком календаря, рамкой и лёгкой тенью, чтобы было видно, что она нажимается. Если настройки не совпадают ни с одной неделей, на плашке «Своя тренировка»;
  - плашка открывает ту же шторку программы, что и сейчас: это состояние формы, а не отдельный экран;
  - у истории, прогресса и настроек заголовок без стрелки «Назад».
- Число повторов меняется только кнопками «−» и «+», поля ввода больше нет. Это решение пользователя, и оно отменяет прежнее («поле остаётся редактируемым, чтобы 20 повторов не набирать нажатиями»): в программе не больше 7 повторов. Клавиатуры на форме не остаётся, поэтому прятать от неё панель вкладок не нужно.
- Системная «Назад» на вкладках «История», «Прогресс» и «Настройки» возвращает на «Тренировку», на «Тренировке» закрывает приложение. Несохранённые правки формы переживают переключение вкладок.

## Context (from discovery)
- `ui/WorkoutScreen.kt`: в ветке `Idle` стоят `rememberNavController()` и `NavHost` с маршрутами `settings` (стартовый), `app_settings`, `history` и `progress`. Маршруты объявлены через `screen()` с непрозрачным фоном, анимации отключены, `safeDrawingPadding()` стоит на `NavHost`, переходы обёрнуты в `dropUnlessResumed`.
- `ui/SettingsContent.kt` содержит:
  - `AppTopBar` с `app_name` и четырьмя `IconButton`, под ним подпись `preset_week_match`;
  - флаг `showProgram` и `ModalBottomSheet` со списком недель;
  - `RoundsCard` с `NumberField` (`OutlinedTextField`) и функцию `digitsOnly`;
  - баннер о фоне, скрытый при `WindowInsets.isImeVisible`.
- `ui/HistoryContent.kt`, `ui/ProgressContent.kt`, `ui/AppSettingsContent.kt` вызывают `AppTopBar(title, onBack = onBack)`.
- `ui/Components.kt`: у `AppTopBar` есть слот `actions` и стрелка «Назад», которая показывается, если передан `onBack`. Строка `button_back` используется только здесь.
- `ui/Theme.kt`: все цвета задаются только здесь, контрасты проверяет `ThemeTest`. `primary` на `surfaceContainer` уже проверяется на 4,5:1.
- Иконки: `Icons.Outlined.DateRange` и `Icons.Outlined.Settings` из `material-icons-core`, `R.drawable.ic_history` и `R.drawable.ic_flag`. Для вкладки «Тренировка» нужна новая векторная иконка `timer` из Material Symbols (Apache 2.0, как `ic_history` и `ic_flag`).
- Строки: `preset_week` («Неделя %1$d») уже есть, `preset_week_match` больше не нужна.
- Тесты: `SettingsInputTest` покрывает `digitsOnly` и `stepRounds`, `ThemeTest` проверяет контрасты.
- Механика Navigation Compose 2.8.5 сверена ревью по байткоду: схема `popUpTo(start) { saveState }` + `launchSingleTop` + `restoreState` держит в стеке не больше двух записей, запись формы не пересоздаётся.
- В рабочей копии лежит незакоммиченная задача ui-polish: правки `ActiveContent.kt` и `SettingsContent.kt` и файл `docs/tasks/completed/20261104-ui-polish.md`. План опирается на её общую константу высоты строки карточки (`CardRowHeight`). Task 0 коммитит её отдельно.

## Development Approach
- **testing approach**: Regular (code first, then tests)
- complete each task fully before moving to the next
- make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
  - tests are not optional - they are a required part of the checklist
  - write unit tests for new functions/methods
  - write unit tests for modified functions/methods
  - add new test cases for new code paths
  - update existing test cases if behavior changes
  - tests cover both success and error scenarios
- По соглашению проекта Compose-код тонкий и тестами не покрывается. Тесты пишутся для чистого кода (описание вкладок) и для контрастов в `ThemeTest`. Задачи только с Compose-кодом прогоняют `./gradlew test` и `lintDebug`.
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- run tests after each change
- maintain backward compatibility

## Testing Strategy
- **unit tests**: required for every task (see Development Approach above)
- **e2e tests**: UI-тестов и эмулятора в проекте нет, экраны проверяются вручную на телефоне (см. Post-Completion).

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview
- **Маршруты.** Navigation Compose остаётся, маршруты те же. Под `NavHost` добавляется Material 3 `NavigationBar`. Выбранная вкладка определяется по маршруту текущей записи стека (`currentBackStackEntryAsState()`).
- **Переключение вкладок** идёт по рекомендованной схеме Navigation Compose:
  ```kotlin
  onClick = {
      if (!selected) navController.navigate(tab.route) {
          popUpTo(ROUTE_SETTINGS) { saveState = true }
          launchSingleTop = true
          restoreState = true
      }
  }
  ```
  - В стеке не больше двух записей: форма и одна вкладка. Системная «Назад» с любой вкладки возвращает на форму, с формы закрывает приложение. Своего `BackHandler` по-прежнему нет.
  - Запись формы не пересоздаётся, её правки хранит `rememberSaveable`, а `remember { loadSettings() }` второй раз не выполняется.
  - Граф плоский, поэтому строка маршрута проще, чем `graph.findStartDestination().id`.
- **Без `dropUnlessResumed`.** Панель стоит вне `NavHost`, её `LocalLifecycleOwner` — activity, которая всегда `RESUMED`, поэтому обёртка ничего бы не защищала. Двойное нажатие безопасно и без неё: переход идемпотентен благодаря `popUpTo` и `launchSingleTop`, нажатие на текущую вкладку ничего не делает, а `popBackStack` вкладки не вызывают, так что пустого `NavHost` не будет. Это записывается в `CLAUDE.md` рядом с правилом про `dropUnlessResumed`.
- **Выбранная вкладка.** Индикатор M3 по умолчанию (`secondaryContainer` на `surfaceContainer`) в светлой теме почти не виден: контраст около 1,18:1. Поэтому иконка и подпись выбранной вкладки берут `primary` (`NavigationBarItemDefaults.colors(selectedIconColor = primary, selectedTextColor = primary)`), остальное по умолчанию.
- **Отступы.** `safeDrawingPadding()` уходит с `NavHost`. Общий `Column` получает `Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))`, именно `windowInsetsPadding`: он поглощает отступы, и `NavigationBar` не добавит боковые второй раз. Снизу системную панель учитывает сам `NavigationBar` своими `windowInsets` по умолчанию, и его фон уходит под жестовую полосу. В альбомной ориентации фон панели не заходит под боковую системную панель, это допустимо. Клавиатуры вне тренировки больше нет, отдельный учёт `ime` не нужен.
- **Шапка формы.** `AppTopBar(title = stringResource(R.string.tab_workout), actions = { WeekChip(...) })`. Высота, отступы и семантика заголовка общие с остальными экранами.
- **Плашка недели** — приватный `WeekChip` в `SettingsContent.kt`:
  - `Surface(onClick)` с `shape = CircleShape`, фон `secondaryContainer`, текст `onSecondaryContainer`;
  - рамка `weekChipBorderColor(dark)`, `shadowElevation = 1.dp`, высота 40 dp, `padding(end = 12.dp)`: слот `actions` сам даёт только 4 dp;
  - иконка `DateRange` 18 dp и текст `labelLarge`;
  - текст считается в месте вызова: `if (selectedWeek != null) stringResource(R.string.preset_week, selectedWeek) else stringResource(R.string.preset_custom)`. Отдельная функция для одного `if` не нужна;
  - `Surface(onClick)` уже задаёт `Role.Button`, поэтому для TalkBack добавляется только `contentDescription` из `preset_chip_description`.
- **Остальные экраны** вызывают `AppTopBar` без `onBack`. Параметр `onBack` убирается у `HistoryContent`, `ProgressContent`, `AppSettingsContent` и у самого `AppTopBar` вместе со стрелкой и строкой `button_back`.

## Technical Details
- `ui/BottomTab.kt` (новый файл, без Android API, кроме id ресурсов `R`):
  ```kotlin
  internal const val ROUTE_SETTINGS = "settings"
  internal const val ROUTE_APP_SETTINGS = "app_settings"
  internal const val ROUTE_HISTORY = "history"
  internal const val ROUTE_PROGRESS = "progress"

  enum class BottomTab(val route: String, @StringRes val labelRes: Int) {
      WORKOUT(ROUTE_SETTINGS, R.string.tab_workout),
      HISTORY(ROUTE_HISTORY, R.string.button_history),
      PROGRESS(ROUTE_PROGRESS, R.string.button_progress),
      SETTINGS(ROUTE_APP_SETTINGS, R.string.button_settings),
  }

  fun bottomTabOf(route: String?): BottomTab? = BottomTab.entries.firstOrNull { it.route == route }
  ```
  Константы маршрутов переезжают сюда из `WorkoutScreen.kt`. Иконки сопоставляются вкладкам в Compose-коде, а не в перечислении.
- Строки в `strings.xml`:
  - добавить `tab_workout` «Тренировка» (общая для вкладки и заголовка формы, как `button_history` у истории), `preset_custom` «Своя тренировка», `preset_chip_description` «%1$s, выбрать неделю программы», `rounds_value` «Повторы: %1$d»;
  - удалить `preset_week_match` и `button_back`.
- Цвета в `Theme.kt`: `weekChipBorderColor(darkTheme)` — светлая `#B7D3C8` (как в макете); тёмная подбирается под `background` тёмной темы, например `#2F5A4E`.
- Новые проверки в `ThemeTest`:
  - `onSecondaryContainer` на `secondaryContainer` не меньше 4,5:1 в обеих темах (текст плашки);
  - рамка плашки на `background` не меньше 1,3:1 в обеих темах, как у фоновой дорожки прогресса: смысл несёт фон плашки, рамка лишь подсказывает, что на неё можно нажать.
  - Выбранная вкладка (`primary` на `surfaceContainer`) уже покрыта проверкой 4,5:1.
- Повторы:
  - в `RoundsCard` вместо `NumberField` стоит `Text` (`titleLarge` + `TabularNumbers`, ширина `RoundsFieldWidth`) с `semantics { contentDescription = "Повторы: N"; liveRegion = LiveRegionMode.Polite }`, чтобы TalkBack объявлял новое значение после «+» и «−»;
  - удаляются `digitsOnly`, `NumberField` и их импорты (`KeyboardOptions`, `ImeAction`, `KeyboardType`, `OutlinedTextField`), тесты `digitsOnly` тоже;
  - `stepRounds`, `canDecreaseRounds` и `canIncreaseRounds` остаются. Проверка `ROUNDS_RANGE` в `WorkoutSettings.validate()` и `workoutOrNull()` тоже: через неё читаются значения из старого хранилища.
- Баннер о фоне: условие `!WindowInsets.isImeVisible` и `@OptIn(ExperimentalLayoutApi::class)` убираются.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): изменения кода, тесты и документация в этом репозитории.
- **Post-Completion** (no checkboxes): ручная проверка на телефоне.

## Implementation Steps

### Task 0: Отдельный коммит задачи ui-polish
- [x] (коммит делает оркестратор) если в рабочей копии остались правки `ActiveContent.kt` и `SettingsContent.kt` и файл `docs/tasks/completed/20261104-ui-polish.md`, закоммитить их отдельным коммитом («fix: высота карточек формы и полоса прогресса тренировки») до начала Task 1. Если их уже закоммитили, пропустить
- [x] run tests - must pass before next task

### Task 1: Повторы только кнопками

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/SettingsInputTest.kt`

- [x] заменить `NumberField` в `RoundsCard` на `Text` с числом повторов той же ширины, с описанием `rounds_value` и `liveRegion`. Высота карточки остаётся `CardRowHeight`
- [x] удалить `NumberField`, `digitsOnly` и ставшие ненужными импорты, убрать проверку `isImeVisible` у баннера о фоне и `ExperimentalLayoutApi`
- [x] обновить устаревшие комментарии: KDoc `RoundsCard` (поле больше не редактируется), комментарий `CardRowHeight` (больше нет «строки повторов с полем 56.dp»), KDoc `parseField` (упоминает `digitsOnly`), комментарий у блока «Старт» про подъём над клавиатурой
- [x] удалить тесты `digitsOnly` из `SettingsInputTest`, проверить, что тесты `stepRounds`, `canDecreaseRounds` и `canIncreaseRounds` покрывают границы 1 и `MAX_ROUNDS`, при нехватке дописать
- [x] run tests - must pass before next task

### Task 2: Описание вкладок

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ui/BottomTab.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/com/ssortia/runtimer/ui/BottomTabTest.kt`

- [ ] создать `BottomTab` и `bottomTabOf(route)`, перенести константы маршрутов из `WorkoutScreen.kt`, добавить строку `tab_workout`
- [ ] написать тесты: первая вкладка стоит на стартовом маршруте `settings`; `bottomTabOf` возвращает нужную вкладку для каждого маршрута и `null` для `null` и неизвестного маршрута
- [ ] run tests - must pass before next task

### Task 3: Цвет рамки плашки недели

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/Theme.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/ThemeTest.kt`

- [ ] добавить `weekChipBorderColor(darkTheme)` с двумя цветами и комментарием, зачем нужна рамка
- [ ] добавить в `ThemeTest` проверки для обеих тем: `onSecondaryContainer` на `secondaryContainer` не меньше 4,5:1, рамка на `background` не меньше 1,3:1
- [ ] run tests - must pass before next task

### Task 4: Нижняя панель вкладок

Панель добавляется раньше новой шапки, чтобы между коммитами история, прогресс и настройки оставались доступны.

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/Components.kt`
- Create: `app/src/main/res/drawable/ic_timer.xml`

- [ ] добавить иконку вкладки «Тренировка» `ic_timer.xml` из Material Symbols (outlined, 24 dp, как `ic_history.xml`)
- [ ] в ветке `Idle` обернуть `NavHost` в `Column` с `windowInsetsPadding(WindowInsets.safeDrawing.only(Top + Horizontal))`, убрать `safeDrawingPadding()` с `NavHost`, под ним (`weight(1f)`) поставить `NavigationBar` с вкладками из `BottomTab.entries`, иконки сопоставить в Compose-коде
- [ ] выбранную вкладку брать из `navController.currentBackStackEntryAsState()` через `bottomTabOf`, переключать по схеме из Solution Overview (`if (!selected)`, `popUpTo(ROUTE_SETTINGS) { saveState = true }`, `launchSingleTop`, `restoreState`), рядом комментарий, почему без `dropUnlessResumed`
- [ ] иконка и подпись выбранной вкладки цвета `primary` через `NavigationBarItemDefaults.colors`, остальное по умолчанию, `windowInsets` у `NavigationBar` по умолчанию
- [ ] обновить устаревшие комментарии: KDoc `AppTopBar` («отступы … уже даёт NavHost (`safeDrawingPadding`)») и комментарий в `WorkoutScreen.kt` про `safeDrawing` и `imePadding()`
- [ ] тесты: маршруты и вкладки покрыты в Task 2, навигация проверяется вручную (Post-Completion); прогнать `./gradlew test` и `lintDebug`
- [ ] run tests - must pass before next task

### Task 5: Новая шапка формы

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] заменить заголовок `app_name` и четыре `IconButton` на `AppTopBar(title = tab_workout, actions = { WeekChip(...) })`, плашка `WeekChip` устроена по Solution Overview
- [ ] плашка ставит `showProgram = true`, шторка и её поведение не меняются, описание для TalkBack из `preset_chip_description`, добавить строки `preset_custom` и `preset_chip_description`
- [ ] убрать подпись `preset_week_match` под заголовком и удалить эту строку
- [ ] убрать параметры `onOpenHistory`, `onOpenProgress`, `onOpenSettings` у `SettingsContent` и их передачу в `WorkoutScreen.kt`
- [ ] тесты: Compose-код по соглашению тестами не покрыт, контрасты плашки покрыты в Task 3; прогнать `./gradlew test` и `lintDebug` (неиспользуемые ресурсы)
- [ ] run tests - must pass before next task

### Task 6: Экраны без стрелки «Назад»

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/HistoryContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ProgressContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/AppSettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/Components.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] убрать параметр `onBack` у `HistoryContent`, `ProgressContent` и `AppSettingsContent` и его передачу в `WorkoutScreen.kt`
- [ ] удалить у `AppTopBar` параметр `onBack`, `navigationIcon` со стрелкой, импорты `IconButton`, `Icon` и `ArrowBack`, фразу про `onBack` в KDoc и строку `button_back`
- [ ] тесты: изменения только в Compose-коде; прогнать `./gradlew test` и `lintDebug`
- [ ] run tests - must pass before next task

### Task 7: Verify acceptance criteria
- [ ] verify all requirements from Overview are implemented:
  - заголовок «Тренировка» и плашка недели справа, «Своя тренировка» для несовпадающих настроек;
  - шторка открывается плашкой;
  - четыре вкладки внизу, «Назад» с вкладки ведёт на форму;
  - повторы меняются только кнопками
- [ ] verify edge cases are handled: правки формы переживают переход на другую вкладку и обратно, повторное нажатие на вкладку ничего не делает, после тренировки открывается форма на вкладке «Тренировка»
- [ ] run full test suite: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [ ] run lint: `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`
- [ ] verify test coverage meets project standard: новый чистый код и цвета покрыты тестами

### Task 8: [Final] Update documentation
- [ ] update `README.md`:
  - «Интерфейс»: нижняя панель вкладок вместо значков в верхней панели, «Назад» возвращает на форму, у экранов нет стрелки «Назад»;
  - «Фоновая работа»: убрать «пока открыта клавиатура поля повторов, баннер скрыт»;
  - «Настройки»: вкладка «Настройки» вместо иконки в верхней панели, повторы только кнопками вместо «поле между кнопками можно править с клавиатуры»;
  - «Программа по неделям»: плашка «Неделя N» / «Своя тренировка» справа от заголовка «Тренировка» вместо иконки и подписи «Неделя N программы»;
  - «Статус»
- [ ] update `USER_STORIES.md`:
  - US-1 и US-2: повторы только кнопками;
  - US-7: скрытие баннера при открытой клавиатуре (строка 76);
  - US-13: плашка вместо иконки «Программа» и подписи под заголовком (строки 137–147);
  - US-14, US-16 и история настроек: вкладки вместо значков и стрелки «Назад» (строки 184, 204 и другие упоминания верхней панели)
- [ ] update `CLAUDE.md`:
  - «Архитектура»: панель вкладок под `NavHost`; `popUpTo`/`saveState`/`restoreState`; почему у вкладок нет `dropUnlessResumed`; отступы `safeDrawing` сверху и по бокам через `windowInsetsPadding`, снизу их даёт `NavigationBar`; выбранная вкладка цвета `primary`;
  - «Неочевидное»: заменить «подписана под заголовком формы» на плашку, добавить рамку плашки в правило о цветах `Theme.kt`
- [ ] move this plan to `docs/plans/completed/`

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification** на телефоне в светлой и тёмной темах:
- **Вкладки и «Назад».** Вкладки переключаются, выбранная хорошо видна. Системная «Назад» с «Истории», «Прогресса» и «Настроек» возвращает на «Тренировку», с «Тренировки» закрывает приложение.
- **Правки формы.** Время и повторы не сбрасываются после перехода на другую вкладку, возврата и поворота экрана.
- **Плашка недели.** Видно, что её можно нажать. Она открывает шторку, после выбора недели номер на ней меняется, при своих значениях написано «Своя тренировка».
- **TalkBack.** Плашка читается как «Неделя N, выбрать неделю программы», вкладки как выбранные или нет. Повторы читаются как «Повторы: N», новое значение объявляется после «+» и «−».
- **Расположение.** Панель вкладок не перекрывается жестовой полосой и кнопками системы, «Старт» и баннер о фоне стоят над ней. В альбомной ориентации боковые отступы не удваиваются.
- **Двойные нажатия.** При быстрых двойных нажатиях на вкладки не открываются лишние экраны и не остаётся пустой `NavHost`.
- **После тренировки** открывается форма на вкладке «Тренировка», при переходах экраны не накладываются друг на друга.
