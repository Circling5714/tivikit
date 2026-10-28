#!/usr/bin/env python3
"""Phát hành TiviKit (26/10/2026) — workflow .github/workflows/phat-hanh.yml gọi; chạy trên máy được để thử.

  python3 scripts/phat_hanh.py ban-moi va|tinh-nang|lon
      Tính số bản mới, ghi vào version.properties, in số bản ra.
  python3 scripts/phat_hanh.py dong-goi <bản> <file apk> [ghi chú] [ghi chú tiếng Anh]
      Ghi vào dist/: TiviKit-<bản>.apk, TiviKit-<bản>.apk.sha256, cap-nhat.json (app đọc để tự cập nhật, CapNhat.kt;
      ghiChuEn cho app đang dùng tiếng Anh), nhat-ky.md (nội dung GitHub Release, hai thứ tiếng).
  python3 scripts/phat_hanh.py chi-du-lieu
      Thoát 0 nếu từ tag trước, thứ vào APK chỉ đổi ở dữ liệu mẫu tivi (du-lieu.json và dòng số mẫu): bản tự phát hành
      không cần duyệt được (môi trường phat-hanh-tu-dong). Có code đổi thì thoát 1, kèm danh sách file.

Số bản lấy từ tag v<lớn>.<tính năng>.<vá> gần nhất: va tăng số cuối, tinh-nang tăng số giữa, lon tăng số đầu. Chưa có tag
thì dùng nguyên version.properties; ai tự sửa version.properties cao hơn tag thì dùng số đó. versionCode = lớn×10000 +
tính năng×100 + vá (giống app/build.gradle.kts). Nhật ký: tiêu đề các commit từ tag trước, bỏ commit "Phát hành …" và
commit việc nội bộ (tiêu đề bắt đầu bằng "Nội bộ:", "Tài liệu:", "README:", "CI:"); commit bắt đầu bằng "Sửa" vào mục
Sửa lỗi. Bản tiếng Anh của mỗi mục: dòng "EN: …" trong thân commit (không có thì dùng tiêu đề tiếng Việt).
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
# Tự phát hành không ai duyệt (26/10/2026) chỉ khi thứ vào APK không đổi ngoài dữ liệu mẫu tivi. Mấy file này không vào APK:
# tài liệu (README, docs/), script phát hành / cập nhật mẫu, test của các module. Workflow thì tính (28/10/2026): nó quyết định
# APK dựng và ký ra sao, đổi thì phải có người duyệt.
NGOAI_APK = re.compile(r"^(\.github/FUNDING\.yml$|docs/|[^/]+\.md$|NOTICE$|LICENSE$|scripts/(phat_hanh|cap_nhat_mau)\.py$|[^/]+/src/test/)")
DU_LIEU = "app/src/main/assets/du-lieu.json"
# Hai file trong APK chỉ được đổi đúng dòng ghi số mẫu (scripts/cap_nhat_mau.py sửa).
DONG_SO_MAU = {
    "app/src/main/java/vn/tivikit/dulieu/DuLieu.kt": re.compile(r"\s*const val SO_MAU = \d+$"),
    "app/src/main/assets/giay-phep.txt": re.compile(r"1\. CleanYourTV — danh sách \d+ mẫu tivi "),
}


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


# Commit không đưa vào nhật ký cho người dùng (26/10/2026): việc nội bộ, tài liệu, CI. Commit "Phát hành …" cũng bỏ.
NOI_BO = re.compile(r"(?i)^(nội bộ|tài liệu|readme|ci):")
# Dòng tiếng Anh của một mục nhật ký: dòng "EN: …" trong thân commit; không có thì phần tiếng Anh dùng tiêu đề tiếng Việt.
DONG_EN = re.compile(r"^EN:\s*(.+?)\s*$", re.M)


def tach_nhat_ky(ban_ghi):
    """(mới, sửa) từ các cặp (tiêu đề, thân) cũ trước mới sau; mỗi mục là (tiếng Việt, tiếng Anh)."""
    muc = []
    for tieu_de, than in ban_ghi:
        tieu_de = tieu_de.strip()
        if not tieu_de or tieu_de.startswith("Phát hành ") or NOI_BO.match(tieu_de):
            continue
        en = DONG_EN.search(than or "")
        muc.append((tieu_de, en.group(1) if en else tieu_de))
    sua = [m for m in muc if re.match(r"(?i)(sửa|fix)\b", m[0])]
    return [m for m in muc if m not in sua], sua


def nhat_ky():
    """(mới, sửa): các mục (tiếng Việt, tiếng Anh) từ commit sau tag trước tới HEAD, cũ trước mới sau."""
    tag = tag_truoc()
    r = git("log", "--no-merges", "--reverse", "--pretty=format:%s%x1f%b%x1e", f"{tag}..HEAD" if tag else "HEAD")
    ban_ghi = [tuple((k.split("\x1f", 1) + [""])[:2]) for k in r.stdout.split("\x1e") if k.strip()]
    return tach_nhat_ky(ban_ghi)


def ngoai_du_lieu():
    """File vào APK đã đổi từ tag trước mà không phải dữ liệu mẫu tivi; rỗng = tự phát hành được."""
    tag = tag_truoc()
    if tag is None:
        return ["(chưa có bản phát hành nào)"]
    con = []
    # --no-renames: file chuyển từ trong APK ra ngoài (docs/…) hiện cả đường cũ, không lọt qua như một file tài liệu mới.
    for f in git("diff", "--name-only", "--no-renames", f"{tag}..HEAD").stdout.splitlines():
        f = f.strip()
        if not f or f == DU_LIEU or NGOAI_APK.match(f):
            continue
        mau = DONG_SO_MAU.get(f)
        if mau:
            diff = git("diff", "--unified=0", f"{tag}..HEAD", "--", f).stdout.splitlines()
            dong = [d[1:] for d in diff if d[:1] in "+-" and not d.startswith(("+++", "---"))]
            if dong and all(mau.match(d) for d in dong):
                continue
        con.append(f)
    return con


def ghi_chu_app(ghi_chu, muc, xem_du):
    """Nhật ký trong app: ghi chú + một dòng mỗi mục, tối đa DONG_TRONG_APP dòng."""
    dong = ([ghi_chu] if ghi_chu else []) + [f"- {s}" for s in muc]
    if len(dong) > DONG_TRONG_APP:
        dong = dong[:DONG_TRONG_APP - 1] + [xem_du]
    return "\n".join(dong)


def dong_goi(ban, apk, ghi_chu="", ghi_chu_en=""):
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
    ghi_chu_en = ghi_chu_en.strip()
    vi = [m[0] for m in moi + sua]
    en = [m[1] for m in moi + sua]
    ngay = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).date().isoformat()
    cap_nhat = {"app": {
        "versionCode": ma_ban(ban), "versionName": ban,
        "apk": f"https://github.com/{KHO}/releases/download/v{ban}/{ten}",
        "co": len(du_lieu), "sha256": sha,
        "ghiChu": ghi_chu_app(ghi_chu, vi, f"… xem đủ ở github.com/{KHO}/releases"),
        "ghiChuEn": ghi_chu_app(ghi_chu_en, en, f"… full notes at github.com/{KHO}/releases") if ghi_chu_en or en else "",
        "ngay": ngay,
    }}
    with open(os.path.join(RA, "cap-nhat.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(cap_nhat, f, ensure_ascii=False, indent=1)
        f.write("\n")

    md = ([ghi_chu, ""] if ghi_chu else [])
    if moi:
        md += ["## Có gì mới", *[f"- {s[0]}" for s in moi], ""]
    if sua:
        md += ["## Sửa lỗi", *[f"- {s[0]}" for s in sua], ""]
    md += ["## Cài đặt",
           f"- Cài lần đầu: tải `{ten}` bên dưới về điện thoại Android (hoặc tivi) rồi mở để cài.",
           "- Đã có TiviKit 1.1.0 trở lên: app tự cập nhật (Android 12+ không cần bấm, máy cũ hơn chạm thông báo một lần).",
           "", "---", "", "### English", ""]
    if ghi_chu_en:
        md += [ghi_chu_en, ""]
    if moi:
        md += ["**What's new**", "", *[f"- {s[1]}" for s in moi], ""]
    if sua:
        md += ["**Fixes**", "", *[f"- {s[1]}" for s in sua], ""]
    md += [f"Install: download `{ten}` below to an Android phone (or TV) and open it. TiviKit 1.1.0 or later updates itself.",
           "",
           f"- SHA-256: `{sha}`",
           f"- Chứng chỉ ký / Signing certificate (SHA-256): `{DAU_CHUNG_CHI}`"]
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
    elif lenh[0] == "dong-goi" and len(lenh) in (3, 4, 5):
        dong_goi(lenh[1], lenh[2], lenh[3] if len(lenh) >= 4 else "", lenh[4] if len(lenh) == 5 else "")
    elif lenh[0] == "chi-du-lieu" and len(lenh) == 1:
        con = ngoai_du_lieu()
        if con:
            print("Có thay đổi vào APK ngoài dữ liệu mẫu tivi, cần duyệt: " + ", ".join(con))
            sys.exit(1)
        print("Từ bản trước, thứ vào APK chỉ đổi ở dữ liệu mẫu tivi: tự phát hành được.")
    else:
        sys.exit(__doc__)
