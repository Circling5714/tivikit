package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.tivikit.dulieu.DnsRieng.Loai

/** DNS riêng / NextDNS (25/10/2026). Mẫu dumpsys dựng theo LinkProperties / NetworkCapabilities.toString của AOSP. */
class DnsRiengTest {
    private val t: (String, String) -> String = { vi, _ -> vi }

    @Test fun hostNextDns_tenThietBi() {
        assertEquals("Skyworth-SWTV-200-P-Khach-abc123.dns.nextdns.io", DnsRieng.hostNextDns("abc123", "Skyworth SWTV-200 (P.Khách)"))
        assertEquals("abc123.dns.nextdns.io", DnsRieng.hostNextDns("abc123", "  "))
        val dai = DnsRieng.hostNextDns("abc123", "x".repeat(100))
        assertTrue(dai.substringBefore('.').length <= 63)
        assertEquals("abc123", DnsRieng.idNextDnsTu("Skyworth-SWTV-200-abc123.dns.nextdns.io"))
        assertEquals("abc123", DnsRieng.idNextDnsTu("abc123.dns.nextdns.io"))
    }

    @Test fun hostCho_kiemDinhDang() {
        assertEquals("Tivi-abc123.dns.nextdns.io", DnsRieng.hostCho(Loai.NEXTDNS, " ABC123 ", "Tivi", t))
        assertTrue(runCatching { DnsRieng.hostCho(Loai.NEXTDNS, "abc12", "", t) }.isFailure)
        assertEquals("p.example.com", DnsRieng.hostCho(Loai.TU_NHAP, "tls://p.example.com", "", t))
        assertTrue(runCatching { DnsRieng.hostCho(Loai.TU_NHAP, "https://dns.google/dns-query", "", t) }.isFailure)
        assertNull(DnsRieng.hostCho(Loai.TAT, "", "", t))
        assertEquals(Loai.NEXTDNS, DnsRieng.loaiCua("Tivi-abc123.dns.nextdns.io"))
        assertEquals(Loai.ADGUARD, DnsRieng.loaiCua("dns.adguard-dns.com"))
        assertEquals(Loai.TU_NHAP, DnsRieng.loaiCua("p.example.com"))
        assertEquals(Loai.TAT, DnsRieng.loaiCua("null".takeIf { false }))
    }

    @Test fun daXacNhan_android11() {
        val host = "tivi-abc123.dns.nextdns.io"
        val chua = "lp{{InterfaceName: wlan0 DnsAddresses: [ /192.168.1.1 ] UsePrivateDns: true PrivateDnsServerName: $host Domains: null}}"
        assertNull(DnsRieng.daXacNhan(chua, host))
        val tot = "lp{{InterfaceName: wlan0 UsePrivateDns: true PrivateDnsServerName: $host ValidatedPrivateDnsAddresses: [45.90.28.0,45.90.30.0] MTU: 0}}"
        assertEquals(true, DnsRieng.daXacNhan(tot, host))
        val hong = "$chua nc{[ Transports: WIFI Capabilities: INTERNET&NOT_RESTRICTED Private DNS is broken]}"
        assertEquals(false, DnsRieng.daXacNhan(hong, host))
        // Còn địa chỉ của máy chủ cũ nhưng tên đã đổi: chưa phải đã xác nhận máy chủ mới.
        assertNull(DnsRieng.daXacNhan("PrivateDnsServerName: dns.adguard-dns.com ValidatedPrivateDnsAddresses: [94.140.14.14]", host))
    }

    @Test fun daXacNhan_android9() {
        val host = "dns.adguard-dns.com"
        assertEquals(true, DnsRieng.daXacNhan("UsePrivateDns: true PrivateDnsServerName: $host ValidatedPrivateDnsAddresses: [94.140.14.14,94.140.15.15,] ", host))
        assertNull(DnsRieng.daXacNhan("UsePrivateDns: true PrivateDnsServerName: $host ", host))
        assertFalse(DnsRieng.daXacNhan("PrivateDnsServerName: x$host ValidatedPrivateDnsAddresses: [1.1.1.1]", host) == true)
    }
}
