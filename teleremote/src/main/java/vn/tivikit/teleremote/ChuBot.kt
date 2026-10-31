package vn.tivikit.teleremote

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Chữ bot trả lời, dựng từ dữ liệu của tivi và của các app phụ (Hẹn giờ, Khoá trẻ em, Map Voice) đọc qua ADB, cùng các số
 * đo của Làm mới (31/10/2026). Không đụng tới tivi hay mạng.
 */
object ChuBot {
    private fun t(en: Boolean, vi: String, enS: String) = if (en) enS else vi

    /** "1,3 GB" (tiếng Việt dùng dấu phẩy, như TiviKit Launcher) / "1.3 GB"; dưới 1 GB thì MB. */
    fun gb(b: Long, en: Boolean): String = if (b >= 1L shl 30) String.format(if (en) Locale.US else Locale("vi"), "%.1f GB", b / (1L shl 30).toDouble())
        else "${b / (1L shl 20)} MB"

    /** "2 ngày 3 giờ" / "5 giờ 53 phút" / "12 phút". */
    fun thoiGian(ms: Long, en: Boolean): String {
        val phut = ms / 60_000
        val gio = phut / 60
        val ngay = gio / 24
        return when {
            ngay > 0 -> t(en, "$ngay ngày ${gio % 24} giờ", "${ngay}d ${gio % 24}h")
            gio > 0 -> t(en, "$gio giờ ${phut % 60} phút", "${gio}h ${phut % 60}m")
            else -> t(en, "$phut phút", "${phut}m")
        }
    }

    /** Giờ của [luc]: "22:30" nếu cùng ngày với [bay], "1/11 06:30" nếu ngày khác. */
    fun gio(luc: Long, bay: Long, mui: ZoneId): String {
        val a = Instant.ofEpochMilli(luc).atZone(mui)
        val hhmm = "%02d:%02d".format(a.hour, a.minute)
        return if (a.toLocalDate() == Instant.ofEpochMilli(bay).atZone(mui).toLocalDate()) hhmm else "${a.dayOfMonth}/${a.monthValue} $hhmm"
    }

    /** Một dòng nhật ký nói bằng lời (như "Việc vừa làm" của TiviKit): "kenh|VTV1" → "mở kênh VTV1". */
    fun moTaViec(viec: String, en: Boolean, tenApp: (String) -> String): String {
        val sau = viec.substringAfter('|', "")
        return when (viec.substringBefore('|')) {
            "dieu-khien" -> t(en, "mở bảng điều khiển", "opened the remote")
            "tat" -> t(en, "tắt tivi", "turned the TV off")
            "khoi-dong-lai" -> t(en, "khởi động lại tivi", "restarted the TV")
            "xin-dung" -> t(en, "xin dùng bot", "asked to use the bot")
            "kenh" -> t(en, "mở kênh ", "opened channel ") + sau
            "mo" -> t(en, "mở ", "opened ") + tenApp(sau)
            "dung" -> t(en, "buộc dừng ", "force-stopped ") + tenApp(sau)
            "noi" -> t(en, "nhắn \"$sau\"", "typed \"$sau\"")
            "tro-ly" -> t(en, "đưa Trợ lý: \"$sau\"", "asked the Assistant: \"$sau\"")
            "link" -> t(en, "mở link ", "opened a link from ") + sau
            "anh" -> t(en, "chiếu ảnh lên tivi", "showed a photo on the TV")
            "video" -> t(en, "phát video lên tivi", "played a video on the TV")
            "go-chu" -> t(en, "gõ chữ lên tivi", "typed text on the TV")
            "chup" -> t(en, "chụp màn hình", "took a screenshot")
            "hen" -> t(en, "hẹn tắt tivi sau ${sau.removePrefix("tat-")} phút", "set the TV to turn off in ${sau.removePrefix("tat-")} min")
            "huy-hen" -> t(en, "huỷ một hẹn giờ", "cancelled a timer")
            "khoa-ngay" -> t(en, "khoá trẻ em ngay", "locked the TV (Kids lock)")
            "mo-tam" -> t(en, "mở khoá trẻ em $sau phút", "unlocked Kids lock for $sau min")
            "dong-app-nen" -> t(en, "đóng app nền", "closed background apps")
            "don-cache" -> t(en, "dọn cache", "cleared the cache")
            "nhan" -> t(en, "nhắn lên màn hình tivi", "showed a message on the TV")
            "bat-man-hinh" -> t(en, "bật màn hình", "turned the screen on")
            "am-luong" -> if (sau == "0") t(en, "tắt tiếng", "muted") else t(en, "chỉnh âm lượng $sau", "set the volume to $sau")
            "kiem-tra-mang" -> t(en, "kiểm tra mạng", "checked the network")
            "duyet" -> t(en, "duyệt $sau", "approved $sau")
            "bo-qua" -> t(en, "bỏ qua $sau", "ignored $sau")
            "vai-tro" -> t(en, "đổi vai trò của $sau", "changed $sau's role")
            "bo-nguoi" -> t(en, "bỏ $sau khỏi danh sách", "removed $sau")
            "them" -> t(en, "thêm $sau", "added $sau")
            "khoa-tv" -> t(en, "khoá thành viên tới $sau", "paused members until $sau")
            "mo-tv" -> t(en, "mở lại cho thành viên", "let members back in")
            else -> viec
        }
    }

