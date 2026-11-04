# Экран прогресса по программе

## Overview

Отдельный экран «Прогресс» показывает, сколько раз пробежана каждая из 10 недель программы, какие недели пройдены и когда была последняя пробежка. Неделя считается пройденной после 4 завершённых пробежек, до этого слева от неё закрашивается по четверти кольца на пробежку. Экран только показывает факты и ничего не меняет на форме и в истории: когда переходить на следующую неделю, человек решает сам.

Прогресс не хранится, а каждый раз считается из истории (`RunHistory.runs`), поэтому удаление записи в истории сразу меняет его. Схема базы, `RunRecord`, сервис, форма и история не меняются.

Задача: `docs/tasks/20261103-program-progress.md`. Макет: https://claude.ai/artifact/WKnsAn8dCvkNbCqtUVcdik, артборды «Прогресс» и «Тёмная · прогресс».

## Context (from discovery)

- Навигация: `ui/WorkoutScreen.kt`, `NavHost` в ветке `Idle` с маршрутами `settings`, `app_settings`, `history`, маршруты объявляются через `screen()` с непрозрачным фоном, колбэки в `dropUnlessResumed`. Маршрут `history` собирает поток `runs` через `collectAsStateWithLifecycle(initialValue = null)`.
- Форма: `ui/SettingsContent.kt`, `AppTopBar` с тремя `IconButton` (программа `Icons.Outlined.DateRange`, история `R.drawable.ic_history`, настройки `Icons.Outlined.Settings`), параметры-колбэки `onOpenHistory`, `onOpenSettings`.
- Общие элементы: `ui/Components.kt` (`AppTopBar` с `onBack`, `SectionCard`, `SectionTitle`). Тема: `ui/Theme.kt`, `LocalDarkTheme`, `segmentColors(isRun, darkTheme).marker` даёт `#047857` и `#10B981`, ровно цвета закрашенных частей из макета, `TabularNumbers` для цифр.
- Программа: `WorkoutPreset.kt` (`WORKOUT_PRESETS`, `presetWeekOf(settings)`), строка недели шторки `PresetRowText.kt` (`presetRowText` даёт «Бег 1:00 · Ходьба 2:00 · ×7»).
- Форматы дат истории: `RunRowText.kt`, `runFormatters(zone, locale)` с явными шаблонами (CLDR в JDK и ICU на телефоне расходятся).
- Векторная иконка из Material Symbols уже есть: `res/drawable/ic_history.xml` с комментарием о происхождении и лицензии.
- Тесты: JUnit 4 в `app/src/test/java/com/ssortia/runtimer/`, Compose и Android API тестами не покрываются (`CLAUDE.md`).

## Development Approach

- **testing approach**: Regular (сначала код, затем тесты), как требует `CLAUDE.md`
- complete each task fully before moving to the next
- make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
  - логика без Android API покрывается unit-тестами; Compose-код по соглашению проекта тестами не покрывается, задачи только с ним проверяются сборкой, lint и ручной проверкой
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- run tests after each change: `JAVA_HOME=~/.local/jdk-17 ./gradlew test`

## Testing Strategy

- **unit tests**: `ProgramProgressTest` (определение недели, подсчёт, порог, четверти), `ProgressRowTextTest` (дата последней пробежки), `ui/ThemeTest` (цвета кольца и полосы)
- **e2e tests**: в проекте нет; экран, кольцо, склонение через `<plurals>` и навигация проверяются вручную на телефоне (Post-Completion)

## Progress Tracking

- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview

