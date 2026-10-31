package vn.tivikit.teleremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangTrTest {
    private val wifiTx3 = "mWifiInfo SSID: \"Nha 5G\", BSSID: 02:00:00:00:00:00, MAC: 02:00:00:00:00:00, Supplicant state: COMPLETED, " +
        "RSSI: -58, Link speed: 433Mbps, Frequency: 5745MHz, Net ID: 1, Metered hint: false, score: 60"

    private fun ping(gui: Int, nhan: Int, tb: String) = "$gui packets transmitted, $nhan received, 0% packet loss, time 1800ms\n" +
        "rtt min/avg/max/mdev = 1.0/$tb/9.0/1.2 ms"

    @Test
    fun docKetNoi() {
        val d = MangTr.tuIpRoute("1.1.1.1 via 10.10.16.1 dev wlan0 src 10.10.17.104 uid 2000")!!
        assertEquals(MangTr.Duong("wlan0", "10.10.16.1", "10.10.17.104"), d)
        assertEquals(MangTr.Wifi(-58, 433, 5745, null), MangTr.tuWifiInfo(wifiTx3))
        assertEquals(MangTr.Ping(10, 9, 3.4, 1.2), MangTr.tuPing(ping(10, 9, "3.4")))
        assertEquals(10, MangTr.Ping(10, 9, 3.4, 1.2).matPhanTram)
        assertTrue(MangTr.loiTen("ping: unknown host www.youtube.com"))
    }

    @Test
    fun nhanXet() {
        val duong = MangTr.Duong("wlan0", "10.10.16.1", "10.10.17.104")
        val tot = MangTr.KetQua(duong, MangTr.Wifi(-58, 433, 5745, null), null, MangTr.Ping(10, 10, 3.0, 1.0),
            MangTr.Ping(10, 10, 25.0, 3.0), MangTr.Ping(10, 10, 28.0, 2.0), false)
        assertEquals(emptyList<MangTr.VanDe>(), MangTr.danhGia(tot))
        val chu = MangTr.chu(tot, "TX3 Mini", en = false)
        assertTrue(chu, chu.contains("Wi-Fi 5 GHz · sóng -58 dBm (tốt) · 433 Mbps"))
        assertTrue(chu, chu.contains("Router: 3 ms · Internet: 25 ms · YouTube: 28 ms"))
        assertTrue(chu, chu.contains("✅ Mạng ổn"))
        // Sóng yếu, 2,4 GHz, router chập chờn: lỗi ở Wi-Fi trong nhà, không kể chậm ra Internet.
        val te = tot.copy(wifi = MangTr.Wifi(-80, 30, 2437, null), router = MangTr.Ping(10, 8, 45.0, 20.0), net = MangTr.Ping(10, 8, 150.0, 50.0))
        assertEquals(listOf(MangTr.VanDe.SONG_YEU, MangTr.VanDe.BANG_24, MangTr.VanDe.TOC_DO_THAP, MangTr.VanDe.NHIEU_TRONG_NHA), MangTr.danhGia(te))
        assertTrue(MangTr.chu(te, "TV", en = true).contains("Wi-Fi 2.4 GHz"))
        // Không ra được Internet: chỉ báo mất mạng.
        assertEquals(listOf(MangTr.VanDe.MAT_MANG), MangTr.danhGia(tot.copy(net = MangTr.Ping(10, 0, null, null), youtube = null)))
        assertTrue(MangTr.chu(tot.copy(wifi = null, duong = duong.copy(dev = "eth0"), lan = 100), "TV", false).contains("Dây LAN · 100 Mbps"))
    }
}
