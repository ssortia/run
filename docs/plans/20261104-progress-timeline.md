# Прогресс лентой

## Overview
- Задача: `docs/tasks/20261104-progress-timeline.md`, макет: артборды «Прогресс лентой» и «Тёмная · прогресс лентой» (`Progress-timeline.dc.html`) в https://claude.ai/artifact/WKnsAn8dCvkNbCqtUVcdik.
- Экран прогресса из итога «N из 10», полосы и одной карточки недель становится вертикальной лентой: у каждой недели узел на линии слева и своя карточка, текущая неделя выделена карточкой «Сейчас» с 4 делениями.
- Текущая неделя — неделя последней завершённой пробежки по программе (пробежки со своими параметрами пропускаются), без таких пробежек — неделя 1. Непройденные недели выше текущей показаны зелёным кругом с прочерком, линия до текущей зелёная.
- Прогресс по-прежнему считается из истории, схема базы, сервис, форма и история не меняются.

## Context (from discovery)
- `ProgramProgress.kt`: `RUNS_TO_PASS_WEEK`, `WeekProgress(preset, completedRuns, lastCompletedAtMs)` с `week` и `passed`, `programProgress(runs)` — все 10 недель, только завершённые пробежки, неделя записи через `presetWeekOf(record)` из `WorkoutPreset.kt`.
- `ProgressRowText.kt`: `progressDateFormatter` («20 октября»), `ProgressLabels`, `ProgressRowText(title, details, runs, runsSpoken, date, ringDescription)`, `progressRowText(...)` поверх `presetRowText`.
- `ui/ProgressContent.kt`: `SummaryCard`, `WeeksCard`, `WeekRow`, `WeekRing` (кольцо из 4 дуг в `Canvas`, круг с галочкой). Колонка с `verticalScroll` и нижним отступом `ScrollContentBottomPadding` (под выступающим «Стартом»).
- `ui/WorkoutScreen.kt`: маршрут `progress` делает `remember(records) { records?.let(::programProgress) }` и отдаёт `ProgressContent(progress)`; сигнатура не меняется.
- `ui/Theme.kt`: `segmentColors(isRun = true, dark).marker`, `progressTrackColor`, `progressCheckColor`; `WeekChip` в `SettingsContent.kt` на `secondaryContainer` / `onSecondaryContainer`. `ThemeTest` уже проверяет `marker` на дорожке, дорожку на `surfaceContainer`, галочку на `marker`.
- `strings.xml`: `progress_title`, `progress_summary` (plurals), `progress_summary_description`, `progress_ring_passed`, `progress_ring_count`, `progress_no_runs`, `progress_runs` (plurals).
- Тесты: `ProgramProgressTest` (хелпер `weekRecord(week, startedAtMs, completed)`), `ProgressRowTextTest`, `ui/ThemeTest`.

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
- по соглашению проекта Compose-код тонкий и тестами не покрыт: текущая неделя, вид узла, цвет отрезков линии и тексты считаются в чистом коде с тестами

## Testing Strategy
- **unit tests**: `ProgramProgressTest` (текущая неделя, вид узлов, отрезки линии), `ProgressRowTextTest` (строка карточки «Сейчас», описания для TalkBack), `ThemeTest` (контраст линии на фоне).
- **e2e tests**: UI-тестов и эмулятора в проекте нет, вид ленты и TalkBack проверяются вручную на телефоне (Post-Completion).

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview
- **Логика в `programProgress`.** Функция по-прежнему отдаёт `List<WeekProgress>` из 10 недель, но каждая неделя дополнительно знает, текущая ли она и стоит ли выше текущей. Вид узла (`WeekMark`) и закраска отрезка линии к следующей неделе выводятся из этих полей и `passed`. `WorkoutScreen` и поток `runs` не меняются.
- **Текущая неделя** считается отдельной чистой функцией `currentProgramWeek(runs)`, чтобы правило было видно и тестировалось само по себе.
- **Тексты** собирает `progressRowText`: для карточки «Сейчас» строка состояния («Пробежек 3 из 4» или «Неделя пройдена · 5 раз»), для остальных число и дата, как сейчас, и для всех — одно описание строки для TalkBack. Видимые элементы карточки скрываются от TalkBack, строка читается этим описанием.
- **Экран.** `SummaryCard` и `WeeksCard` удаляются. Лента — колонка строк: `Row(Modifier.height(IntrinsicSize.Min))` с колонкой узла фиксированной ширины и карточкой. Промежуток между неделями (8 dp) — `spacedBy` колонки, вне строк, поэтому линия в колонке узла, нарисованная `drawBehind` на высоту строки, в промежутке прерывается, как в макете. Верхний отрезок (от верха строки до узла) берёт цвет `lineToNextFilled` предыдущей недели, нижний (от узла до низа) — своей; у первой недели верхнего нет, у последней нижнего. Узел рисуется поверх линии с кольцом цвета фона.

