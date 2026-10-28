package vn.tivikit.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cast SmartTube (28/10/2026): tên hiện trong nút Cast, lệnh đặt tên chỉ ASCII, đọc trạng thái app phụ. */
class CastSmartTubeTest {
    @Test fun chuanTen() {
        assertEquals("SmartTube phòng khách", CastSmartTube.chuanTen("  SmartTube \n phòng\tkhách  "))
        assertEquals(CastSmartTube.TEN_TOI_DA, CastSmartTube.chuanTen("x".repeat(100)).length)
    }

    @Test fun lenhDatTen_chiAscii_khongNhayDon() {
        val l = CastSmartTube.lenhDatTen("TV phòng khách 'Bố'")
        assertTrue(l.startsWith("content call --uri content://vn.tivikit.cast --method dat --arg '"))
        assertTrue(l.all { it.code < 0x80 })
        val json = l.substringAfter("--arg '").removeSuffix("'")
        assertFalse(json.contains('\''))
        assertEquals("TV phòng khách 'Bố'", JSONObject(json).getString("ten"))
    }

    @Test fun trangThai() {
        val j = JSONObject("""{"dichVuChay":true,"ten":"SmartTube · TX3 Mini","cong":8095,"daNoi":true,"dienThoai":1,
            "dangPhat":"eReMfUyYXB4","nhatKy":[{"luc":5,"viec":"điện thoại nối: iCM16prm"}]}""")
        val tt = CastSmartTube.trangThai(j)
        assertTrue(tt.dichVuChay && tt.daNoi)
        assertEquals(8095, tt.cong)
        assertEquals(1, tt.dienThoai)
        assertEquals("eReMfUyYXB4", tt.dangPhat)
        assertEquals(listOf(CastSmartTube.NhatKy(5, "điện thoại nối: iCM16prm")), tt.nhatKy)
        assertFalse(tt.biChe)
        assertFalse(CastSmartTube.trangThai(JSONObject("{}")).dichVuChay)
        assertTrue(CastSmartTube.trangThai(JSONObject("""{"dichVuChay":true,"biChe":true}""")).biChe)
    }

    @Test fun trangThai_dienThoai_app20() {
        val j = JSONObject("""{"dichVuChay":true,"ten":"Premium YTB","nhanCast":false,"dangNoi":["a"],
            "dienThoai":[{"id":"a","ten":"iCM16prm","taiKhoan":"Cao Minh","trangThai":"CHO_PHEP","hetHan":0,"lanDau":1,"lanCuoi":2},
                         {"id":"tk:Khách","ten":"","taiKhoan":"Khách","trangThai":"CHO_PHEP","hetHan":5,"lanDau":3,"lanCuoi":4},
                         {"id":"c","ten":"iPhone","taiKhoan":"","trangThai":"CHAN","hetHan":0,"lanDau":3,"lanCuoi":4}]}""")
        val tt = CastSmartTube.trangThai(j)
        assertTrue(tt.coQuanLy)
        assertFalse(tt.nhanCast)
        assertEquals(setOf("a"), tt.dangNoi)
        assertEquals("Cao Minh · iCM16prm", tt.dsMay[0].tenHien)
        assertTrue(tt.dsMay[1].quaHan(6))
        assertFalse(tt.dsMay[1].quaHan(4))
        assertTrue(tt.dsMay[2].biChan)
        // App Cast bản cũ (chưa có danh sách): không có phần quản lý, vẫn nhận cast.
        val cu = CastSmartTube.trangThai(JSONObject("""{"dichVuChay":true}"""))
        assertFalse(cu.coQuanLy)
        assertTrue(cu.nhanCast)
    }

    @Test fun lenhDienThoai_chiAscii() {
        val l = CastSmartTube.lenhDienThoai("tk:Khách 'A'", CastSmartTube.Lam.MOT_NGAY)
        assertTrue(l.startsWith("content call --uri content://vn.tivikit.cast --method dien-thoai --arg '"))
        assertTrue(l.all { it.code < 0x80 })
        val json = JSONObject(l.substringAfter("--arg '").removeSuffix("'"))
        assertEquals("tk:Khách 'A'", json.getString("id"))
        assertEquals("24h", json.getString("lam"))
        assertEquals("content call --uri content://vn.tivikit.cast --method dat --arg '{\"nhanCast\":false}'", CastSmartTube.lenhNhanCast(false))
    }

    @Test fun maTv() {
        assertEquals("123456789012", CastSmartTube.maTv("Result: Bundle[{ma=123456789012}]"))
        assertEquals("", CastSmartTube.maTv("Result: Bundle[{ma=}]"))
        assertEquals("", CastSmartTube.maTv("Error while accessing provider:vn.tivikit.cast"))
        assertEquals("123 456 789 012", CastSmartTube.hienMa("123456789012"))
    }
}
