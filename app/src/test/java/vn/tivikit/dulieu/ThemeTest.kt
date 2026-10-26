package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Giao diện Home sạch (26/10/2026): bỏ ảnh nền VGS (không thuộc MIT), giao diện TiviKit thay chỗ. */
class ThemeTest {
    @Test fun anhCuaMoiGiaoDienDeuCoTrongAssets() {
        THEMES.mapNotNull { it.anhNen ?: it.anhXem }.forEach { assertTrue(it, File("src/main/assets/$it").isFile) }
    }

    @Test fun khongConAnhHayTenVgs() {
        assertFalse(File("src/main/assets/theme/vgs.jpg").exists())
        assertTrue(THEMES.none { it.id == "vgs" || it.anhNen.orEmpty().contains("vgs") || "${it.tenVi} ${it.moTaVi} ${it.moTaEn}".contains("VGS") })
    }

    /** "Apple TV" là nhãn hiệu của Apple: giao diện kiểu đó của CleanTV mang tên Kính mờ. */
    @Test fun khongDungTenAppleTv() {
        assertTrue(THEMES.none { "${it.tenVi} ${it.tenEn} ${it.moTaVi} ${it.moTaEn}".contains("Apple") })
        assertEquals(true, themeTheoId("kinh-mo")?.cauHinh?.get("uniform_tiles"))
    }

    /** config.json ghi id giao diện để lần kết nối sau mở đúng giao diện (CleanTV 2.9.0). */
    @Test fun ghiIdGiaoDienVaoCauHinh() {
        val o = org.json.JSONObject()
        themeTheoId("kinh-mo")!!.ghiVao(o)
        assertEquals("kinh-mo", o.getString("vgs_theme"))
        assertEquals(false, o.getBoolean("wallpaper"))
    }

    @Test fun idVgsCuThanhGiaoDienTiviKit() {
        assertEquals("tivikit", themeTheoId("vgs")?.id)
        assertEquals("aurora", themeTheoId("aurora")?.id)
        assertNull(themeTheoId("khong-co"))
    }

    @Test fun nhanRaAnhVgsCuTheoSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256("abc".toByteArray()))
        assertTrue(Regex("^[0-9a-f]{64}$").matches(SHA256_ANH_VGS_CU))
        assertFalse(laAnhNenVgsCu(File("src/main/assets/theme/tivikit.jpg").readBytes()))
        assertFalse(laAnhNenVgsCu(ByteArray(0)))
    }
}
