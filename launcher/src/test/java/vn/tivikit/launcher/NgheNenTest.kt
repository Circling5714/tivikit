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
    private val chuaLen: (String, Long) -> Boolean = { _, _ -> false }

    private fun p(goi: String, tt: Int) = NgheNen.Phien(goi, tt)

    @Test
    fun appKhongPhatNenThiKhongHien() {
        val n = NgheNen()
        n.homeTu = 0L
        // TV360 về Home: phiên còn đó, tạm dừng, hay vẫn báo đang phát mà tivi im tiếng.
        assertNull(n.chon(listOf(p("tv360", tamDung)), false, 60_000L, chuaLen))
        assertNull(n.chon(listOf(p("tv360", phat)), false, 60_000L, chuaLen))
        assertNull(n.chon(listOf(p("tv360", dangTai), p("fpt", dung)), false, 60_000L, chuaLen))
        assertNull(n.chon(emptyList(), true, 60_000L, chuaLen))
        // Có tiếng nhưng không phiên nào báo đang phát (app phát không có phiên): không có gì để hiện.
        assertNull(n.chon(listOf(p("tv360", tamDung)), true, 60_000L, chuaLen))
    }

    @Test
    fun vuaVeHomeAppConTiengThiChuaTinh() {
        val n = NgheNen()
        n.homeTu = 10_000L
        // Vừa bấm Home, TV360 chưa kịp dừng (còn tiếng): chưa tính, và không ghi nhận.
        assertNull(n.chon(listOf(p("tv360", phat)), true, 10_400L, chuaLen))
        assertNull(n.chon(listOf(p("tv360", phat)), true, 12_000L, chuaLen))
        // Rồi nó tự dừng: không bao giờ hiện.
        assertNull(n.chon(listOf(p("tv360", tamDung)), false, 13_000L, chuaLen))
        assertNull(n.chon(listOf(p("tv360", tamDung)), false, 20_000L, chuaLen))
        assertEquals(emptyList<String>(), n.daNghe())
    }

    @Test
    fun appPhatNenThiHienRoiGiuKhiTamDung() {
        val n = NgheNen()
        n.homeTu = 0L
        // SmartTube vẫn phát sau 2,5 giây trên màn Home: hiện, có sóng nhạc. Phiên cũ của TV360 đứng trước cũng không lẫn.
        assertEquals(NgheNen.Chon(1, true), n.chon(listOf(p("tv360", tamDung), p("smarttube", phat)), true, 3_000L, chuaLen))
        assertEquals(listOf("smarttube"), n.daNghe())
        // Giữ OK tạm dừng: thẻ còn, dấu tạm dừng (để phát tiếp).
        assertEquals(NgheNen.Chon(1, false), n.chon(listOf(p("tv360", tamDung), p("smarttube", tamDung)), false, 5_000L, chuaLen))
        // Phát tiếp, tiếng chưa kịp lên: vẫn sóng nhạc.
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("smarttube", phat)), false, 6_000L, chuaLen))
        // Tạm dừng quá 30 phút: thôi hiện.
        assertNull(n.chon(listOf(p("smarttube", tamDung)), false, 3_000L + NgheNen.GIU + 1, chuaLen))
    }

    @Test
    fun imTiengLauThiThoiSongNhac() {
        val n = NgheNen()
        n.homeTu = 0L
        n.chon(listOf(p("smarttube", phat)), true, 3_000L, chuaLen)
        // Đang tải bài sau: thẻ còn, không vẽ sóng nhạc.
        assertEquals(NgheNen.Chon(0, false), n.chon(listOf(p("smarttube", dangTai)), false, 5_000L, chuaLen))
        // Báo đang phát mà im tiếng quá 10 giây: dấu tạm dừng.
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("smarttube", phat)), false, 3_000L + NgheNen.GIAN_DOAN, chuaLen))
        assertEquals(NgheNen.Chon(0, false), n.chon(listOf(p("smarttube", phat)), false, 3_001L + NgheNen.GIAN_DOAN, chuaLen))
    }

    @Test
    fun moAppLenManHinhThiThoi() {
        val n = NgheNen()
        n.homeTu = 0L
        n.chon(listOf(p("smarttube", phat)), true, 3_000L, chuaLen)
        n.homeTu = null
        // Người xem mở SmartTube lên, tạm dừng trong app rồi về Home: không còn thẻ. Tra thống kê một lần thôi.
        var tra = 0
        val daLen: (String, Long) -> Boolean = { goi, tu -> tra++; goi == "smarttube" && tu == 3_000L }
        n.homeTu = 90_000L
        assertNull(n.chon(listOf(p("smarttube", tamDung)), false, 90_100L, daLen))
        assertNull(n.chon(listOf(p("smarttube", tamDung)), false, 91_000L, daLen))
        assertEquals(1, tra)
        // Lần sau bấm Home lúc nó đang phát (SmartTube bật phát nền): hiện lại sau 2,5 giây như lần đầu.
        n.homeTu = null
        n.homeTu = 120_000L
        assertNull(n.chon(listOf(p("smarttube", phat)), true, 121_000L, daLen))
        assertEquals(NgheNen.Chon(0, true), n.chon(listOf(p("smarttube", phat)), true, 122_500L, daLen))
        // Rồi tạm dừng: thẻ còn (lần nghe nền mới, chưa mở app lên).
        assertEquals(NgheNen.Chon(0, false), n.chon(listOf(p("smarttube", tamDung)), false, 124_000L, daLen))
    }

    @Test
    fun quayVeHomeThiHienNgay() {
        val n = NgheNen()
        n.homeTu = 0L
        n.chon(listOf(p("smarttube", phat)), true, 3_000L, chuaLen)
        // Sang app khác (SmartTube vẫn phát nền), rồi về Home: thẻ hiện ngay, không chờ.
        n.homeTu = null
        n.homeTu = 60_000L
        assertEquals(NgheNen.Chon(1, true), n.chon(listOf(p("tv360", phat), p("smarttube", phat)), true, 60_050L, chuaLen))
    }

    @Test
    fun homeDangAnThiKhongGhiNhan() {
        val n = NgheNen()
        // Lệnh doc gọi lúc app khác đang mở trên màn hình (Home ẩn): app đang phát có thể đang ở trước màn hình.
        assertNull(n.chon(listOf(p("fpt", phat)), true, 100_000L, chuaLen))
        assertEquals(emptyList<String>(), n.daNghe())
    }

    @Test
    fun nhieuPhienCungBaoPhatThiUuTienAppDaBiet() {
        val n = NgheNen()
        n.homeTu = 0L
        n.chon(listOf(p("smarttube", phat)), true, 3_000L, chuaLen)
        // VTVgo để lại phiên "đang phát" cũ, đứng trước: vẫn là SmartTube, VTVgo không bị ghi nhận.
        assertEquals(NgheNen.Chon(1, true), n.chon(listOf(p("vtvgo", phat), p("smarttube", phat)), true, 8_000L, chuaLen))
        assertEquals(listOf("smarttube"), n.daNghe())
    }
}
