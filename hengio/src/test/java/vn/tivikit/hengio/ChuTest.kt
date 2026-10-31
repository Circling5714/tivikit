package vn.tivikit.hengio

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Chữ trên màn Hẹn giờ của tivi (31/10/2026): tiếng Việt, nhật ký nói bằng lời như TiviKit. */
class ChuTest {
    private val cu = Locale.getDefault()
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun luc(y: Int, m: Int, d: Int, h: Int, p: Int) = ZonedDateTime.of(y, m, d, h, p, 0, 0, vn).toInstant().toEpochMilli()
    private val ten = { p: String -> if (p == "vn.tivikit.kenh") "Kênh nhanh: VTV1" else p }

    @Before fun tiengViet() = Locale.setDefault(Locale("vi"))

    @After fun traLai() = Locale.setDefault(cu)

    @Test fun ngayVaViec() {
        assertEquals("Mọi ngày", Chu.ngay((1..7).toSet()))
        assertEquals("T2–T6", Chu.ngay((1..5).toSet()))
        assertEquals("T7, CN", Chu.ngay(setOf(6, 7)))
        assertEquals("T2 T4 CN", Chu.ngay(setOf(7, 1, 3)))
        assertEquals("Bật tivi rồi mở Kênh nhanh: VTV1", Chu.viec(Viec.BAT, "vn.tivikit.kenh", ten))
        assertEquals("Tắt tivi", Chu.viec(Viec.TAT, null, ten))
        assertEquals("Mở org.smarttube.stable", Chu.viec(Viec.MO, "org.smarttube.stable", ten))
    }

    @Test fun gioVaThoiGianConLai() {
        val bay = luc(2026, 10, 31, 21, 14)
        assertEquals("21:44", Chu.gio(luc(2026, 10, 31, 21, 44), vn))
        assertEquals("hôm nay", Chu.ngayCua(luc(2026, 10, 31, 23, 0), bay, vn))
        assertEquals("ngày mai", Chu.ngayCua(luc(2026, 11, 1, 6, 30), bay, vn))
        assertEquals("Thứ Hai 02/11", Chu.ngayCua(luc(2026, 11, 2, 6, 30), bay, vn))
        assertEquals("1 giờ 30 phút", Chu.phut(90))
        assertEquals("2 giờ", Chu.phut(120))
        assertEquals("còn 30 phút", Chu.con(29 * 60_000L + 1))
    }

    @Test fun nhatKyNoiBangLoi() {
        assertEquals("Bật tivi – tivi đang chờ, đã bật lên", Chu.nhatKy("bat", "danh-thuc", ten))
        assertEquals("Mở Kênh nhanh: VTV1 – mở lại vì launcher vừa đè lên", Chu.nhatKy("mo:vn.tivikit.kenh", "mo-lai", ten))
        assertEquals("Tắt tivi – hoãn 30 phút", Chu.nhatKy("tat", "hoan-30", ten))
        assertEquals("Thử bật tivi – hẹn tự bật sau 120 giây", Chu.nhatKy("thu", "hen-120", ten))
        assertEquals("viec-moi – ma-moi", Chu.nhatKy("viec-moi", "ma-moi", ten))
    }
}
