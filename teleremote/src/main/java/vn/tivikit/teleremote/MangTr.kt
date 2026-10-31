package vn.tivikit.teleremote

/**
 * Kiểm tra mạng của tivi từ Telegram (1/11/2026), cùng cách với Kiểm tra mạng của TiviKit (dulieu/Mang.kt): đọc đường ra
 * Internet (`ip route get`), Wi-Fi đang nối (dòng mWifiInfo của `dumpsys wifi`), ping tới router / Internet / YouTube, rồi
 * nhận xét. "YouTube giật" thường do Wi-Fi tới tivi (sóng yếu, băng 2,4 GHz, nhiễu) chứ không phải tivi yếu.
 */
object MangTr {
    /** Đường ra Internet: [dev] card mạng (wlan0, eth0…), [router] cổng ra, [ip] địa chỉ tivi. */
    data class Duong(val dev: String, val router: String?, val ip: String?)

    /** Wi-Fi đang nối: [rssi] dBm, [tocDo] Mbps, [tanSo] MHz, [chuan] "11ac"…; null = không đọc được. */
    data class Wifi(val rssi: Int?, val tocDo: Int?, val tanSo: Int?, val chuan: String?)

    /** Kết quả ping: gói gửi / nhận, thời gian trung bình và độ lệch (ms). */
    data class Ping(val gui: Int, val nhan: Int, val tb: Double?, val lech: Double?) {
        val matPhanTram: Int get() = if (gui <= 0) 100 else (gui - nhan) * 100 / gui
    }

    /** Mọi số đo của một lần kiểm tra; [lan] = tốc độ dây LAN (Mbps) khi tivi cắm dây. */
    data class KetQua(val duong: Duong?, val wifi: Wifi?, val lan: Int?, val router: Ping?, val net: Ping?, val youtube: Ping?, val loiTenYoutube: Boolean)

    fun tuIpRoute(s: String): Duong? {
        val dev = Regex("""\bdev\s+(\S+)""").find(s)?.groupValues?.get(1) ?: return null
        val ip = Regex("""\b(via|src)\s+([0-9a-fA-F.:]+)""")
        return Duong(dev, ip.findAll(s).firstOrNull { it.groupValues[1] == "via" }?.groupValues?.get(2),
            ip.findAll(s).firstOrNull { it.groupValues[1] == "src" }?.groupValues?.get(2))
    }

