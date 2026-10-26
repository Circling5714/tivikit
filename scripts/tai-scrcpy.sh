#!/usr/bin/env bash
# Tải máy chủ scrcpy (Genymobile, Apache 2.0) đúng bản TiviKit nói giao thức (GiaoThucScrcpy.BAN), kiểm SHA-256 rồi đặt
# vào app/src/main/assets/scrcpy-server.jar — dùng cho Xem màn hình tivi trên màn Điều khiển. Đổi bản: sửa BAN và SHA ở
# đây, GiaoThucScrcpy.BAN / CO_MAY_CHU, rồi đọc doc/develop.md của bản mới xem giao thức có đổi không.
set -euo pipefail
cd "$(dirname "$0")/.."
BAN=5.0.1
SHA=764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536
ra=app/src/main/assets/scrcpy-server.jar
tam=$(mktemp)
trap 'rm -f "$tam"' EXIT
curl -fsSL -o "$tam" "https://github.com/Genymobile/scrcpy/releases/download/v$BAN/scrcpy-server-v$BAN"
co=$(sha256sum "$tam" | cut -d' ' -f1)
[ "$co" = "$SHA" ] || { echo "SHA-256 sai: $co (cần $SHA)" >&2; exit 1; }
mv "$tam" "$ra"
trap - EXIT
echo "Xong: $ra ($(wc -c < "$ra") byte, scrcpy $BAN)"
