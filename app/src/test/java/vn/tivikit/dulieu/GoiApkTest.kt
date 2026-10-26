package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chọn phần APK trong gói chia nhỏ và giải thích lỗi pm install (25/10/2026). */
class GoiApkTest {
    private fun p(vararg ten: String) = ten.map { PhanApk(it, 100) }
    private val TV_ARM64 = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
    private val TV_ARM32 = listOf("armeabi-v7a", "armeabi")

    @Test fun apkm_chiGiuAbiTotNhat() {
        val goi = p("base.apk", "split_config.arm64_v8a.apk", "split_config.armeabi_v7a.apk", "split_config.x86_64.apk",
            "split_config.x86.apk", "split_config.xhdpi.apk", "split_config.vi.apk", "info.json", "icon.png")
        assertEquals(listOf("base.apk", "split_config.arm64_v8a.apk", "split_config.xhdpi.apk", "split_config.vi.apk"),
            GoiApk.chonPhan(goi, TV_ARM64).map { it.muc })
        assertEquals(listOf("base.apk", "split_config.armeabi_v7a.apk", "split_config.xhdpi.apk", "split_config.vi.apk"),
            GoiApk.chonPhan(goi, TV_ARM32).map { it.muc })
    }

    @Test fun x86_64_khongBiNhamLaX86() {
        assertEquals("x86_64", GoiApk.abiCua(PhanApk("config.x86_64.apk", 1)))
        assertEquals("x86", GoiApk.abiCua(PhanApk("config.x86.apk", 1)))
        assertEquals("armeabi_v7a", GoiApk.abiCua(PhanApk("splits/base-armeabi_v7a.apk", 1)))
        assertEquals(null, GoiApk.abiCua(PhanApk("config.xhdpi.apk", 1)))
    }

    @Test fun xapk_boObbVaManifest() {
        val goi = p("com.vidu.game.apk", "config.arm64_v8a.apk", "manifest.json", "Android/obb/com.vidu.game/main.1.com.vidu.game.obb")
        val chon = GoiApk.chonPhan(goi, TV_ARM64)
        assertEquals(listOf("com.vidu.game.apk", "config.arm64_v8a.apk"), chon.map { it.muc })
        assertEquals("com.vidu.game.apk", GoiApk.phanChinh(chon)?.muc)
    }

    @Test fun bundletool_chiLaySplits() {
        val goi = p("toc.pb", "splits/base-master.apk", "splits/base-arm64_v8a.apk", "splits/base-x86.apk", "standalones/standalone-arm64_v8a.apk")
        val chon = GoiApk.chonPhan(goi, TV_ARM64)
        assertEquals(listOf("splits/base-master.apk", "splits/base-arm64_v8a.apk"), chon.map { it.muc })
        assertEquals("splits/base-master.apk", GoiApk.phanChinh(chon)?.muc)
        assertEquals(listOf("universal.apk"), GoiApk.chonPhan(p("toc.pb", "universal.apk"), TV_ARM64).map { it.muc })
    }

    /** Gói chỉ có bản x86 trên tivi ARM: giữ nguyên để Android báo NO_MATCHING_ABIS, không lặng lẽ bỏ hết. */
    @Test fun khongCoAbiHop_giuNguyen() {
        val goi = p("base.apk", "split_config.x86.apk")
        assertEquals(listOf("base.apk", "split_config.x86.apk"), GoiApk.chonPhan(goi, TV_ARM64).map { it.muc })
    }

    @Test fun loiCanGo() {
        assertTrue(GoiApk.canGoTruoc("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.a signatures do not match]"))
        assertTrue(GoiApk.canGoTruoc("Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"))
        assertFalse(GoiApk.canGoTruoc("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]"))
        assertFalse(GoiApk.canGoTruoc("Success"))
    }

    @Test fun giaiThich_maLaThiHienNguyenVan() {
        assertTrue(GoiApk.giaiThich("Failure [INSTALL_FAILED_OLDER_SDK: Requires newer sdk version #30 (current version is #28)]", 28).contains("28"))
        assertEquals("Failure [INSTALL_FAILED_WEIRD_NEW_CODE]", GoiApk.giaiThich("Failure [INSTALL_FAILED_WEIRD_NEW_CODE]\n", 30))
    }
}
