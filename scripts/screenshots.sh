#!/usr/bin/env bash
# Takes the README screenshots on a running emulator (used by .github/workflows/screenshots.yml).
set -euo pipefail

PKG=io.github.schnubbirunkel.boardshelf.debug
ACT=io.github.schnubbirunkel.boardshelf.MainActivity
OUT=docs/screenshots
mkdir -p "$OUT"

# migration test: previous version with demo data, then update to the new build
if [ -f old.apk ]; then
  adb install -r old.apk
  adb shell am start -W -n "$PKG/$ACT" --ez seed_demo true > /dev/null
  sleep 25
  adb shell am force-stop "$PKG"
  adb logcat -c
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk

# clean status bar
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter || true
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 || true
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false || true
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 || true
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false || true

shot() {
  local name=$1 wait=$2 scroll=$3; shift 3
  adb shell am force-stop "$PKG"
  adb shell "am start -W -n $PKG/$ACT $*" > /dev/null
  sleep "$wait"
  if [ "$scroll" = "1" ]; then
    adb shell input swipe 540 2000 540 900 400
    sleep 2
  fi
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "captured $name"
}

for lang in de en; do
  adb shell cmd locale set-app-locales "$PKG" --locales "$lang" || true
  shot "${lang}_0_warmup" 8 0 --es lang "$lang"
  shot "${lang}_1_shelf" 20 0 --ez seed_demo true --ez spine false
  shot "${lang}_2_shelf_lower" 6 1
  shot "${lang}_3_box3d" 6 0 --ez spine false --es route "'game/1'"
  shot "${lang}_4_rating" 6 1 --es route "'game/7?tab=1'"
  shot "${lang}_5_savestates" 6 1 --es route "'game/1?tab=2'"
  shot "${lang}_6_manuals" 6 1 --es route "'game/1?tab=3'"
  shot "${lang}_7_crop" 6 0 --el crop_game 2
  shot "${lang}_8_barcode" 14 0 --es barcode 4002051682682
  shot "${lang}_9_settings" 6 1 --es route settings
  if [ "$lang" = "de" ]; then
    shot "test_import_catan" 30 0 --es barcode_import 4002051682682
    shot "test_import_catan_info" 6 1 --es route "'game/16'"
    adb shell am force-stop "$PKG"
    adb shell "am start -W -n $PKG/$ACT --ez csv_test true" > /dev/null
    sleep 75
    adb shell input swipe 540 1900 540 300 300; sleep 1; adb shell input swipe 540 1900 540 300 300; sleep 2
    adb exec-out screencap -p > "$OUT/test_csv_shelf.png"
    shot "test_csv_game" 6 1 --es route "'game/18'"
  fi
done
# record the take-out animation (animations enabled for this part)
adb shell cmd locale set-app-locales "$PKG" --locales de || true
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb shell am force-stop "$PKG"
adb shell "am start -W -n $PKG/$ACT --es lang de" > /dev/null
sleep 6
adb shell "screenrecord --time-limit 6 --bit-rate 6000000 /sdcard/open.mp4 & sleep 1.5; input tap 100 560; wait" || true
sleep 3
adb pull /sdcard/open.mp4 "$OUT/open.mp4"
# peek gesture: long-press and slide over the boxes
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
adb shell am force-stop "$PKG"
adb shell "am start -W -n $PKG/$ACT --es lang de" > /dev/null
sleep 6
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
sleep 1
adb shell "screenrecord --time-limit 7 --bit-rate 6000000 /sdcard/peek.mp4 & sleep 1; input motionevent DOWN 95 840; sleep 1.2; for x in 120 145 170 200 230 260 290 320 350 380; do input motionevent MOVE \$x 840; sleep 0.35; done; sleep 1; input motionevent UP 380 840; wait" || true
sleep 3
adb pull /sdcard/peek.mp4 "$OUT/peek.mp4"
rm -f "$OUT"/*_0_warmup.png
adb logcat -d -v brief "Boardshelf:V" "AndroidRuntime:E" "*:S" > "$OUT/logcat.txt" || true
ls -la "$OUT"
