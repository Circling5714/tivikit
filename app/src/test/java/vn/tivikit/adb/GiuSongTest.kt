package vn.tivikit.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Giữ app phụ chạy nền trên box RAM thấp (ThaoTacTv.lenhGiuSong). */
class GiuSongTest {
    @Test fun lenh_baViecMoiGoi_imLangKhiLoi() {
        val l = ThaoTacTv.lenhGiuSong(listOf("com.gathin.tvhome", "x;reboot"))
        assertTrue(l.contains("dumpsys deviceidle whitelist +com.gathin.tvhome >/dev/null 2>&1;"))
        assertTrue(l.contains("am set-standby-bucket com.gathin.tvhome active >/dev/null 2>&1;"))
        assertTrue(l.contains("cmd appops set com.gathin.tvhome RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1;"))
        // Tên gói lạ không lọt vào shell; lệnh luôn kết thúc thành công.
        assertFalse(l.contains("reboot"))
        assertTrue(l.endsWith("true"))
        assertEquals("true", ThaoTacTv.lenhGiuSong(emptyList()))
    }

    @Test fun giuSong_appPhuChayNen() {
        val g = ThaoTacTv.GIU_SONG
        assertTrue(g.containsAll(ThaoTacTv.HOME_SACH))
        assertTrue(g.containsAll(listOf(ThaoTacTv.PKG_KHOA, vn.tivikit.dulieu.HenGio.GOI, vn.tivikit.dulieu.TiviKitLauncher.GOI)))
        // Bộ gõ và ô Kênh nhanh không chạy nền: không cần.
        assertFalse(g.contains(vn.tivikit.dulieu.GoChu.GOI))
    }
}
