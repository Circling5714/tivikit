package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ô kênh VTVgo trên Home sạch (CleanTV 2.10.0 – 3.0.0): tên, slug, dòng link_tiles khớp LinkTiles của bản vá launcher. */
class KenhVtvGoTest {
    private val json = """{"data":[
        {"name":"Kênh quốc gia","channels":[
            {"id":"1","name":"VTV1","thumbnail":"https://img.vtvdigital.org/vtv1.webp","enabled":true},
            {"id":"2","name":"VTV2","logo":"https://img.vtvdigital.org/vtv2.png"},
            {"id":"bad id","name":"Sai mã"},
            {"id":"1","name":"VTV1 trùng mã"}]},
        {"name":"Kênh địa phương","channels":[
            {"id":"vl1","name":"Truyền hình Vĩnh Long 1","mustLogin":true},
            {"id":"off","name":"Kênh tắt","enabled":false},
            {"id":"cjk","name":"中文台"}]}]}"""

    @Test fun tach_boMaSai_trungMa_kenhTat_tenKhongCoChuLatin() {
        val ds = KenhVtvGo.tach(json)
        assertEquals(listOf("1", "2", "vl1"), ds.map { it.ma })
        assertEquals("https://img.vtvdigital.org/vtv2.png", ds[1].anh)
        assertEquals("Kênh địa phương", ds[2].nhom)
        assertTrue(ds[2].canDangNhap)
        assertEquals("Vĩnh Long 1", ds[2].tenO)
    }

    /** Như LinkTiles: chữ thường, bỏ mọi ký tự ngoài a-z 0-9 (chữ có dấu bị bỏ). */
    @Test fun slug_nhuLauncher() {
        assertEquals("vtv1", KenhVtvGo.slug("VTV1"))
        assertEquals("vnhlong1", KenhVtvGo.slug("Vĩnh Long 1"))
        assertEquals("", KenhVtvGo.slug("中文台"))
    }

    @Test fun dongLinkTiles_diVaVe() {
        val dong = KenhVtvGo.oLink("VTV3", "3")
        assertEquals("VTV3|vn.vtv.vtvgotv|vtvgo://play?mediaType=CHANNEL&contentId=3|#E8590C", dong)
        assertEquals("VTV3" to "3", KenhVtvGo.tuLink(dong))
        assertTrue(KenhVtvGo.oLink("HTV9", "htv9").endsWith("|#C62828"))
        assertNull(KenhVtvGo.tuLink("YouTube|com.google.android.youtube.tv|https://youtube.com|#FF0000"))
        assertNull(KenhVtvGo.tuLink("hỏng"))
    }

    /**
     * Lưu ô kênh từ màn Kênh nhanh vào config.json đang có (26/10/2026): ô kênh đứng đầu dock theo thứ tự chọn, app giữ
     * nguyên thứ tự, ô kênh cũ bỏ đi, khoá khác không đụng tới; đọc lại ra đúng danh sách vừa ghi.
     */
    @Test fun datVao_oKenhDauDock_giuAppVaKhoaKhac() {
        val j = org.json.JSONObject("""{"theme":"tivikit","link_tiles":["VTV9|vn.vtv.vtvgotv|vtvgo://play?mediaType=CHANNEL&contentId=39|#283593"],
            "dock_apps":["vgs.kenh.vtv9","com.google.android.youtube.tv","vn.tivikit.kenh"," "]}""")
        assertTrue(KenhVtvGo.datVao(j, listOf("VTV1" to "1", "Vĩnh Long 1" to "vl1")))
        val dock = j.getJSONArray("dock_apps").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertEquals(listOf("vgs.kenh.vtv1", "vgs.kenh.vnhlong1", "com.google.android.youtube.tv", "vn.tivikit.kenh"), dock)
        assertEquals(listOf("VTV1" to "1", "Vĩnh Long 1" to "vl1"), KenhVtvGo.docTu(j))
        assertEquals("tivikit", j.getString("theme"))
    }

    @Test fun datVao_boHetKenh_vaKhongCoDock() {
        val j = org.json.JSONObject("""{"link_tiles":["VTV1|vn.vtv.vtvgotv|vtvgo://play?mediaType=CHANNEL&contentId=1|#D71920"],"dock_apps":["vgs.kenh.vtv1","com.a.b"]}""")
        assertTrue(KenhVtvGo.datVao(j, emptyList()))
        assertTrue(!j.has("link_tiles"))
        assertEquals("[\"com.a.b\"]", j.getJSONArray("dock_apps").toString())
        assertEquals(emptyList<Pair<String, String>>(), KenhVtvGo.docTu(j))
        // Home sạch đang dùng dock mặc định: không ghi gì (ghi là mất dock đó).
        val macDinh = org.json.JSONObject("""{"theme":"tivikit"}""")
        assertTrue(!KenhVtvGo.datVao(macDinh, listOf("VTV1" to "1")))
        assertEquals("""{"theme":"tivikit"}""", macDinh.toString())
    }

    /** Launcher bỏ ô có tên quá 24 ký tự; mã kênh VTV1–9 không trùng nhau. */
    @Test fun tenO_toiDa24_vaMaVtvKhongTrung() {
        assertEquals(24, KenhVtvGo.tenNgan("Truyền hình " + "A".repeat(40)).length)
        assertEquals(KenhVtvGo.VTV.size, KenhVtvGo.VTV.map { it.second }.toSet().size)
    }
}