    // ---------- Hẹn giờ (app vn.tivikit.hengio) ----------

    private val THU_VI = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    private val THU_EN = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** Ngày trong tuần của một mục lịch (1 = thứ Hai … 7 = Chủ nhật, như app Hẹn giờ): "hằng ngày", "T2–T6", "T7, CN"… */
    fun ngay(ds: List<Int>, en: Boolean): String {
        val d = ds.filter { it in 1..7 }.distinct().sorted()
        val ten = if (en) THU_EN else THU_VI
        return when {
            d.size == 7 -> t(en, "hằng ngày", "every day")
            d == listOf(1, 2, 3, 4, 5) -> "${ten[0]}–${ten[4]}"
            d.isEmpty() -> t(en, "không ngày nào", "no day")
            else -> d.joinToString(", ") { ten[it - 1] }
        }
    }

    /** Việc của một mục Hẹn giờ: "bật tivi, mở Kênh nhanh: VTV1" / "tắt tivi" / "mở SmartTube". */
    fun viecHen(loai: String, goi: String?, en: Boolean, tenApp: (String) -> String): String = when (loai) {
        "bat" -> t(en, "bật tivi", "turn on") + (goi?.let { t(en, ", mở ", ", open ") + tenApp(it) } ?: "")
        "tat" -> t(en, "tắt tivi", "turn off")
        "mo" -> t(en, "mở ", "open ") + (goi?.let(tenApp) ?: "app")
        else -> loai
    }

    /** Chữ của bảng Hẹn giờ và các hẹn một lần (id, chữ trên nút huỷ). */
    data class HenGioTv(val chu: String, val motLan: List<Pair<String, String>>)

    fun henGio(j: JSONObject, tenTivi: String, en: Boolean, mui: ZoneId, tenApp: (String) -> String): HenGioTv {
        val bay = j.optLong("bayGio").takeIf { it > 0 } ?: System.currentTimeMillis()
        val dong = mutableListOf("⏰ ${t(en, "Hẹn giờ", "Timer")} · $tenTivi")
        val lich = ds(j.optJSONArray("lich")).filter { it.optBoolean("bat", true) }.sortedBy { it.optInt("gio") * 60 + it.optInt("phut") }
        if (lich.isNotEmpty()) {
            dong += t(en, "Lịch hằng tuần:", "Weekly:")
            lich.forEach { v ->
                val ngay = v.optJSONArray("ngay")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
                dong += "• %02d:%02d · %s · %s".format(v.optInt("gio"), v.optInt("phut"), ngay(ngay, en),
                    viecHen(v.optString("loai"), v.optString("goi").takeIf { it.isNotEmpty() }, en, tenApp))
            }
        }
        val motLan = ds(j.optJSONArray("motLan")).filter { it.optLong("luc") > 0 }.sortedBy { it.optLong("luc") }
        if (motLan.isNotEmpty()) {
            dong += t(en, "Hẹn một lần:", "One-off:")
            motLan.forEach { m ->
                dong += "• ${gio(m.optLong("luc"), bay, mui)} · " + viecHen(m.optString("loai"), m.optString("goi").takeIf { it.isNotEmpty() }, en, tenApp)
            }
        }
        if (lich.isEmpty() && motLan.isEmpty()) dong += t(en, "Chưa hẹn gì.", "Nothing scheduled.")
        if (j.has("dichVuChay") && !j.optBoolean("dichVuChay")) dong += t(en,
            "⚠️ Dịch vụ Hẹn giờ trên tivi đang tắt: mở TiviKit → Hẹn giờ, bấm Bật lại.", "⚠️ The Timer service on the TV is off: open TiviKit → Timer and turn it on again.")
        dong += t(en, "Lịch hằng tuần đặt ở TiviKit → Hẹn giờ.", "Set the weekly schedule in TiviKit → Timer.")
        val nut = motLan.mapNotNull { m ->
            m.optString("id").takeIf { it.isNotEmpty() }?.let { it to "${gio(m.optLong("luc"), bay, mui)} " + viecHen(m.optString("loai"), null, en, tenApp) }
        }
        return HenGioTv(dong.joinToString("\n"), nut)
    }

