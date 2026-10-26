package vn.tivikit.adb

import vn.tivikit.dulieu.CaiDatNhanh

/** Đọc / ghi các cài đặt của màn Cài đặt nhanh (25/10/2026); chỉ các khoá trong CaiDatNhanh.TAT_CA. */
class CaiDatTv(private val adb: KetNoiAdb) {
    private fun sh(lenh: String) = adb.shell(lenh).replace("\r", "")

    fun doc(): Map<CaiDatNhanh.Khoa, String?> = CaiDatNhanh.tuDoc(sh(CaiDatNhanh.lenhDoc()))

    /** Ghi [gt] vào [k]; null = xoá khoá để tivi dùng mặc định. */
    fun dat(k: CaiDatNhanh.Khoa, gt: String?) {
        require(k in CaiDatNhanh.TAT_CA) { "khoa ${k.ten}" }
        if (gt == null) sh("settings delete ${k.ns} ${k.ten}")
        else { require(CaiDatNhanh.GIA_TRI.matches(gt)) { "gia tri $gt" }; sh("settings put ${k.ns} ${k.ten} $gt") }
    }
}
