#!/usr/bin/env bash
# Собирает debug-APK, запускает эмулятор, устанавливает и открывает приложение.
# Если эмулятор уже запущен, использует его.
set -euo pipefail

cd "$(dirname "$0")"

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
AVD="${AVD:-test}"
PACKAGE=com.ssortia.runtimer
APK=app/build/outputs/apk/debug/app-debug.apk
BOOT_TIMEOUT_SEC=180

if [[ -z "${JAVA_HOME:-}" ]] && ! command -v java >/dev/null; then
    export JAVA_HOME="$HOME/.local/jdk-17"
fi

./gradlew assembleDebug

if ! "$ADB" devices | grep -q '^emulator-'; then
    echo "Запуск эмулятора $AVD..."
    # Отдельная сессия: эмулятор продолжит работать после завершения скрипта
    setsid nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" >/dev/null 2>&1 &
fi

echo "Ожидание загрузки Android..."
for ((i = 0; i < BOOT_TIMEOUT_SEC; i++)); do
    booted=$("$ADB" -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    [[ "$booted" == 1 ]] && break
    sleep 1
done
if [[ "${booted:-}" != 1 ]]; then
    echo "Эмулятор не загрузился за $BOOT_TIMEOUT_SEC с" >&2
    exit 1
fi

"$ADB" -e install -r "$APK"
"$ADB" -e shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
echo "Приложение запущено"
