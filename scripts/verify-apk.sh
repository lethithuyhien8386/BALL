#!/usr/bin/env bash
# Fail CI if Gradle did not emit a parseable, signed and aligned APK.
set -euo pipefail
cd "$(dirname "$0")/.."
APK="$PWD/app/build/outputs/apk/debug/app-debug.apk"
SDK_TOOLS="${ANDROID_HOME:?ANDROID_HOME must point to Android SDK}/build-tools/35.0.0"
REPORT="$PWD/app/build/outputs/apk/debug/build-report.txt"

if [[ ! -s "$APK" ]]; then echo "ERROR: APK does not exist" >&2; exit 10; fi
if ! unzip -tq "$APK"; then echo "ERROR: APK ZIP is corrupt" >&2; exit 11; fi
if ! unzip -Z1 "$APK" | grep -qx 'classes.dex'; then
  echo "ERROR: classes.dex missing (not a compiled app)" >&2; exit 12
fi
if ! unzip -Z1 "$APK" | grep -qx 'AndroidManifest.xml'; then
  echo "ERROR: AndroidManifest.xml missing" >&2; exit 13
fi
"$SDK_TOOLS/zipalign" -c -v 4 "$APK"
"$SDK_TOOLS/apksigner" verify --verbose --print-certs "$APK"
BADGING="$("$SDK_TOOLS/aapt" dump badging "$APK")"
echo "$BADGING" | grep -q "package: name='com.example.bubblepilot'"
echo "$BADGING" | grep -q "sdkVersion:'26'"
echo "$BADGING" | grep -q "versionCode='5'"
echo "$BADGING" | grep -q "versionName='0.5.0'"
{
  echo "Bubble Pilot Astra — debug APK verification"
  echo "The ZIP uploaded by GitHub Actions is only a container. Extract app-debug.apk inside it."
  echo "APK bytes: $(stat -c '%s' "$APK")"
  sha256sum "$APK"
  echo "$BADGING" | grep -E '^package:|^sdkVersion:|^targetSdkVersion:'
  "$SDK_TOOLS/apksigner" verify --verbose "$APK"
} | tee "$REPORT"
echo "PASS: APK has dex + manifest, valid zip alignment, valid APK signature and Android >= 8 metadata."
