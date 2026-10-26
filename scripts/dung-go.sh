#!/usr/bin/env bash
# Dựng app "Gõ tiếng Việt" (module go/) rồi chép vào assets của TiviKit (app cài nó lên tivi khi gửi chữ có dấu lần đầu).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :go:testReleaseUnitTest :go:assembleRelease --console=plain
cp go/build/outputs/apk/release/go-release.apk app/src/main/assets/go.apk
echo "Xong: go.apk $(du -h app/src/main/assets/go.apk | cut -f1)"
