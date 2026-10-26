package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Lưu / đọc / xoá bộ cài đặt mẫu (25/10/2026). */
class BoCaiTest {
    @get:Rule val tam = TemporaryFolder()

    @Test fun luuDocXoa() {
        val kho = KhoBoCai(tam.root)
        val tm = kho.thuMucBo("b1")
        File(tm, "vlc.apk").writeText("x"); File(tm, "bo.apk").writeText("y")
        val b = BoCai("b1", "Bộ cơ bản", listOf(MucBoCai("vlc.apk", "VLC", "org.videolan.vlc", "3.6.0", 13060001, 1),
            MucBoCai.tuGoiY(GoiY.theoId("kodi")!!)), dnsLoai = DnsRieng.Loai.NEXTDNS, dnsGiaTri = "abc123")
        kho.luu(b)
        assertEquals(listOf(b), KhoBoCai(tam.root).tatCa())
        // File không còn app nào dùng thì xoá.
        assertTrue(File(tm, "vlc.apk").exists()); assertFalse(File(tm, "bo.apk").exists())
        kho.luu(b.copy(ten = "Đổi tên"))
        assertEquals(1, kho.tatCa().size)
        kho.xoa("b1")
        assertTrue(kho.tatCa().isEmpty()); assertFalse(tm.exists())
    }

    /** Bộ lưu từ bản trước (chỉ có cờ "dns") vẫn đọc được: dns = true là AdGuard. */
    @Test fun boCuChiCoCoDns() {
        val cu = org.json.JSONObject("""{"id":"x","ten":"Cũ","dns":true,"apps":[]}""")
        assertEquals(DnsRieng.Loai.ADGUARD, BoCai.tuJson(cu).dnsLoai)
        assertEquals(DnsRieng.Loai.TAT, BoCai.tuJson(org.json.JSONObject("""{"id":"y","ten":"","apps":[]}""")).dnsLoai)
    }

    @Test fun boPhanDangSoan() {
        val kho = KhoBoCai(tam.root)
        File(kho.thuMucBo("moi"), "a.apk").writeText("x")
        kho.donFileThua("moi")
        assertFalse(File(kho.thuMuc, "moi").exists())
        // Bộ đã lưu: chỉ xoá file thêm trong lúc sửa mà chưa lưu.
        val tm = kho.thuMucBo("cu")
        File(tm, "giu.apk").writeText("x"); File(tm, "moi-them.apk").writeText("y")
        kho.luu(BoCai("cu", "Cũ", listOf(MucBoCai("giu.apk", "Giữ", "a.b", "1", 1, 1))))
        File(tm, "moi-them.apk").writeText("y")
        kho.donFileThua("cu")
        assertTrue(File(tm, "giu.apk").exists()); assertFalse(File(tm, "moi-them.apk").exists())
    }
}