## Technical Details
- `ProgramProgress.kt`:
  ```kotlin
  enum class WeekMark { PASSED, CURRENT, SKIPPED, AHEAD }

  data class WeekProgress(
      val preset: WorkoutPreset,
      val completedRuns: Int,
      val lastCompletedAtMs: Long?,
      val current: Boolean,
      /** Неделя стоит выше текущей в программе. */
      val beforeCurrent: Boolean,
  ) {
      val week: Int get() = preset.week
      val passed: Boolean get() = completedRuns >= RUNS_TO_PASS_WEEK
      val mark: WeekMark get() = when {
          current -> WeekMark.CURRENT
          passed -> WeekMark.PASSED
          beforeCurrent -> WeekMark.SKIPPED
          else -> WeekMark.AHEAD
      }
      /** Отрезок линии от этой недели к следующей закрашен. */
      val lineToNextFilled: Boolean get() = beforeCurrent || passed
  }
  ```
  Поля без значений по умолчанию: компилятор не даст забыть их заполнить. В `ProgressRowTextTest` — хелпер, собирающий `WeekProgress`.
- `currentProgramWeek(runs: List<RunRecord>): Int` — среди `completed` с `presetWeekOf(it) != null` максимум по `startedAtMs`, при равенстве большая неделя (`compareBy({ it.startedAtMs }, { week })`), иначе `WORKOUT_PRESETS.first().week`.
- `programProgress`: `current = week == currentWeek`, `beforeCurrent = week < currentWeek` (`lineToNextFilled` у последней недели экраном не используется).
- Узлы на экране: `PASSED` и `SKIPPED` — круг 22 dp цвета `marker`, внутри галочка (`Icons.Outlined.Check`) или прочерк цвета `progressCheckColor`. Прочерк рисуется в `Canvas` (`drawLine` с `StrokeCap.Round`) или `Box` со скруглённым фоном: минуса в `material-icons-core` нет; `CURRENT` — кольцо из 4 частей 28 dp (нынешний `WeekRing`, закрашено `min(completedRuns, 4)`) с точкой `marker` в центре; `AHEAD` — полое кольцо 12 dp, обводка `marker` при `completedRuns > 0`, иначе `progressTrackColor`. Линия 2 dp: `marker` или `progressTrackColor`.
- Узел по высоте: центр узла совпадает с серединой строки заголовка. Отступ считается как верхний отступ карточки плюс половина `lineHeight` стиля заголовка, переведённая в dp через `LocalDensity`, поэтому узел не съезжает при крупном системном шрифте. У карточки «Сейчас» плашка выровнена по центру строки заголовка и не выше её (иначе считать по высоте плашки). Верхний отрезок линии кончается, нижний начинается в центре узла.
- Дорожка на фоне окна: серая линия и кольца `AHEAD` лежат на фоне окна, а не на карточке. `#DCDCD8` на `#F5F5F3` даёт около 1,26:1, меньше порога 1,3:1 для дорожки на карточке, поэтому `ProgressTrackLight` чуть затемняется так, чтобы дорожка на `windowBackground` давала не меньше 1,3:1, а `marker` на дорожке оставался не меньше 3:1.
- Карточка «Сейчас»: заголовок «Неделя N» и плашка «Сейчас» (`secondaryContainer` / `onSecondaryContainer`, как `WeekChip`), параметры, 4 деления (`marker` / `progressTrackColor`, закрашено `min(N, 4)`), строка состояния и справа дата (без пробежек даты нет). Остальные карточки: заголовок (`onSurfaceVariant` у `SKIPPED` и `AHEAD`), параметры, справа число или «—» и дата.
- `ProgressRowText` получает поля `status: String?` (только у текущей: «Пробежек N из 4» до порога, «Неделя пройдена · N раз» с порога, `runsText` из `progress_runs`) и `description: String` (вся строка для TalkBack). `ringDescription` и `runsSpoken` убираются, если после перехода на `description` не нужны.
- Описание строки (части через «, »): «Неделя 3, сейчас, пробежек 3 из 4, последняя 3 ноября»; «Неделя 1, пройдена, 5 раз, последняя 20 октября»; «Неделя 2, пробежек 2 из 4, последняя 29 октября»; «Неделя 4, пробежек нет». Текущая пройденная: «Неделя 3, сейчас, пройдена, 6 раз, последняя 3 ноября». Текущая без пробежек (пустая история): «Неделя 1, сейчас, пробежек нет». Подписи («сейчас», «пройдена», «последняя %s», «пробежек нет», шаблон со строчной «пробежек %1$d из %2$d») приходят через `ProgressLabels` из ресурсов.
- `strings.xml`: добавить `progress_now` «Сейчас», шаблон строки состояния пройденной недели «Неделя пройдена · %s» целиком вместе с разделителем и подписи описания (шаблон «пробежек %1$d из %2$d» отдельный, со строчной). Строка состояния «Пробежек %1$d из %2$d» берётся из `progress_ring_count`. Удалить `progress_summary`, `progress_summary_description` и `progress_ring_passed`, если он перестанет использоваться.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): код, тесты, документация в репозитории
- **Post-Completion** (no checkboxes): проверка на телефоне

