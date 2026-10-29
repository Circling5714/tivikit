package vn.tivikit.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapVoiceTest {
    /** Phần --arg '…' của lệnh content call. */
    private fun arg(lenh: String) = lenh.substringAfter("--arg '").substringBeforeLast("'")

    @Test
    fun lenhChiAsciiKhongNhayDon() {
        listOf(
            MapVoice.lenhThu("mở nhạc 'Bống bống bang bang' trên YouTube"),
            MapVoice.lenhXem("xem kênh Hà Nội 1"),
            MapVoice.lenhDat(MapVoice.CauHinh(cachMo = MapVoice.CachMo.PHAT_LUON, appKenh = MapVoice.APP_TV360)),
            MapVoice.lenhKenh(MapVoice.dsKenh(listOf(KenhVtvGo.Kenh("Truyền hình Hà Nội 1", "hn1", "Địa phương", "", false)), KenhNhanh.GOI_Y)),
        ).forEach { l ->
            assertTrue(l, l.all { it.code < 0x80 })
            assertEquals(l, 2, l.count { it == '\'' })
            assertTrue(l.startsWith("content call --uri content://vn.tivikit.mapvoice --method "))
        }
        assertEquals("mở nhạc 'Bống bống bang bang' trên YouTube", JSONObject(arg(MapVoice.lenhThu("mở nhạc 'Bống bống bang bang' trên YouTube"))).getString("cau"))
        assertEquals("content call --uri content://vn.tivikit.mapvoice --method noi-lai", MapVoice.LENH_NOI_LAI)
    }

    @Test
    fun cauHinhQuaLaiJson() {
        val c = MapVoice.CauHinh(bat = false, appYouTube = "org.smarttube.beta", cachMo = MapVoice.CachMo.TIM_KIEM, nhacVideo = false, kenh = false, appKenh = MapVoice.APP_TV360)
        assertEquals(c, MapVoice.cauHinh(JSONObject(arg(MapVoice.lenhDat(c)))))
        assertEquals(MapVoice.CauHinh(), MapVoice.cauHinh(JSONObject("{}")))
        assertNull(MapVoice.cauHinh(null))
    }

    @Test
    fun dsKenhCoCaHaiApp() {
        val j = JSONObject(MapVoice.dsKenh(
            listOf(KenhVtvGo.Kenh("Truyền hình Hà Nội 1", "hn1", "", "", false), KenhVtvGo.Kenh("VTV3", "3", "", "", false), KenhVtvGo.Kenh("VTV3", "3", "", "", false)),
            KenhNhanh.GOI_Y + KenhNhanh.Kenh("", "VTV1", "1") + KenhNhanh.Kenh("003", "VTV3"),
        ))
        val go = j.getJSONArray("vtvgo")
        assertEquals(2, go.length())
        assertEquals("Hà Nội 1", go.getJSONObject(0).getString("ten"))
        val tv360 = j.getJSONArray("tv360")
        assertEquals(KenhNhanh.GOI_Y.size, tv360.length()) // ô VTVgo bỏ, số kênh trùng bỏ
        assertEquals("001", tv360.getJSONObject(0).getString("so"))
    }

    @Test
    fun docViec() {
        assertEquals(MapVoice.Viec("yt-phat", "nhạc piano"), MapVoice.viecTrong("Result: Bundle[{viec=yt-phat|nhạc piano, ok=1}]"))
        assertEquals(MapVoice.Viec("kenh", "VTV3", "tv360"), MapVoice.viecTrong("Result: Bundle[{ok=1, viec=kenh|tv360|VTV3}]"))
        assertEquals(MapVoice.Viec("bo-qua", ""), MapVoice.viec("bo-qua|"))
        assertNull(MapVoice.viecTrong("Result: Bundle[{loi=dich-vu-tat}]"))
        assertEquals("dich-vu-tat", MapVoice.loiTrong("Result: Bundle[{loi=dich-vu-tat}]"))
        assertEquals("", MapVoice.loiTrong("Result: Bundle[{ok=1}]"))
    }

    @Test
    fun docTrangThai() {
        val tt = MapVoice.trangThai(JSONObject("""{"phienBan":"1.0","dichVuChay":true,"adb":"da-noi","troLy":true,"appYouTube":"org.smarttube.stable",
            "cauHinh":{"bat":true,"cachMo":"phat-luon","appKenh":"tv360"},"soKenh":140,
            "nhatKy":[{"luc":5,"cau":"mở VTV3","viec":"kenh|vtvgo|VTV3"}]}"""))
        assertTrue(tt.dichVuChay)
        assertEquals("da-noi", tt.adb)
        assertEquals(MapVoice.CachMo.PHAT_LUON, tt.cauHinh.cachMo)
        assertEquals(MapVoice.APP_TV360, tt.cauHinh.appKenh)
        assertEquals(140, tt.soKenh)
        assertEquals(listOf(MapVoice.NhatKy(5, "mở VTV3", MapVoice.Viec("kenh", "VTV3", "vtvgo"))), tt.nhatKy)
        assertFalse(MapVoice.trangThai(JSONObject("{}")).dichVuChay)
    }
}
