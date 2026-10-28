package vn.tivikit.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Lệnh có chữ ngoài ASCII đi qua libadb dạng base64 (28/10/2026). */
class LenhAsciiTest {
    private fun giai(lenh: String) = String(Base64.getDecoder().decode(lenh.substringAfter("echo ").substringBefore(" |")), Charsets.UTF_8)

    @Test fun ascii_giuNguyen() {
        assertEquals("shell:echo ok", LenhAscii.doi("shell:echo ok"))
        assertEquals("exec:cmd package install -r -S 10", LenhAscii.doi("exec:cmd package install -r -S 10"))
    }

    @Test fun coDau_guiBase64() {
        val goc = "content call --uri content://vn.tivikit.kenh --method dat --arg '{\"ten\":\"Hà Nội 1\"}'"
        val r = LenhAscii.doi("shell:$goc")
        assertTrue(r.startsWith("shell:eval \"\$(echo "))
        assertTrue(r.all { it.code < 0x80 })
        assertEquals(goc, giai(r))
        val ghi = "sh -c 'cat > \"/sdcard/Download/Ảnh cưới.jpg\"'"
        val e = LenhAscii.doi("exec:$ghi")
        assertTrue(e.startsWith("exec:eval ") && e.all { it.code < 0x80 })
        assertEquals(ghi, giai(e))
    }

    @Test fun khongPhaiLenhShell_giuNguyen() {
        assertEquals("localabstract:scrcpy_ừ", LenhAscii.doi("localabstract:scrcpy_ừ"))
    }
}
