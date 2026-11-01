package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tiện ích có bản mới (1/11/2026): so bản trên tivi với bản đi kèm, đọc kết quả lệnh trên tivi. */
class TienIchTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    @Test fun canCapNhat_khacShaVaKhongHaBan() {
        // Tele Remote 1.0 (1) trên tivi, đi kèm 1.0.1 (2).
        assertTrue(TienIch.canCapNhat(TienIch.Ban(1, "1.0", a), TienIch.Ban(2, "1.0.1", b)))
        // Dựng lại cùng số bản (Home sạch 3.0, versionCode 9): vẫn cập nhật.
        assertTrue(TienIch.canCapNhat(TienIch.Ban(9, "3.0", a), TienIch.Ban(9, "3.0", b)))
        // Đúng bản đi kèm, tivi chưa có, bản thử mới hơn trên tivi, không đọc ra số bản đi kèm: thôi.
        assertFalse(TienIch.canCapNhat(TienIch.Ban(2, "1.0.1", b), TienIch.Ban(2, "1.0.1", b)))
        assertFalse(TienIch.canCapNhat(null, TienIch.Ban(2, "1.0.1", b)))
        assertFalse(TienIch.canCapNhat(TienIch.Ban(3, "1.0.2", a), TienIch.Ban(2, "1.0.1", b)))
        assertFalse(TienIch.canCapNhat(TienIch.Ban(1, "1.0", a), TienIch.Ban(0, "", b)))
    }

    @Test fun docKetQua_dongCuaLenh() {
        val out = """
            TI|vn.tivikit.teleremote|$a|    versionCode=1 minSdk=26 targetSdk=35     versionName=1.0
            TI|com.gathin.tvhome|$b|    versionCode=9 minSdk=26 targetSdk=34     versionName=3.0
            TI|vn.tivikit.khoa||    versionCode=10 minSdk=26 targetSdk=35     versionName=1.8.0
            TI|vn.tivikit.go|$a|
            rác khác
        """.trimIndent()
        val m = TienIch.docKetQua(out)
        assertEquals(setOf("vn.tivikit.teleremote", "com.gathin.tvhome"), m.keys)
        assertEquals(TienIch.Ban(1, "1.0", a), m["vn.tivikit.teleremote"])
        assertEquals(TienIch.Ban(9, "3.0", b), m["com.gathin.tvhome"])
    }

    @Test fun lenhDoc_chiGoiHopLe() {
        val l = TienIch.lenhDoc(listOf("vn.tivikit.khoa", "x; rm -rf /", "vn.tivikit.go"))
        assertTrue(l.startsWith("for p in vn.tivikit.khoa vn.tivikit.go; do "))
        assertFalse(l.contains("rm -rf"))
        assertEquals("true", TienIch.lenhDoc(emptyList()))
    }

    @Test fun ds_launcherSauCung() {
        val ds = TienIch.ds("com.gathin.tvhome", "tvhome.apk")
        assertEquals(listOf(TienIch.Loai.TKL, TienIch.Loai.HOME), ds.takeLast(2).map { it.loai })
        assertEquals(ds.size, ds.map { it.goi }.toSet().size)
        assertEquals(KenhNhanh.SO_O, ds.count { it.loai == TienIch.Loai.KENH })
        assertEquals("kenh6.apk", ds.single { it.goi == KenhNhanh.goiCua(6) }.apk)
        assertEquals("com.ts.launcher", TienIch.ds("com.ts.launcher", "tvhome-xiaomi.apk").last().goi)
    }

    @Test fun moTa() {
        val m = TienIch.Muc(TienIch.Loai.TELE_REMOTE, "vn.tivikit.teleremote", "teleremote.apk", "Tele Remote")
        assertEquals("Tele Remote 1.0 → 1.0.1", TienIch.moTa(TienIch.CanCapNhat(m, TienIch.Ban(1, "1.0", a), TienIch.Ban(2, "1.0.1", b))))
        assertTrue(TienIch.moTa(TienIch.CanCapNhat(m, TienIch.Ban(2, "1.0.1", a), TienIch.Ban(2, "1.0.1", b))).startsWith("Tele Remote 1.0.1 ("))
    }
}
