#!/usr/bin/env bash
# Dựng app "IPTV (TiviKit)" (module iptv/) rồi chép vào assets của TiviKit (iptv.apk), như dung-mapvoice.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :iptv:testReleaseUnitTest :iptv:assembleRelease --console=plain
cp iptv/build/outputs/apk/release/iptv-release.apk app/src/main/assets/iptv.apk
echo "Xong: iptv.apk $(du -h app/src/main/assets/iptv.apk | cut -f1)"
