package vn.tivikit.hengio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Auto Reboot (2/11/2026): giờ hẹn kế tiếp, chờ tivi tắt 15 phút một lần tối đa 3 giờ, tránh giờ bật tivi / mở app. */
class AutoRebootTest {
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun luc(y: Int, m: Int, d: Int, h: Int, p: Int, s: Int = 0) = ZonedDateTime.of(y, m, d, h, p, s, 0, vn).toInstant().toEpochMilli()
    private val phut = 60_000L

    @Test fun doc_khuHoi_vaTuChoiSai() {
        val c = AutoReboot(true, 4, 0, (1..7).toSet())
        assertEquals(c, AutoReboot.doc(c.json().toString()))
        assertEquals(AutoReboot(false, 3, 30, setOf(6, 7)), AutoReboot.doc("""{"bat":false,"gio":3,"phut":30,"ngay":[6,7]}"""))
        assertNull(AutoReboot.doc("""{"bat":true,"gio":24,"phut":0,"ngay":[1]}"""))
        assertNull(AutoReboot.doc("""{"bat":true,"gio":4,"phut":60,"ngay":[1]}"""))
        assertNull(AutoReboot.doc("""{"bat":true,"gio":4,"phut":0,"ngay":[]}"""))
        assertNull(AutoReboot.doc("""{"bat":true,"gio":4,"phut":0,"ngay":[8]}"""))
        assertNull(AutoReboot.doc("""{"gio":4,"phut":0,"ngay":[1]}"""))
        assertNull(AutoReboot.doc("hỏng"))
        assertNull(AutoReboot.doc(null))
        // Mặc định: tắt, 04:00 mọi ngày (giờ anh chọn).
        assertEquals(AutoReboot(false, 4, 0, (1..7).toSet()), AutoReboot())
    }

    // 2/11/2026 là Thứ Hai.
    @Test fun lanToi_homNayChuaQuaGio_daQuaThiNgayMai() {
        val c = AutoReboot(true, 4, 0, (1..7).toSet())
        assertEquals(luc(2026, 11, 2, 4, 0), AutoReboot.lanToi(c, luc(2026, 11, 2, 1, 0), vn))
        // Đúng giờ hẹn không tính (báo thức đang chạy lần đó), sang ngày mai.
        assertEquals(luc(2026, 11, 3, 4, 0), AutoReboot.lanToi(c, luc(2026, 11, 2, 4, 0), vn))
        assertEquals(luc(2026, 11, 3, 4, 0), AutoReboot.lanToi(c, luc(2026, 11, 2, 21, 0), vn))
        // Lúc chạy báo thức: tính từ ngay trước giờ hẹn thì vẫn là lần đó (Kho.motLan(luc - 1)).
        assertEquals(luc(2026, 11, 2, 4, 0), AutoReboot.lanToi(c, luc(2026, 11, 2, 4, 0) - 1, vn))
    }

    @Test fun lanToi_theoNgayDaChon_vaTat() {
        // Chỉ Thứ Bảy (6): từ Thứ Hai 2/11 là 7/11.
        assertEquals(luc(2026, 11, 7, 3, 30), AutoReboot.lanToi(AutoReboot(true, 3, 30, setOf(6)), luc(2026, 11, 2, 12, 0), vn))
        // Chỉ Thứ Hai, đã qua giờ hôm nay: Thứ Hai tuần sau.
        assertEquals(luc(2026, 11, 9, 4, 0), AutoReboot.lanToi(AutoReboot(true, 4, 0, setOf(1)), luc(2026, 11, 2, 5, 0), vn))
        assertNull(AutoReboot.lanToi(AutoReboot(false, 4, 0, (1..7).toSet()), luc(2026, 11, 2, 1, 0), vn))
    }

