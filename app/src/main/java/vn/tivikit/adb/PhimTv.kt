package vn.tivikit.adb

import java.text.Normalizer

/**
 * Phím remote và chữ gửi lên tivi bằng lệnh `input` qua ADB (25/10/2026, điều khiển tivi từ điện thoại).
 * Từ Android 11 `input` là `cmd input` (nhanh); Android 9–10 mỗi lệnh khởi động một máy ảo Java (~0,5 giây), nên
 * phím bấm dồn được gửi chung một lệnh.
 */
object PhimTv {
    const val LEN = 19
    const val XUONG = 20
    const val TRAI = 21
    const val PHAI = 22
    const val OK = 23
    const val QUAY_LAI = 4
    const val HOME = 3
    const val MENU = 82
    const val CAI_DAT = 176
    const val TANG_AM = 24
    const val GIAM_AM = 25
    const val TAT_TIENG = 164
    const val PHAT_DUNG = 85
    const val TUA_LUI = 89
    const val TUA_TOI = 90
    const val NGUON_VAO = 178
    const val KENH_LEN = 166
    const val KENH_XUONG = 167
    const val NGUON = 26
    const val ENTER = 66
    const val XOA = 67

    /** Một lệnh cho nhiều phím (`input keyevent` nhận nhiều mã); [giu] = nhấn giữ (`--longpress`), chỉ một phím. */
    fun lenhPhim(ma: List<Int>, giu: Boolean = false): String =
        "input keyevent " + (if (giu) "--longpress ${ma.first()}" else ma.joinToString(" "))

    /**
     * Bỏ dấu tiếng Việt và mọi ký tự `input text` không gõ được: lệnh này chỉ dựng được phím của bàn phím ảo tiếng
     * Anh, gặp chữ có dấu thì báo lỗi và không gõ gì.
     */
    fun boDau(s: String): String =
        Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .filter { it in ' '..'~' }

    /**
     * Lệnh `input text` gõ đúng [chu] (đã bỏ dấu): khoảng trắng viết %s (input đọc %s là dấu cách), cả chuỗi bọc nháy
     * đơn cho shell, nháy đơn bên trong viết '\''. Chuỗi dài chia đoạn 200 ký tự (một số tivi bỏ sót khi quá dài).
     */
    fun lenhChu(chu: String): List<String> = boDau(chu).chunked(200).map { doan ->
        "input text '" + doan.replace("'", "'\\''").replace(" ", "%s") + "'"
    }
}
