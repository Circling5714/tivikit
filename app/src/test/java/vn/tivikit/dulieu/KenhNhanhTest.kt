package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kênh nhanh (25/10/2026): số kênh TV360, các ô (mỗi ô một gói), tên ô qua app_labels, ô trong dock_apps. */
class KenhNhanhTest {
    private fun mang(j: JSONObject, k: String): List<String> = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    private val o3 = KenhNhanh.goiCua(3)

    @Test fun chuanSoVaTenSach() {
        assertEquals("001", KenhNhanh.chuanSo("1"))
        assertEquals("098", KenhNhanh.chuanSo("098"))
        assertNull(KenhNhanh.chuanSo("0"))
        assertNull(KenhNhanh.chuanSo("VTV1"))
        assertEquals("Hà Nội 1", KenhNhanh.tenSach("  Hà  Nội 1 "))
        assertEquals("VTV1 HD x", KenhNhanh.tenSach("VTV1, HD='x'"))
        // Gợi ý: số đúng dạng, không trùng số.
        assertTrue(KenhNhanh.GOI_Y.all { KenhNhanh.chuanSo(it.so) == it.so })
        assertEquals(KenhNhanh.GOI_Y.size, KenhNhanh.GOI_Y.map { it.so }.toSet().size)
    }

    @Test fun cacO() {
        // Ô 1 giữ tên gói của bản một ô trước đây; tên gói, file APK, màn mở khớp với flavor o1 … o6 của module kenh/.
        assertEquals("vn.tivikit.kenh", KenhNhanh.goiCua(1))
        assertEquals("vn.tivikit.kenh3", o3)
        assertEquals("kenh.apk", KenhNhanh.apkCua(1))
        assertEquals("kenh6.apk", KenhNhanh.apkCua(6))
        assertEquals("vn.tivikit.kenh2/vn.tivikit.kenh.MoKenh", KenhNhanh.moCua(2))
        assertEquals(3, KenhNhanh.oCua(o3))
        assertNull(KenhNhanh.oCua("vn.tivikit.kenh7"))
        assertNull(KenhNhanh.oCua("vn.tivikit.khoa"))
        assertFalse(KenhNhanh.laGoiKenh(KenhNhanh.TV360))
    }

    @Test fun json_khuHoi() {
        val k = KenhNhanh.Kenh("073", "HTV7 HD")
        assertEquals(k, KenhNhanh.tuJson(JSONObject(KenhNhanh.json(k))))
        // Tên có dấu: lệnh thuần ASCII, app phụ đọc ra đúng chữ.
        val hn = KenhNhanh.Kenh("098", "Hà Nội 1")
        assertTrue(KenhNhanh.json(hn).all { it.code < 0x80 })
        assertEquals(hn, KenhNhanh.tuJson(JSONObject(KenhNhanh.json(hn))))
        assertNull(KenhNhanh.tuJson(JSONObject("""{"so":"x"}""")))
        assertNull(KenhNhanh.tuJson(null))
    }

    /** Ô VTVgo (26/10/2026): cùng dạng JSON với app phụ (module kenh/, Kenh.json), mở bằng VTVgo, không trùng ô TV360. */
    @Test fun oVtvGo_json_vaTrung() {
        val go = KenhNhanh.Kenh("", "VTV3", "3")
        val j = JSONObject(KenhNhanh.json(go))
        assertEquals(listOf("vtvgo", "3", "VTV3"), listOf(j.getString("app"), j.getString("ma"), j.getString("ten")))
        assertFalse(j.has("so"))
        assertEquals(go, KenhNhanh.tuJson(j))
        assertEquals(KenhNhanh.VTVGO, go.app)
        assertEquals(KenhNhanh.TV360, KenhNhanh.Kenh("003", "VTV3").app)
        assertNull(KenhNhanh.tuJson(JSONObject("""{"app":"vtvgo","ma":"a b","ten":"X"}""")))
        assertTrue(go.trung(KenhNhanh.Kenh("", "VTV3 HD", "3")))
        assertFalse(go.trung(KenhNhanh.Kenh("003", "VTV3")))
        assertFalse(KenhNhanh.Kenh("003", "VTV3").trung(KenhNhanh.Kenh("", "VTV3", "003")))
    }

