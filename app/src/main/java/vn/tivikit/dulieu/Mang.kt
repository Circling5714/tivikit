package vn.tivikit.dulieu

/**
 * Kiểm tra mạng tivi (25/10/2026): đọc đường ra Internet (`ip route get`), Wi-Fi đang nối (dòng mWifiInfo của
 * `dumpsys wifi`) và kết quả ping tới router / Internet / YouTube, rồi nhận xét. "YouTube giật" thường do Wi-Fi tới tivi
 * (sóng yếu, băng 2,4 GHz, nhiễu) chứ không phải tivi yếu.
 */
object Mang {
    /** Đường ra Internet: [dev] card mạng (wlan0, eth0…), [router] cổng ra, [ip] địa chỉ tivi. */
    data class Duong(val dev: String, val router: String?, val ip: String?)

    /** Wi-Fi đang nối: [rssi] dBm, [tocDo] Mbps, [tanSo] MHz, [chuan] "11ac"…; null = không đọc được. */
    data class Wifi(val ssid: String?, val rssi: Int?, val tocDo: Int?, val tanSo: Int?, val chuan: String?)

    /** Kết quả ping: gói gửi / nhận, thời gian trung bình và độ lệch (ms). */
    data class Ping(val gui: Int, val nhan: Int, val tb: Double?, val lech: Double?) {
        val matPhanTram: Int get() = if (gui <= 0) 100 else (gui - nhan) * 100 / gui
    }

    fun tuIpRoute(s: String): Duong? {
        val dev = Regex("""\bdev\s+(\S+)""").find(s)?.groupValues?.get(1) ?: return null
        val ip = Regex("""\b(via|src)\s+([0-9a-fA-F.:]+)""")
        return Duong(dev, ip.findAll(s).firstOrNull { it.groupValues[1] == "via" }?.groupValues?.get(2),
            ip.findAll(s).firstOrNull { it.groupValues[1] == "src" }?.groupValues?.get(2))
    }

    fun tuWifiInfo(s: String): Wifi? {
        val d = s.lineSequence().firstOrNull { it.contains("mWifiInfo") } ?: return null
        fun so(re: String) = Regex(re).find(d)?.groupValues?.get(1)?.toIntOrNull()
        val ssid = Regex("""mWifiInfo\s+SSID:\s*(?:"([^"]*)"|([^,]*))""").find(d)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] }.trim() }
            ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
        // "Link speed" đứng ngay sau dấu phẩy (khác "Tx Link speed", "Max Supported Tx Link speed"). -127 / -1 = chưa nối.
        val chuan = Regex("""Wi-Fi standard:\s*([^,]+)""").find(d)?.groupValues?.get(1)?.trim()?.let { c ->
            when (c) { "1" -> "legacy"; "4" -> "11n"; "5" -> "11ac"; "6" -> "11ax"; "7" -> "11ad"; "8" -> "11be"; else -> c }
        }
        return Wifi(ssid, so("""RSSI:\s*(-?\d+)""")?.takeIf { it in -126..0 }, so(""",\s*Link speed:\s*(-?\d+)\s*Mbps""")?.takeIf { it > 0 },
            so("""Frequency:\s*(-?\d+)\s*MHz""")?.takeIf { it > 0 }, chuan)
    }

    /** Dòng tổng kết của ping (iputils / toybox: có mdev; BusyBox: "round-trip min/avg/max"). */
    fun tuPing(s: String): Ping? {
        val m = Regex("""(\d+) packets transmitted, (\d+) (?:packets )?received""").find(s) ?: return null
        val rtt = Regex("""=\s*([\d.]+)/([\d.]+)/([\d.]+)(?:/([\d.]+))?\s*ms""").find(s)
        return Ping(m.groupValues[1].toInt(), m.groupValues[2].toInt(), rtt?.groupValues?.get(2)?.toDoubleOrNull(), rtt?.groupValues?.get(4)?.toDoubleOrNull())
    }

    /** Ping không đổi được tên ra địa chỉ (lỗi DNS). */
    fun loiTen(s: String): Boolean = Regex("unknown host|Name or service not known|bad address|No address associated", RegexOption.IGNORE_CASE).containsMatchIn(s)

    fun laLan(dev: String): Boolean = dev.startsWith("eth")

    enum class Song { RAT_TOT, TOT, TRUNG_BINH, YEU }

    fun song(rssi: Int): Song = when {
        rssi >= -55 -> Song.RAT_TOT
        rssi >= -67 -> Song.TOT
        rssi >= -75 -> Song.TRUNG_BINH
        else -> Song.YEU
    }

    enum class Bang { B24, B5, B6 }

    fun bang(tanSo: Int): Bang? = when (tanSo) {
        in 2400..2500 -> Bang.B24
        in 4900..5900 -> Bang.B5
        in 5925..7125 -> Bang.B6
        else -> null
    }

    /** Thế hệ Wi-Fi theo chuẩn: 11n = Wi-Fi 4, 11ac = 5, 11ax = 6, 11be = 7. */
    fun theHe(chuan: String?): Int? = when (chuan) { "11n" -> 4; "11ac" -> 5; "11ax" -> 6; "11be" -> 7; else -> null }

    enum class VanDe { MAT_MANG, LOI_DNS, SONG_YEU, SONG_VUA, BANG_24, TOC_DO_THAP, NHIEU_TRONG_NHA, MANG_CHAM, MAT_GOI, CHAP_CHON }

    /** Nhận xét theo thứ tự nặng trước; rỗng = mạng ổn. */
    fun danhGia(duong: Duong?, wifi: Wifi?, router: Ping?, net: Ping?, youtube: Ping?, loiTenYoutube: Boolean): List<VanDe> {
        val ds = mutableListOf<VanDe>()
        // Không tới được cả 1.1.1.1 lẫn YouTube: mất Internet (lỗi tên lúc này chỉ là hệ quả). Tới được 1.1.1.1 mà không
        // đổi được tên YouTube: lỗi DNS (thường do DNS riêng đặt sai).
        val raNet = (net != null && net.nhan > 0) || (youtube != null && youtube.nhan > 0)
        if (duong == null || !raNet) return listOf(VanDe.MAT_MANG)
        if (loiTenYoutube) ds += VanDe.LOI_DNS
        if (wifi != null && !laLan(duong.dev)) {
            wifi.rssi?.let { when (song(it)) { Song.YEU -> ds += VanDe.SONG_YEU; Song.TRUNG_BINH -> ds += VanDe.SONG_VUA; else -> {} } }
            if (wifi.tanSo?.let { bang(it) } == Bang.B24) ds += VanDe.BANG_24
            if (wifi.tocDo != null && wifi.tocDo < 50) ds += VanDe.TOC_DO_THAP
        }
        // Tới router đã chậm / rớt gói thì lỗi nằm ở Wi-Fi trong nhà: chậm / rớt / chập chờn ra Internet chỉ là hệ quả.
        val trongNha = router != null && (router.matPhanTram >= 3 || (router.tb ?: 0.0) > 30)
        if (trongNha) ds += VanDe.NHIEU_TRONG_NHA
        else if (net != null && net.nhan > 0) {
            if ((net.tb ?: 0.0) > 100) ds += VanDe.MANG_CHAM
            if (net.matPhanTram >= 3) ds += VanDe.MAT_GOI
            if ((net.lech ?: 0.0) > 30) ds += VanDe.CHAP_CHON
        }
        return ds
    }
}
