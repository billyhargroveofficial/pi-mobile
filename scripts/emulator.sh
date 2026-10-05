#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
case "${1:-start}" in
 start)
  if "$ADB" devices | grep -q '^emulator-5554[[:space:]]*device'; then echo 'Emulator already running'; exit 0; fi
  mkdir -p "$ROOT/artifacts"
  nohup "$SDK/emulator/emulator" -avd PiMobile_API35 -port 5554 -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 2048 -cores 2 > "$ROOT/artifacts/emulator.log" 2>&1 < /dev/null &
  echo "Emulator PID $!; control with adb -s emulator-5554"
  ;;
 stop) "$ADB" -s emulator-5554 emu kill ;;
 install) "$ADB" -s emulator-5554 install -r "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"; "$ADB" -s emulator-5554 shell am start -n ru.billyhargrove.pimobile/.MainActivity ;;
 screenshot) mkdir -p "$ROOT/artifacts"; "$ADB" -s emulator-5554 exec-out screencap -p > "$ROOT/artifacts/android.png"; echo "$ROOT/artifacts/android.png" ;;
 *) echo 'Usage: emulator.sh start|stop|install|screenshot' >&2; exit 2;;
esac
