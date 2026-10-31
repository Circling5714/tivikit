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
        // Map Voice 1.1: TizenTube, FPT Play, câu riêng có dấu (lệnh vẫn thuần ASCII, không nháy đơn).
        val c2 = MapVoice.CauHinh(appYouTube = MapVoice.TIZENTUBE, appKenh = MapVoice.APP_FPT,
            cauRieng = listOf(MapVoice.CauRieng("giá vàng", MapVoice.DICH_TRO_LY), MapVoice.CauRieng("phim bộ", "net.fptplay.ottbox")))
        val l = MapVoice.lenhDat(c2)
        assertTrue(l.all { it.code < 0x80 })
        assertEquals(2, l.count { it == '\'' })
        assertEquals(c2, MapVoice.cauHinh(JSONObject(arg(l))))
    }

    @Test
    fun chuCauRiengSach() {
        assertEquals("giá vàng sjc", MapVoice.chuSach("  giá   vàng 'SJC' ".lowercase()))
        assertEquals("a b", MapVoice.chuSach("a|b"))
        assertEquals(MapVoice.CHU_TOI_DA, MapVoice.chuSach("x".repeat(100)).length)
    }

    @Test
    fun dsKenhCoFptPlay() {
        val fpt = listOf(KenhFpt.Kenh("vtv1-hd", "VTV1 HD", "Kênh cơ bản", "1", ""), KenhFpt.Kenh("hanoitv-1", "HaNoiTV 1", "Kênh cơ bản", "29", ""),
            KenhFpt.Kenh("vtv1-hd", "VTV1 HD", "", "", ""))
        val j = JSONObject(MapVoice.dsKenh(emptyList(), KenhNhanh.GOI_Y + KenhNhanh.Kenh("", "VTV1", "vtv1-hd", KenhNhanh.LOAI_FPT), fpt))
        val a = j.getJSONArray("fptplay")
        assertEquals(2, a.length()) // mã trùng bỏ
        assertEquals("vtv1-hd", a.getJSONObject(0).getString("ma"))
        assertEquals("HaNoiTV 1", a.getJSONObject(1).getString("ten"))
        assertEquals(KenhNhanh.GOI_Y.size, j.getJSONArray("tv360").length()) // ô FPT Play không vào danh sách TV360
        // Tivi không có FPT Play: không gửi khoá fptplay (Map Voice 1.0 cũng đọc được).
        assertFalse(JSONObject(MapVoice.dsKenh(emptyList(), KenhNhanh.GOI_Y)).has("fptplay"))
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
        // Map Voice 1.1 báo gói app sẽ mở / đã mở.
        assertEquals(MapVoice.Viec("yt-tim", "nhạc piano", goi = MapVoice.TIZENTUBE),
            MapVoice.viecTrong("Result: Bundle[{app=io.gh.reisxd.tizentube.cobalt, viec=yt-tim|nhạc piano, ok=1}]"))
        assertEquals(MapVoice.Viec("kenh", "VTV3 HD", MapVoice.APP_FPT, "net.fptplay.ottbox"),
            MapVoice.viecTrong("Result: Bundle[{ok=1, viec=kenh|fptplay|VTV3 HD, app=net.fptplay.ottbox}]"))
        assertEquals(MapVoice.Viec("tro-ly", "giá vàng"), MapVoice.viecTrong("Result: Bundle[{viec=tro-ly|giá vàng, ok=1}]"))
        assertEquals(MapVoice.Viec("mo-app", "net.fptplay.ottbox", goi = "net.fptplay.ottbox"),
            MapVoice.viecTrong("Result: Bundle[{app=net.fptplay.ottbox, viec=mo-app|net.fptplay.ottbox, ok=1}]"))
    }

    @Test
    fun docTrangThai() {
        val tt = MapVoice.trangThai(JSONObject("""{"phienBan":"1.0","dichVuChay":true,"adb":"da-noi","troLy":true,"appYouTube":"org.smarttube.stable",
            "cauHinh":{"bat":true,"cachMo":"phat-luon","appKenh":"tv360"},"soKenh":140,
            "nhatKy":[{"luc":5,"cau":"mở VTV3","viec":"kenh|vtvgo|VTV3"},{"luc":6,"cau":"karaoke","viec":"yt-tim|karaoke","app":"io.gh.reisxd.tizentube.cobalt"}]}"""))
        assertTrue(tt.dichVuChay)
        assertEquals("da-noi", tt.adb)
        assertEquals(MapVoice.CachMo.PHAT_LUON, tt.cauHinh.cachMo)
        assertEquals(MapVoice.APP_TV360, tt.cauHinh.appKenh)
        assertEquals(140, tt.soKenh)
        assertEquals(listOf(MapVoice.NhatKy(5, "mở VTV3", MapVoice.Viec("kenh", "VTV3", "vtvgo")),
            MapVoice.NhatKy(6, "karaoke", MapVoice.Viec("yt-tim", "karaoke", goi = MapVoice.TIZENTUBE))), tt.nhatKy)
        assertFalse(MapVoice.trangThai(JSONObject("{}")).dichVuChay)
    }
}
