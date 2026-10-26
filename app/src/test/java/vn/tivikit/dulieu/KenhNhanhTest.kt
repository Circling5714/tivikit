package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kênh nhanh (25/10/2026): số kênh TV360, các ô (mỗi ô một gói), tên ô qua app_labels, ô trong dock_apps. */
class KenhNhanhTest {
    private fun mang(j: JSONObject, k: String): List<String> = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    private val o3 = KenhNhanh.goiCua(3)

    @Test fun chuanSoVaTenSach() {
        assertEquals("001", KenhNhanh.chuanSo("1"))
        assertEquals("098", KenhNhanh.chuanSo("098"))
        assertNull(KenhNhanh.chuanSo("0"))
        assertNull(KenhNhanh.chuanSo("VTV1"))
        assertEquals("Hà Nội 1", KenhNhanh.tenSach("  Hà  Nội 1 "))
        assertEquals("VTV1 HD x", KenhNhanh.tenSach("VTV1, HD='x'"))
        // Gợi ý: số đúng dạng, không trùng số.
        assertTrue(KenhNhanh.GOI_Y.all { KenhNhanh.chuanSo(it.so) == it.so })
        assertEquals(KenhNhanh.GOI_Y.size, KenhNhanh.GOI_Y.map { it.so }.toSet().size)
    }

    @Test fun cacO() {
        // Ô 1 giữ tên gói của bản một ô trước đây; tên gói, file APK, màn mở khớp với flavor o1 … o6 của module kenh/.
        assertEquals("vn.tivikit.kenh", KenhNhanh.goiCua(1))
        assertEquals("vn.tivikit.kenh3", o3)
        assertEquals("kenh.apk", KenhNhanh.apkCua(1))
        assertEquals("kenh6.apk", KenhNhanh.apkCua(6))
        assertEquals("vn.tivikit.kenh2/vn.tivikit.kenh.MoKenh", KenhNhanh.moCua(2))
        assertEquals(3, KenhNhanh.oCua(o3))
        assertNull(KenhNhanh.oCua("vn.tivikit.kenh7"))
        assertNull(KenhNhanh.oCua("vn.tivikit.khoa"))
        assertFalse(KenhNhanh.laGoiKenh(KenhNhanh.TV360))
    }

    @Test fun json_khuHoi() {
        val k = KenhNhanh.Kenh("073", "HTV7 HD")
        assertEquals(k, KenhNhanh.tuJson(JSONObject(KenhNhanh.json(k))))
        assertNull(KenhNhanh.tuJson(JSONObject("""{"so":"x"}""")))
        assertNull(KenhNhanh.tuJson(null))
    }

    @Test fun tenO_nhieuO_giuTenCuaAppKhac() {
        val j = JSONObject().put("app_labels", JSONArray(listOf("com.telekom.onetv.tv.pl=TV")))
        KenhNhanh.datTen(j, KenhNhanh.GOI, "VTV1")
        KenhNhanh.datTen(j, o3, "HTV7 HD")
        assertEquals(listOf("com.telekom.onetv.tv.pl=TV", "vn.tivikit.kenh=VTV1", "vn.tivikit.kenh3=HTV7 HD"), mang(j, "app_labels"))
        assertEquals(mapOf(KenhNhanh.GOI to "VTV1", o3 to "HTV7 HD"), KenhNhanh.tenTrong(j))
        KenhNhanh.datTen(j, KenhNhanh.GOI, "VTV3")
        assertEquals("VTV3", KenhNhanh.tenTrong(j)[KenhNhanh.GOI])
        KenhNhanh.datTen(j, o3, null)
        assertEquals(listOf("com.telekom.onetv.tv.pl=TV", "vn.tivikit.kenh=VTV3"), mang(j, "app_labels"))
        val k = KenhNhanh.datTen(JSONObject(), KenhNhanh.GOI, "VTV1").let { KenhNhanh.datTen(it, KenhNhanh.GOI, null) }
        assertFalse(k.has("app_labels"))
    }

    @Test fun dock() {
        val j = JSONObject().put("dock_apps", JSONArray(listOf("com.netflix.ninja", "com.viettel.tv360.tv")))
        assertEquals(true, KenhNhanh.datDock(j, KenhNhanh.GOI, true, 8))
        assertEquals(true, KenhNhanh.datDock(j, o3, true, 8))
        assertEquals(listOf("com.netflix.ninja", "com.viettel.tv360.tv", KenhNhanh.GOI, o3), mang(j, "dock_apps"))
        assertEquals(setOf("com.netflix.ninja", "com.viettel.tv360.tv", KenhNhanh.GOI, o3), KenhNhanh.trongDock(j))
        // Có rồi thì giữ chỗ, không thêm lần nữa.
        assertEquals(true, KenhNhanh.datDock(j, KenhNhanh.GOI, true, 4))
        assertEquals(4, mang(j, "dock_apps").size)
        assertEquals(true, KenhNhanh.datDock(j, KenhNhanh.GOI, false, 8))
        assertEquals(listOf("com.netflix.ninja", "com.viettel.tv360.tv", o3), mang(j, "dock_apps"))
        assertEquals(false, KenhNhanh.datDock(j, KenhNhanh.goiCua(2), true, 3))
        // Home sạch đang dùng dock mặc định: không đụng vào.
        assertNull(KenhNhanh.datDock(JSONObject(), KenhNhanh.GOI, true, 8))
    }
}
