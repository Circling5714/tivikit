package vn.tivikit.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeleRemoteTest {
    /** Phần --arg '…' của lệnh content call. */
    private fun arg(lenh: String) = lenh.substringAfter("--arg '").substringBeforeLast("'")

    @Test
    fun chuanTenUsername() {
        assertEquals("@caominh", TeleRemote.chuanTen("caominh"))
        assertEquals("@CaoMinh", TeleRemote.chuanTen(" @CaoMinh "))
        assertEquals("@cao_minh", TeleRemote.chuanTen("https://t.me/cao_minh"))
        assertEquals("@cao_minh", TeleRemote.chuanTen("t.me/cao_minh"))
        assertNull(TeleRemote.chuanTen("@ab"))
        assertNull(TeleRemote.chuanTen("cao minh"))
        assertNull(TeleRemote.chuanTen("@x'; reboot"))
    }

    @Test
    fun chuanTokenBot() {
        assertEquals("123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw", TeleRemote.chuanToken(" 123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw\n"))
        assertNull(TeleRemote.chuanToken("123456789:ngan"))
        assertNull(TeleRemote.chuanToken("Use this token to access the HTTP API"))
    }

    @Test
    fun lenhChiAsciiVaKhuHoi() {
        val c = TeleRemote.CauHinh(nguoiDung = listOf(TeleRemote.NguoiDung("@caominh", 111, TeleRemote.VaiTro.CHU, "Cao Minh"),
            TeleRemote.NguoiDung("", 333, TeleRemote.VaiTro.THANH_VIEN, "Ông Bà'")), tiengAnh = false, chupManHinh = true, baoTin = true)
        listOf(TeleRemote.lenhDat(c), TeleRemote.lenhToken("123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"), TeleRemote.lenhXoaCho(333)).forEach { l ->
            assertTrue(l, l.all { it.code < 0x80 })
            assertEquals(l, 2, l.count { it == '\'' })
            assertTrue(l.startsWith("content call --uri content://vn.tivikit.teleremote --method "))
        }
        assertEquals(c, TeleRemote.cauHinh(JSONObject(arg(TeleRemote.lenhDat(c)))))
        assertEquals("content call --uri content://vn.tivikit.teleremote --method nhan-thu", TeleRemote.LENH_NHAN_THU)
    }

    @Test
    fun docTrangThai() {
        val tt = TeleRemote.trangThai(JSONObject("""{"phienBan":"1.0","dichVuChay":true,"adb":"da-noi","tele":"dang-nghe","lanCuoi":5,
            "coToken":true,"bot":"TiviNha_bot","cauHinh":{"bat":true,"nguoiDung":[{"ten":"@caominh","id":111,"vaiTro":"chu","tenHien":"Minh"}]},
            "cho":[{"id":444,"ten":"","tenHien":"Bà","luc":9}],"nhatKy":[{"luc":7,"ai":"@caominh","viec":"kenh|VTV1"}]}"""))
        assertTrue(tt.dichVuChay)
        assertEquals("dang-nghe", tt.tele)
        assertEquals("TiviNha_bot", tt.bot)
        assertEquals(TeleRemote.VaiTro.CHU, tt.cauHinh.nguoiDung.single().vaiTro)
        assertEquals("Bà", tt.cho.single().tenXem)
        assertEquals("ID 5", TeleRemote.XinDung(5, "", "", 0).tenXem)
        assertEquals(TeleRemote.NhatKy(7, "@caominh", "kenh|VTV1"), tt.nhatKy.single())
        assertFalse(TeleRemote.trangThai(JSONObject("{}")).dichVuChay)
    }

    @Test
    fun ketQuaLenh() {
        assertEquals("TiviNha_bot", TeleRemote.trong("Result: Bundle[{bot=TiviNha_bot, ok=1}]", "bot"))
        assertEquals("khong-mang", TeleRemote.trong("Result: Bundle[{ok=1, loi=khong-mang}]", "loi"))
        assertEquals("", TeleRemote.trong("Result: Bundle[{ok=1}]", "loi"))
        assertEquals("2", TeleRemote.trong("Result: Bundle[{so=2, ok=1}]", "so"))
    }
}
