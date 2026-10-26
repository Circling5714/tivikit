package vn.tivikit.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Gõ chữ có dấu lên tivi qua bộ gõ vn.tivikit.go. */
class GoChuTest {
    private val LEANBACK = "com.google.android.leanback.ime/.LeanbackImeService"

    /** Chuỗi --arg trong lệnh, giải ra JSON. */
    private fun goi(lenh: String): JSONObject =
        JSONObject(String(Base64.getDecoder().decode(Regex("--arg '([A-Za-z0-9+/=]+)'").find(lenh)!!.groupValues[1]), Charsets.UTF_8))

    @Test fun canBoGo_khiCoDau() {
        assertFalse(GoChu.canBoGo("Son Tung M-TP 2024"))
        assertTrue(GoChu.canBoGo("Sơn Tùng")); assertTrue(GoChu.canBoGo("đi")); assertTrue(GoChu.canBoGo("nhạc 😀"))
    }

    @Test fun lenh_guiChuNguyenVenVaTraBanPhim() {
        val chu = "Hãy trao cho anh 'Sơn Tùng' \$(reboot)"
        val l = GoChu.lenh(chu, LEANBACK)
        assertEquals(chu, goi(l).getString("chu")); assertEquals(LEANBACK, goi(l).getString("cu"))
        // Chữ chỉ nằm trong base64: không có nháy / $() lọt ra shell.
        assertFalse(l.contains("reboot")); assertFalse(l.contains("Sơn"))
        assertTrue(l.startsWith("ime enable vn.tivikit.go/.BoGo"))
        assertTrue(l.contains("|| ime set '$LEANBACK'")); assertTrue(l.contains("ime disable vn.tivikit.go/.BoGo"))
    }

    @Test fun lenh_dauTachRoiThanhDungSan() {
        // "ệ" gõ kiểu tách dấu (e + dấu mũ + dấu nặng) → một ký tự dựng sẵn.
        assertEquals("Việt", goi(GoChu.lenh("Việt", LEANBACK)).getString("chu"))
    }

    @Test fun boGoCuKhongHopLe_khongTraBangLenh() {
        for (cu in listOf("", "null", "vn.tivikit.go/.BoGo", "x'; reboot; '")) {
            val l = GoChu.lenh("ơ", cu)
            assertEquals("", goi(l).getString("cu")); assertFalse(l.contains("ime set '"))
        }
        assertTrue(GoChu.laBoGoCu(LEANBACK)); assertFalse(GoChu.laBoGoCu("vn.tivikit.go/.BoGo"))
    }

    @Test fun ketQua() {
        assertEquals("ok", GoChu.ketQua("Result: Bundle[{ok=1}]"))
        assertEquals("khong-o-nhap", GoChu.ketQua("Result: Bundle[{loi=khong-o-nhap}]"))
        assertEquals("khong-tra-loi", GoChu.ketQua("Error while accessing provider:vn.tivikit.go"))
    }
}
