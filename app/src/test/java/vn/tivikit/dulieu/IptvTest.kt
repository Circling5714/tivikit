package vn.tivikit.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** IPTV (1/11/2026): đọc danh sách để chọn kênh, ô IPTV, nguồn IPTV của ô Auto (tên, link là ví dụ). */
class IptvTest {
    private val m3u = """
        #EXTM3U url-tvg="https://example.test/epg.xml"
        #EXTINF:-1 tvg-id="vtv1hd" tvg-logo="https://example.test/vtv1.png" group-title="KÊNH VTV",VTV1 HD
        https://example.test/vtv1.m3u8
        #EXTINF:-1 tvg-id="vtv3hd" group-title="KÊNH VTV",VTV3 HD
        #EXTVLCOPT:http-user-agent=Dalvik/2.1.0
        http://example.test/vtv3
        #EXTINF:-1 tvg-id="a b" group-title="KÊNH ĐẶC SẮC, PHIM",Phim, Hay HD
        http://example.test/phim
        #EXTINF:-1,Kênh thiếu link
    """.trimIndent()

    @Test fun doc_tenMaNhomLogo() {
        val ds = Iptv.doc(m3u)
        assertEquals(listOf("VTV1 HD", "VTV3 HD", "Phim, Hay HD"), ds.map { it.ten })
        assertEquals(Iptv.Kenh("VTV1 HD", "vtv1hd", "KÊNH VTV", "https://example.test/vtv1.png"), ds[0])
        assertEquals("KÊNH ĐẶC SẮC, PHIM", ds[2].nhom)
        // tvg-id có khoảng trắng không dùng được trong lệnh: mã ô là tên đã gọn.
        assertEquals("vtv1hd", ds[0].maO)
        assertEquals("phimhay", ds[2].maO)
    }

    @Test fun lenhDat_asciiKhongNhay() {
        val l = Iptv.lenhDat("https://example.test/ds?x=Hà")
        assertTrue(l.all { it.code < 0x80 })
        assertEquals(2, l.count { it == '\'' })
        assertFalse(Iptv.LINK.matches("https://example.test/a';reboot"))
        assertFalse(Iptv.LINK.matches("ftp://example.test/a"))
        assertEquals("https://x.test/ds", Iptv.linkTrong(JSONObject("""{"link":"https://x.test/ds","soKenh":3}""")))
        assertEquals("", Iptv.linkTrong(null))
    }

    @Test fun oIptv_jsonVaApp() {
        val k = KenhNhanh.Kenh("", "VTV1 HD", "vtv1hd", KenhNhanh.LOAI_IPTV)
        assertTrue(k.laIptv)
        assertEquals(Iptv.GOI, k.app)
        assertEquals("IPTV", k.tenApp)
        val j = JSONObject(KenhNhanh.json(k))
        assertEquals(listOf("iptv", "vtv1hd", "VTV1 HD"), listOf(j.getString("app"), j.getString("ma"), j.getString("ten")))
        assertEquals(k, KenhNhanh.tuJson(j))
        assertNull(KenhNhanh.tuJson(JSONObject("""{"app":"iptv","ma":"a b","ten":"X"}""")))
    }

    /** Danh sách thật: nhiều kênh chung tvg-id ("no_epg"), kênh không mã mà tên không gọn được (chữ Nga). */
    @Test fun maO_vaKenhChungMa() {
        val nga = Iptv.Kenh("Первый Псковский", "", "TV", "")
        assertTrue(nga.maO.startsWith("t") && Iptv.MA.matches(nga.maO))
        assertEquals(nga.maO, Iptv.Kenh("Первый Псковский", "a b", "", "").maO)
        val a = KenhNhanh.Kenh("", "Sputnik", "no_epg", KenhNhanh.LOAI_IPTV)
        assertFalse(a.trung(KenhNhanh.Kenh("", "NHK World Radio", "no_epg", KenhNhanh.LOAI_IPTV)))
        assertTrue(a.trung(KenhNhanh.Kenh("", "Sputnik", "no_epg", KenhNhanh.LOAI_IPTV)))
    }

    @Test fun auto_coNguonIptvSauFpt() {
        val go = listOf(KenhVtvGo.Kenh("VTV1", "1", "VTV", "", false))
        val fpt = listOf(KenhFpt.Kenh("vtv1-hd", "VTV1 HD", "Kênh cơ bản", "1", ""))
        val ds = KenhNhanh.nguonAuto("VTV1", go, fpt, listOf("VTV1 HD" to "473"), KenhNhanh.GOI_Y, Iptv.doc(m3u))
        assertEquals(listOf(KenhNhanh.LOAI_VTVGO, KenhNhanh.LOAI_TV360, KenhNhanh.LOAI_FPT, KenhNhanh.LOAI_IPTV, KenhNhanh.LOAI_M3U), ds.map { it.loai })
        assertEquals("vtv1hd", ds[3].ma)
        // Kênh chỉ có trong danh sách IPTV.
        assertEquals(listOf(KenhNhanh.LOAI_IPTV), KenhNhanh.nguonAuto("Phim Hay", go, fpt, emptyList(), emptyList(), Iptv.doc(m3u)).map { it.loai })
    }
}
