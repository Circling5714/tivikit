package vn.tivikit.dulieu

/**
 * Cài đặt nhanh tivi (25/10/2026): vài cài đặt hệ thống hay cần chỉnh mà menu tivi giấu sâu hoặc không có — tốc độ hiệu
 * ứng chuyển màn (trong Tuỳ chọn nhà phát triển), tự tắt khi không bấm remote (Google TV mặc định 4 giờ), lúc bật màn
 * hình chờ, tiếng bấm remote. Ghi bằng `settings put`; "Mặc định" là `settings delete` (tivi dùng lại giá trị gốc).
 */
object CaiDatNhanh {
    /** Một khoá settings: [ns] global / secure / system. */
    data class Khoa(val ns: String, val ten: String)

    val HIEU_UNG = listOf(Khoa("global", "window_animation_scale"), Khoa("global", "transition_animation_scale"), Khoa("global", "animator_duration_scale"))
    /** Tự tắt khi không bấm remote (ms; 0 trở xuống = không tự tắt), Android 10+. */
    val TU_TAT = Khoa("secure", "attentive_timeout")
    /** Không bấm remote bao lâu thì bật màn hình chờ (ms). */
    val MAN_CHO = Khoa("system", "screen_off_timeout")
    val TIENG_BAM = Khoa("system", "sound_effects_enabled")
    val TAT_CA = HIEU_UNG + TU_TAT + MAN_CHO + TIENG_BAM

    /** Một lệnh đọc mọi khoá, các giá trị cách nhau bằng dòng "@@" theo thứ tự TAT_CA. */
    fun lenhDoc(): String = TAT_CA.joinToString("; echo @@; ") { "settings get ${it.ns} ${it.ten}" }

    /** Khoá → giá trị; null = chưa đặt (tivi dùng mặc định của hãng). */
    fun tuDoc(out: String): Map<Khoa, String?> {
        val p = out.split("@@").map { it.trim() }
        return TAT_CA.mapIndexed { i, k -> k to p.getOrNull(i)?.takeIf { it.isNotEmpty() && it != "null" && GIA_TRI.matches(it) } }.toMap()
    }

    /** Giá trị được ghi: số nguyên / thập phân (VD 0.5, -1, 14400000). */
    val GIA_TRI = Regex("""^-?\d+(\.\d+)?$""")

    enum class HieuUng { MAC_DINH, NHANH, TAT, KHAC }

    fun hieuUng(gt: Map<Khoa, String?>): HieuUng {
        val v = HIEU_UNG.map { gt[it]?.toFloatOrNull() ?: 1f }
        return when {
            v.all { it == 1f } -> HieuUng.MAC_DINH
            v.all { it == 0.5f } -> HieuUng.NHANH
            v.all { it == 0f } -> HieuUng.TAT
            else -> HieuUng.KHAC
        }
    }

    /** Giá trị ghi cho cả ba khoá hiệu ứng; null = xoá (mặc định 1×). */
    fun giaTriHieuUng(h: HieuUng): String? = when (h) { HieuUng.NHANH -> "0.5"; HieuUng.TAT -> "0"; else -> null }

    const val GIO = 3_600_000L
    const val PHUT = 60_000L

    /** Lựa chọn tự tắt: null = mặc định, -1 = không tự tắt. */
    val TU_TAT_CHON: List<Long?> = listOf(null, 4 * GIO, 8 * GIO, 12 * GIO, -1L)
    val MAN_CHO_CHON: List<Long?> = listOf(null, 5 * PHUT, 15 * PHUT, 30 * PHUT, GIO)

    /** Lựa chọn đang dùng trong [chon] theo giá trị trên tivi; giá trị lạ (đặt từ menu của hãng) thì không ô nào sáng. */
    fun dangChon(gt: String?, chon: List<Long?>): Long? {
        val v = gt?.toLongOrNull() ?: return null
        return if (chon.contains(-1L) && v <= 0) -1L else chon.firstOrNull { it == v } ?: KHONG_KHOP
    }

    /** Giá trị trên tivi không khớp ô nào. */
    const val KHONG_KHOP = Long.MIN_VALUE
}
