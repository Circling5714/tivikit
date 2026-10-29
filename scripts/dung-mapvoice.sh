#!/usr/bin/env bash
# Dựng app "Map Voice" (module mapvoice/) rồi chép vào assets của TiviKit (app cài nó lên tivi ở màn Map Voice).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :mapvoice:testReleaseUnitTest :mapvoice:assembleRelease --console=plain
cp mapvoice/build/outputs/apk/release/mapvoice-release.apk app/src/main/assets/mapvoice.apk
echo "Xong: mapvoice.apk $(du -h app/src/main/assets/mapvoice.apk | cut -f1)"
