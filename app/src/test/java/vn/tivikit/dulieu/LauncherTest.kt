package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.tivikit.dulieu.Launcher.Loai
import vn.tivikit.dulieu.Launcher.LyDoGiu

/** Quản lý launcher: đọc chữ lệnh dò, phân loại, nút Home mở gì, launcher nào chặn, launcher nào được tắt. */
class LauncherTest {
    private val homeSach = setOf("com.gathin.tvhome", "com.ts.launcher")
    private val gocBiet = listOf("com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher", "com.mitv.tvhome")

    /** Skyworth SWTV-22AE (Google TV, Android 11) ngày 27/10/2026: Home sạch mở đè launcherx; danh sách gói đã rút gọn. */
    private val skyworth = """
        @@BAT
        4 activities found:
          Activity #0:
            priority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.apps.tv.launcherx/.home.HomeActivity
          Activity #1:
            priority=1 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.tungsten.setupwraith/.RecoveryActivity
          Activity #2:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.gathin.tvhome/.MainActivity
          Activity #3:
            priority=-1000 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.android.tv.settings/.system.FallbackHome
        @@TAT
        8 activities found:
          Activity #0:
            priority=4 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.tungsten.setupwraith/.MainActivity
          Activity #1:
            priority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.apps.tv.launcherx/.home.HomeActivity
          Activity #2:
            priority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.apps.tv.launcherx/.home.VanillaModeHomeActivity
          Activity #3:
            priority=1 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.tungsten.setupwraith/.RecoveryActivity
          Activity #4:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.gathin.tvhome/.MainActivity
          Activity #5:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            eu.chainfire.tv.sideloadlauncher/.HomeAlias
          Activity #6:
            priority=-100 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false
            android/com.android.internal.app.SystemUserHomeActivity
          Activity #7:
            priority=-1000 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.android.tv.settings/.system.FallbackHome
        @@MD
        priority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
        com.google.android.apps.tv.launcherx/.home.HomeActivity
        @@D
        package:com.smartdevice.setupwraith
        package:com.smartdevice.leanbacklauncher.customizer
        package:com.netflix.ninja
        @@S
        package:android
        package:com.android.tv.settings
        package:com.google.android.apps.tv.launcherx
        package:com.google.android.tungsten.setupwraith
        package:com.smartdevice.setupwraith
        @@A
        vn.tivikit.hengio/.HenGioDichVu:com.gathin.tvhome/com.gathin.tvhome.HomeKeyService
        1
        @@SETUP
        package:com.google.android.tungsten.setupwraith
        @@CH
        {"dock_apps":["org.smarttube.stable"],"power_on_home_always":true,"app_keys":["scan:397:intent:\/\/search?launch=voice#Intent;scheme=youtube;package=org.smarttube.stable;end"],"stock_home_redirect":"com.google.android.apps.tv.launcherx"}

        @@HET
    """.trimIndent()

    /** Tanix TX3 Mini (Android 9) ngày 27/10/2026: không có --query-flags, tvlauncher đang tắt (mức ưu tiên 2). */
    private val tx3 = """
        @@BAT
        2 activities found:
          Activity #0:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.gathin.tvhome/.MainActivity
          Activity #1:
            priority=-1000 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.android.tv.settings/.system.FallbackHome
        @@TAT

        Exception occurred while executing:
        java.lang.IllegalArgumentException: Unknown option: --query-flags
            at android.content.Intent.parseCommandArgs(Intent.java:6922)
        @@MD
        priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
        com.gathin.tvhome/.MainActivity
        @@D
        package:cn.com.factorytest
        package:com.google.android.tvlauncher
        package:com.google.android.katniss
        @@S
        package:android
        package:cn.com.factorytest
        package:com.google.android.tvlauncher
        package:com.google.android.leanbacklauncher
        package:com.android.tv.settings
        @@A
        com.gathin.tvhome/com.gathin.tvhome.InputKeyService:com.gathin.tvhome/com.gathin.tvhome.HomeKeyService
        1
        @@SETUP
        @@CH
        {"dock_apps":["org.smarttube.stable"],"all_apps_home":true}

        @@HET
    """.trimIndent()