    fun tuWifiInfo(s: String): Wifi? {
        val d = s.lineSequence().firstOrNull { it.contains("mWifiInfo") } ?: return null
        fun so(re: String) = Regex(re).find(d)?.groupValues?.get(1)?.toIntOrNull()
        // "Link speed" đứng ngay sau dấu phẩy (khác "Tx Link speed", "Max Supported Tx Link speed"). -127 / -1 = chưa nối.
        val chuan = Regex("""Wi-Fi standard:\s*([^,]+)""").find(d)?.groupValues?.get(1)?.trim()?.let { c ->
            when (c) { "1" -> "legacy"; "4" -> "11n"; "5" -> "11ac"; "6" -> "11ax"; "7" -> "11ad"; "8" -> "11be"; else -> c }
        }
        return Wifi(so("""RSSI:\s*(-?\d+)""")?.takeIf { it in -126..0 }, so(""",\s*Link speed:\s*(-?\d+)\s*Mbps""")?.takeIf { it > 0 },
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

    enum class VanDe { MAT_MANG, LOI_DNS, SONG_YEU, SONG_VUA, BANG_24, TOC_DO_THAP, NHIEU_TRONG_NHA, MANG_CHAM, MAT_GOI, CHAP_CHON }

    /** Nhận xét theo thứ tự nặng trước; rỗng = mạng ổn (như Mang.danhGia của TiviKit). */
    fun danhGia(kq: KetQua): List<VanDe> {
        val ds = mutableListOf<VanDe>()
        val (duong, wifi, _, router, net, youtube, loiTenYoutube) = kq
        // Không tới được cả 1.1.1.1 lẫn YouTube: mất Internet (lỗi tên lúc này chỉ là hệ quả).
        val raNet = (net != null && net.nhan > 0) || (youtube != null && youtube.nhan > 0)
        if (duong == null || !raNet) return listOf(VanDe.MAT_MANG)
        if (loiTenYoutube) ds += VanDe.LOI_DNS
        if (wifi != null && !laLan(duong.dev)) {
            wifi.rssi?.let { if (it < -75) ds += VanDe.SONG_YEU else if (it < -67) ds += VanDe.SONG_VUA }
            if (wifi.tanSo in 2400..2500) ds += VanDe.BANG_24
            if (wifi.tocDo != null && wifi.tocDo < 50) ds += VanDe.TOC_DO_THAP
        }
        // Tới router đã chậm / rớt gói thì lỗi nằm ở Wi-Fi trong nhà: chậm / rớt ra Internet chỉ là hệ quả.
        val trongNha = router != null && (router.matPhanTram >= 3 || (router.tb ?: 0.0) > 30)
        if (trongNha) ds += VanDe.NHIEU_TRONG_NHA
        else if (net != null && net.nhan > 0) {
            if ((net.tb ?: 0.0) > 100) ds += VanDe.MANG_CHAM
            if (net.matPhanTram >= 3) ds += VanDe.MAT_GOI
            if ((net.lech ?: 0.0) > 30) ds += VanDe.CHAP_CHON
        }
        return ds
    }

    private fun t(en: Boolean, vi: String, enS: String) = if (en) enS else vi

    /** Tên vấn đề và cách sửa (như màn Kiểm tra mạng của TiviKit). */
    fun loiKhuyen(v: VanDe, en: Boolean): Pair<String, String> = when (v) {
        VanDe.MAT_MANG -> t(en, "Tivi không ra được Internet", "The TV can't reach the Internet") to
            t(en, "Kiểm tra router còn mạng, tắt bật lại router và tivi. Nếu tivi đặt DNS riêng, thử tắt ở mục DNS chặn quảng cáo của TiviKit.",
                "Check the router has Internet, restart the router and the TV. If the TV uses a private DNS, try turning it off in TiviKit's Ad-blocking DNS.")
        VanDe.LOI_DNS -> t(en, "Lỗi DNS: tivi không tìm được địa chỉ YouTube", "DNS error: the TV can't look up YouTube") to
            t(en, "Thường do DNS riêng đặt sai hoặc máy chủ DNS đang hỏng: vào DNS chặn quảng cáo của TiviKit, chọn Tắt rồi đo lại.",
                "Usually a wrong private DNS or a broken DNS server: open TiviKit's Ad-blocking DNS, choose Off and measure again.")
        VanDe.SONG_YEU -> t(en, "Sóng Wi-Fi yếu", "Weak Wi-Fi signal") to
            t(en, "Video dễ giật, tụt nét. Dời router lại gần tivi, bỏ vật cản, thêm bộ phát Mesh, hoặc cắm dây LAN.",
                "Video will stutter or drop quality. Move the router closer, remove obstacles, add a mesh node, or use an Ethernet cable.")
        VanDe.SONG_VUA -> t(en, "Sóng Wi-Fi trung bình", "Average Wi-Fi signal") to
            t(en, "Xem thường ổn nhưng 4K có thể giật lúc mạng đông. Dời router gần hơn hoặc cắm dây LAN nếu được.",
                "Usually fine, but 4K may stutter when the network is busy. Move the router closer or use a cable if you can.")
        VanDe.BANG_24 -> t(en, "Tivi đang dùng Wi-Fi 2,4 GHz", "The TV is on 2.4 GHz Wi-Fi") to
            t(en, "Băng 2,4 GHz chậm và nhiễu. Nếu router có mạng 5 GHz (tên thường có đuôi 5G), cho tivi nối mạng đó.",
                "2.4 GHz is slow and crowded. If the router has a 5 GHz network (often named …5G), connect the TV to it.")
        VanDe.TOC_DO_THAP -> t(en, "Tốc độ kết nối Wi-Fi thấp", "Low Wi-Fi link speed") to
            t(en, "Dưới 50 Mbps giữa tivi và router: không đủ chắc cho 4K. Thường do sóng yếu hoặc băng 2,4 GHz.",
                "Under 50 Mbps between TV and router isn't enough headroom for 4K. Usually weak signal or 2.4 GHz.")
        VanDe.NHIEU_TRONG_NHA -> t(en, "Wi-Fi trong nhà chập chờn", "In-home Wi-Fi is unstable") to
            t(en, "Ngay tới router đã chậm hoặc rớt gói: lỗi ở Wi-Fi giữa tivi và router, không phải nhà mạng. Dời router, đổi kênh Wi-Fi, hoặc cắm dây.",
                "Even the router is slow or dropping packets: the problem is the Wi-Fi between TV and router. Move the router, change the Wi-Fi channel or use a cable.")
        VanDe.MANG_CHAM -> t(en, "Mạng ra Internet chậm", "Slow connection to the Internet") to
            t(en, "Wi-Fi trong nhà ổn nhưng ra ngoài chậm: có thể giờ cao điểm hoặc lỗi nhà mạng. Khởi động lại modem, báo nhà mạng nếu kéo dài.",
                "Home Wi-Fi is fine but the outside link is slow: maybe peak hours or a provider issue. Restart the modem; call your provider if it persists.")
        VanDe.MAT_GOI -> t(en, "Mạng ra Internet rớt gói", "Packet loss to the Internet") to
            t(en, "Video dễ dừng tải. Khởi động lại modem; vẫn bị thì báo nhà mạng kiểm tra đường dây.",
                "Video may pause to buffer. Restart the modem; if it continues, ask your provider to check the line.")
        VanDe.CHAP_CHON -> t(en, "Độ trễ dao động mạnh", "Latency jumps around") to
            t(en, "Có lúc nhanh lúc chậm, thường do máy khác đang tải nặng (camera, tải game, cập nhật). Thử lại khi mạng rảnh.",
                "Fast one moment, slow the next, often another device downloading heavily. Try again when the network is quiet.")
    }

    /** Chữ bot gửi: kết nối, độ trễ, rồi nhận xét (hay "mạng ổn"). */
    fun chu(kq: KetQua, tenTivi: String, en: Boolean): String {
        val dong = mutableListOf("🌐 ${t(en, "Mạng", "Network")} · $tenTivi")
        val w = kq.wifi
        when {
            kq.duong == null -> {}
            laLan(kq.duong.dev) -> dong += t(en, "Dây LAN", "Ethernet cable") + (kq.lan?.let { " · $it Mbps" } ?: "")
            w != null -> dong += listOfNotNull(
                "Wi-Fi" + when (w.tanSo) {
                    null -> ""
                    in 2400..2500 -> t(en, " 2,4 GHz", " 2.4 GHz")
                    in 4900..5900 -> " 5 GHz"
                    in 5925..7125 -> " 6 GHz"
                    else -> ""
                },
                w.rssi?.let { t(en, "sóng $it dBm", "signal $it dBm") + " (" + when {
                    it >= -55 -> t(en, "rất tốt", "excellent"); it >= -67 -> t(en, "tốt", "good"); it >= -75 -> t(en, "trung bình", "fair"); else -> t(en, "yếu", "weak")
                } + ")" },
                w.tocDo?.let { "$it Mbps" },
            ).joinToString(" · ")
        }
        fun ms(p: Ping?) = when {
            p == null -> "—"
            p.nhan == 0 -> t(en, "không tới", "unreachable")
            else -> "${p.tb?.toInt() ?: "?"} ms" + (if (p.matPhanTram > 0) t(en, ", mất ${p.matPhanTram}%", ", ${p.matPhanTram}% lost") else "")
        }
        dong += "Router: ${ms(kq.router)} · Internet: ${ms(kq.net)} · YouTube: ${if (kq.loiTenYoutube) t(en, "lỗi DNS", "DNS error") else ms(kq.youtube)}"
        val vanDe = danhGia(kq)
        if (vanDe.isEmpty()) dong += t(en, "✅ Mạng ổn, đủ cho video 4K.", "✅ The network is fine, good enough for 4K video.")
        else vanDe.forEach { v -> loiKhuyen(v, en).let { (ten, cach) -> dong += "⚠️ $ten: $cach" } }
        return dong.joinToString("\n")
    }
}
