package vn.tivikit.dulieu

import vn.tivikit.adb.PhimTv

/**
 * DNS riêng của tivi (25/10/2026): "DNS riêng tư" (Private DNS) của Android 9+ chính là DNS-over-TLS tới một tên máy chủ.
 * Chế độ chỉ định tên máy chủ là bắt buộc: máy chủ sai / mạng chặn cổng 853 thì tivi mất DNS (mất mạng) — nên đặt xong
 * phải chờ Android xác nhận, hỏng thì trả lại DNS cũ (ThaoTacTv.datDnsAnToan).
 */
object DnsRieng {
    enum class Loai { TAT, ADGUARD, ADGUARD_GIA_DINH, NEXTDNS, TU_NHAP }

    const val ADGUARD = "dns.adguard-dns.com"
    const val ADGUARD_GIA_DINH = "family.adguard-dns.com"
    private const val DUOI_NEXTDNS = ".dns.nextdns.io"

    /** ID cấu hình NextDNS (my.nextdns.io → Setup, VD abc123). NextDNS nhận mọi chuỗi nên chỉ chặn được gõ sai định dạng. */
    fun idNextDnsHopLe(id: String) = Regex("^[0-9a-z]{6}$").matches(id)

    /**
     * Tên máy chủ DoT NextDNS. [tenThietBi] đặt trước ID để nhật ký NextDNS phân biệt từng tivi ("Ten-abc123.dns…"):
     * bỏ dấu, ký tự khác chữ / số thành một "-" (không dùng "--" cho dấu cách như NextDNS gợi ý: nhãn kiểu "ab--cd" là
     * dạng dành riêng của IDN). Nhãn DNS tối đa 63 ký tự nên tên cắt còn 56.
     */
    fun hostNextDns(id: String, tenThietBi: String = ""): String {
        val ten = PhimTv.boDau(tenThietBi).replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(56).trimEnd('-')
        return (if (ten.isEmpty()) id else "$ten-$id") + DUOI_NEXTDNS
    }

    /** ID NextDNS trong tên máy chủ ("Ten-abc123.dns.nextdns.io" → "abc123"). */
    fun idNextDnsTu(host: String): String? =
        host.lowercase().removeSuffix(DUOI_NEXTDNS).substringAfterLast('-').takeIf { idNextDnsHopLe(it) }

    /** Tên máy chủ DoT hợp lệ (tự nhập): tên miền có ít nhất một dấu chấm, chỉ chữ / số / "-". */
    fun hostHopLe(h: String) =
        h.length in 4..253 && Regex("""^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$""").matches(h)

    fun loaiCua(host: String?): Loai = when {
        host.isNullOrBlank() -> Loai.TAT
        host.equals(ADGUARD, true) -> Loai.ADGUARD
        host.equals(ADGUARD_GIA_DINH, true) -> Loai.ADGUARD_GIA_DINH
        host.endsWith(DUOI_NEXTDNS, true) -> Loai.NEXTDNS
        else -> Loai.TU_NHAP
    }

    /**
     * Tên máy chủ cho một lựa chọn; [giaTri] = ID NextDNS hoặc tên máy chủ tự nhập. null = Tắt. Sai định dạng thì báo lỗi
     * (chữ hiện cho người dùng).
     */
    fun hostCho(loai: Loai, giaTri: String, tenThietBi: String, t: (String, String) -> String): String? = when (loai) {
        Loai.TAT -> null
        Loai.ADGUARD -> ADGUARD
        Loai.ADGUARD_GIA_DINH -> ADGUARD_GIA_DINH
        Loai.NEXTDNS -> giaTri.trim().lowercase().let { id ->
            require(idNextDnsHopLe(id)) { t("ID NextDNS gồm 6 chữ / số (xem my.nextdns.io → Setup), VD abc123.", "A NextDNS ID is 6 letters / digits (my.nextdns.io → Setup), e.g. abc123.") }
            hostNextDns(id, tenThietBi)
        }
        Loai.TU_NHAP -> giaTri.trim().lowercase().removePrefix("tls://").also {
            require(hostHopLe(it)) { t("Nhập tên máy chủ DNS-over-TLS, VD dns.example.com (không có https:// hay số cổng).", "Enter a DNS-over-TLS host name such as dns.example.com (no https:// or port).") }
        }
    }

    /**
     * Android đã kiểm tra máy chủ DoT [host] chưa, đọc từ `dumpsys connectivity`. LinkProperties in
     * "PrivateDnsServerName: host" và, khi đã kiểm xong, "ValidatedPrivateDnsAddresses: [ip…]" (Android 9: "ip,ip,";
     * Android 11: "ip,ip"). NetworkCapabilities in " Private DNS is broken" khi hỏng — chỉ từ Android 11.
     * true dùng được, false hỏng, null chưa rõ (đang kiểm, hoặc Android 9–10 không báo hỏng).
     */
    fun daXacNhan(dump: String, host: String): Boolean? {
        val coTen = Regex("""PrivateDnsServerName:\s*${Regex.escape(host)}(\s|$)""", RegexOption.IGNORE_CASE).containsMatchIn(dump)
        val coDiaChi = Regex("""ValidatedPrivateDnsAddresses: \[\s*[0-9A-Fa-f:.]""").containsMatchIn(dump)
        return when {
            coTen && coDiaChi -> true
            Regex("""Private DNS is broken""", RegexOption.IGNORE_CASE).containsMatchIn(dump) -> false
            else -> null
        }
    }
}
