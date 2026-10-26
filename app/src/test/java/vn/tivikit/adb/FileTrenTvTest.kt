package vn.tivikit.adb

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tên file gửi lên tivi (25/10/2026). */
class FileTrenTvTest {
    @Test fun tenAnToan() {
        assertEquals("Ảnh gia đình 2026.jpg", FileTrenTv.tenAnToan("Ảnh gia đình 2026.jpg"))
        assertEquals("a_b_c_d_.mp4", FileTrenTv.tenAnToan("a\"b'c\$d`.mp4"))
        assertEquals("x_y.txt", FileTrenTv.tenAnToan("../x/y.txt").removePrefix("_"))
        assertEquals("file", FileTrenTv.tenAnToan("  "))
    }

    @Test fun tenKhongTrung() {
        assertEquals("a.jpg", FileTrenTv.tenKhongTrung("a.jpg", setOf("b.jpg")))
        assertEquals("a (3).jpg", FileTrenTv.tenKhongTrung("a.jpg", setOf("a.jpg", "a (2).jpg")))
        assertEquals("README (2)", FileTrenTv.tenKhongTrung("README", setOf("README")))
    }
}
