package vn.tivikit.mapvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.tivikit.mapvoice.TuChonNguon.Nguon
import vn.tivikit.mapvoice.TuChonNguon.ThongKe

/** Kênh tự chọn nguồn: thứ tự thử, đọc `dumpsys audio` / phím tắt M3U (mẫu lấy trên Skyworth, Android 11, 1/11/2026). */
class TuChonNguonTest {
    private val go = Nguon(TuChonNguon.VTVGO, "VTV1", ma = "1")
    private val tv360 = Nguon(TuChonNguon.TV360, "VTV1", so = "001")
    private val fpt = Nguon(TuChonNguon.FPT, "VTV1 HD", ma = "vtv1-hd")
    private val m3u = Nguon(TuChonNguon.M3U, "VTV1 HD", ma = "473")
    private val bay = 1_000_000_000L

    @Test fun xep_chuaCoLichSuThiTheoThuTuMacDinh() =
        assertEquals(listOf(go, tv360, fpt, m3u), TuChonNguon.xep(listOf(m3u, fpt, tv360, go), emptyMap(), bay))

    @Test fun xep_nguonLenTiengNhanhTruoc() {
        val tk = mapOf(fpt.khoa to ThongKe(tb = 4000), tv360.khoa to ThongKe(tb = 9000))
        assertEquals(listOf(fpt, tv360, go, m3u), TuChonNguon.xep(listOf(go, tv360, fpt, m3u), tk, bay))
    }

    @Test fun xep_nguonVuaLoiXuongCuoi_hetPhatThiVeLai() {
        val tk = mapOf(go.khoa to ThongKe(tb = 3000, loi = 1, lucLoi = bay - 60_000), tv360.khoa to ThongKe(loi = 2, lucLoi = bay - 120_000))
        // Lỗi lâu hơn (TV360) đứng trước nguồn vừa lỗi (VTVgo) ở cuối danh sách.
        assertEquals(listOf(fpt, m3u, tv360, go), TuChonNguon.xep(listOf(go, tv360, fpt, m3u), tk, bay))
        // 30 phút sau lần lỗi thứ nhất, VTVgo (đã từng lên tiếng 3 giây) về đầu; TV360 lỗi 2 lần còn bị phạt tới 60 phút.
        assertEquals(listOf(go, fpt, m3u, tv360), TuChonNguon.xep(listOf(go, tv360, fpt, m3u), tk, bay + 30 * 60_000))
    }

    @Test fun phat_gapDoiToiDaMotNgay() {
        assertEquals(0L, TuChonNguon.phat(0))
        assertEquals(30 * 60_000L, TuChonNguon.phat(1))
        assertEquals(60 * 60_000L, TuChonNguon.phat(2))
        assertEquals(24 * 3_600_000L, TuChonNguon.phat(20))
    }

    @Test fun thongKe_duocThiTrungBinhVaXoaLoi() {
        val tk = ThongKe(tb = 9000, loi = 2, lucLoi = 5).duoc(3000)
        assertEquals(ThongKe(tb = 7000), tk)
        assertEquals(ThongKe(tb = 3000), ThongKe().duoc(3000))
        assertEquals(ThongKe(tb = 7000, loi = 1, lucLoi = 9), tk.hong(9))
        val m = TuChonNguon.thongKeTu(TuChonNguon.thongKeJson(mapOf(go.khoa to tk, m3u.khoa to ThongKe(loi = 1, lucLoi = 7))))
        assertEquals(mapOf("vtvgo:1" to tk, "m3u:473" to ThongKe(loi = 1, lucLoi = 7)), m)
    }

    private val audio11 = """
        Audio Focus stack entries (last is top of stack):
          source:android.os.BinderProxy@1 -- pack: org.smarttube.stable -- client: android.media.AudioManager@2 -- gain: AUDIOFOCUS_GAIN -- flags:  -- loss: LOSS -- notified: true -- uid: 10145 -- attr: AudioAttributes: usage=USAGE_MEDIA -- sdk:33
          source:android.os.BinderProxy@3 -- pack: vn.vtv.vtvgotv -- client: android.media.AudioManager@4 -- gain: AUDIOFOCUS_GAIN -- flags:  -- loss: none -- notified: true -- uid: 10150 -- attr: AudioAttributes: usage=USAGE_MEDIA -- sdk:30

        Interrupted focus stack entries:
          players:
          AudioPlaybackConfiguration piid:15 type:android.media.SoundPool u/pid:1000/797 state:idle attr:AudioAttributes: usage=USAGE_ASSISTANCE_SONIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null
          AudioPlaybackConfiguration piid:63 type:android.media.AudioTrack u/pid:10145/3043 state:paused attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MOVIE flags=0xA00 tags= bundle=null
          AudioPlaybackConfiguration piid:71 type:android.media.AudioTrack u/pid:10150/4211 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MOVIE flags=0xA00 tags= bundle=null
    """.trimIndent()

