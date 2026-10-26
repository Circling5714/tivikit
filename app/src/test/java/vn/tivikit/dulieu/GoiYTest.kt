package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chọn file gợi ý theo ABI / phiên bản và đặt tên file tải về (25/10/2026, dữ liệu lấy từ máy chủ thật). */
class GoiYTest {
    private val kodi = (GoiY.theoId("kodi")!!.nguon as GoiY.Nguon.ThuMuc).loc
    private val vlc = (GoiY.theoId("vlc")!!.nguon as GoiY.Nguon.ThuMuc).loc

    @Test fun kodi_banOnDinhMoiNhat() {
        val html = listOf("kodi-17.6-Krypton-arm64-v8a.apk", "kodi-21.0-Omega_rc1-arm64-v8a.apk", "kodi-21.3-Omega-arm64-v8a.apk",
            "kodi-21.2-Omega-arm64-v8a.apk", "kodi-22.0-Piers_rc1-arm64-v8a.apk", "kodi-22.0-Piers_beta2-arm64-v8a.apk")
            .joinToString("\n") { "<a href=\"$it\">$it</a>" }
        assertEquals("kodi-21.3-Omega-arm64-v8a.apk", GoiY.chonTrongThuMuc(html, kodi, "arm64-v8a"))
        assertNull(GoiY.chonTrongThuMuc(html, kodi, "armeabi-v7a"))
    }

