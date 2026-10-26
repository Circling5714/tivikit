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
