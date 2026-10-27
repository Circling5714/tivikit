#!/usr/bin/env bash
# Dựng TiviKit Launcher (module launcher/) rồi chép vào assets của TiviKit (app cài nó lên tivi ở màn Quản lý launcher).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :launcher:testReleaseUnitTest :launcher:assembleRelease --console=plain
cp launcher/build/outputs/apk/release/launcher-release.apk app/src/main/assets/tivikit-launcher.apk
echo "Xong: tivikit-launcher.apk $(du -h app/src/main/assets/tivikit-launcher.apk | cut -f1)"
