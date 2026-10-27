package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mã mở Khoá trẻ em (28/10/2026, thay PIN 4 số). Lúc cài, TiviKit tạo mã gốc, hiện QR để phụ huynh quét vào app xác thực
 * (Google Authenticator, Microsoft Authenticator, app Mật khẩu của iPhone…), rồi gửi mã gốc lên app khoá trên tivi; mã
 * nhập sau đó do tivi kiểm tra (khoa/Otp.kt, cùng chuẩn RFC 6238: HMAC-SHA1, 6 số, 30 giây). TiviKit không giữ lại mã
 * gốc: trẻ cầm điện thoại của bố mẹ cũng không xem được mã. Mã dự phòng 8 số dùng một lần, tivi chỉ giữ dạng băm.
 */
object MaOtp {
    const val SO_CHU_SO = 6
    const val SO_CHU_SO_DU_PHONG = 8
    const val SO_DU_PHONG = 10
    private const val BANG = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val ngauNhien = SecureRandom()

    /** Mã gốc mới: 20 byte ngẫu nhiên (160 bit, như Google Authenticator), viết base32. */
    fun taoKhoa(): String = base32(ByteArray(20).also { ngauNhien.nextBytes(it) })

    /** Base32 RFC 4648, không dấu '='. */
    fun base32(b: ByteArray): String {
        val sb = StringBuilder()
        var dem = 0
        var bit = 0
        for (x in b) {
            dem = ((dem shl 8) or (x.toInt() and 0xff)) and 0xffff
            bit += 8
            while (bit >= 5) { bit -= 5; sb.append(BANG[(dem shr bit) and 31]) }
        }
        if (bit > 0) sb.append(BANG[(dem shl (5 - bit)) and 31])
        return sb.toString()
    }

    /** Giải base32 (bỏ khoảng trắng, gạch, '='; chữ thường cũng được); null nếu rỗng hoặc có ký tự lạ. */
    fun giaiBase32(s: String): ByteArray? {
        val sach = s.uppercase().filter { it !in " -=\t\r\n" }
        if (sach.isEmpty()) return null
        val ra = java.io.ByteArrayOutputStream()
        var dem = 0
        var bit = 0
        for (ch in sach) {
            val v = BANG.indexOf(ch)
            if (v < 0) return null
            dem = ((dem shl 5) or v) and 0xffff
            bit += 5
            if (bit >= 8) { bit -= 8; ra.write((dem shr bit) and 0xff) }
        }
        return ra.toByteArray()
    }

    /** Mã gốc phụ huynh dán vào (lấy từ tivi khác trong nhà): viết hoa, bỏ khoảng trắng; null nếu không phải base32 đủ dài. */
    fun chuanKhoa(s: String): String? = s.uppercase().filter { it !in " -=\t\r\n" }.takeIf { (giaiBase32(it)?.size ?: 0) >= 10 }

    /** Mã 6 số của [khoa] ở bước thời gian [buoc] (HOTP, RFC 4226). */
    fun ma(khoa: ByteArray, buoc: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(khoa, "HmacSHA1")) }
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(buoc).array())
        val o = h[h.size - 1].toInt() and 0xf
        val so = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or
            ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        return (so % 1_000_000).toString().padStart(SO_CHU_SO, '0')
    }

    /** [nhap] là mã app xác thực đang hiện cho mã gốc [khoa] lúc [ms] (lệch ±30 giây): phụ huynh đã quét đúng. */
    fun dung(khoa: String, nhap: String, ms: Long = System.currentTimeMillis()): Boolean {
        val k = giaiBase32(khoa) ?: return false
        if (nhap.length != SO_CHU_SO || nhap.any { it !in '0'..'9' }) return false
        val b = Math.floorDiv(ms / 1000, 30L)
        return (b - 1..b + 1).any { ma(k, it) == nhap }
    }

    /** Mã gốc chia nhóm 4 chữ cho dễ chép tay. */
    fun nhom(khoa: String): String = khoa.chunked(4).joinToString(" ")

    /** Liên kết otpauth:// trong QR, cũng mở được app xác thực cài trên chính điện thoại này. */
    fun lienKet(khoa: String, nhan: String = "Khoá trẻ em"): String =
        "otpauth://totp/TiviKit:" + URLEncoder.encode(nhan, "UTF-8").replace("+", "%20") +
            "?secret=$khoa&issuer=TiviKit&algorithm=SHA1&digits=6&period=30"

    /** [SO_DU_PHONG] mã dự phòng 8 số, khác nhau. */
    fun taoDuPhong(): List<String> {
        val ds = LinkedHashSet<String>()
        while (ds.size < SO_DU_PHONG) ds += "%08d".format(ngauNhien.nextInt(100_000_000))
        return ds.toList()
    }

    /** Mã dự phòng viết "1234 5678". */
    fun chuDuPhong(ma: String): String = ma.chunked(4).joinToString(" ")

    /** Dạng băm SHA-256 của muối + mã, như khoa/Otp.bam. */
    fun bam(muoi: String, ma: String): String =
        MessageDigest.getInstance("SHA-256").digest((muoi + ma).toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Thêm mã vào lệnh gửi tivi [o]: "otp" = mã gốc ([khoa] null = giữ mã gốc đang có), "duPhong" = {muoi, bam: [...]} —
     * tivi chỉ nhận dạng băm của mã dự phòng.
     */
    fun ghiVao(o: JSONObject, khoa: String?, duPhong: List<String>): JSONObject {
        val muoi = ByteArray(8).also { ngauNhien.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        if (khoa != null) o.put("otp", khoa)
        return o.put("duPhong", JSONObject().put("muoi", muoi).put("bam", JSONArray(duPhong.map { bam(muoi, it) })))
    }
}
