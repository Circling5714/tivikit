#!/usr/bin/env python3
"""Đọc (CHỈ ĐỌC) thư người dùng trả lời về cleantv@ / contest@ — hộp Migadu cleantv-hop@alexvgs.tech qua IMAP.
Không đánh dấu đã đọc (BODY.PEEK), không xoá. Mật khẩu ở .env.mail (không vào git).
usage: scripts/doc-thu.py [số ngày, mặc định 3]"""
import email, imaplib, os, sys, datetime
from email.header import decode_header, make_header
env = dict(l.strip().split("=", 1) for l in open(os.path.join(os.path.dirname(__file__), "..", ".env.mail")) if "=" in l)
ngay = int(sys.argv[1]) if len(sys.argv) > 1 else 3
tu = (datetime.date.today() - datetime.timedelta(days=ngay)).strftime("%d-%b-%Y")
m = imaplib.IMAP4_SSL(env["MIGADU_IMAP_HOST"], 993)
m.login(env["MIGADU_IMAP_USER"], env["MIGADU_IMAP_PASS"])
m.select("INBOX", readonly=True)
_, so = m.search(None, f'(SINCE "{tu}")')
ds = so[0].split()
print(f"{len(ds)} thư từ {tu}")
for i in reversed(ds):
    _, d = m.fetch(i, "(BODY.PEEK[])")
    msg = email.message_from_bytes(d[0][1])
    h = lambda k: str(make_header(decode_header(msg.get(k, ""))))
    than = ""
    for p in (msg.walk() if msg.is_multipart() else [msg]):
        if p.get_content_type() == "text/plain" and not p.get_filename():
            than = p.get_payload(decode=True).decode(p.get_content_charset() or "utf-8", "replace"); break
    # Bỏ phần trích thư cũ (dòng bắt đầu bằng ">" và từ "On ... wrote:" / "Vào ... đã viết:")
    gon = []
    for l in than.splitlines():
        if l.startswith(">") or ("wrote:" in l) or ("đã viết:" in l): break
        gon.append(l)
    print("—" * 60); print(h("Date")); print("Từ:", h("From")); print("Tiêu đề:", h("Subject"))
    print("\n".join(gon).strip()[:1500])
m.logout()
