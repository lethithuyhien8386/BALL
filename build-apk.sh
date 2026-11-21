#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v gradle >/dev/null 2>&1; then
  echo "Chưa tìm thấy Gradle 8.9. Hãy cài Gradle/JDK17/Android SDK35 hoặc mở dự án bằng Android Studio." >&2
  exit 1
fi
gradle assembleDebug
echo "Đã tạo app/build/outputs/apk/debug/app-debug.apk"
