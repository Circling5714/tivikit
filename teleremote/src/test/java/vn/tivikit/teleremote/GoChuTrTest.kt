package vn.tivikit.teleremote

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoChuTrTest {
    @Test
    fun goKhongDau() {
        assertFalse(GoChuTr.canBoGo("matkhau 123!"))
        assertTrue(GoChuTr.canBoGo("mật khẩu"))
        assertTrue(GoChuTr.canBoGo("hi 🎵"))
        assertEquals("input text 'it'\\''s%sme'", GoChuTr.lenhAscii("it's me"))
        // Chuỗi dài chia đoạn 200 ký tự.
        assertEquals(2, GoChuTr.lenhAscii("a".repeat(250)).split("; ").size)
    }

    @Test
    fun goQuaBoGo() {
        val l = GoChuTr.lenhBoGo("phở bò", "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME", thay = true)
        // Lệnh gửi qua ADB thuần ASCII; chữ đi trong base64 của JSON, kèm bàn phím cũ để trả lại.
        assertTrue(l.all { it.code in 0x20..0x7e })
        val b64 = l.substringAfter("--method chu --arg '").substringBefore("'")
        val j = JSONObject(String(Base64.getDecoder().decode(b64)))
        assertEquals("phở bò", j.getString("chu"))
        assertEquals("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME", j.getString("cu"))
        assertTrue(j.getBoolean("thay"))
        assertTrue(l.startsWith("ime enable vn.tivikit.go/.BoGo"))
        assertTrue(l.contains("ime disable vn.tivikit.go/.BoGo"))
        // Bàn phím cũ là bộ gõ của TiviKit (lần trước bị ngắt) hay đọc lỗi: không trả về nó.
        assertFalse(GoChuTr.laBoGoCu("vn.tivikit.go/.BoGo"))
        assertFalse(GoChuTr.laBoGoCu("null"))
        assertEquals("", JSONObject(String(Base64.getDecoder().decode(GoChuTr.lenhBoGo("x", "null", thay = false).substringAfter("--arg '").substringBefore("'")))).getString("cu"))
        assertEquals("ok", GoChuTr.ketQua("Result: Bundle[{ok=1}]"))
        assertEquals("khong-o-nhap", GoChuTr.ketQua("Result: Bundle[{loi=khong-o-nhap}]"))
        assertEquals("khong-tra-loi", GoChuTr.ketQua(""))
    }
}