    /** Ô FPT Play (31/10/2026): cùng dạng JSON với app phụ (module kenh/, Kenh.json), mở bằng FPT Play, không trùng ô VTVgo. */
    @Test fun oFpt_json_vaTrung() {
        val fpt = KenhNhanh.Kenh("", "VTV1", "vtv1-hd", KenhNhanh.LOAI_FPT)
        val j = JSONObject(KenhNhanh.json(fpt))
        assertEquals(listOf("fptplay", "vtv1-hd", "VTV1"), listOf(j.getString("app"), j.getString("ma"), j.getString("ten")))
        assertFalse(j.has("so"))
        assertEquals(fpt, KenhNhanh.tuJson(j))
        assertTrue(fpt.laFpt)
        assertFalse(fpt.laVtvGo)
        assertEquals(KenhNhanh.FPTPLAY, fpt.app)
        assertEquals("FPT Play", fpt.tenApp)
        assertEquals("VTVgo", KenhNhanh.Kenh("", "VTV1", "1").tenApp)
        assertTrue(fpt.trung(KenhNhanh.Kenh("", "VTV1 HD", "vtv1-hd", KenhNhanh.LOAI_FPT)))
        assertFalse(fpt.trung(KenhNhanh.Kenh("", "VTV1", "vtv1-hd")))
        assertNull(KenhNhanh.tuJson(JSONObject("""{"app":"fptplay","ma":"vtv1 hd","ten":"VTV1"}""")))
    }

    /**
     * Logo ô (26/10/2026): app_banners "gói=kenh-o<n>" (tên khớp kenh-[a-z0-9]{1,24} mà Home sạch nhận), giữ ảnh người dùng
     * đặt cho app khác ở Nâng cao, bỏ được.
     */
    @Test fun logoO_appBanners() {
        assertEquals("kenh-o3", KenhNhanh.tenAnh(3))
        assertTrue((1..KenhNhanh.SO_O).all { Regex("kenh-[a-z0-9]{1,24}").matches(KenhNhanh.tenAnh(it)) })
        val j = JSONObject().put("app_banners", JSONArray(listOf("com.a=banner_tv")))
        KenhNhanh.datAnh(j, o3, KenhNhanh.tenAnh(3))
        assertEquals(listOf("com.a=banner_tv", "vn.tivikit.kenh3=kenh-o3"), mang(j, "app_banners"))
        assertEquals(mapOf(o3 to "kenh-o3"), KenhNhanh.anhTrong(j))
        KenhNhanh.datAnh(j, o3, null)
        assertEquals(listOf("com.a=banner_tv"), mang(j, "app_banners"))
        assertFalse(KenhNhanh.datAnh(JSONObject(), o3, null).has("app_banners"))
    }

    /** Ô link kiểu cũ (vgs.kenh.*) không tính vào số app tối đa của dock, như lúc Áp dụng Home sạch. */
    @Test fun datDock_khongTinhOLinkCu() {
        val j = JSONObject().put("dock_apps", JSONArray(listOf("vgs.kenh.vtv1", "vgs.kenh.vtv3", "com.a", "com.b")))
        assertEquals(true, KenhNhanh.datDock(j, o3, true, 3))
        assertEquals(listOf("vgs.kenh.vtv1", "vgs.kenh.vtv3", "com.a", "com.b", o3), mang(j, "dock_apps"))
        assertEquals(false, KenhNhanh.datDock(j, KenhNhanh.GOI, true, 3))
    }

