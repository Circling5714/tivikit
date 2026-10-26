package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CH Play và tài khoản Google: đọc kết quả lệnh, nhận xét. Mẫu lấy từ box Tanix TX3 Mini (Android 9, 26/10/2026). */
class ChPlayTest {
    private val gio = 1_793_000_000L

    private fun ra(coChPlay: String = "co", tat: String = "", taiKhoan: String = "1", loi: String = "",
                   bayGio: String = "10-26 11:05:04", giay: Long = gio, tags: String = "release-keys") =
        "$coChPlay\n@@\n$tat\n@@\n$taiKhoan\n@@\n$loi\n@@\n$bayGio\n$giay\n@@\n$tags\n"

    @Test fun docKetQuaTx3() {
        val out = ra(tat = "package:com.android.backupconfirm\npackage:com.google.android.tvlauncher\n",
            loi = "10-26 10:59:04.553 E/AuthPII ( 3791): [RequestTokenManager] getToken() -> BAD_AUTHENTICATION. App: com.android.vending",
            giay = gio + 12, tags = "test-keys")
        assertEquals(ChPlay.TrangThai(true, emptyList(), 1, 360, 12, true), ChPlay.tuKetQua(out, gio))
        assertNull(ChPlay.tuKetQua("co\n@@\n", gio))
    }

    @Test fun goiTatChiLayGoiCanChoChPlay() {
        assertEquals(listOf("com.google.android.gms"),
            ChPlay.tuGoiTat("package:com.android.printspooler\r\npackage:com.google.android.gms\npackage:com.google.android.gmsx\n"))
    }

    @Test fun giayTuLoiCuoi() {
        assertEquals(360L, ChPlay.giayTuLoiCuoi("10-26 10:59:04.553 E/AuthPII: x", "10-26 11:05:04"))
        assertEquals(120L, ChPlay.giayTuLoiCuoi("12-31 23:59:00.000 E/x", "01-01 00:01:00"))
        assertEquals(86_400L + 60, ChPlay.giayTuLoiCuoi("02-28 23:00:00.000", "02-29 23:01:00"))
        assertNull(ChPlay.giayTuLoiCuoi("--------- beginning of main", "10-26 11:05:04"))
    }

    @Test fun loiDangNhapGanDay_tucCanDangNhapLai_kemRomTestKeys() {
        val tt = ChPlay.TrangThai(true, emptyList(), 1, 360, 3, true)
        assertEquals(listOf(ChPlay.VanDe.CAN_DANG_NHAP_LAI, ChPlay.VanDe.ROM_CHUA_CHUNG_NHAN), ChPlay.danhGia(tt))
        // ROM release-keys: không nhắc chứng nhận.
        assertEquals(listOf(ChPlay.VanDe.CAN_DANG_NHAP_LAI), ChPlay.danhGia(tt.copy(testKeys = false)))
    }

    @Test fun loiDangNhapCu_hoacKhongCo_thiOn() {
        assertTrue(ChPlay.danhGia(ChPlay.TrangThai(true, emptyList(), 1, 7_200L, 0, true)).isEmpty())
        assertTrue(ChPlay.danhGia(ChPlay.TrangThai(true, emptyList(), 2, null, null, false)).isEmpty())
    }

    @Test fun khongCoChPlay_chiBaoMotDong() {
        assertEquals(listOf(ChPlay.VanDe.KHONG_CO_CH_PLAY), ChPlay.danhGia(ChPlay.TrangThai(false, emptyList(), 0, 60, 900, true)))
    }

    @Test fun goiTat_lechGio_chuaCoTaiKhoan() {
        val tt = ChPlay.TrangThai(true, listOf("com.google.android.gsf"), 0, null, -600, false)
        assertEquals(listOf(ChPlay.VanDe.GOI_GOOGLE_TAT, ChPlay.VanDe.LECH_GIO, ChPlay.VanDe.CHUA_CO_TAI_KHOAN), ChPlay.danhGia(tt))
        // Lệch dưới 5 phút: bỏ qua.
        assertEquals(listOf(ChPlay.VanDe.CHUA_CO_TAI_KHOAN), ChPlay.danhGia(tt.copy(goiTat = emptyList(), lechGio = 240)))
    }
}
