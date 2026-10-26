package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cập nhật app đã cài: đọc phiên bản bản mới từ tên file / tag, so với bản trên tivi. */
class PhienBanTest {
    @Test fun phienBanTuTenFile() {
        assertEquals("29.15", GoiY.phienBanTu("SmartTube_stable_29.15_arm64-v8a.apk", "29.15"))
        assertEquals("3.6.5", GoiY.phienBanTu("VLC-Android-3.6.5-arm64-v8a.apk", null))
        assertEquals("21.2", GoiY.phienBanTu("kodi-21.2-Omega-arm64-v8a.apk", null))
        assertEquals("3.0.422", GoiY.phienBanTu("SmartTV_twitch_3_0_422.apk", "422"))
        assertEquals("0.191", GoiY.phienBanTu("Just.Player.v0.191.apk", "v0.191"))
        assertEquals("1.17.0", GoiY.phienBanTu("LocalSend-1.17.0-android-arm64v8.apk", "v1.17.0"))
        // Tên file không có số (chỉ ABI): lấy tag.
        assertEquals("1.4.0", GoiY.phienBanTu("cobalt-arm64.apk", "v1.4.0"))
        assertNull(GoiY.phienBanTu("app-default-arm64-v8a-release.apk", "latest"))
    }

    @Test fun soSanh() {
        assertEquals(true, GoiY.moiHon("29.15", "29.12"))
        assertEquals(false, GoiY.moiHon("29.15", "29.15"))
        assertEquals(false, GoiY.moiHon("1.2", "1.2.0"))
        assertEquals(true, GoiY.moiHon("1.10.0", "1.9.9"))
        assertEquals(false, GoiY.moiHon("3.6.5", "3.6.5 Beta 1"))
        assertEquals(true, GoiY.moiHon("2025-05-17", "2024-11-21-release"))
        assertNull(GoiY.moiHon(null, "1.0"))
        assertNull(GoiY.moiHon("1.0", ""))
        assertEquals(listOf(2024, 11, 21), GoiY.soPhienBan("2024-11-21-release"))
        assertTrue(GoiY.moiHon("21.2", "20.5")!!)
        assertFalse(GoiY.moiHon("0.191", "0.192")!!)
    }
}
