package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Gợi ý mẫu gần giống khi không khớp đúng (23/10/2026: Sony BRAVIA_VU3_4K, danh sách có VU31). */
class MauGanGiongTest {
    private val du = DuLieu.docJson(File("src/main/assets/du-lieu.json").readText())
    private fun tv(brand: String, model: String, device: String) =
        ThongTinTv(brand, brand, model, device, device, device, "14", 34, "", "1")

    @Test fun sonyVu3_goiYVu31() {
        val t = tv("Sony", "BRAVIA VU3", "BRAVIA_VU3_4K")
        assertTrue(du.khopMau(t).isEmpty())
        assertEquals("sony-bravia-vu31", du.ganGiong(t).firstOrNull()?.id)
    }

    @Test fun khacHang_khongGoiY() = assertTrue(du.ganGiong(tv("Acme", "VU3", "VU3_4K")).isEmpty())

    @Test fun boQuaMauDaKhop() =
        assertTrue(du.ganGiong(tv("Sony", "BRAVIA VU3", "BRAVIA_VU3_4K"), setOf("sony-bravia-vu31")).none { it.id == "sony-bravia-vu31" })
}
