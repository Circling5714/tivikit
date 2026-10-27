package vn.tivikit.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiviKitLauncherTest {
    @Test
    fun cauHinhKhongCoNhayDon() {
        val j = TiviKitLauncher.cauHinh(" Nhà O'Brien ", tiengViet = true)
        assertFalse(j.contains('\''))
        val o = JSONObject(j)
        assertEquals("Nhà O’Brien", o.getString("ten"))
        assertEquals("vi", o.getString("ngon_ngu"))
        assertEquals("en", JSONObject(TiviKitLauncher.cauHinh("", tiengViet = false)).getString("ngon_ngu"))
        assertEquals(40, JSONObject(TiviKitLauncher.cauHinh("x".repeat(80), true)).getString("ten").length)
        assertTrue(TiviKitLauncher.lenhDat(j).startsWith("content call --uri content://vn.tivikit.launcher --method dat --arg '{"))
    }

    @Test
    fun layTuHomeSachChoLanCaiDau() {
        val j = JSONObject().put("greeting_name", " Nhà bác Hùng ").put("weather_place", "Phường Bến Thành").put("weather_lat", 10.77)
            .put("weather_lon", 106.70).put("dock_apps", org.json.JSONArray(listOf("vgs.kenh.vtv1", "org.smarttube.stable", "vn.tivikit.kenh2", "")))
        val hs = TiviKitLauncher.tuHomeSach(j)
        assertEquals("Nhà bác Hùng", hs.ten)
        assertEquals(ThanhPho("Phường Bến Thành", 10.77, 106.70), hs.noi)
        assertEquals(listOf("org.smarttube.stable", "vn.tivikit.kenh2"), hs.dock)
        assertEquals(TiviKitLauncher.TuHomeSach(), TiviKitLauncher.tuHomeSach(null))
        // Thiếu toạ độ: không có nơi.
        assertNull(TiviKitLauncher.tuHomeSach(JSONObject().put("weather_place", "Huế")).noi)

        val o = JSONObject(TiviKitLauncher.cauHinh(hs.ten, true, hs.noi, hs.dock))
        assertEquals("Phường Bến Thành", o.getJSONObject("noi").getString("ten"))
        assertEquals(10.77, o.getJSONObject("noi").getDouble("lat"), 1e-9)
        assertEquals(2, o.getJSONArray("dock").length())
        // Cập nhật: không gửi dock, không có nơi thì không gửi nơi.
        val capNhat = JSONObject(TiviKitLauncher.cauHinh("", false))
        assertFalse(capNhat.has("dock"))
        assertFalse(capNhat.has("noi"))
        assertTrue(TiviKitLauncher.LENH_CAP_QUYEN.contains("allow_listener vn.tivikit.launcher/vn.tivikit.launcher.NgheNhac"))
    }

    @Test
    fun tuyChinhTuDienThoai() {
        // Đọc lại cấu hình launcher để điền sẵn phần Tuỳ chỉnh.
        val j = JSONObject("""{"ban":"1.0","ma":1,"homeBat":true,"nen":81234,"cauHinh":{"ten":"hihi","noi":{"ten":"TP.HCM","lat":10.7769,"lon":106.7009}}}""")
        val k = TiviKitLauncher.trangThai(j, canCapNhat = false)
        assertEquals("hihi", k.ten)
        assertEquals(ThanhPho("TP.HCM", 10.7769, 106.7009), k.noi)
        assertTrue(k.coNenRieng)
        assertTrue(k.homeBat)
        val trong = TiviKitLauncher.trangThai(JSONObject("""{"ban":"1.0","cauHinh":{}}"""), canCapNhat = true)
        assertNull(trong.noi)
        assertFalse(trong.coNenRieng)
        assertTrue(trong.canCapNhat)
        // "Không hiện" thời tiết: gửi noi = null để launcher bỏ nơi đã lưu; lúc cài / cập nhật thì không đụng tới.
        assertTrue(JSONObject(TiviKitLauncher.cauHinh("", true, null, boNoiNeuRong = true)).isNull("noi"))
        assertFalse(JSONObject(TiviKitLauncher.cauHinh("", true, null)).has("noi"))
        // Ảnh có sẵn đều có trong assets của app, ảnh đầu là ảnh mặc định của launcher.
        assertEquals(TiviKitLauncher.NEN_MAC_DINH, TiviKitLauncher.ANH_CO_SAN.first().asset)
        TiviKitLauncher.ANH_CO_SAN.forEach { assertTrue(it.asset, java.io.File("src/main/assets/${it.asset}").isFile) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun lenhDatTuChoiNhayDon() {
        TiviKitLauncher.lenhDat("{\"ten\":\"a'b\"}")
    }

    @Test
    fun docKetQuaCuaLauncher() {
        val out = "Result: Bundle[{json={\"ban\":\"1.0\",\"ma\":1,\"homeBat\":false,\"cauHinh\":{\"dock\":[\"org.smarttube.stable\"]}}}]"
        val j = TiviKitLauncher.docKetQua(out)!!
        assertEquals("1.0", j.getString("ban"))
        assertFalse(j.getBoolean("homeBat"))
        assertNull(TiviKitLauncher.docKetQua("Error: Unknown authority vn.tivikit.launcher"))
        assertFalse(TiviKitLauncher.TrangThai(null).daCai)
        assertTrue(TiviKitLauncher.TrangThai("1.0").daCai)
    }
}
