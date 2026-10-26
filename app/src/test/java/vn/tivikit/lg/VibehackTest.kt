package vn.tivikit.lg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nhận ra tivi LG đã root và dựng lệnh cài LG TV Vibehack (bản ghim). */
class VibehackTest {
    @Test fun nhanRaRoot() {
        val k = Vibehack.docKiemTra("UID=0\nHB=1\nIH=1\n")
        assertTrue(k.laRoot); assertTrue(k.coHomebrew); assertTrue(k.coInputHook); assertFalse(k.coVibehack)
        // Chế độ nhà phát triển (prisoner) hay root giả: không phải root.
        assertFalse(Vibehack.docKiemTra("UID=5000\n").laRoot)
        assertTrue(Vibehack.laSsh("SSH-2.0-dropbear_2022.83")); assertFalse(Vibehack.laSsh(null)); assertFalse(Vibehack.laSsh("HTTP/1.1 400"))
    }

    @Test fun lenhCai_ghimBanVaTuyChon() {
        val l = Vibehack.lenhCai(Vibehack.TuyChon(tiengViet = true, dongHo24 = true, cot = 7, thanhPho = "Đà Lạt", nutHome = false, coAnhNen = true))
        // Ghim đúng commit và bản phát hành, không chạy thẳng nhánh main / bản mới nhất.
        assertTrue(l.contains("/39f0174941516215e76c1070d49d51f4b598f692/install.sh"))
        assertTrue(l.contains("releases/download/${Vibehack.BAN}")); assertFalse(l.contains("| sh"))
        assertTrue(l.contains("'--lang' 'vi'")); assertTrue(l.contains("'--clock24' 'on'")); assertTrue(l.contains("'--columns' '7'"))
        assertTrue(l.contains("'--city' 'Da Lat'")); assertTrue(l.contains("'--wallpaper' '${Vibehack.NEN_TAM}'")); assertTrue(l.contains("'--no-home-button'"))
        assertTrue(l.endsWith("} 2>&1"))
        // Không thời tiết, bỏ ảnh nền.
        val k = Vibehack.lenhCai(Vibehack.TuyChon(thanhPho = "", boAnhNen = true))
        assertTrue(k.contains("'--no-weather'")); assertTrue(k.contains("'--no-wallpaper'")); assertFalse(k.contains("--no-home-button"))
        assertTrue(Vibehack.lenhGo().contains("'--uninstall'"))
    }

    @Test fun trichVaBoMau() {
        assertEquals("'Ho Chi Minh City'", Vibehack.trich("Ho Chi Minh City"))
        assertEquals("'it'\\''s'", Vibehack.trich("it's"))
        // Tên thành phố có nháy / lệnh: chỉ là chữ trong nháy đơn.
        assertTrue(Vibehack.lenhCai(Vibehack.TuyChon(thanhPho = "x'; reboot; '")).contains("'x'\\''; reboot; '\\'''"))
        assertEquals("==> Downloading scripts", Vibehack.boMau("\u001B[1;36m==>\u001B[0m Downloading scripts"))
    }
}
