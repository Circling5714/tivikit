package vn.tivikit.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gửi danh sách cho CleanYourTV (26/10/2026): debloat.txt và tv.json phải qua được kiểm tra của chính kho đó
 * (tests/test_examples.py, tools/gen_tables.py), không lộ gì riêng tư, link issue điền đúng ô của tv-list.yml.
 */
class GopNguocTest {
    private val getprop = """
        [ro.product.brand]: [Xiaomi]
        [ro.product.manufacturer]: [Xiaomi]
        [ro.product.model]: [MiTV-MOOQ1]
        [ro.product.device]: [tarzan]
        [ro.build.version.release]: [10]
        [ro.build.version.sdk]: [29]
        [ro.build.display.id]: [QTG3.200305.006.2591]
        [ro.serialno]: [SERIAL123]
    """.trimIndent()

    private val mau = MauTv("xiaomi-mi-tv-4a", "Xiaomi Mi TV 4A", "Xiaomi Mi TV 4A (MiTV-MOOQ1, codename tarzan)", listOf(
        GoiMau("com.miui.tv.analytics", "Xiaomi telemetry", ""),
        GoiMau("com.xiaomi.mitv.tvmanager", "Xiaomi background services", "\"TV Manager\" cleaner app"),
    ))
    private val khac = MauTv("acer-x", "Acer X", "Acer X", listOf(GoiMau("com.google.android.feedback", "Google telemetry", "Google feedback")))
    private val du = DuLieu(listOf(mau, khac), mapOf(
        "com.miui.tv.analytics" to ThongKeGoi(3, 0),
        "com.google.android.feedback" to ThongKeGoi(120, 0),
        "com.google.android.katniss" to ThongKeGoi(40, 10),
    ))
    private val luc = 1_792_000_000_000L
    private val sauSauNgay = luc + 6 * 86_400_000L

    private fun saoLuu(daTat: List<String>, getprop: String = this.getprop, goi: List<String> = listOf("com.android.tv.settings"),
                       home: String = "com.mitv.tvhome.atv", launcher: Boolean = true) = BanSaoLuu(
        khoa = "SERIAL123", ten = "Xiaomi MiTV-MOOQ1", ip = "192.168.1.5", luc = luc, daTatTruoc = setOf("com.factory.off"),
        homeTruoc = "$home/.HomeActivity", dnsModeTruoc = "", dnsHostTruoc = "", truyCapTruoc = "", getprop = getprop,
        goiTruoc = goi, daTatBoiApp = daTat.toCollection(linkedSetOf()), launcherDaCai = launcher, homeGocDaTat = if (launcher) home else "",
    )

    private val tatCa = listOf(
        "com.xiaomi.mitv.tvmanager", "com.miui.tv.analytics", "com.google.android.feedback", "com.google.android.katniss",
        "com.vendor.ads", "com.android.permissioncontroller", "com.user.vpn", "com.mitv.tvhome.atv",
    )

    private fun xuat(sl: BanSaoLuu = saoLuu(tatCa), conTat: Set<String>? = tatCa.toSet() - "com.vendor.ads",
                     tuCai: Set<String>? = setOf("com.user.vpn"), m: MauTv? = mau) =
        GopNguoc.tao(sl, sl.getprop, sl.goiTruoc, du, m, conTat, tuCai, "1.4.0", sauSauNgay)

    @Test fun dongDau_mauTivi_banAndroid_firmware() {
        val b = xuat()
        assertEquals("# Xiaomi MiTV-MOOQ1 (codename tarzan), Android TV 10, firmware QTG3.200305.006.2591", b.debloat.lines().first())
        assertEquals("xiaomi-mitv-mooq1", b.thuMuc)
        assertTrue(b.debloat.contains("# Apply:   TV_HOST=<tv ip> tools/disable.sh examples/xiaomi-mitv-mooq1/debloat.txt\n"))
        assertTrue(b.debloat.contains("# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-mitv-mooq1/debloat.txt\n"))
        assertTrue(b.debloat.contains("# Stock launcher: com.mitv.tvhome.atv, replaced as Home by the CleanYourTV launcher.\n"))
        assertEquals(6, b.soNgay)
        assertTrue(b.daKiemTra)
    }

