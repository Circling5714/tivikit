package vn.tivikit.teleremote

import org.junit.Assert.assertEquals
import org.junit.Test

class AnhTrTest {
    @Test
    fun catGiua169() {
        // Ảnh dọc 3000 x 4000 (điện thoại): giữ cả chiều rộng, cắt giữa theo chiều cao.
        assertEquals(listOf(0, 1156, 3000, 1687), AnhTr.khungCat(3000, 4000, 1920, 1080))
        // Ảnh rất ngang 4000 x 1000: giữ cả chiều cao, cắt giữa theo chiều rộng.
        assertEquals(listOf(1111, 0, 1777, 1000), AnhTr.khungCat(4000, 1000, 1920, 1080))
        // Đúng 16:9 thì giữ nguyên.
        assertEquals(listOf(0, 0, 1920, 1080), AnhTr.khungCat(1920, 1080, 1920, 1080))
    }
}