    @Test fun tenO_nhieuO_giuTenCuaAppKhac() {
        val j = JSONObject().put("app_labels", JSONArray(listOf("com.telekom.onetv.tv.pl=TV")))
        KenhNhanh.datTen(j, KenhNhanh.GOI, "VTV1")
        KenhNhanh.datTen(j, o3, "HTV7 HD")
        assertEquals(listOf("com.telekom.onetv.tv.pl=TV", "vn.tivikit.kenh=VTV1", "vn.tivikit.kenh3=HTV7 HD"), mang(j, "app_labels"))
        assertEquals(mapOf(KenhNhanh.GOI to "VTV1", o3 to "HTV7 HD"), KenhNhanh.tenTrong(j))
        KenhNhanh.datTen(j, KenhNhanh.GOI, "VTV3")
        assertEquals("VTV3", KenhNhanh.tenTrong(j)[KenhNhanh.GOI])
        KenhNhanh.datTen(j, o3, null)
        assertEquals(listOf("com.telekom.onetv.tv.pl=TV", "vn.tivikit.kenh=VTV3"), mang(j, "app_labels"))
        val k = KenhNhanh.datTen(JSONObject(), KenhNhanh.GOI, "VTV1").let { KenhNhanh.datTen(it, KenhNhanh.GOI, null) }
        assertFalse(k.has("app_labels"))
    }

    @Test fun dock() {
        val j = JSONObject().put("dock_apps", JSONArray(listOf("com.netflix.ninja", "com.viettel.tv360.tv")))
        assertEquals(true, KenhNhanh.datDock(j, KenhNhanh.GOI, true, 8))
        assertEquals(true, KenhNhanh.datDock(j, o3, true, 8))
        assertEquals(listOf("com.netflix.ninja", "com.viettel.tv360.tv", KenhNhanh.GOI, o3), mang(j, "dock_apps"))
        assertEquals(setOf("com.netflix.ninja", "com.viettel.tv360.tv", KenhNhanh.GOI, o3), KenhNhanh.trongDock(j))
        // Có rồi thì giữ chỗ, không thêm lần nữa.
        assertEquals(true, KenhNhanh.datDock(j, KenhNhanh.GOI, true, 4))
        assertEquals(4, mang(j, "dock_apps").size)
        assertEquals(true, KenhNhanh.datDock(j, KenhNhanh.GOI, false, 8))
        assertEquals(listOf("com.netflix.ninja", "com.viettel.tv360.tv", o3), mang(j, "dock_apps"))
        assertEquals(false, KenhNhanh.datDock(j, KenhNhanh.goiCua(2), true, 3))
        // Home sạch đang dùng dock mặc định: không đụng vào.
        assertNull(KenhNhanh.datDock(JSONObject(), KenhNhanh.GOI, true, 8))
    }

    /** Ô M3U (1/11/2026): mã kênh là số của M3UAndroid; JSON gửi app phụ như ô VTVgo / FPT Play. */
    @Test fun m3u_jsonVaApp() {
        val k = KenhNhanh.Kenh("", "VTV1 HD", "473", KenhNhanh.LOAI_M3U)
        assertTrue(k.laM3u)
        assertEquals(KenhNhanh.M3U, k.app)
        assertEquals("M3U", k.tenApp)
        val j = JSONObject(KenhNhanh.json(k))
        assertEquals(listOf("m3u", "473", "VTV1 HD"), listOf(j.getString("app"), j.getString("ma"), j.getString("ten")))
        assertEquals(k, KenhNhanh.tuJson(j))
        assertNull(KenhNhanh.tuJson(JSONObject("""{"app":"m3u","ma":"vtv1","ten":"VTV1"}""")))
        // Cùng mã trên M3U là trùng; cùng mã mà khác app (ô VTVgo mã 473) thì không.
        assertTrue(k.trung(KenhNhanh.Kenh("", "VTV1", "473", KenhNhanh.LOAI_M3U)))
        assertFalse(k.trung(KenhNhanh.Kenh("", "VTV1 HD", "473")))
    }