    @Test fun quaKiemTraDinhDangCuaCleanYourTv() {
        assertEquals(emptyList<String>(), loiDinhDang(xuat().debloat))
        assertEquals(emptyList<String>(), loiDinhDang(xuat(conTat = null, tuCai = null, m = null).debloat))
    }

    @Test fun nhomCuaMauKhopTruoc_ghiChuThangCot() {
        val d = xuat().debloat
        assertTrue(d.contains("\n# Xiaomi telemetry\ncom.miui.tv.analytics\n"))
        assertTrue(d.contains("\n# Xiaomi background services\ncom.xiaomi.mitv.tvmanager              # \"TV Manager\" cleaner app\n"))
        assertTrue(d.contains("\n# Not in examples/xiaomi-mi-tv-4a, disabled on other lists\ncom.google.android.feedback            # Google feedback\n"))
        assertTrue(d.indexOf("# Xiaomi telemetry") < d.indexOf("# Not in examples/xiaomi-mi-tv-4a"))
        assertTrue(d.contains("\n# Voice search and Google Assistant, off by choice (the remote's mic button stops working)\ncom.google.android.katniss\n"))
        assertTrue(d.contains("\n# Stock launcher, replaced by the CleanYourTV launcher (TiviKit build)\ncom.mitv.tvhome.atv\n"))
        assertTrue(d.contains("# TiviKit matched this TV to examples/xiaomi-mi-tv-4a/"))
    }

    @Test fun khongLoGoiLoiAppTuCaiVaThongTinRieng() {
        val b = xuat()
        val moi = b.debloat + b.tvJson + GopNguoc.chiTiet(b) + GopNguoc.linkIssue(b)
        for (an in listOf("com.android.permissioncontroller", "com.user.vpn", "SERIAL123", "192.168.1.5", "com.factory.off"))
            assertFalse(an, moi.contains(an))
        assertEquals(listOf("com.user.vpn"), b.boTuCai)
    }

    @Test fun goiTiviBatLai_chiGhiChuCuoiFile() {
        val b = xuat()
        assertEquals(listOf("com.vendor.ads"), b.daBatLai)
        val d = b.debloat
        assertTrue(d.contains("\n# Disabled by TiviKit, then found enabled again (the firmware at boot, or by hand), so left out:\n# com.vendor.ads\n"))
        assertFalse(d.lines().any { it.trim() == "com.vendor.ads" })
        assertEquals(5, b.soGoi)
    }

    @Test fun chuaNoiTivi_theoBanSaoLuu() {
        val b = xuat(conTat = null, tuCai = null, m = null)
        assertFalse(b.daKiemTra)
        assertTrue(b.debloat.contains("not re-checked on the TV since"))
        assertTrue("chưa biết là app tự cài, vẫn liệt kê để người dùng xem", b.debloat.contains("\ncom.user.vpn\n"))
        assertTrue(b.debloat.contains("\n# On no CleanYourTV list yet\ncom.user.vpn\ncom.vendor.ads\n"))
        assertTrue(b.debloat.contains("\n# Disabled on other CleanYourTV lists too\n"))
        assertNull(b.mau)
    }

    @Test fun googleTv_setupwraithTheoHomeGoc() {
        val gp = getprop.replace("[10]", "[12]")
        val sl = saoLuu(listOf("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith", "com.google.android.feedback"),
            getprop = gp, goi = listOf("com.google.android.apps.tv.launcherx", "com.android.tv.settings"), home = "com.google.android.apps.tv.launcherx")
        val b = GopNguoc.tao(sl, gp, sl.goiTruoc, du, null, null, null, "1.4.0", sauSauNgay)
        assertEquals("Google TV 12", b.os)
        assertTrue(b.debloat.contains("\n# Stock launcher, replaced by the CleanYourTV launcher (TiviKit build)\n" +
            "com.google.android.apps.tv.launcherx\ncom.google.android.tungsten.setupwraith  # takes Home while enabled\n"))
        assertFalse("không nằm ở nhóm cast", b.debloat.contains("# Casting and Google Home"))
    }

