package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.dulieu.DnsRieng
import vn.tivikit.dulieu.DnsRieng.Loai
import vn.tivikit.t

/**
 * DNS chặn quảng cáo (25/10/2026): "DNS riêng tư" của tivi = DNS-over-TLS tới AdGuard, AdGuard Gia đình, NextDNS (ID
 * cấu hình + tên tivi để xem nhật ký từng máy) hoặc máy chủ tự nhập. Đặt qua bản sao lưu (Khôi phục trả DNS cũ), chờ
 * tivi kiểm tra, hỏng thì trả lại (ThaoTacTv.datDnsAnToan).
 */
class DnsTivi(private val vm: PhienLamViec) {
    /** Tên máy chủ DoT tivi đang dùng; null = DNS thường của router / nhà mạng. */
    var dangDung by mutableStateOf<String?>(null)
        private set
    /** Kết quả kiểm tra gần nhất: true dùng được, false hỏng, null chưa rõ / chưa kiểm. */
    var xacNhan by mutableStateOf<Boolean?>(null)
        private set
    var loai by mutableStateOf(Loai.ADGUARD)
    var idNextDns by mutableStateOf("")
    /** Tên tivi trong nhật ký NextDNS (đặt trước ID trong tên máy chủ). */
    var tenNextDns by mutableStateOf("")
    var tuNhap by mutableStateOf("")
    var bao by mutableStateOf("")

    /** Private DNS có từ Android 9 (API 28). */
    val duocDung: Boolean get() = (vm.thongTin?.sdk ?: 0) >= 28

    fun mo() = vm.chay(t("Đang đọc DNS của tivi…", "Reading the TV's DNS…")) {
        vm.damBaoKetNoi()
        bao = ""; xacNhan = null
        val host = vm.io { vm.tv.dnsRiengDangDung() }
        dangDung = host
        when (val l = DnsRieng.loaiCua(host)) {
            Loai.TAT -> {}
            Loai.NEXTDNS -> { loai = l; DnsRieng.idNextDnsTu(host!!)?.let { idNextDns = it } }
            Loai.TU_NHAP -> { loai = l; tuNhap = host!! }
            else -> loai = l
        }
        if (tenNextDns.isBlank()) tenNextDns = vm.thongTin?.tenHienThi ?: ""
        vm.man = Man.DNS
    }

    fun apDung() = vm.chay(t("Đang đặt DNS…", "Setting the DNS…")) {
        val info = vm.thongTin ?: return@chay
        if (!duocDung) error(t("DNS riêng (DNS-over-TLS) cần Android 9 trở lên; tivi này là Android ${info.android}.",
            "Private DNS (DNS-over-TLS) needs Android 9 or later; this TV runs Android ${info.android}."))
        val host = DnsRieng.hostCho(loai, if (loai == Loai.NEXTDNS) idNextDns else tuNhap, tenNextDns, ::t)
        vm.damBaoKetNoi()
        bao = ""; xacNhan = null
        // Sao lưu trước khi đổi: Khôi phục tivi trả đúng DNS như lúc chưa dùng app.
        val sl = vm.saoLuu ?: vm.io { vm.tv.damBaoSaoLuu(info, vm.getprop, vm.ip.trim()) }.also { vm.saoLuu = it }
        if (host == null) {
            vm.io { vm.tv.tatDnsChanQc(sl) }
            dangDung = vm.io { vm.tv.dnsRiengDangDung() }
            vm.dnsChanQc = false
            bao = t("Đã tắt DNS riêng: tivi dùng lại DNS như trước khi dùng app.", "Private DNS off: the TV is back to its DNS from before.")
            return@chay
        }
        vm.thongBao = t("Đang đặt $host, chờ tivi kiểm tra (tối đa 20 giây)…", "Setting $host, waiting for the TV to check it (up to 20 s)…")
        val kq = vm.io { vm.tv.datDnsAnToan(sl, host) }
        dangDung = vm.io { vm.tv.dnsRiengDangDung() }
        vm.dnsChanQc = dangDung.equals(DnsRieng.ADGUARD, ignoreCase = true)
        if (kq == false) error(t("Tivi không dùng được $host (sai tên máy chủ, hoặc mạng chặn DNS-over-TLS cổng 853). Đã trả lại DNS cũ, tivi vẫn có mạng.",
            "The TV can't use $host (wrong host name, or DNS-over-TLS port 853 is blocked). The old DNS is back; the TV stays online."))
        xacNhan = kq
        bao = (if (kq == true) t("Đã bật. Tivi đã kiểm tra máy chủ DNS: dùng được.", "On. The TV checked the DNS server: it works.")
            else t("Đã đặt, nhưng tivi chưa báo kết quả kiểm tra. Mở thử một app trên tivi: báo mất mạng thì chọn Tắt rồi Áp dụng.",
                "Set, but the TV hasn't reported its check yet. Open an app on the TV: if it says offline, choose Off and apply.")) +
            if (loai == Loai.NEXTDNS) t(" Mở my.nextdns.io → Logs: thấy tivi này là đúng ID.", " Open my.nextdns.io → Logs: seeing this TV there means the ID is right.") else ""
    }

    fun kiemTraLai() = vm.chay(t("Đang kiểm tra DNS…", "Checking the DNS…")) {
        val host = dangDung ?: return@chay
        vm.damBaoKetNoi()
        xacNhan = vm.io { vm.tv.kiemTraDns(host, giay = 8) }
        bao = when (xacNhan) {
            true -> t("Tivi dùng được $host.", "The TV can use $host.")
            false -> t("Tivi báo $host đang hỏng: chọn DNS khác hoặc Tắt rồi Áp dụng.", "The TV reports $host as broken: pick another DNS or Off and apply.")
            null -> t("Chưa có kết quả. Mở thử một app trên tivi xem có mạng không.", "No result yet. Open an app on the TV to see if it's online.")
        }
    }
}
