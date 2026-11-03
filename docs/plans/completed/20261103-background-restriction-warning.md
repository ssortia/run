# Предупреждение об ограничении фоновой работы

## Overview
- На realme (realme UI 6, Android 15, RMX3741) служба прошивки `OplusProxyWakeLock` через 60 с после выключения экрана забирает wake lock сервиса. Процессор засыпает, `runLoop` стоит на `delay(TICK_MS)`, и сигналы пропадают до включения экрана. Время при этом не сбивается: оно считается от `elapsedRealtime`, и первый шаг после пробуждения подаёт пропущенный сигнал.
- Помогает только переключатель прошивки «О приложении → Использование батареи → Разрешить работу в фоновом режиме». Публичного API у него нет, но включённый переключатель добавляет приложение в белый список Doze, и `PowerManager.isIgnoringBatteryOptimizations(packageName)` начинает возвращать `true`. Одного белого списка Doze мало.
- План добавляет на форму предупреждение под кнопкой «Старт», если `isIgnoringBatteryOptimizations` возвращает `false`, и кнопку «Настройки батареи», которая ведёт на системную страницу приложения. Признак перечитывается при каждом возврате activity в `RESUMED`. Старт тренировки предупреждение не блокирует.
- Исходная задача: `docs/tasks/completed/20261103-background-restriction-warning.md`.

## Context (from discovery)
- `MainActivity.kt`: в `setContent` уже есть состояние Compose уровня activity (`permissionPending`, `requestAbandoned`) и `LifecycleEventEffect(Lifecycle.Event.ON_STOP)`. Системные службы берутся через `by lazy` (`audioManager`). Колбэки в `WorkoutScreen` передаются ссылками на методы или лямбдами.
- `ui/WorkoutScreen.kt`: в маршруте `settings` колбэки `SettingsContent` `onOpenHistory` и `onOpenSettings` обёрнуты в `dropUnlessResumed`. Новый колбэк оборачивается так же.
- `ui/SettingsContent.kt`: `Column` с `spacedBy(24.dp)`, под `Button` «Старт» стоит `Row` с кнопками «История» и «Настройки» (`TextButton`, `titleMedium`).
- `res/values/strings.xml`: предупреждения названы `warning_*` (`warning_no_sound`, `warning_muted`), кнопки `button_*`.
- `minSdk = 26`, `targetSdk = 35`: `isIgnoringBatteryOptimizations` (API 23) и `ACTION_APPLICATION_DETAILS_SETTINGS` доступны без проверок версии. `<queries>` в манифесте не нужен: видимость пакетов ограничивает `resolveActivity` и `queryIntentActivities`, а не `startActivity`.
- `ui/ActiveContent.kt`: `SoundWarning` использует `bodyLarge` с `TextAlign.Center` и обычным цветом текста. На форме `colorScheme.error` уже занят ошибкой проверки полей над «Старт».
- `README.md`, таблица файлов: строки `MainActivity.kt` и `ui/SettingsContent.kt` описывают их обязанности и требуют дополнения.
- `USER_STORIES.md`, US-14: «Под кнопкой «Старт» в одной строке стоят кнопки «История» и «Настройки»». Блок предупреждения встаёт между ними, критерий надо уточнить.
- Признак `false` по умолчанию почти на всех телефонах, в том числе на стоковом Android и у Samsung: приложение не в белом списке Doze. Поэтому предупреждение видно сразу после установки, пока работа в фоне не разрешена.
- Документация: `README.md` («Как это работает → Фоновая работа», «Известные ограничения», пункт про Xiaomi, Huawei и Samsung), `USER_STORIES.md` (US-7 «Держать телефон в кармане»), `CLAUDE.md` («Неочевидное»).

## Development Approach
- **testing approach**: Regular (сначала код, потом тесты)
- задачи выполняются по одной, следующая начинается только после завершения предыдущей
- изменения маленькие и сфокусированные
- **CRITICAL: каждая задача с изменением кода включает новые или обновлённые тесты**, если в ней появляется логика без Android API
- **CRITICAL: все тесты проходят до начала следующей задачи**
- **CRITICAL: при изменении объёма работ план обновляется**
- В этом плане новых unit-тестов нет, и это намеренно. Решение показывать предупреждение тривиально (`!ignoring`), задача прямо запрещает выносить его в отдельную чистую функцию. Остальной код работает с Android API (`PowerManager`, `Intent`, Compose) и по соглашению проекта тестами не покрыт. Правильность проверяется сборкой, lint и ручной проверкой на телефоне.

## Testing Strategy
- **unit tests**: новых нет (см. выше). Существующие тесты должны проходить: `./gradlew test`.
- **lint**: `./gradlew lintDebug` без новых предупреждений.
- **e2e tests**: в проекте их нет.

## Progress Tracking
- выполненный пункт сразу отмечается `[x]`
- новые задачи добавляются с префиксом ➕
- проблемы и блокеры отмечаются префиксом ⚠️

