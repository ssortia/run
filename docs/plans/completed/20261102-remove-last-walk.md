# Убрать ходьбу из последнего повтора

## Overview
- Тренировка заканчивается бегом: в последнем повторе нет отрезка ходьбы. Постановка лежит в `docs/tasks/completed/20261101-remove-last-walk.md`.
- Ходьба после последнего бега бессмысленна: бегун уже закончил, а таймер ещё идёт. Пример 2 / 2 / 7 даёт 13 отрезков и 26 минут вместо 14 и 28.
- Меняется только длительность в `Workout`. Позиция, сигналы, прогресс, wake lock и итог на форме считаются от `totalMs` и следуют за ней сами.
- Решения, принятые при планировании:
  - при 1 повторе ходьбы нет вообще, но проверка `walkTotalSec == 0` и US-2 остаются: длительность ходьбы обязательна всегда, поле просто не используется;
  - новых настроек нет, поведение одно для всех тренировок.

## Context (from discovery)
- Файлы:
  - `Workout.kt`: `totalMs`, `at()`, `progressAt()`;
  - `WorkoutState.kt`: `tickAt` берёт финальную позицию из `workout.at(workout.totalMs - 1)`, код менять не нужно;
  - `WorkoutService.kt:93`: wake lock от `totalMs`, сокращается сам;
  - `ui/SettingsContent.kt:158`: строка «Всего» от `totalMs`, меняется сама.
- Паттерны: логика в чистых функциях с unit-тестами, Android-код не трогаем.
- Новых зависимостей нет.
- В списке файлов задачи не хватает двух тестов, которые сломаются:
  - `WorkoutSettingsTest`: пример 2/2/7 и проверка максимальных значений;
  - `NotificationTextTest:40`: `Workout(10, 20, 2).at(45_000)!!` при новой длительности 40 с даст `null` и `NullPointerException`.
- План паузы и вибрации (`docs/plans/20261101-pause-and-vibration.md`) не конфликтует: `WorkoutClock` получает `totalMs` параметром.
- В `README.md` есть незакоммиченная правка про `build-apk.sh`, не относящаяся к задаче. В коммит задачи она попасть не должна.

## Development Approach
- **testing approach**: Regular, сначала код, затем тесты в той же задаче.
- complete each task fully before moving to the next
- make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
  - update existing test cases if behavior changes
  - add new test cases for new code paths
  - tests cover both success and error scenarios
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- run tests after each change

## Testing Strategy
- **unit tests**: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug` после каждой задачи.
- **e2e tests**: в проекте их нет, эмулятора и устройства в окружении нет.
- Звук финала и вид экрана в момент окончания подтверждаются только на телефоне, см. Post-Completion.

## Progress Tracking
- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview
- `totalMs` уменьшается на длительность одной ходьбы. Формула `at()` не меняется: внутри последнего повтора `elapsedMs` не доходит до ходьбы, поэтому `at()` возвращает `null` сразу после последнего бега.
- Альтернативу с отдельным подсчётом отрезков не берём: она добавила бы код без новых возможностей.

## Technical Details
```kotlin
private val cycleMs = (runSec + walkSec) * 1000L
val totalMs = cycleMs * (rounds - 1) + runMs
```

- Обновляется KDoc `Workout`: в последнем повторе только бег.
- Последний отрезок: `index = (rounds - 1) * 2`, `isRun = true`, `round = rounds`. Всего отрезков `rounds * 2 - 1`.
- Финал: `tickAt` показывает последний бег с нулевым остатком и `progress = 1f`, сигнал `FINISH`. При 1 повторе обычных сигналов нет, только финальный.
- Новые ожидаемые значения:

| Тренировка | Было | Стало |
|---|---|---|
| 10 / 5 / 3 (`WorkoutTest`) | 45 с, 6 отрезков | 40 с, 5 отрезков |
| 10 / 10 / 2 (`WorkoutStateTest`) | 40 с, 4 отрезка, 3 обычных сигнала | 30 с, 3 отрезка, 2 обычных сигнала |
| 2 / 2 / 7 | 1 680 000 мс, 14 отрезков | 1 560 000 мс, 13 отрезков |
| 99:59 / 99:59 / 99 | 11 998 000 × 99 мс, 198 отрезков, `329:56:42` | 1 181 803 000 мс, 197 отрезков, `328:16:43` |

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): изменения кода, тестов и документации в репозитории.
- **Post-Completion** (no checkboxes): проверка на телефоне.

## Implementation Steps

### Task 1: Сократить длительность тренировки

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/Workout.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutStateTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutSettingsTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/NotificationTextTest.kt`

