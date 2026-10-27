package vn.tivikit.launcher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockTest {
    @Test
    fun dockMacDinhLayAppGoiYCoTrenTivi() {
        val coTren = listOf("com.android.vending", "com.netflix.ninja", "org.smarttube.stable", "com.viettel.tv360.tv", "org.videolan.vlc")
        assertEquals(listOf("org.smarttube.stable", "com.viettel.tv360.tv", "com.netflix.ninja"), Dock.macDinh(coTren))
        // Không có app gợi ý nào: lấy các app đầu danh sách.
        assertEquals(listOf("a.b", "c.d"), Dock.macDinh(listOf("a.b", "c.d")))
    }

    @Test
    fun themBoDoi() {
        val ds = listOf("a.a", "b.b", "c.c")
        assertEquals(ds, Dock.them(ds, "b.b"))
        assertEquals(ds + "d.d", Dock.them(ds, "d.d"))
        assertEquals(listOf("a.a", "c.c"), Dock.bo(ds, "b.b"))
        assertEquals(listOf("b.b", "a.a", "c.c"), Dock.doi(ds, "b.b", -1))
        assertEquals(listOf("a.a", "c.c", "b.b"), Dock.doi(ds, "b.b", 1))
        assertEquals(ds, Dock.doi(ds, "a.a", -1))
        assertEquals(ds, Dock.doi(ds, "x.x", 1))
        assertEquals(listOf("a.a", "c.c"), Dock.conLai(ds, setOf("a.a", "c.c")))
    }

    @Test
    fun ganDay() {
        var ls = emptyList<String>()
        for (g in listOf("a.a", "b.b", "a.a", "c.c", "d.d")) ls = Dock.ghiMo(ls, g)
        assertEquals(listOf("d.d", "c.c", "a.a", "b.b"), ls)
        assertEquals(listOf("c.c", "b.b"), Dock.ganDay(ls, dock = listOf("d.d", "a.a"), coTren = setOf("a.a", "b.b", "c.c", "d.d")))
        assertEquals(listOf("c.c"), Dock.ganDay(ls, listOf("d.d"), setOf("c.c", "d.d"), toiDa = 1))
    }

    @Test
    fun tenGoiHopLe() {
        assertTrue(Dock.hopLe("org.smarttube.stable"))
        assertFalse(Dock.hopLe("khong-cham"))
        assertFalse(Dock.hopLe("a.b; rm -rf /"))
    }

    @Test
    fun tronCauHinhTuTiviKit() {
        val cu = JSONObject().put("ten", "Cũ").put("gan_day", JSONArray(listOf("x.y")))
        val moi = JSONObject().put("ten", "  Nhà bác Hùng  ").put("dock", JSONArray(listOf("org.smarttube.stable", "sai tên", "org.smarttube.stable")))
            .put("ngon_ngu", "en").put("gan_day", JSONArray(listOf("a.b")))
        assertEquals(listOf("ten", "dock", "ngon_ngu"), CauHinh.tron(cu, moi))
        assertEquals("Nhà bác Hùng", cu.getString("ten"))
        assertEquals(listOf("org.smarttube.stable"), CauHinh.dsGoi(cu.getJSONArray("dock")))
        assertEquals("en", cu.getString("ngon_ngu"))
        // gan_day chỉ màn Home ghi.
        assertEquals(listOf("x.y"), CauHinh.dsGoi(cu.getJSONArray("gan_day")))
        // Tên rỗng / ngôn ngữ lạ: bỏ khoá.
        CauHinh.tron(cu, JSONObject().put("ten", "").put("ngon_ngu", "fr"))
        assertFalse(cu.has("ten"))
        assertFalse(cu.has("ngon_ngu"))
        CauHinh.tron(cu, JSONObject().put("ten", "x".repeat(60)))
        assertEquals(CauHinh.TEN_TOI_DA, cu.getString("ten").length)
    }

    @Test
    fun tenRiengCuaAppTuTiviKit() {
        val cu = JSONObject()
        val moi = JSONObject().put("ten_app", JSONObject().put("vn.tivikit.kenh", " VTV1 ").put("vn.tivikit.kenh2", "VTV2")
            .put("sai tên", "X").put("vn.tivikit.kenh3", 5))
        assertEquals(listOf("ten_app"), CauHinh.tron(cu, moi))
        assertEquals(mapOf("vn.tivikit.kenh" to "VTV1", "vn.tivikit.kenh2" to "VTV2"), CauHinh.tenApp(cu))
        // Gộp vào tên đã có; "" bỏ tên của gói đó, hết tên thì bỏ cả khoá.
        CauHinh.tron(cu, JSONObject().put("ten_app", JSONObject().put("vn.tivikit.kenh2", "").put("vn.tivikit.kenh4", "x".repeat(60))))
        assertEquals(mapOf("vn.tivikit.kenh" to "VTV1", "vn.tivikit.kenh4" to "x".repeat(CauHinh.TEN_TOI_DA)), CauHinh.tenApp(cu))
        CauHinh.tron(cu, JSONObject().put("ten_app", JSONObject().put("vn.tivikit.kenh", "").put("vn.tivikit.kenh4", "")))
        assertFalse(cu.has("ten_app"))
        assertEquals(emptyMap<String, String>(), CauHinh.tenApp(cu))

        // Màn Home: tên riêng thay tên app rồi xếp lại theo tên.
        val ds = listOf(AppTv("com.viettel.tv360.tv", "TV360"), AppTv("org.smarttube.stable", "SmartTube"), AppTv("vn.tivikit.kenh", "Kênh nhanh 1"))
        assertEquals(listOf("SmartTube", "TV360", "VTV1"), DanhSachApp.doiTen(ds, mapOf("vn.tivikit.kenh" to "VTV1")).map { it.ten })
        assertEquals(ds, DanhSachApp.doiTen(ds, mapOf("vn.khac" to "Khác")))
    }
}
