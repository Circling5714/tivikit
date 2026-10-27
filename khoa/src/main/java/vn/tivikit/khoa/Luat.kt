package vn.tivikit.khoa

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Một khung giờ khoá: phút trong ngày [tu, den) — den < tu là qua đêm (21:00–06:00); ngay 1 = Thứ Hai … 7 = Chủ Nhật. */
data class Khung(val tu: Int, val den: Int, val ngay: Set<Int>)

/**
 * Cấu hình phụ huynh đặt từ TiviKit. [phutThuong] / [phutCuoiTuan]: số phút được xem mỗi ngày thứ Hai–Sáu / thứ Bảy–Chủ
 * Nhật, 0 = không giới hạn (25/10/2026). [coMa]: tivi đã có mã mở khoá (Kho.maMo, lưu riêng; 28/10/2026 thay PIN).
 */
data class CauHinh(
    val bat: Boolean, val khung: List<Khung>, val moTamPhut: Int = 60,
    val phutThuong: Int = 0, val phutCuoiTuan: Int = 0, val coMa: Boolean = false,
)

/** Luật khoá, không đụng Android để test được trên máy tính. */
object Luat {
    fun docJson(s: String?): CauHinh? = runCatching {
        val o = JSONObject(s ?: return null)
        val ks = o.optJSONArray("khung") ?: JSONArray()
        CauHinh(
            bat = o.optBoolean("bat"),
            khung = (0 until ks.length()).map { i ->
                val k = ks.getJSONObject(i)
                val ng = k.optJSONArray("ngay") ?: JSONArray("[1,2,3,4,5,6,7]")
                Khung(phut(k.getString("tu")), phut(k.getString("den")), (0 until ng.length()).map { ng.getInt(it) }.filter { it in 1..7 }.toSet())
            },
            moTamPhut = o.optInt("moTamPhut", 60).coerceIn(5, 240),
            phutThuong = o.optInt("phutThuong", 0).coerceIn(0, 24 * 60),
            phutCuoiTuan = o.optInt("phutCuoiTuan", 0).coerceIn(0, 24 * 60),
        )
    }.getOrNull()

    /** "21:30" → 1290. */
    fun phut(hhmm: String): Int {
        val (h, m) = hhmm.trim().split(":").map { it.toInt() }
        require(h in 0..23 && m in 0..59)
        return h * 60 + m
    }

    fun chuGio(phut: Int): String = "%02d:%02d".format(phut / 60 % 24, phut % 60)

