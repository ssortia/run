# Выбор тренировки из программы по неделям

## Overview
- Задача из `docs/tasks/20261103-preset-weeks.md`: на форме тренировки одним нажатием подставляются время бега, время ходьбы и число повторов из готовой программы на 11 недель.
- Программа (из `plan.md`):

| Неделя | Интервал бега | Интервал ходьбы | Повторения |
| ------ | ------------- | --------------- | ---------- |
| 1      | 1 мин         | 2 мин           | 7          |
| 2      | 2 мин         | 2 мин           | 7          |
| 3      | 3 мин         | 2 мин           | 5          |
| 4      | 4 мин         | 2 мин           | 4          |
| 5      | 6 мин         | 2 мин           | 3          |
| 6      | 8 мин         | 90 сек          | 3          |
| 7      | 9 мин         | 90 сек          | 2          |
| 8      | 11 мин        | 90 сек          | 2          |
| 9      | 13 мин        | 1 мин           | 2          |
| 10     | 15 мин        | 1 мин           | 2          |
| 11     | 30 мин        |                 | 1          |

- Выбор недели только заполняет поля формы. Значения можно поправить вручную, проверяются и сохраняются они как раньше. Отдельной настройки «выбранная неделя» нет.
- Чип подсвечивается, если значения в полях совпадают с неделей программы.
- Решения, принятые при планировании:
  - нулевая ходьба разрешается при одном повторе: при 1 повторе тренировка состоит из одного бега (план `docs/plans/completed/20261102-remove-last-walk.md`), поле ходьбы не используется. Это отменяет решение того плана «длительность ходьбы обязательна всегда» и меняет US-2. Неделя 11 подставляет ходьбу 0:00;
  - при нескольких повторах нулевая ходьба по-прежнему ошибка `WALK_ZERO`;
  - значения по умолчанию в `WorkoutSettings` (2 / 2 / 7) совпадают с неделей 2 и не меняются;
  - совпадение с неделей проверяется точно, по всем пяти полям `WorkoutSettings`. Поэтому 30 мин бега, 1 повтор и ходьба, например, 0:30 дают ту же тренировку, что неделя 11, но чип не подсвечивается. Это ожидаемое поведение, а не ошибка;
  - эта задача частично закрывает пункт «Сохранённые шаблоны тренировок» из раздела «Вне первой версии» `USER_STORIES.md`: шаблоны есть, но заданы в коде, своих сохранять нельзя. Пункт остаётся, с уточнением про свои шаблоны.

## Context (from discovery)
- Файлы:
  - `WorkoutSettings.kt`: `validate()` с проверкой `walkTotalSec == 0`;
  - `Workout.kt`: `workoutOrNull()` отклоняет `walkSec == 0`, им пользуется `WorkoutService` при разборе интента; KDoc `Workout` говорит, что длительности больше нуля;
  - `ui/SettingsContent.kt`: `SettingsInput`, `SettingsInput.from()`, форма в `Column` с `verticalScroll`;
  - `res/values/strings.xml`: строки формы и ошибок;
  - тесты `WorkoutSettingsTest.kt`, `WorkoutTest.kt`, `ui/SettingsInputTest.kt`.
- `Workout` при `walkSec = 0` и `rounds = 1` считает правильно: `cycleMs == runMs`, `totalMs == runMs`, `at()` всегда возвращает бег. При `rounds > 1` нулевая ходьба дала бы несколько отрезков бега подряд, такой режим не проверен и не нужен, поэтому она остаётся запрещённой.
- Compose BOM 2024.12.01, Material 3 1.3.x: `FilterChip` стабилен, `@OptIn` не нужен.
- План `docs/plans/20261102-run-history.md` ещё не реализован и тоже меняет `ui/SettingsContent.kt` и `USER_STORIES.md`. Кто влит вторым, тот разрешает конфликт и перенумеровывает историю.
- Новых зависимостей нет. Сервис, звук и вибрация не меняются, кроме общей с ним функции `workoutOrNull()`.
- Паттерны: логика в чистых функциях без Android API с unit-тестами, подписи передаются параметрами, Compose-код тонкий и тестами не покрыт.

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
- maintain backward compatibility

