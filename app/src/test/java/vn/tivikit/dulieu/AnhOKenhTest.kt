package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ảnh ô Kênh nhanh: xếp tên kênh cho ô chữ, không biểu tượng thì giữ nguyên logo. */
class AnhOKenhTest {
    /** Mỗi ký tự rộng 0,6 lần cỡ chữ. */
    private val doRong = { s: String -> s.length * 0.6f }

    @Test fun xepChu_tenNganMotDongCoLon() = assertEquals(52f to listOf("VTV1"), AnhOKenh.xepChu("VTV1", 264f, doRong))

    @Test fun xepChu_motDongThuNhoDan() = assertEquals(30f to listOf("VTV Cần Thơ HD"), AnhOKenh.xepChu("VTV Cần Thơ HD", 264f, doRong))

    @Test fun xepChu_haiDongChiaCanNhat() =
        assertEquals(36f to listOf("Cartoon", "Network HD"), AnhOKenh.xepChu("Cartoon Network HD", 264f, doRong))

    @Test fun xepChu_khongChiaDuocThiThuChoVua() {
        val ten = "A".repeat(30)
        val (co, dong) = AnhOKenh.xepChu(ten, 264f, doRong)
        assertEquals(listOf(ten), dong)
        assertEquals(264f, doRong(ten) * co, 0.01f)
    }

    @Test fun ve_khongBieuTuongThiGiuNguyen() {
        val logo = byteArrayOf(1, 2, 3)
        assertEquals(logo, AnhOKenh.ve(logo, "VTV1", null))
        assertNull(AnhOKenh.ve(null, "VTV1", null))
    }
}
