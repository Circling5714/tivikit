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
