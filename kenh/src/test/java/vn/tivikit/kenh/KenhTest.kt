package vn.tivikit.kenh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kênh nhanh (25/10/2026): số kênh dạng shortcutKeyboard 3 chữ số của TV360, cấu hình đi qua ADB dạng JSON. */
class KenhTest {
    @Test fun chuanSo() {
        assertEquals("001", Kenh.chuanSo("1"))
        assertEquals("001", Kenh.chuanSo(" 001 "))
        assertEquals("073", Kenh.chuanSo("73"))
        assertEquals("360", Kenh.chuanSo("360"))
        assertNull(Kenh.chuanSo("0"))
        assertNull(Kenh.chuanSo("1000"))
        assertNull(Kenh.chuanSo("VTV1"))
        assertNull(Kenh.chuanSo(null))
    }

    @Test fun json_khuHoi() {
        val k = Kenh("073", "HTV7 HD")
        assertEquals(k, Kenh.tuJson(k.json()))
        assertEquals(Kenh("001", "VTV1"), Kenh.tuJson("""{"so":"1","ten":"  VTV1 "}"""))
        assertNull(Kenh.tuJson("""{"so":"abc","ten":"VTV1"}"""))
        assertNull(Kenh.tuJson("khong phai json"))
        assertNull(Kenh.tuJson(null))
    }

    /** Ô VTVgo (26/10/2026): mã kênh của VTVgo thay cho số kênh TV360; mã lạ (dấu cách, ký tự shell) thì bỏ. */
    @Test fun vtvgo_jsonKhuHoi_vaLink() {
        val k = Kenh("", "VTV3", "3")
        assertTrue(k.laVtvGo)
        assertEquals(k, Kenh.tuJson(k.json()))
        assertEquals(Kenh("", "Vĩnh Long 1", "vl1"), Kenh.tuJson("""{"app":"vtvgo","ma":"vl1","ten":" Vĩnh Long 1 "}"""))
        assertNull(Kenh.tuJson("""{"app":"vtvgo","ma":"bad id","ten":"X"}"""))
        assertNull(Kenh.tuJson("""{"app":"vtvgo","ma":"3","ten":""}"""))
        assertEquals("vtvgo://play?mediaType=CHANNEL&contentId=3", Kenh.linkVtvGo("3"))
        // Ô TV360 cũ (chưa có khoá app) vẫn đọc ra ô TV360.
        assertFalse(Kenh.tuJson("""{"so":"3","ten":"VTV3"}""")!!.laVtvGo)
    }

    /** Ô FPT Play (31/10/2026): mã kênh của FPT Play (vtv1-hd), link màn xem truyền hình của FPT Play. */
    @Test fun fpt_jsonKhuHoi_vaLink() {
        val k = Kenh("", "VTV1", "vtv1-hd", Kenh.APP_FPT)
        assertTrue(k.laFpt)
        assertFalse(k.laVtvGo)
        val j = org.json.JSONObject(k.json())
        assertEquals(listOf("fptplay", "vtv1-hd", "VTV1"), listOf(j.getString("app"), j.getString("ma"), j.getString("ten")))
        assertFalse(j.has("so"))
        assertEquals(k, Kenh.tuJson(k.json()))
        assertEquals(Kenh("", "HTV Thể Thao", "htvc-the-thao", Kenh.APP_FPT), Kenh.tuJson("""{"app":"fptplay","ma":" htvc-the-thao ","ten":"HTV Thể Thao"}"""))
        assertNull(Kenh.tuJson("""{"app":"fptplay","ma":"vtv1 hd","ten":"VTV1"}"""))
        assertNull(Kenh.tuJson("""{"app":"fptplay","ma":"vtv1-hd'; reboot","ten":"VTV1"}"""))
        assertEquals("https://fptplay.vn/xem-truyen-hinh/vtv1-hd", Kenh.linkFpt("vtv1-hd"))
        // Ô VTVgo và TV360 giữ nguyên dạng JSON cũ.
        assertEquals("vtvgo", org.json.JSONObject(Kenh("", "VTV3", "3").json()).getString("app"))
        val tv360 = org.json.JSONObject(Kenh("001", "VTV1").json())
        assertEquals("001", tv360.getString("so"))
        assertFalse(tv360.has("app"))
    }

