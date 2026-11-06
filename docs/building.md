# Сборка, запуск и установка

## Требования

- Для сборки: JDK 17 и Android SDK (`platforms;android-35`, `build-tools;34.0.0`, `platform-tools` для `adb`), лицензии принимаются командой `sdkmanager --licenses`. Android Studio не обязательна. Gradle 8.11.1 скачивается через Gradle Wrapper, для первой сборки нужен интернет.
- Для запуска: телефон с Android 8.0 (API 26) или новее.

Версии инструментов: Android Gradle Plugin 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Room 2.6.1, Navigation Compose 2.8.5, Compose BOM 2024.12.01, `compileSdk` и `targetSdk` 35.

## Сборка

Если `java` в системе не JDK 17, задайте `JAVA_HOME` на JDK 17, например `JAVA_HOME=<путь к JDK 17> ./gradlew test assembleDebug`.

Путь к Android SDK задаётся в `local.properties` (файл не хранится в репозитории):

```properties
sdk.dir=/путь/к/Android/Sdk
```

Сборка и тесты:

```bash
./gradlew test assembleDebug
```

Проверка lint:

```bash
./gradlew lintDebug
```

Только сборка APK, скрипт сам подставит `JAVA_HOME`, если `java` нет в `PATH`:

```bash
./build-apk.sh
```

Готовый APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

На GitHub каждый push в `master` и каждый PR проверяет CI (`.github/workflows/ci.yml`): `./gradlew test lintDebug assembleDebug` на JDK 17. Debug-APK и отчёты тестов и lint лежат в артефактах прогона во вкладке Actions. Dependabot (`.github/dependabot.yml`) раз в неделю открывает PR с обновлениями Gradle-зависимостей и версий GitHub Actions; `androidx.core` из них исключён, причина в разделе «Неочевидное» `CLAUDE.md`. Зелёный CI не подтверждает звук, вибрацию и работу при выключенном экране: после обновления зависимостей их проверяют на телефоне.

## Запуск в эмуляторе

```bash
./run-emulator.sh
```

Скрипт собирает APK, запускает эмулятор, если он ещё не запущен, устанавливает и открывает приложение. Нужны пакеты `emulator` и образ Android, а также устройство `test`. Другое устройство задаётся так: `AVD=имя ./run-emulator.sh`.

## Установка на телефон

1. Скопируйте `app-debug.apk` на телефон.
2. Откройте файл и разрешите установку из неизвестного источника.
3. На Android 13 и новее при нажатии «Старт», пока разрешение на уведомления не выдано, приложение запрашивает его, в уведомлении показывается ход тренировки. После повторных отказов система перестаёт показывать запрос, разрешение включается в настройках. Без разрешения таймер работает, но уведомления не видно.

Подпись релизной версии не настроена, поэтому устанавливается отладочная сборка.
