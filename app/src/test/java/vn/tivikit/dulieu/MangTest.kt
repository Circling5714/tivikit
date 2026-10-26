package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kiểm tra mạng tivi: đọc ip route, mWifiInfo, ping; nhận xét. */
class MangTest {
    @Test fun ipRoute() {
        assertEquals(Mang.Duong("wlan0", "192.168.1.1", "192.168.1.23"),
            Mang.tuIpRoute("1.1.1.1 via 192.168.1.1 dev wlan0 table 1021 src 192.168.1.23 uid 2000 \n    cache "))
        assertEquals(Mang.Duong("eth0", "10.0.0.1", "10.0.0.5"), Mang.tuIpRoute("1.1.1.1 via 10.0.0.1 dev eth0 src 10.0.0.5"))
        assertNull(Mang.tuIpRoute("RTNETLINK answers: Network is unreachable"))
    }

    @Test fun wifiAndroid11() {
        val d = "mWifiInfo SSID: \"Nha Minh\", BSSID: aa:bb:cc:dd:ee:ff, MAC: 02:00:00:00:00:00, IP: /192.168.1.23, Security type: 2, " +
            "Supplicant state: COMPLETED, Wi-Fi standard: 5, RSSI: -61, Link speed: 351Mbps, Tx Link speed: 351Mbps, " +
            "Max Supported Tx Link speed: 433Mbps, Rx Link speed: 263Mbps, Max Supported Rx Link speed: 433Mbps, Frequency: 5745MHz, Net ID: 0"
        assertEquals(Mang.Wifi("Nha Minh", -61, 351, 5745, "11ac"), Mang.tuWifiInfo(d))
    }

    @Test fun wifiAndroid9VaChuaNoi() {
        assertEquals(Mang.Wifi("NhaMinh", -72, 72, 2437, null),
            Mang.tuWifiInfo("mWifiInfo SSID: NhaMinh, BSSID: 11:22:33:44:55:66, MAC: 02:00:00:00:00:00, Supplicant state: COMPLETED, RSSI: -72, Link speed: 72Mbps, Frequency: 2437MHz, Net ID: 1"))
        assertEquals(Mang.Wifi(null, null, null, null, "11ax"),
            Mang.tuWifiInfo("mWifiInfo SSID: <unknown ssid>, BSSID: <none>, Supplicant state: DISCONNECTED, Wi-Fi standard: 11ax, RSSI: -127, Link speed: -1Mbps, Frequency: -1MHz"))
        assertNull(Mang.tuWifiInfo(""))
    }

    @Test fun ping() {
        val tot = "--- 1.1.1.1 ping statistics ---\n10 packets transmitted, 10 received, 0% packet loss, time 1809ms\nrtt min/avg/max/mdev = 11.931/14.218/22.457/2.993 ms\n"
        assertEquals(Mang.Ping(10, 10, 14.218, 2.993), Mang.tuPing(tot))
        assertEquals(Mang.Ping(10, 0, null, null), Mang.tuPing("10 packets transmitted, 0 received, 100% packet loss, time 9200ms\n"))
        // BusyBox: không có mdev.
        assertEquals(Mang.Ping(10, 9, 22.4, null), Mang.tuPing("10 packets transmitted, 9 packets received, 10% packet loss\nround-trip min/avg/max = 10.1/22.4/80.0 ms\n"))
        assertEquals(10, Mang.Ping(10, 9, null, null).matPhanTram)
        assertNull(Mang.tuPing("ping: unknown host www.youtube.com"))
        assertTrue(Mang.loiTen("ping: unknown host www.youtube.com"))
    }

    @Test fun mucSongVaBang() {
        assertEquals(Mang.Song.RAT_TOT, Mang.song(-50)); assertEquals(Mang.Song.TOT, Mang.song(-67))
        assertEquals(Mang.Song.TRUNG_BINH, Mang.song(-70)); assertEquals(Mang.Song.YEU, Mang.song(-80))
        assertEquals(Mang.Bang.B24, Mang.bang(2437)); assertEquals(Mang.Bang.B5, Mang.bang(5745)); assertEquals(Mang.Bang.B6, Mang.bang(5955))
        assertEquals(5, Mang.theHe("11ac"))
    }

    private val duong = Mang.Duong("wlan0", "192.168.1.1", "192.168.1.23")
    private val tot = Mang.Ping(10, 10, 14.0, 3.0)

    @Test fun danhGia() {
        assertEquals(emptyList<Mang.VanDe>(), Mang.danhGia(duong, Mang.Wifi("x", -55, 433, 5745, "11ac"), Mang.Ping(10, 10, 3.0, 1.0), tot, tot, false))
        assertEquals(listOf(Mang.VanDe.SONG_YEU, Mang.VanDe.BANG_24, Mang.VanDe.TOC_DO_THAP, Mang.VanDe.NHIEU_TRONG_NHA),
            Mang.danhGia(duong, Mang.Wifi("x", -80, 24, 2437, "11n"), Mang.Ping(10, 8, 45.0, 30.0), Mang.Ping(10, 7, 60.0, 40.0), tot, false))
        assertEquals(listOf(Mang.VanDe.MAT_MANG), Mang.danhGia(duong, null, Mang.Ping(10, 10, 3.0, 1.0), Mang.Ping(10, 0, null, null), null, true))
        assertEquals(listOf(Mang.VanDe.MAT_MANG), Mang.danhGia(null, null, null, null, null, false))
        assertEquals(listOf(Mang.VanDe.LOI_DNS), Mang.danhGia(Mang.Duong("eth0", "10.0.0.1", null), null, null, tot, null, true))
        assertEquals(listOf(Mang.VanDe.MANG_CHAM, Mang.VanDe.MAT_GOI, Mang.VanDe.CHAP_CHON),
            Mang.danhGia(Mang.Duong("eth0", "10.0.0.1", null), null, Mang.Ping(10, 10, 1.0, 0.2), Mang.Ping(10, 8, 150.0, 45.0), tot, false))
    }
}
