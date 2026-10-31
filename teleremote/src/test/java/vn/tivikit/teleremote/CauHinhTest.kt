package vn.tivikit.teleremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CauHinhTest {
    @Test
    fun jsonKhuHoi() {
        val c = CauHinh(bat = false, nguoiDung = listOf(NguoiDung("@caominh", 111, VaiTro.CHU, "Minh"), NguoiDung("", 333, VaiTro.THANH_VIEN, "Ông")),
            chupManHinh = true, tiengAnh = true)
        assertEquals(c, CauHinh.tuJson(c.json().toString()))
        assertEquals(CauHinh(), CauHinh.tuJson("{}"))
        assertNull(CauHinh.tuJson("hỏng"))
    }

    @Test
    fun boMucHong() {
        val c = CauHinh.tuJson("""{"nguoiDung":[
            {"ten":"caominh","vaiTro":"chu"},
            {"ten":"@ab","vaiTro":"chu"},
            {"ten":"@hop_le","vaiTro":"chu"},
            {"ten":"@HOP_LE","vaiTro":"thanh-vien"},
            {"ten":"","id":0},
            {"ten":"","id":444,"vaiTro":"la"},
            {"ten":"@x'; reboot","vaiTro":"chu"}
        ]}""")!!
        // Thiếu @, quá ngắn, trùng (không phân biệt hoa thường), không username mà cũng không ID, ký tự lạ: bỏ. Vai trò lạ = thành viên.
        assertEquals(listOf(NguoiDung("@hop_le", 0, VaiTro.CHU), NguoiDung("", 444, VaiTro.THANH_VIEN)), c.nguoiDung)
    }

    @Test
    fun toiDaHaiMuoiNguoi() {
        val ds = (1..30).joinToString(",") { """{"ten":"@nguoi_$it","vaiTro":"thanh-vien"}""" }
        assertEquals(CauHinh.NGUOI_TOI_DA, CauHinh.tuJson("""{"nguoiDung":[$ds]}""")!!.nguoiDung.size)
    }

    @Test
    fun tokenDungDang() {
        assertTrue(Telegram.TOKEN.matches("123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"))
        assertFalse(Telegram.TOKEN.matches("123456789:ngan"))
        assertFalse(Telegram.TOKEN.matches("abc:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"))
        assertFalse(Telegram.TOKEN.matches("123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw' ; reboot"))
    }
}
