package vn.tivikit.khoa

import java.security.SecureRandom

/**
 * Nhập mã mở khoá (28/10/2026): trên màn khoá của tivi hay từ TiviKit (content call … xac-thuc), dùng chung một bộ đếm
 * sai. Sai [SAI_TOI_DA] lần liền thì phải chờ, lần chờ sau lâu hơn (Luat.phutCho). Đúng thì xoá đếm và huỷ yêu cầu quên
 * mã. TiviKit nhập đúng thì được một phiên [PHIEN_MS]: đổi cấu hình phải kèm phiên, nên trẻ cầm điện thoại có TiviKit của
 * bố mẹ cũng không tắt được khoá nếu không có mã.
 */
object XacThuc {
    const val SAI_TOI_DA = 5
    private const val PHIEN_MS = 15 * 60_000L
    /** Quên mã: TiviKit gửi yêu cầu, sau chừng này không cần mã nữa (tivi báo suốt lúc chờ, ai nhập đúng mã là huỷ). */
    const val CHO_QUEN_MS = 24 * 3_600_000L

    sealed interface KetQua
    object Dung : KetQua
    class Sai(val conLan: Int) : KetQua
    class Cho(val ms: Long) : KetQua

    fun choCon(kho: Kho, bay: Long = System.currentTimeMillis()): Long = (kho.chanDen - bay).coerceAtLeast(0)

    /** Thử [nhap], không đếm sai (màn khoá thử 6 số đầu, phòng khi đang gõ mã dự phòng 8 số). Đúng thì ghi mã đã dùng. */
    @Synchronized fun dung(kho: Kho, nhap: String, bay: Long = System.currentTimeMillis()): Boolean {
        if (choCon(kho, bay) > 0) return false
        val sau = kho.maMo?.dung(nhap, bay) ?: return false
        kho.maMo = sau
        kho.sai = 0; kho.lanCho = 0; kho.yeuCauGo = 0
        return true
    }

    /** Ghi một lần sai; đủ [SAI_TOI_DA] lần thì bắt chờ. */
    @Synchronized fun ghiSai(kho: Kho, bay: Long = System.currentTimeMillis()): KetQua {
        kho.sai = kho.sai + 1
        if (kho.sai < SAI_TOI_DA) return Sai(SAI_TOI_DA - kho.sai)
        val ms = Luat.phutCho(kho.lanCho) * 60_000L
        kho.sai = 0; kho.lanCho = kho.lanCho + 1; kho.chanDen = bay + ms
        return Cho(ms)
    }

    @Synchronized fun thu(kho: Kho, nhap: String, bay: Long = System.currentTimeMillis()): KetQua {
        val cho = choCon(kho, bay)
        return when {
            cho > 0 -> Cho(cho)
            dung(kho, nhap, bay) -> Dung
            else -> ghiSai(kho, bay)
        }
    }

    /** Mở phiên cho TiviKit sau khi nhập đúng mã; tivi chỉ giữ dạng băm của phiên. */
    fun moPhien(kho: Kho, bay: Long = System.currentTimeMillis()): String {
        val p = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        kho.phien = Otp.bam("phien", p); kho.phienHet = bay + PHIEN_MS
        return p
    }

    /** Đã quá [CHO_QUEN_MS] từ lúc TiviKit báo quên mã mà không ai nhập đúng mã để huỷ. */
    fun goDuoc(kho: Kho, bay: Long = System.currentTimeMillis()): Boolean = kho.yeuCauGo in 1..(bay - CHO_QUEN_MS)

    /** Được đổi cấu hình / mã không: tivi chưa có mã, có phiên còn hạn, hoặc quên mã đã quá 24 giờ. */
    fun duocDoi(kho: Kho, phien: String?, bay: Long = System.currentTimeMillis()): Boolean =
        kho.maMo == null || goDuoc(kho, bay) ||
            (!phien.isNullOrBlank() && kho.phienHet > bay && kho.phien == Otp.bam("phien", phien))
}