    /** Chữ LENH_TAT_CO_HOME trên TX3 Mini. */
    private val tx3Tat = "com.google.android.tvlauncher mPriority=2\n"

    /** Tivi Android TV giả định: tvlauncher (mức 2) đang là Home, có Projectivy và Home sạch, Projectivy bật trợ năng. */
    private val coProjectivy = """
        @@BAT
        3 activities found:
          Activity #0:
            priority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.tvlauncher/.MainActivity
          Activity #1:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.spocky.projengmenu/.ui.home.MainActivity
          Activity #2:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.gathin.tvhome/.MainActivity
        @@TAT
        3 activities found:
          Activity #0:
            priority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.google.android.tvlauncher/.MainActivity
          Activity #1:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.spocky.projengmenu/.ui.home.MainActivity
          Activity #2:
            priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
            com.gathin.tvhome/.MainActivity
        @@MD
        priority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
        com.google.android.tvlauncher/.MainActivity
        @@D
        @@S
        package:com.google.android.tvlauncher
        @@A
        com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService
        1
        @@SETUP
        @@CH

        @@HET
    """.trimIndent()

    @Test fun docQueryKemMucUuTien() {
        val ds = Launcher.docQuery(skyworth.substringAfter("@@BAT").substringBefore("@@TAT"))
        assertEquals(listOf(2, 1, 0, -1000), ds.map { it.uuTien })
        assertEquals("com.google.android.apps.tv.launcherx", ds[0].goi)
        assertEquals("com.android.tv.settings/.system.FallbackHome", ds[3].cmp)
    }

    @Test fun skyworthHomeSachMoDeLauncherx() {
        val tt = Launcher.tuChu(skyworth, "com.gathin.tvhome", homeSach)
        assertFalse(Launcher.thieuManTat(skyworth))
        // setupwraith, Cài đặt, hộp chọn của hệ thống, màn tắt lẻ (VanillaMode, HomeAlias của Sideload Launcher) không hiện.
        assertEquals(listOf("com.gathin.tvhome", "com.google.android.apps.tv.launcherx"), tt.ds.map { it.goi })
        assertEquals(listOf(Loai.HOME_SACH, Loai.GOC), tt.ds.map { it.loai })
        assertTrue(tt.ds.none { it.dangTat })
        assertEquals("com.google.android.apps.tv.launcherx/.home.HomeActivity", tt.macDinh)
        assertEquals("com.google.android.apps.tv.launcherx", tt.chuyenDe)
        // Khoá làm Home sạch tự mở: chọn launcher khác làm Home thì phải gỡ.
        assertEquals(listOf("stock_home_redirect", "power_on_home_always"), tt.khoaGianh)
        assertEquals("com.gathin.tvhome", tt.homeMo)
        assertTrue(tt.setupBat)
        assertTrue(tt.troNangKhac.isEmpty())
        // Home sạch mức 0: launcherx (2) và setupwraith (1) luôn thắng, mà Google TV còn setupwraith thì không tắt được.
        val hs = tt.cua("com.gathin.tvhome")!!
        assertEquals(listOf("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith"), Launcher.chan(hs, tt))
        assertFalse(Launcher.tatDuocDeNhuong("com.google.android.apps.tv.launcherx", tt, gocBiet))
        assertFalse(Launcher.tatDuocDeNhuong("com.google.android.tungsten.setupwraith", tt, gocBiet))
        assertEquals(LyDoGiu.GOOGLE_TV, Launcher.lyDoGiu("com.google.android.apps.tv.launcherx", tt, gocBiet))
        assertEquals(LyDoGiu.HOME_SACH, Launcher.lyDoGiu("com.gathin.tvhome", tt, gocBiet))
        // Về Home gốc: không launcher nào mức cao hơn launcherx.
        assertTrue(Launcher.chan(tt.cua("com.google.android.apps.tv.launcherx")!!, tt).isEmpty())
    }

