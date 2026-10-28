package vn.tivikit.khoa

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class KhoaAppTest {
    private fun luc(y: Int, mo: Int, d: Int, h: Int, mi: Int) = Calendar.getInstance().apply { clear(); set(y, mo - 1, d, h, mi) }
    private val ST = "org.smarttube.stable"
    private val YT = "com.google.android.youtube.tv"
    private val CA_TUAN = (1..7).toSet()
    private fun ch(vararg l: LuatApp, bat: Boolean = true, coMa: Boolean = true) = CauHinh(bat, emptyList(), coMa = coMa, apps = l.toList())
    private val khongDung: (String) -> Long = { 0L }

    @Test fun khoaLuonVaTatKhoa() {
        val c = ch(LuatApp("a", setOf(ST), luon = true))
        val bay = luc(2026, 10, 28, 10, 0)
        assertEquals(KhoaApp.LyDo.LUON, KhoaApp.khoa(c, emptyList(), ST, bay, khongDung)!!.lyDo)
        assertNull(KhoaApp.khoa(c, emptyList(), YT, bay, khongDung))                     // app không có trong luật
        assertNull(KhoaApp.khoa(c.copy(bat = false), emptyList(), ST, bay, khongDung))     // tắt Khoá trẻ em
        assertNull(KhoaApp.khoa(c.copy(coMa = false), emptyList(), ST, bay, khongDung))    // chưa có mã thì không ai mở được: không khoá
        assertNull(KhoaApp.khoa(c, emptyList(), ST, bay, khongDung, moDen = bay.timeInMillis + 1))  // bố mẹ vừa mở riêng app
    }

    @Test fun khungGioCuaApp_moLaiLucHetKhung() {
        val c = ch(LuatApp("a", setOf(ST, YT), khung = listOf(Khung(Luat.phut("20:00"), Luat.phut("06:00"), CA_TUAN))))
        val k = KhoaApp.khoa(c, emptyList(), YT, luc(2026, 10, 28, 21, 15), khongDung)!!
        assertEquals(KhoaApp.LyDo.GIO_KHOA, k.lyDo)
        val het = Calendar.getInstance().apply { timeInMillis = k.den!! }
        assertEquals(29, het.get(Calendar.DAY_OF_MONTH)); assertEquals(6, het.get(Calendar.HOUR_OF_DAY))
        assertNull(KhoaApp.khoa(c, emptyList(), YT, luc(2026, 10, 28, 19, 59), khongDung))
    }

    @Test fun hetPhutTrongNgay_tinhChungCaNhom() {
        val c = ch(LuatApp("video", setOf(ST, YT), phutNgay = 60))
        val bay = luc(2026, 10, 28, 15, 0)
        assertNull(KhoaApp.khoa(c, emptyList(), ST, bay, { if (it == "video") 3599 else 0 }))
        val k = KhoaApp.khoa(c, emptyList(), ST, bay, { if (it == "video") 3600 else 0 })!!
        assertEquals(KhoaApp.LyDo.HET_GIO, k.lyDo); assertEquals(60, k.phut)
        assertEquals(KhoaApp.nuaDem(bay.timeInMillis), k.den)
        assertEquals(listOf("video"), KhoaApp.luatDem(c, YT)); assertEquals(emptyList<String>(), KhoaApp.luatDem(c, "com.khac.app"))
    }

    @Test fun henKhoaMotLan() {
        val bay = luc(2026, 10, 28, 19, 0)
        val hen = listOf(HenKhoa(setOf(ST), bay.timeInMillis + 30 * 60_000, KhoaApp.nuaDem(bay.timeInMillis)))
        val c = ch()
        assertNull(KhoaApp.khoa(c, hen, ST, bay, khongDung))                                  // chưa tới giờ hẹn
        assertEquals(KhoaApp.LyDo.HEN, KhoaApp.khoa(c, hen, ST, luc(2026, 10, 28, 19, 30), khongDung)!!.lyDo)
        assertNull(KhoaApp.khoa(c, hen, ST, luc(2026, 10, 29, 0, 0), khongDung))               // sang ngày mới thì hết
        assertEquals(setOf(ST), KhoaApp.goiCo(c, hen))
        assertEquals(0, KhoaApp.conHan(hen, luc(2026, 10, 29, 0, 0).timeInMillis).size)
    }

    @Test fun sapKhoa_baoTruoc() {
        val dem = ch(LuatApp("a", setOf(ST), khung = listOf(Khung(Luat.phut("20:00"), Luat.phut("06:00"), CA_TUAN))))
        assertEquals(3, KhoaApp.sapKhoa(dem, emptyList(), ST, luc(2026, 10, 28, 19, 57), khongDung))
        assertNull(KhoaApp.sapKhoa(dem, emptyList(), ST, luc(2026, 10, 28, 19, 50), khongDung))
        // Còn 2 phút là đủ 60 phút trong ngày.
        val han = ch(LuatApp("a", setOf(ST), phutNgay = 60))
        assertEquals(2, KhoaApp.sapKhoa(han, emptyList(), ST, luc(2026, 10, 28, 10, 0), { 58 * 60L }))
        // 23:58 đã dùng 58 phút: 00:00 mới đủ 60 nhưng sang ngày mới đếm lại từ 0, nên không báo.
        assertNull(KhoaApp.sapKhoa(han, emptyList(), ST, luc(2026, 10, 28, 23, 58), { 58 * 60L }))
        // Hẹn khoá sau 4 phút.
        val bay = luc(2026, 10, 28, 18, 0)
        assertEquals(4, KhoaApp.sapKhoa(ch(), listOf(HenKhoa(setOf(ST), bay.timeInMillis + 4 * 60_000, KhoaApp.nuaDem(bay.timeInMillis))), ST, bay, khongDung))
    }

    @Test fun docLuatVaHen() {
        val c = Luat.docJson("""{"bat":true,"khung":[],"apps":[
            {"id":"v","goi":["org.smarttube.stable","rm -rf /","com.google.android.youtube.tv"],"khung":[{"tu":"20:00","den":"06:00"}],"phutNgay":90},
            {"id":"x","goi":[]}]}""")!!
        assertEquals(1, c.apps.size)
        assertEquals(setOf(ST, YT), c.apps[0].goi)
        assertEquals(Khung(1200, 360, CA_TUAN), c.apps[0].khung[0]); assertEquals(90, c.apps[0].phutNgay)
        val hen = listOf(HenKhoa(setOf(ST, YT), 1000, 2000))
        assertEquals(hen, KhoaApp.docHen(KhoaApp.jsonHen(hen).toString()))
        assertEquals(emptyList<HenKhoa>(), KhoaApp.docHen("hỏng"))
        assertEquals(setOf(ST), KhoaApp.dsGoi(JSONArray(listOf(ST, "khong hop le"))))
    }

    @Test fun nuaDem() {
        val nd = Calendar.getInstance().apply { timeInMillis = KhoaApp.nuaDem(luc(2026, 10, 28, 23, 59).timeInMillis) }
        assertEquals(29, nd.get(Calendar.DAY_OF_MONTH)); assertEquals(0, nd.get(Calendar.HOUR_OF_DAY)); assertEquals(0, nd.get(Calendar.MINUTE))
    }
}
