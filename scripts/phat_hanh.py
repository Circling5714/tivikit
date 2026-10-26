#!/usr/bin/env python3
"""Phát hành TiviKit (26/10/2026) — workflow .github/workflows/phat-hanh.yml gọi; chạy trên máy được để thử.

  python3 scripts/phat_hanh.py ban-moi va|tinh-nang|lon
      Tính số bản mới, ghi vào version.properties, in số bản ra.
  python3 scripts/phat_hanh.py dong-goi <bản> <file apk> [ghi chú]
      Ghi vào dist/: TiviKit-<bản>.apk, TiviKit-<bản>.apk.sha256, cap-nhat.json (app đọc để tự cập nhật, CapNhat.kt),
      nhat-ky.md (nội dung GitHub Release).

Số bản lấy từ tag v<lớn>.<tính năng>.<vá> gần nhất: va tăng số cuối, tinh-nang tăng số giữa, lon tăng số đầu. Chưa có tag
thì dùng nguyên version.properties; ai tự sửa version.properties cao hơn tag thì dùng số đó. versionCode = lớn×10000 +
tính năng×100 + vá (giống app/build.gradle.kts). Nhật ký: tiêu đề các commit từ tag trước, bỏ commit "Phát hành …";
commit bắt đầu bằng "Sửa" vào mục Sửa lỗi.
"""
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

GOC = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KHO = os.environ.get("GITHUB_REPOSITORY") or "Circling5714/tivikit"
FILE_BAN = os.path.join(GOC, "version.properties")
RA = os.path.join(GOC, "dist")
# Chứng chỉ ký của TiviKit (README, Kiểm tra bản cài); workflow cũng chặn APK ký sai khoá.
DAU_CHUNG_CHI = "ec4c282ecefa87e7e959d44de052ccb047a8164c383ff39341d910d85af6f653"
# Nhật ký trong app (banner, thông báo) chỉ chừng này dòng; đầy đủ ở GitHub Release.
DONG_TRONG_APP = 12


def git(*ts):
    return subprocess.run(["git", *ts], cwd=GOC, capture_output=True, text=True, encoding="utf-8")


def tach(ban):
    """'1.2.3' → (1, 2, 3); tính năng và vá phải dưới 100 để versionCode luôn tăng."""
    m = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", ban.strip())
    if not m:
        sys.exit(f"Số bản không hợp lệ: {ban!r}")
    lon, tinh_nang, va = map(int, m.groups())
    if tinh_nang > 99 or va > 99:
        sys.exit(f"Số tính năng / vá phải dưới 100: {ban} (dùng lon để lên số lớn)")
    return lon, tinh_nang, va


def ma_ban(ban):
    lon, tinh_nang, va = tach(ban)
    return lon * 10000 + tinh_nang * 100 + va


def tag_truoc():
    r = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*.[0-9]*.[0-9]*")
    return r.stdout.strip() if r.returncode == 0 else None


def doc_file_ban():
    with open(FILE_BAN, encoding="utf-8") as f:
        for d in f:
            if d.startswith("VERSION_NAME="):
                return d.split("=", 1)[1].strip()
    sys.exit("version.properties thiếu dòng VERSION_NAME=")


def ghi_file_ban(ban):
    with open(FILE_BAN, encoding="utf-8") as f:
        dong = f.read().splitlines()
    dong = [f"VERSION_NAME={ban}" if d.startswith("VERSION_NAME=") else d for d in dong]
    with open(FILE_BAN, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(dong) + "\n")


def ban_moi(loai):
    if loai not in ("va", "tinh-nang", "lon"):
        sys.exit(f"Loại bản không hợp lệ: {loai!r} (va, tinh-nang, lon)")
    tag, trong_file = tag_truoc(), doc_file_ban()
    if tag is None:
        ban = trong_file
    else:
        lon, tinh_nang, va = tach(tag[1:])
        if tach(trong_file) > (lon, tinh_nang, va):
            ban = trong_file
        elif loai == "va":
            ban = f"{lon}.{tinh_nang}.{va + 1}"
        elif loai == "tinh-nang":
            ban = f"{lon}.{tinh_nang + 1}.0"
        else:
            ban = f"{lon + 1}.0.0"
    tach(ban)
    return ban


