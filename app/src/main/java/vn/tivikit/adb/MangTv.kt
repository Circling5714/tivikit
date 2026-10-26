package vn.tivikit.adb

import vn.tivikit.dulieu.ChPlay
import vn.tivikit.dulieu.Mang
import vn.tivikit.t

/**
 * Kết quả kiểm tra mạng: đường ra, Wi-Fi (nếu dùng), tốc độ cổng LAN (Mbps, nếu cắm dây), ping router / Internet / YouTube,
 * CH Play và tài khoản Google ([chPlay], null = không đọc được).
 */
data class KetQuaMang(
    val duong: Mang.Duong?, val wifi: Mang.Wifi?, val lanMbps: Int?,
    val router: Mang.Ping?, val net: Mang.Ping?, val youtube: Mang.Ping?, val loiTenYoutube: Boolean,
    val chPlay: ChPlay.TrangThai? = null,
) {
    val vanDe: List<Mang.VanDe> get() = Mang.danhGia(duong, wifi, router, net, youtube, loiTenYoutube)
}

/** Kiểm tra mạng tivi (25/10/2026): đọc kết nối và ping qua ADB. */
class MangTv(private val adb: KetNoiAdb) {
    private fun sh(lenh: String, giay: Int = 60) = adb.shell(lenh, giay).replace("\r", "")

    /** Mỗi đích 10 gói cách 0,2 giây (nhanh nhất shell được phép), chờ mỗi gói 1 giây: khoảng 2–3 giây. */
    private fun ping(dich: String) = sh("ping -c 10 -i 0.2 -W 1 $dich 2>&1", 40)

    fun kiemTra(bao: (String) -> Unit): KetQuaMang {
        bao(t("Đọc kết nối mạng của tivi…", "Reading the TV's network connection…"))
        val p = sh("ip route get 1.1.1.1 2>&1; echo @@; dumpsys wifi 2>/dev/null | grep -m 1 mWifiInfo").split("@@")
        val duong = Mang.tuIpRoute(p[0])
        val wifi = Mang.tuWifiInfo(p.getOrElse(1) { "" })
        val lan = duong?.dev?.takeIf { Mang.laLan(it) && Regex("^[A-Za-z0-9_.-]+$").matches(it) }
            ?.let { sh("cat /sys/class/net/$it/speed 2>/dev/null").trim().toIntOrNull()?.takeIf { s -> s > 0 } }
        val router = duong?.router?.takeIf { Regex("^[0-9a-fA-F.:]+$").matches(it) }?.let { r ->
            bao(t("Đo độ trễ tới router…", "Pinging the router…")); Mang.tuPing(ping(r))
        }
        bao(t("Đo độ trễ tới Internet…", "Pinging the Internet…"))
        val net = Mang.tuPing(ping("1.1.1.1"))
        bao(t("Đo độ trễ tới YouTube…", "Pinging YouTube…"))
        val yt = ping("www.youtube.com")
        bao(t("Xem CH Play và tài khoản Google…", "Checking the Play Store and Google account…"))
        val chPlay = runCatching { ChPlay.tuKetQua(sh(ChPlay.LENH), System.currentTimeMillis() / 1000) }.getOrNull()
        return KetQuaMang(duong, wifi, lan, router, net, Mang.tuPing(yt), Mang.loiTen(yt), chPlay)
    }
}
