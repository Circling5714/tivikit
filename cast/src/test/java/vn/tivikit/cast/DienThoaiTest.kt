package vn.tivikit.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Danh sách điện thoại của tivi (28/10/2026): máy lạ hỏi, khách có hạn, chặn, ai gửi video. */
class DienThoaiTest {
    private val gioVn = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
    private fun luc(gio: Int, phut: Int = 0): Long = (gioVn.clone() as Calendar).apply {
        clear(); set(2026, Calendar.OCTOBER, 28, gio, phut)
    }.timeInMillis

    @Test fun mayMoi_choDuyet_mayCu_capNhat() {
        var ds = DanhBa.noi(emptyList(), "a", "iCM16prm", "Cao Minh", luc(10))
        assertEquals(DienThoai.Xet.HOI, ds.single().xet(luc(10)))
        ds = DanhBa.lam(ds, "a", DanhBa.Lam.CHO_PHEP, luc(10))
        ds = DanhBa.noi(ds, "a", "", "", luc(11))
        val a = ds.single()
        assertEquals("iCM16prm", a.ten)
        assertEquals("Cao Minh", a.taiKhoan)
        assertEquals(luc(10), a.lanDau)
        assertEquals(luc(11), a.lanCuoi)
        assertEquals(DienThoai.Xet.PHAT, a.xet(luc(23)))
        assertEquals("Cao Minh · iCM16prm", a.tenHien)
    }

    @Test fun khach_hetHomNay_va_24Gio() {
        val ds = DanhBa.noi(emptyList(), "k", "iPhone", "Khach", luc(20))
        val homNay = DanhBa.lam(ds, "k", DanhBa.Lam.HOM_NAY, luc(20), gioVn.clone() as Calendar).single()
        assertEquals(DienThoai.Xet.PHAT, homNay.xet(luc(23, 59)))
        assertEquals(DienThoai.Xet.BO_QUA, homNay.xet(luc(23, 59) + 60_000))
        assertTrue(homNay.quaHan(luc(23, 59) + 60_000))
        val motNgay = DanhBa.lam(ds, "k", DanhBa.Lam.MOT_NGAY, luc(20)).single()
        assertEquals(DienThoai.Xet.PHAT, motNgay.xet(luc(20) + DanhBa.NGAY_MS - 1))
        assertEquals(DienThoai.Xet.BO_QUA, motNgay.xet(luc(20) + DanhBa.NGAY_MS))
    }

    @Test fun chan_xoa_vaJson() {
        var ds = DanhBa.noi(emptyList(), "a", "A", "", luc(9))
        ds = DanhBa.noi(ds, "b", "B", "", luc(10))
        ds = DanhBa.lam(ds, "b", DanhBa.Lam.CHAN, luc(10))
        assertEquals(DienThoai.Xet.BO_QUA, ds.first { it.id == "b" }.xet(luc(10)))
        assertEquals(ds, DanhBa.tuJson(DanhBa.json(ds)))
        ds = DanhBa.lam(ds, "b", DanhBa.Lam.XOA, luc(11))
        assertEquals(listOf("a"), ds.map { it.id })
        assertEquals(ds, DanhBa.lam(ds, "khong-co", DanhBa.Lam.CHAN, luc(11)))
        assertEquals(DanhBa.Lam.MOT_NGAY, DanhBa.Lam.tu("24h"))
        assertNull(DanhBa.Lam.tu("la"))
    }

    @Test fun nguoiGui_theoTaiKhoan_khongThiMayGanNhat() {
        var ds = DanhBa.noi(emptyList(), "chu", "iCM16prm", "Cao Minh", luc(9))
        ds = DanhBa.noi(ds, "khach", "iPhone", "Khach", luc(10))
        val dangNoi = listOf("chu", "khach")
        assertEquals("chu", DanhBa.nguoiGui(ds, dangNoi, "khach", "Cao Minh")?.id)
        assertEquals("khach", DanhBa.nguoiGui(ds, dangNoi, "khach", "")?.id)
        assertEquals("khach", DanhBa.nguoiGui(ds, dangNoi, "khach", "Ai Do")?.id)
        assertNull(DanhBa.nguoiGui(ds, dangNoi, null, ""))
    }

    @Test fun dieuKhien_khiChiCoMayBiChan() {
        var ds = DanhBa.noi(emptyList(), "a", "A", "", luc(9))
        assertTrue(DanhBa.duocDieuKhien(ds, emptyList(), luc(9)))
        assertFalse(DanhBa.duocDieuKhien(ds, listOf("a"), luc(9))) // chờ duyệt
        ds = DanhBa.lam(ds, "a", DanhBa.Lam.CHO_PHEP, luc(9))
        assertTrue(DanhBa.duocDieuKhien(ds, listOf("a"), luc(9)))
    }

    @Test fun tenTuNhatKy_mayDaCastTruocBan20() {
        val nk = org.json.JSONArray("""[{"luc":1,"viec":"bật máy nhận, cổng 8095"},{"luc":2,"viec":"điện thoại nối: iCM16prm"},
            {"luc":3,"viec":"mở video abc"},{"luc":4,"viec":"điện thoại nối: iCM16prm"},{"luc":5,"viec":"điện thoại nối: "}]""")
        assertEquals(setOf("iCM16prm"), DanhBa.tenTuNhatKy(nk))
    }

    @Test fun gon_giuMayBiChan() {
        var ds = DanhBa.lam(DanhBa.noi(emptyList(), "chan", "X", "", 1), "chan", DanhBa.Lam.CHAN, 1)
        for (i in 1..DanhBa.TOI_DA + 5) ds = DanhBa.noi(ds, "m$i", "M$i", "", 10L + i)
        assertEquals(DanhBa.TOI_DA, ds.size)
        assertTrue(ds.any { it.id == "chan" })
        assertTrue(ds.any { it.id == "m${DanhBa.TOI_DA + 5}" })
    }
}