## Implementation Steps

### Task 1: Текущая неделя и вид узлов в `programProgress`

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ProgramProgress.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ProgramProgressTest.kt`

- [x] добавить `WeekMark`, обязательные поля `current` и `beforeCurrent` в `WeekProgress`, свойства `mark` и `lineToNextFilled`
- [x] добавить `currentProgramWeek(runs)`: последняя завершённая пробежка по программе, при равном `startedAtMs` большая неделя, без таких пробежек неделя 1
- [x] заполнять `current` и `beforeCurrent` в `programProgress`, KDoc с правилом линии
- [x] тесты текущей недели: пустая история — 1; последняя завершённая не из программы — по более ранней; остановленная пробежка позже не меняет текущую; равный `startedAtMs` у двух недель — большая, независимо от порядка списка
- [x] тесты вида узлов и линии: пустая история — неделя 1 `CURRENT`, остальные `AHEAD`, все `lineToNextFilled` false; незакрытые недели выше текущей — `SKIPPED` и закрашенные отрезки; недели не по порядку (неделя 5 пройдена, последняя пробежка недели 3) — неделя 5 `PASSED` ниже текущей, её отрезок закрашен, отрезок текущей непройденной нет; текущая пройденная (6 пробежек) — `CURRENT`, `passed`, отрезок закрашен
- [x] прежние тесты подсчёта остаются зелёными; run tests — must pass before next task

### Task 2: Тексты карточек и описания для TalkBack

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ProgressRowText.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ssortia/runtimer/ProgressRowTextTest.kt`

- [x] расширить `ProgressLabels` подписями «сейчас», «пройдена», «последняя %s», «пробежек нет», шаблоном строки состояния и «Неделя пройдена»
- [x] `progressRowText`: `status` у текущей недели («Пробежек N из 4» / «Неделя пройдена · N раз»), `description` всей строки по виду узла; убрать ставшие лишними поля
- [x] ресурсы: `progress_now` и подписи описания; удалить неиспользуемые `progress_summary`, `progress_summary_description` и прочие осиротевшие
- [x] тесты: строка состояния 0, 3, 4 и 6 пробежек (с 4 настоящее число, без «из 4»); описания текущей, текущей пройденной (6 пробежек), текущей без пробежек, пройденной, `SKIPPED` с пробежками, `AHEAD` без пробежек; дата и формат параметров прежние
- [x] run tests — must pass before next task
- ➕ `ui/ProgressContent.kt` минимально поправлен под новые поля, чтобы сборка шла: убран `SummaryCard` (его ресурсы удалены), строка читается `description`, у текущей недели под параметрами строка состояния; лента — задача 3

