package vn.tivikit.khoa

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mã mở khoá (28/10/2026), thay PIN 4 số: trẻ đứng cạnh nhìn bố mẹ bấm vài lần là thuộc PIN. Mã OTP theo thời gian chuẩn
 * RFC 6238 (HMAC-SHA1, 6 số, đổi mỗi 30 giây) như Google Authenticator, nên phụ huynh dùng app xác thực nào cũng được,
 * cả app Mật khẩu trên iPhone; mỗi mã chỉ mở được một lần. Mã dự phòng 8 số (lúc không có điện thoại, hay giờ tivi
 * sai) dùng một lần rồi bỏ. Không đụng Android để test được trên máy tính.
 */
object Otp {
    const val BUOC_GIAY = 30L
    const val SO_CHU_SO = 6
    const val SO_CHU_SO_DU_PHONG = 8
    private const val BANG = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Giải base32 (RFC 4648; bỏ khoảng trắng, gạch, '='; chữ thường cũng được); null nếu rỗng hoặc có ký tự lạ. */
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

    /** Mã [SO_CHU_SO] số của [khoa] ở bước thời gian [buoc] (HOTP, RFC 4226). */
    fun ma(khoa: ByteArray, buoc: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(khoa, "HmacSHA1")) }
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(buoc).array())
        val o = h[h.size - 1].toInt() and 0xf
        val so = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or
            ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        return (so % 1_000_000).toString().padStart(SO_CHU_SO, '0')
    }

    /** Bước thời gian (30 giây) chứa thời điểm [ms]. */
    fun buoc(ms: Long): Long = Math.floorDiv(ms / 1000, BUOC_GIAY)

    /**
     * Bước của [nhap] nếu là mã OTP đúng và chưa dùng (bước phải sau [daDung]); null nếu sai hoặc dùng lại. Nhận lệch một
     * bước (±30 giây): giờ tivi lệch chút, hay bấm remote chậm qua lúc mã đổi.
     */
    fun kiemTra(khoa: ByteArray, nhap: String, ms: Long, daDung: Long): Long? {
        if (nhap.length != SO_CHU_SO || nhap.any { it !in '0'..'9' }) return null
        val b = buoc(ms)
        return (b - 1..b + 1).firstOrNull { it > daDung && ma(khoa, it) == nhap }
    }

    /** Dạng băm (SHA-256 của muối + mã): tivi chỉ giữ dạng này của mã dự phòng và phiên của TiviKit. */
    fun bam(muoi: String, ma: String): String =
        MessageDigest.getInstance("SHA-256").digest((muoi + ma).toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * Mã mở khoá lưu trên tivi: [khoa] = mã gốc OTP (base32), [daDung] = bước OTP dùng gần nhất (một mã không mở được hai
 * lần), [muoi] + [duPhong] = dạng băm của các mã dự phòng chưa dùng.
 */
data class MaMo(val khoa: String, val daDung: Long = 0, val muoi: String = "", val duPhong: List<String> = emptyList()) {
    /** [nhap] đúng thì trả trạng thái sau khi dùng nó (ghi bước OTP / bỏ mã dự phòng vừa dùng); sai thì null. */
    fun dung(nhap: String, ms: Long): MaMo? = when (nhap.length) {
        Otp.SO_CHU_SO -> Otp.giaiBase32(khoa)?.let { Otp.kiemTra(it, nhap, ms, daDung) }?.let { copy(daDung = it) }
        Otp.SO_CHU_SO_DU_PHONG -> if (nhap.all { it in '0'..'9' }) Otp.bam(muoi, nhap).takeIf { it in duPhong }?.let { copy(duPhong = duPhong - it) } else null
        else -> null
    }

    fun json(): String = JSONObject().put("khoa", khoa).put("daDung", daDung).put("muoi", muoi).put("duPhong", JSONArray(duPhong)).toString()

    companion object {
        /** Mã gốc phải đủ dài: 10 byte (80 bit) trở lên, như app xác thực đòi. */
        private const val KHOA_TOI_THIEU = 10
        private val BAM = Regex("^[0-9a-f]{64}$")

        fun doc(s: String?): MaMo? = runCatching {
            val o = JSONObject(s ?: return null)
            val ds = o.optJSONArray("duPhong") ?: JSONArray()
            MaMo(o.getString("khoa"), o.optLong("daDung"), o.optString("muoi"), (0 until ds.length()).map { ds.getString(it) })
        }.getOrNull()

        /**
         * Mã mới TiviKit gửi trong lệnh [o] — "otp" = mã gốc base32, "duPhong" = {muoi, bam: [...]} — gộp vào mã đang có
         * [cu]. Lấy hai khoá đó ra khỏi [o]. null = lệnh không đổi mã; mã gốc ngắn / băm sai dạng thì báo lỗi.
         */
        fun tuLenh(o: JSONObject, cu: MaMo?): MaMo? {
            val otp = o.optString("otp").takeIf { it.isNotBlank() }
            val dp = o.optJSONObject("duPhong")
            o.remove("otp"); o.remove("duPhong")
            if (otp == null && dp == null) return null
            if (otp != null) require((Otp.giaiBase32(otp)?.size ?: 0) >= KHOA_TOI_THIEU) { "otp" }
            val khoa = otp?.uppercase()?.filter { it.isLetterOrDigit() } ?: cu?.khoa ?: throw IllegalArgumentException("otp")
            val bam = dp?.optJSONArray("bam")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            if (dp != null) require(bam != null && bam.all { BAM.matches(it) } && dp.optString("muoi").isNotBlank()) { "duPhong" }
            return MaMo(
                khoa = khoa,
                // Mã gốc mới thì bước đã dùng của mã cũ không còn nghĩa; giữ mã gốc thì giữ luôn để mã vừa dùng không dùng lại.
                daDung = if (otp != null) 0 else cu?.daDung ?: 0,
                muoi = if (dp != null) dp.optString("muoi") else cu?.muoi.orEmpty(),
                duPhong = bam ?: cu?.duPhong.orEmpty(),
            )
        }
    }
}