    private fun ds(a: JSONArray?): List<JSONObject> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    // ---------- Khoá trẻ em (app vn.tivikit.khoa) ----------

    fun khoa(j: JSONObject, tenTivi: String, bay: Long, en: Boolean, mui: ZoneId, tenApp: (String) -> String): String {
        val dong = mutableListOf("🔒 ${t(en, "Khoá trẻ em", "Kids lock")} · $tenTivi")
        if (j.has("dichVuChay") && !j.optBoolean("dichVuChay")) dong += t(en,
            "⚠️ Dịch vụ Khoá trẻ em trên tivi đang tắt: mở TiviKit → Khoá trẻ em.", "⚠️ The Kids lock service on the TV is off: open TiviKit → Kids lock.")
        if (!j.optBoolean("bat")) {
            dong += t(en, "Khoá trẻ em đang tắt: bật và đặt giờ ở TiviKit → Khoá trẻ em.", "Kids lock is off: turn it on and set the hours in TiviKit → Kids lock.")
            return dong.joinToString("\n")
        }
        val lyDo = j.optString("lyDo")
        val moTam = j.optLong("moTamDen")
        dong += when {
            lyDo.isNotEmpty() -> t(en, "Đang khoá: ", "Locked: ") + when (lyDo) {
                "het-gio" -> t(en, "hết giờ xem hôm nay", "today's viewing time is used up")
                "gio-khoa" -> t(en, "trong giờ khoá", "within locked hours")
                "gio-sai" -> t(en, "đồng hồ tivi chưa đúng", "the TV's clock is wrong")
                "bo-me-khoa" -> t(en, "bố mẹ đã khoá", "locked by a parent")
                else -> lyDo
            }
            moTam > bay -> t(en, "Đang mở tạm tới ", "Unlocked until ") + gio(moTam, bay, mui)
            else -> t(en, "Đang mở (trong giờ được xem)", "Open (within allowed hours)")
        }
        val xem = j.optLong("giayHomNay") * 1000
        val han = j.optInt("gioiHanHomNay")
        dong += t(en, "Hôm nay đã xem ", "Watched today: ") + thoiGian(xem, en) + (if (han > 0) t(en, " / được ", " of ") + XuLyLenh.thoiLuong(han, en) else "")
        val appKhoa = j.optJSONArray("appKhoa")?.let { a -> (0 until a.length()).map { tenApp(a.optString(it)) } }.orEmpty()
        if (appKhoa.isNotEmpty()) dong += t(en, "App đang khoá: ", "Apps locked now: ") + appKhoa.joinToString(", ")
        if (j.optBoolean("coOtp")) dong += t(en, "Mở tạm cần mã 6 số trong app xác thực của bố mẹ (hay mã dự phòng 8 số).",
            "Unlocking needs the 6-digit code from the parent's authenticator app (or an 8-digit backup code).")
        return dong.joinToString("\n")
    }

    // ---------- Map Voice (app vn.tivikit.mapvoice) ----------

    /**
     * Chữ báo việc Map Voice vừa làm cho câu nhắn như nói ([viec] = "yt-phat|nhạc trịnh"…, [app] = gói app đã mở); null nếu
     * Map Voice không làm gì (câu để Trợ lý, câu không phải tìm nhạc / video / mở kênh).
     */
    fun mapVoice(viec: String, app: String, en: Boolean, tenApp: (String) -> String): String? {
        val loai = viec.substringBefore('|')
        val sau = viec.substringAfter('|', "")
        val ten = app.takeIf { it.isNotEmpty() }?.let(tenApp) ?: "YouTube"
        return when (loai) {
            "yt-phat" -> t(en, "▶️ Đang phát video đầu tiên cho “$sau” trên $ten.", "▶️ Playing the first video for “$sau” in $ten.")
            "yt-tim" -> t(en, "🔎 Đang tìm “$sau” trên $ten.", "🔎 Searching “$sau” in $ten.")
            "yt-app" -> t(en, "▶️ Đã mở $ten.", "▶️ $ten is open.")
            "kenh" -> t(en, "📺 Đang mở ${sau.substringAfter('|')} trên $ten.", "📺 Opening ${sau.substringAfter('|')} in $ten.")
            "mo-app" -> t(en, "📱 Đã mở ${tenApp(sau)}.", "📱 ${tenApp(sau)} is open.")
            "loi" -> when (sau) {
                "khong-co-smarttube" -> t(en, "Tivi chưa có SmartTube hay TizenTube để mở YouTube.", "The TV has no SmartTube or TizenTube for YouTube.")
                "khong-mo-duoc" -> t(en, "Không mở được $ten.", "Couldn't open $ten.")
                "khong-co-app" -> t(en, "Tivi không còn app ${tenApp(app)}.", "The TV no longer has ${tenApp(app)}.")
                else -> t(en, "Map Voice báo lỗi ($sau).", "Map Voice reported an error ($sau).")
            }
            else -> null
        }
    }

