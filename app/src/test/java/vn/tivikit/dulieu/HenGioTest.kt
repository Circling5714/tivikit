package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Hẹn giờ (25/10/2026): giờ người dùng gõ, lịch gửi cho app phụ trên tivi, trạng thái nó trả về. */
class HenGioTest {
    @Test fun docGio() {
        assertEquals(6 to 30, HenGio.docGio("6:30"))
        assertEquals(6 to 30, HenGio.docGio("06:30"))
        assertEquals(18 to 58, HenGio.docGio("18h58"))
        assertEquals(7 to 5, HenGio.docGio("705"))
        assertEquals(0 to 0, HenGio.docGio(" 0:00 "))
        assertNull(HenGio.docGio("24:00"))
        assertNull(HenGio.docGio("6:60"))
        assertNull(HenGio.docGio("sáu giờ"))
    }

    @Test fun json_khuHoi_tatKhongMangGoi() {
        val ds = listOf(
            HenGio.Viec(6, 30, setOf(1, 2, 3, 4, 5), HenGio.BAT, "vn.tivikit.kenh"),
            HenGio.Viec(23, 0, (1..7).toSet(), HenGio.TAT, "a.b"),
            HenGio.Viec(19, 0, setOf(6, 7), HenGio.MO, "org.smarttube.stable", bat = false),
        )
        val chu = HenGio.json(ds)
        assertFalse(chu.contains('\''))
        val lai = HenGio.docLich(JSONArray(chu))
        assertEquals(ds[0], lai[0]); assertEquals(ds[2], lai[2])
        assertNull(lai[1].goi)
        assertEquals("06:30", lai[0].gioChu)
    }

    @Test fun sapXep_theoGio() {
        val a = HenGio.Viec(23, 0, setOf(1), HenGio.TAT)
        val b = HenGio.Viec(6, 30, setOf(1), HenGio.BAT)
        val c = HenGio.Viec(6, 30, setOf(1), HenGio.MO, "x.y")
        assertEquals(listOf(b, c, a), HenGio.sapXep(listOf(a, c, b)))
    }

    @Test fun trangThai() {
        val j = JSONObject("""{"lich":[{"gio":6,"phut":30,"ngay":[1,2],"loai":"bat"}],"keTiep":1792971000000,"hoanDen":0,
            "dichVuChay":true,"chinhXac":false,"dangBat":true,"bayGio":1793000000000,"muiGio":"Asia/Ho_Chi_Minh",
            "nhatKy":[{"luc":1793000000000,"viec":"thu","kq":"danh-thuc"}]}""")
        val t = HenGio.trangThai(j)
        assertEquals(1, t.lich.size)
        assertTrue(t.dichVuChay)
        assertFalse(t.chinhXac)
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), t.muiGio)
        assertEquals("danh-thuc", t.nhatKy.single().kq)
        // 1792971000000 ms = 26/10/2026 06:30 giờ Việt Nam, Thứ Hai.
        assertEquals("06:30" to 1, HenGio.gioCua(t.keTiep, t.muiGio))
    }
}