## Testing Strategy
- **unit tests**: required for every task. Команда: `JAVA_HOME=~/.local/jdk-17 ./gradlew test`.
- **e2e tests**: в проекте их нет, UI проверяется вручную (см. Post-Completion).

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview
- Программа описывается списком в новом файле `WorkoutPreset.kt`: номер недели и готовые `WorkoutSettings`. Секунды ходьбы недель 6–8 записываются как `walkMin = 1, walkSec = 30`, так поля формы получают обычные минуты и секунды без отдельного разбора.
- Функция `presetWeekOf(settings)` возвращает неделю, совпадающую с настройками, или `null`. Форма вызывает её на каждой рекомпозиции от `input.toSettings()`, поэтому подсветка следует за ручной правкой и после перезапуска без отдельного состояния.
- Нажатие на чип заменяет `input` на `SettingsInput.from(preset.settings)`. Проверка и итоговая длительность пересчитываются сами, потому что уже вычисляются из `input`.
- Ряд чипов стоит первым в форме, в `Row` с `horizontalScroll`: 11 чипов не помещаются по ширине. `LazyRow` не нужен, элементов мало.
- На экран помещается 3–4 чипа, поэтому при первом показе формы ряд прокручивается к подсвеченному чипу: иначе после перезапуска с неделей 7–11 выбор не виден. Прокрутка один раз через `LaunchedEffect(Unit)` и `BringIntoViewRequester` на подсвеченном чипе; флаг в `rememberSaveable` не повторяет её после поворота и возврата из настроек приложения. При ручной правке и выборе ряд не дёргается.

## Technical Details
- `WorkoutPreset.kt`:
  ```kotlin
  data class WorkoutPreset(val week: Int, val settings: WorkoutSettings)

  val WORKOUT_PRESETS: List<WorkoutPreset> = listOf(
      WorkoutPreset(1, WorkoutSettings(runMin = 1, runSec = 0, walkMin = 2, walkSec = 0, rounds = 7)),
      // ...
      WorkoutPreset(11, WorkoutSettings(runMin = 30, runSec = 0, walkMin = 0, walkSec = 0, rounds = 1)),
  )

  fun presetWeekOf(settings: WorkoutSettings): Int? =
      WORKOUT_PRESETS.firstOrNull { it.settings == settings }?.week
  ```
- `WorkoutSettings.validate()`: ветка `walkTotalSec == 0 -> WALK_ZERO` получает условие `&& rounds > 1`. Порядок веток прежний: диапазоны раньше нулей.
- `workoutOrNull()`: `walkSec > 0 || (walkSec == 0 && rounds == 1)`, отрицательные значения по-прежнему `null`.
- Строки: `preset_week` = «Неделя %1$d», `preset_title` = «Программа» (подпись над рядом чипов). `error_walk_zero` меняется на «Время ходьбы должно быть больше нуля, если повторов больше одного»: иначе после недели 11 и правки повторов ошибка не объясняет, почему раньше всё работало.
- Ожидаемые итоги недель вписываются в тест числами, а не считаются из `WORKOUT_PRESETS`, иначе тест проверяет сам себя и не поймает опечатку:

| Неделя | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Итог, мин | 19 | 26 | 23 | 22 | 22 | 27 | 19,5 | 23,5 | 27 | 31 | 30 |

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): изменения кода, тесты, документация в этом репозитории.
- **Post-Completion** (no checkboxes): ручная проверка на телефоне.

## Implementation Steps

### Task 1: Разрешить нулевую ходьбу при одном повторе

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutSettings.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/Workout.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutSettingsTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/ui/SettingsInputTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutStateTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] в `WorkoutSettings.validate()` возвращать `WALK_ZERO` только при `rounds > 1`
- [x] уточнить текст `error_walk_zero` (см. Technical Details)
- [x] в `workoutOrNull()` пропускать `walkSec == 0` при `rounds == 1`; поправить KDoc `Workout` про длительности
- [x] обновить тест `нулевое время ходьбы`: ошибка при нескольких повторах, `null` при одном
- [x] в тесте `WorkoutTest` на отклонение нулевых и отрицательных параметров заменить `workoutOrNull(runSec = 1, walkSec = 0, rounds = 1)` (сейчас `assertNull`, строка 99) на `rounds = 2`, иначе тест упадёт
- [x] добавить тесты: `workoutOrNull(1, 0, 1)` даёт тренировку, `workoutOrNull(1, -1, 1)` даёт `null`; `Workout(runSec = 3, walkSec = 0, rounds = 1)` длится 3 с и всё время показывает бег
- [x] обновить `SettingsInputTest`: пустые поля ходьбы при одном повторе не дают ошибки
- [x] добавить в `WorkoutStateTest` прогон `simulate(Workout(runSec = 3, walkSec = 0, rounds = 1), 250)` по образцу соседнего теста для одного повтора: нет `INTERVAL`, ровно один `FINISH`
- [x] запустить тесты, они должны пройти