    @Test fun dangPhat_theoUid() {
        assertTrue(TuChonNguon.dangPhat(audio11, 10150))
        assertFalse(TuChonNguon.dangPhat(audio11, 10145))
        assertFalse(TuChonNguon.dangPhat(audio11, 1000))
        // Android 8–9: các mục cách nhau " -- ".
        val audio9 = "  ID:63 -- type:android.media.AudioTrack -- u/pid:10088/5120 -- state:started -- attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MOVIE flags=0x0 tags= bundle=null -- sessionId:1185"
        assertTrue(TuChonNguon.dangPhat(audio9, 10088))
        assertFalse(TuChonNguon.dangPhat(audio9.replace("started", "paused"), 10088))
        assertFalse(TuChonNguon.dangPhat(audio9.replace("USAGE_MEDIA", "USAGE_ASSISTANCE_SONIFICATION"), 10088))
    }

    @Test fun coTrinhPhat_caKhiChuaPhat() {
        assertTrue(TuChonNguon.coTrinhPhat(audio11, 10145))
        assertTrue(TuChonNguon.coTrinhPhat(audio11, 10150))
        // Tiếng bấm phím của hệ thống không tính; uid khác không có.
        assertFalse(TuChonNguon.coTrinhPhat(audio11, 1000))
        assertFalse(TuChonNguon.coTrinhPhat(audio11, 10087))
    }

    @Test fun giuTieng_dauNganXep() {
        assertEquals("vn.vtv.vtvgotv", TuChonNguon.giuTieng(audio11))
        assertNull(TuChonNguon.giuTieng("Audio Focus stack entries (last is top of stack):\n\n  players:\n"))
        assertNull(TuChonNguon.giuTieng(""))
    }

    @Test fun kenhM3u_tenMaVaLink() {
        val dump = """
            Package: com.m3u.smartphone  UID: 10171
                ShortcutInfo {id=channel_recently, flags=0x1a4 [ImManIc-rStr]
                  packageName=com.m3u.smartphone
                  shortLabel=Recently, resId=2131886112[app_shortcut_channel_recently_short_label]
                  longLabel=Play recently channel, resId=2131886111[app_shortcut_channel_recently_long_label]
                ShortcutInfo {id=channel_473, flags=0x89 [DynIc-fStr]
                  packageName=com.m3u.smartphone
                  shortLabel=VTV1 HD, resId=0[null]
                  longLabel=https://example.test/live/vtv1/index.m3u8, resId=0[null]
                  intents=[Intent { act=android.intent.action.VIEW cmp=com.m3u.smartphone/com.m3u.business.channel.PlayerActivity }]
                ShortcutInfo {id=channel_12, flags=0x89 [DynIc-fStr]
                  shortLabel=HTV7 HD, resId=0[null]
                  longLabel=null, resId=0[null]
            Package: com.other
        """.trimIndent()
        assertEquals(
            listOf(
                Nguon(TuChonNguon.M3U, "VTV1 HD", ma = "473", url = "https://example.test/live/vtv1/index.m3u8"),
                Nguon(TuChonNguon.M3U, "HTV7 HD", ma = "12"),
            ),
            TuChonNguon.kenhM3u(dump),
        )
    }

    @Test fun nguonCua_gopM3uCungTen() {
        val k = XuLyCau.dsKenh("""{"tv360":[{"ten":"VTV1","so":"1"}],"vtvgo":[{"ten":"VTV1","ma":"1"}],"fptplay":[{"ten":"VTV1 HD","ma":"vtv1-hd"}]}""").single()
        val ds = TuChonNguon.nguonCua(k, listOf(m3u, Nguon(TuChonNguon.M3U, "HTV7 HD", ma = "12")))
        assertEquals(listOf(TuChonNguon.VTVGO, TuChonNguon.TV360, TuChonNguon.FPT, TuChonNguon.M3U), ds.map { it.app })
        assertEquals("001", ds[1].so)
        assertEquals("473", ds[3].ma)
        // TV360 không biết số kênh thì không thử TV360.
        val htv = XuLyCau.dsKenh("""{"vtvgo":[{"ten":"HTV9","ma":"h9"}]}""").single()
        assertEquals(listOf(TuChonNguon.VTVGO), TuChonNguon.nguonCua(htv, emptyList()).map { it.app })
    }