- **Логика** в чистом `ProgramProgress.kt`: по списку `RunRecord` строит 10 `WeekProgress` (по одной на неделю программы, в порядке недель), считая только завершённые пробежки, параметры которых совпадают с неделей. Порог `RUNS_TO_PASS_WEEK = 4` и число закрашенных четвертей выводятся из числа пробежек свойствами `WeekProgress`.
- **Текст** строки недели берётся из готового `presetRowText` (заголовок и параметры), дата последней пробежки форматируется явным шаблоном `d MMMM` («20 октября») в новом `ProgressRowText.kt`. Склонение «раз/раза» идёт ресурсом `<plurals>` через `pluralStringResource`, своей функции нет.
- **Экран** `ui/ProgressContent.kt`: `AppTopBar` со стрелкой, карточка итога («N из 10 недель пройдено» и 10 делений), карточка со строками недель. Кольцо рисуется в `Canvas`. Получает готовый `List<WeekProgress>?` и про Room не знает.
- **Маршрут** `progress` в `WorkoutScreen` по образцу `history`: тот же поток `runs`, `collectAsStateWithLifecycle(initialValue = null)`, `programProgress` считается в `remember(records)`.
- **Вход**: четвёртая иконка в верхней панели формы, векторный `ic_flag.xml` из Material Symbols (flag, Outlined, 24 dp, Apache 2.0).

## Technical Details

```kotlin
// ProgramProgress.kt
const val RUNS_TO_PASS_WEEK = 4

data class WeekProgress(val week: Int, val completedRuns: Int, val lastCompletedAtMs: Long?) {
    val passed: Boolean get() = completedRuns >= RUNS_TO_PASS_WEEK
    /** Сколько частей кольца закрашено, от 0 до 4. */
    val filledQuarters: Int get() = completedRuns.coerceAtMost(RUNS_TO_PASS_WEEK)
}

/** Неделя программы с теми же бегом, ходьбой и повторами, что у записи, или null. */
fun presetWeekOf(record: RunRecord): Int?

/** Все недели программы по порядку, в том числе без пробежек. Учитываются только завершённые. */
fun programProgress(runs: List<RunRecord>): List<WeekProgress>

```

- `presetWeekOf(record)` сравнивает `Workout(runSec, if (rounds == 1) 0 else walkSec, rounds)` записи с `preset.settings.toWorkout()`: так ходьба 1 мин 30 сек недель 5–6 совпадает с 90 секундами записи. Ходьба при одном повторе не выполняется (её не показывает и `runRowText`), а форма разрешает её задать, поэтому «Бег 30:00, Ходьба 2:00, ×1» засчитывается неделе 10. Перегрузка рядом с `presetWeekOf(settings)` в `WorkoutPreset.kt`.
- `lastCompletedAtMs` — максимум `startedAtMs` завершённых пробежек недели, а не первая запись списка: функция не должна зависеть от порядка, в котором `RunHistory.runs` отдаёт записи.
- `ProgressRowText.kt`: `progressDateFormatter(zone, locale)` с шаблоном `d MMMM` и `progressDateText(startedAtMs, formatter)`. Шаблон `d MMM` не берём: сокращённый месяц в `ru` пишется с точкой («окт.»), и в JDK и на телефоне точка может различаться. Года в дате нет сознательно: программа длится 2–3 месяца, а при переходе через Новый год дата всё равно однозначна по контексту ближайших пробежек.
- Ресурсы `strings.xml`: `progress_title` «Прогресс» для заголовка экрана, `button_progress` «Прогресс» для иконки на форме (по образцу `history_title` и `button_history`), `progress_summary` «из %1$d недель пройдено» (крупное число отдельно, как в макете), `progress_ring_passed` «Неделя пройдена», `progress_ring_count` «Пробежек %1$d из %2$d», `progress_summary_description` для полосы «Пройдено недель: %1$d из %2$d». `<plurals name="progress_runs">` с `one` «%d раз», `few` «%d раза», `many` и `other` «%d раз».
- Цвета: закрашенные части, галочка и деления — `segmentColors(isRun = true, darkTheme).marker`; незакрашенные части и деления — новый `progressTrackColor(darkTheme)` в `ui/Theme.kt`: `#DCDCD8` в светлой и `#3A3B40` в тёмной теме (тон поля на карточке почти не виден). Галочка на закрашенном круге: белая в светлой теме, `#111214` в тёмной, как в макете; цвет отдаёт функция `progressCheckColor(darkTheme)` рядом с `progressTrackColor` в `Theme.kt`, литералов в экране нет.
- Кольцо: `Canvas` 24 dp, четыре `drawArc` по 90° минус зазор около 8°, начиная с −90° (сверху) по часовой стрелке, `Stroke` около 2 dp с `StrokeCap.Butt`. Пройденная неделя: закрашенный круг и галочка (`Icons.Outlined.Check` поверх или путь в `Canvas`). У значка `Modifier.semantics { contentDescription = … }`.
- Полоса итога: `Row` из 10 `Box` высотой 8 dp с `weight(1f)`, скругление 4 dp, зазор 4 dp; для TalkBack полоса одна с описанием «Пройдено недель: N из 10», деления без описаний.
- Список недель: обычный `Column` в `verticalScroll`, не `LazyColumn`: строк всего 10. Разделители между строками `outlineVariant`, строка около 56 dp, не нажимается. Строка помечена `semantics(mergeDescendants = true) {}`, чтобы TalkBack читал неделю одной фразой.
- Пока `records == null`, под `AppTopBar` ничего не показывается.

