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
        assertFalse(CastSmartTube.trangThai(JSONObject("{}")).dichVuChay)
    }
}