### Task 3: Лента на экране прогресса

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ProgressContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/Theme.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/ThemeTest.kt`

- [x] удалить `SummaryCard`, `WeeksCard`, `WeekRow`; колонка с `verticalScroll` и нижним отступом `ScrollContentBottomPadding` остаётся, при `null` по-прежнему только `AppTopBar`
- [x] строка недели: `Row(height(IntrinsicSize.Min))`, колонка узла ~28 dp с линией в `drawBehind` (верхний отрезок по предыдущей неделе, нижний по своей, в промежутке 8 dp между строками (`spacedBy`) линии нет), узел по `mark` с кольцом цвета фона, центр на середине строки заголовка через `lineHeight` и `LocalDensity`; прочерк `SKIPPED` в `Canvas` или `Box`, не иконкой
- [x] карточка «Сейчас» (плашка, параметры, 4 деления, строка состояния и дата) и компактная карточка остальных недель (`SectionCard` или тот же фон `surfaceContainer`), цвета только из `ui/Theme.kt` и `MaterialTheme`
- [x] TalkBack: строка — один узел `clearAndSetSemantics { contentDescription = text.description }`, узлы, линия и деления без семантики
- [x] `ThemeTest` с `windowBackground(darkTheme)`: линия `marker` не меньше 3:1, дорожка `progressTrackColor` не меньше 1,3:1 в обеих темах (прочерк берёт `progressCheckColor`, его контраст на `marker` уже проверен)
- [x] затемнить `ProgressTrackLight` в `ui/Theme.kt`, чтобы прошла проверка дорожки на фоне, сохранив `marker` на дорожке не меньше 3:1
- [x] `./gradlew test assembleDebug` — must pass before next task

### Task 4: Verify acceptance criteria
- [ ] verify all requirements from `docs/tasks/20261104-progress-timeline.md` are implemented (экран, правила, TalkBack; вид на телефоне — Post-Completion)
- [ ] verify edge cases: пустая история, последняя пробежка не из программы, текущая пройденная, недели не по порядку, неделя 10 без шага
- [ ] run full test suite: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [ ] run lint: `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`

### Task 5: [Final] Update documentation
- [ ] `USER_STORIES.md`, US-16: итог и полоса убраны, лента, карточка «Сейчас», правило текущей недели, незакрытые недели выше текущей, число больше 4; переписать ««0 из 10», у всех недель прочерки» на «Сейчас» у недели 1
- [ ] `README.md`: описание прогресса и строки `ProgramProgress.kt`, `ProgressRowText.kt`, `ui/ProgressContent.kt` в таблице файлов, фразу «а не «0 из 10»» в описании прогресса
- [ ] `CLAUDE.md`: абзац о прогрессе в «Архитектуре» (текущая неделя, `WeekMark`, линия), цвета экрана прогресса в «Неочевидном» (узел пропущенной недели, линия, проверка контраста линии), линия рисуется в строке и в промежутках между карточками прерывается намеренно; пункт про `.catch` в `RunHistory.runs` («только `AppTopBar`, а не «0 из 10»») переписать без итога
- [ ] перенести задачу в `docs/tasks/completed/`, этот план в `docs/plans/completed/`

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification** (на телефоне, в светлой и тёмной теме):
- экран совпадает с макетом «Прогресс лентой»: узлы на уровне заголовков, линия прерывается в промежутках между карточками, как в макете, последняя карточка не прячется под «Старт»
- после завершённой тренировки недели программы «Сейчас» переезжает на её неделю и закрашивается следующее деление; после четвёртой строка «Неделя пройдена · 4 раза», деления не растут дальше
- тренировка со своими параметрами и остановленная тренировка «Сейчас» не двигают
- пропущенная неделя выше текущей — зелёный круг с прочерком, линия до «Сейчас» зелёная
- удаление записи в истории сразу меняет ленту, в том числе текущую неделю
- пустая история: «Сейчас» у недели 1, остальные с «—»
- крупный системный шрифт и узкий экран: параметры недели не налезают на число, плашка «Сейчас» не обрезана
- TalkBack читает каждую неделю одной фразой, без повторов числа