    /** Phím tắt kênh của M3U trong `dumpsys shortcut` (chép từ Skyworth, 1/11/2026; thêm một phím tắt có dấu phẩy trong tên). */
    @Test fun m3u_docPhimTat() {
        val dump = """
              Package: com.m3u.smartphone  UID: 10161
                Calls: 0
                  ShortcutInfo {id=channel_recently, flags=0x1a4 [ImManIc-rStr]
                    shortLabel=Recently, resId=2131886112[app_shortcut_channel_recently_short_label]
                    intents=[Intent { act=android.intent.action.VIEW flg=0x1000c000 cmp=com.m3u.smartphone/com.m3u.business.channel.PlayerActivity }/PersistableBundle[{shortcut:channel-recently=true}]]
                  ShortcutInfo {id=channel_473, flags=0x89 [DynIc-fStr]
                    shortLabel=VTV1 HD, resId=0[null]
                    intents=[Intent { act=android.intent.action.VIEW cmp=com.m3u.smartphone/.ui.business.channel.PlayerActivity }/PersistableBundle[{shortcut:channel-id=473}]]
                  ShortcutInfo {id=channel_501, flags=0x89 [DynIc-fStr]
                    shortLabel=HTV7, Thể thao, resId=0[null]
                  ShortcutInfo {id=channel_473, flags=0x89 [DynIc-fStr]
                    shortLabel=VTV1 HD, resId=0[null]
              Package: com.khac
        """.trimIndent()
        assertEquals(listOf("VTV1 HD" to "473", "HTV7, Thể thao" to "501"), KenhNhanh.kenhM3u(dump))
        assertEquals(emptyList<Pair<String, String>>(), KenhNhanh.kenhM3u(""))
    }

    @Test fun khoaTen_gopTenKhac() {
        assertEquals("vtv1", KenhNhanh.khoaTen("VTV1 HD"))
        assertEquals("hanoi1", KenhNhanh.khoaTen("Truyền hình Hà Nội 1"))
        assertEquals("hanoi1", KenhNhanh.khoaTen("HaNoiTV 1"))
        assertEquals("quocphongvietnam", KenhNhanh.khoaTen("QPVN HD"))
        assertEquals(KenhNhanh.khoaTen("Kênh Vĩnh Long 1"), KenhNhanh.khoaTen("THVL1"))
    }

    /** Ô Auto (1/11/2026): nguồn tìm theo tên kênh, thứ tự VTVgo → TV360 (biết số) → FPT Play → M3U. */
    @Test fun auto_nguonVaJson() {
        val go = listOf(KenhVtvGo.Kenh("VTV1", "1", "VTV", "", false), KenhVtvGo.Kenh("Truyền hình Hà Nội 1", "hn1", "Địa phương", "", false))
        val fpt = listOf(KenhFpt.Kenh("vtv1-hd", "VTV1 HD", "Kênh cơ bản", "1", ""), KenhFpt.Kenh("htv7-hd", "HTV7 HD", "Kênh cơ bản", "73", "", canGoi = true))
        val m3u = listOf("VTV1 HD" to "473", "HTV7 HD" to "12", "VTV1 FHD" to "474", "VTV1 HD (2)" to "475")
        val ds = KenhNhanh.nguonAuto("VTV1", go, fpt, m3u, KenhNhanh.GOI_Y)
        assertEquals(listOf(KenhNhanh.LOAI_VTVGO, KenhNhanh.LOAI_TV360, KenhNhanh.LOAI_FPT, KenhNhanh.LOAI_M3U, KenhNhanh.LOAI_M3U), ds.map { it.loai })
        assertEquals(listOf("1", "001", "vtv1-hd", "473", "474"), ds.map { it.ma.ifEmpty { it.so } })
        // HTV7: không có trên VTVgo; TV360 số 73 trong gợi ý.
        assertEquals(listOf(KenhNhanh.LOAI_TV360, KenhNhanh.LOAI_FPT, KenhNhanh.LOAI_M3U), KenhNhanh.nguonAuto("HTV7", go, fpt, m3u, KenhNhanh.GOI_Y).map { it.loai })
        // Không biết số TV360 thì không có TV360.
        assertEquals(listOf(KenhNhanh.LOAI_VTVGO), KenhNhanh.nguonAuto("Hà Nội 1", go, fpt, m3u, emptyList()).map { it.loai })
        assertEquals(emptyList<KenhNhanh.Kenh>(), KenhNhanh.nguonAuto("  ", go, fpt, m3u, KenhNhanh.GOI_Y))

        val k = KenhNhanh.Kenh("", "VTV1", "", KenhNhanh.LOAI_AUTO, ds)
        assertTrue(k.laAuto && !k.laTv360)
        val s = KenhNhanh.json(k)
        assertTrue(s.all { it.code < 0x80 })
        val nguon = JSONObject(s).getJSONArray("nguon")
        assertEquals("tv360", nguon.getJSONObject(1).getString("app"))
        assertEquals(k, KenhNhanh.tuJson(JSONObject(s)))
        // Ô Auto trùng theo tên kênh, không theo nguồn.
        assertTrue(k.trung(KenhNhanh.Kenh("", "VTV1 HD", "", KenhNhanh.LOAI_AUTO)))
        assertFalse(k.trung(KenhNhanh.Kenh("", "VTV1", "1")))
        assertNull(KenhNhanh.tuJson(JSONObject("""{"app":"auto","ten":"VTV1","nguon":[{"app":"vtvgo","ma":"x y","ten":"VTV1"}]}""")))
        assertFalse(JSONObject(s).has("coDinh"))
    }

