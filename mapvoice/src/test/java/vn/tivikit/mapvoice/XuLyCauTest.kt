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
    }
}