- [x] уменьшить `totalMs` на длительность одной ходьбы и обновить KDoc `Workout`
- [x] `WorkoutTest`: комментарий у `workout` («всего 40 с и 5 отрезков»), последний отрезок это бег с индексом 4 на `39_999`, `at(40_000)` и `at(40_001)` равны `null`, список номеров повторов заканчивается на `39_999`
- [x] `WorkoutTest`: переписать `один повтор состоит из двух отрезков` на один отрезок бега, `at(3_000)` равен `null`
- [x] `WorkoutStateTest`: обновить комментарий у `workout` («всего 30 с и 3 отрезка»), длительность, прогресс на `10_000`, `20_000`, `30_000` и `1`, границу `40_001` на `30_001`, прогресс шага внутри тренировки (`12_500 / 30_000`), `329:56:42` на `328:16:43`, пример итога `"28:00"` на `assertEquals("26:00", formatTotal(1_560_000))`
- [x] `WorkoutStateTest`: заменить `threeIntervalsAndFinish` на два обычных сигнала и финальный, переименовать тесты с «тремя» сигналами
- [x] `WorkoutStateTest`, тест `финал показывает последний отрезок`: `previousIndex = 2` заменить на `1`, иначе запоздавший финал больше ничего не перепрыгивает; ожидается бег `index = 2`, `round = 2`; комментарий: «Предыдущий шаг был в ходьбе: запоздавший финал всё равно показывает последний бег»
- [x] `WorkoutStateTest`: в `первый шаг уже после конца тренировки` ожидать индекс `2`, в проверке сбоя звука вызвать `tickAt(workout, 30_000, 2, soundFailed = true)`
- [x] написать тест: при 1 повторе цикл даёт только `FINISH`, финальная позиция это бег с нулевым остатком
- [x] `WorkoutSettingsTest`: пример 2/2/7 (`at(1_559_999)?.index == 12`, `at(1_560_000) == null`, название теста) и максимальные значения (индекс 196, граница `1_181_803_000`, комментарий)
- [x] `NotificationTextTest`: взять 3 повтора для теста с вычисленной позицией ходьбы (`Повтор 2/3`), финальная позиция становится бегом (`index = 12`, `isRun = true`, `Бег · 0:00 · Повтор 7/7`)
- [x] запустить тесты, они должны пройти

### Task 2: Проверить приёмочные критерии
- [x] убедиться, что `tickAt`, `signalFor`, `progressAt` и `WorkoutService` не требуют правок: финальный сигнал, прогресс 1 и экран верны, когда последний отрезок это бег
- [x] запустить полный набор: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug`
- [x] запустить lint: `JAVA_HOME=~/.local/jdk-17 ./gradlew lintDebug`

### Task 3: [Final] Обновить документацию

**Files:**
- Modify: `USER_STORIES.md`
- Modify: `README.md`
- Modify: `docs/plans/20261101-first-version.md`
- Modify: `docs/tasks/completed/20261101-remove-last-walk.md`

- [x] US-1: повтор состоит из бега и ходьбы, в последнем повторе только бег; пример 2/2/7 это 13 отрезков и 26 минут
- [x] US-6: «В конце последнего отрезка бега звучит только финальный сигнал»
- [x] `README.md`: пример в строке 5; незакоммиченную правку про `build-apk.sh` не трогать и в коммит задачи не включать: сохранить patch только с hunk строки 5 и проиндексировать его через `git apply --cached`
- [x] план первой версии, раздел ручной проверки: 10/10/2 даёт 2 обычных сигнала и 1 финальный, финальный у 2/2/7 звучит на 26-й минуте; строки реализации с `[x]` и таблицу результатов проверки не менять, это история
- [x] отметить выполненные пункты в `docs/tasks/completed/20261101-remove-last-walk.md`
- [x] перенести этот план в docs/plans/completed/ (отложено до конца ревью)

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification:**
- Короткая тренировка 10 с / 10 с / 2 повтора: 2 обычных сигнала и 1 финальный, финал звучит на 30-й секунде, сразу после второго бега.
- Тренировка из 1 повтора: один отрезок бега, только финальный сигнал, форма возвращается после него.
- В момент финала экран и уведомление показывают бег с `0:00`, а затем тренировка завершается как обычно.
- Строка «Всего» на форме для 2 / 2 / 7 показывает `26:00`.
