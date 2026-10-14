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
# Starts away from the screen edges, which belong to the system back gesture.
swipe_right() { adb shell input swipe $((W / 5)) $((H * 6 / 10)) $((W * 4 / 5)) $((H * 6 / 10)) 200; sleep 1.5; }
swipe_left() { adb shell input swipe $((W * 4 / 5)) $((H * 6 / 10)) $((W / 5)) $((H * 6 / 10)) 200; sleep 1.5; }
swipe_up() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 200; sleep 1.5; }

# The app list is open when its search field is on screen.
drawer_open() { [ -n "$(find_text "Search")" ]; }

# Prints the UI hierarchy, one node per line.
dump_ui() {
  local i
  for i in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && break
    sleep 1
  done
  adb exec-out cat /sdcard/ui.xml | tr '>' '\n'
}

# Prints the centre "x y" of the last node with attribute $1 exactly "$2".
find_attr() {
  dump_ui | grep -F "$1=\"$2\"" | tail -1 |
    sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' |
    awk '{ print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# Prints the centre "x y" of the last node whose text is exactly $1.
find_text() { find_attr text "$1"; }

# Succeeds if some node's text contains $1.
has_text() { dump_ui | grep -F "text=\"" | grep -qF "$1"; }

# Waits until the launcher has drawn its UI and has input focus; the first start can take a
# while.
wait_for_launcher() {
  local i
  for i in $(seq 1 30); do
    if adb shell dumpsys window | grep -E "mCurrentFocus=.*$PKG" >/dev/null &&
      adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 &&
      adb exec-out cat /sdcard/ui.xml | grep "package=\"$PKG\"" >/dev/null; then
      sleep 1
      return 0
    fi
    sleep 1
  done
  echo "Launcher did not appear"
  return 1
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

# Keep logs and the last UI hierarchy for debugging, whether the test passes or not.
save_logs() {
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  adb logcat -d -b crash > "$OUT/crash-buffer.txt" 2>/dev/null || true
  adb exec-out cat /sdcard/ui.xml > "$OUT/last-ui.xml" 2>/dev/null || true
}
trap save_logs EXIT

adb install -r "$APK"
# Calendar access and widget binding normally need a dialog each; pre-grant both.
adb shell pm grant "$PKG" android.permission.READ_CALENDAR
adb shell appwidget grantbind --package "$PKG" --user 0 || true

# A local calendar with a timed and an all-day event for the agenda.
events_added=0
adb shell content insert \
  --uri "'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=smoke&account_type=LOCAL'" \
  --bind account_name:s:smoke --bind account_type:s:LOCAL --bind name:s:Smoke \
  --bind calendar_displayName:s:Smoke --bind calendar_access_level:i:700 --bind ownerAccount:s:smoke \
  --bind visible:i:1 --bind sync_events:i:1 --bind calendar_timezone:s:UTC || true
CAL_ID=$(adb shell content query --uri content://com.android.calendar/calendars --projection _id:name |
  grep -F 'name=Smoke' | sed -nE 's/.*_id=([0-9]+).*/\1/p' | head -1)
if [ -n "$CAL_ID" ]; then
  NOW_MS=$(( $(date +%s) * 1000 ))
  HOUR_MS=3600000
  TOMORROW_UTC_MS=$(( ($(date +%s) / 86400 + 1) * 86400 * 1000 ))
  adb shell content insert --uri content://com.android.calendar/events \
    --bind calendar_id:i:"$CAL_ID" --bind title:s:Dentist --bind eventTimezone:s:UTC \
    --bind dtstart:l:$((NOW_MS + 2 * HOUR_MS)) --bind dtend:l:$((NOW_MS + 3 * HOUR_MS)) &&
  adb shell content insert --uri content://com.android.calendar/events \
    --bind calendar_id:i:"$CAL_ID" --bind title:s:Holiday --bind eventTimezone:s:UTC --bind allDay:i:1 \
    --bind dtstart:l:"$TOMORROW_UTC_MS" --bind dtend:l:$((TOMORROW_UTC_MS + 3 * 86400000)) &&
  events_added=1
fi
echo "Calendar events added: $events_added"
adb shell cmd role add-role-holder android.app.role.HOME "$PKG" || true
adb shell cmd package set-home-activity "$PKG/.MainActivity" || true
adb logcat -c
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
home
wait_for_launcher
shot 01-home-empty
if [ "$events_added" -eq 1 ]; then
  has_text Dentist || { echo "Agenda does not show the calendar event"; exit 1; }
fi

swipe_right
shot 02-app-list
drawer_open || { echo "Swipe right did not open the app list"; exit 1; }

adb shell input text "c"
shot 03-search

# Add a few favorites via long-press menu. Typing all but the last letter keeps the
# search field text different from the list entry we look for.
added=0
for app in Settings Clock Contacts Chrome Camera Phone Files Messages; do
  [ "$added" -ge 4 ] && break
  home
  swipe_right
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

# Long-press on empty space above the date opens settings (below the favorites may be
# scrolled content once the agenda and widget are shown).
open_settings() { adb shell input swipe $((W / 2)) $((H * 8 / 100)) $((W / 2)) $((H * 8 / 100)) 1200; sleep 1.5; }
open_settings
shot 06-settings

# Back returns home; Back on home does nothing.
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
shot 07-home-after-back

# Swipe left closes the app list; swiping up on home does nothing.
swipe_right
swipe_left
drawer_open && { echo "Swipe left did not close the app list"; exit 1; }
swipe_up
drawer_open && { echo "Swipe up should not open the app list"; exit 1; }
home

# Enter launches the top search result.
swipe_right
adb shell input text "Sett"
adb shell input keyevent KEYCODE_ENTER
shot 08-enter-launches-top-result
adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | tee "$OUT/top-activity.txt" || true
home
shot 09-home-again

# Widget slot: add a widget from settings, then long-press it to get its menu.
home
open_settings
tap_text "Add"
shot 11-widget-picker
# First entry below the picker title.
first_widget=$(dump_ui | grep -F 'text="' | sed -nE 's/.*text="([^"]+)".*/\1/p' |
  grep -A1 -x 'Choose widget' | tail -1)
if [ -n "$first_widget" ] && [ "$first_widget" != "Choose widget" ] && [ "$first_widget" != "No widgets installed" ]; then
  echo "Adding widget: $first_widget"
  tap_text "$first_widget"
  sleep 3
  # Leave a configure activity, if the widget opened one, by accepting its defaults is not
  # generic; going home instead cancels it, which must not crash either.
  home
  shot 12-home-widget
  p=$(find_attr content-desc "Home widget")
  if [ -n "$p" ]; then
    set -- $p
    adb shell input swipe "$1" "$2" "$1" "$2" 1200
    sleep 1.5
    shot 13-widget-menu
    tap_text "Remove widget"
    shot 14-widget-removed
    [ -z "$(find_attr content-desc "Home widget")" ] || { echo "Widget was not removed"; exit 1; }
  else
    echo "Widget not on home (needs configuration?)"
  fi
else
  echo "No widgets to add"
  home
fi

# Agenda on its own page: swipe left from home, swipe right back.
if [ "$events_added" -eq 1 ]; then
  home
  open_settings
  tap_text "Change"
  home
  swipe_left
  shot 15-agenda-page
  [ -n "$(find_text "Next 7 days")" ] || { echo "Swipe left did not open the agenda"; exit 1; }
  swipe_right
  [ -z "$(find_text "Next 7 days")" ] || { echo "Swipe right did not close the agenda"; exit 1; }
  shot 16-home-without-agenda
fi

save_logs
if grep -q "$PKG" "$OUT/crash-buffer.txt" ||
  adb shell run-as "$PKG" ls files/last_crash.txt >/dev/null 2>&1; then
  echo "Launcher crashed:"
  cat "$OUT/crash-buffer.txt"
  exit 1
fi
echo "No crashes."
