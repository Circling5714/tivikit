package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Hẹn giờ (25/10/2026): app phụ vn.tivikit.hengio trên tivi (module hengio/) giữ lịch bật tivi / tắt tivi / mở app
 * và tự chạy bằng báo thức của tivi. Ở đây: lịch dạng TiviKit gửi đi (cùng dạng JSON với vn.tivikit.hengio.Lich) và
 * trạng thái app phụ trả về.
 */
object HenGio {
    const val GOI = "vn.tivikit.hengio"
    /** Dịch vụ trợ năng của app phụ (tắt tivi đúng giờ, mở màn hình hẹn). */
    const val DICH_VU = "$GOI/vn.tivikit.hengio.HenGioDichVu"
    const val BAT = "bat"
    const val TAT = "tat"
    const val MO = "mo"
    const val TOI_DA = 30

    /** Một việc: [gio]:[phut] các ngày [ngay] (1 = Thứ Hai … 7 = Chủ Nhật); [goi] = app mở (bật tivi: không bắt buộc). */
    data class Viec(val gio: Int, val phut: Int, val ngay: Set<Int>, val loai: String, val goi: String? = null, val bat: Boolean = true) {
        val gioChu: String get() = "%02d:%02d".format(java.util.Locale.ROOT, gio, phut)
    }

    /** "6:30", "06:30", "6h30", "630" → (6, 30); sai → null. */
    fun docGio(s: String): Pair<Int, Int>? {
        val m = Regex("""^(\d{1,2})\s*[:hH.]?\s*(\d{2})$""").matchEntire(s.trim()) ?: return null
        val g = m.groupValues[1].toInt(); val p = m.groupValues[2].toInt()
        return if (g in 0..23 && p in 0..59) g to p else null
    }

    /** Lịch gửi cho app phụ (content call … --method dat): chỉ số và tên gói, không có nháy đơn. */
    fun json(ds: List<Viec>): String = JSONArray(ds.map { v ->
        JSONObject().put("gio", v.gio).put("phut", v.phut).put("ngay", JSONArray(v.ngay.sorted())).put("loai", v.loai)
            .put("bat", v.bat).apply { if (v.loai != TAT) v.goi?.let { put("goi", it) } }
    }).toString()

    fun docLich(a: JSONArray?): List<Viec> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val ngay = o.optJSONArray("ngay")?.let { n -> (0 until n.length()).map { n.optInt(it) }.filter { it in 1..7 }.toSet() } ?: emptySet()
        val loai = o.optString("loai")
        if (ngay.isEmpty() || loai !in setOf(BAT, TAT, MO)) return@mapNotNull null
        Viec(o.optInt("gio"), o.optInt("phut"), ngay, loai, o.optString("goi").ifBlank { null }, o.optBoolean("bat", true))
    }

    /** Sắp theo giờ trong ngày, rồi theo loại (bật trước mở, tắt sau). */
    fun sapXep(ds: List<Viec>): List<Viec> = ds.sortedWith(compareBy({ it.gio * 60 + it.phut }, { listOf(BAT, MO, TAT).indexOf(it.loai) }))

    data class NhatKy(val luc: Long, val viec: String, val kq: String)

    /** Trạng thái app phụ trên tivi (content call … --method doc). */
    data class TrangThai(
        val lich: List<Viec>, val keTiep: Long, val hoanDen: Long, val dichVuChay: Boolean, val chinhXac: Boolean,
        val bayGio: Long, val muiGio: ZoneId, val nhatKy: List<NhatKy>,
    )

    fun trangThai(j: JSONObject): TrangThai {
        val zone = runCatching { ZoneId.of(j.optString("muiGio")) }.getOrDefault(ZoneId.systemDefault())
        val nk = j.optJSONArray("nhatKy")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { NhatKy(it.optLong("luc"), it.optString("viec"), it.optString("kq")) } }.orEmpty()
        return TrangThai(docLich(j.optJSONArray("lich")), j.optLong("keTiep"), j.optLong("hoanDen"), j.optBoolean("dichVuChay"),
            j.optBoolean("chinhXac", true), j.optLong("bayGio"), zone, nk)
    }

    /** Giờ:phút của [luc] theo múi giờ của tivi, kèm thứ (1 = Thứ Hai) để hiện "06:30 Thứ Hai". */
    fun gioCua(luc: Long, zone: ZoneId): Pair<String, Int> {
        val t = Instant.ofEpochMilli(luc).atZone(zone)
        return "%02d:%02d".format(java.util.Locale.ROOT, t.hour, t.minute) to t.dayOfWeek.value
    }
}
