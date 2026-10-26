package vn.tivikit.khoa

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import vn.tivikit.khoa.ThongKe.SuKien

/** Gộp sự kiện app lên / rời màn hình thành thời gian từng app, chia theo ngày. */
class ThongKeTest {
    private fun luc(d: Int, h: Int, mi: Int) = Calendar.getInstance().apply { clear(); set(2026, 9, d, h, mi) }.timeInMillis
    private val PHUT = 60_000L

    @Test fun haiApp_vaTatMan() {
        val kq = ThongKe.gom(listOf(
            SuKien(luc(25, 10, 0), "yt", ThongKe.LEN), SuKien(luc(25, 10, 30), "yt", ThongKe.XUONG),
            SuKien(luc(25, 10, 30), "fpt", ThongKe.LEN), SuKien(luc(25, 11, 0), "", ThongKe.TAT),
            SuKien(luc(25, 11, 5), "fpt", ThongKe.XUONG), // rời màn hình sau khi màn đã tắt: không cộng thêm
        ), luc(25, 0, 0), luc(25, 20, 0))
        assertEquals(mapOf("2026-10-25" to mapOf("yt" to 30 * PHUT, "fpt" to 30 * PHUT)), kq)
    }

    @Test fun quaNuaDem_chiaHaiNgay() {
        val kq = ThongKe.gom(listOf(SuKien(luc(24, 23, 30), "yt", ThongKe.LEN), SuKien(luc(25, 0, 45), "yt", ThongKe.XUONG)), luc(24, 0, 0), luc(25, 20, 0))
        assertEquals(30 * PHUT, kq["2026-10-24"]!!["yt"]); assertEquals(45 * PHUT, kq["2026-10-25"]!!["yt"])
    }

    @Test fun catTheoKhoangDoc() {
        // Mở từ trước đầu khoảng (đọc lùi một ngày để thấy sự kiện lên), chưa rời màn hình tới cuối khoảng.
        val kq = ThongKe.gom(listOf(SuKien(luc(25, 8, 0), "yt", ThongKe.LEN), SuKien(luc(25, 9, 30), "yt", ThongKe.XUONG),
            SuKien(luc(25, 10, 0), "fpt", ThongKe.LEN)), luc(25, 9, 0), luc(25, 10, 20))
        assertEquals(mapOf("yt" to 30 * PHUT, "fpt" to 20 * PHUT), kq["2026-10-25"])
    }

    @Test fun appMoiLenTruocKhiAppCuRoi() {
        val kq = ThongKe.gom(listOf(SuKien(luc(25, 10, 0), "a", ThongKe.LEN), SuKien(luc(25, 10, 10), "b", ThongKe.LEN),
            SuKien(luc(25, 10, 10), "a", ThongKe.XUONG), SuKien(luc(25, 10, 40), "b", ThongKe.XUONG)), luc(25, 0, 0), luc(25, 20, 0))
        assertEquals(mapOf("a" to 10 * PHUT, "b" to 30 * PHUT), kq["2026-10-25"])
    }
}
