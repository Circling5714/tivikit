package vn.tivikit.dulieu

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kênh FPT Play (31/10/2026): danh sách đóng sẵn assets/kenh-fpt.json, tên ô, link kênh. */
class KenhFptTest {
    private val ds = KenhFpt.tach(File("src/main/assets/${KenhFpt.ASSET}").readText())

    @Test fun danhSachDongSan() {
        assertEquals(93, ds.size)
        assertEquals(ds.size, ds.map { it.ma }.toSet().size)
        assertTrue(ds.all { Regex("[a-z0-9-]{2,40}").matches(it.ma) && it.ten.isNotBlank() && it.ten == it.ten.trim() })
        assertEquals(setOf("Kênh cơ bản", "Kênh địa phương", "Kênh quốc tế"), ds.map { it.nhom }.toSet())
        assertTrue(ds.all { it.anh.startsWith("https://images.fptplay53.net/") })
        // Kênh hay xem đều có trong danh sách, đúng thứ tự.
        assertEquals(KenhFpt.HAY_XEM, KenhFpt.hayXem(ds).map { it.ma })
        assertEquals("VTV1 HD", ds.first { it.ma == "vtv1-hd" }.ten)
        // Kênh cần gói (nhãn premium của FPT Play): VTV3 hỏi mua gói, VTV1 phát (thử trên Skyworth đã đăng nhập).
        assertEquals(36, ds.count { it.canGoi })
        assertEquals(listOf(false, true, false, false), listOf("vtv1-hd", "vtv3-hd", "hanoitv-1", "antv").map { m -> ds.first { it.ma == m }.canGoi })
    }

    @Test fun tenO_vaLink() {
        assertEquals("VTV1", KenhFpt.tenNgan("VTV1 HD"))
        assertEquals("Vĩnh Long 1", KenhFpt.tenNgan(" Vĩnh Long 1 HD"))
        assertEquals("HTV Thể Thao", KenhFpt.tenNgan("HTV Thể Thao"))
        assertEquals("https://fptplay.vn/xem-truyen-hinh/vtv3-hd", KenhFpt.link("vtv3-hd"))
    }

    @Test fun tachBoMucHong() {
        val d = KenhFpt.tach("""{"kenh":[{"ma":"vtv1-hd","ten":"VTV1 HD"},{"ma":"vtv1 hd","ten":"X"},{"ma":"a","ten":" "},
            {"ma":"vtv1-hd","ten":"Trùng"},{"ma":"b","ten":"B","anh":"http://khong-an-toan"}]}""")
        assertEquals(listOf("vtv1-hd", "b"), d.map { it.ma })
        assertEquals("", d[1].anh)
        assertEquals(emptyList<KenhFpt.Kenh>(), KenhFpt.tach("hỏng"))
    }
}