    @Test fun chuaCaiHomeSach_giuHomeGoc_khongGhiHopChonHome() {
        val sl = saoLuu(listOf("com.vendor.ads"), launcher = false)
        val b = GopNguoc.tao(sl, getprop, sl.goiTruoc, du, null, null, null, "1.4.0", sauSauNgay)
        assertTrue(b.debloat.contains("# Stock launcher: com.mitv.tvhome.atv, kept as Home.\n"))
        assertFalse(JSONObject(b.tvJson).getString("notes").contains("launcher"))
        val chuaCoHome = saoLuu(listOf("com.vendor.ads"), home = "android", launcher = false)
        assertFalse(GopNguoc.tao(chuaCoHome, getprop, chuaCoHome.goiTruoc, du, null, null, null, "1.4.0", sauSauNgay).debloat.contains("# Stock launcher:"))
    }

    @Test fun tvJson_hopLeTheoGenTables() {
        val b = xuat()
        val j = JSONObject(b.tvJson)
        assertEquals(setOf("tv", "os", "brand", "notes"), j.keySet())
        assertEquals("Xiaomi MiTV-MOOQ1 (tarzan)", j.getString("tv"))
        assertEquals("Android TV 10", j.getString("os"))
        assertEquals("Xiaomi", j.getString("brand"))
        for (k in j.keySet()) {
            val v = j.getString(k)
            assertTrue(k, v.isNotBlank() && v == v.trim() && '\n' !in v && '|' !in v)
        }
        assertTrue(b.tvJson.startsWith("{\n  \"tv\": "))
    }

    @Test fun linkIssue_dienSanCacOCuaTvList() {
        val b = xuat()
        val l = GopNguoc.linkIssue(b)
        assertTrue(l.startsWith("https://github.com/Gathin23/cleanyourtv/issues/new?template=tv-list.yml&title=%5BTV%5D%20Xiaomi%20MiTV-MOOQ1%20%28Android%20TV%2010%29"))
        assertTrue(l.contains("&model=Xiaomi%20MiTV-MOOQ1%20%28tarzan%29"))
        assertTrue(l.contains("&android=Android%20TV%2010%2C%20build%20QTG3.200305.006.2591"))
        assertTrue(l.contains("&details=%23%20Suggested%20folder%3A%20examples%2Fxiaomi-mitv-mooq1%2F"))
        assertFalse(l.contains("+"))
        assertTrue(l.length <= GopNguoc.LINK_TOI_DA)
    }

    @Test fun danhSachDai_linkNhacDan() {
        val nhieu = (1..400).map { "com.vendor.junk$it" }
        val sl = saoLuu(nhieu)
        val b = GopNguoc.tao(sl, getprop, sl.goiTruoc, du, null, null, null, "1.4.0", sauSauNgay)
        val l = GopNguoc.linkIssue(b)
        assertTrue(l.length <= GopNguoc.LINK_TOI_DA)
        assertTrue(l.endsWith("&details=" + java.net.URLEncoder.encode(GopNguoc.DAN_VAO, "UTF-8").replace("+", "%20")))
        assertTrue("chép đủ để dán", GopNguoc.chiTiet(b).contains("com.vendor.junk400"))
    }

