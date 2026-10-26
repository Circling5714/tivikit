#!/usr/bin/env bash
# Dựng app "Hẹn giờ" (module hengio/) rồi chép vào assets của iCM_CleanTV (app cài nó lên tivi khi người dùng lưu lịch).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :hengio:testReleaseUnitTest :hengio:assembleRelease --console=plain
cp hengio/build/outputs/apk/release/hengio-release.apk app/src/main/assets/hengio.apk
echo "Xong: hengio.apk $(du -h app/src/main/assets/hengio.apk | cut -f1)"