    @Test fun tx3Android9DoGoiTat() {
        assertTrue(Launcher.thieuManTat(tx3))
        // Chưa chạy lệnh dò gói tắt: chỉ thấy launcher đang bật.
        assertEquals(listOf("com.gathin.tvhome"), Launcher.tuChu(tx3, "com.gathin.tvhome", homeSach).ds.map { it.goi })
        val tt = Launcher.tuChu(tx3, "com.gathin.tvhome", homeSach, tx3Tat)
        assertEquals(listOf("com.gathin.tvhome", "com.google.android.tvlauncher"), tt.ds.map { it.goi })
        val tv = tt.cua("com.google.android.tvlauncher")!!
        assertTrue(tv.dangTat)
        assertNull(tv.cmp)
        assertEquals(2, tv.uuTien)
        assertEquals(Loai.GOC, tv.loai)
        assertEquals("com.gathin.tvhome", tt.homeMo)
        assertEquals("", tt.chuyenDe)
        assertTrue(tt.khoaGianh.isEmpty())
        assertFalse(tt.setupBat)
        // Bật lại tvlauncher (mức 2) là nó giành Home của Home sạch (mức 0): màn hình phải hỏi trước.
        assertTrue(Launcher.batLaGianhHome(tv, tt))
        assertTrue(Launcher.chan(tv, tt).isEmpty())
        assertNull(Launcher.lyDoGiu("com.google.android.tvlauncher", tt, gocBiet))
    }

    @Test fun projectivyBiHomeGocChan() {
        val tt = Launcher.tuChu(coProjectivy, "com.gathin.tvhome", homeSach)
        assertEquals(listOf(Loai.HOME_SACH, Loai.CAI_THEM, Loai.GOC), tt.ds.map { it.loai })
        assertEquals("com.google.android.tvlauncher", tt.homeMo)
        // Không có config.json của Home sạch: không chuyển đè.
        assertEquals("", tt.chuyenDe)
        assertEquals(listOf("com.spocky.projengmenu"), tt.troNangKhac)
        val p = tt.cua("com.spocky.projengmenu")!!
        assertEquals(listOf("com.google.android.tvlauncher"), Launcher.chan(p, tt))
        // Không phải Google TV còn setupwraith: tắt tvlauncher để nhường Home được, dù nó đang là Home.
        assertTrue(Launcher.tatDuocDeNhuong("com.google.android.tvlauncher", tt, gocBiet))
        assertEquals(LyDoGiu.DANG_LA_HOME, Launcher.lyDoGiu("com.google.android.tvlauncher", tt, gocBiet))
        assertNull(Launcher.lyDoGiu("com.spocky.projengmenu", tt, gocBiet))
        assertFalse(Launcher.batLaGianhHome(p, tt))
    }

    @Test fun dangHoiChonHome() {
        val chu = coProjectivy.replace(
            "@@MD\npriority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\ncom.google.android.tvlauncher/.MainActivity",
            "@@MD\npriority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=true\nandroid/com.android.internal.app.ResolverActivity")
        val tt = Launcher.tuChu(chu, "com.gathin.tvhome", homeSach)
        assertTrue(tt.dangHoiChon)
        assertEquals("android", tt.homeMo)
        // Đang hỏi chọn: không launcher nào bị coi là "đang là Home".
        assertNull(Launcher.lyDoGiu("com.spocky.projengmenu", tt, gocBiet))
    }

    /** Launcher bật cuối cùng không được tắt; trợ năng tắt (accessibility_enabled 0) thì không tính là bắt nút Home. */
    @Test fun launcherCuoiCungVaTroNangTat() {
        val chu = coProjectivy.replace("ProjectivyAccessibilityService\n1", "ProjectivyAccessibilityService\n0")
        val tt = Launcher.tuChu(chu, "com.gathin.tvhome", homeSach)
        assertTrue(tt.troNangKhac.isEmpty())
        val mot = tt.copy(ds = tt.ds.filter { it.goi == "com.spocky.projengmenu" }, macDinh = "", homeMo = "")
        assertEquals(LyDoGiu.CON_MOT, Launcher.lyDoGiu("com.spocky.projengmenu", mot, gocBiet))
    }

