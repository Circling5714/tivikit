package vn.tivikit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class ChuTest {
    @Test
    fun loiChaoTheoGio() {
        assertEquals("Chào buổi sáng, Diễn", Chu.loiChao(7, " Diễn ", true))
        assertEquals("Chào buổi trưa", Chu.loiChao(12, "", true))
        assertEquals("Chào buổi chiều", Chu.loiChao(15, "", true))
        assertEquals("Chào buổi tối", Chu.loiChao(20, "", true))
        assertEquals("Chúc ngủ ngon", Chu.loiChao(23, "", true))
        assertEquals("Chúc ngủ ngon", Chu.loiChao(2, "", true))
        assertEquals("Good morning, The Smiths", Chu.loiChao(4, "The Smiths", false))
        // Tên đã là lời chào thì hiện nguyên.
        assertEquals("Chào CaoMinh Family", Chu.loiChao(20, " Chào CaoMinh Family ", true))
        assertEquals("Xin chào cả nhà!", Chu.loiChao(8, "Xin chào cả nhà!", true))
        assertEquals("Hi there", Chu.loiChao(8, "Hi there", false))
        assertEquals("Chào buổi sáng, Hiếu", Chu.loiChao(8, "Hiếu", true))
        assertEquals("Chào buổi sáng, Chàng trai", Chu.loiChao(8, "Chàng trai", true))
    }

    @Test
    fun ngayThang() {
        assertEquals("THỨ BA, 27 THÁNG 10", Chu.ngay(LocalDate.of(2026, 10, 27), true))
        assertEquals("CHỦ NHẬT, 1 THÁNG 11", Chu.ngay(LocalDate.of(2026, 11, 1), true))
        assertEquals("TUESDAY, OCTOBER 27", Chu.ngay(LocalDate.of(2026, 10, 27), false))
    }

    @Test
    fun dungLuongVaTocDo() {
        val mb = 1024L * 1024
        assertEquals("502 MB", Chu.dungLuong(502 * mb, true))
        assertEquals("1,2 GB", Chu.dungLuong(1229 * mb, true))
        assertEquals("1.2 GB", Chu.dungLuong(1229 * mb, false))
        assertEquals("24 GB", Chu.dungLuong(24L * 1024 * mb, true))
        assertEquals("0 KB/s", Chu.tocDo(300, true))
        assertEquals("2 KB/s", Chu.tocDo(2 * 1024 + 100, true))
        assertEquals("2,4 MB/s", Chu.tocDo((2.4 * mb).toLong(), true))
        assertEquals("RAM trống 502 MB · Bộ nhớ trống 1,2 GB", Chu.thongSo(502 * mb, 1229 * mb, true))
    }

    @Test
    fun tenWifiVaGoiGiuLai() {
        assertEquals("Dien Nguyen 5G", HeThong.tenSsid("\"Dien Nguyen 5G\""))
        assertNull(HeThong.tenSsid("<unknown ssid>"))
        assertNull(HeThong.tenSsid("\"\""))
        assertTrue(HeThong.giuLai("vn.tivikit.khoa", "vn.tivikit.launcher"))
        assertTrue(HeThong.giuLai("vn.tivikit", "vn.tivikit.launcher"))
        assertFalse(HeThong.giuLai("vn.tivikitx", "vn.tivikit.launcher"))
        assertTrue(HeThong.giuLai("com.gathin.tvhome", "vn.tivikit.launcher"))
        assertFalse(HeThong.giuLai("org.smarttube.stable", "vn.tivikit.launcher"))
    }
}
