package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kịch bản khôi phục cho máy tính: mỗi dòng phải là lệnh adb thuần hoặc chú thích ": ", chạy được cả .bat lẫn sh. */
class KichBanKhoiPhucTest {
    private fun sl() = BanSaoLuu(
        khoa = "k", ten = "TCL Smart TV Pro", ip = "192.168.1.20", luc = 0, daTatTruoc = emptySet(),
        homeTruoc = "com.google.android.apps.tv.launcherx/.home.HomeActivity", dnsModeTruoc = "opportunistic", dnsHostTruoc = "null",
        truyCapTruoc = "null", getprop = "", goiTruoc = emptyList(),
        daTatBoiApp = linkedSetOf("com.tcl.ttvs", "com.google.android.apps.tv.launcherx"),
        launcherDaCai = true, dnsDaDoi = true,
    )

    @Test fun moiDongLaLenhAdbHoacChuThich() {
        val s = KichBanKhoiPhuc.tao(sl(), vi = true)
        assertFalse(s.contains('\r'))
        val ds = s.trimEnd().split("\n")
        ds.forEach { assertTrue(it, it.startsWith("adb ") || it.startsWith(": ")) }
        // Chú thích là ": # …" (sh bỏ qua cả ngoặc, nháy đơn); dòng ": adb …" là lệnh để sẵn, không có ký tự lạ.
        ds.filter { it.startsWith(": ") && !it.startsWith(": # ") }.forEach { d -> assertTrue(d, d.startsWith(": adb ") && d.none { it in "()&|<>'\"%;`" }) }
        // Không có ký tự mà cmd.exe hay sh hiểu khác nhau.
        ds.filter { it.startsWith("adb ") }.forEach { d -> assertFalse(d, d.any { it in "&|<>'\"%;`" }) }
    }

    @Test fun duBuocKhoiPhuc() {
        val s = KichBanKhoiPhuc.tao(sl(), vi = false)
        assertTrue(s.contains("adb connect 192.168.1.20:5555"))
        assertTrue(s.contains("adb shell pm enable --user 0 com.tcl.ttvs"))
        assertTrue(s.contains("adb shell pm enable --user 0 com.google.android.apps.tv.launcherx"))
        assertTrue(s.contains("adb shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity"))
        assertTrue(s.contains("adb shell settings delete secure enabled_accessibility_services"))
        assertTrue(s.contains("adb shell settings put global private_dns_mode opportunistic"))
        assertTrue(s.contains("adb shell settings delete global private_dns_specifier"))
        // Gỡ Home sạch chỉ là gợi ý, nằm trong dòng chú thích.
        assertTrue(s.contains(": adb uninstall com.gathin.tvhome"))
        assertFalse(s.lines().any { it.trim() == "adb uninstall com.gathin.tvhome" })
        // App phụ: tên gói mới và tên trước khi đổi sang vn.tivikit.
        assertTrue(s.contains("adb uninstall vn.tivikit.khoa"))
        assertTrue(s.contains("adb uninstall vn.tivikit.kenh3"))
        assertTrue(s.contains("adb uninstall vn.tivikit.hengio"))
        assertTrue(s.contains("adb uninstall vn.tivikit.launcher"))
        assertTrue(s.contains("adb uninstall vn.tivikit.go"))
        assertTrue(s.contains("adb uninstall vn.tivikit.mapvoice"))
        assertTrue(s.contains("adb uninstall com.icm.cleantv.khoa"))
        assertTrue(s.contains("adb uninstall com.icm.cleantv.kenh"))
    }

    /** DNS gốc là NextDNS người dùng tự đặt (tên có "-"): kịch bản phải trả lại đúng, không xoá. */
    @Test fun dnsGocCoGachNgang() {
        val s = KichBanKhoiPhuc.tao(sl().copy(dnsModeTruoc = "hostname", dnsHostTruoc = "Tivi-abc123.dns.nextdns.io"), vi = true)
        assertTrue(s.contains("adb shell settings put global private_dns_mode hostname"))
        assertTrue(s.contains("adb shell settings put global private_dns_specifier Tivi-abc123.dns.nextdns.io"))
    }

    /** Home đổi ở Quản lý launcher, tivi chưa cài Home sạch bằng app: vẫn trả Home cũ; hộp chọn ("android/…") thì không. */
    @Test fun homeDaDoiOQuanLyLauncher() {
        val s = KichBanKhoiPhuc.tao(sl().copy(launcherDaCai = false, homeDaDoi = true, homeTruoc = "com.google.android.tvlauncher/.MainActivity"), vi = true)
        assertTrue(s.contains("adb shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity"))
        assertFalse(s.contains("enabled_accessibility_services"))
        val hoi = KichBanKhoiPhuc.tao(sl().copy(homeDaDoi = true, homeTruoc = "android/com.android.internal.app.ResolverActivity"), vi = true)
        assertEquals(0, hoi.lines().count { it.contains("set-home-activity") })
    }

    @Test fun homeTruocLaHomeSachThiKhongDat() {
        val s = KichBanKhoiPhuc.tao(sl().copy(homeTruoc = "com.gathin.tvhome/.MainActivity"), vi = true)
        assertEquals(0, s.lines().count { it.contains("set-home-activity") })
    }
}