    // ---------- Làm mới (như màn Làm mới tivi của TiviKit) ----------

    /** Số đo của tivi: RAM (byte), CPU bận (%, null = không đo được), nhiệt độ chip (°C), thời gian chạy, bộ nhớ (byte). */
    data class SoDo(val ramTong: Long, val ramCon: Long, val cpu: Int?, val nhiet: Int?, val msChay: Long, val boNhoTong: Long, val boNhoCon: Long)

    /** Phần trăm CPU bận giữa dòng "cpu …" đầu và cuối của /proc/stat (bận = tổng − rảnh − chờ đĩa), như TiviKit. */
    fun tuProcStat(s: String): Int? {
        val dong = s.lineSequence().map { it.trim() }.filter { it.startsWith("cpu ") }.toList()
        if (dong.size < 2) return null
        fun cot(l: String) = l.split(Regex("\\s+")).drop(1).take(8).mapNotNull { it.toLongOrNull() }
        val a = cot(dong.first()); val b = cot(dong.last())
        if (a.size < 5 || b.size != a.size) return null
        val tong = b.sum() - a.sum()
        if (tong <= 0) return null
        val ranh = (b[3] + b[4]) - (a[3] + a[4])
        return ((tong - ranh).coerceAtLeast(0) * 100 / tong).toInt().coerceIn(0, 100)
    }

    /** Nhiệt độ thermal_zone0: phần nghìn độ (70000) hay độ (45) tuỳ máy; ngoài 1..150 °C là đọc sai. */
    fun tuNhietDo(s: String): Int? {
        val v = s.trim().lineSequence().firstOrNull()?.trim()?.toLongOrNull() ?: return null
        return (if (v >= 1000) v / 1000 else v).toInt().takeIf { it in 1..150 }
    }

    /** Bộ nhớ gần đầy: trống dưới 1 GB hay dưới 15% (như TiviKit; Android tự báo thiếu chỗ ở 10%). */
    fun boNhoGanDay(tong: Long, con: Long): Boolean = tong > 0 && con < maxOf(1L shl 30, tong * 15 / 100)

    fun lamMoi(sd: SoDo, tenTivi: String, en: Boolean): String {
        val dong = mutableListOf("🧹 ${t(en, "Làm mới", "Refresh TV")} · $tenTivi",
            t(en, "RAM trống: ", "Free RAM: ") + "${gb(sd.ramCon, en)} / ${gb(sd.ramTong, en)}")
        listOfNotNull(sd.cpu?.let { t(en, "CPU bận: $it%", "CPU busy: $it%") }, sd.nhiet?.let { t(en, "Nhiệt độ chip: $it °C", "Chip temperature: $it °C") })
            .takeIf { it.isNotEmpty() }?.let { dong += it.joinToString(" · ") }
        if (sd.boNhoTong > 0) dong += t(en, "Bộ nhớ trống: ", "Free storage: ") + "${gb(sd.boNhoCon, en)} / ${gb(sd.boNhoTong, en)}"
        dong += t(en, "Đã bật: ", "Up for: ") + thoiGian(sd.msChay, en)
        val goiY = mutableListOf<String>()
        if (sd.ramTong > 0 && sd.ramCon * 100 < sd.ramTong * 15) goiY += t(en, "RAM còn ít: bấm 🧹 Đóng app nền.", "RAM is low: tap 🧹 Close background apps.")
        if ((sd.cpu ?: 0) >= 85) goiY += t(en, "CPU đang quá tải: có thể CH Play đang cập nhật app, đợi vài phút.", "The CPU is overloaded: the Play Store may be updating apps; wait a few minutes.")
        if ((sd.nhiet ?: 0) >= 80) goiY += t(en, "Chip đang nóng (80 °C trở lên): để tivi thoáng.", "The chip is hot (80 °C+): give the TV some air.")
        if (boNhoGanDay(sd.boNhoTong, sd.boNhoCon)) goiY += t(en, "Bộ nhớ gần đầy: bấm 🗑 Dọn cache.", "Storage is nearly full: tap 🗑 Clear cache.")
        if (sd.msChay >= 7 * 86_400_000L) goiY += t(en, "Tivi chạy liên tục ${sd.msChay / 86_400_000} ngày: nên khởi động lại (tắt bằng remote chỉ là chờ).",
            "The TV has run for ${sd.msChay / 86_400_000} days straight: a restart helps (the remote's power button only puts it in standby).")
        if (goiY.isEmpty()) goiY += t(en, "Tivi đang ổn.", "The TV looks fine.")
        return (dong + goiY.map { "• $it" }).joinToString("\n")
    }
}
