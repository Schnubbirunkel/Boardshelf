#!/usr/bin/env bash
# Takes the README screenshots on a running emulator (used by .github/workflows/screenshots.yml).
set -euo pipefail

PKG=io.github.schnubbirunkel.boardshelf.debug
ACT=io.github.schnubbirunkel.boardshelf.MainActivity
OUT=docs/screenshots
mkdir -p "$OUT"

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
  adb shell cmd locale set-app-locales "$PKG" --locales "$lang"
  shot "${lang}_1_shelf" 20 0 --ez seed_demo true --ez spine false
  shot "${lang}_2_spines" 6 0 --ez spine true
  shot "${lang}_3_box3d" 6 0 --ez spine false --es route "'game/1'"
  shot "${lang}_4_rating" 6 1 --es route "'game/7?tab=1'"
  shot "${lang}_5_savestates" 6 1 --es route "'game/1?tab=2'"
  shot "${lang}_6_manuals" 6 1 --es route "'game/1?tab=3'"
done
ls -la "$OUT"
