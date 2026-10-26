package vn.tivikit.lg

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tin nhắn SSAP với tivi LG webOS: ghép đôi, yêu cầu, phím, đọc trả lời, app, nguồn, MAC, Wake-on-LAN, link YouTube. */
class WebOsTest {
    @Test fun dangKy() {
        val lanDau = JSONObject(WebOs.dangKy(null))
        assertEquals("register", lanDau.getString("type")); assertEquals(WebOs.ID_DANG_KY, lanDau.getString("id"))
        val p = lanDau.getJSONObject("payload")
        assertEquals("PROMPT", p.getString("pairingType")); assertFalse(p.has("client-key"))
        val quyen = p.getJSONObject("manifest").getJSONArray("permissions").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertTrue("CONTROL_INPUT_TEXT" in quyen); assertTrue("LAUNCH" in quyen); assertTrue("CONTROL_POWER" in quyen)
        // Lần sau gửi kèm client-key: tivi nhận luôn, không hỏi.
        assertEquals("abc123", JSONObject(WebOs.dangKy("abc123")).getJSONObject("payload").getString("client-key"))
    }

    @Test fun yeuCauVaPhim() {
        val y = JSONObject(WebOs.yeuCau("tk1", "com.webos.service.ime/insertText", JSONObject().put("text", "Sơn Tùng").put("replace", 0)))
        assertEquals("request", y.getString("type")); assertEquals("ssap://com.webos.service.ime/insertText", y.getString("uri"))
        assertEquals("Sơn Tùng", y.getJSONObject("payload").getString("text"))
        assertEquals("type:button\nname:HOME\n\n", WebOs.nut("HOME"))
    }

    @Test fun docTraLoi() {
        val ok = WebOs.doc("""{"type":"response","id":"tk2","payload":{"returnValue":true,"socketPath":"wss://192.168.1.50:3001/resources/abc/netinput.pointer.sock"}}""")!!
        assertTrue(WebOs.thanhCong(ok)); assertEquals("tk2", ok.id)
        val hong = WebOs.doc("""{"type":"response","id":"tk3","payload":{"returnValue":false,"errorText":"no focused input"}}""")!!
        assertFalse(WebOs.thanhCong(hong)); assertEquals("no focused input", WebOs.chuLoi(hong))
        val loi = WebOs.doc("""{"type":"error","id":"tk4","error":"404 no such service or method","payload":{}}""")!!
        assertFalse(WebOs.thanhCong(loi)); assertTrue(LgLoiException(WebOs.chuLoi(loi)).khongCo)
        val daGhep = WebOs.doc("""{"type":"registered","id":"register_0","payload":{"client-key":"k9"}}""")!!
        assertTrue(WebOs.thanhCong(daGhep))
        assertNull(WebOs.doc("không phải JSON"))
    }

    @Test fun dsAppVaNguon() {
        val apps = WebOs.dsApp(JSONObject("""{"returnValue":true,"launchPoints":[
            {"id":"youtube.leanback.v4","title":"YouTube","icon":"http://192.168.1.50:3000/resources/1/yt.png"},
            {"id":"netflix","title":"Netflix","largeIcon":"http://192.168.1.50:3000/resources/2/nf.png"},
            {"id":"netflix","title":"Netflix"}, {"title":"không có id"}]}"""))
        assertEquals(listOf("netflix", "youtube.leanback.v4"), apps.map { it.id })
        assertEquals("http://192.168.1.50:3000/resources/2/nf.png", apps[0].icon)
        val nguon = WebOs.dsNguon(JSONObject("""{"returnValue":true,"devices":[{"id":"HDMI_1","label":"HDMI 1","connected":true},{"id":"HDMI_2","label":"","connected":false}]}"""))
        assertEquals(listOf("HDMI 1", "HDMI_2"), nguon.map { it.ten }); assertTrue(nguon[0].coThietBi)
    }

    @Test fun macVaWol() {
        val mac = WebOs.mac(JSONObject("""{"wiredInfo":{"macAddress":"aa:bb:cc:00:11:22","state":"disconnected"},
            "wifiInfo":{"macAddress":"A0:B1:C2:D3:E4:F5","state":"connected"}}"""))
        assertEquals("a0:b1:c2:d3:e4:f5", mac)
        assertNull(WebOs.mac(JSONObject("""{"wifiInfo":{"macAddress":"sai"}}""")))
        val goi = WebOs.goiWol("a0:b1:c2:d3:e4:f5")!!
        assertEquals(6 + 16 * 6, goi.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, goi.copyOfRange(0, 6))
        assertArrayEquals(byteArrayOf(0xA0.toByte(), 0xB1.toByte(), 0xC2.toByte(), 0xD3.toByte(), 0xE4.toByte(), 0xF5.toByte()), goi.copyOfRange(96, 102))
        assertNull(WebOs.goiWol("không phải MAC"))
    }

    @Test fun linkYoutube() {
        assertEquals("dQw4w9WgXcQ", WebOs.maYoutube("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", WebOs.maYoutube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
        assertEquals("dQw4w9WgXcQ", WebOs.maYoutube("https://m.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", WebOs.maYoutube("dQw4w9WgXcQ"))
        assertNull(WebOs.maYoutube("https://vnexpress.net/"))
        assertEquals("https://www.youtube.com/tv?v=dQw4w9WgXcQ", WebOs.moYoutube("dQw4w9WgXcQ").getJSONObject("params").getString("contentTarget"))
    }
}
