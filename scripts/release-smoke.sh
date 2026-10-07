#!/usr/bin/env bash
# Installs the minified release APK on the running emulator, opens it and checks it stays up.
# Catches R8 removing something Hilt, Room or kotlinx.serialization need at runtime, which the
# (unminified) debug-build tests can't see. Used by .github/workflows/device-tests.yml.
set -euo pipefail

apk=app/build/outputs/apk/release/app-release.apk
pkg=com.hardtekpt.crux

adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity"
sleep 15

if adb logcat -d -b crash | grep -q "$pkg"; then
  echo "::error::The release build crashed after launch."
  adb logcat -d -b crash
  exit 1
fi
if ! adb shell pidof "$pkg" > /dev/null; then
  echo "::error::The release build closed after launch."
  exit 1
fi
echo "The release build launched and stayed up."