    /** 1 = Thứ Hai … 7 = Chủ Nhật. */
    fun thu(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

    private fun homTruoc(ngay: Int) = if (ngay == 1) 7 else ngay - 1

    fun trongKhung(k: Khung, ngay: Int, phut: Int): Boolean = when {
        k.tu < k.den -> ngay in k.ngay && phut >= k.tu && phut < k.den
        k.tu > k.den -> (ngay in k.ngay && phut >= k.tu) || (homTruoc(ngay) in k.ngay && phut < k.den)
        else -> ngay in k.ngay // tu == den: cả ngày
    }

    fun trongGio(c: CauHinh, luc: Calendar): Boolean {
        if (!c.bat || !c.coMa) return false
        val ngay = thu(luc); val p = luc.get(Calendar.HOUR_OF_DAY) * 60 + luc.get(Calendar.MINUTE)
        return c.khung.any { trongKhung(it, ngay, p) }
    }

    /** Giờ khung khoá đang chạy kết thúc (dò từng phút, tối đa 8 ngày); null nếu không khoá. */
    fun hetKhoaLuc(c: CauHinh, bayGio: Calendar): Calendar? {
        if (!trongGio(c, bayGio)) return null
        val x = bayGio.clone() as Calendar
        x.set(Calendar.SECOND, 0); x.set(Calendar.MILLISECOND, 0)
        repeat(8 * 24 * 60) {
            x.add(Calendar.MINUTE, 1)
            if (!trongGio(c, x)) return x
        }
        return null
    }

    // ---------- Giới hạn giờ xem mỗi ngày (25/10/2026) ----------

    /** Số phút được xem ngày [thu] (1 = Thứ Hai … 7 = Chủ Nhật); 0 = không giới hạn. */
    fun gioiHanPhut(c: CauHinh, thu: Int): Int = if (thu >= 6) c.phutCuoiTuan else c.phutThuong

    /** Đã xem hết giờ hôm nay: khoá tới hết ngày (nhập mã mở thêm moTamPhut). Khoá tắt / chưa có mã thì không. */
    fun hetGioXem(c: CauHinh, thu: Int, giayDaXem: Long): Boolean {
        if (!c.bat || !c.coMa) return false
        val gh = gioiHanPhut(c, thu)
        return gh > 0 && giayDaXem >= gh * 60L
    }

    /** Khoá ngày của [c]: "2026-10-25" (giờ của tivi). */
    fun ngay(c: Calendar): String = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    /** Cộng [giay] vào ngày [ngay] trong sổ thời gian xem (JSON ngày → giây), chỉ giữ [giu] ngày gần nhất. */
    fun cong(so: JSONObject, ngay: String, giay: Long, giu: Int = 14): JSONObject {
        so.put(ngay, so.optLong(ngay) + giay)
        val cu = so.keys().asSequence().toList().sortedDescending().drop(giu)
        cu.forEach { so.remove(it) }
        return so
    }

    /** "1 giờ 25 phút" / "40 phút" (để hiện trên màn khoá). */
    fun chuThoiGian(phut: Int, vi: Boolean): String = when {
        phut >= 60 && phut % 60 == 0 -> if (vi) "${phut / 60} giờ" else "${phut / 60} h"
        phut >= 60 -> if (vi) "${phut / 60} giờ ${phut % 60} phút" else "${phut / 60} h ${phut % 60} min"
        else -> if (vi) "$phut phút" else "$phut min"
    }

    /** Vì sao tivi khoá: hết giờ xem hôm nay (ưu tiên: khung giờ khoá có hết thì vẫn khoá tới hết ngày) hay trong khung giờ khoá. */
    enum class LyDo { HET_GIO, GIO_KHOA }

    /** Lúc [luc] có khoá không, vì sao; null = không khoá (kể cả đang mở tạm bằng mã tới [moTamDen]). */
    fun lyDo(c: CauHinh?, luc: Calendar, giayDaXem: Long, moTamDen: Long): LyDo? = when {
        c == null || luc.timeInMillis < moTamDen -> null
        hetGioXem(c, thu(luc), giayDaXem) -> LyDo.HET_GIO
        trongGio(c, luc) -> LyDo.GIO_KHOA
        else -> null
    }

    /**
     * Cứ xem tiếp thì còn mấy phút (1..[truoc]) nữa tivi khoá, để báo trước; null nếu [truoc] phút tới không khoá. Qua
     * nửa đêm thì ngày mới tính lại từ 0.
     */
    fun sapKhoa(c: CauHinh, bayGio: Calendar, giayDaXem: Long, moTamDen: Long, truoc: Int = 5): Int? {
        val homNay = ngay(bayGio)
        for (m in 1..truoc) {
            val x = (bayGio.clone() as Calendar).apply { add(Calendar.MINUTE, m) }
            val daXem = if (ngay(x) == homNay) giayDaXem + m * 60L else 0L
            if (lyDo(c, x, daXem, moTamDen) != null) return m
        }
        return null
    }

    /**
     * Sai mã 5 lần liền thì chờ, lần chờ sau lâu hơn: 1, 5, 15 rồi 60 phút (28/10/2026). Trước đây lần nào cũng chờ 1 phút,
     * nên dò hết 10.000 PIN 4 số chỉ mất chưa tới 2 ngày bấm liền.
     */
    fun phutCho(lan: Int): Int = intArrayOf(1, 5, 15, 60)[lan.coerceIn(0, 3)]
}
