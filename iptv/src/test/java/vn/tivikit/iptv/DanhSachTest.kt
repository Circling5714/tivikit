package vn.tivikit.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Đọc danh sách IPTV (.m3u): dạng dòng như danh sách thật (tên, link là ví dụ). */
class DanhSachTest {
    private val m3u = """
        #EXTM3U url-tvg="https://example.test/epg.xml"
        #PLAYLIST:Ví dụ
        #EXTINF:-1 tvg-id="vtv1hd" tvg-logo="https://example.test/vtv1.png" group-title="KÊNH VTV",VTV1 HD
        https://example.test/live/vtv1/index.m3u8
        #EXTINF:-1 tvg-id="vtv3hd" tvg-logo="https://example.test/vtv3.png" group-title="KÊNH VTV",VTV3 HD
        #EXTVLCOPT:http-user-agent=Dalvik/2.1.0
        http://example.test/vtv3/playlist.m3u8
        #EXTINF:-1 tvg-logo="https://example.test/a.png" group-title="KÊNH ĐẶC SẮC, PHIM",Phim, Hay HD
        #EXTVLCOPT:http-referrer=https://example.test/
        http://example.test/phim
        #EXTINF:-1 group-title="RADIO",VOV1
        #EXTHTTP:{"User-Agent":"Radio/1.0"}
        http://example.test/vov1.mp3
        #EXTINF:-1 tvg-id="htv7",HTV7 HD
        http://example.test/htv7.m3u8|User-Agent=Kodi/20&Referer=https://ref.test/

        #EXTINF:-1,Kênh thiếu link
        #EXTINF:-1 tvg-id="qpvn",Quốc phòng Việt Nam
        https://example.test/qpvn.m3u8?token=1
    """.trimIndent()

    @Test fun doc_thuocTinhVaHeader() {
        val ds = DanhSach.doc(m3u)
        assertEquals(listOf("VTV1 HD", "VTV3 HD", "Phim, Hay HD", "VOV1", "HTV7 HD", "Quốc phòng Việt Nam"), ds.map { it.ten })
        val vtv1 = ds[0]
        assertEquals("vtv1hd", vtv1.ma)
        assertEquals("KÊNH VTV", vtv1.nhom)
        assertEquals("https://example.test/vtv1.png", vtv1.logo)
        assertEquals("", vtv1.ua)
        assertEquals("Dalvik/2.1.0", ds[1].ua)
        // Dấu phẩy trong ngoặc kép (group-title) không cắt tên.
        assertEquals("KÊNH ĐẶC SẮC, PHIM", ds[2].nhom)
        assertEquals("https://example.test/", ds[2].referer)
        assertEquals("Radio/1.0", ds[3].ua)
        // Đuôi "|User-Agent=…&Referer=…" kiểu Kodi.
        assertEquals("http://example.test/htv7.m3u8", ds[4].link)
        assertEquals("Kodi/20", ds[4].ua)
        assertEquals("https://ref.test/", ds[4].referer)
    }

    @Test fun tim_theoMaRoiTheoTen() {
        val ds = DanhSach.doc(m3u)
        assertEquals("VTV3 HD", DanhSach.tim(ds, "vtv3hd", "tên cũ")?.ten)
        // Mất mã (danh sách đổi tvg-id) thì theo tên đã gọn.
        assertEquals("VTV1 HD", DanhSach.tim(ds, "khong-con", "VTV1")?.ten)
        assertEquals("Quốc phòng Việt Nam", DanhSach.tim(ds, "", "QPVN HD")?.ten)
        assertNull(DanhSach.tim(ds, "", "VTV9"))
        assertNull(DanhSach.tim(ds, "", "  "))
        // Tên trên ô đã gọn (bỏ dấu phẩy).
        assertEquals("Phim, Hay HD", DanhSach.tim(ds, "phimhay", "Phim Hay HD")?.ten)
    }

    /** Danh sách thật có nhiều kênh chung một tvg-id ("no_epg", số), có kênh không mã mà tên không gọn được (chữ Nga). */
    @Test fun tim_maDungChungVaTenNgoaiLatin() {
        val ds = DanhSach.doc("""
            #EXTM3U
            #EXTINF:-1 tvg-id="no_epg" group-title="RADIO",NHK World Radio
            http://example.test/nhk
            #EXTINF:-1 tvg-id="no_epg" group-title="RADIO",Sputnik
            http://example.test/sputnik
            #EXTINF:-1 group-title="TV",Первый Псковский
            http://example.test/pskov
        """.trimIndent())
        assertEquals("http://example.test/sputnik", DanhSach.tim(ds, "no_epg", "Sputnik")?.link)
        // Mã chung mà tên không còn: không đoán bừa một kênh trong số đó.
        assertNull(DanhSach.tim(ds, "no_epg", "Radio Khác"))
        assertEquals("http://example.test/pskov", DanhSach.tim(ds, "t1234", "Первый Псковский")?.link)
        assertEquals("Phim Hay HD", DanhSach.gon("Phim, Hay  HD"))
    }

    @Test fun khoa_gonTen() {
        assertEquals("vtv1", DanhSach.khoa("VTV1 HD"))
        assertEquals("hanoi1", DanhSach.khoa("Truyền hình Hà Nội 1"))
        assertEquals(DanhSach.khoa("Vĩnh Long 1 HD"), DanhSach.khoa("THVL1"))
    }

    @Test fun laHls_theoDuoi() {
        assertTrue(PhatKenh.laHls("https://example.test/a/index.m3u8"))
        assertTrue(PhatKenh.laHls("https://example.test/a.m3u8?token=1"))
        assertFalse(PhatKenh.laHls("http://example.test/phim"))
        assertFalse(PhatKenh.laHls("http://example.test/vov1.mp3"))
    }

    /** Kiểu luồng: .m3u8 luôn HLS; đuôi file thì file thường trước; không đuôi thì HLS trước, lỗi mới đổi kiểu. */
    @Test fun kieuHls_theoDuoiVaLanThu() {
        assertTrue(PhatKenh.kieuHls("https://example.test/a/index.m3u8"))
        assertTrue(PhatKenh.kieuHls("https://example.test/a/index.m3u8", doiKieu = true))
        assertTrue(PhatKenh.kieuHls("https://example.test/live/vtv3"))
        assertFalse(PhatKenh.kieuHls("https://example.test/live/vtv3", doiKieu = true))
        assertTrue(PhatKenh.kieuHls("http://example.test:8000/stream"))
        assertFalse(PhatKenh.kieuHls("http://example.test/vov1.mp3?x=1"))
        assertTrue(PhatKenh.kieuHls("http://example.test/vov1.mp3", doiKieu = true))
        assertFalse(PhatKenh.kieuHls("http://example.test/phim.MP4"))
    }

    @Test fun link_chiNhanHttp() {
        assertTrue(CauHinhProvider.LINK.matches("https://example.test/ds"))
        assertFalse(CauHinhProvider.LINK.matches("https://example.test/a b"))
        assertFalse(CauHinhProvider.LINK.matches("https://example.test/a';reboot"))
        assertFalse(CauHinhProvider.LINK.matches("file:///sdcard/a.m3u"))
    }
}
