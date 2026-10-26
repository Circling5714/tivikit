#!/usr/bin/env bash
# Dựng Home sạch: launcher của CleanYourTV (upstream/launcher) ở đúng commit ghi trong app/src/main/assets/upstream-commit.txt,
# cộng bản vá patches/launcher-vgs.patch (phần CleanTV sửa, đã chuyển lên commit đó), ký bằng khoá TiviKit như app
# (ky.properties), chép tvhome.apk + tvhome-xiaomi.apk vào assets rồi làm lại danh sách Nâng cao (nang-cao.json).
# Đổi commit upstream: ghi hash mới vào upstream-commit.txt rồi chạy lại. Bản vá không áp được thì áp kiểu 3-way, gộp tay,
# rồi ghi lại bản vá: git -C upstream diff --cached --binary HEAD > patches/launcher-vgs.patch
# Dữ liệu mẫu tivi không làm ở đây: workflow Cập nhật mẫu tivi (scripts/cap_nhat_mau.py) lo hằng tuần.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
# R8 trên Windows ghi META-INF/services xuống dòng CRLF: ép LF cho bản dựng giống nhau trên mọi máy. Phải có nháy kép:
# java tách JDK_JAVA_OPTIONS theo khoảng trắng, thiếu nháy thì ký tự xuống dòng mất, line.separator rỗng làm Gradle treo.
export JDK_JAVA_OPTIONS=$'"-Dline.separator=\n"'
cam=$(tr -d '\r\n ' < app/src/main/assets/upstream-commit.txt)
# autocrlf=false: bản vá có file nhị phân (font), Git trên Windows đổi xuống dòng thì bản vá áp hỏng.
[ -d upstream ] || git clone -q -c core.autocrlf=false https://github.com/Gathin23/cleanyourtv upstream
git -C upstream cat-file -e "$cam^{commit}" 2>/dev/null || git -C upstream fetch -q origin
# Thư mục upstream chỉ để dựng: bỏ mọi thứ đã sửa trong đó rồi áp bản vá lên commit ghim.
git -C upstream checkout -q -f --detach "$cam"
git -C upstream clean -qfd
git -C upstream apply --index --binary ../patches/launcher-vgs.patch
echo "sdk.dir=$ANDROID_HOME" > upstream/launcher/local.properties

# Khoá ký: Home sạch ký cùng khoá với TiviKit (26/10/2026; trước đó dùng bản CleanTV dựng sẵn, ký khoá debug của máy
# VGS). Tivi đang có Home sạch khác khoá thì app tự gỡ rồi cài lại (ThaoTacTv.caiApkHome).
[ -f ky.properties ] || { echo "Thiếu ky.properties (khoá ký TiviKit)" >&2; exit 1; }
doc() { sed -n "s/^$1=//p" ky.properties | tr -d '\r' | sed 's/\\\(.\)/\1/g'; }
ky=(-Pandroid.injected.signing.store.file="$(doc storeFile)" -Pandroid.injected.signing.store.password="$(doc storePassword)"
    -Pandroid.injected.signing.key.alias="$(doc keyAlias)" -Pandroid.injected.signing.key.password="$(doc keyPassword)")

mkdir -p app/src/main/assets
# Bản tên gói com.ts.launcher cho tivi Xiaomi bản Trung Quốc: hãng chỉ nhận Home có tên trong danh sách (upstream
# docs/devices/xiaomi-mitv-aetp0-dofus.md). Mã Java vẫn là com.gathin.tvhome, chỉ đổi applicationId.
(cd upstream/launcher && ./gradlew assembleRelease -q --console=plain -PlauncherApplicationId=com.ts.launcher "${ky[@]}")
cp upstream/launcher/app/build/outputs/apk/release/app-release.apk app/src/main/assets/tvhome-xiaomi.apk
(cd upstream/launcher && ./gradlew assembleRelease -q --console=plain "${ky[@]}")
cp upstream/launcher/app/build/outputs/apk/release/app-release.apk app/src/main/assets/tvhome.apk
# Windows: python3 có thể chỉ là lối tắt mở Microsoft Store.
py=python3; "$py" -c '' 2>/dev/null || py=python
"$py" -I -X utf8 scripts/lam-nang-cao.py upstream/themes/README.md app/src/main/assets/nang-cao.json
echo "Xong: Home sạch $(du -h app/src/main/assets/tvhome.apk | cut -f1), upstream ${cam:0:7}"
