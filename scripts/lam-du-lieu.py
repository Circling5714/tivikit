#!/usr/bin/env python3
"""Gom 148 danh sách debloat của cleanyourtv thành một file JSON cho app.

Chạy: python3 -I scripts/lam-du-lieu.py upstream app/src/main/assets/du-lieu.json

Mỗi mẫu TV: tên thư mục, dòng đầu header (tên máy), toàn bộ header (để khớp model), và các gói
kèm nhóm + ghi chú. Thêm bảng "gói" toàn cục: mỗi gói xuất hiện ở bao nhiêu danh sách, và có danh
sách nào nhắc nó trong header mà KHÔNG tắt (thường là "giữ lại") — app dùng để cảnh báo.
"""
import json
import os
import re
import sys

PKG_RE = re.compile(r'^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$')
TOKEN_PKG = re.compile(r'\b[a-z][a-z0-9_]*(?:\.[A-Za-z0-9_]+){2,}\b')
PHIM = ('app_keys', 'settings_key_keycodes', 'settings_key_scancodes', 'key_filter', 'handle_view_inputs')
KEEP_WORDS = re.compile(r"\b(keep|kept|must stay|must remain|stays? enabled|do not disable|don't disable|"
                        r"never disable|leave it|left enabled|needed|needs it|load-bearing)\b", re.I)


def parse(path):
    raw = open(path, encoding='utf-8-sig', errors='replace').read().replace('\r', '')
    lines = raw.split('\n')
    header, i = [], 0
    while i < len(lines) and lines[i].startswith('#'):
        header.append(lines[i][1:].strip())
        i += 1
    group, entries = '', []
    prev_blank = True
    for line in lines[i:]:
        s = line.strip()
        if not s:
            prev_blank = True
            continue
        if s.startswith('#'):
            text = s.lstrip('#').strip()
            # Một dòng chú thích đứng ngay sau dòng trống là tên nhóm.
            if prev_blank and text and not PKG_RE.match(text.split()[0] if text.split() else ''):
                group = text
            prev_blank = False
            continue
        prev_blank = False
        pkg, _, note = s.partition('#')
        pkg = pkg.split()[0] if pkg.split() else ''
        if PKG_RE.match(pkg):
            entries.append({'p': pkg, 'g': group, 'n': note.strip()})
    return header, entries


def main():
    src, out = sys.argv[1], sys.argv[2]
    ex_dir = os.path.join(src, 'examples')
    models, count, kept = [], {}, {}
    for name in sorted(os.listdir(ex_dir)):
        f = os.path.join(ex_dir, name, 'debloat.txt')
        if not os.path.isfile(f):
            continue
        header, entries = parse(f)
        listed = {e['p'] for e in entries}
        for p in listed:
            count[p] = count.get(p, 0) + 1
        # Gói nhắc trong header nhưng không nằm trong danh sách tắt = máy đó giữ lại.
        # Chỉ tính dòng có chữ kiểu "keep/kept/must stay/do not disable", không thì header nào
        # nhắc tên gói (lệnh mẫu, ghi chú lịch sử) cũng thành "giữ".
        # Ghép header thành câu (một câu có thể vắt nhiều dòng), xét từng câu.
        giu = set()
        for cau in re.split(r'(?<=[.!?])\s+(?=[A-Z(])|\n\s*\n', '\n'.join(header)):
            if KEEP_WORDS.search(cau):
                giu.update(TOKEN_PKG.findall(cau))
        for p in giu - listed:
            kept.setdefault(p, []).append(name)
        m = {
            'id': name,
            'ten': header[0] if header else name,
            'header': '\n'.join(header),
            'goi': entries,
        }
        # Phím remote của mẫu (config.json cạnh debloat.txt): nút app / Cài đặt trên remote, chỉ chạy khi có
        # HomeKeyService. TCL tắt launcherx là nút YouTube, Netflix… chết (24/10/2026, góp ý TCL G09).
        cfg = os.path.join(ex_dir, name, 'config.json')
        if os.path.isfile(cfg):
            try:
                j = json.load(open(cfg, encoding='utf-8-sig'))
            except ValueError:
                j = {}
            phim = {k: j[k] for k in PHIM if k in j}
            if phim:
                m['phim'] = phim
        models.append(m)
    goi = {p: {'c': c} for p, c in count.items()}
    for p, names in kept.items():
        goi.setdefault(p, {'c': 0})['giu'] = len(names)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8') as fh:
        json.dump({'mau': models, 'goi': goi}, fh, ensure_ascii=False, separators=(',', ':'))
    print(f'{len(models)} mẫu, {len(count)} gói, {len(kept)} gói có máy giữ lại -> {out}')


if __name__ == '__main__':
    main()
