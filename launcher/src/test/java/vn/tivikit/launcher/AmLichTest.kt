package vn.tivikit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AmLichTest {
    private fun am(y: Int, m: Int, d: Int) = AmLich.tuDuongLich(d, m, y)

    @Test
    fun ngayChupManHinhMau() {
        // Ảnh mẫu người dùng gửi (27/10/2026): "Âm lịch 18/09 (Bính Ngọ)".
        assertEquals(AmLich.Ngay(18, 9, 2026, false), am(2026, 10, 27))
        assertEquals("Âm lịch 18/09 (Bính Ngọ)", AmLich.chu(LocalDate.of(2026, 10, 27), true))
        assertEquals("Lunar 18/09 · Year of the Horse", AmLich.chu(LocalDate.of(2026, 10, 27), false))
    }

    @Test
    fun tetVaTrungThu() {
        assertEquals(AmLich.Ngay(1, 1, 2026, false), am(2026, 2, 17))
        assertEquals(AmLich.Ngay(1, 1, 2025, false), am(2025, 1, 29))
        assertEquals(AmLich.Ngay(15, 8, 2026, false), am(2026, 9, 25))
        // Ngày cuối năm Ất Tỵ thuộc năm âm 2025.
        assertEquals(2025, am(2026, 2, 16).nam)
        assertEquals(12, am(2026, 2, 16).thang)
    }

    @Test
    fun thangNhuan() {
        // 2023 nhuận tháng 2 (Quý Mão), 2025 nhuận tháng 6 (Ất Tỵ).
        assertEquals(AmLich.Ngay(1, 2, 2023, true), am(2023, 3, 22))
        assertEquals(AmLich.Ngay(1, 2, 2023, false), am(2023, 2, 20))
        assertEquals(AmLich.Ngay(1, 6, 2025, true), am(2025, 7, 25))
        assertEquals("Âm lịch 01/06 nhuận (Ất Tỵ)", AmLich.chu(LocalDate.of(2025, 7, 25), true))
    }

    @Test
    fun canChi() {
        assertEquals("Bính Ngọ", AmLich.canChi(2026))
        assertEquals("Ất Tỵ", AmLich.canChi(2025))
        assertEquals("Quý Mão", AmLich.canChi(2023))
        assertEquals("Giáp Thìn", AmLich.canChi(2024))
    }
}
