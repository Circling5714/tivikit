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

    @Test fun timKhongDau() {
        val kq = XaPhuong.timXa(ds, "ben thanh")
        assertEquals("Phường Bến Thành", kq.first().ten)
        assertEquals("Bến Thành", kq.first().tenNgan)
        assertTrue(XaPhuong.timXa(ds, "Cửa Lò").any { it.tinh == "Tỉnh Nghệ An" })
        assertTrue(XaPhuong.timXa(ds, "dong anh ha noi").any { it.ten.contains("Đông Anh") })
    }
}
