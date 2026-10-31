package vn.tivikit.hengio

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Chữ trên màn Hẹn giờ của tivi (31/10/2026): tiếng Việt khi tivi đặt tiếng Việt, không thì tiếng Anh (như Khoá trẻ em).
 * Nhật ký nói bằng lời giống TiviKit trên điện thoại (dulieu/HenGio.moTa).
 */
internal object Chu {
    val vi: Boolean get() = Locale.getDefault().language == "vi"

    fun t(v: String, e: String) = if (vi) v else e

    private val THU = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    private val THU_EN = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val THU_DAI = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")
    private val THU_DAI_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** Thứ ngắn (1 = Thứ Hai): "T2", "CN". */
    fun thu(d: Int) = t(THU[d - 1], THU_EN[d - 1])

    fun thuDai(d: Int) = t(THU_DAI[d - 1], THU_DAI_EN[d - 1])

    /** "Mọi ngày", "T2–T6", "T7, CN" hay "T2 T4 T6". */
    fun ngay(ds: Set<Int>): String = when (ds) {
        (1..7).toSet() -> t("Mọi ngày", "Every day")
        (1..5).toSet() -> t("T2–T6", "Mon–Fri")
        setOf(6, 7) -> t("T7, CN", "Sat, Sun")
        else -> ds.sorted().joinToString(" ") { thu(it) }
    }

    fun loai(loai: String) = when (loai) {
        Viec.BAT -> t("Bật tivi", "Turn on")
        Viec.TAT -> t("Tắt tivi", "Turn off")
        else -> t("Mở app / kênh", "Open an app")
    }

    /** "Bật tivi", "Bật tivi rồi mở VTV1", "Tắt tivi", "Mở SmartTube"; [ten] đổi tên gói ra tên app. */
    fun viec(loai: String, goi: String?, ten: (String) -> String): String = when (loai) {
        Viec.BAT -> t("Bật tivi", "Turn on") + (goi?.let { t(" rồi mở ", ", then open ") + ten(it) } ?: "")
        Viec.TAT -> t("Tắt tivi", "Turn off")
        else -> t("Mở ", "Open ") + (goi?.let(ten) ?: "?")
    }

    fun gio(luc: Long, zone: ZoneId): String {
        val z = Instant.ofEpochMilli(luc).atZone(zone)
        return "%02d:%02d".format(Locale.ROOT, z.hour, z.minute)
    }

    /** "hôm nay", "ngày mai" hay "Thứ Hai 02/11": ngày của [luc] so với [bay]. */
    fun ngayCua(luc: Long, bay: Long, zone: ZoneId): String {
        val d = Instant.ofEpochMilli(luc).atZone(zone).toLocalDate()
        val homNay = Instant.ofEpochMilli(bay).atZone(zone).toLocalDate()
        return when (d) {
            homNay -> t("hôm nay", "today")
            homNay.plusDays(1) -> t("ngày mai", "tomorrow")
            else -> thuDai(d.dayOfWeek.value) + " %02d/%02d".format(Locale.ROOT, d.dayOfMonth, d.monthValue)
        }
    }

    /** "30 phút", "1 giờ", "1 giờ 30 phút". */
    fun phut(p: Int): String {
        val g = p / 60; val m = p % 60
        return when {
            g == 0 -> t("$m phút", "$m min")
            m == 0 -> t("$g giờ", "$g h")
            else -> t("$g giờ $m phút", "$g h $m min")
        }
    }

    /** Thời gian còn lại tới [ms] nữa, làm tròn lên phút: "còn 30 phút". */
    fun con(ms: Long): String = t("còn ", "") + phut(((ms + 59_999) / 60_000).coerceAtLeast(0).toInt()) + t("", " left")

    private val MA_SO = Regex("""^(hoan|hen)-(\d+)$""")

    /** Một dòng nhật ký nói bằng lời: "Bật tivi – tivi đang chờ, đã bật lên"; mã chưa biết thì hiện nguyên mã. */
    fun nhatKy(viec: String, kq: String, ten: (String) -> String): String {
        val v = when {
            viec == Viec.BAT -> t("Bật tivi", "Turn on")
            viec == "thu" -> t("Thử bật tivi", "Power-on test")
            viec == Viec.TAT -> t("Tắt tivi", "Turn off")
            viec.startsWith("${Viec.MO}:") -> t("Mở ", "Open ") + ten(viec.removePrefix("${Viec.MO}:"))
            viec == Viec.MO -> t("Mở app", "Open an app")
            else -> viec
        }
        val k = MA_SO.matchEntire(kq)?.destructured?.let { (loai, so) ->
            if (loai == "hoan") t("hoãn $so phút", "snoozed $so min") else t("hẹn tự bật sau $so giây", "set to come on in $so s")
        } ?: when (kq) {
            "danh-thuc" -> t("tivi đang chờ, đã bật lên", "the TV was in standby and came on")
            "dang-bat" -> if (viec == "thu") t("tivi vẫn đang bật, chưa tắt bằng remote", "the TV was still on, not turned off with the remote")
                else t("tivi đang bật sẵn", "the TV was already on")
            "ok" -> if (viec == Viec.TAT) t("đã tắt", "done") else t("đã mở", "opened")
            "mo-lai" -> t("mở lại vì launcher vừa đè lên", "reopened after the launcher came up on top")
            "khong-mo-duoc" -> t("không mở được (app đã gỡ hay đang tắt?)", "couldn't open it (removed or disabled?)")
            "tivi-dang-tat" -> t("bỏ qua vì tivi đang tắt", "skipped: the TV was off")
            "bao-truoc" -> t("đã báo trước 1 phút", "showed the 1-minute warning")
            "bao-tre" -> t("đã báo trễ, ngay trước giờ tắt", "showed the warning late, just before the time")
            "da-tat-san" -> t("tivi đã tắt sẵn", "the TV was already off")
            "dich-vu-tat" -> t("không tắt được vì dịch vụ Hẹn giờ đang tắt", "couldn't: the Timer service is off")
            "khong-tat-duoc" -> t("tivi không cho tắt", "the TV didn't allow it")
            "lo-gio" -> t("bỏ qua vì trễ quá 10 phút (tivi ngủ sâu hay vừa đổi giờ)", "skipped: over 10 minutes late (deep sleep or a clock change)")
            else -> kq
        }
        return "$v – $k"
    }
}
