#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
case "${1:-start}" in
 start|run)
  if "$ADB" devices | grep -q '^emulator-5554[[:space:]]*device'; then echo 'Emulator already running'; exit 0; fi
  TASK_TEMP_DIR="/Users/billy/temp/pi-mobile-emulator"
  mkdir -p "$TASK_TEMP_DIR/runtime"
  DISPLAY_ARGS=(-no-window)
  if [[ "${PI_MOBILE_SHOW_WINDOW:-0}" == 1 ]]; then DISPLAY_ARGS=(); fi
  # The AVD disables its GPU; auto selected software rendering and consumed
  # eight host cores. Explicit host GPU avoids that fallback. ADB screenshots
  # and gestures work without a desktop window; stop between verification runs.
  if [[ "${1:-start}" == run ]]; then
   TMPDIR="$TASK_TEMP_DIR/runtime" exec "$SDK/emulator/emulator" -avd PiMobile_API35 -port 5554 -no-snapshot -no-audio -no-boot-anim -gpu "${PI_MOBILE_GPU:-host}" -memory 1536 -cores 2 "${DISPLAY_ARGS[@]}" > "$TASK_TEMP_DIR/emulator.log" 2>&1
  fi
  TMPDIR="$TASK_TEMP_DIR/runtime" nohup "$SDK/emulator/emulator" -avd PiMobile_API35 -port 5554 -no-snapshot -no-audio -no-boot-anim -gpu "${PI_MOBILE_GPU:-host}" -memory 1536 -cores 2 "${DISPLAY_ARGS[@]}" > "$TASK_TEMP_DIR/emulator.log" 2>&1 < /dev/null &
  echo "Emulator PID $!; control with adb -s emulator-5554"
  ;;
 stop) "$ADB" -s emulator-5554 emu kill ;;
 install) "$ADB" -s emulator-5554 install -r "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"; "$ADB" -s emulator-5554 shell am start -n ru.billyhargrove.pimobile/.MainActivity ;;
 screenshot) mkdir -p /Users/billy/temp/pi-mobile-emulator; "$ADB" -s emulator-5554 exec-out screencap -p > /Users/billy/temp/pi-mobile-emulator/android.png; echo /Users/billy/temp/pi-mobile-emulator/android.png ;;
 *) echo 'Usage: emulator.sh start|run|stop|install|screenshot' >&2; exit 2;;
esac