## Solution Overview
- **Чтение признака в `MainActivity`.** Состояние `ignoringBatteryOptimizations` хранится в `remember { mutableStateOf(...) }` внутри `setContent`. Начальное значение читается сразу, иначе при первом показе предупреждение мигнуло бы или появилось бы с задержкой. `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` перечитывает значение при каждом возврате activity в `RESUMED`, в том числе при возврате из системных настроек кнопкой «Назад». `rememberSaveable` не нужен: значение всегда читается заново, а не восстанавливается.
- **Уровень activity, а не маршрута.** Признак меняется только вне приложения. Переходы внутри `NavHost` (история → форма) его изменить не могут, а при уходе в системные настройки activity сама проходит `ON_PAUSE` и `ON_RESUME`. Поэтому хватает события activity, и `SettingsContent` получает готовое значение, как `HistoryContent` получает готовый список.
- **Проброс в форму.** `WorkoutScreen` и `SettingsContent` получают параметр `showBackgroundWarning: Boolean` (в `MainActivity` это `!ignoringBatteryOptimizations`) и колбэк `onOpenAppDetails: () -> Unit`. Имя колбэка отличается от существующего `onOpenSettings`, который ведёт на экран настроек приложения. В `WorkoutScreen` колбэк обёрнут в `dropUnlessResumed`, иначе двойное нажатие открыло бы две системные страницы.
- **Открытие страницы приложения.** `MainActivity.openAppDetails()` запускает `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))`. `ActivityNotFoundException` ловится и пишется в лог: на урезанной прошивке страницы может не быть, и ронять из-за этого приложение не нужно. `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` не используется: он только добавляет приложение в белый список Doze, а этого мало. Кроме того, для него нужно разрешение `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, которое ограничивают правила Google Play.
- **Вид предупреждения.** Блок стоит сразу под «Старт», перед строкой «История» и «Настройки». Это отдельная `Column` с меньшим интервалом, чтобы текст и кнопка читались как одно целое, а не как два элемента формы с шагом 24 dp. Текст обычного цвета, как у `SoundWarning`, а не `colorScheme.error`: красный текст под «Старт» выглядел бы как вторая ошибка формы, мешающая старту. Текст предупреждения не обещает, что после настройки сигналы будут звучать: признак неточный.

## Technical Details
- `MainActivity`:
  - `private val powerManager by lazy { getSystemService(PowerManager::class.java) }`;
  - `private fun readIgnoringBatteryOptimizations(): Boolean = powerManager.isIgnoringBatteryOptimizations(packageName)`. Имя функции отличается от имени состояния, чтобы присваивание не читалось как рекурсия;
  - в `setContent`:
    ```kotlin
    var ignoringBatteryOptimizations by remember { mutableStateOf(readIgnoringBatteryOptimizations()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ignoringBatteryOptimizations = readIgnoringBatteryOptimizations()
    }
    ```
    При первой композиции наблюдатель получает догоняющий `ON_RESUME` и читает значение ещё раз, это безвредно;
    Над этим кодом нужен комментарий: признак неточный, а перечитывается он на `ON_RESUME`, потому что пользователь включает переключатель в системных настройках и возвращается кнопкой «Назад»;
  - `WorkoutScreen(..., showBackgroundWarning = !ignoringBatteryOptimizations, onOpenAppDetails = ::openAppDetails, ...)`;
  - `private fun openAppDetails()` с `try { startActivity(intent) } catch (e: ActivityNotFoundException) { Log.w(TAG, ...) }`.
- `WorkoutScreen`: параметры `showBackgroundWarning: Boolean` и `onOpenAppDetails: () -> Unit`. В маршруте `settings` они передаются так: `showBackgroundWarning = showBackgroundWarning` и `onOpenAppDetails = dropUnlessResumed { onOpenAppDetails() }`.
- `SettingsContent`: параметры `showBackgroundWarning: Boolean` и `onOpenAppDetails: () -> Unit` перед `modifier`. Под `Button` «Старт»:
  ```kotlin
  if (showBackgroundWarning) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
              text = stringResource(R.string.warning_background),
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
          )
          OutlinedButton(onClick = onOpenAppDetails, modifier = Modifier.fillMaxWidth()) {
              Text(stringResource(R.string.button_open_app_details))
          }
      }
  }
  ```
  Цвет текста обычный, как у `SoundWarning` (см. Solution Overview).
- `strings.xml`:
  - `warning_background` = «Система может остановить сигналы при выключенном экране»;
  - `button_open_app_details` = «Настройки батареи». Подпись отличается от кнопки «Настройки» приложения на той же форме. Кнопка всё равно ведёт на общую страницу приложения: прямой страницы батареи для одного приложения в публичном API нет, а на realme «Использование батареи» лежит на этой странице.

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): код, сборка, lint, документация в репозитории.
- **Post-Completion** (без чекбоксов): ручная проверка на телефоне realme.

## Implementation Steps

### Task 1: Чтение признака и открытие страницы приложения в `MainActivity`

Параметры в `SettingsContent` и их проброс из `WorkoutScreen` появляются только в Task 2, вместе с использованием: так между задачами не остаётся неиспользуемых параметров.

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`

