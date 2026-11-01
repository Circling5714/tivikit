package vn.tivikit.adb

import org.junit.Assert.assertTrue
import org.junit.Test
import vn.tivikit.dulieu.MapVoice

/** Bật lại riêng một dịch vụ trợ năng sau khi cài đè (ThaoTacTv.lenhBatLaiTroNang, 1/11/2026). */
class TroNangTest {
    @Test fun lenhBatLai_asciiVaChiDungDichVuDo() {
        val l = ThaoTacTv.lenhBatLaiTroNang(MapVoice.DICH_VU)
        assertTrue(l.all { it.code < 0x80 })
        assertTrue(l.contains("case \"\$a\" in *${MapVoice.DICH_VU}*)"))
        assertTrue(l.contains("s#${MapVoice.DICH_VU}##"))
        // Danh sách cũ ghi lại nguyên vẹn sau 1 giây.
        assertTrue(l.contains("sleep 1; settings put secure enabled_accessibility_services \"\$a\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun lenhBatLai_tuChoiChuLa() { ThaoTacTv.lenhBatLaiTroNang("x;reboot/y") }
}
