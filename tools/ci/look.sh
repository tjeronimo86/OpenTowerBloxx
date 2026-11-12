#!/bin/bash
# Kurze Pruefung der Optik: Baustelle, Turm, verschiedene Hoehen, Nacht, Party.
set -x
PKG=de.tonyapps.towerbloxx
OUT=out/verify
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
run() { adb shell am force-stop $PKG; sleep 2; adb shell am start -n $PKG/.DebugPlayActivity "$@"; }
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put global hide_error_dialogs 1
adb shell cmd locale set-app-locales $PKG --locales de
adb logcat -c
run --es mode quick --es theme day
sleep 20; shot 1-start
sleep 40; shot 2-tower
run --es mode quick --es theme day --ei floors 14
sleep 20; shot 3-floors14
run --es mode quick --es theme day --ei floors 33
sleep 20; shot 4-floors33
run --es mode quick --es theme day --ei floors 95
sleep 20; shot 5-floors95
run --es mode quick --es theme night --ei floors 24
sleep 20; shot 6-night
run --es mode party --es theme day
sleep 45; shot 7-party
timeout 60 adb logcat -d -s 'TowerGl:*' AndroidRuntime:E > "$OUT/log.txt"
(cd out && zip -qr verify.zip verify)
