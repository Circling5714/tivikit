#!/usr/bin/env python3
"""Cập nhật mẫu tivi từ CleanYourTV (26/10/2026) — workflow .github/workflows/cap-nhat-mau.yml gọi hằng tuần.

  python3 scripts/cap_nhat_mau.py <thư mục CleanYourTV>

Dựng lại app/src/main/assets/du-lieu.json bằng scripts/lam-du-lieu.py, so với dữ liệu đang có theo từng mẫu (thêm / bỏ /
đổi). Không khác thì in "doi=0". Khác thì ghi dữ liệu mới, sửa số mẫu ở DuLieu.SO_MAU, giay-phep.txt và README (mỗi chỗ
phải khớp đúng một lần, không thì dừng), viết dist/mau-commit.txt (lời commit) và dist/mau-ghi-chu.txt (ghi chú phát hành),
rồi in "doi=1", "so_mau=…" và "chan=…" (dạng GITHUB_OUTPUT). "chan" khác rỗng = không tự phát hành (bỏ quá nhiều mẫu
một lần: CleanYourTV dọn hàng loạt hay dựng dữ liệu lỗi), workflow mở pull request để xem.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

GOC = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DU_LIEU = os.path.join(GOC, "app", "src", "main", "assets", "du-lieu.json")
RA = os.path.join(GOC, "dist")
# Một lần cập nhật bỏ quá chừng này mẫu thì không tự phát hành.
BO_TOI_DA = 5
# (file, mẫu regex có một nhóm là số mẫu)
SO_MAU_O = [
    (os.path.join(GOC, "app", "src", "main", "java", "vn", "tivikit", "dulieu", "DuLieu.kt"), r"(const val SO_MAU = )(\d+)"),
    (os.path.join(GOC, "app", "src", "main", "assets", "giay-phep.txt"), r"(CleanYourTV — danh sách )(\d+)( mẫu tivi)"),
    (os.path.join(GOC, "README.md"), r"(So với )(\d+)( danh sách đã thử)"),
    (os.path.join(GOC, "README.md"), r"(Matches )(\d+)( tested lists)"),
]


def bao(*ts):
    print(*ts, file=sys.stderr)


def ten_ngan(m):
    """Tên máy gọn từ dòng đầu header: phần trước " (" hoặc dấu phẩy, tối đa 40 ký tự."""
    t = re.split(r" \(|, ", m["ten"], maxsplit=1)[0].strip()
    return t if len(t) <= 40 else t[:39].rstrip() + "…"


def sua_so_mau(n):
    for duong_dan, mau in SO_MAU_O:
        with open(duong_dan, encoding="utf-8") as f:
            chu = f.read()
        moi, so_cho = re.subn(mau, lambda m: m.group(1) + str(n) + (m.group(3) if m.lastindex >= 3 else ""), chu)
        if so_cho != 1:
            sys.exit(f"{os.path.relpath(duong_dan, GOC)}: tìm thấy {so_cho} chỗ ghi số mẫu (cần đúng 1) — sửa tay rồi chạy lại")
        with open(duong_dan, "w", encoding="utf-8", newline="\n") as f:
            f.write(moi)


def main(nguon):
    with tempfile.TemporaryDirectory() as tam:
        ra_tam = os.path.join(tam, "du-lieu.json")
        # -X utf8: in tiếng Việt được cả trên console Windows (-I bỏ qua biến PYTHONIOENCODING).
        subprocess.run([sys.executable, "-I", "-X", "utf8", os.path.join(GOC, "scripts", "lam-du-lieu.py"), nguon, ra_tam],
                       check=True, stdout=sys.stderr)
        with open(ra_tam, encoding="utf-8") as f:
            moi = json.load(f)
        with open(DU_LIEU, encoding="utf-8") as f:
            cu = json.load(f)
        if moi == cu:
            bao("Danh sách tivi không đổi.")
            print("doi=0")
            return
        with open(ra_tam, "rb") as f:
            du_lieu_moi = f.read()
    with open(DU_LIEU, "wb") as f:
        f.write(du_lieu_moi)

    im, ic = {m["id"]: m for m in moi["mau"]}, {m["id"]: m for m in cu["mau"]}
    them = sorted(set(im) - set(ic))
    bo = sorted(set(ic) - set(im))
    doi = sorted(i for i in set(im) & set(ic) if im[i] != ic[i])
    n = len(im)
    sua_so_mau(n)

    phan = [f"+{len(them)} mới"] if them else []
    phan_en = [f"+{len(them)} new"] if them else []
    if doi:
        phan.append(f"{len(doi)} cập nhật")
        phan_en.append(f"{len(doi)} updated")
    if bo:
        phan.append(f"−{len(bo)} bỏ")
        phan_en.append(f"−{len(bo)} removed")
    if not phan:
        phan.append("đổi bảng gói")
        phan_en.append("package table changes")
    commit = subprocess.run(["git", "-C", nguon, "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip()
    tieu_de = f"Mẫu tivi: {n} mẫu từ CleanYourTV ({', '.join(phan)})"
    than = [f"Dữ liệu dựng từ CleanYourTV {commit} (github.com/Gathin23/cleanyourtv) bằng scripts/lam-du-lieu.py."]
    if them:
        than += ["", "Mẫu mới: " + "; ".join(ten_ngan(im[i]) for i in them) + "."]
    if bo:
        than += ["", "Bỏ (CleanYourTV đã gỡ): " + "; ".join(bo) + "."]
    if doi:
        than += ["", f"Cập nhật {len(doi)} mẫu: " + ", ".join(doi) + "."]
    # Dòng tiếng Anh cho nhật ký phát hành (scripts/phat_hanh.py đọc "EN: …").
    than += ["", f"EN: TV lists: {n} models from CleanYourTV ({', '.join(phan_en)})"]

    ghi_chu = f"Danh sách tivi cập nhật từ CleanYourTV, nay {n} mẫu"
    ghi_chu_en = f"TV lists updated from CleanYourTV, now {n} models"
    if them:
        vai = [ten_ngan(im[i]) for i in them[:6]]
        ghi_chu += f": thêm {len(them)} mẫu ({', '.join(vai)}{', …' if len(them) > 6 else ''})"
        ghi_chu_en += f": {len(them)} new ({', '.join(vai)}{', …' if len(them) > 6 else ''})"
    if doi:
        ghi_chu += f"{',' if them else ':'} cập nhật {len(doi)} mẫu"
        ghi_chu_en += f"{',' if them else ':'} {len(doi)} updated"
    ghi_chu += "."
    ghi_chu_en += "."

    os.makedirs(RA, exist_ok=True)
    with open(os.path.join(RA, "mau-commit.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(tieu_de + "\n\n" + "\n".join(than) + "\n")
    with open(os.path.join(RA, "mau-ghi-chu.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(ghi_chu + "\n")
    with open(os.path.join(RA, "mau-ghi-chu-en.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(ghi_chu_en + "\n")
    bao(tieu_de)
    print("doi=1")
    print(f"so_mau={n}")
    print(f"chan=bỏ {len(bo)} mẫu một lần (quá {BO_TOI_DA})" if len(bo) > BO_TOI_DA else "chan=")


if __name__ == "__main__":
    for luong in (sys.stdout, sys.stderr):
        if hasattr(luong, "reconfigure"):
            luong.reconfigure(encoding="utf-8")
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
