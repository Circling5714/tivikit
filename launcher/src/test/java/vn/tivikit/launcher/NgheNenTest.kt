package vn.tivikit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Thẻ Đang phát chỉ hiện app đang phát nền (TiviKit Launcher 1.5.1). */
class NgheNenTest {
    // Trạng thái phiên của Android: 1 = dừng, 2 = tạm dừng, 3 = đang phát, 6 = đang tải.
    private val dung = 1
    private val tamDung = 2
    private val phat = 3
    private val dangTai = 6

    /** App đã rời màn hình từ lúc nào theo thống kê sử dụng; null = còn trên màn hình; không có = không có sự kiện nào. */
    private val roi = HashMap<String, NgheNen.Roi?>()
    private val tra: (String) -> NgheNen.Roi? = { goi -> if (goi in roi) roi[goi] else NgheNen.Roi(0L, NgheNen.CHO_DUNG) }

    private fun p(goi: String, tt: Int) = NgheNen.Phien(goi, tt)
    private fun dungLuc(luc: Long) = NgheNen.Roi(luc, NgheNen.CHO_DUNG)
    private fun moi() = NgheNen().apply { homeHien = true }

    @Test
    fun appKhongPhatNenThiKhongHien() {
        val n = moi()
        roi["tv360"] = dungLuc(50_000L)
        // Về Home: phiên còn đó, tạm dừng, hay vẫn báo đang phát mà tivi im tiếng.
        assertNull(n.chon(listOf(p("tv360", tamDung)), false, 60_000L, tra))
        assertNull(n.chon(listOf(p("tv360", phat)), false, 60_000L, tra))
        assertNull(n.chon(listOf(p("tv360", dangTai), p("fpt", dung)), false, 60_000L, tra))
        assertNull(n.chon(emptyList(), true, 60_000L, tra))
        // Có tiếng nhưng không phiên nào báo đang phát (app phát không có phiên): không có gì để hiện.
        assertNull(n.chon(listOf(p("tv360", tamDung)), true, 60_000L, tra))
        assertEquals(emptyList<String>(), n.daNghe())
    }

    @Test
    fun appDungChamThiKhongTinh() {
        // TizenTube trên Skyworth (4/11/2026): bấm Home, launcher hiện, 2 giây sau Android mới dừng hẳn app (onStop) và app
        // mới thôi phát. Trong lúc đó vẫn có tiếng, phiên vẫn báo đang phát.
        val n = moi()
        roi["tizentube"] = null
        assertNull(n.chon(listOf(p("tizentube", phat)), true, 10_000L, tra))
        assertNull(n.chon(listOf(p("tizentube", phat)), true, 12_000L, tra))
        // Đã dừng hẳn (giờ thống kê lệch giờ máy một chút), còn phát thêm chưa tới 2,5 giây kể từ lúc launcher thấy.
        roi["tizentube"] = dungLuc(11_200L)
        assertNull(n.chon(listOf(p("tizentube", phat)), true, 12_500L, tra))
        assertNull(n.chon(listOf(p("tizentube", phat)), true, 14_000L, tra))
        // Rồi thôi phát, bỏ phiên: không bao giờ hiện, không ghi nhận.
        assertNull(n.chon(emptyList(), false, 15_000L, tra))
        assertEquals(emptyList<String>(), n.daNghe())
    }