## What Goes Where

- **Implementation Steps** (`[ ]` checkboxes): код, тесты, ресурсы и документация в репозитории
- **Post-Completion** (без чекбоксов): ручная проверка на телефоне

## Implementation Steps

### Task 1: Подсчёт прогресса по истории

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ProgramProgress.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutPreset.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/ProgramProgressTest.kt`

- [x] добавить `presetWeekOf(record: RunRecord)` в `WorkoutPreset.kt`, сравнение через `toWorkout()`, ходьба при одном повторе приводится к 0
- [x] создать `ProgramProgress.kt`: `RUNS_TO_PASS_WEEK`, `WeekProgress` с `passed` и `filledQuarters`, `programProgress(runs)`; число пройденных недель экран считает сам (`count { it.passed }`)
- [x] написать тесты: пустая история даёт 10 недель с нулями и `null`; только остановленные пробежки не считаются; несколько завершённых пробежек одной недели дают число и наибольший `startedAtMs` независимо от порядка списка; пробежка с параметрами не из программы не учитывается
- [x] написать тесты порога и четвертей: 3 пробежки — не пройдена, `filledQuarters` 3; 4 — пройдена; 6 — пройдена, `filledQuarters` 4, `completedRuns` 6; пройденные недели не по порядку (1 и 3) дают `passed` именно у них
- [x] написать тесты определения недели: ходьба 90 сек совпадает с неделями 5–6; неделя 10 без ходьбы; «30:00, ходьба 2:00, ×1» находит неделю 10; у каждой недели `WORKOUT_PRESETS` запись с её параметрами находит именно её
- [x] run tests - must pass before next task

### Task 2: Дата последней пробежки

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ProgressRowText.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/ProgressRowTextTest.kt`

- [ ] создать `progressDateFormatter(zone, locale)` с шаблоном `d MMMM` и `progressDateText(startedAtMs, formatter)`
- [ ] написать тесты: «20 октября» и «3 ноября» в `ru` для фиксированной зоны; граница суток зависит от переданной зоны
- [ ] run tests - must pass before next task

### Task 3: Экран «Прогресс»

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ui/ProgressContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/Theme.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/ThemeTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] добавить `progressTrackColor(darkTheme)` и `progressCheckColor(darkTheme)` в `ui/Theme.kt`, строки и `<plurals name="progress_runs">` в `strings.xml`
- [ ] написать тесты в `ui/ThemeTest`: в обеих темах закрашенная часть (`marker`) отличается от дорожки с контрастом не меньше 3:1, дорожка отличается от карточки `surfaceContainer` (контраст не меньше 1,3), галочка читается на `marker` с контрастом не меньше 3:1
- [ ] создать `ProgressContent(progress: List<WeekProgress>?, onBack)`: `AppTopBar` со стрелкой, при `null` больше ничего
- [ ] карточка итога: крупное число пройденных недель с `TabularNumbers`, подпись, полоса из 10 делений по `passed`, описание полосы для TalkBack
- [ ] карточка недель: строки строятся по `WORKOUT_PRESETS.zip(progress)` (оба списка по порядку недель), заголовок `stringResource(R.string.preset_week, week)` и параметры из `presetRowText`; кольцо из четырёх дуг или круг с галочкой с `contentDescription`; справа `pluralStringResource` и дата через `progressDateText` или «—»
- [ ] формат даты создаётся один раз на экран (`remember` с зоной и локалью), как `runFormatters` в истории
- [ ] остальной Compose-код тестами не покрывается (`CLAUDE.md`); убедиться, что `./gradlew assembleDebug` собирается и тесты проходят
- [ ] run tests - must pass before next task

