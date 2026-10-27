package vn.tivikit.dulieu

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaOtpTest {
    /** Mã gốc mẫu của RFC 6238 (SHA-1): chữ "12345678901234567890" viết base32. */
    private val KHOA = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test fun base32_haiChieu() {
        assertEquals(KHOA, MaOtp.base32("12345678901234567890".toByteArray()))
        assertEquals("MZXW6YQ", MaOtp.base32("foob".toByteArray()))           // RFC 4648, bỏ dấu '='
        assertArrayEquals("foob".toByteArray(), MaOtp.giaiBase32("mzxw 6yq="))
        val k = MaOtp.taoKhoa()
        assertEquals(32, k.length)
        assertEquals(20, MaOtp.giaiBase32(k)!!.size)
        assertEquals(k, MaOtp.base32(MaOtp.giaiBase32(k)!!))
    }

    /** Cùng bộ thử RFC 6238 với app khoá (khoa/OtpTest): hai bên tính ra một mã. */
    @Test fun maTheoRfc6238() {
        val k = MaOtp.giaiBase32(KHOA)!!
        mapOf(59L to "287082", 1111111109L to "081804", 1234567890L to "005924", 20000000000L to "353130").forEach { (giay, ma) ->
            assertEquals("T=$giay", ma, MaOtp.ma(k, giay / 30))
        }
        assertTrue(MaOtp.dung(KHOA, "005924", 1234567890_000L))
        assertTrue(MaOtp.dung(KHOA, "005924", 1234567890_000L + 30_000))
        assertFalse(MaOtp.dung(KHOA, "005924", 1234567890_000L + 61_000))
        assertFalse(MaOtp.dung(KHOA, "5924", 1234567890_000L))
    }

    @Test fun chuanKhoa() {
        assertEquals(KHOA, MaOtp.chuanKhoa("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"))
        assertEquals(KHOA, MaOtp.chuanKhoa(MaOtp.nhom(KHOA)))
        assertNull(MaOtp.chuanKhoa("GEZDGNBV"))     // ngắn quá
        assertNull(MaOtp.chuanKhoa("GEZDGNBVGY3TQOJ1"))
    }

    @Test fun lienKet() {
        assertEquals("otpauth://totp/TiviKit:Kho%C3%A1%20tr%E1%BA%BB%20em?secret=$KHOA&issuer=TiviKit&algorithm=SHA1&digits=6&period=30",
            MaOtp.lienKet(KHOA))
    }

    @Test fun maDuPhong_vaLenhGuiTivi() {
        val ds = MaOtp.taoDuPhong()
        assertEquals(MaOtp.SO_DU_PHONG, ds.toSet().size)
        assertTrue(ds.all { it.length == 8 && it.all(Char::isDigit) })
        assertEquals("1234 5678", MaOtp.chuDuPhong("12345678"))
        val o = MaOtp.ghiVao(JSONObject().put("bat", true), KHOA, ds)
        assertEquals(KHOA, o.getString("otp"))
        val dp = o.getJSONObject("duPhong")
        val muoi = dp.getString("muoi")
        assertEquals(MaOtp.bam(muoi, ds[0]), dp.getJSONArray("bam").getString(0))
        // Tivi chỉ nhận dạng băm, không có mã dự phòng gốc trong lệnh.
        assertFalse(o.toString().contains(ds[0]))
        assertFalse(MaOtp.ghiVao(JSONObject(), null, ds).has("otp"))
    }
}
