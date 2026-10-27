package vn.tivikit.khoa

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpTest {
    /** Mã gốc mẫu của RFC 6238 (SHA-1): chữ "12345678901234567890" viết base32. */
    private val KHOA = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private val khoa = "12345678901234567890".toByteArray()

    @Test fun base32() {
        assertArrayEquals(khoa, Otp.giaiBase32(KHOA))
        assertArrayEquals(khoa, Otp.giaiBase32("gezd gnbv-gy3t qojq gezd gnbv gy3t qojq===="))
        assertNull(Otp.giaiBase32("GEZDG1"))   // 1 không có trong bảng base32
        assertNull(Otp.giaiBase32(" "))
    }

    /** Bộ thử của RFC 6238 (lấy 6 số cuối của mã 8 số). */
    @Test fun maTheoRfc6238() {
        mapOf(59L to "287082", 1111111109L to "081804", 1111111111L to "050471", 1234567890L to "005924",
            2000000000L to "279037", 20000000000L to "353130").forEach { (giay, ma) ->
            assertEquals("T=$giay", ma, Otp.ma(khoa, Otp.buoc(giay * 1000)))
        }
    }

    @Test fun kiemTra_lechMotBuoc_khongDungLai() {
        val ms = 1234567890_000L
        val b = Otp.buoc(ms)
        assertEquals(b, Otp.kiemTra(khoa, "005924", ms, 0))
        assertEquals(b, Otp.kiemTra(khoa, "005924", ms + 30_000, 0))     // giờ tivi nhanh 30 giây
        assertEquals(b, Otp.kiemTra(khoa, "005924", ms - 30_000, 0))     // giờ tivi chậm 30 giây
        assertNull(Otp.kiemTra(khoa, "005924", ms + 61_000, 0))          // quá một bước
        assertNull(Otp.kiemTra(khoa, "005924", ms, b))                   // mã đã dùng
        assertNull(Otp.kiemTra(khoa, "00592", ms, 0))
        assertNull(Otp.kiemTra(khoa, "00592a", ms, 0))
    }

    private val muoi = "m"
    private val duPhong = listOf("12345678", "87654321")
    private val ma = MaMo(KHOA, 0, muoi, duPhong.map { Otp.bam(muoi, it) })

    @Test fun maMo_otpVaMaDuPhongMoiMaMotLan() {
        val ms = 1234567890_000L
        val sau = ma.dung("005924", ms)!!
        assertEquals(Otp.buoc(ms), sau.daDung)
        assertNull(sau.dung("005924", ms))                                // cùng mã không mở lần hai
        val dp = sau.dung("12345678", ms)!!
        assertEquals(1, dp.duPhong.size)
        assertNull(dp.dung("12345678", ms))                              // mã dự phòng đã dùng
        assertTrue(dp.dung("87654321", ms)!!.duPhong.isEmpty())
        assertNull(ma.dung("1234567", ms))
        assertEquals(ma, MaMo.doc(ma.json()))
    }

    @Test fun maMo_tuLenh() {
        val bam = JSONArray(listOf(Otp.bam("s", "11112222")))
        // Cài lần đầu: mã gốc + mã dự phòng; lấy hai khoá đó ra khỏi lệnh.
        val o = JSONObject().put("bat", true).put("otp", "gezd gnbv gy3t qojq gezd gnbv gy3t qojq").put("duPhong", JSONObject().put("muoi", "s").put("bam", bam))
        val moi = MaMo.tuLenh(o, null)!!
        assertEquals(KHOA, moi.khoa); assertEquals(1, moi.duPhong.size)
        assertEquals(setOf("bat"), o.keys().asSequence().toSet())
        // Chỉ tạo mã dự phòng mới: giữ mã gốc và bước đã dùng.
        val cu = ma.copy(daDung = 7)
        val chiDp = MaMo.tuLenh(JSONObject().put("duPhong", JSONObject().put("muoi", "s").put("bam", bam)), cu)!!
        assertEquals(KHOA, chiDp.khoa); assertEquals(7, chiDp.daDung); assertEquals("s", chiDp.muoi)
        // Không đổi mã.
        assertNull(MaMo.tuLenh(JSONObject().put("bat", true), cu))
        // Mã gốc quá ngắn, băm sai dạng.
        assertThrows(IllegalArgumentException::class.java) { MaMo.tuLenh(JSONObject().put("otp", "GEZDGNBV"), null) }
        assertThrows(IllegalArgumentException::class.java) {
            MaMo.tuLenh(JSONObject().put("duPhong", JSONObject().put("muoi", "s").put("bam", JSONArray(listOf("abc")))), cu)
        }
        assertThrows(IllegalArgumentException::class.java) { MaMo.tuLenh(JSONObject().put("duPhong", JSONObject().put("muoi", "s").put("bam", bam)), null) }
    }
}
