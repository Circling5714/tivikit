package vn.tivikit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LamMoTest {
    private fun xam(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun anhMotMauGiuNguyen() {
        val px = IntArray(20 * 10) { 0xFF336699.toInt() }
        lamMo(px, 20, 10, 3)
        assertTrue(px.all { it == 0xFF336699.toInt() })
    }

    @Test
    fun diemSangLoangRaMaKhongVuotMep() {
        val w = 15
        val h = 15
        val px = IntArray(w * h) { xam(0) }
        px[7 * w + 7] = xam(255)
        lamMo(px, w, h, 2)
        val giua = px[7 * w + 7] and 0xFF
        val canh = px[7 * w + 9] and 0xFF
        val xa = px[0] and 0xFF
        assertTrue("giữa còn sáng nhất: $giua", giua > canh)
        assertTrue("đã loang sang bên: $canh", canh > 0)
        assertEquals(0, xa)
        assertTrue(px.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun mauNenOChuKhongQuaSangKhongQuaToi() {
        val trang = IntArray(144) { 0xFFFFFFFF.toInt() }
        val hsvSang = DanhSachApp.mauVua(trang)
        assertTrue(((hsvSang shr 16) and 0xFF) <= 160)
        val trongSuot = IntArray(144) { 0x00FF0000 }
        assertEquals(0xFF3A4060.toInt(), DanhSachApp.mauVua(trongSuot))
        val do_ = IntArray(144) { 0xFFE53935.toInt() }
        val r = (DanhSachApp.mauVua(do_) shr 16) and 0xFF
        val g = (DanhSachApp.mauVua(do_) shr 8) and 0xFF
        assertTrue("vẫn là màu đỏ: r=$r g=$g", r > g * 2)
    }
}
