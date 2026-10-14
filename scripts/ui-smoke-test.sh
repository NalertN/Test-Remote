#!/usr/bin/env bash
# Emulator smoke test: installs the APK as home app, walks through the main screens and
# saves screenshots to screenshots/. Fails if the launcher crashes along the way.
set -euo pipefail

PKG=de.lennart.minilauncher
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
OUT=screenshots
mkdir -p "$OUT"

read -r W H < <(adb shell wm size | tail -1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
echo "Screen: ${W}x${H}"

shot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "Saved $1.png"
}

home() { adb shell input keyevent KEYCODE_HOME; sleep 1.5; }
swipe_up() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 200; sleep 1.5; }

# Prints the centre "x y" of the last node whose text is exactly $1.
find_text() {
  local i
  for i in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && break
    sleep 1
  done
  adb exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep -F "text=\"$1\"" | tail -1 |
    sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' |
    awk '{ print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

tap_text() {
  local p; p=$(find_text "$1")
  [ -n "$p" ] || { echo "Not found: $1"; return 1; }
  adb shell input tap $p
  sleep 1.5
}

long_press_text() {
  local p; p=$(find_text "$1")
  [ -n "$p" ] || { echo "Not found: $1"; return 1; }
  set -- $p
  adb shell input swipe "$1" "$2" "$1" "$2" 1200
  sleep 1.5
}

adb install -r "$APK"
adb shell cmd role add-role-holder android.app.role.HOME "$PKG" || true
adb shell cmd package set-home-activity "$PKG/.MainActivity" || true
adb logcat -c
# Fixed, discharging battery so the ring shows a stable level.
adb shell dumpsys battery unplug
adb shell dumpsys battery set status 3
adb shell dumpsys battery set level 78
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
home
sleep 3
shot 01-home-empty

swipe_up
shot 02-app-list

adb shell input text "c"
shot 03-search

# Add a few favorites via long-press menu. Typing all but the last letter keeps the
# search field text different from the list entry we look for.
added=0
for app in Settings Clock Contacts Chrome Camera Phone Files Messages; do
  [ "$added" -ge 4 ] && break
  home
  swipe_up
  adb shell input text "${app%?}"
  sleep 1
  if long_press_text "$app"; then
    [ "$added" -eq 0 ] && shot 04-app-menu
    tap_text "Add to favorites" && added=$((added + 1))
  fi
done
echo "Favorites added: $added"

home
shot 05-home-favorites

# Long-press empty space below the favorites opens settings.
adb shell input swipe $((W / 2)) $((H * 92 / 100)) $((W / 2)) $((H * 92 / 100)) 1200
shot 06-settings

# Back returns home; Back on home does nothing.
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
shot 07-home-after-back

# Enter launches the top search result.
swipe_up
adb shell input text "Sett"
adb shell input keyevent KEYCODE_ENTER
shot 08-enter-launches-top-result
adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | tee "$OUT/top-activity.txt" || true
home
shot 09-home-again

# Low battery turns the ring's arc red.
adb shell dumpsys battery set level 12
shot 10-home-low-battery
adb shell dumpsys battery reset

adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/crash-buffer.txt" || true
if grep -q "$PKG" "$OUT/crash-buffer.txt" ||
  adb shell run-as "$PKG" ls files/last_crash.txt >/dev/null 2>&1; then
  echo "Launcher crashed:"
  cat "$OUT/crash-buffer.txt"
  exit 1
fi
echo "No crashes."
