package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tên app phụ bản thử cũ (com.icm.cleantv.*) mà Khôi phục và kịch bản khôi phục gỡ nếu còn sót. */
class GoiCuTest {
    @Test fun tenGoi() {
        assertEquals("com.icm.cleantv.kenh", GoiCu.kenh(1))
        assertEquals("com.icm.cleantv.kenh4", GoiCu.kenh(4))
        assertEquals(KenhNhanh.SO_O + 2, GoiCu.APP_PHU.size)
        assertTrue(GoiCu.KHOA in GoiCu.APP_PHU && GoiCu.HEN_GIO in GoiCu.APP_PHU)
    }
}
