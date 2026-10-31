package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

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
        // Chỉ có giờ: phút 00; phút gõ dở thì chưa đúng.
        assertEquals(6 to 0, HenGio.docGio("06"))
        assertEquals(21 to 0, HenGio.docGio("21"))
        assertNull(HenGio.docGio("24"))
        assertNull(HenGio.docGio("06:3"))
    }

    /** Ô giờ tự chèn dấu hai chấm khi gõ (31/10/2026). */
    @Test fun oGio_tuChenHaiCham() {
        assertEquals("1", HenGio.oGio("1"))
        assertEquals("18", HenGio.oGio("18"))
        assertEquals("18:3", HenGio.oGio("183"))
        assertEquals("18:30", HenGio.oGio("1830"))
        assertEquals("18:30", HenGio.oGio("18305"))
        // Chữ số đầu từ 3 trở lên là giờ một chữ số.
        assertEquals("06", HenGio.oGio("6"))
        assertEquals("06:30", HenGio.oGio("630"))
        // Xoá lùi qua dấu hai chấm, dán chữ có dấu phân cách.
        assertEquals("06", HenGio.oGio("06:"))
        assertEquals("06:30", HenGio.oGio("6:30"))
        assertEquals("18:30", HenGio.oGio("18h30"))
        assertEquals("", HenGio.oGio("ab"))
        assertFalse(HenGio.gioSai("23:59"))
        assertFalse(HenGio.gioSai("2"))
        assertTrue(HenGio.gioSai("24"))
        assertTrue(HenGio.gioSai("06:7"))
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

    /** Nhật ký app phụ nói bằng lời (31/10/2026); mã chưa biết thì hiện nguyên. */
    @Test fun moTaNhatKy() {
        val ten = { p: String -> if (p == "vn.tivikit.kenh") "Kênh nhanh: VTV1" else p }
        fun mt(viec: String, kq: String) = HenGio.moTa(HenGio.NhatKy(0, viec, kq), ten)
        assertEquals("Bật tivi – tivi đang chờ, đã bật lên", mt("bat", "danh-thuc"))
        assertEquals("Thử bật tivi – hẹn tự bật sau 120 giây", mt("thu", "hen-120"))
        assertEquals("Thử bật tivi – tivi vẫn đang bật, chưa tắt bằng remote", mt("thu", "dang-bat"))
        assertEquals("Mở Kênh nhanh: VTV1 – mở lại vì launcher vừa đè lên", mt("mo:vn.tivikit.kenh", "mo-lai"))
        assertEquals("Mở org.smarttube.stable – đã mở", mt("mo:org.smarttube.stable", "ok"))
        assertEquals("Tắt tivi – hoãn 30 phút", mt("tat", "hoan-30"))
        assertEquals("Tắt tivi – đã tắt", mt("tat", "ok"))
        // App phụ 1.1.1 ghi việc lỡ giờ chỉ bằng loại việc.
        assertEquals("Mở app – bỏ qua vì trễ quá 10 phút (tivi ngủ sâu hay vừa đổi giờ)", mt("mo", "lo-gio"))
        assertEquals("viec-moi – ma-moi", mt("viec-moi", "ma-moi"))
    }

    /** Việc một lần (31/10/2026): trạng thái app phụ 1.2, lệnh gửi đi, tên app gửi tivi. */
    @Test fun motLan() {
        val j = JSONObject("""{"lich":[],"keTiep":0,"phienBan":"1.2.0","bayGio":1793455200000,"muiGio":"Asia/Ho_Chi_Minh","motLan":[
            {"id":"tat-1793457000000","luc":1793457000000,"loai":"tat"},
            {"id":"bat-1793400000000","luc":1793400000000,"loai":"bat","goi":"vn.tivikit.kenh"},
            {"id":"","luc":1,"loai":"tat"}]}""")
        val tt = HenGio.trangThai(j)
        assertTrue(tt.coManTv)
        assertEquals(listOf("bat-1793400000000", "tat-1793457000000"), tt.motLan.map { it.id })
        assertEquals("vn.tivikit.kenh", tt.motLan[0].goi)
        // App phụ 1.1 không có motLan: chưa có màn trên tivi.
        assertFalse(HenGio.trangThai(JSONObject("""{"lich":[]}""")).coManTv)
        val tat = JSONObject(HenGio.jsonTatSau(30))
        assertEquals("tat", tat.getString("loai")); assertEquals(30, tat.getInt("sauPhut"))
        val bat = JSONObject(HenGio.jsonMotLanLuc(HenGio.BAT, 6, 30, "vn.tivikit.kenh"))
        assertEquals(6, bat.getInt("gio")); assertEquals(30, bat.getInt("phut")); assertEquals("vn.tivikit.kenh", bat.getString("goi"))
        assertFalse(JSONObject(HenGio.jsonMotLanLuc(HenGio.TAT, 22, 0, "a.b")).has("goi"))
        // Tên app gửi tivi: chỉ ASCII (libadb), không có nháy đơn, đọc lại vẫn đúng chữ.
        val ten = HenGio.jsonTenApp(mapOf("vn.tivikit.kenh" to "Kênh nhanh: VTV1", "a.b" to "Bà's"))
        assertTrue(ten.all { it.code < 128 })
        assertFalse(ten.contains('\''))
        assertEquals("Kênh nhanh: VTV1", JSONObject(ten).getString("vn.tivikit.kenh"))
        assertEquals("Bà's", JSONObject(ten).getString("a.b"))
    }

    @Test fun ngayCua() {
        val vn = ZoneId.of("Asia/Ho_Chi_Minh")
        fun luc(m: Int, d: Int, h: Int) = ZonedDateTime.of(2026, m, d, h, 0, 0, 0, vn).toInstant().toEpochMilli()
        val bay = luc(10, 31, 21)
        assertEquals("hôm nay", HenGio.ngayCua(luc(10, 31, 23), bay, vn))
        assertEquals("ngày mai", HenGio.ngayCua(luc(11, 1, 6), bay, vn))
        assertEquals("Thứ Hai 02/11", HenGio.ngayCua(luc(11, 2, 6), bay, vn))
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
