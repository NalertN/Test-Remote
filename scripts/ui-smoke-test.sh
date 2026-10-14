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
# Swipe right opens the second page, swipe left goes back to the home page, swipe up opens
# the app list. Horizontal swipes start away from the screen edges, which belong to the
# system back gesture.
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

# Succeeds if some node's text contains $1. Counts instead of using grep -q, which exits at the
# first match and would fail the pipeline (SIGPIPE) under pipefail.
has_text() {
  local n
  n=$(dump_ui | grep -F 'text="' | grep -cF "$1") || true
  [ "${n:-0}" -gt 0 ]
}

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
  local p; p=$(find_text "$1") || true
  [ -n "$p" ] || { echo "Not found: $1"; return 1; }
  adb shell input tap $p
  sleep 1.5
}

long_press_text() {
  local p; p=$(find_text "$1") || true
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
# Widget binding normally needs a system dialog; pre-grant it.
adb shell appwidget grantbind --package "$PKG" --user 0 || true
adb shell cmd role add-role-holder android.app.role.HOME "$PKG" || true
adb shell cmd package set-home-activity "$PKG/.MainActivity" || true
adb logcat -c
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true

# Shown in an empty widget slot; long-pressing it opens the widget picker.
HINT="Long-press to add a widget"

home
wait_for_launcher
shot 01-home-empty
has_text "$HINT" || { echo "Home page shows no empty widget slot"; exit 1; }

swipe_up
# The very first gesture after a cold start can be lost while the app is still loading its
# gesture code (seen on the slower API 37 image); a second try must then work.
drawer_open || { echo "First swipe up did not open the app list, retrying once"; swipe_up; }
shot 02-app-list
drawer_open || { echo "Swipe up did not open the app list"; exit 1; }

adb shell input text "c"
shot 03-search

# Add a few apps to favorites 1 via the long-press menu. Typing all but the last letter keeps
# the search field text different from the list entry we look for.
list1=()
for app in Settings Clock Contacts Chrome Camera Phone Files Messages; do
  [ "${#list1[@]}" -ge 4 ] && break
  home
  swipe_up
  adb shell input text "${app%?}"
  sleep 1
  if long_press_text "$app"; then
    [ "${#list1[@]}" -eq 0 ] && shot 04-app-menu
    tap_text "Add to favorites 1" && list1+=("$app")
  fi
done
echo "Favorites 1: ${list1[*]:-}"

home
shot 05-home-favorites

# Favorites 2: an app that is not on the home page, so its presence tells the pages apart.
p2app=""
for app in Camera Phone Files Messages Settings Clock Contacts Chrome; do
  case " ${list1[*]:-} " in *" $app "*) continue ;; esac
  home
  swipe_up
  adb shell input text "${app%?}"
  sleep 1
  if long_press_text "$app"; then
    tap_text "Add to favorites 2" && { p2app=$app; break; }
  fi
done
echo "Favorites 2: ${p2app:-none}"

if [ -n "$p2app" ]; then
  home
  [ -z "$(find_text "$p2app")" ] || { echo "$p2app should not be on the home page"; exit 1; }
  swipe_right
  shot 06-second-page
  [ -n "$(find_text "$p2app")" ] || { echo "Swipe right did not open the second page"; exit 1; }
  swipe_left
  [ -z "$(find_text "$p2app")" ] || { echo "Swipe left did not return to the home page"; exit 1; }
fi

# Long-press on empty space above the widget slot opens settings.
open_settings() { adb shell input swipe $((W / 2)) $((H * 8 / 100)) $((W / 2)) $((H * 8 / 100)) 1200; sleep 1.5; }
home
open_settings
shot 07-settings

# Back returns home; Back on home does nothing.
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
shot 08-home-after-back

# Back closes the app list (the first Back may only hide the keyboard).
swipe_up
drawer_open || { echo "Swipe up did not open the app list"; exit 1; }
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1
drawer_open && { echo "Back did not close the app list"; exit 1; }

# Enter launches the top search result.
swipe_up
adb shell input text "Sett"
adb shell input keyevent KEYCODE_ENTER
shot 09-enter-launches-top-result
adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | tee "$OUT/top-activity.txt" || true
home
shot 10-home-again

# Opens the widget picker from the empty slot of the page on screen and picks the first
# widget; prints nothing and returns 1 if the picker lists none.
add_first_widget() {
  long_press_text "$HINT" || { echo "Cannot long-press the empty widget slot"; exit 1; }
  shot "$1"
  # First entry below the picker title.
  local first
  first=$(dump_ui | grep -F 'text="' | sed -nE 's/.*text="([^"]+)".*/\1/p' |
    grep -A1 -x 'Choose widget' | tail -1) || true
  if [ -z "$first" ] || [ "$first" = "Choose widget" ] || [ "$first" = "No widgets installed" ]; then
    return 1
  fi
  echo "Adding widget: $first"
  tap_text "$first"
  sleep 3
  # A widget with a configure activity opens it; going home cancels that, which must not crash.
  home
}

widgets_added=0
home
if add_first_widget 11-widget-picker; then
  widgets_added=1
  shot 12-home-widget
  [ -n "$(find_attr content-desc "Home widget")" ] ||
    echo "Widget not on the home page (it may need configuration)"
fi

if [ "$widgets_added" -eq 1 ] && [ -n "$(find_attr content-desc "Home widget")" ]; then
  # A swipe that starts on the widget still switches pages; the second page has its own slot.
  swipe_right
  [ -z "$(find_attr content-desc "Home widget")" ] || { echo "Swipe right on the widget did not open the second page"; exit 1; }
  has_text "$HINT" || { echo "Second page shows no empty widget slot"; exit 1; }
  if add_first_widget 13-second-page-picker; then
    swipe_right
    shot 14-second-page-widget
    [ -n "$(find_attr content-desc "Second page widget")" ] ||
      echo "Widget not on the second page (it may need configuration)"
    swipe_left
    [ -z "$(find_attr content-desc "Second page widget")" ] || { echo "Swipe left on the widget did not return home"; exit 1; }
  fi

  # Long-press on the home widget opens its menu; Remove brings the empty slot back.
  home
  p=$(find_attr content-desc "Home widget") || true
  if [ -n "$p" ]; then
    set -- $p
    adb shell input swipe "$1" "$2" "$1" "$2" 1200
    sleep 1.5
    shot 15-widget-menu
    tap_text "Remove widget"
    shot 16-widget-removed
    [ -z "$(find_attr content-desc "Home widget")" ] || { echo "Widget was not removed"; exit 1; }
    has_text "$HINT" || { echo "Empty widget slot did not come back"; exit 1; }
  fi
else
  echo "No widget to test with"
fi

save_logs
if grep -q "$PKG" "$OUT/crash-buffer.txt" ||
  adb shell run-as "$PKG" ls files/last_crash.txt >/dev/null 2>&1; then
  echo "Launcher crashed:"
  cat "$OUT/crash-buffer.txt"
  exit 1
fi
echo "No crashes."
