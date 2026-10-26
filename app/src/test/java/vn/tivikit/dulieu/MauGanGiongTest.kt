package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gợi ý mẫu gần giống khi không khớp đúng (23/10/2026: Sony BRAVIA_VU3_4K, danh sách có VU31). Dữ liệu dựng sẵn trong
 * test (26/10/2026): du-lieu.json tự cập nhật hằng tuần từ CleanYourTV (nay đã có danh sách riêng cho VU3), nên test
 * thuật toán không dựa vào mẫu nào đang có hay chưa có.
 */
class MauGanGiongTest {
    private val du = DuLieu(listOf(
        MauTv("sony-bravia-vu31", "Sony BRAVIA VU31", "Sony BRAVIA VU31 Google TV (device BRAVIA_VU31_4K), Android TV 12", emptyList()),
        MauTv("sony-kd-55x85j", "Sony KD-55X85J", "Sony KD-55X85J (device BRAVIA_ATV3_4K), Google TV 10", emptyList()),
    ), emptyMap())

    private fun tv(brand: String, model: String, device: String) =
        ThongTinTv(brand, brand, model, device, device, device, "14", 34, "", "1")

    @Test fun sonyVu3_goiYVu31() {
        val t = tv("Sony", "BRAVIA VU3", "BRAVIA_VU3_4K")
        assertTrue(du.khopMau(t).isEmpty())
        assertEquals(listOf("sony-bravia-vu31"), du.ganGiong(t).map { it.id })
    }

    @Test fun khacHang_khongGoiY() = assertTrue(du.ganGiong(tv("Acme", "VU3", "VU3_4K")).isEmpty())

    @Test fun boQuaMauDaKhop() =
        assertTrue(du.ganGiong(tv("Sony", "BRAVIA VU3", "BRAVIA_VU3_4K"), setOf("sony-bravia-vu31")).none { it.id == "sony-bravia-vu31" })
}