### Task 2: Программа по неделям в `WorkoutPreset.kt`

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/WorkoutPreset.kt`
- Create: `app/src/test/java/com/ssortia/runtimer/WorkoutPresetTest.kt`

- [x] описать `WorkoutPreset`, список `WORKOUT_PRESETS` из 11 недель по таблице и `presetWeekOf()`
- [x] написать тесты: недели идут с 1 по 11 без пропусков, каждая проходит `validate()`, итоговая длительность каждой недели совпадает с таблицей
- [x] написать тесты `presetWeekOf()`: точное совпадение возвращает неделю, значения по умолчанию дают неделю 2, отличие в секундах или повторах даёт `null`
- [x] запустить тесты, они должны пройти

### Task 3: Ряд чипов на форме

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] добавить строки `preset_title` и `preset_week`
- [x] добавить первым элементом формы подпись и `Row` с `horizontalScroll` из `FilterChip` по `WORKOUT_PRESETS`
- [x] `selected` у чипа: `presetWeekOf(settings) == preset.week`, где `settings` уже вычислен из `input`
- [x] нажатие на чип: `input = SettingsInput.from(preset.settings)`
- [x] при первом показе формы прокрутить ряд к подсвеченному чипу (см. Solution Overview)
- [x] UI тестами не покрыт по соглашению проекта, логика проверена в Task 2; запустить `./gradlew test assembleDebug lintDebug`, всё должно пройти

### Task 4: Verify acceptance criteria
- [x] verify all requirements from Overview are implemented
- [x] verify edge cases are handled: неделя 11 с ходьбой 0:00 запускается, нулевая ходьба при 2+ повторах по-прежнему ошибка, ручная правка снимает подсветку
- [x] run full test suite: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [x] run lint: `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`
- [x] verify test coverage meets project standard: вся новая логика в `WorkoutPreset.kt` и изменённые проверки покрыты тестами

### Task 5: [Final] Update documentation

**Files:**
- Modify: `USER_STORIES.md`
- Modify: `README.md`
- Modify: `docs/tasks/20261103-preset-weeks.md`
- Delete: `plan.md`

- [x] `USER_STORIES.md`, US-2: пункт «Тренировка не запускается, если длительность бега или ходьбы равна нулю» заменить на «…если время бега нулевое или время ходьбы нулевое при 2 и более повторах»; пункт «Длительность ходьбы обязательна и при 1 повторе» заменить на «При 1 повторе время ходьбы может быть нулевым»
- [x] `USER_STORIES.md`: добавить историю «Выбрать тренировку из программы» со следующим свободным номером (US-13, если план `docs/plans/20261102-run-history.md` ещё не влит; он тоже займёт следующий номер) с критериями приёмки и таблицей программы; уточнить пункт «Сохранённые шаблоны тренировок» в «Вне первой версии»: остаются только свои шаблоны
- [x] `README.md`: описать программу в «Как это работает», добавить `WorkoutPreset.kt` в структуру проекта; проверить строку `WorkoutSettings.kt` в структуре и описание проверки формы на правило про ходьбу
- [x] отметить выполненные пункты в `docs/tasks/20261103-preset-weeks.md`
- [x] удалить `plan.md`: источником программы становится `WorkoutPreset.kt`. Файл не в git, таблица остаётся в этом плане и в задаче
- [ ] move this plan to `docs/plans/completed/` (после ревью, выполняет оркестратор)

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification** (на телефоне):
- выбор недели заполняет поля, итоговая длительность пересчитывается; у недель 6–8 ходьба 1 мин 30 сек;
- при первом запуске подсвечена неделя 2; ручная правка любого поля снимает подсветку, возврат значений возвращает её;
- неделя 11 запускается, за 30 минут звучит только финальный сигнал;
- значения сохраняются после старта и перезапуска приложения, подсветка восстанавливается; после перезапуска с неделей 11 ряд прокручен и её чип виден;
- ряд чипов прокручивается на узком экране, поворот экрана не сбрасывает поля.
