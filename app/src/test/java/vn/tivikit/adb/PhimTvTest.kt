package vn.tivikit.adb

import org.junit.Assert.assertEquals
import org.junit.Test

/** Lệnh `input` cho remote và ô gõ chữ (25/10/2026). */
class PhimTvTest {
    @Test fun boDau_tiengViet() {
        assertEquals("Phim hanh dong Da Nang", PhimTv.boDau("Phim hành động Đà Nẵng"))
        assertEquals("Tieng Viet co dau: aaeeiooouuy", PhimTv.boDau("Tiếng Việt có dấu: ăâêếịồơởưữỹ"))
        assertEquals("matkhau123!@#", PhimTv.boDau("matkhau123!@#"))
        assertEquals("ab", PhimTv.boDau("a😀b"))
    }

    @Test fun lenhChu_khoangTrangVaNhay() {
        assertEquals(listOf("input text 'it'\\''s%sa%stest'"), PhimTv.lenhChu("it's a test"))
        assertEquals(listOf("input text 'pass\$word;rm%s-rf'"), PhimTv.lenhChu("pass\$word;rm -rf"))
        assertEquals(2, PhimTv.lenhChu("x".repeat(250)).size)
    }

    @Test fun lenhPhim() {
        assertEquals("input keyevent 19 19 22", PhimTv.lenhPhim(listOf(19, 19, 22)))
        assertEquals("input keyevent --longpress 23", PhimTv.lenhPhim(listOf(23), giu = true))
    }
}
