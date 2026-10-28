package vn.tivikit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LichNgayTest {
    private fun ngay(y: Int, m: Int, d: Int) = LichNgay.tinh(LocalDate.of(y, m, d))

    @Test
    fun ngay28Thang10Nam2026() {
        // Đối chiếu ngaydep.com / xemlicham.com: 19/9 Bính Ngọ, ngày Ất Hợi, tháng Mậu Tuất, Ngọc Đường hoàng đạo,
        // tiết Sương giáng, trực Trừ, giờ hoàng đạo Sửu, Thìn, Ngọ, Mùi, Tuất, Hợi.
        val n = ngay(2026, 10, 28)
        assertEquals(AmLich.Ngay(19, 9, 2026, false), n.am)
        assertEquals("Ất Hợi", LichNgay.canChi(n.canNgay, n.chiNgay))
        assertEquals("Mậu Tuất", LichNgay.canChi(n.canThang, n.chiThang))
        assertEquals("Ngọc Đường", LichNgay.than(n.than))
        assertTrue(n.hoangDao)
        assertEquals("Sương giáng", LichNgay.tiet(n.tiet, true))
        assertEquals("Frost's Descent", LichNgay.tiet(n.tiet, false))
        assertEquals("Trừ", LichNgay.truc(n.truc))
        assertEquals(listOf("Sửu", "Thìn", "Ngọ", "Mùi", "Tuất", "Hợi"), n.gioTot.map(LichNgay::chi))
        assertEquals("1–3h", LichNgay.khungGio(1))
        assertEquals("23–1h", LichNgay.khungGio(0))
        assertTrue(n.le.isEmpty())
        assertFalse(n.tamNuong)
        assertFalse(n.nguyetKy)
    }

    @Test
    fun canChiNgayMoc() {
        // 1/1/2000 là ngày Mậu Ngọ.
        val n = ngay(2000, 1, 1)
        assertEquals("Mậu Ngọ", LichNgay.canChi(n.canNgay, n.chiNgay))
    }

    @Test
    fun tietKhiDoiTheoGioViet() {
        // Đông chí 2026: 20:50 UTC ngày 21/12, tức 3:50 sáng 22/12 giờ Việt Nam.
        assertEquals("Đại tuyết", LichNgay.tiet(ngay(2026, 12, 21).tiet, true))
        assertEquals("Đông chí", LichNgay.tiet(ngay(2026, 12, 22).tiet, true))
        // Xuân phân 2026: 21:46 ngày 20/3 giờ Việt Nam.
        assertEquals("Kinh trập", LichNgay.tiet(ngay(2026, 3, 19).tiet, true))
        assertEquals("Xuân phân", LichNgay.tiet(ngay(2026, 3, 20).tiet, true))
    }

    @Test
    fun ngayGiaoTietLapLaiTruc() {
        // Ngày sang tiết mới (Lập xuân, Kinh trập, Thanh minh…) lặp lại trực của hôm trước, các ngày khác trực chạy tiếp.
        var d = LocalDate.of(2026, 1, 1)
        var truoc = LichNgay.tinh(d.minusDays(1))
        var soLanGiao = 0
        while (d.year == 2026) {
            val n = LichNgay.tinh(d)
            val thangTiet = (n.tiet + 1) / 2 % 12 != (truoc.tiet + 1) / 2 % 12
            if (thangTiet) { soLanGiao++; assertEquals("$d", truoc.truc, n.truc) }
            else assertEquals("$d", (truoc.truc + 1) % 12, n.truc)
            truoc = n
            d = d.plusDays(1)
        }
        assertEquals(12, soLanGiao)
    }

    @Test
    fun thanTheoThangAm() {
        // Tháng Giêng và tháng 7 âm: Thanh Long ở ngày Tý; tháng 2 và 8: ở ngày Dần…
        var d = LocalDate.of(2026, 1, 1)
        while (d.year == 2026) {
            val n = LichNgay.tinh(d)
            val batDau = ((n.am.thang - 1) % 6) * 2
            if (n.chiNgay == batDau) assertEquals("$d", "Thanh Long", LichNgay.than(n.than))
            d = d.plusDays(1)
        }
    }

    @Test
    fun ngayLe() {
        assertEquals(listOf("Giao thừa"), ngay(2026, 2, 16).le.map { it.vi })
        assertEquals(listOf("Tết Nguyên Đán"), ngay(2026, 2, 17).le.map { it.vi })
        assertEquals(listOf("Tết Trung thu"), ngay(2026, 9, 25).le.map { it.vi })
        assertEquals(listOf("Quốc khánh"), ngay(2026, 9, 2).le.map { it.vi })
        assertEquals(listOf("Christmas Day"), ngay(2026, 12, 25).le.map { it.en })
        // Tháng nhuận không có lễ: 2025 nhuận tháng 6, rằm tháng 6 nhuận là 8/8/2025.
        val nhuan = ngay(2025, 8, 8)
        assertEquals(AmLich.Ngay(15, 6, 2025, true), nhuan.am)
        assertTrue(nhuan.le.isEmpty())
        assertTrue(nhuan.soc)
    }

    @Test
    fun tamNuongNguyetKy() {
        // 3/9 âm 2026 (12/10/2026) là Tam nương; 5/9 âm (14/10/2026) là Nguyệt kỵ.
        assertEquals(3, ngay(2026, 10, 12).am.ngay)
        assertTrue(ngay(2026, 10, 12).tamNuong)
        assertEquals(5, ngay(2026, 10, 14).am.ngay)
        assertTrue(ngay(2026, 10, 14).nguyetKy)
    }

    @Test
    fun oThangBatDauTuThuHai() {
        val o = LichNgay.oThang(YearMonth.of(2026, 10))
        assertEquals(42, o.size)
        // 1/10/2026 là thứ Năm: ô đầu là thứ Hai 28/9.
        assertEquals(LocalDate.of(2026, 9, 28), o.first())
        assertEquals(LocalDate.of(2026, 11, 8), o.last())
    }

    @Test
    fun tenThangAm() {
        assertEquals("tháng Giêng", LichNgay.tenThangAm(1, false))
        assertEquals("tháng Chạp", LichNgay.tenThangAm(12, false))
        assertEquals("tháng 6 nhuận", LichNgay.tenThangAm(6, true))
    }
}
