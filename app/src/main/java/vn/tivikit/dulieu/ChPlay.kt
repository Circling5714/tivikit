package vn.tivikit.dulieu

/**
 * CH Play và tài khoản Google trên tivi (26/10/2026, rút từ box Tanix TX3 Mini): CH Play hiện "Đã xảy ra lỗi — kiểm tra
 * Wi-Fi" cả khi mạng tốt, mà lỗi thật nằm trong nhật ký: Google trả BAD_AUTHENTICATION cho tài khoản đang lưu (phải xoá
 * tài khoản rồi thêm lại). Đọc thêm gói Google bị tắt, số tài khoản, giờ tivi lệch (hỏng kết nối bảo mật) và ROM tự dựng
 * (test-keys: Google không chứng nhận, đăng nhập lại có thể bị chặn).
 */
object ChPlay {
    /** Ba gói CH Play cần; tắt một gói là CH Play hỏng. */
    val GOI_CAN = listOf("com.android.vending", "com.google.android.gms", "com.google.android.gsf")

    /** Lỗi đăng nhập trong nhật ký cũ hơn chừng này (giây) thì coi là đã qua (có thể đã đăng nhập lại). */
    const val LOI_GAN_DAY = 30 * 60L

    /** Giờ tivi lệch quá chừng này (giây) so với điện thoại thì báo. */
    const val LECH_TOI_DA = 5 * 60L

    /**
     * Một lệnh shell, các đoạn cách nhau bằng dòng "@@": có CH Play không, gói cần mà đang tắt, số tài khoản Google,
     * dòng BAD_AUTHENTICATION cuối trong nhật ký (logcat -v time), giờ tivi ("MM-dd HH:mm:ss" và giây Unix), ro.build.tags.
     */
    const val LENH = "pm path com.android.vending >/dev/null 2>&1 && echo co || echo khong; echo @@; " +
        "pm list packages -d 2>/dev/null; echo @@; " +
        "dumpsys account 2>/dev/null | grep -cE 'Account \\{name=.*, type=com\\.google\\}'; echo @@; " +
        "logcat -d -v time 2>/dev/null | grep -E 'BAD_AUTHENTICATION|BadAuthentication' | tail -n 1; echo @@; " +
        "date '+%m-%d %H:%M:%S'; date +%s; echo @@; getprop ro.build.tags"

    /**
     * [giayTuLoi]: lỗi đăng nhập cuối cách đây bao nhiêu giây (null = không có trong nhật ký); [lechGio]: giờ tivi trừ
     * giờ điện thoại (giây).
     */
    data class TrangThai(
        val coChPlay: Boolean, val goiTat: List<String>, val soTaiKhoan: Int,
        val giayTuLoi: Long?, val lechGio: Long?, val testKeys: Boolean,
    )

    enum class VanDe { KHONG_CO_CH_PLAY, GOI_GOOGLE_TAT, LECH_GIO, CAN_DANG_NHAP_LAI, CHUA_CO_TAI_KHOAN, ROM_CHUA_CHUNG_NHAN }

    /** [gioDienThoai]: giây Unix trên điện thoại lúc chạy [LENH]. */
    fun tuKetQua(out: String, gioDienThoai: Long): TrangThai? {
        val p = out.replace("\r", "").split("@@").map { it.trim() }
        if (p.size < 6) return null
        val gio = p[4].lines()
        return TrangThai(
            coChPlay = p[0] == "co",
            goiTat = tuGoiTat(p[1]),
            soTaiKhoan = p[2].toIntOrNull() ?: 0,
            giayTuLoi = p[3].takeIf { it.isNotEmpty() }?.let { giayTuLoiCuoi(it, gio.getOrElse(0) { "" }) },
            lechGio = gio.getOrNull(1)?.trim()?.toLongOrNull()?.let { it - gioDienThoai },
            testKeys = p[5].contains("test-keys"),
        )
    }

    /** Gói trong [GOI_CAN] có trong danh sách `pm list packages -d`. */
    fun tuGoiTat(s: String): List<String> =
        s.lines().map { it.trim().removePrefix("package:") }.filter { it in GOI_CAN }.distinct()

    /** Giây từ dòng nhật ký [dong] ("10-26 10:59:04.553 E/…") tới [bayGio] ("10-26 11:05:04"); qua năm mới vẫn đúng. */
    fun giayTuLoiCuoi(dong: String, bayGio: String): Long? {
        val a = moc(dong) ?: return null
        val b = moc(bayGio) ?: return null
        val d = b - a
        return if (d >= 0) d else d + 366 * 86_400L
    }

    /** "MM-dd HH:mm:ss" ở đầu chuỗi → giây tính từ đầu một năm nhuận (chỉ để trừ hai mốc). */
    private fun moc(s: String): Long? {
        val m = Regex("""^(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})""").find(s.trim()) ?: return null
        val (thang, ngay, gio, phut, giay) = m.destructured.toList().map { it.toInt() }
        if (thang !in 1..12 || ngay !in 1..31) return null
        val ngayTruoc = intArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335)[thang - 1] + ngay - 1
        return ngayTruoc * 86_400L + gio * 3600L + phut * 60L + giay
    }

    /** Vấn đề theo thứ tự nên sửa trước; rỗng = CH Play ổn. */
    fun danhGia(tt: TrangThai): List<VanDe> {
        if (!tt.coChPlay) return listOf(VanDe.KHONG_CO_CH_PLAY)
        val ds = mutableListOf<VanDe>()
        if (tt.goiTat.isNotEmpty()) ds += VanDe.GOI_GOOGLE_TAT
        if (tt.lechGio != null && kotlin.math.abs(tt.lechGio) > LECH_TOI_DA) ds += VanDe.LECH_GIO
        if (tt.giayTuLoi != null && tt.giayTuLoi <= LOI_GAN_DAY) ds += VanDe.CAN_DANG_NHAP_LAI
        if (tt.soTaiKhoan == 0) ds += VanDe.CHUA_CO_TAI_KHOAN
        // Chỉ nhắc khi có việc phải đăng nhập: ROM tự dựng vẫn chạy CH Play bình thường nếu tài khoản không bị đụng tới.
        if (tt.testKeys && (VanDe.CAN_DANG_NHAP_LAI in ds || VanDe.CHUA_CO_TAI_KHOAN in ds)) ds += VanDe.ROM_CHUA_CHUNG_NHAN
        return ds
    }
}
