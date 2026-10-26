package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nút remote gán riêng (25/10/2026). Mẫu getevent theo getevent.c của toolbox (`%s: %04x %04x %08x`), mẫu keylayout
 * theo bảng remote Google TV Streamer ở upstream TROUBLESHOOTING (YouTube = usage 0x000c0077 → BUTTON_3).
 */
class PhimRiengTest {
    private val bleYouTube = """
        /dev/input/event5: 0002 0000 fffffffe
        /dev/input/event5: 0000 0000 00000000
        /dev/input/event4: 0004 0004 000c0077
        /dev/input/event4: 0001 00f0 00000001
        /dev/input/event4: 0000 0000 00000000
        /dev/input/event4: 0004 0004 000c0077
        /dev/input/event4: 0001 00f0 00000000
        /dev/input/event4: 0000 0000 00000000
    """.trimIndent()

    @Test fun docGetevent_nutBluetooth() {
        val n = PhimRieng.docGetevent(bleYouTube)!!
        assertEquals("/dev/input/event4", n.thietBi)
        assertEquals(PhimRieng.MA_CHUNG, n.maQuet)
        assertEquals(0xc0077L, n.usage)
    }

    @Test fun docGetevent_nutHongNgoai_khongLayUsageLuotTruoc() {
        // MSC_SCAN của lượt trước (đã có EV_SYN) không được dính vào nút sau; dòng thả nút / lặp (0, 2) bỏ qua.
        val chu = "/dev/input/event2: 0004 0004 00000045\r\n/dev/input/event2: 0000 0000 00000000\r\n" +
            "/dev/input/event2: 0001 01ea 00000002\r\n/dev/input/event2: 0001 01ea 00000001\r\n"
        val n = PhimRieng.docGetevent(chu)!!
        assertEquals(490, n.maQuet)
        assertNull(n.usage)
        assertNull(PhimRieng.docGetevent("/dev/input/event2: 0001 01ea 00000000\n"))
        assertNull(PhimRieng.docGetevent("/system/bin/sh: getevent: inaccessible or not found"))
    }

    @Test fun docGetevent_theoDungNut() {
        val khac = "/dev/input/event4: 0004 0004 000c0078\n/dev/input/event4: 0001 00f0 00000001\n/dev/input/event4: 0000 0000 00000000\n"
        val nut = PhimRieng.Nut("/dev/input/event4", 240, 0xc0077)
        assertNull(PhimRieng.docGetevent(khac, nut))
        assertEquals(0xc0077L, PhimRieng.docGetevent(khac + bleYouTube, nut)?.usage)
    }

    @Test fun fileKeylayout_dungThietBi() {
        val dump = """
            |      Path: <virtual>
            |      KeyLayoutFile: /system/usr/keylayout/Generic.kl
            |      Path: /dev/input/event10
            |      KeyLayoutFile: /system/usr/keylayout/Vendor_1234_Product_0001.kl
            |      Path: /dev/input/event1
            |      KeyLayoutFile: /vendor/usr/keylayout/Vendor_0957_Product_0004.kl
            |      Path: /dev/input/event3
            |      KeyLayoutFile:
        """.trimMargin()
        assertEquals("/vendor/usr/keylayout/Vendor_0957_Product_0004.kl", PhimRieng.fileKeylayout(dump, "/dev/input/event1"))
        assertEquals("/system/usr/keylayout/Vendor_1234_Product_0001.kl", PhimRieng.fileKeylayout(dump, "/dev/input/event10"))
        assertNull(PhimRieng.fileKeylayout(dump, "/dev/input/event3"))
        assertNull(PhimRieng.fileKeylayout(dump, "/dev/input/event7"))
    }

    @Test fun tenTrongKl_usageTruocMaQuet() {
        val kl = """
            # Remote
            key 116   POWER
            key 172   HOME
            key 240   PROG_RED        # mã quét chung: usage phải thắng
            key usage 0x000c0077 BUTTON_3
            key usage 0x0c0078   BUTTON_4      # Netflix
            key 0x1ea  BUTTON_5
        """.trimIndent()
        assertEquals("BUTTON_3", PhimRieng.tenTrongKl(kl, 240, 0xc0077))
        assertEquals("BUTTON_4", PhimRieng.tenTrongKl(kl, 240, 0xc0078))
        assertEquals("HOME", PhimRieng.tenTrongKl(kl, 172, null))
        assertEquals("BUTTON_5", PhimRieng.tenTrongKl(kl, 490, null))
        assertNull(PhimRieng.tenTrongKl(kl, 999, 0xc0999))
    }

