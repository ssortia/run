#!/usr/bin/env bash
# Собирает debug-APK.
set -euo pipefail

cd "$(dirname "$0")"

if [[ -z "${JAVA_HOME:-}" ]] && ! command -v java >/dev/null; then
    export JAVA_HOME="$HOME/.local/jdk-17"
fi

./gradlew assembleDebug
echo "APK: $PWD/app/build/outputs/apk/debug/app-debug.apk"