    @Test
    fun appPhatNenThiHienRoiGiuKhiTamDung() {
        val n = moi()
        roi["smarttube"] = dungLuc(20_000L)
        // SmartTube bật phát nền: vừa dừng hẳn mà vẫn phát; đủ 2,5 giây kể từ lúc launcher thấy nó dừng thì hiện.
        assertNull(n.chon(listOf(p("smarttube", phat)), true, 20_300L, tra))
        assertEquals(NgheNen.Chon(1, true), n.chon(listOf(p("tv360", tamDung), p("smarttube", phat)), true, 22_800L, tra))
        assertEquals(listOf("smarttube"), n.daNghe())
        // Giữ OK tạm dừng: thẻ còn, dấu tạm dừng (để phát tiếp). Phiên cũ của TV360 đứng trước cũng không lẫn.
        assertEquals(NgheNen.Chon(1, false), n.chon(listOf(p("tv360", tamDung), p("smarttube", tamDung)), false, 25_000L, tra))
        // Phát tiếp, tiếng chưa kịp lên: vẫn sóng nhạc.
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("smarttube", phat)), false, 26_000L, tra))
        // Tạm dừng quá 30 phút: thôi hiện.
        assertNull(n.chon(listOf(p("smarttube", tamDung)), false, 22_800L + NgheNen.GIU + 1, tra))
    }

    @Test
    fun dungLauRoiThiHienNgay() {
        val n = moi()
        // Đã dừng hẳn từ lâu (quá 2,5 giây cộng độ lệch giờ) mà còn phát: hiện ngay lần đầu thấy.
        roi["smarttube"] = dungLuc(100_000L)
        val ngay = 100_000L + NgheNen.CHO_DUNG + NgheNen.LECH_GIO
        assertNull(n.chon(listOf(p("smarttube", phat)), true, ngay - 1, tra))
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("smarttube", phat)), true, ngay, tra))
        // Không có sự kiện nào trong 10 phút (ở nền từ lâu): cũng hiện ngay.
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("spotify", phat)), true, 200_000L, tra))
    }

    @Test
    fun moAppLenManHinhThiThoi() {
        val n = moi()
        roi["smarttube"] = dungLuc(1_000L)
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("smarttube", phat)), true, 30_000L, tra))
        // Người xem mở SmartTube lên: không còn thẻ (app đang trên màn hình).
        roi["smarttube"] = null
        assertNull(n.chon(listOf(p("smarttube", tamDung)), false, 40_000L, tra))
        // Tạm dừng trong app rồi về Home: lần dừng mới, không còn thẻ.
        roi["smarttube"] = dungLuc(45_000L)
        assertNull(n.chon(listOf(p("smarttube", tamDung)), false, 46_000L, tra))
        // Lại phát (từ điện thoại) mà vẫn ở nền: chờ như lần đầu rồi hiện, tạm dừng thì thẻ còn.
        assertNull(n.chon(listOf(p("smarttube", phat)), true, 47_000L, tra))
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("smarttube", phat)), true, 49_500L, tra))
        assertEquals(NgheNen.Chon(0, false), n.chon(listOf(p("smarttube", tamDung)), false, 50_000L, tra))
    }

    @Test
    fun suKienDungTroiKhoiKhoangTraVanLaLanCu() {
        val n = moi()
        roi["smarttube"] = dungLuc(1_000L)
        n.chon(listOf(p("smarttube", phat)), true, 30_000L, tra)
        // 10 phút sau sự kiện dừng không còn trong khoảng tra: vẫn là lần ở nền cũ, thẻ tạm dừng còn.
        roi.remove("smarttube")
        assertEquals(NgheNen.Chon(0, false), n.chon(listOf(p("smarttube", tamDung)), false, 700_000L, tra))
    }

    @Test
    fun homeAnThiKhongGhiNhan() {
        val n = NgheNen()
        // Lệnh doc gọi lúc app khác đang mở (Home ẩn): không ghi nhận gì.
        assertNull(n.chon(listOf(p("fpt", phat)), true, 100_000L, tra))
        assertEquals(emptyList<String>(), n.daNghe())
        // App đã ghi nhận (lúc Home hiện) mà đang tạm dừng ở nền thì lệnh doc vẫn thấy.
        n.homeHien = true
        n.chon(listOf(p("smarttube", phat)), true, 110_000L, tra)
        n.homeHien = false
        assertEquals(NgheNen.Chon(0, false), n.chon(listOf(p("smarttube", tamDung)), false, 120_000L, tra))
    }

    @Test
    fun nhieuPhienCungBaoPhatThiUuTienAppDaBiet() {
        val n = moi()
        n.chon(listOf(p("smarttube", phat)), true, 3_000L, tra)
        // VTVgo để lại phiên "đang phát" cũ, đứng trước: vẫn là SmartTube, VTVgo không bị ghi nhận.
        assertEquals(NgheNen.Chon(1, true), n.chon(listOf(p("vtvgo", phat), p("smarttube", phat)), true, 8_000L, tra))
        assertEquals(listOf("smarttube"), n.daNghe())
    }

    @Test
    fun imTiengLauThiThoiSongNhac() {
        val n = moi()
        n.chon(listOf(p("smarttube", phat)), true, 3_000L, tra)
        // Đang tải bài sau: thẻ còn, không vẽ sóng nhạc.
        assertEquals(NgheNen.Chon(0, false), n.chon(listOf(p("smarttube", dangTai)), false, 5_000L, tra))
        // Báo đang phát mà im tiếng quá 10 giây: dấu tạm dừng.
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("smarttube", phat)), false, 3_000L + NgheNen.GIAN_DOAN, tra))
        assertEquals(NgheNen.Chon(0, false), n.chon(listOf(p("smarttube", phat)), false, 3_001L + NgheNen.GIAN_DOAN, tra))
    }

    @Test
    fun android9ChoLauHon() {
        // Android 9 chỉ báo lúc tạm dừng: chờ 8 giây kể từ lúc thấy.
        val n = moi()
        roi["kodi"] = NgheNen.Roi(50_000L, NgheNen.CHO_TAM_DUNG)
        assertNull(n.chon(listOf(p("kodi", phat)), true, 50_500L, tra))
        assertNull(n.chon(listOf(p("kodi", phat)), true, 55_000L, tra))
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("kodi", phat)), true, 58_500L, tra))
    }
}