    @Test fun vlc_dungAbi_x86KhongNhamX86_64() {
        val html = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").joinToString("\n") { "<a href=\"VLC-Android-3.7.1-$it.apk\">" }
        assertEquals("VLC-Android-3.7.1-armeabi-v7a.apk", GoiY.chonTrongThuMuc(html, vlc, "armeabi-v7a"))
        assertEquals("VLC-Android-3.7.1-x86.apk", GoiY.chonTrongThuMuc(html, vlc, "x86"))
    }

    @Test fun github_chonTheoAbi() {
        val tvbro = listOf("tvbro-2.1.6-generic-geckoIncluded-arm64-v8a.apk", "tvbro-2.1.6-generic-geckoIncluded-armeabi-v7a.apk")
        assertEquals("tvbro-2.1.6-generic-geckoIncluded-armeabi-v7a.apk", GoiY.chonTheoAbi(tvbro, listOf("armeabi-v7a", "armeabi")))
        assertEquals("jellyfin-androidtv-v0.19.10-release.apk", GoiY.chonTheoAbi(listOf("jellyfin-androidtv-v0.19.10-release.apk"), listOf("arm64-v8a")))
        assertNull(GoiY.chonTheoAbi(listOf("app-x86_64.apk"), listOf("arm64-v8a")))
    }

    private val arm64 = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
    private val armv7 = listOf("armeabi-v7a", "armeabi")
    private val x86 = listOf("x86")
    private fun gh(id: String) = GoiY.theoId(id)!!.nguon as GoiY.Nguon.GitHub

    @Test fun danhSach_hopLe() {
        val goi = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
        assertEquals(GoiY.DS.size, GoiY.DS.map { it.id }.toSet().size)
        GoiY.DS.forEach { g ->
            assertTrue(g.id, g.goi.isNotEmpty() && g.goi.all { goi.matches(it) })
            if (g.laCHPlay) assertEquals(g.id, 1, g.goi.size)
        }
        // Không gói nào thuộc về hai gợi ý (nút "đã cài" sẽ nhầm).
        assertEquals(GoiY.DS.sumOf { it.goi.size }, GoiY.DS.flatMap { it.goi }.toSet().size)
        assertTrue(GoiY.DS.any { it.id == "smarttube" && it.khongChinhThuc })
    }

    /** Tên file thật trong bản phát hành mới nhất trên GitHub (25/10/2026). */
    @Test fun github_chonDungFile() {
        val smarttube = listOf("SmartTube_stable_32.68_arm64-v8a.apk", "SmartTube_stable_32.68_armeabi-v7a.apk", "SmartTube_stable_32.68_universal.apk", "SmartTube_stable_32.68_x86.apk")
        assertEquals("SmartTube_stable_32.68_arm64-v8a.apk", GoiY.chonTep(gh("smarttube"), smarttube, arm64))
        assertEquals("SmartTube_stable_32.68_armeabi-v7a.apk", GoiY.chonTep(gh("smarttube"), smarttube, armv7))

        val tizen = listOf("cobalt-arm.apk", "cobalt-arm64.apk")
        assertEquals("cobalt-arm64.apk", GoiY.chonTep(gh("tizentube"), tizen, arm64))
        assertEquals("cobalt-arm.apk", GoiY.chonTep(gh("tizentube"), tizen, armv7))
        assertNull(GoiY.chonTep(gh("tizentube"), tizen, x86))

        val localsend = listOf("LocalSend-1.18.2-android-arm32v7.apk", "LocalSend-1.18.2-android-arm64v8.apk", "LocalSend-1.18.2-android-google-play.apk", "LocalSend-1.18.2-android-x64.apk")
        assertEquals("LocalSend-1.18.2-android-arm64v8.apk", GoiY.chonTep(gh("localsend"), localsend, arm64))
        assertEquals("LocalSend-1.18.2-android-arm32v7.apk", GoiY.chonTep(gh("localsend"), localsend, armv7))
        assertEquals("LocalSend-1.18.2-android-x64.apk", GoiY.chonTep(gh("localsend"), localsend, listOf("x86_64")))

        val mpv = listOf("app-allstorage-universal-debug.apk", "app-allstorage-universal-release.apk", "app-default-arm64-v8a-release.apk", "app-default-armeabi-v7a-release.apk",
            "app-default-universal-debug.apk", "app-default-universal-release.apk", "app-default-x86-release.apk", "app-default-x86_64-release.apk")
        assertEquals("app-default-arm64-v8a-release.apk", GoiY.chonTep(gh("mpv"), mpv, arm64))
        assertEquals("app-default-armeabi-v7a-release.apk", GoiY.chonTep(gh("mpv"), mpv, armv7))
        assertEquals("app-default-x86-release.apk", GoiY.chonTep(gh("mpv"), mpv, x86))

        val nova = listOf("arm64-v8a", "armeabi-v7a", "universal", "x86", "x86_64").map { "org.courville.nova-2672099-6.5.5-20261009.2113-$it-release.apk" }
        assertEquals(nova[0], GoiY.chonTep(gh("nova"), nova, arm64))
        assertEquals(nova[1], GoiY.chonTep(gh("nova"), nova, armv7))

        val flicky = listOf("arm64-v8a", "armeabi-v7a", "universal", "x86", "x86_64").map { "flicky-4.6.1-$it.apk" }
        assertEquals("flicky-4.6.1-armeabi-v7a.apk", GoiY.chonTep(gh("flicky"), flicky, armv7))

        assertEquals("Just.Player.v0.217.apk", GoiY.chonTep(gh("justplayer"), listOf("Just.Player.v0.217-legacy.apk", "Just.Player.v0.217.apk"), arm64))
        assertEquals("SmartTV_twitch_3_0_379.apk", GoiY.chonTep(gh("smarttwitch"), listOf("SmartTV_twitch_3_0_379.apk"), armv7))
        assertEquals("aerial-views-1.8.5.apk", GoiY.chonTep(gh("aerialviews"), listOf("aerial-views-1.8.5.apk"), arm64))
        assertEquals("lemuroid-app-free-dynamic-release.apk", GoiY.chonTep(gh("lemuroid"), listOf("lemuroid-app-free-dynamic-release.apk"), arm64))
    }

    @Test fun tenFile() {
        assertEquals("VLC-Android-3.7.1-arm64-v8a.apk", TaiLink.tenFile("https://get.videolan.org/vlc-android/last/VLC-Android-3.7.1-arm64-v8a.apk", null))
        assertEquals("app nho.apk", TaiLink.tenFile("https://x.org/tai?id=1", "attachment; filename=\"app nho.apk\""))
        assertEquals("Ứng dụng.apk", TaiLink.tenFile("https://x.org/a", "attachment; filename*=UTF-8''%E1%BB%A8ng%20d%E1%BB%A5ng.apk"))
        assertEquals("tai", TaiLink.tenFile("https://x.org/tai/?a=1", null))
    }
}
