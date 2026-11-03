# Ревью изменений сигналов и настроек

Изменения 2–3 ноября 2026 года внесены без плана, по одному запросу за раз, и закоммичены одним коммитом «feat: Звуки из файлов, приглушение заранее, настройки на отдельном экране». Их нужно проверить целиком и исправить найденное. Тесты, сборка и lint на момент постановки задачи проходили, на телефоне ничего не проверялось.

Что вошло:

1. **Звуки сигналов из файлов.** Смена отрезка и старт звучат файлом `res/raw/signal_interval.wav` («New Notification 062»), финал файлом `signal_finish.wav` («New Notification 022»). Оба звука автора Universfield с Pixabay, Pixabay Content License. Файлы обрезаны и приведены к PCM 16 бит моно 44,1 кГц. Синтез тонов удалён, `Tones.kt` переименован в `SignalSound.kt`. Разбор WAV лежит в `Wav.kt`, длительности звуков продублированы константами, а `SignalFilesTest` сверяет их с файлами.
2. **Приглушение музыки заранее.** `Beeper.duck()` берёт аудиофокус за `DUCK_AHEAD_MS` (1 с) до каждого сигнала: перед сменой отрезка, перед финалом и в конце отсчёта. `stopDucking()` возвращает фокус на паузе. Тишина перед звуком удалена, сигнал звучит точно на границе отрезка.
3. **Сигнал смены отрезка одиночный.** Вибрация смены отрезка теперь один импульс 400 мс.
4. **Задержка старта на экране настроек.** Задержка убрана из `WorkoutSettings`, формы и интента сервиса. Она хранится отдельным ключом в `SettingsStore` под прежним именем `start_delay_sec`, сервис читает её в `startWorkout`. На экране это ползунок от 0 до 60 с шагом 5, логика в `StartDelay.kt`. Ошибка `START_DELAY_RANGE` и её строка удалены.
5. **Громкость на экране настроек.** Ползунок громкости перенесён с формы на экран настроек.
6. **Запуск на телефоне.** Скрипт `run-device.sh` уже закоммичен, проверять его не нужно.

На что смотреть:
- контракт сервиса из `CLAUDE.md`: `duck()` и `stopDucking()` вызываются из цикла в главном потоке, `release()` в `onDestroy` снимает и заранее взятый фокус;
- `shouldDuck` получает `position.remainingMs`, то есть считается, что каждый отрезок кончается сигналом. Это надо проверить для последнего отрезка, для паузы внутри секунды до сигнала и для продолжения после неё;
- что будет, если `requestAudioFocus` в `duck()` отклонён: `holding` всё равно становится `true`;
- `decodeWav` на неожиданных файлах и поведение `Beeper`, если сигнал не подготовлен;
- остались ли ссылки на удалённое: `DUCK_LEAD_MS`, `tonesFor`, `synthesize`, `startDelaySec`, `START_DELAY_RANGE`, строки `field_start_delay` и `error_start_delay_range`;
- согласованность `README.md`, `USER_STORIES.md` и `CLAUDE.md` с кодом: длительности, частоты, описание экранов, раздел «Известные ограничения»;
- задача истории пробежек и её план (`docs/plans/20261102-run-history.md`) опираются на `SettingsContent` с полем задержки и на прежнюю форму. После переноса задержки и громкости план нужно сверить с кодом.

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/SignalSound.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/Wav.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/Beeper.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutService.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/MainActivity.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/Vibration.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/StartDelay.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/SettingsStore.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/WorkoutSettings.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/Workout.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/AppSettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/SettingsContent.kt`
- Modify: `app/src/main/java/com/ssortia/runtimer/ui/WorkoutScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/raw/signal_interval.wav`
- Modify: `app/src/main/res/raw/signal_finish.wav`
- Modify: тесты `SignalSoundTest`, `SignalFilesTest`, `WavTest`, `StartDelayTest`, `SettingsStoreTest`, `VibrationTest`, `WorkoutSettingsTest`, `WorkoutTest`, `ui/SettingsInputTest`
- Modify: `README.md`
- Modify: `USER_STORIES.md`
- Modify: `CLAUDE.md`
- Modify: `docs/plans/20261102-run-history.md`

- [ ] провести ревью по списку выше и исправить найденное
- [x] сверить план истории пробежек с текущей формой и экраном настроек
- [ ] запустить тесты и lint, они должны пройти
- [ ] закоммитить исправления
- [ ] проверить на телефоне: звуки слышны поверх музыки, музыка приглушается заранее и возвращается после сигнала и на паузе, ползунки на экране настроек сохраняют значения, пробный сигнал звучит
