package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Bộ lập kế hoạch với dữ liệu thật (assets/du-lieu.json, DuLieu.SO_MAU mẫu). 22/10/2026: Mi Box S mất mic, Google Home mất tivi. */
class KeHoachTest {
    private val du = DuLieu.docJson(File("src/main/assets/du-lieu.json").readText())

    /** Số mẫu ghi trên giao diện và trong trang giấy phép phải khớp dữ liệu thật (đã từng lệch: 147, 213 so với 260). */
    @Test fun soMau_khopDuLieu() {
        assertEquals(du.mau.size, DuLieu.SO_MAU)
        assertTrue(File("src/main/assets/giay-phep.txt").readText().contains("${DuLieu.SO_MAU} mẫu tivi"))
    }

    /**
     * Chốt chặn cho bản tự phát hành (26/10/2026): danh sách mẫu tự cập nhật hằng tuần từ CleanYourTV và tự phát hành
     * không ai duyệt, nên với MỌI mẫu, gói tivi cần để chạy (lõi Android, CH Play, Dịch vụ Google Play, Home, trình cài
     * app, bàn phím…) không bao giờ được tích sẵn, kể cả khi danh sách mẫu ghi tắt.
     */
    @Test fun moiMau_khongTichSanGoiCanGiu() {
        val canGiu = setOf("com.android.vending", "com.google.android.gms", "com.google.android.gsf", "com.android.systemui",
            "com.android.tv.settings", "com.google.android.packageinstaller", "com.gathin.tvhome", "com.android.providers.tv")
        var soMuc = 0
        for (m in du.mau) {
            val tt = TrangThaiGoi(caiDat = m.goi.map { it.pkg }.toSet() + canGiu, daTat = emptySet(), coBieuTuong = emptySet(),
                homeHienTai = "com.gathin.tvhome", banPhim = setOf("com.google.android.inputmethod.latin"))
            val ds = KeHoach.lap(du, m, tt)
            soMuc += ds.size
            val sai = ds.filter { it.chonSan && (KeHoach.laGoiCanGiu(it.pkg, tt) || KeHoach.laGoiLoiAndroid(it.pkg)) }
            assertTrue("${m.id} tích sẵn gói cần giữ: ${sai.map { it.pkg }}", sai.isEmpty())
        }
        assertTrue("bài thử phải chạy qua dữ liệu thật", soMuc > 1000)
    }

    private fun tivi(mauId: String, them: Set<String> = emptySet()): Pair<MauTv, List<MucGoi>> {
        val mau = du.mau.first { it.id == mauId }
        val caiDat = mau.goi.map { it.pkg }.toSet() + them
        val tt = TrangThaiGoi(caiDat = caiDat, daTat = emptySet(), coBieuTuong = emptySet(), homeHienTai = "", banPhim = emptySet())
        return mau to KeHoach.lap(du, mau, tt)
    }

    /** Sony: gói nằm sau màn Cài đặt không bao giờ tích sẵn (CleanTV 2.10.2/2.10.4: Cài đặt mở ra trống, phải reset cứng). */
    @Test fun sony_goiSauManCaiDat_khongTichSan() {
        val (_, ds) = tivi("sony-bravia-4k-ur2")
        for (p in listOf("com.sony.dtv.b2b.hotelmode", "com.sony.dtv.deviceadminsettings")) {
            val m = ds.first { it.pkg == p }
            assertFalse("$p không được tích sẵn", m.chonSan)
        }
    }

    /** Chế độ cửa hàng (shopsettings) vẫn để danh sách đúng mẫu quyết định. */
    @Test fun sony_cheDoCuaHang_vanTheoDanhSach() {
        val (_, ds) = tivi("sony-kdl-43w950d")
        assertTrue("shopsettings (nhóm demo cửa hàng) vẫn tích sẵn", ds.first { it.pkg == "com.sony.dtv.shopsettings" }.chonSan)
    }

    @Test fun miBoxS_khongTichSanKatniss() {
        val (_, ds) = tivi("xiaomi-mi-box-s")
        val k = ds.first { it.pkg == "com.google.android.katniss" }
        assertEquals(LoaiMuc.GIONG_NOI, k.loai)
        assertFalse("katniss không được tích sẵn", k.chonSan)
    }

    @Test fun mediashell_luonNhomGoogleHome_khongTichSan() {
        val (_, ds) = tivi("xiaomi-mi-box-s", setOf("com.google.android.apps.mediashell"))
        ds.filter { it.pkg == "com.google.android.apps.mediashell" }.forEach {
            assertEquals(LoaiMuc.GOOGLE_HOME, it.loai); assertFalse(it.chonSan)
        }
    }

    @Test fun nhomTheoYRieng_khongTichSan_conQuangCaoVanTich() {
        var tichQc = 0; var tongTheoY = 0
        for (m in du.mau) {
            val (_, ds) = tivi(m.id)
            for (x in ds) {
                if (Regex("by choice|personal choice|owner's choice", RegexOption.IGNORE_CASE).containsMatchIn(x.nhom)) {
                    tongTheoY++; assertFalse("${m.id} ${x.pkg} (${x.nhom}) không được tích sẵn", x.chonSan)
                }
                if (Regex("^(ads|telemetry)", RegexOption.IGNORE_CASE).containsMatchIn(x.nhom) && x.chonSan) tichQc++
            }
        }
        println("dòng theo ý riêng: $tongTheoY, dòng quảng cáo/theo dõi vẫn tích sẵn: $tichQc")
        assertTrue(tongTheoY > 500); assertTrue(tichQc > 50)
    }

    /** JVC LT-43N7115 treo màn khởi động sau khi tắt Home CVTE (upstream #741): không mẫu nào được đưa Home CVTE vào danh sách tắt. */
    @Test fun homeCvte_khongBaoGioVaoDanhSachTat() {
        var coTrongDuLieu = 0
        for (m in du.mau) {
            if (m.goi.any { KeHoach.laHomeCvte(it.pkg) }) coTrongDuLieu++
            val (_, ds) = tivi(m.id, setOf("com.cvte.tv.launcher.space", "com.cvte.tv.launcher.highend"))
            assertTrue("${m.id}: ${ds.filter { KeHoach.laHomeCvte(it.pkg) }.map { it.pkg }}", ds.none { KeHoach.laHomeCvte(it.pkg) })
        }
        assertTrue("dữ liệu phải có mẫu ghi tắt Home CVTE để bài thử có nghĩa", coTrongDuLieu >= 2)
        assertTrue(KeHoach.laHomeCvte("com.cvte.tv.launcher.space.skin"))
        assertFalse(KeHoach.laHomeCvte("com.cvte.tv.appstore"))
    }

    /** Upstream #884: Mi TV Stick không khởi động lại sau khi tắt gói lõi Android. */
    @Test fun goiLoiAndroid_luonGiu() {
        val tt = TrangThaiGoi(caiDat = emptySet(), daTat = emptySet(), coBieuTuong = emptySet(), homeHienTai = "", banPhim = emptySet())
        listOf("com.google.android.ext.services", "com.android.ext.shared", "com.google.android.modulemetadata",
            "com.google.android.overlay.modules.permissioncontroller", "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller", "com.android.providers.tv").forEach {
            assertTrue(it, KeHoach.laGoiCanGiu(it, tt))
        }
        assertFalse(KeHoach.laGoiCanGiu("com.google.android.feedback", tt))
    }
}