    @Test fun muc_maQuetHoacNhan() {
        assertEquals("scan:490:org.smarttube.stable", PhimRieng.muc(PhimRieng.Nut("/dev/input/event2", 490), "org.smarttube.stable"))
        // Remote Bluetooth: mã quét 240 chung cho mọi nút app → gán theo nhãn phím Android.
        assertEquals("KEYCODE_BUTTON_3:org.smarttube.stable", PhimRieng.muc(PhimRieng.Nut("/dev/input/event4", 240, 0xc0077, tenAndroid = "BUTTON_3"), "org.smarttube.stable"))
        assertNull(PhimRieng.muc(PhimRieng.Nut("/dev/input/event4", 240, 0xc0077), "org.smarttube.stable"))
    }

    @Test fun laNutCoBan() {
        assertTrue(PhimRieng.laNutCoBan(PhimRieng.Nut("e", 172, tenAndroid = "HOME")))
        assertTrue(PhimRieng.laNutCoBan(PhimRieng.Nut("e", 103)))
        assertFalse(PhimRieng.laNutCoBan(PhimRieng.Nut("e", 240, tenAndroid = "BUTTON_3")))
        assertFalse(PhimRieng.laNutCoBan(PhimRieng.Nut("e", 490)))
        // Nhãn keylayout quyết định hơn bảng mã Linux.
        assertFalse(PhimRieng.laNutCoBan(PhimRieng.Nut("e", 172, tenAndroid = "BUTTON_3")))
    }

    @Test fun khoaVaDich() {
        assertEquals("scan:490", PhimRieng.khoaCua("scan:490:com.wbd.hbomax"))
        assertEquals("hold:scan:490", PhimRieng.khoaCua("hold:scan:490:org.xbmc.kodi"))
        assertEquals("KEYCODE_BUTTON_3", PhimRieng.khoaCua("KEYCODE_BUTTON_3:com.google.android.youtube.tv"))
        assertEquals("hold:190", PhimRieng.khoaCua(" hold:190:org.xbmc.kodi "))
        assertEquals("input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104",
            PhimRieng.dichCua("scan:227:input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104"))
        assertEquals("org.xbmc.kodi", PhimRieng.dichCua("hold:KEYCODE_BUTTON_8:org.xbmc.kodi"))
    }

    private fun mang(j: JSONObject, k: String): List<String> = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

    @Test fun apVao_giuCauHinhVaBangPhimMau() {
        val j = JSONObject().put("dock_apps", JSONArray(listOf("com.netflix.ninja"))).put("theme", "vgs")
            .put("app_keys", JSONArray(listOf("4065:com.google.android.youtube.tv", "scan:490:com.wbd.hbomax")))
        PhimRieng.apVao(j, listOf("scan:490:org.smarttube.stable"))
        assertEquals(listOf("4065:com.google.android.youtube.tv", "scan:490:org.smarttube.stable"), mang(j, "app_keys"))
        assertEquals(listOf("scan:490:org.smarttube.stable"), PhimRieng.docTu(j))
        assertEquals(listOf("com.netflix.ninja"), mang(j, "dock_apps"))
        assertEquals("vgs", j.getString("theme"))
    }

    @Test fun apVao_thayVaBoHet() {
        val j = JSONObject().put("app_keys", JSONArray(listOf("4062:com.netflix.ninja", "scan:490:app.cu")))
            .put(PhimRieng.KHOA, JSONArray(listOf("scan:490:app.cu")))
        PhimRieng.apVao(j, listOf("KEYCODE_BUTTON_3:org.smarttube.beta"))
        assertEquals(listOf("4062:com.netflix.ninja", "KEYCODE_BUTTON_3:org.smarttube.beta"), mang(j, "app_keys"))
        PhimRieng.apVao(j, emptyList())
        assertEquals(listOf("4062:com.netflix.ninja"), mang(j, "app_keys"))
        assertFalse(j.has(PhimRieng.KHOA))
        // Không còn mục nào thì bỏ hẳn app_keys (launcher dùng bảng rỗng mặc định).
        val k = JSONObject().put("app_keys", JSONArray(listOf("scan:490:app.cu"))).put(PhimRieng.KHOA, JSONArray(listOf("scan:490:app.cu")))
        PhimRieng.apVao(k, emptyList())
        assertFalse(k.has("app_keys"))
    }

    @Test fun apVao_keyFilterTatThiBoRoiTraLai() {
        val j = JSONObject().put("key_filter", false)
        PhimRieng.apVao(j, listOf("scan:490:org.smarttube.stable"))
        assertFalse(j.has("key_filter"))
        assertTrue(j.optBoolean(PhimRieng.KHOA_BO_TAT_LOC))
        PhimRieng.apVao(j, emptyList())
        assertEquals(false, j.getBoolean("key_filter"))
        assertFalse(j.has(PhimRieng.KHOA_BO_TAT_LOC))
        // key_filter=true do người dùng đặt thì để nguyên.
        val k = JSONObject().put("key_filter", true)
        PhimRieng.apVao(k, listOf("scan:490:x.y"))
        assertTrue(k.getBoolean("key_filter"))
    }
}
