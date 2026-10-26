package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** App chạy ngầm: đọc ps, gộp theo gói, app nên tắt. Số RAM lấy từ box Tanix TX3 Mini (26/10/2026). */
class ChayNgamTest {
    private val ps = """
          RSS NAME
       109136 app.revanced.android.gms
       100776 app.revanced.android.gms:persistent
       222160 com.google.android.gms
       175432 com.google.android.gms.unstable
        81284 com.droidlogic.otaupgrade
        66412 com.droidlogic.mediacenter
       102620 com.google.android.katniss:interactor
       287988 org.smarttube.stable
         3900 android.hardware.dumpstate@1.0-service.droidlogic
            0 [kworker/u8:2]
         4800 adbd
    """.trimIndent()

    private val goi = setOf("app.revanced.android.gms", "com.google.android.gms", "com.droidlogic.otaupgrade", "com.droidlogic.mediacenter",
        "com.google.android.katniss", "org.smarttube.stable", "com.droidlogic")

    @Test fun docPs() {
        val ds = ChayNgam.tuPs(ps)
        assertEquals(11, ds.size)
        assertEquals("app.revanced.android.gms" to 109_136L, ds.first())
        assertTrue(ChayNgam.tuPs("bad pid '-A'\n").isEmpty())
    }

    @Test fun gopTheoGoi_tienTrinhPhuVeGoiChinh() {
        assertEquals(listOf(
            ChayNgam.App("com.google.android.gms", 222_160L + 175_432),
            ChayNgam.App("org.smarttube.stable", 287_988),
            ChayNgam.App("app.revanced.android.gms", 109_136L + 100_776),
            ChayNgam.App("com.google.android.katniss", 102_620),
            ChayNgam.App("com.droidlogic.otaupgrade", 81_284),
            ChayNgam.App("com.droidlogic.mediacenter", 66_412),
        ), ChayNgam.gopTheoGoi(ChayNgam.tuPs(ps), goi))
    }

    @Test fun goiCua() {
        assertEquals("com.google.android.gms", ChayNgam.goiCua("com.google.android.gms.persistent", goi))
        assertEquals("app.revanced.android.gms", ChayNgam.goiCua("app.revanced.android.gms:persistent", goi))
        // Dịch vụ hệ thống không thuộc gói nào, kể cả gói "android" (không có dấu chấm).
        assertNull(ChayNgam.goiCua("android.hardware.audio@2.0-service", goi + "android"))
        assertNull(ChayNgam.goiCua("[kworker/u8:2]", goi))
    }

    @Test fun nenTat() {
        assertEquals(ChayNgam.LyDo.MICROG_THUA, ChayNgam.nenTat("app.revanced.android.gms", goi))
        assertTrue(ChayNgam.LyDo.MICROG_THUA.chac)
        // Còn YouTube ReVanced thì microG đang được dùng.
        assertNull(ChayNgam.nenTat("app.revanced.android.gms", goi + "app.revanced.android.youtube"))
        assertEquals(ChayNgam.LyDo.MICROG_THUA, ChayNgam.nenTat("com.mgoogle.android.gms", goi))
        assertNull(ChayNgam.nenTat("com.mgoogle.android.gms", goi + "com.vanced.android.youtube"))
        assertEquals(ChayNgam.LyDo.CAP_NHAT_ROM, ChayNgam.nenTat("com.droidlogic.otaupgrade", goi))
        assertFalse(ChayNgam.LyDo.CAP_NHAT_ROM.chac)
        assertEquals(ChayNgam.LyDo.DLNA, ChayNgam.nenTat("com.droidlogic.mediacenter", goi))
        assertEquals(ChayNgam.LyDo.TRO_LY_GIONG_NOI, ChayNgam.nenTat("com.google.android.katniss", goi))
        assertNull(ChayNgam.nenTat("org.smarttube.stable", goi))
    }
}