    @Test fun quyet_choToiDaBaGio() {
        val dot = luc(2026, 11, 2, 4, 0)
        assertEquals(AutoReboot.Quyet.KHOI_DONG_LAI, AutoReboot.quyet(dot, dot + 2_000, dangCho = true))
        assertEquals(AutoReboot.Quyet.CHO, AutoReboot.quyet(dot, dot + 2_000, dangCho = false))
        // Lần kiểm 06:45: lần sau 07:00 vẫn trong 3 giờ thì chờ tiếp.
        assertEquals(AutoReboot.Quyet.CHO, AutoReboot.quyet(dot, dot + 165 * phut, dangCho = false))
        // 07:00: tivi tắt thì vẫn khởi động lại; còn bật thì bỏ hôm đó.
        assertEquals(AutoReboot.Quyet.KHOI_DONG_LAI, AutoReboot.quyet(dot, dot + 180 * phut, dangCho = true))
        assertEquals(AutoReboot.Quyet.BO_QUA, AutoReboot.quyet(dot, dot + 180 * phut, dangCho = false))
        // Báo thức tới trễ quá 3 giờ (tivi ngủ sâu tới khi bấm nguồn, tắt điện): lỡ, dù tivi đang chờ.
        assertEquals(AutoReboot.Quyet.LO, AutoReboot.quyet(dot, dot + 181 * phut, dangCho = true))
        assertEquals(AutoReboot.Quyet.LO, AutoReboot.quyet(dot, dot + 10 * 3_600_000L, dangCho = false))
    }

    @Test fun quyet_kiemMoi15Phut_du13Lan() {
        // 04:00, 04:15, …, 07:00: tivi bật suốt thì 12 lần chờ rồi lần 13 bỏ.
        val dot = luc(2026, 11, 2, 4, 0)
        var bay = dot
        var lan = 0
        while (AutoReboot.quyet(dot, bay, dangCho = false) == AutoReboot.Quyet.CHO) { lan++; bay += AutoReboot.KIEM_LAI_MS }
        assertEquals(12, lan)
        assertEquals(dot + 180 * phut, bay)
        assertEquals(AutoReboot.Quyet.BO_QUA, AutoReboot.quyet(dot, bay, dangCho = false))
    }

    @Test fun coViecMo_tranhGioBatTiviHayMoApp() {
        val tu = luc(2026, 11, 2, 6, 10)
        val den = tu + AutoReboot.TRANH_VIEC_MS
        val bat = Viec(6, 30, (1..7).toSet(), Viec.BAT, "vn.tivikit.kenh")
        assertTrue(Lich.coViecMo(listOf(bat), emptyList(), tu, den, vn))
        // Lịch đang tắt, việc tắt tivi, hay ngoài khoảng thì không tính.
        assertFalse(Lich.coViecMo(listOf(bat.copy(bat = false)), emptyList(), tu, den, vn))
        assertFalse(Lich.coViecMo(listOf(Viec(6, 30, (1..7).toSet(), Viec.TAT)), emptyList(), tu, den, vn))
        assertFalse(Lich.coViecMo(listOf(bat), emptyList(), luc(2026, 11, 2, 5, 50), luc(2026, 11, 2, 6, 20), vn))
        // Ngày không có trong lịch (chỉ Thứ Ba) thì không.
        assertFalse(Lich.coViecMo(listOf(bat.copy(ngay = setOf(2))), emptyList(), tu, den, vn))
        // Mở app một lần, thử đánh thức (motLan); Auto Reboot thì không tính.
        assertTrue(Lich.coViecMo(emptyList(), listOf(luc(2026, 11, 2, 6, 15) to Viec(0, 0, (1..7).toSet(), Viec.MO, "a.b")), tu, den, vn))
        assertFalse(Lich.coViecMo(emptyList(), listOf(luc(2026, 11, 2, 6, 15) to Viec(0, 0, (1..7).toSet(), Viec.KDL)), tu, den, vn))
        // Qua nửa đêm: 23:50 tới 00:20 có việc bật lúc 00:10 ngày hôm sau.
        val khuya = luc(2026, 11, 2, 23, 50)
        assertTrue(Lich.coViecMo(listOf(Viec(0, 10, (1..7).toSet(), Viec.BAT)), emptyList(), khuya, khuya + AutoReboot.TRANH_VIEC_MS, vn))
    }

    @Test fun keTiep_autoRebootChungBaoThucVoiViecKhac() {
        // Kho.motLan đưa Auto Reboot vào như một việc một lần: trùng giờ với việc tắt (đã trừ phút báo trước) thì đi chung.
        val gio = luc(2026, 11, 2, 4, 0)
        val k = Lich.keTiep(listOf(Viec(4, 1, (1..7).toSet(), Viec.TAT)), listOf(gio to Viec(0, 0, (1..7).toSet(), Viec.KDL)), gio - 1, vn)!!
        assertEquals(gio, k.luc)
        assertEquals(setOf(Viec.TAT, Viec.KDL), k.viec.map { it.loai }.toSet())
    }
}
