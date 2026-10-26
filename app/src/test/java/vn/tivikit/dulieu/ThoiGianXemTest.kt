package vn.tivikit.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Báo cáo thời gian xem đọc từ app khoá trên tivi (content call … --method thoi-gian). */
class ThoiGianXemTest {
    private val json = """{"homNay":"2026-10-25","demTu":"2026-10-23","coQuyen":true,"ngay":[
        {"ngay":"2026-10-22","thu":3,"giay":0,"gioiHan":120,"apps":[{"goi":"com.google.android.youtube.tv","ten":"YouTube","giay":5400}]},
        {"ngay":"2026-10-23","thu":4,"giay":4800,"gioiHan":120,"apps":[]},
        {"ngay":"2026-10-25","thu":7,"giay":7500,"gioiHan":180,"apps":[{"goi":"x.y","ten":"","giay":61}]}]}"""

    @Test fun docBaoCao() {
        val bc = ThoiGianXem.tuJson(JSONObject(json))
        assertEquals(3, bc.ngay.size); assertTrue(bc.coQuyen)
        // Trước ngày app khoá bắt đầu đếm: chỉ có danh sách app, không có số giờ xem.
        assertFalse(bc.ngay[0].daDem); assertTrue(bc.ngay[1].daDem); assertTrue(bc.ngay[2].daDem)
        assertEquals("YouTube", bc.ngay[0].apps.single().ten)
        assertEquals("x.y", bc.ngay[2].apps.single().ten) // không có tên thì dùng tên gói
        assertEquals(7500L, bc.homNayXem!!.giay); assertEquals(180, bc.homNayXem!!.gioiHanPhut)
    }

    @Test fun khongCoHomNay_hoacRong() {
        assertNull(ThoiGianXem.tuJson(JSONObject("""{"homNay":"2026-10-26","ngay":[{"ngay":"2026-10-25","thu":7,"giay":1}]}""")).homNayXem)
        val rong = ThoiGianXem.tuJson(JSONObject("{}"))
        assertTrue(rong.ngay.isEmpty()); assertFalse(rong.coQuyen)
        // Chưa có ngày bắt đầu đếm (app khoá chưa đếm lần nào): không ngày nào có số.
        assertFalse(ThoiGianXem.tuJson(JSONObject("""{"ngay":[{"ngay":"2026-10-25","thu":7,"giay":0}]}""")).ngay[0].daDem)
    }

    @Test fun chuThoiGian() {
        assertEquals("dưới 1 phút", ThoiGianXem.chu(59)); assertEquals("45 phút", ThoiGianXem.chu(45 * 60 + 30))
        assertEquals("2 giờ", ThoiGianXem.chu(7200)); assertEquals("2 giờ 5 phút", ThoiGianXem.chu(7500))
        assertEquals("30p", ThoiGianXem.ngan(1800)); assertEquals("2h", ThoiGianXem.ngan(7200)); assertEquals("1h30", ThoiGianXem.ngan(5400))
        assertEquals("Không giới hạn", ThoiGianXem.chuGioiHan(0)); assertEquals("30 phút", ThoiGianXem.chuGioiHan(30))
        assertEquals("1,5 giờ", ThoiGianXem.chuGioiHan(90)); assertEquals("3 giờ", ThoiGianXem.chuGioiHan(180))
    }
}
