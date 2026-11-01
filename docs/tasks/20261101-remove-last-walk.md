# Убрать ходьбу из последнего повтора

Тренировка заканчивается бегом: в последнем повторе нет отрезка ходьбы. Пример 2 / 2 / 7 даёт 13 отрезков и 26 минут.

**Files:**
- Modify: `app/src/main/java/com/ssortia/runtimer/Workout.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutTest.kt`
- Modify: `app/src/test/java/com/ssortia/runtimer/WorkoutStateTest.kt`
- Modify: `USER_STORIES.md`
- Modify: `README.md`
- Modify: `docs/plans/20261101-first-version.md`

- [ ] уменьшить `totalMs` в `Workout` на длительность одной ходьбы, чтобы `at()` возвращал `null` сразу после бега последнего повтора
- [ ] обновить тесты: длительность, конец тренировки, один повтор, итоговая длительность на форме
- [ ] проверить, что финальный сигнал, прогресс и экран в момент окончания верны, когда последний отрезок это бег
- [ ] обновить US-1 и US-6 в `USER_STORIES.md`, пример в `README.md` и ожидаемые значения в разделе ручной проверки плана первой версии
- [ ] запустить тесты, они должны пройти
