package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Góp ý & ủng hộ (26/10/2026): trang báo lỗi GitHub điền sẵn, thông tin chuyển khoản. */
class LienHeTest {
    @Test fun noiDung_coBanAppDienThoaiTiviVaLoi() {
        val s = LienHe.noiDungBaoLoi(true, "1.4.0", "Samsung SM-A515F, Android 13", "TX3 Mini (p281, Android 9)", "Mất kết nối\nvới tivi")
        assertTrue(s.contains("- TiviKit: 1.4.0"))
        assertTrue(s.contains("- Điện thoại: Samsung SM-A515F, Android 13"))
        assertTrue(s.contains("- Tivi: TX3 Mini (p281, Android 9)"))
        assertTrue("lỗi gọn một dòng", s.contains("- App báo lỗi: Mất kết nối với tivi"))
    }

    @Test fun noiDung_khongTiviKhongLoi_tiengAnh() {
        val s = LienHe.noiDungBaoLoi(false, "1.4.0", "Pixel 8, Android 15", null, null)
        assertTrue(s.contains("- Phone: Pixel 8, Android 15"))
        assertFalse(s.contains("- TV:"))
        assertFalse(s.contains("error"))
    }

    @Test fun loiDai_catCon500KyTu() {
        val s = LienHe.noiDungBaoLoi(true, "1", "x", null, "a".repeat(2000))
        assertEquals(500, s.lines().first { it.startsWith("- App báo lỗi: ") }.removePrefix("- App báo lỗi: ").length)
    }

    @Test fun linkBaoLoi_khoTiviKit_dauCachLa20() {
        val l = LienHe.linkBaoLoi("[TiviKit 1.4.0] ", "a b")
        assertTrue(l.startsWith("https://github.com/Circling5714/tivikit/issues/new?title="))
        assertTrue(l.endsWith("&body=a%20b"))
        assertFalse(l.contains("+"))
    }

    @Test fun taiKhoan_chiCoChuSo() = assertTrue(LienHe.SO_TAI_KHOAN.all { it.isDigit() })
}
