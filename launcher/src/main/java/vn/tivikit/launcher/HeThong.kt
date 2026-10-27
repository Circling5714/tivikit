package vn.tivikit.launcher

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

/** RAM, bộ nhớ, mạng của tivi và nút Dọn RAM. */
object HeThong {
    fun ramTrong(ctx: Context): Long = runCatching {
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        mi.availMem
    }.getOrDefault(0L)

    fun boNhoTrong(): Long = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L)

    /** Tổng byte đã nhận / gửi từ lúc bật máy; -1 nếu tivi không đếm. */
    fun byteXuong(): Long = TrafficStats.getTotalRxBytes()
    fun byteLen(): Long = TrafficStats.getTotalTxBytes()

    enum class LoaiMang { WIFI, DAY, KHAC, KHONG }

    /** [ten] = tên Wi-Fi, chỉ có khi TiviKit đã cấp quyền vị trí (Android chỉ cho app có quyền đó đọc tên Wi-Fi). */
    data class Mang(val loai: LoaiMang, val ten: String? = null)

    fun mang(ctx: Context): Mang {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return Mang(LoaiMang.KHONG)
        val nc = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return Mang(LoaiMang.KHONG)
        return when {
            nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Mang(LoaiMang.WIFI, tenWifi(ctx))
            nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Mang(LoaiMang.DAY)
            else -> Mang(LoaiMang.KHAC)
        }
    }

    @Suppress("DEPRECATION")
    private fun tenWifi(ctx: Context): String? = runCatching {
        ctx.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
    }.getOrNull()?.let(::tenSsid)

    /** Bỏ ngoặc kép quanh tên; "<unknown ssid>" (chưa có quyền) và tên rỗng thành null. */
    fun tenSsid(s: String): String? = s.trim().removeSurrounding("\"").takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }

    /** Android 14 trở đi không cho app đóng tiến trình nền của app khác: nút Dọn RAM ẩn đi. */
    fun donDuocRam() = Build.VERSION.SDK_INT < 34

    /** Gói không bao giờ đóng: chính launcher, TiviKit và các app phụ của nó (Khoá trẻ em, Hẹn giờ…), Home sạch. */
    fun giuLai(goi: String, minh: String) =
        goi == minh || goi == "vn.tivikit" || goi.startsWith("vn.tivikit.") || goi == "com.gathin.tvhome" || goi == "com.ts.launcher"

    /** Đóng tiến trình nền (không đóng app đang mở hay đang phát nhạc nền) của các gói [goi]. */
    fun donRam(ctx: Context, goi: Collection<String>) {
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return
        for (g in goi) runCatching { am.killBackgroundProcesses(g) }
    }
}
