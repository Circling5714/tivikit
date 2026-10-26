package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import java.io.File

/** Thời tiết theo phường/xã (góp ý 23/10/2026): dữ liệu assets/xa.json + tìm không dấu. */
class XaPhuongTest {
    private val ds: List<XaPhuong.Xa> = JSONObject(File("src/main/assets/xa.json").readText()).let { o ->
        val t = o.getJSONArray("tinh"); val tinh = (0 until t.length()).map { t.getJSONArray(it).getString(0) }
        val x = o.getJSONArray("xa")
        (0 until x.length()).map { i -> x.getJSONArray(i).let { XaPhuong.Xa(it.getString(0), tinh[it.getInt(1)], it.getDouble(2), it.getDouble(3)) } }
    }

    @Test fun du3321XaTrongVietNam() {
        assertEquals(3321, ds.size)
        assertTrue(ds.all { it.lat in 8.0..23.5 && it.lon in 102.0..117.5 })
    }

    /** Thành phố ngoài Việt Nam từ Open-Meteo geocoding (CleanTV 2.10.7): bỏ kết quả ở Việt Nam, ghi "vùng, nước". */
    @Test fun theGioi_boVietNam_ghiVungVaNuoc() {
        val json = """{"results":[
            {"name":"Osaka","latitude":34.69,"longitude":135.50,"country_code":"JP","country":"Nhật Bản","admin1":"Osaka"},
            {"name":"Hà Nội","latitude":21.02,"longitude":105.84,"country_code":"VN","country":"Việt Nam","admin1":"Hà Nội"},
            {"name":"Bali","latitude":-8.4,"longitude":115.1,"country_code":"ID","country":"Indonesia"}]}"""
        val kq = XaPhuong.tachTheGioi(json)
        assertEquals(listOf("Osaka", "Bali"), kq.map { it.ten })
        assertEquals("Osaka, Nhật Bản", kq[0].tinh)
        assertEquals("Indonesia", kq[1].tinh)
        assertEquals(34.69, kq[0].lat, 1e-9)
        assertTrue(XaPhuong.tachTheGioi("""{"generationtime_ms":0.5}""").isEmpty())
    }

    @Test fun timKhongDau() {
        val kq = XaPhuong.timXa(ds, "ben thanh")
        assertEquals("Phường Bến Thành", kq.first().ten)
        assertEquals("Bến Thành", kq.first().tenNgan)
        assertTrue(XaPhuong.timXa(ds, "Cửa Lò").any { it.tinh == "Tỉnh Nghệ An" })
        assertTrue(XaPhuong.timXa(ds, "dong anh ha noi").any { it.ten.contains("Đông Anh") })
    }
}
