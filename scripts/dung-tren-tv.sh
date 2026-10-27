#!/usr/bin/env bash
# Dựng các lớp Java chạy trên tivi bằng app_process (module trentv/: tên + ảnh app, bài đang phát) rồi chép vào assets của
# TiviKit (tren-tv.jar). Không cài lên tivi: app đặt file vào /data/local/tmp khi cần (adb/TrenTv.kt).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :trentv:testReleaseUnitTest :trentv:assembleRelease --console=plain
cp trentv/build/outputs/apk/release/trentv-release.apk app/src/main/assets/tren-tv.jar
echo "Xong: tren-tv.jar $(du -h app/src/main/assets/tren-tv.jar | cut -f1)"