    @Test fun chiTiet_coThuMucSoNgayVaTvJsonDangChuThich() {
        val c = GopNguoc.chiTiet(xuat())
        assertTrue(c.startsWith("# Suggested folder: examples/xiaomi-mitv-mooq1/ (debloat.txt below, then tv.json)\n# Off for 6 days so far.\n\n# Xiaomi MiTV-MOOQ1"))
        assertTrue(c.contains("\n# tv.json\n# {\n#   \"tv\": \"Xiaomi MiTV-MOOQ1 (tarzan)\","))
        assertEquals("tv.json chỉ là chú thích: vẫn qua kiểm tra định dạng", emptyList<String>(), loiDinhDang(c))
    }

    @Test fun banSaoLuuLayTuTivi_khongCoGetprop() {
        val sl = saoLuu(listOf("com.vendor.ads"), getprop = "", goi = emptyList())
        val b = GopNguoc.tao(sl, "", emptyList(), du, null, null, null, "1.4.0", sauSauNgay)
        assertEquals("# Xiaomi MiTV-MOOQ1, Android TV", b.debloat.lines().first())
        assertEquals("xiaomi-mitv-mooq1", b.thuMuc)
        assertEquals("Xiaomi", JSONObject(b.tvJson).getString("brand"))
    }

    @Test fun thuMuc_kieuCleanYourTv() {
        assertEquals("xiaomi-mitv-mooq1", GopNguoc.thuMuc("Xiaomi", "MiTV-MOOQ1"))
        assertEquals("tcl-55c728", GopNguoc.thuMuc("TCL", "TCL 55C728"))
        assertEquals("sony-bravia-4k-vh2", GopNguoc.thuMuc("Sony", "BRAVIA 4K VH2"))
        assertEquals("my-tv", GopNguoc.thuMuc("", " "))
    }

    @Test fun heDieuHanh_fireOsAndroidThuong() {
        fun tv(model: String, nsx: String = "x") = ThongTinTv("b", nsx, model, "d", "", "", "9", 28, "", "")
        assertEquals("Fire OS, Android 9", GopNguoc.heDieuHanh(tv("AFTKA", "Amazon"), listOf("com.amazon.tv.launcher")))
        assertEquals("Android 9", GopNguoc.heDieuHanh(tv("TX3 Mini"), listOf("com.android.settings")))
        assertEquals("Android TV 9", GopNguoc.heDieuHanh(tv("TX3 Mini"), emptyList()))
    }

    @Test fun duLieuThat_mauXiaomi_quaKiemTra() {
        val that = DuLieu.docJson(File("src/main/assets/du-lieu.json").readText())
        val m = that.mau.first { it.id == "xiaomi-mi-tv-4a" }
        val daTat = m.goi.map { it.pkg } + listOf("com.google.android.youtube.tvmusic", "com.vendor.ads")
        val sl = saoLuu(daTat)
        val b = GopNguoc.tao(sl, getprop, sl.goiTruoc, that, m, daTat.toSet(), emptySet(), "1.4.0", sauSauNgay)
        assertEquals(emptyList<String>(), loiDinhDang(b.debloat))
        assertEquals(daTat.distinct().size, b.soGoi)
    }

    /** Như format_problems của tests/test_examples.py (CleanYourTV). */
    private fun loiDinhDang(chu: String): List<String> {
        val dong = chu.split("\n")
        val loi = mutableListOf<String>()
        if (!dong[0].startsWith("#")) loi += "dòng đầu không phải chú thích"
        val goi = dong.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        goi.filterNot { GOI.containsMatchIn(it) }.forEach { loi += "không phải tên gói: $it" }
        if (goi.isEmpty()) loi += "không có gói"
        goi.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { loi += "lặp: $it" }
        val dau = dong.indexOfFirst { it.substringBefore('#').trim().isNotEmpty() }
        dong.drop(dau + 1).mapNotNull { BO_CHU_THICH.matchEntire(it)?.groupValues?.get(1) }.filter { it in goi }
            .forEach { loi += "chú thích để giữ mà vẫn tắt: $it" }
        return loi
    }

    private companion object {
        val GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
        val BO_CHU_THICH = Regex("""^\s*#\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)\s*(?:#.*)?$""")
    }
}