### Task 4: Маршрут и вход с формы

**Files:**
- Create: `app/src/main/res/drawable/ic_flag.xml`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`

- [ ] добавить `ic_flag.xml` из Material Symbols (flag, Outlined, 24 dp, Apache 2.0) с комментарием о происхождении, как у `ic_history.xml`
- [ ] добавить параметр `onOpenProgress` в `SettingsContent` и `IconButton` с флажком между «Историей» и «Настройками», `contentDescription` из `button_progress`
- [ ] добавить маршрут `ROUTE_PROGRESS = "progress"` в `WorkoutScreen` через `screen()`: `runs.collectAsStateWithLifecycle(initialValue = null)`, `remember(records) { records?.let(::programProgress) }`, возврат `dropUnlessResumed { navController.popBackStack() }`; переход с формы в `dropUnlessResumed`
- [ ] Compose-код тестами не покрывается; проверить сборку и что существующие тесты проходят
- [ ] run tests - must pass before next task

### Task 5: Verify acceptance criteria

- [ ] verify all requirements from Overview and `docs/tasks/20261103-program-progress.md` are implemented: только завершённые пробежки, порог 4, кольцо по четвертям, «—» без пробежек, состояние до прихода данных, ничего не хранится отдельно
- [ ] verify edge cases are handled: неделя 10 без ходьбы, ходьба 1:30, пробежки сверх четырёх, параметры не из программы
- [ ] run full test suite: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [ ] run lint: `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`, новых предупреждений нет

### Task 6: [Final] Update documentation

- [ ] добавить в `USER_STORIES.md` историю US-16 «Видеть прогресс по программе» («Как бегун по программе, я хочу видеть, какие недели и сколько раз я уже пробежал, чтобы самому решать, когда переходить дальше») с критериями приёмки из разделов «Экран» и «Правила» задачи
- [ ] обновить `README.md`: описание приложения, строки таблицы структуры для `ProgramProgress.kt`, `ProgressRowText.kt`, `ui/ProgressContent.kt`, `ui/WorkoutScreen.kt` (маршрут прогресса), `ui/SettingsContent.kt` (четвёртая иконка)
- [ ] обновить `CLAUDE.md`: в «Архитектуре» четыре маршрута с `progress`; прогресс считается из истории и не хранится, удаление записи меняет его; порог `RUNS_TO_PASS_WEEK`; в «Неочевидное»: неделя записи определяется без ходьбы при одном повторе, потому что такая ходьба не выполняется
- [ ] отметить выполненные пункты задачи и перенести `docs/tasks/20261103-program-progress.md` в `docs/tasks/completed/` (останется только ручная проверка)
- [ ] move this plan to `docs/plans/completed/`

## Post-Completion

*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification** на телефоне:
- после завершённой тренировки недели программы прогресс обновился, закрасилась следующая четверть кольца, после четвёртой пробежки появилась галочка, число пройденных недель и деление полосы обновились
- остановленная тренировка прогресс не меняет
- удаление записи в истории меняет прогресс после возврата на экран; удаление одной из четырёх пробежек недели возвращает кольцо из трёх четвертей
- склонение: 1 раз, 2 раза, 5 раз, 21 раз
- правая колонка вмещает «20 сентября» и «5 раз» без переноса и обрезки
- кольцо и его незакрашенные части различимы в светлой и тёмной теме, экран совпадает с макетом
- системная «Назад» и стрелка возвращают на форму без потери несохранённых правок, двойное нажатие на флажок не кладёт два экрана
- TalkBack читает строку недели одной фразой вместе с «Пробежек 3 из 4» или «Неделя пройдена» и описание полосы
