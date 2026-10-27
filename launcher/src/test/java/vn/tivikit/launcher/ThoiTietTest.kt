package vn.tivikit.launcher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ThoiTietTest {
    @Test
    fun docCauTraLoiOpenMeteo() {
        val s = """{"latitude":21.0,"longitude":105.875,"current_units":{"temperature_2m":"°C"},
            "current":{"time":"2026-10-27T07:45","interval":900,"temperature_2m":25.6,"weather_code":3}}"""
        val h = ThoiTiet.docJson(s)!!
        assertEquals(26, h.nhietDo)
        assertEquals(3, h.ma)
        assertEquals("26°C Nhiều mây", ThoiTiet.dong(h, true))
        assertEquals("26°C Overcast", ThoiTiet.dong(h, false))
        assertNull(ThoiTiet.docJson("""{"error":true,"reason":"Latitude must be in range"}"""))
        assertNull(ThoiTiet.docJson("không phải json"))
    }

    @Test
    fun maThoiTiet() {
        assertEquals("Trời quang", ThoiTiet.chu(0, true))
        assertEquals("Mưa phùn", ThoiTiet.chu(53, true))
        assertEquals("Mưa to", ThoiTiet.chu(65, true))
        assertEquals("Mưa", ThoiTiet.chu(63, true))
        assertEquals("Mưa rào", ThoiTiet.chu(81, true))
        assertEquals("Dông", ThoiTiet.chu(95, true))
        assertEquals("", ThoiTiet.chu(42, true))
        assertEquals(ThoiTiet.Loai.MUA, ThoiTiet.loai(81))
        assertEquals(ThoiTiet.Loai.IT_MAY, ThoiTiet.loai(2))
        assertEquals(ThoiTiet.Loai.SUONG, ThoiTiet.loai(48))
        assertEquals(ThoiTiet.Loai.DONG, ThoiTiet.loai(99))
        assertEquals("-3°C", ThoiTiet.dong(ThoiTiet.HienTai(-3, 42), true))
    }

    @Test
    fun duongDanDungDauCham() {
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=21.0285&longitude=105.8542&current=temperature_2m,weather_code&timezone=auto",
            ThoiTiet.url(ThoiTiet.Noi("Hà Nội", 21.02851, 105.85418)))
    }

    @Test
    fun noiTuTiviKit() {
        val cu = JSONObject()
        CauHinh.tron(cu, JSONObject().put("noi", JSONObject().put("ten", "Phường Bến Thành").put("lat", 10.77).put("lon", 106.70)))
        assertEquals(ThoiTiet.Noi("Phường Bến Thành", 10.77, 106.70), CauHinh.noi(cu))
        // Toạ độ sai / gửi null: bỏ nơi, thôi hiện thời tiết.
        CauHinh.tron(cu, JSONObject().put("noi", JSONObject().put("ten", "x").put("lat", 120.0).put("lon", 10.0)))
        assertFalse(cu.has("noi"))
        CauHinh.tron(cu, JSONObject().put("noi", JSONObject().put("ten", "y").put("lat", 1.0).put("lon", 2.0)))
        CauHinh.tron(cu, JSONObject().put("noi", JSONObject.NULL))
        assertFalse(cu.has("noi"))
    }

    @Test
    fun dangPhatVaAppGanDay() {
        // 2 = tạm dừng, 3 = đang phát, 1 = dừng, 6 = đang tải.
        assertEquals(1, DangPhat.chonPhien(listOf(2, 3)))
        assertEquals(0, DangPhat.chonPhien(listOf(2, 1)))
        assertEquals(1, DangPhat.chonPhien(listOf(1, 6)))
        assertNull(DangPhat.chonPhien(listOf(1, 0)))
        assertNull(DangPhat.chonPhien(emptyList()))
        assertEquals("SmartTube · Alan Walker", DangPhat.dongPhu("SmartTube", " Alan Walker "))
        assertEquals("Spotify", DangPhat.dongPhu("Spotify", "Spotify"))
        assertEquals("Spotify", DangPhat.dongPhu("Spotify", null))

        val coTren = setOf("a.a", "b.b", "c.c", "d.d", "home.sach")
        // Có thống kê sử dụng: theo nó, bỏ launcher; không có thì theo lịch sử mở từ màn Home.
        assertEquals(listOf("c.c", "b.b"), Dock.ganDayTu(listOf("home.sach", "c.c", "a.a", "b.b"), listOf("d.d"), listOf("a.a"), coTren, setOf("home.sach")))
        assertEquals(listOf("d.d"), Dock.ganDayTu(emptyList(), listOf("d.d", "a.a"), listOf("a.a"), coTren, setOf("home.sach")))
    }
}
