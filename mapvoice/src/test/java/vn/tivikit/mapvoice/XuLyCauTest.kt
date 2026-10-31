package vn.tivikit.mapvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XuLyCauTest {
    private val ch = CauHinh()
    private val ds = XuLyCau.dsKenh(
        """{"tv360":[{"ten":"VTV3","so":"3"},{"ten":"HTV7 HD","so":"73"},{"ten":"QPVN HD","so":"8"}],
            "vtvgo":[{"ten":"VTV3","ma":"3"},{"ten":"Quốc phòng Việt Nam","ma":"q1"},{"ten":"Truyền hình Hà Nội 1","ma":"hn1"}]}"""
    )
    private val caHai = { _: String -> true }

    private fun quyet(cau: String, c: CauHinh = ch, coApp: (String) -> Boolean = caHai) = XuLyCau.quyet(cau, c, ds, coApp)

    @Test
    fun cauTrongDongLogcat() {
        assertEquals("mở nhạc piano", XuLyCau.cauTuLog("I/katniss_interactor_StreamingTextView( 1234): setFinalRecognizedText(mở nhạc piano)"))
        assertEquals("nhạc (remix) 2024", XuLyCau.cauTuLog("10-29 11:00:00.123  1234  1250 I katniss_interactor_StreamingTextView: setFinalRecognizedText(nhạc (remix) 2024)"))
        assertEquals("vtv3", XuLyCau.cauTuLog("I/katniss: setFinalRecognizedText: vtv3"))
        assertNull(XuLyCau.cauTuLog("I/ActivityTaskManager( 500): START u0 {cmp=com.google.android.youtube.tv/.Main}"))
        assertNull(XuLyCau.cauTuLog("I/x( 1): setFinalRecognizedText()"))
        assertNull(XuLyCau.cauTuLog("I/x( 1): setFinalRecognizedText(null)"))
    }

    @Test
    fun noiYouTubeThiMoSmartTube() {
        assertEquals(XuLyCau.YouTube("nhạc piano", true), quyet("mở nhạc piano trên YouTube"))
        assertEquals(XuLyCau.YouTube("nhạc piano", true), quyet("Mở nhạc Piano trên you tube."))
        assertEquals(XuLyCau.YouTube("nhạc piano", false), quyet("tìm nhạc piano trên YouTube"))
        assertEquals(XuLyCau.YouTube("sơn tùng", false), quyet("YouTube Sơn Tùng"))
        assertEquals(XuLyCau.YouTube("hoạt hình", true), quyet("cho tôi xem hoạt hình trên YouTube"))
        assertEquals(XuLyCau.YouTube("nhạc thiền", true), quyet("mở cho tôi nghe nhạc thiền bằng YouTube"))
        // Chỉ mở app.
        assertEquals(XuLyCau.YouTube("", false), quyet("mở YouTube"))
        assertEquals(XuLyCau.YouTube("", false), quyet("mở ứng dụng YouTube"))
    }

    @Test
    fun cachMoDoiPhatHayTim() {
        assertEquals(XuLyCau.YouTube("nhạc piano", true), quyet("nhạc piano trên YouTube", ch.copy(cachMo = CachMo.PHAT_LUON)))
        assertEquals(XuLyCau.YouTube("nhạc piano", false), quyet("mở nhạc piano trên YouTube", ch.copy(cachMo = CachMo.TIM_KIEM)))
    }

    @Test
    fun cauNhacVideoKhongNoiYouTube() {
        assertEquals(XuLyCau.YouTube("nhạc piano", false), quyet("nhạc piano"))
        assertEquals(XuLyCau.YouTube("nhạc piano", true), quyet("mở nhạc piano"))
        assertEquals(XuLyCau.YouTube("bài hát đi về nhà", true), quyet("phát bài hát đi về nhà"))
        assertEquals(XuLyCau.BoQua, quyet("mở nhạc piano", ch.copy(nhacVideo = false)))
    }

    @Test
    fun cauKhacDeTroLyLam() {
        listOf("thời tiết hôm nay", "tắt tivi", "mở Netflix", "tăng âm lượng", "mấy giờ rồi").forEach { assertEquals(it, XuLyCau.BoQua, quyet(it)) }
        assertEquals(XuLyCau.BoQua, quyet("mở nhạc piano trên YouTube", ch.copy(bat = false)))
        assertEquals(XuLyCau.BoQua, quyet("   "))
    }

    @Test
    fun moKenhBangAppDaChon() {
        assertEquals(XuLyCau.MoKenh("VTV3", "vtvgo", ma = "3"), quyet("mở VTV3"))
        assertEquals(XuLyCau.MoKenh("VTV3", "vtvgo", ma = "3"), quyet("chuyển sang kênh VTV 3"))
        assertEquals(XuLyCau.MoKenh("VTV3", "tv360", so = "003"), quyet("xem kênh VTV ba trên TV360"))
        assertEquals(XuLyCau.MoKenh("VTV3", "tv360", so = "003"), quyet("VTV3", ch.copy(appKenh = CauHinh.APP_TV360)))
        // VTVgo không có HTV7: mở bằng TV360.
        assertEquals(XuLyCau.MoKenh("HTV7 HD", "tv360", so = "073"), quyet("mở HTV7"))
        // Tên viết tắt gộp với tên đủ của VTVgo.
        assertEquals(XuLyCau.MoKenh("QPVN HD", "vtvgo", ma = "q1"), quyet("mở kênh QPVN"))
        assertEquals(XuLyCau.MoKenh("QPVN HD", "vtvgo", ma = "q1"), quyet("xem quốc phòng việt nam"))
    }

    @Test
    fun appKenhKhongCoThiDungAppKia() {
        val tv360 = ch.copy(appKenh = CauHinh.APP_TV360)
        // Chọn TV360 mà TV360 không biết số kênh, VTVgo biết chắc: dùng VTVgo.
        assertEquals(XuLyCau.MoKenh("Truyền hình Hà Nội 1", "vtvgo", ma = "hn1"), quyet("Hà Nội một", tv360))
        // Không có VTVgo trên tivi: TV360 tìm theo tên.
        val chi360 = { g: String -> g == XuLyCau.TV360 }
        assertEquals(XuLyCau.MoKenh("Truyền hình Hà Nội 1", "tv360"), quyet("mở hà nội 1", ch, chi360))
        // Nói rõ "trên TV360" thì mở TV360 dù TV360 không biết số kênh.
        assertEquals(XuLyCau.MoKenh("Truyền hình Hà Nội 1", "tv360"), quyet("hà nội 1 trên tv360"))
        // Không app nào: để Trợ lý lo.
        assertEquals(XuLyCau.BoQua, quyet("mở VTV3", ch) { false })
    }

    @Test
    fun kenhLaHayTatBatKenhThiBoQua() {
        assertEquals(XuLyCau.BoQua, quyet("mở kênh K+"))
        assertEquals(XuLyCau.BoQua, quyet("mở VTV3", ch.copy(kenh = false)))
        // Nói YouTube thì là YouTube, kể cả có tên kênh.
        assertEquals(XuLyCau.YouTube("vtv3", true), quyet("mở VTV3 trên YouTube"))
    }

    @Test
    fun khoaKenhGonTen() {
        assertEquals("hanoi1", XuLyCau.khoaKenh("Hà Nội 1"))
        assertEquals("hanoi1", XuLyCau.khoaKenh("hà nội một"))
        assertEquals("hanoi1", XuLyCau.khoaKenh("Truyền hình HÀ NỘI 1 HD"))
        assertEquals("vtv3", XuLyCau.khoaKenh("Kênh VTV 3"))
        // Chữ trùng số đọc mà khác dấu thì giữ nguyên.
        assertEquals("quocphongvietnam", XuLyCau.khoaKenh("Quốc phòng Việt Nam"))
        assertEquals("haiphong", XuLyCau.khoaKenh("Hải Phòng"))
        assertEquals("bariavungtau", XuLyCau.khoaKenh("Bà Rịa - Vũng Tàu"))
        assertEquals("vtv5", XuLyCau.khoaKenh("VTV năm"))
    }

    @Test
    fun dsKenhBoMucHong() {
        val d = XuLyCau.dsKenh("""{"tv360":[{"ten":"","so":"1"},{"ten":"VTV1","so":"abc"},{"ten":"VTV2","so":"2"}],"vtvgo":[{"ten":"X","ma":"có dấu"}]}""")
        assertEquals(listOf("VTV2"), d.map { it.ten })
        assertEquals(emptyList<KenhTv>(), XuLyCau.dsKenh("không phải json"))
    }

    @Test
    fun cauHinhJson() {
        val c = CauHinh(bat = false, appYouTube = "org.smarttube.beta", cachMo = CachMo.TIM_KIEM, nhacVideo = false, kenh = false, appKenh = CauHinh.APP_TV360)
        assertEquals(c, CauHinh.tuJson(c.json().toString()))
        assertEquals("", CauHinh.tuJson("""{"appYouTube":"x; rm -rf /"}""")?.appYouTube)
        assertEquals(CauHinh(), CauHinh.tuJson("{}"))
        assertNull(CauHinh.tuJson("hỏng"))
        // 1.1: câu riêng, FPT Play, TizenTube.
        val c2 = CauHinh(appYouTube = XuLyCau.TIZENTUBE, appKenh = CauHinh.APP_FPT,
            cauRieng = listOf(CauRieng("giá vàng", "tro-ly"), CauRieng("karaoke", "org.smarttube.stable"), CauRieng("thời sự", "youtube")))
        assertEquals(c2, CauHinh.tuJson(c2.json().toString()))
        // Câu riêng hỏng thì bỏ: chữ rỗng, đích lạ, chữ quá dài.
        val hong = CauHinh.tuJson("""{"cauRieng":[{"chu":" ","dich":"tro-ly"},{"chu":"x","dich":"rm -rf"},{"chu":"${"a".repeat(61)}","dich":"tro-ly"},{"chu":"  phim   hay ","dich":"net.fptplay.ottbox"}]}""")
        assertEquals(listOf(CauRieng("phim hay", "net.fptplay.ottbox")), hong?.cauRieng)
        // Bản cũ (1.0) không có cauRieng, appKenh lạ thì VTVgo.
        assertEquals(CauHinh(), CauHinh.tuJson("""{"appKenh":"k+"}"""))
    }

    private val dsFpt = XuLyCau.dsKenh(
        """{"tv360":[{"ten":"VTV3","so":"3"},{"ten":"HTV7 HD","so":"73"}],
            "vtvgo":[{"ten":"VTV3","ma":"3"},{"ten":"Truyền hình Hà Nội 1","ma":"hn1"}],
            "fptplay":[{"ten":"VTV3 HD","ma":"vtv3-hd"},{"ten":"HTV7 HD","ma":"htv7-hd"},{"ten":"HaNoiTV 1","ma":"hanoitv-1"},
                {"ten":"Vĩnh Long 1 HD","ma":"thvl1"},{"ten":"Quốc Phòng VN HD","ma":"quoc-phong-vn"}]}"""
    )

    private fun quyetFpt(cau: String, c: CauHinh = ch.copy(appKenh = CauHinh.APP_FPT), coApp: (String) -> Boolean = caHai) =
        XuLyCau.quyet(cau, c, dsFpt, coApp)

    @Test
    fun kenhFptPlay() {
        // Chọn FPT Play: mở bằng mã kênh của FPT Play, tên FPT Play đặt.
        assertEquals(XuLyCau.MoKenh("VTV3 HD", "fptplay", ma = "vtv3-hd"), quyetFpt("mở VTV3"))
        assertEquals(XuLyCau.MoKenh("HaNoiTV 1", "fptplay", ma = "hanoitv-1"), quyetFpt("xem kênh Hà Nội một"))
        assertEquals(XuLyCau.MoKenh("Vĩnh Long 1 HD", "fptplay", ma = "thvl1"), quyetFpt("mở THVL1"))
        assertEquals(XuLyCau.MoKenh("Quốc Phòng VN HD", "fptplay", ma = "quoc-phong-vn"), quyetFpt("mở kênh QPVN"))
        // Nói "trên FPT Play" thì FPT Play dù đã chọn VTVgo.
        assertEquals(XuLyCau.MoKenh("VTV3 HD", "fptplay", ma = "vtv3-hd"), quyetFpt("mở VTV3 trên FPT Play", ch))
        assertEquals(XuLyCau.MoKenh("HTV7 HD", "fptplay", ma = "htv7-hd"), quyetFpt("xem HTV7 bằng fpt play", ch))
        // Đã chọn VTVgo: VTVgo vẫn trước; VTVgo không có HTV7 thì FPT Play (biết chắc) trước TV360.
        assertEquals(XuLyCau.MoKenh("VTV3", "vtvgo", ma = "3"), quyetFpt("mở VTV3", ch))
        assertEquals(XuLyCau.MoKenh("HTV7 HD", "fptplay", ma = "htv7-hd"), quyetFpt("mở HTV7", ch))
        // Chọn FPT Play mà tivi chưa có FPT Play: VTVgo, rồi TV360.
        val khongFpt = { g: String -> g != XuLyCau.FPTPLAY }
        assertEquals(XuLyCau.MoKenh("VTV3", "vtvgo", ma = "3"), quyetFpt("mở VTV3", coApp = khongFpt))
        assertEquals(XuLyCau.MoKenh("HTV7 HD", "tv360", so = "073"), quyetFpt("mở HTV7", coApp = khongFpt))
    }

    private val rieng = ch.copy(cauRieng = listOf(
        CauRieng("giá vàng", XuLyCau.DICH_TRO_LY), CauRieng("karaoke", XuLyCau.TIZENTUBE), CauRieng("thoi su", XuLyCau.DICH_YOUTUBE),
        CauRieng("phim bộ", "net.fptplay.ottbox"), CauRieng("nhạc thiếu nhi", XuLyCau.YOUTUBE_GOC),
    ))

    @Test
    fun cauRiengXetTruocHet() {
        assertEquals(XuLyCau.TroLy("giá vàng"), quyet("tìm giá vàng SJC hôm nay", rieng))
        // Câu riêng thắng cả câu nói tới YouTube và câu nhạc.
        assertEquals(XuLyCau.TroLy("giá vàng"), quyet("giá vàng trên YouTube", rieng))
        assertEquals(XuLyCau.YouTube("karaoke sơn tùng", true, XuLyCau.TIZENTUBE), quyet("mở karaoke Sơn Tùng", rieng))
        assertEquals(XuLyCau.YouTube("nhạc thiếu nhi", false, XuLyCau.YOUTUBE_GOC), quyet("tìm nhạc thiếu nhi", rieng))
        // Gõ không dấu thì khớp cả câu có dấu; "youtube" = app YouTube đã chọn.
        assertEquals(XuLyCau.YouTube("thời sự", false), quyet("thời sự", rieng))
        assertEquals(XuLyCau.MoApp("net.fptplay.ottbox"), quyet("xem phim bộ hàn quốc", rieng))
        // Có dấu thì phải đúng dấu, và phải trọn từ.
        assertEquals(XuLyCau.BoQua, quyet("già vàng", rieng))
        assertEquals(XuLyCau.YouTube("karaoke", false), quyet("karaoke", ch))
        assertEquals(XuLyCau.BoQua, quyet("karaokee", rieng))
        // Không có câu riêng khớp: như cũ.
        assertEquals(XuLyCau.MoKenh("VTV3", "vtvgo", ma = "3"), quyet("mở VTV3", rieng))
        assertEquals(XuLyCau.BoQua, quyet("thời tiết hôm nay", rieng))
        // Tắt Map Voice thì câu riêng cũng thôi.
        assertEquals(XuLyCau.BoQua, quyet("tìm giá vàng", rieng.copy(bat = false)))
    }
}