    /** Ô Auto thứ tự riêng (1/11/2026, VD VTV1 mở TV360 trước): xếp nguồn theo loại, cờ coDinh đi theo cấu hình. */
    @Test fun auto_thuTuRieng() {
        val ds = listOf(
            KenhNhanh.Kenh("", "VTV1", "1"), KenhNhanh.Kenh("001", "VTV1"), KenhNhanh.Kenh("", "VTV1 HD", "473", KenhNhanh.LOAI_M3U),
            KenhNhanh.Kenh("", "VTV1 FHD", "474", KenhNhanh.LOAI_M3U), KenhNhanh.Kenh("", "VTV1 HD", "vtv1-hd", KenhNhanh.LOAI_FPT),
        )
        val xep = KenhNhanh.theoThuTu(ds, listOf(KenhNhanh.LOAI_TV360, KenhNhanh.LOAI_M3U))
        // Loại có trong thứ tự riêng trước (hai nguồn M3U giữ thứ tự), loại chưa có ở cuối như cũ.
        assertEquals(listOf("001", "473", "474", "1", "vtv1-hd"), xep.map { it.ma.ifEmpty { it.so } })
        val k = KenhNhanh.Kenh("", "VTV1", "", KenhNhanh.LOAI_AUTO, xep, coDinh = true)
        val j = JSONObject(KenhNhanh.json(k))
        assertTrue(j.getBoolean("coDinh"))
        assertEquals("tv360", j.getJSONArray("nguon").getJSONObject(0).getString("app"))
        assertEquals(k, KenhNhanh.tuJson(j))
        // Gửi Map Voice (câu nói theo thứ tự của ô): chỉ ô Auto có thứ tự riêng.
        val tuDong = KenhNhanh.Kenh("", "VTV6", "", KenhNhanh.LOAI_AUTO, listOf(KenhNhanh.Kenh("", "VTV6", "13")))
        val a = JSONArray(KenhNhanh.jsonThuTu(listOf(KenhNhanh.Kenh("001", "VTV1"), tuDong, k)))
        assertEquals(1, a.length())
        assertEquals("VTV1", a.getJSONObject(0).getString("ten"))
        assertEquals("tv360", a.getJSONObject(0).getJSONArray("nguon").getJSONObject(0).getString("app"))
        assertEquals("[]", KenhNhanh.jsonThuTu(listOf(tuDong)))
    }
}
