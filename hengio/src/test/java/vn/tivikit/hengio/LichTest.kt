package vn.tivikit.hengio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Hẹn giờ (25/10/2026): lần chạy kế tiếp theo giờ Việt Nam, báo trước khi tắt, hoãn / thử một lần. */
class LichTest {
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun luc(y: Int, m: Int, d: Int, h: Int, p: Int, s: Int = 0) = ZonedDateTime.of(y, m, d, h, p, s, 0, vn).toInstant().toEpochMilli()
    private val moiNgay = (1..7).toSet()

    // 25/10/2026 là Chủ Nhật.
    @Test fun batMoiNgay_homNayChuaQua() {
        val bat = Viec(6, 30, moiNgay, Viec.BAT)
        val k = Lich.keTiep(listOf(bat), emptyList(), luc(2026, 10, 25, 6, 0), vn)!!
        assertEquals(luc(2026, 10, 25, 6, 30), k.luc)
        assertEquals(listOf(bat), k.viec)
    }

    @Test fun daQuaGio_sangNgayMai() {
        val k = Lich.keTiep(listOf(Viec(6, 30, moiNgay, Viec.BAT)), emptyList(), luc(2026, 10, 25, 6, 30), vn)!!
        assertEquals(luc(2026, 10, 26, 6, 30), k.luc)
    }

    @Test fun tat_baoTruocMotPhut() {
        val k = Lich.keTiep(listOf(Viec(23, 0, moiNgay, Viec.TAT)), emptyList(), luc(2026, 10, 25, 20, 0), vn)!!
        assertEquals(luc(2026, 10, 25, 22, 59), k.luc)
    }

    @Test fun chiThuHaiDenThuSau() {
        // Chủ Nhật 25/10 → lần kế là Thứ Hai 26/10; Thứ Sáu 30/10 sau giờ → Thứ Hai 2/11.
        val v = Viec(18, 58, (1..5).toSet(), Viec.MO, "vn.tivikit.kenh")
        assertEquals(luc(2026, 10, 26, 18, 58), Lich.keTiep(listOf(v), emptyList(), luc(2026, 10, 25, 12, 0), vn)!!.luc)
        assertEquals(luc(2026, 11, 2, 18, 58), Lich.keTiep(listOf(v), emptyList(), luc(2026, 10, 30, 19, 0), vn)!!.luc)
    }

    @Test fun cungGio_diChungMotLan_tatBoQuaViecDangTat() {
        val a = Viec(7, 0, moiNgay, Viec.BAT)
        val b = Viec(7, 0, moiNgay, Viec.MO, "org.smarttube.stable")
        val c = Viec(6, 0, moiNgay, Viec.BAT, bat = false)
        val k = Lich.keTiep(listOf(a, b, c), emptyList(), luc(2026, 10, 25, 5, 0), vn)!!
        assertEquals(luc(2026, 10, 25, 7, 0), k.luc)
        assertEquals(listOf(a, b), k.viec)
        assertNull(Lich.keTiep(listOf(c), emptyList(), luc(2026, 10, 25, 5, 0), vn))
    }

    @Test fun motLan_hoanVaThu() {
        val bat = Viec(6, 30, moiNgay, Viec.BAT)
        val thu = Viec(0, 0, moiNgay, Viec.BAT)
        val tu = luc(2026, 10, 25, 5, 0)
        val k = Lich.keTiep(listOf(bat), listOf(tu + 120_000 to thu, tu - 1 to thu), tu, vn)!!
        assertEquals(tu + 120_000, k.luc)
        assertEquals(listOf(thu), k.viec)
    }

    @Test fun baoThucTre_boKhongChayBu() {
        val hen = luc(2026, 10, 28, 8, 39)
        assertFalse(Lich.daLo(hen, hen + 5_000))
        assertFalse(Lich.daLo(hen, hen + 9 * 60_000L))
        assertTrue(Lich.daLo(hen, hen + 11 * 60_000L))
        // Hộp không pin giữ giờ (TX3): bật lên còn giờ 2021, hẹn theo giờ đó; lấy được giờ mạng thì báo thức thành quá hạn.
        assertTrue(Lich.daLo(luc(2021, 1, 21, 8, 39), luc(2026, 10, 28, 8, 44)))
    }

    @Test fun json_khuHoiVaTuChoi() {
        val ds = listOf(Viec(6, 30, setOf(1, 2, 3), Viec.BAT, "vn.tivikit.kenh"), Viec(23, 0, moiNgay, Viec.TAT), Viec(19, 0, setOf(7), Viec.MO, "org.smarttube.stable", false))
        assertEquals(ds, Lich.docJson(Lich.json(ds).toString()))
        assertNull(Lich.docJson("""[{"gio":24,"phut":0,"ngay":[1],"loai":"bat"}]"""))
        assertNull(Lich.docJson("""[{"gio":6,"phut":0,"ngay":[],"loai":"bat"}]"""))
        assertNull(Lich.docJson("""[{"gio":6,"phut":0,"ngay":[1],"loai":"mo"}]"""))
        assertNull(Lich.docJson("""[{"gio":6,"phut":0,"ngay":[1],"loai":"xoa"}]"""))
        assertNull(Lich.docJson("""[{"gio":6,"phut":0,"ngay":[1],"loai":"mo","goi":"x; reboot"}]"""))
        assertTrue(Lich.docJson("[]")!!.isEmpty())
        // Tắt tivi không mang gói theo.
        assertNull(Lich.docJson("""[{"gio":23,"phut":0,"ngay":[1],"loai":"tat","goi":"a.b"}]""")!!.single().goi)
    }

    /** Đặt lại báo thức giữa phút báo trước của lần tắt (tivi khởi động lại 21:59:30, tắt 22:00): báo ngay (28/10/2026). */
    @Test fun tatLoBao_trongPhutBaoTruoc() {
        val tat = Viec(22, 0, moiNgay, Viec.TAT)
        assertEquals(luc(2026, 10, 25, 22, 0), Lich.tatLoBao(listOf(tat), luc(2026, 10, 25, 21, 59, 30), vn))
        assertNull(Lich.tatLoBao(listOf(tat), luc(2026, 10, 25, 21, 58, 59), vn))
        assertNull(Lich.tatLoBao(listOf(tat), luc(2026, 10, 25, 22, 0), vn))
        assertNull(Lich.tatLoBao(listOf(tat.copy(bat = false)), luc(2026, 10, 25, 21, 59, 30), vn))
        // Tắt lúc 00:00 thứ Hai (26/10): phút báo trước là 23:59 Chủ Nhật.
        val nuaDem = Viec(0, 0, setOf(1), Viec.TAT)
        assertEquals(luc(2026, 10, 26, 0, 0), Lich.tatLoBao(listOf(nuaDem), luc(2026, 10, 25, 23, 59, 20), vn))
        // Việc bật không tính.
        assertNull(Lich.tatLoBao(listOf(Viec(22, 0, moiNgay, Viec.BAT)), luc(2026, 10, 25, 21, 59, 30), vn))
    }
}

