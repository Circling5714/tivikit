package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import vn.tivikit.t
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

    /** "6:30", "06:30", "6h30", "630" → (6, 30); chỉ có giờ ("06") là phút 00; sai → null. */
    fun docGio(s: String): Pair<Int, Int>? {
        Regex("""^\d{1,2}$""").matchEntire(s.trim())?.let { m -> return m.value.toInt().takeIf { it in 0..23 }?.let { it to 0 } }
        val m = Regex("""^(\d{1,2})\s*[:hH.]?\s*(\d{2})$""").matchEntire(s.trim()) ?: return null
        val g = m.groupValues[1].toInt(); val p = m.groupValues[2].toInt()
        return if (g in 0..23 && p in 0..59) g to p else null
    }

    /**
     * Ô giờ lúc đang gõ (31/10/2026): chỉ giữ chữ số, tự chèn dấu hai chấm khi đã có chữ số phút; chữ số đầu từ 3 trở lên là
     * giờ một chữ số nên thêm 0: "6" → "06", "630" → "06:30", "1830" → "18:30". Bàn phím số của điện thoại không có dấu ':'.
     */
    fun oGio(s: String): String {
        var so = s.filter { it in '0'..'9' }
        if (so.firstOrNull()?.let { it in '3'..'9' } == true) so = "0$so"
        so = so.take(4)
        return if (so.length <= 2) so else so.take(2) + ":" + so.drop(2)
    }

    /** Ô giờ đang gõ đã sai chưa: giờ quá 23, hay chữ số đầu của phút quá 5. */
    fun gioSai(s: String): Boolean {
        val so = s.filter { it in '0'..'9' }
        return (so.length >= 2 && so.take(2).toInt() > 23) || (so.length >= 3 && so[2] > '5')
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

    /** Việc "thử đánh thức" (nút Thử) trong nhật ký. */
    private const val VIEC_THU = "thu"
    private val MA_SO = Regex("""^(hoan|hen)-(\d+)$""")

    /**
     * Một dòng nhật ký của app phụ, nói cho người dùng (31/10/2026): "Bật tivi – tivi đang chờ, đã bật lên", "Mở SmartTube
     * – đã mở". [tenApp] đổi tên gói ra tên app. Mã chưa biết (app phụ bản sau) thì hiện nguyên mã.
     */
    fun moTa(n: NhatKy, tenApp: (String) -> String): String {
        val viec = when {
            n.viec == BAT -> t("Bật tivi", "Turn on")
            n.viec == VIEC_THU -> t("Thử bật tivi", "Power-on test")
            n.viec == TAT -> t("Tắt tivi", "Turn off")
            n.viec.startsWith("$MO:") -> t("Mở ", "Open ") + tenApp(n.viec.removePrefix("$MO:"))
            // App phụ 1.1.1 trở về trước ghi việc lỡ giờ chỉ bằng loại việc.
            n.viec == MO -> t("Mở app", "Open an app")
            else -> n.viec
        }
        val kq = MA_SO.matchEntire(n.kq)?.destructured?.let { (loai, so) ->
            if (loai == "hoan") t("hoãn $so phút", "snoozed $so min") else t("hẹn tự bật sau $so giây", "set to come on in $so s")
        } ?: when (n.kq) {
            "danh-thuc" -> t("tivi đang chờ, đã bật lên", "the TV was in standby and came on")
            "dang-bat" -> if (n.viec == VIEC_THU) t("tivi vẫn đang bật, chưa tắt bằng remote", "the TV was still on, not turned off with the remote")
                else t("tivi đang bật sẵn", "the TV was already on")
            "ok" -> if (n.viec == TAT) t("đã tắt", "done") else t("đã mở", "opened")
            "mo-lai" -> t("mở lại vì launcher vừa đè lên", "reopened after the launcher came up on top")
            "khong-mo-duoc" -> t("không mở được (app đã gỡ hay đang tắt?)", "couldn't open it (removed or disabled?)")
            "tivi-dang-tat" -> t("bỏ qua vì tivi đang tắt", "skipped: the TV was off")
            "bao-truoc" -> t("đã báo trước 1 phút", "showed the 1-minute warning")
            "bao-tre" -> t("đã báo trễ, ngay trước giờ tắt", "showed the warning late, just before the time")
            "da-tat-san" -> t("tivi đã tắt sẵn", "the TV was already off")
            "dich-vu-tat" -> t("không tắt được vì dịch vụ Hẹn giờ đang tắt", "couldn't: the Timer service is off")
            "khong-tat-duoc" -> t("tivi không cho tắt", "the TV didn't allow it")
            "lo-gio" -> t("bỏ qua vì trễ quá 10 phút (tivi ngủ sâu hay vừa đổi giờ)", "skipped: over 10 minutes late (deep sleep or a clock change)")
            else -> n.kq
        }
        return "$viec – $kq"
    }

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
