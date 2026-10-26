#!/usr/bin/env bash
# Đọc danh sách gói người dùng gửi từ màn Xong (bảng danh_sach của D1 cleantv-dem), mới nhất trước.
# Dùng khi "check góp ý": scripts/doc-danh-sach.sh [số dòng, mặc định 20]
set -euo pipefail
cd "$(dirname "$0")/../dem"
set -a; . ../../vgs/vgs-contest/.env.cloudflare; set +a
npx wrangler d1 execute cleantv-dem --remote --json --command \
  "SELECT id, datetime(luc, '+7 hours') luc, hang, model, device, android, mau, danh_gia, ghi_chu, lien_he, json_array_length(goi) so_goi, goi, giu, chan_doan FROM danh_sach ORDER BY luc DESC LIMIT ${1:-20}" 2>/dev/null
