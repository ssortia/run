# Задержка перед стартом тренировки

На экране настроек можно указать, через сколько секунд после нажатия «Старт» начнётся таймер. За это время бегун успевает убрать телефон в карман и приготовиться.

- Диапазон 0–60 секунд, по умолчанию 5. При 0 тренировка начинается сразу, как сейчас.
- Значение сохраняется вместе с остальными настройками.
- Задержка не входит в итоговую длительность на форме и в прогресс тренировки.
- Обратный отсчёт идёт в `WorkoutService`, а не на экране: за это время телефон блокируют и убирают в карман.
- Конец задержки слышно: бегун не смотрит на экран, поэтому без сигнала не узнает, что пора бежать.

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutSettings.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/SettingsStore.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutState.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/Tones.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/ActiveContent.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutSettingsTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/SettingsStoreTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutStateTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/TonesTest.kt`
- Modify: `USER_STORIES.md`
- Modify: `README.md`

- [x] добавить задержку в `WorkoutSettings`, в `validate()` и в `SettingsStore`, новую ошибку показывать как остальные `SettingsError`
- [x] добавить поле на экран настроек
- [x] передавать задержку в сервис через `startIntent`, отрицательное значение отклонять как неверный запрос
- [x] добавить состояние обратного отсчёта в `WorkoutState`: экран показывает оставшиеся секунды и кнопку «Стоп», уведомление показывает отсчёт
- [x] отсчитывать задержку в сервисе от `SystemClock.elapsedRealtime()`, время тренировки начинать считать только после неё
- [x] увеличить таймаут wake lock на длительность задержки
- [x] добавить сигнал `START` в конце задержки, на слух отличный от `INTERVAL` и `FINISH`; при задержке 0 сигнал не звучит
- [x] проверить по коду, что «Стоп» во время отсчёта сразу завершает сервис, снимает уведомление и новых сигналов нет
- [x] написать тесты: диапазон и значение по умолчанию, сохранение и чтение, состояние отсчёта, тоны `START`, тексты уведомления
- [x] добавить историю в `USER_STORIES.md` и обновить `README.md`
- [x] запустить тесты, они должны пройти
- [ ] проверить на телефоне: нажать «Старт», заблокировать экран, убрать телефон в карман; сигнал старта и первая смена отрезка звучат вовремя; «Стоп» во время отсчёта сразу завершает сервис, снимает уведомление и новых сигналов нет
