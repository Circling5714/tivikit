package vn.tivikit.go

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/** Lệnh gõ TiviKit gửi qua `content call --arg`: base64 của JSON. */
class LenhTest {
    private fun goi(o: JSONObject) = Base64.getEncoder().encodeToString(o.toString().toByteArray())

    @Test fun docChuCoDau() {
        val l = Lenh.doc(goi(JSONObject().put("chu", "Sơn Tùng M-TP 'Hãy trao cho anh' 😀").put("cu", "com.google.android.leanback.ime/.LeanbackImeService")))!!
        assertEquals("Sơn Tùng M-TP 'Hãy trao cho anh' 😀", l.chu)
        assertEquals("com.google.android.leanback.ime/.LeanbackImeService", l.cu)
    }

    @Test fun boGoCuLaThiBo() {
        // Tên bộ gõ cũ không đúng dạng gói/lớp (có thể là chữ chèn lệnh): bỏ, bộ gõ tự quay về bàn phím dùng trước đó.
        assertEquals("", Lenh.doc(goi(JSONObject().put("chu", "a").put("cu", "x; rm -rf /")))!!.cu)
        assertEquals("", Lenh.doc(goi(JSONObject().put("chu", "a")))!!.cu)
    }

    @Test fun sai() {
        assertNull(Lenh.doc(null)); assertNull(Lenh.doc("không phải base64"))
        assertNull(Lenh.doc(goi(JSONObject().put("chu", ""))))
        assertNull(Lenh.doc(Base64.getEncoder().encodeToString("[1,2]".toByteArray())))
    }
}
