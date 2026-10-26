package vn.tivikit.dulieu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Khối gói lõi giống hệt repo CleanYourTV: hai danh sách chép từ tests/test_core_packages.py (23/10/2026). */
class GoiLoiTest {
    private val CORE = listOf(
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.google.android.packageinstaller", "com.android.packageinstaller",
        "com.google.android.ext.services", "com.android.ext.services",
        "com.google.android.ext.shared", "com.android.ext.shared",
        "com.google.android.modulemetadata", "com.android.modulemetadata",
        "com.google.android.overlay.modules.ext.services", "com.google.android.overlay.modules.permissioncontroller",
        "com.android.systemui", "com.android.shell", "com.android.phone",
        "com.android.settings", "com.android.tv.settings",
        "com.android.providers.settings", "com.android.providers.tv", "com.android.providers.media",
        "com.android.providers.downloads", "android",
    )
    private val NOT_CORE = listOf(
        "com.android.providers.calendar", "com.android.providers.contacts",
        "com.android.providers.userdictionary", "com.android.providers.blockednumber",
        "com.android.settings.intelligence", "com.android.tv.settings.overlay", "com.android.phone.extra",
        "com.google.android.tungsten.setupwraith", "com.google.android.apps.tv.launcherx",
        "com.android.shell2", "com.example.android.ext.services",
    )

    @Test fun goiLoi_bichan() = CORE.forEach { assertTrue(it, KeHoach.laGoiLoiAndroid(it)) }
    @Test fun goiGiongTen_khongChan() = NOT_CORE.forEach { assertFalse(it, KeHoach.laGoiLoiAndroid(it)) }
}
