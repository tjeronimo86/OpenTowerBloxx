#!/bin/bash
# Laeuft im Emulator-Job: startet alle Modi im Selbstspiel und nimmt Screenshots auf.
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

# Erster Start: Was ist neu, dann Menue
adb shell am start -n $PKG/.MainActivity
sleep 25
shot 01-news
adb shell input keyevent KEYCODE_BACK
sleep 4
shot 02-menu

run --es mode menu --es theme night; sleep 10; shot 03-menu-night

run --es mode quick --es theme day
sleep 6; shot 10-quick-start
sleep 14; shot 11-quick-20s
sleep 25; shot 12-quick-45s
sleep 30; shot 13-quick-75s

run --es mode quick --es theme night
sleep 30; shot 20-quick-night

run --es mode time --es theme day
sleep 25; shot 30-time

run --es mode challenge --ei level 6
sleep 25; shot 40-challenge

run --es mode city --ei kind 1
sleep 40; shot 50-city-build
sleep 30; shot 51-city-build-end

run --es mode party
sleep 8; shot 60-party-start
sleep 25; shot 61-party

run --es mode citymap; sleep 8; shot 70-citymap
run --es mode challenges; sleep 8; shot 71-challenges
run --es mode hall; sleep 8; shot 72-hall
run --es mode settings; sleep 8; shot 73-settings

adb shell cmd locale set-app-locales $PKG --locales en
run --es mode menu --es theme day; sleep 10; shot 80-menu-en
run --es mode quick; sleep 20; shot 81-quick-en

timeout 60 adb logcat -d -s 'TowerGl:*' AndroidRuntime:E System.err:W > "$OUT/log.txt"
(cd out && zip -qr verify.zip verify)
