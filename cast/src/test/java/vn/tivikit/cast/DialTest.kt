package vn.tivikit.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DIAL (28/10/2026): trả lời SSDP M-SEARCH, mô tả thiết bị / app, nhận mã ghép đôi của điện thoại. */
class DialTest {
    private val ghepNhan = mutableListOf<Pair<String, String>>()
    private val dial = Dial("u-1", { "SmartTube · Tivi <A&B>" }, "Skyworth", "SWTV", "Android/11 UPnP/1.1 TiviKit-Cast/1.0",
        { ma, theme -> ghepNhan += ma to theme; ma != "hong" }, {})

    private fun tim(st: String) = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: $st\r\n\r\n"

    @Test fun ssdp_timDial() {
        val tl = dial.traLoiTim(tim(Dial.DICH_VU), "http://10.0.0.5:8095/dd.xml")
        assertEquals(1, tl.size)
        assertTrue(tl[0].startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(tl[0].contains("\r\nLOCATION: http://10.0.0.5:8095/dd.xml\r\n"))
        assertTrue(tl[0].contains("\r\nST: ${Dial.DICH_VU}\r\n"))
        assertTrue(tl[0].contains("\r\nUSN: uuid:u-1::${Dial.DICH_VU}\r\n"))
        assertTrue(tl[0].endsWith("\r\n\r\n"))
    }

    @Test fun ssdp_tatCa_vaKhongLienQuan() {
        assertEquals(4, dial.traLoiTim(tim("ssdp:all"), "x").size)
        assertTrue(dial.traLoiTim(tim("uuid:u-1"), "x")[0].contains("\r\nUSN: uuid:u-1\r\n"))
        assertTrue(dial.traLoiTim(tim("urn:dial-multiscreen-org:service:other:1"), "x").isEmpty())
        assertTrue(dial.traLoiTim("NOTIFY * HTTP/1.1\r\nNT: upnp:rootdevice\r\n\r\n", "x").isEmpty())
        assertTrue(dial.traLoiTim("M-SEARCH * HTTP/1.1\r\nST: ssdp:all\r\n\r\n", "x").isEmpty()) // thiếu MAN
    }

    private fun chu(b: ByteArray) = String(b, Charsets.UTF_8)

    @Test fun http_moTaThietBi_vaApp() {
        val dd = chu(dial.traLoi("GET", "/dd.xml", "", "http://10.0.0.5:8095"))
        assertTrue(dd.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(dd.contains("\r\nApplication-URL: http://10.0.0.5:8095/apps\r\n"))
        assertTrue(dd.contains("<friendlyName>SmartTube · Tivi &lt;A&amp;B&gt;</friendlyName>"))
        assertTrue(dd.contains("<UDN>uuid:u-1</UDN>"))
        val app = chu(dial.traLoi("GET", "/apps/YouTube", "", "http://10.0.0.5:8095"))
        assertTrue(app.contains("<state>running</state>"))
        assertTrue(chu(dial.traLoi("GET", "/apps/Netflix", "", "g")).startsWith("HTTP/1.1 404"))
    }

    @Test fun http_dienThoaiGuiMaGhep() {
        val ok = chu(dial.traLoi("POST", "/apps/YouTube", "pairingCode=ab-12&v=xyz&t=0&theme=cl", "http://10.0.0.5:8095"))
        assertTrue(ok.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(ok.contains("\r\nLOCATION: http://10.0.0.5:8095/apps/YouTube/run\r\n"))
        assertEquals("ab-12" to "cl", ghepNhan.last())
        assertTrue(chu(dial.traLoi("POST", "/apps/YouTube", "pairingCode=hong&theme=cl", "g")).startsWith("HTTP/1.1 503"))
        assertTrue(chu(dial.traLoi("POST", "/apps/YouTube", "v=xyz", "g")).startsWith("HTTP/1.1 503"))
        assertTrue(chu(dial.traLoi("DELETE", "/apps/YouTube/run", "", "g")).startsWith("HTTP/1.1 405"))
    }

    @Test fun thamSo() {
        assertEquals(mapOf("pairingCode" to "a b", "theme" to "cl"), Dial.thamSo("pairingCode=a+b&theme=cl\r\n"))
    }

    @Test fun biChe_mayDialCuaHangCungIp() {
        fun dap(tu: String, loc: String) = org.json.JSONObject().put("tu", tu).put("location", loc)
        val minh = dap("10.10.16.203:1900", "http://10.10.16.203:8095/dd.xml")
        // Skyworth: dialserver MediaTek cùng IP, cổng khác.
        assertEquals(true, Dial.biChe(listOf(dap("10.10.16.203:41234", "http://10.10.16.203:56790/dd.xml"), minh), 8095))
        // TX3: chỉ có mình; máy DIAL của tivi khác trong nhà không tính.
        assertEquals(false, Dial.biChe(listOf(minh, dap("10.10.16.50:1900", "http://10.10.16.50:8008/ssdp/device-desc.xml")), 8095))
        // Không thấy cả lời đáp của mình: mạng chưa lên, chưa kết luận.
        assertEquals(null, Dial.biChe(listOf(dap("10.10.16.203:41234", "http://10.10.16.203:56790/dd.xml")), 8095))
        assertEquals(null, Dial.biChe(emptyList(), 8095))
    }
}
