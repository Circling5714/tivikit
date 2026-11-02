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
    /** Việc một lần đang chờ tối đa (app phụ 1.2). */
    const val MOT_LAN_TOI_DA = 10
    /** Hẹn tắt sau từ 1 phút tới 12 giờ. */
    val SAU_PHUT = 1..720

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
    /** Việc Auto Reboot trong nhật ký (app phụ 1.4). */
    const val VIEC_KDL = "kdl"
    private val MA_SO = Regex("""^(hoan|hen)-(\d+)$""")

    /**
     * Một dòng nhật ký của app phụ, nói cho người dùng (31/10/2026): "Bật tivi – tivi đang chờ, đã bật lên", "Mở SmartTube
     * – đã mở". [tenApp] đổi tên gói ra tên app. Mã chưa biết (app phụ bản sau) thì hiện nguyên mã.
     */
    fun moTa(n: NhatKy, tenApp: (String) -> String): String {
        if (n.viec == VIEC_KDL) return "Auto Reboot – " + moTaKdl(n.kq)
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

    /** Kết quả Auto Reboot trong nhật ký, nói bằng lời (như Chu.kdl của app phụ). */
    fun moTaKdl(kq: String): String = when (kq) {
        "khoi-dong-lai" -> t("tivi đang chờ, đã gửi lệnh khởi động lại", "the TV was in standby; restarting it")
        "thu" -> t("thử ngay: đã gửi lệnh khởi động lại", "test: restarting now")
        "da-khoi-dong-lai" -> t("đã khởi động lại, màn hình tắt như trước", "restarted, screen back off")
        "cho-tivi-tat" -> t("tivi đang bật, chờ tivi tắt (kiểm lại mỗi 15 phút)", "the TV was on; waiting for it to be off (checks every 15 min)")
        "bo-qua" -> t("bỏ qua hôm nay: tivi bật suốt 3 giờ", "skipped today: the TV stayed on for 3 hours")
        "lo-gio" -> t("bỏ qua vì tới trễ quá 3 giờ (tivi ngủ sâu, tắt điện hay vừa đổi giờ)", "skipped: over 3 hours late (deep sleep, power off or a clock change)")
        "loi-khong-co-adb" -> t("không khởi động lại được: ADB qua mạng của tivi đang tắt", "couldn't restart: the TV's network ADB is off")
        "loi-chua-cho-phep" -> t("không khởi động lại được: tivi chưa cho phép gỡ lỗi cho Hẹn giờ", "couldn't restart: the TV hasn't allowed debugging for the Timer")
        "loi" -> t("không khởi động lại được (lỗi ADB)", "couldn't restart (ADB error)")
        "tat-loi" -> t("đã khởi động lại nhưng không tắt được màn hình", "restarted but couldn't turn the screen off")
        else -> kq
    }

    /**
     * Auto Reboot (2/11/2026, app phụ 1.4): mỗi ngày lúc [gio]:[phut] các ngày [ngay] khởi động lại tivi lúc nó đang chờ cho
     * tivi luôn mượt, lên lại thì tắt màn hình. Tivi đang bật thì chờ tivi tắt (15 phút kiểm một lần, tối đa 3 giờ).
     */
    data class AutoReboot(val bat: Boolean = false, val gio: Int = 4, val phut: Int = 0, val ngay: Set<Int> = (1..7).toSet()) {
        val gioChu: String get() = "%02d:%02d".format(java.util.Locale.ROOT, gio, phut)
    }

    /** Cấu hình gửi app phụ (content call … --method kdl): chỉ số, không có nháy đơn. */
    fun jsonKdl(k: AutoReboot): String =
        JSONObject().put("bat", k.bat).put("gio", k.gio).put("phut", k.phut).put("ngay", JSONArray(k.ngay.sorted())).toString()

    /**
     * Auto Reboot trên tivi: cấu hình, [keTiep] lần tới (giờ hẹn, hay lần kiểm lại khi đang chờ), [cho] giờ hẹn đang chờ tivi
     * tắt (0 = không), [adb] ADB riêng của app phụ: "" chưa thử, dang-thu, cho-cho-phep, da-noi, khong-co, chua-cho-phep, loi.
     */
    data class TrangThaiKdl(val cauHinh: AutoReboot, val keTiep: Long, val cho: Long, val adb: String)

    fun docKdl(o: JSONObject?): TrangThaiKdl? {
        o ?: return null
        val ngay = o.optJSONArray("ngay")?.let { n -> (0 until n.length()).map { n.optInt(it) }.filter { it in 1..7 }.toSet() }.orEmpty()
        val k = AutoReboot(o.optBoolean("bat"), o.optInt("gio", 4).coerceIn(0, 23), o.optInt("phut").coerceIn(0, 59), ngay.ifEmpty { (1..7).toSet() })
        return TrangThaiKdl(k, o.optLong("ke"), o.optLong("cho"), o.optString("adb"))
    }

    /** Việc một lần đang chờ trên tivi (app phụ 1.2): [loai] lúc [luc] (tắt tivi: giờ tắt), [id] để huỷ. */
    data class MotLan(val id: String, val luc: Long, val loai: String, val goi: String?)

    /**
     * Trạng thái app phụ trên tivi (content call … --method doc). [coManTv]: app phụ 1.2 trở lên, có màn Hẹn giờ trên tivi
     * và việc một lần. [kdl]: Auto Reboot (app phụ 1.4; null với bản cũ hơn). [dangBat]: màn hình tivi đang sáng.
     */
    data class TrangThai(
        val lich: List<Viec>, val keTiep: Long, val hoanDen: Long, val dichVuChay: Boolean, val chinhXac: Boolean,
        val bayGio: Long, val muiGio: ZoneId, val nhatKy: List<NhatKy>,
        val motLan: List<MotLan> = emptyList(), val coManTv: Boolean = false, val kdl: TrangThaiKdl? = null,
        val dangBat: Boolean = true,
    )

    fun trangThai(j: JSONObject): TrangThai {
        val zone = runCatching { ZoneId.of(j.optString("muiGio")) }.getOrDefault(ZoneId.systemDefault())
        val nk = j.optJSONArray("nhatKy")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { NhatKy(it.optLong("luc"), it.optString("viec"), it.optString("kq")) } }.orEmpty()
        val ml = j.optJSONArray("motLan")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty().mapNotNull { o ->
            val loai = o.optString("loai")
            if (o.optString("id").isBlank() || loai !in setOf(BAT, TAT, MO)) null else MotLan(o.optString("id"), o.optLong("luc"), loai, o.optString("goi").ifBlank { null })
        }
        return TrangThai(docLich(j.optJSONArray("lich")), j.optLong("keTiep"), j.optLong("hoanDen"), j.optBoolean("dichVuChay"),
            j.optBoolean("chinhXac", true), j.optLong("bayGio"), zone, nk, ml.sortedBy { it.luc }, j.has("motLan"), docKdl(j.optJSONObject("kdl")),
            j.optBoolean("dangBat", true))
    }

    /** Lệnh hẹn một lần (content call … --method mot-lan): tắt tivi sau [phut] phút. */
    fun jsonTatSau(phut: Int): String = JSONObject().put("loai", TAT).put("sauPhut", phut).toString()

    /** Lệnh hẹn một lần lúc [gio]:[phut]; tivi tính lần tới của giờ đó (hôm nay hay ngày mai) theo đồng hồ của nó. */
    fun jsonMotLanLuc(loai: String, gio: Int, phut: Int, goi: String?): String =
        JSONObject().put("loai", loai).put("gio", gio).put("phut", phut).apply { if (loai != TAT) goi?.let { put("goi", it) } }.toString()

    /**
     * Tên app gửi app phụ (content call … --method ten-app) để màn Hẹn giờ trên tivi hiện tên ô Kênh nhanh thay cho "Kênh
     * nhanh 2": chỉ ASCII (chữ có dấu thành \uXXXX, libadb tính độ dài lệnh theo ký tự), không có nháy đơn.
     */
    fun jsonTenApp(ten: Map<String, String>): String = TiviKitLauncher.chiAscii(JSONObject(ten).toString()).replace("'", "\\u0027")

    /** "hôm nay", "ngày mai" hay "Thứ Hai 02/11": ngày của [luc] so với [bay], theo múi giờ của tivi. */
    fun ngayCua(luc: Long, bay: Long, zone: ZoneId): String {
        val d = Instant.ofEpochMilli(luc).atZone(zone).toLocalDate()
        val homNay = Instant.ofEpochMilli(bay).atZone(zone).toLocalDate()
        return when (d) {
            homNay -> t("hôm nay", "today")
            homNay.plusDays(1) -> t("ngày mai", "tomorrow")
            else -> t(listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")[d.dayOfWeek.value - 1],
                listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[d.dayOfWeek.value - 1]) +
                " %02d/%02d".format(java.util.Locale.ROOT, d.dayOfMonth, d.monthValue)
        }
    }

    /** Giờ:phút của [luc] theo múi giờ của tivi, kèm thứ (1 = Thứ Hai) để hiện "06:30 Thứ Hai". */
    fun gioCua(luc: Long, zone: ZoneId): Pair<String, Int> {
        val t = Instant.ofEpochMilli(luc).atZone(zone)
        return "%02d:%02d".format(java.util.Locale.ROOT, t.hour, t.minute) to t.dayOfWeek.value
    }
}
