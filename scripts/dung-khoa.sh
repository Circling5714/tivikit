#!/usr/bin/env bash
# Dựng app "Khoá trẻ em" (module khoa/) rồi chép vào assets của CleanTV (app cài nó lên tivi khi phụ huynh bật).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :khoa:testReleaseUnitTest :khoa:assembleRelease --console=plain
cp khoa/build/outputs/apk/release/khoa-release.apk app/src/main/assets/khoa.apk
echo "Xong: khoa.apk $(du -h app/src/main/assets/khoa.apk | cut -f1)"
