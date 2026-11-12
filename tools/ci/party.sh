#!/bin/bash
# Kurze Pruefung: Party im Querformat und ein schnelles Spiel.
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
run --es mode party --es theme day
sleep 25; shot party-1
sleep 30; shot party-2
run --es mode quick --es theme day
sleep 45; shot quick-45
timeout 60 adb logcat -d -s 'TowerGl:*' AndroidRuntime:E > "$OUT/log.txt"
(cd out && zip -qr verify.zip verify)
