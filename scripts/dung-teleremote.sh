#!/usr/bin/env bash
# Dựng app "Tele Remote" (module teleremote/) rồi chép vào assets của TiviKit (app cài nó lên tivi ở màn Tele Remote).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :teleremote:testReleaseUnitTest :teleremote:assembleRelease --console=plain
cp teleremote/build/outputs/apk/release/teleremote-release.apk app/src/main/assets/teleremote.apk
echo "Xong: teleremote.apk $(du -h app/src/main/assets/teleremote.apk | cut -f1)"
