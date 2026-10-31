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
}
