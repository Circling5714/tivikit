#!/usr/bin/env bash
# Dựng app "Cast SmartTube" (module cast/) rồi chép vào assets của TiviKit (app cài nó lên tivi ở màn Cast SmartTube).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :cast:testReleaseUnitTest :cast:assembleRelease --console=plain
cp cast/build/outputs/apk/release/cast-release.apk app/src/main/assets/cast.apk
echo "Xong: cast.apk $(du -h app/src/main/assets/cast.apk | cut -f1)"
