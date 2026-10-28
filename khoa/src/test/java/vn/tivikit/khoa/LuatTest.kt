package vn.tivikit.khoa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LuatTest {
    private fun luc(y: Int, mo: Int, d: Int, h: Int, mi: Int) = Calendar.getInstance().apply { clear(); set(y, mo - 1, d, h, mi) }
    private fun ch(vararg k: Khung, bat: Boolean = true) = CauHinh(bat, k.toList(), coMa = true)
    private val CA_TUAN = (1..7).toSet()

    @Test fun thuHai_laMot() { assertEquals(1, Luat.thu(luc(2026, 10, 19, 10, 0))); assertEquals(7, Luat.thu(luc(2026, 10, 25, 10, 0))) }

    @Test fun quaDem() {
        val c = ch(Khung(Luat.phut("21:00"), Luat.phut("06:00"), CA_TUAN))
        assertTrue(Luat.trongGio(c, luc(2026, 10, 23, 22, 30)))
        assertTrue(Luat.trongGio(c, luc(2026, 10, 24, 5, 59)))
        assertFalse(Luat.trongGio(c, luc(2026, 10, 24, 6, 0)))
        assertFalse(Luat.trongGio(c, luc(2026, 10, 23, 20, 59)))
        val het = Luat.hetKhoaLuc(c, luc(2026, 10, 23, 22, 30))!!
        assertEquals(24, het.get(Calendar.DAY_OF_MONTH)); assertEquals(6, het.get(Calendar.HOUR_OF_DAY)); assertEquals(0, het.get(Calendar.MINUTE))
    }

    @Test fun chiNgayHoc_quaDemTinhTheoNgayBatDau() {
        // Khoá tối thứ Hai–Thứ Năm 21:00–06:00: sáng thứ Bảy (đêm thứ Sáu không khoá) phải mở.
        val c = ch(Khung(Luat.phut("21:00"), Luat.phut("06:00"), setOf(1, 2, 3, 4)))
        assertTrue(Luat.trongGio(c, luc(2026, 10, 23, 5, 0)))   // Thứ Sáu 05:00, từ tối Thứ Năm
        assertFalse(Luat.trongGio(c, luc(2026, 10, 23, 22, 0)))  // Thứ Sáu 22:00
        assertFalse(Luat.trongGio(c, luc(2026, 10, 24, 5, 0)))   // Thứ Bảy 05:00
    }

    @Test fun haiKhung_noiTiep() {
        val c = ch(Khung(Luat.phut("14:00"), Luat.phut("16:30"), CA_TUAN), Khung(Luat.phut("16:30"), Luat.phut("17:00"), CA_TUAN))
        assertEquals(17, Luat.hetKhoaLuc(c, luc(2026, 10, 23, 15, 0))!!.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun tatHoacChuaCoMa_khongKhoa() {
        val k = Khung(0, 0, CA_TUAN)
        assertFalse(Luat.trongGio(ch(k, bat = false), luc(2026, 10, 23, 10, 0)))
        assertFalse(Luat.trongGio(CauHinh(true, listOf(k)), luc(2026, 10, 23, 10, 0)))
        assertNull(Luat.hetKhoaLuc(ch(k, bat = false), luc(2026, 10, 23, 10, 0)))
    }

    @Test fun saiNhieuLan_choLauDan() {
        assertEquals(listOf(1, 5, 15, 60, 60, 60), (0..5).map { Luat.phutCho(it) })
    }

    @Test fun docJson() {
        // Cấu hình của bản 1.1 còn muoi / pinBam: đọc được, PIN bỏ qua (chưa có mã OTP thì không khoá).
        val c = Luat.docJson("""{"bat":true,"muoi":"m","pinBam":"x","khung":[{"tu":"21:00","den":"06:30","ngay":[1,2,9]}],"moTamPhut":60}""")!!
        assertFalse(c.coMa)
        assertEquals(Khung(1260, 390, setOf(1, 2)), c.khung[0]); assertEquals(60, c.moTamPhut)
        assertEquals(0, c.phutThuong); assertEquals(0, c.phutCuoiTuan)
        assertNull(Luat.docJson("""{"khung":[{"tu":"25:00","den":"06:00"}]}"""))
        val g = Luat.docJson("""{"bat":true,"khung":[],"phutThuong":90,"phutCuoiTuan":99999}""")!!
        assertEquals(90, g.phutThuong); assertEquals(24 * 60, g.phutCuoiTuan)
    }

    // ---------- Giới hạn giờ xem mỗi ngày ----------

    private fun han(thuong: Int, cuoiTuan: Int = thuong, vararg k: Khung) = ch(*k).copy(phutThuong = thuong, phutCuoiTuan = cuoiTuan)

    @Test fun gioiHan_ngayThuongVaCuoiTuan() {
        val c = han(120, 180)
        assertEquals(120, Luat.gioiHanPhut(c, 1)); assertEquals(120, Luat.gioiHanPhut(c, 5))
        assertEquals(180, Luat.gioiHanPhut(c, 6)); assertEquals(180, Luat.gioiHanPhut(c, 7))
    }

    @Test fun hetGioXem() {
        val c = han(60)
        assertFalse(Luat.hetGioXem(c, 1, 3599)); assertTrue(Luat.hetGioXem(c, 1, 3600))
        assertFalse(Luat.hetGioXem(han(0), 1, 99_999))                       // 0 = không giới hạn
        assertFalse(Luat.hetGioXem(c.copy(bat = false), 1, 99_999))
        assertFalse(Luat.hetGioXem(c.copy(coMa = false), 1, 99_999))         // chưa có mã thì không khoá được
        assertTrue(Luat.hetGioXem(han(120, 0), 1, 7200)); assertFalse(Luat.hetGioXem(han(120, 0), 7, 7200))
    }

    @Test fun lyDo_hetGioUuTienVaMoTam() {
        val c = han(60, 60, Khung(Luat.phut("14:00"), Luat.phut("16:30"), CA_TUAN))
        val trongKhung = luc(2026, 10, 19, 15, 0); val ngoaiKhung = luc(2026, 10, 19, 17, 0)
        assertEquals(Luat.LyDo.GIO_KHOA, Luat.lyDo(c, trongKhung, 0, 0))
        assertEquals(Luat.LyDo.HET_GIO, Luat.lyDo(c, trongKhung, 3600, 0))   // khung 16:30 hết vẫn khoá tới hết ngày
        assertEquals(Luat.LyDo.HET_GIO, Luat.lyDo(c, ngoaiKhung, 3600, 0))
        assertNull(Luat.lyDo(c, ngoaiKhung, 3599, 0))
        assertNull(Luat.lyDo(c, ngoaiKhung, 3600, ngoaiKhung.timeInMillis + 1))  // đang mở tạm bằng mã
        assertNull(Luat.lyDo(null, ngoaiKhung, 3600, 0))
    }

    @Test fun sapKhoa_baoTruoc5Phut() {
        val dem = ch(Khung(Luat.phut("21:00"), Luat.phut("06:00"), CA_TUAN))
        assertEquals(3, Luat.sapKhoa(dem, luc(2026, 10, 19, 20, 57), 0, 0))
        assertNull(Luat.sapKhoa(dem, luc(2026, 10, 19, 20, 50), 0, 0))
        // Còn 3 phút là hết 1 giờ xem.
        assertEquals(3, Luat.sapKhoa(han(60), luc(2026, 10, 19, 10, 0), 57 * 60L, 0))
        assertNull(Luat.sapKhoa(han(60), luc(2026, 10, 19, 10, 0), 50 * 60L, 0))
        // Đang mở tạm trong giờ khoá: báo trước lúc hết lượt mở.
        val bay = luc(2026, 10, 19, 22, 0)
        assertEquals(2, Luat.sapKhoa(dem, bay, 0, bay.timeInMillis + 2 * 60_000))
        assertNull(Luat.sapKhoa(dem, bay, 0, bay.timeInMillis + 30 * 60_000))
        // Mở tạm tới nửa đêm, xem đã quá giới hạn: sang ngày mới tính lại từ 0, không báo.
        assertNull(Luat.sapKhoa(han(60), luc(2026, 10, 19, 23, 58), 3700, luc(2026, 10, 20, 0, 0).timeInMillis))
    }

    @Test fun soXem_congVaGiu14Ngay() {
        val so = org.json.JSONObject()
        Luat.cong(so, "2026-10-25", 60); Luat.cong(so, "2026-10-25", 30)
        assertEquals(90L, so.getLong("2026-10-25"))
        (1..20).forEach { Luat.cong(so, "2026-09-%02d".format(it), 1) }
        assertEquals(14, so.length())
        assertTrue(so.has("2026-10-25")); assertFalse(so.has("2026-09-07")); assertTrue(so.has("2026-09-08"))
        assertEquals("2026-10-25", Luat.ngay(luc(2026, 10, 25, 23, 59)))
    }

    @Test fun chuThoiGian() {
        assertEquals("1 giờ 30 phút", Luat.chuThoiGian(90, true)); assertEquals("2 giờ", Luat.chuThoiGian(120, true))
        assertEquals("45 phút", Luat.chuThoiGian(45, true)); assertEquals("1 h 30 min", Luat.chuThoiGian(90, false))
    }

    /** Giờ tivi sai (hộp không pin giữ giờ vừa mất điện): khoá tới khi có giờ đúng, mở tạm bằng mã vẫn được (28/10/2026). */
    @Test fun gioSai_khoaToiKhiCoGio() {
        val c = CauHinh(bat = true, coMa = true, khung = emptyList(), moTamPhut = 60)
        assertEquals(Luat.LyDo.GIO_SAI, Luat.lyDo(c, luc(2021, 1, 1, 10, 0), 0, 0))
        assertNull(Luat.lyDo(c, luc(2026, 10, 28, 10, 0), 0, 0))
        val sai = luc(2021, 1, 1, 10, 0)
        assertNull(Luat.lyDo(c, sai, 0, sai.timeInMillis + 60_000))
        assertNull(Luat.lyDo(c.copy(bat = false), sai, 0, 0))
    }
}

