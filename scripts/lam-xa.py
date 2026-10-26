#!/usr/bin/env python3
"""Toạ độ 3.321 phường/xã sau sáp nhập 2025 cho ô chọn thời tiết (góp ý 23/10/2026: muốn thời tiết theo xã).
Tên + tỉnh: vietmap-company/vietnam_administrative_address (admin_new). Toạ độ: tâm ranh giới phường/xã trên
OpenStreetMap (Overpass, admin_level 6, "out center"), tên trùng giữa các tỉnh thì chọn bản gần tâm tỉnh nhất.
Không khớp được thì lấy tâm tỉnh. Ghi assets/xa.json: {"tinh": [[tên, lat, lon]], "xa": [[tên, chỉ số tỉnh, lat, lon]]}.
usage: lam-xa.py ward.json province.json osm-xa.json osm-tinh.json out.json"""
import json, math, sys, unicodedata
ward, prov, osm_xa, osm_tinh, out = sys.argv[1:6]
chuan = lambda s: " ".join(unicodedata.normalize("NFC", s).strip().lower().replace(" - ", "-").replace("–", "-").split())
bo_loai = lambda s: chuan(s).replace("thành phố ", "").replace("tỉnh ", "")
W = json.load(open(ward, encoding="utf-8")); P = json.load(open(prov, encoding="utf-8"))
ot = {bo_loai(e["tags"]["name"]): (e["center"]["lat"], e["center"]["lon"]) for e in json.load(open(osm_tinh))["elements"]}
ds_tinh = sorted(P.values(), key=lambda p: p["name"])
chi_so = {p["code"]: i for i, p in enumerate(ds_tinh)}
tam = [ot[bo_loai(p["name_with_type"])] for p in ds_tinh]
theo_ten = {}
for e in json.load(open(osm_xa))["elements"]:
    if "center" in e: theo_ten.setdefault(chuan(e["tags"]["name"]), []).append((e["center"]["lat"], e["center"]["lon"]))
xa, khop, truot = [], 0, []
for w in sorted(W.values(), key=lambda w: (chi_so[w["parent_code"]], w["name"])):
    i = chi_so[w["parent_code"]]; c = theo_ten.get(chuan(w["name_with_type"]), [])
    if c:
        lat, lon = min(c, key=lambda p: math.dist(p, tam[i])); khop += 1
        if math.dist((lat, lon), tam[i]) > 2.5: lat, lon = tam[i]; khop -= 1; truot.append(w["path_with_type"])
    else:
        lat, lon = tam[i]; truot.append(w["path_with_type"])
    xa.append([w["name_with_type"], i, round(lat, 4), round(lon, 4)])
json.dump({"tinh": [[p["name_with_type"], round(t[0], 4), round(t[1], 4)] for p, t in zip(ds_tinh, tam)], "xa": xa},
          open(out, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print(f"{len(xa)} phường/xã, {khop} có toạ độ riêng, {len(truot)} dùng tâm tỉnh -> {out}")
for t in truot[:15]: print("  tâm tỉnh:", t)
