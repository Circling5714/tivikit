package vn.tivikit.dulieu

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chuyển app phụ bản cũ (com.icm.cleantv.*) sang gói mới: đổi tên gói trong config.json Home sạch và lịch Hẹn giờ. */
class GoiCuTest {
    private val doi = (1..KenhNhanh.SO_O).associate { GoiCu.kenh(it) to KenhNhanh.goiCua(it) }

    private fun mang(j: JSONObject, k: String): List<String> = j.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }

    @Test fun tenGoi() {
        assertEquals("com.icm.cleantv.kenh", GoiCu.kenh(1))
        assertEquals("com.icm.cleantv.kenh4", GoiCu.kenh(4))
        assertEquals("vn.tivikit.kenh", KenhNhanh.goiCua(1))
        assertEquals("vn.tivikit.hengio", HenGio.GOI)
        assertEquals(KenhNhanh.SO_O + 2, GoiCu.APP_PHU.size)
        assertTrue(GoiCu.KHOA in GoiCu.APP_PHU && GoiCu.HEN_GIO in GoiCu.APP_PHU)
    }

    @Test fun doiChuChiThayNguyenTenGoi() {
        assertEquals("scan:490:vn.tivikit.kenh", GoiCu.doiChu("scan:490:com.icm.cleantv.kenh", doi))
        assertEquals("hold:KEYCODE_PROG_RED:vn.tivikit.kenh3", GoiCu.doiChu("hold:KEYCODE_PROG_RED:com.icm.cleantv.kenh3", doi))
        assertEquals("vn.tivikit.kenh=VTV1", GoiCu.doiChu("com.icm.cleantv.kenh=VTV1", doi))
        assertEquals("vn.tivikit.kenh2/zone.vgs.kenh.MoKenh", GoiCu.doiChu("com.icm.cleantv.kenh2/zone.vgs.kenh.MoKenh", doi))
        // Không phải ô kênh cũ: để nguyên.
        listOf("com.icm.cleantv", "com.icm.cleantv.kenh7", "com.icm.cleantv.kenhx", "org.com.icm.cleantv.kenh", "com.icm.cleantv.kenh.x", "org.smarttube.stable")
            .forEach { assertEquals(it, GoiCu.doiChu(it, doi)) }
    }

    @Test fun doiCauHinhHomeSach() {
        val j = JSONObject()
            .put("app_keys", JSONArray(listOf("KEYCODE_BUTTON_3:org.smarttube.stable", "scan:490:com.icm.cleantv.kenh")))
            .put("vgs_phim_rieng", JSONArray(listOf("scan:490:com.icm.cleantv.kenh")))
            .put("app_labels", JSONArray(listOf("com.telekom.onetv.tv.pl=TV", "com.icm.cleantv.kenh=VTV1", "com.icm.cleantv.kenh3=HTV7 HD")))
            // Ô mới đã nằm sẵn trong dock (lần chuyển trước dở dang): sau khi đổi không được thành hai ô.
            .put("dock_apps", JSONArray(listOf("com.google.android.youtube.tv", "vn.tivikit.kenh", "com.icm.cleantv.kenh", "com.icm.cleantv.kenh3")))
            .put("greeting_name", "Nhà bác Hùng")
            .put("columns", 5)
        assertTrue(GoiCu.doiTrongCauHinh(j, doi))
        assertEquals(listOf("KEYCODE_BUTTON_3:org.smarttube.stable", "scan:490:vn.tivikit.kenh"), mang(j, "app_keys"))
        assertEquals(listOf("scan:490:vn.tivikit.kenh"), mang(j, "vgs_phim_rieng"))
        assertEquals(listOf("com.telekom.onetv.tv.pl=TV", "vn.tivikit.kenh=VTV1", "vn.tivikit.kenh3=HTV7 HD"), mang(j, "app_labels"))
        assertEquals(listOf("com.google.android.youtube.tv", "vn.tivikit.kenh", "vn.tivikit.kenh3"), mang(j, "dock_apps"))
        assertEquals("Nhà bác Hùng", j.getString("greeting_name"))
        assertEquals(5, j.getInt("columns"))
        // Đọc lại như các màn khác: tên ô và nút gán nhận ra gói mới.
        assertEquals(mapOf("vn.tivikit.kenh" to "VTV1", "vn.tivikit.kenh3" to "HTV7 HD"), KenhNhanh.tenTrong(j))
        assertEquals("vn.tivikit.kenh", PhimRieng.dichCua(PhimRieng.docTu(j).single()))
    }

    @Test fun cauHinhKhongCoGoiCuThiKhongDoi() {
        val j = JSONObject().put("dock_apps", JSONArray(listOf("com.google.android.youtube.tv", "vn.tivikit.kenh"))).put("app_keys", JSONArray(listOf("scan:490:org.smarttube.stable")))
        val truoc = j.toString()
        assertFalse(GoiCu.doiTrongCauHinh(j, doi))
        assertEquals(truoc, j.toString())
        assertFalse(GoiCu.doiTrongCauHinh(JSONObject(), doi))
    }

    /** Ô cũ chuyển sang số ô khác (ô mới cùng số đã có kênh khác): mọi chỗ đi theo bảng đổi thật. */
    @Test fun doiTheoBangDoiThat() {
        val j = JSONObject().put("app_labels", JSONArray(listOf("com.icm.cleantv.kenh=VTV3")))
        assertTrue(GoiCu.doiTrongCauHinh(j, mapOf(GoiCu.kenh(1) to KenhNhanh.goiCua(2))))
        assertEquals(listOf("vn.tivikit.kenh2=VTV3"), mang(j, "app_labels"))
    }

    @Test fun doiLichHenGio() {
        val ds = listOf(
            HenGio.Viec(6, 30, setOf(1, 2, 3, 4, 5), HenGio.BAT, GoiCu.kenh(1)),
            HenGio.Viec(18, 58, setOf(6, 7), HenGio.MO, GoiCu.kenh(2), bat = false),
            HenGio.Viec(23, 0, (1..7).toSet(), HenGio.TAT),
            HenGio.Viec(19, 0, setOf(7), HenGio.MO, "org.smarttube.stable"),
        )
        val moi = GoiCu.doiLich(ds, mapOf(GoiCu.kenh(1) to KenhNhanh.goiCua(1)))
        assertEquals("vn.tivikit.kenh", moi[0].goi)
        // Ô 2 chưa chuyển được: giữ gói cũ (app cũ vẫn còn trên tivi), không mất việc.
        assertEquals(GoiCu.kenh(2), moi[1].goi)
        assertFalse(moi[1].bat)
        assertEquals(ds.drop(2), moi.drop(2))
    }
}
