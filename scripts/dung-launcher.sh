#!/usr/bin/env bash
# Dựng launcher của cleanyourtv (upstream/launcher) và chép vào assets của app, kèm dữ liệu mẫu tivi (sửa DuLieu.SO_MAU
# và số trong giay-phep.txt nếu số mẫu đổi; KeHoachTest báo khi lệch).
# Chạy lại khi cập nhật upstream: git -C upstream pull && scripts/dung-launcher.sh
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
[ -d upstream ] || git clone -q https://github.com/Gathin23/cleanyourtv upstream
# Bản sửa của CleanTV (dòng thời tiết…): áp patch nếu chưa áp.
if git -C upstream apply --check --binary ../patches/launcher-vgs.patch 2>/dev/null; then git -C upstream apply --binary ../patches/launcher-vgs.patch; fi
echo "sdk.dir=$ANDROID_HOME" > upstream/launcher/local.properties
mkdir -p app/src/main/assets
# Bản tên gói com.ts.launcher cho tivi Xiaomi bản Trung Quốc: hãng chỉ nhận Home có tên trong danh sách (upstream
# docs/devices/xiaomi-mitv-aetp0-dofus.md). Mã Java vẫn là com.gathin.tvhome, chỉ đổi applicationId.
(cd upstream/launcher && ./gradlew assembleRelease -q --console=plain -PlauncherApplicationId=com.ts.launcher)
cp upstream/launcher/app/build/outputs/apk/release/app-release.apk app/src/main/assets/tvhome-xiaomi.apk
(cd upstream/launcher && ./gradlew assembleRelease -q --console=plain)
cp upstream/launcher/app/build/outputs/apk/release/app-release.apk app/src/main/assets/tvhome.apk
python3 -I scripts/lam-du-lieu.py upstream app/src/main/assets/du-lieu.json
python3 -I scripts/lam-nang-cao.py upstream/themes/README.md app/src/main/assets/nang-cao.json
git -C upstream rev-parse HEAD > app/src/main/assets/upstream-commit.txt
echo "Xong: launcher $(du -h app/src/main/assets/tvhome.apk | cut -f1), upstream $(cat app/src/main/assets/upstream-commit.txt | cut -c1-7)"