    @Test fun iptv_ketQuaTimVaJsonAscii() {
        assertEquals(Nguon(TuChonNguon.IPTV, "VTV3 HD", ma = "vtv3hd"), TuChonNguon.iptvTu("Result: Bundle[{ma=vtv3hd, ok=1, ten=VTV3 HD}]"))
        // Tên có dấu phẩy, mã rỗng (danh sách không ghi tvg-id) thì mã là tên đã gọn.
        assertEquals(Nguon(TuChonNguon.IPTV, "Phim, Hay HD", ma = "phimhay"), TuChonNguon.iptvTu("Result: Bundle[{ma=, ok=1, ten=Phim, Hay HD}]"))
        assertNull(TuChonNguon.iptvTu("Result: Bundle[{loi=khong co}]"))
        // Chữ có dấu và nháy đơn thành mã thoát: lệnh thuần ASCII, không nháy đơn, đọc lại ra đúng tên.
        val ascii = TuChonNguon.jsonAscii(org.json.JSONObject().put("ten", "Hà Nội 1 'x"))
        assertTrue(ascii.all { it.code in 0x20..0x7e } && '\'' !in ascii)
        assertEquals("Hà Nội 1 'x", org.json.JSONObject(ascii).getString("ten"))
        // Thứ tự mặc định: IPTV sau FPT Play, trước M3U.
        val iptv = Nguon(TuChonNguon.IPTV, "VTV1 HD", ma = "vtv1hd")
        assertEquals(listOf(go, tv360, fpt, iptv, m3u), TuChonNguon.xep(listOf(m3u, iptv, fpt, tv360, go), emptyMap(), bay))
        assertEquals("vn.tivikit.iptv", iptv.goi)
    }

    /** Ô có thứ tự riêng (1/11/2026): giữ đúng thứ tự, kể cả nguồn từng lỗi; mất tiếng thì thử các nguồn sau nó trước. */
    @Test fun thuTuRieng_giuNguyenVaXoayKhiHong() {
        val ds = listOf(tv360, go, fpt)
        assertEquals(ds, TuChonNguon.theoThuTu(ds))
        assertEquals(listOf(fpt, tv360, go), TuChonNguon.theoThuTu(ds, hong = go))
        assertEquals(listOf(go, fpt, tv360), TuChonNguon.theoThuTu(ds, hong = tv360))
        // Nguồn hỏng không còn trong danh sách (app vừa gỡ): giữ nguyên thứ tự.
        assertEquals(ds, TuChonNguon.theoThuTu(ds, hong = m3u))
    }

    @Test fun json_quaLaiVaBoMucHong() {
        val ds = listOf(go, tv360, fpt, m3u)
        assertEquals(ds, TuChonNguon.tuJson(TuChonNguon.json(ds)))
        val hong = """[{"app":"vtvgo","ma":"x;reboot"},{"app":"m3u","ma":"abc"},{"app":"khac","ma":"1"},{"app":"tv360","so":"1","ten":"VTV1"},{}]"""
        assertEquals(listOf(Nguon(TuChonNguon.TV360, "VTV1", so = "001")), TuChonNguon.tuJson(hong))
        assertEquals(emptyList<Nguon>(), TuChonNguon.tuJson("khong phai json"))
    }

    @Test fun appKenhAuto() {
        val ds = XuLyCau.dsKenh("""{"tv360":[{"ten":"VTV3","so":"3"}],"vtvgo":[{"ten":"VTV3","ma":"3"}]}""")
        val ch = CauHinh(appKenh = CauHinh.APP_AUTO)
        val v = XuLyCau.quyet("mở VTV3", ch, ds) { true }
        assertTrue(v is XuLyCau.KenhTuDong && v.k.ten == "VTV3")
        // Nói rõ app thì mở đúng app đó.
        assertEquals(XuLyCau.MoKenh("VTV3", CauHinh.APP_TV360, so = "003"), XuLyCau.quyet("mở VTV3 trên TV360", ch, ds) { true })
        // Cấu hình: "auto" đọc lại được, doiNguon mặc định bật.
        val doc = CauHinh.tuJson(ch.copy(doiNguon = false).json().toString())!!
        assertEquals(CauHinh.APP_AUTO, doc.appKenh)
        assertFalse(doc.doiNguon)
        assertTrue(CauHinh.tuJson("""{"appKenh":"auto"}""")!!.doiNguon)
    }
}
