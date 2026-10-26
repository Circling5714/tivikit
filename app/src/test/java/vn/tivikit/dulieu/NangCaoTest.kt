package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mục Nâng cao: danh mục từ assets/nang-cao.json và chuyển chữ người dùng gõ sang giá trị config.json. */
class NangCaoTest {
    private fun tc(kieu: String, min: Int = 0, max: Int = 100, chon: List<String> = emptyList()) = TuyChon("k", kieu, min, max, chon, "", false)

    @Test fun danhMucHopLe() {
        val a = JSONArray(File("src/main/assets/nang-cao.json").readText())
        assertTrue("có ít nhất 40 tuỳ chọn", a.length() >= 40)
        val khoa = (0 until a.length()).map { a.getJSONObject(it).getString("khoa") }
        assertEquals("không trùng khoá", khoa.size, khoa.toSet().size)
        for (k in listOf("dock_apps", "lunar_date", "always_show_names", "wallpaper")) assertTrue("$k do app tự lo", k !in khoa)
        val appKeys = (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("khoa") == "app_keys" }
        assertTrue("app_keys thuộc nhóm cẩn thận", appKeys.getBoolean("canThan"))
    }

    @Test fun chuyenKieu() {
        assertEquals(true, NangCao.giaTri(tc("bool"), "true"))
        assertNull(NangCao.giaTri(tc("bool"), "có"))
        assertEquals(8, NangCao.giaTri(tc("so", 1, 8), "12"))
        assertEquals(1, NangCao.giaTri(tc("so", 1, 8), "-3"))
        assertNull(NangCao.giaTri(tc("so"), "abc"))
        assertEquals("[\"a.b\",\"c.d\"]", NangCao.giaTri(tc("ds"), "a.b, c.d,\n").toString())
        assertEquals("[83,190]", NangCao.giaTri(tc("ds-so"), "83, 190").toString())
        assertEquals("shuffle", NangCao.giaTri(tc("chon", chon = listOf("shuffle", "sequential")), "shuffle"))
        assertNull(NangCao.giaTri(tc("chon", chon = listOf("shuffle")), "random"))
        assertNull(NangCao.giaTri(tc("chu"), "   "))
    }

    @Test fun docLaiTuTivi() {
        val j = JSONObject("""{"columns":6,"hidden_apps":["a","b"]}""")
        assertEquals("6", NangCao.docChu(tc("so").copy(khoa = "columns"), j))
        assertEquals("a, b", NangCao.docChu(tc("ds").copy(khoa = "hidden_apps"), j))
        assertNull(NangCao.docChu(tc("bool").copy(khoa = "dock_row"), j))
    }
}
