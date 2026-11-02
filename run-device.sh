#!/usr/bin/env bash
# Собирает debug-APK, устанавливает его на телефон по USB и открывает приложение.
# С флагом --logs после запуска показывает логи приложения (выход по Ctrl+C).
set -euo pipefail

cd "$(dirname "$0")"

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
PACKAGE=com.ssortia.runtimer
APK=app/build/outputs/apk/debug/app-debug.apk

logs=false
case "${1:-}" in
    "") ;;
    --logs) logs=true ;;
    *)
        echo "Использование: $0 [--logs]" >&2
        exit 2
        ;;
esac

# Проверка до сборки: без телефона незачем ждать Gradle
state=$("$ADB" -d get-state 2>/dev/null || true)
if [[ "$state" != device ]]; then
    echo "Телефон не готов. Подключите его по USB, включите отладку и подтвердите запрос на экране." >&2
    "$ADB" devices -l >&2
    exit 1
fi

./build-apk.sh

"$ADB" -d install -r "$APK"
"$ADB" -d shell am start -W -n "$PACKAGE/.MainActivity" >/dev/null
echo "Приложение запущено"

if [[ "$logs" == true ]]; then
    exec "$ADB" -d logcat --pid="$("$ADB" -d shell pidof -s "$PACKAGE" | tr -d '\r')"
fi
