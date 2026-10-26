package vn.tivikit.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Chỉ xét chủ của mạng Wi-Fi ĐANG dùng (23/10/2026, Sony VU3 còn lưu mạng cũ do setupwraith tạo). */
class WifiChuTest {
    private fun dump(dangDung: String) = """
mWifiInfo SSID: $dangDung, BSSID: 11:22:33:44:55:66, MAC: 02:00:00:00:00:00, IP: /192.168.1.50
WifiConfigManager - Configured networks Begin ----
ID: 0 SSID: "MangCu" PROVIDER-NAME: null BSSID: null
 Network selection status: NETWORK_SELECTION_ENABLED
 cuid=10080 cname=com.google.android.tungsten.setupwraith luid=10080 lname=com.google.android.tungsten.setupwraith
ID: 1 SSID: "NhaMinh" PROVIDER-NAME: null BSSID: null
 Network selection status: NETWORK_SELECTION_ENABLED
 cuid=1000 cname=com.android.tv.settings luid=1000 lname=com.android.tv.settings
WifiConfigManager - Configured networks End ----
""".trimIndent()

    @Test fun mangDangDung_quenRoiVaoLai_khongBiMangCuChan() =
        assertEquals("com.android.tv.settings", ThaoTacTv.chuWifiDangDung(dump("\"NhaMinh\"")))

    @Test fun mangDangDung_doSetupwraithTao() =
        assertEquals("com.google.android.tungsten.setupwraith", ThaoTacTv.chuWifiDangDung(dump("\"MangCu\"")))

    @Test fun khongDungWifi() = assertNull(ThaoTacTv.chuWifiDangDung(dump("<unknown ssid>")))

    /** Android ghi "* " trước dòng ID của mạng đang nối (máy ảo Android 14, 23/10/2026). */
    @Test fun dongIdCoDauSao() = assertEquals("com.android.tv.settings",
        ThaoTacTv.chuWifiDangDung(dump("\"NhaMinh\"").replace("ID: 1 SSID", "* ID: 1 SSID")))

    /** Cùng tên "NhaMinh" lưu hai lần (khác bảo mật), một của setupwraith: không đoán, trả "" để xét mọi mạng. */
    @Test fun trungTenHaiMang_khongDoan() = assertEquals("", ThaoTacTv.chuWifiDangDung(dump("\"NhaMinh\"").replace("ID: 0 SSID: \"MangCu\"", "ID: 0 SSID: \"NhaMinh\"")))

    @Test fun khongThayKhoi() = assertEquals("", ThaoTacTv.chuWifiDangDung(dump("\"Khac\"")))
}