    /** Ô M3U (1/11/2026): mã kênh của M3UAndroid là số (phím tắt channel_473 trên Skyworth là VTV1 HD). */
    @Test fun m3u_jsonKhuHoi() {
        val k = Kenh("", "VTV1 HD", "473", Kenh.APP_M3U)
        assertTrue(k.laM3u)
        assertFalse(k.laFpt || k.laVtvGo)
        assertEquals(k, Kenh.tuJson(k.json()))
        assertEquals("m3u", org.json.JSONObject(k.json()).getString("app"))
        assertEquals(k, Kenh.tuJson("""{"app":"m3u","ma":" 473 ","ten":"VTV1 HD"}"""))
        // Mã phải là số nguyên dương (đi vào extra kiểu int của M3U).
        assertNull(Kenh.tuJson("""{"app":"m3u","ma":"vtv1","ten":"VTV1"}"""))
        assertNull(Kenh.tuJson("""{"app":"m3u","ma":"0","ten":"VTV1"}"""))
        assertNull(Kenh.tuJson("""{"app":"m3u","ma":"12345678901","ten":"VTV1"}"""))
    }

    /** Ô Auto (1/11/2026): tên kênh và nguồn, gửi cho Map Voice mỗi nguồn ghi rõ app (cả TV360). */
    @Test fun auto_jsonKhuHoiVaBoNguonHong() {
        val k = Kenh("", "VTV1", "", Kenh.APP_AUTO, listOf(
            Kenh("", "VTV1", "1"), Kenh("001", "VTV1"), Kenh("", "VTV1 HD", "vtv1-hd", Kenh.APP_FPT), Kenh("", "VTV1 HD", "473", Kenh.APP_M3U),
        ))
        assertTrue(k.laAuto)
        assertEquals(k, Kenh.tuJson(k.json()))
        val ds = org.json.JSONArray(k.jsonNguon())
        assertEquals(listOf("vtvgo", "tv360", "fptplay", "m3u"), (0 until ds.length()).map { ds.getJSONObject(it).getString("app") })
        assertEquals("001", ds.getJSONObject(1).getString("so"))
        // Nguồn hỏng bỏ; TV360 không biết số thì tìm theo tên; không còn nguồn nào thì không nhận.
        val doc = Kenh.tuJson("""{"app":"auto","ten":"HTV7 HD","nguon":[{"app":"vtvgo","ma":"x;reboot"},{"app":"tv360","ten":"HTV7 HD"},{"app":"auto","ten":"x","nguon":[]},{"app":"m3u","ma":"12","ten":"HTV7 HD"},{"app":"m3u","ma":"13"}]}""")
        assertEquals(listOf(Kenh("", "HTV7 HD"), Kenh("", "HTV7 HD", "12", Kenh.APP_M3U)), doc?.nguon)
        assertNull(Kenh.tuJson("""{"app":"auto","ten":"VTV1","nguon":[{"app":"vtvgo","ma":"x y"}]}"""))
        assertNull(Kenh.tuJson("""{"app":"auto","ten":"","nguon":[{"app":"vtvgo","ma":"1"}]}"""))
        assertFalse(k.coDinh)
        assertFalse(org.json.JSONObject(k.json()).has("coDinh"))
    }

    /** Ô Auto thứ tự riêng (1/11/2026): cờ coDinh đi theo cấu hình, thứ tự nguồn giữ nguyên (TV360 trước VTVgo). */
    @Test fun auto_thuTuRieng() {
        val k = Kenh("", "VTV1", "", Kenh.APP_AUTO, listOf(Kenh("001", "VTV1"), Kenh("", "VTV1", "1")), coDinh = true)
        val doc = Kenh.tuJson(k.json())
        assertEquals(k, doc)
        assertTrue(doc!!.coDinh)
        assertEquals(listOf(Kenh.APP_TV360, Kenh.APP_VTVGO), doc.nguon.map { it.app })
    }

    /** Ô IPTV (1/11/2026): mã kênh là tvg-id (hay tên đã gọn), không có ký tự lạ (đi trong lệnh shell khi đặt kênh). */
    @Test fun iptv_jsonKhuHoi() {
        val k = Kenh("", "VTV1 HD", "vtv1hd", Kenh.APP_IPTV)
        assertTrue(k.laIptv)
        assertEquals(k, Kenh.tuJson(k.json()))
        assertEquals(k, Kenh.tuJson("""{"app":"iptv","ma":"vtv1hd","ten":"VTV1 HD"}"""))
        assertNull(Kenh.tuJson("""{"app":"iptv","ma":"vtv1';reboot","ten":"VTV1"}"""))
        assertNull(Kenh.tuJson("""{"app":"iptv","ma":"vtv1hd","ten":""}"""))
        // Nguồn IPTV trong ô Auto.
        val a = Kenh.tuJson("""{"app":"auto","ten":"VTV1","nguon":[{"app":"vtvgo","ma":"1","ten":"VTV1"},{"app":"iptv","ma":"vtv1hd","ten":"VTV1 HD"}]}""")
        assertEquals(listOf(Kenh.APP_VTVGO, Kenh.APP_IPTV), a?.nguon?.map { it.app })
    }
}
