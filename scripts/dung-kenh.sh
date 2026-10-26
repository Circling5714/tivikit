#!/usr/bin/env bash
# Dựng app "Kênh nhanh" (module kenh/, mỗi ô kênh một app: o1 … o6) rồi chép vào assets của iCM_CleanTV
# (kenh.apk = ô 1 tên gói cũ, kenh2.apk … kenh6.apk); app cài ô nào khi người dùng lưu ô đó.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew -q :kenh:testO1ReleaseUnitTest :kenh:assembleRelease --console=plain
cp kenh/build/outputs/apk/o1/release/kenh-o1-release.apk app/src/main/assets/kenh.apk
for n in 2 3 4 5 6; do cp "kenh/build/outputs/apk/o$n/release/kenh-o$n-release.apk" "app/src/main/assets/kenh$n.apk"; done
echo "Xong: kenh.apk, kenh2.apk … kenh6.apk ($(du -ch app/src/main/assets/kenh*.apk | tail -n 1 | cut -f1))"