def nhat_ky():
    """(mới, sửa): tiêu đề commit từ tag trước tới HEAD, cũ trước mới sau."""
    tag = tag_truoc()
    r = git("log", "--no-merges", "--reverse", "--pretty=format:%s", f"{tag}..HEAD" if tag else "HEAD")
    tieu_de = [s.strip() for s in r.stdout.splitlines() if s.strip() and not s.startswith("Phát hành ")]
    sua = [s for s in tieu_de if re.match(r"(?i)(sửa|fix)\b", s)]
    return [s for s in tieu_de if s not in sua], sua


def dong_goi(ban, apk, ghi_chu=""):
    tach(ban)
    os.makedirs(RA, exist_ok=True)
    ten = f"TiviKit-{ban}.apk"
    shutil.copyfile(apk, os.path.join(RA, ten))
    with open(apk, "rb") as f:
        du_lieu = f.read()
    sha = hashlib.sha256(du_lieu).hexdigest()
    with open(os.path.join(RA, ten + ".sha256"), "w", encoding="utf-8", newline="\n") as f:
        f.write(f"{sha}  {ten}\n")

    moi, sua = nhat_ky()
    ghi_chu = ghi_chu.strip()
    dong = ([ghi_chu] if ghi_chu else []) + [f"- {s}" for s in moi + sua]
    if len(dong) > DONG_TRONG_APP:
        dong = dong[:DONG_TRONG_APP - 1] + [f"… xem đủ ở github.com/{KHO}/releases"]
    ngay = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).date().isoformat()
    cap_nhat = {"app": {
        "versionCode": ma_ban(ban), "versionName": ban,
        "apk": f"https://github.com/{KHO}/releases/download/v{ban}/{ten}",
        "co": len(du_lieu), "sha256": sha, "ghiChu": "\n".join(dong), "ghiChuEn": "", "ngay": ngay,
    }}
    with open(os.path.join(RA, "cap-nhat.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(cap_nhat, f, ensure_ascii=False, indent=1)
        f.write("\n")

    md = ([ghi_chu, ""] if ghi_chu else [])
    if moi:
        md += ["## Có gì mới", *[f"- {s}" for s in moi], ""]
    if sua:
        md += ["## Sửa lỗi", *[f"- {s}" for s in sua], ""]
    md += ["## Cài đặt",
           f"- Cài lần đầu: tải `{ten}` bên dưới về điện thoại Android (hoặc tivi) rồi mở để cài.",
           "- Đã có TiviKit 1.1.0 trở lên: app tự cập nhật (Android 12+ không cần bấm, máy cũ hơn chạm thông báo một lần).",
           f"- SHA-256 của file: `{sha}`",
           f"- Chứng chỉ ký (SHA-256): `{DAU_CHUNG_CHI}`"]
    with open(os.path.join(RA, "nhat-ky.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(md) + "\n")
    print(f"{ten} {len(du_lieu)} byte, sha256 {sha}, {len(moi)} mục mới, {len(sua)} mục sửa lỗi")


if __name__ == "__main__":
    for luong in (sys.stdout, sys.stderr):
        if hasattr(luong, "reconfigure"):
            luong.reconfigure(encoding="utf-8")
    lenh = sys.argv[1:] or ["?"]
    if lenh[0] == "ban-moi" and len(lenh) == 2:
        b = ban_moi(lenh[1])
        ghi_file_ban(b)
        print(b)
    elif lenh[0] == "dong-goi" and len(lenh) in (3, 4):
        dong_goi(lenh[1], lenh[2], lenh[3] if len(lenh) == 4 else "")
    else:
        sys.exit(__doc__)
