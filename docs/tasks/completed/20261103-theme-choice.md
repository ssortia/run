# Выбор темы оформления

Сейчас тема всегда следует за системой: `RunTimerTheme` в `ui/Theme.kt` берёт `isSystemInDarkTheme()`, фон окна до первого кадра задают `values/themes.xml` и `values-night/themes.xml`. Нужно дать выбрать тему вручную на экране настроек приложения: «Как в системе», «Светлая», «Тёмная». По умолчанию «Как в системе», то есть поведение после обновления не меняется.

Что сделать:

1. **Модель и хранение.** Перечисление `ThemeMode` (`SYSTEM`, `LIGHT`, `DARK`) и чистая функция `isDarkTheme(mode, systemDark)` без Android API. Выбор хранится отдельным ключом в `SettingsStore` (`theme_mode`, строка с именем значения), как остальные настройки приложения, а не в `WorkoutSettings`. Неизвестное или отсутствующее значение читается как `SYSTEM`.
2. **Экран настроек.** В `AppSettingsContent` строка «Тема» с тремя вариантами (например, `SingleChoiceSegmentedButtonRow` или радиокнопки). Выбор применяется сразу, без кнопки «Сохранить», и сохраняется в `SettingsStore`. Подписи в `strings.xml`.
3. **Применение во всём UI.** Выбор должен менять тему сразу, не дожидаясь перезапуска activity, поэтому `MainActivity` держит его в состоянии Compose (`mutableStateOf`, начальное значение из `SettingsStore`) и передаёт вниз вместе с колбэком сохранения. Признак тёмной темы вычисляется один раз и передаётся туда, где сейчас вызывается `isSystemInDarkTheme()`: `RunTimerTheme`, `darkStatusIcons` в `MainActivity`, `segmentColors` и `countdownColors` в `ActiveContent`, чипы недель в `SettingsContent`. Удобно отдать его через `CompositionLocal` из `RunTimerTheme`, тогда экранам не нужен новый параметр. Ни одного прямого вызова `isSystemInDarkTheme()` вне вычисления признака остаться не должно, иначе цвета отрезков или значки строки состояния разойдутся с темой.
4. **Фон окна при запуске.** Тема окна из `values-night` знает только о системной теме: при выбранной светлой теме и тёмной системе (и наоборот) до первого кадра мелькнёт чужой фон. На API 31+ можно вызвать `UiModeManager.setApplicationNightMode()` при сохранении выбора: система запоминает его сама и применяет к окну и заставке до старта процесса. На API 26–30 достаточно `window.setBackgroundDrawable` в `onCreate` до `setContent` по сохранённому выбору; короткая вспышка системной заставки там допустима, это записать в «Известные ограничения». Если `setApplicationNightMode` пересоздаёт activity, проверить, что форма не теряет несохранённые правки (`rememberSaveable`) и открытый экран стека.
5. **Тренировка.** Экраны отсчёта и тренировки тоже следуют выбору. Сервис и уведомление тему не используют, их трогать не нужно.

**Files:**
- Create: `app/src/main/java/com/ssortia/runtimer/ThemeMode.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/SettingsStore.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/Theme.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/AppSettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ActiveContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/com/ssortia/runtimer/ThemeModeTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/SettingsStoreTest.kt`
- Modify: `README.md`
- Modify: `USER_STORIES.md`
- Modify: `CLAUDE.md`

- [x] добавить `ThemeMode`, `isDarkTheme` и хранение выбора в `SettingsStore`
- [x] добавить выбор темы на экран настроек приложения
- [x] применять выбор во всём UI сразу, убрать прямые вызовы `isSystemInDarkTheme()` из экранов и `MainActivity`
- [x] убрать вспышку чужого фона при запуске (API 31+ через `UiModeManager`, ниже через фон окна)
- [x] написать тесты: `isDarkTheme` для всех сочетаний, чтение неизвестного и отсутствующего значения, сохранение и чтение каждого варианта
- [x] обновить `README.md` (раздел «Интерфейс», таблица структуры для `ui/Theme.kt`, ограничения), `USER_STORIES.md` (новая история о выборе темы) и `CLAUDE.md` (настройка темы в списке настроек `SettingsStore`, признак тёмной темы только из одного места)
- [x] запустить тесты и lint, они должны пройти
- [ ] проверить на телефоне: каждый из трёх вариантов меняет тему сразу на форме, настройках и в истории; цвета отрезков и значки строки состояния на тренировке совпадают с выбранной темой; при теме, отличной от системной, при запуске нет вспышки чужого фона; «Как в системе» следует за переключением системной темы на лету; выбор переживает перезапуск приложения; при смене темы нет чёрного кадра; при переходах между формой, настройками и историей экраны не накладываются