    /** Google TV: Home sạch mở đè launcherx; home_target báo nó mở Projectivy thay chính nó (giai đoạn 2, 27/10/2026). */
    @Test fun homeSachMoProjectivyDeLauncherx() {
        val coP = skyworth
            .replace("  Activity #3:\n    priority=-1000", "  Activity #9:\n    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n    com.spocky.projengmenu/.ui.home.MainActivity\n  Activity #3:\n    priority=-1000")
            .replace("\"stock_home_redirect\":", "\"home_target\":\"com.spocky.projengmenu/.ui.home.MainActivity\",\"stock_home_redirect\":")
        val tt = Launcher.tuChu(coP, "com.gathin.tvhome", homeSach)
        assertEquals("com.spocky.projengmenu/.ui.home.MainActivity", tt.dichHome)
        assertEquals("com.spocky.projengmenu", tt.homeMo)
        assertEquals(listOf(Loai.HOME_SACH, Loai.CAI_THEM, Loai.GOC), tt.ds.map { it.loai })
        // Projectivy bị tắt (gỡ, tắt gói): Home sạch mở lại chính nó.
        val tat = Launcher.tuChu(coP.replace("@@D\n", "@@D\npackage:com.spocky.projengmenu\n"), "com.gathin.tvhome", homeSach)
        assertEquals("com.gathin.tvhome", tat.homeMo)
    }

    /** TiviKit Launcher 1.4 bật dịch vụ Nút nguồn (menu ⏻): không bắt phím, không bị coi là launcher tự bắt nút Home. */
    @Test fun nutNguonTklKhongBatPhimHome() {
        val chu = coProjectivy.replace("com.spocky.projengmenu/.ui.home.MainActivity", "vn.tivikit.launcher/.Home")
            .replace("com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService", TiviKitLauncher.DICH_VU)
        val tt = Launcher.tuChu(chu, "com.gathin.tvhome", homeSach)
        assertTrue(tt.ds.any { it.goi == TiviKitLauncher.GOI })
        assertTrue(tt.troNangKhac.isEmpty())
        assertTrue(TiviKitLauncher.DICH_VU in tt.troNangDangBat)
    }

    @Test fun troNangCuaLauncher() {
        val chu = coProjectivy.replace("@@SETUP\n", "@@SETUP\n@@TN\ncom.spocky.projengmenu/.services.ProjectivyAccessibilityService\ncom.gathin.tvhome/.HomeKeyService\nvn.tivikit.hengio/.HenGioDichVu\n")
        val tt = Launcher.tuChu(chu, "com.gathin.tvhome", homeSach)
        val dv = "com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService"
        // Chỉ dịch vụ của launcher khác Home sạch; tên ngắn ".services.X" đổi ra tên đầy đủ để so với danh sách đang bật.
        assertEquals(mapOf("com.spocky.projengmenu" to listOf(dv)), tt.troNangCua)
        assertTrue(dv in tt.troNangDangBat)
        assertEquals("a.b/a.b.C", Launcher.dayDu("a.b/.C"))
        assertEquals("a.b/x.y.C", Launcher.dayDu("a.b/x.y.C"))
    }

    @Test fun docTatCoHomeVaLocLauncher() {
        assertEquals(mapOf("com.google.android.tvlauncher" to 2, "com.example.home" to 0),
            Launcher.docTatCoHome("com.google.android.tvlauncher mPriority=2\ncom.example.home \n\nrác/không"))
        assertFalse(Launcher.laLauncher("android"))
        assertFalse(Launcher.laLauncher("com.smartdevice.setupwraith"))
        assertFalse(Launcher.laLauncher("com.duokan.factorytest"))
        assertFalse(Launcher.laLauncher("com.android.tv.settings"))
        assertFalse(Launcher.laLauncher("com.example.tv", "com.example.tv/.FallbackHome"))
        assertTrue(Launcher.laLauncher("com.spocky.projengmenu", "com.spocky.projengmenu/.ui.home.MainActivity"))
    }

    @Test fun lenhDocCoDuPhan() {
        val l = Launcher.lenhDoc("com.ts.launcher")
        listOf("@@BAT", "@@TAT", "@@MD", "@@D", "@@S", "@@A", "@@SETUP", "@@CH", "@@HET").forEach { assertTrue(it, l.contains("echo $it")) }
        assertTrue(l.contains("content://com.ts.launcher.theme/config.json"))
        assertTrue(l.contains("--query-flags 512"))
    }
}