- [x] добавить `powerManager` и `readIgnoringBatteryOptimizations()` в `MainActivity`
- [x] хранить признак в состоянии Compose и перечитывать в `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)`
- [x] добавить `openAppDetails()` с `ACTION_APPLICATION_DETAILS_SETTINGS` и перехватом `ActivityNotFoundException`
- [x] добавить параметры `showBackgroundWarning` и `onOpenAppDetails` в `WorkoutScreen` и передать их из `MainActivity`
- [x] тесты: новых нет (Android API, см. Development Approach); `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug` проходит

### Task 2: Предупреждение и кнопка на форме

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] добавить строки `warning_background` и `button_open_app_details`
- [x] добавить параметры `showBackgroundWarning` и `onOpenAppDetails` в `SettingsContent`, в маршруте `settings` у `WorkoutScreen` передать их, обернув колбэк в `dropUnlessResumed`
- [x] показать блок предупреждения под «Старт» при `showBackgroundWarning`: текст обычного цвета с `TextAlign.Center`, `OutlinedButton`
- [x] убедиться, что `Button` «Старт» не зависит от `showBackgroundWarning` (старт не блокируется)
- [x] тесты: новых нет (Compose); `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug lintDebug` проходит без новых предупреждений lint

### Task 3: Verify acceptance criteria
- [x] предупреждение показывается только при `isIgnoringBatteryOptimizations == false` и перечитывается на `ON_RESUME`
- [x] кнопка открывает страницу приложения, а не `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; разрешение `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` в манифест не добавлено
- [x] `SettingsContent` не импортирует `PowerManager` и `android.provider.Settings`
- [x] текст не обещает, что сигналы точно будут звучать
- [x] полный прогон: `JAVA_HOME=~/.local/jdk-17 ./gradlew test assembleDebug lintDebug`

### Task 4: [Final] Update documentation

**Files:**
- Modify: `USER_STORIES.md`
- Modify: `README.md`
- Modify: `CLAUDE.md`

- [x] `USER_STORIES.md`, US-7: критерий «Если система может ограничить работу приложения в фоне, форма под „Старт“ предупреждает об этом и даёт кнопку „Настройки батареи“ со страницей приложения; старт предупреждение не блокирует. На большинстве телефонов предупреждение видно сразу после установки, пока работа в фоне не разрешена. Предупреждение может пропасть, хотя ограничение осталось»
- [x] `USER_STORIES.md`, US-14: уточнить критерий о кнопках «История» и «Настройки»: они стоят под «Старт», а при показанном предупреждении о фоновой работе под ним
- [x] `README.md`, «Как это работает → Фоновая работа»: предупреждение на форме, признак `isIgnoringBatteryOptimizations`, его неточность, показ по умолчанию после установки, почему не используется `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- [x] `README.md`, «Как это работает → Сон процессора»: прошивки realme, OPPO и OnePlus (`OplusProxyWakeLock`) забирают wake lock через минуту после выключения экрана, если работа в фоне не разрешена
- [x] `README.md`, таблица файлов: в строке `MainActivity.kt` упомянуть проверку ограничения фоновой работы и открытие страницы приложения, в строке `ui/SettingsContent.kt` предупреждение о фоновой работе
- [x] `README.md`, «Известные ограничения»: дополнить пункт про Xiaomi, Huawei и Samsung путём для realme, OPPO и OnePlus («О приложении → Использование батареи → Разрешить работу в фоновом режиме») и симптомом: сигналы пропадают через минуту после выключения экрана и звучат при его включении, таймер показывает верное время
- [x] `CLAUDE.md`, «Неочевидное»: `OplusProxyWakeLock` забирает wake lock через 60 с, белого списка Doze мало, `isIgnoringBatteryOptimizations` лишь косвенный и неточный признак переключателя, перечитывается на `ON_RESUME` в `MainActivity`, запрос `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` не используется намеренно
- [x] перенести задачу в `docs/tasks/completed/`: остаётся только ручная проверка на телефоне
- [x] перенести план в `docs/plans/completed/`

## Post-Completion
*Ручные проверки, вне репозитория*

**Проверка на телефоне realme (RMX3741):**
- при выключенном «Разрешить работу в фоновом режиме» форма показывает предупреждение и кнопку;
- кнопка открывает страницу приложения, двойное нажатие не открывает две страницы;
- после включения переключателя и возврата «Назад» предупреждение пропадает без перезапуска приложения;
- после выключения переключателя и возврата предупреждение появляется снова;
- «Старт» работает и при видимом предупреждении;
- если есть телефон на другой прошивке, посмотреть вид формы с предупреждением и куда ведёт кнопка;
- тренировка дольше 2 минут с выключенным экраном даёт все сигналы вовремя, wake lock держится до `onDestroy` (`adb shell dumpsys power`, лог `OplusProxyWakeLock`).
