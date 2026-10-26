package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.DnsRieng
import vn.tivikit.dulieu.DnsRieng.Loai
import vn.tivikit.t

/** Tên hiện của một lựa chọn DNS (dùng cả ở Bộ cài đặt mẫu). */
internal fun tenDns(l: Loai): String = when (l) {
    Loai.TAT -> t("Tắt (DNS của router / nhà mạng)", "Off (router / ISP DNS)")
    Loai.ADGUARD -> "AdGuard"
    Loai.ADGUARD_GIA_DINH -> t("AdGuard Gia đình", "AdGuard Family")
    Loai.NEXTDNS -> "NextDNS"
    Loai.TU_NHAP -> t("Máy chủ tự nhập", "Custom server")
}

/** Màn DNS chặn quảng cáo (25/10/2026). */
@Composable
internal fun ManDns(vm: PhienLamViec) = Cuon {
    val d = vm.dnsTivi
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("DNS chặn quảng cáo", "Ad-blocking DNS"), style = KieuTieuDeLon)
        Phu(t("Đặt \"DNS riêng tư\" (DNS-over-TLS) cho tivi: chặn quảng cáo, theo dõi, web xấu trong mọi app. Là cài đặt của tivi, không cài thêm app nào.",
            "Sets the TV's Private DNS (DNS-over-TLS): blocks ads, trackers and bad sites in every app. It's a TV setting; nothing is installed."))
    }
    The(t("Tivi đang dùng", "The TV uses"), Icons.Rounded.Dns) {
        val h = d.dangDung
        Text(if (h == null) tenDns(Loai.TAT) else tenDns(DnsRieng.loaiCua(h)), fontWeight = FontWeight.SemiBold)
        if (h != null) Phu(h)
        when (d.xacNhan) {
            true -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = Xanh); Spacer(Modifier.width(6.dp))
                Text(t("Tivi đã kiểm tra: dùng được", "Checked by the TV: working"), fontSize = 13.sp, color = Xanh)
            }
            false -> Phu(t("Tivi báo máy chủ này đang hỏng.", "The TV reports this server as broken."), DoTuoi)
            null -> {}
        }
        if (h != null) TextButton(onClick = { d.kiemTraLai() }, enabled = !vm.dangBan) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Kiểm tra lại", "Check again"))
        }
    }
    if (!d.duocDung) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Phu(t("DNS riêng cần Android 9 trở lên; tivi này là Android ${vm.thongTin?.android}.", "Private DNS needs Android 9 or later; this TV runs Android ${vm.thongTin?.android}."), ChuChinh)
    }
    The(t("Chọn DNS", "Pick a DNS"), Icons.Rounded.Shield) {
        LuaChon(d.loai == Loai.TAT, tenDns(Loai.TAT), t("Như trước khi dùng app.", "As before using the app.")) { d.loai = Loai.TAT }
        LuaChon(d.loai == Loai.ADGUARD, tenDns(Loai.ADGUARD), t("Chặn quảng cáo, theo dõi. Miễn phí, không cần tài khoản.", "Blocks ads and trackers. Free, no account.")) { d.loai = Loai.ADGUARD }
        LuaChon(d.loai == Loai.ADGUARD_GIA_DINH, tenDns(Loai.ADGUARD_GIA_DINH), t("Như AdGuard, chặn thêm web người lớn, bật tìm kiếm an toàn.", "Like AdGuard, plus adult sites blocked and safe search.")) { d.loai = Loai.ADGUARD_GIA_DINH }
        LuaChon(d.loai == Loai.NEXTDNS, tenDns(Loai.NEXTDNS), t("Tự chọn danh sách chặn, xem nhật ký từng tivi. Cần tài khoản nextdns.io (gói miễn phí 300.000 truy vấn / tháng).",
            "Your own block lists and per-TV logs. Needs a nextdns.io account (free tier: 300,000 queries / month).")) { d.loai = Loai.NEXTDNS }
        if (d.loai == Loai.NEXTDNS) {
            OutlinedTextField(d.idNextDns, { d.idNextDns = it.trim().lowercase().take(6) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                label = { Text(t("ID cấu hình (6 ký tự, VD abc123)", "Configuration ID (6 chars, e.g. abc123)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.None))
            OutlinedTextField(d.tenNextDns, { d.tenNextDns = it.take(40) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                label = { Text(t("Tên tivi trong nhật ký NextDNS", "TV name in NextDNS logs")) })
            if (d.idNextDns.length == 6 && DnsRieng.idNextDnsHopLe(d.idNextDns)) Phu(DnsRieng.hostNextDns(d.idNextDns, d.tenNextDns))
            TextButton(onClick = { runCatching { uri.openUri("https://my.nextdns.io") } }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Mở my.nextdns.io lấy ID (mục Setup)", "Open my.nextdns.io for the ID (Setup)"))
            }
            Phu(t("NextDNS nhận cả ID gõ sai: khi đó tivi vẫn có mạng nhưng không chặn gì. Áp dụng xong, xem trang NextDNS → Logs có tên tivi này là đúng.",
                "NextDNS accepts a mistyped ID too: the TV stays online but nothing is blocked. After applying, check NextDNS → Logs shows this TV."))
        }
        LuaChon(d.loai == Loai.TU_NHAP, tenDns(Loai.TU_NHAP), t("Máy chủ DNS-over-TLS khác: Control D, Cloudflare…", "Another DNS-over-TLS server: Control D, Cloudflare…")) { d.loai = Loai.TU_NHAP }
        if (d.loai == Loai.TU_NHAP) OutlinedTextField(d.tuNhap, { d.tuNhap = it.trim().take(253) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(t("Tên máy chủ, VD family.cloudflare-dns.com", "Host name, e.g. family.cloudflare-dns.com")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    }
    NutChinh(t("Áp dụng lên tivi", "Apply to the TV"), enabled = !vm.dangBan && d.duocDung, icon = Icons.Rounded.Check) { d.apDung() }
    if (d.bao.isNotEmpty()) Phu(d.bao, Xanh)
    Phu(t("Áp dụng xong, app chờ tivi kiểm tra máy chủ DNS; tivi không dùng được thì tự trả lại DNS cũ để không mất mạng. Không chặn được quảng cáo trong YouTube. Nhà đã chặn ở router (Pi-hole, AdGuard Home, VPN router) thì để Tắt. Khôi phục tivi trả DNS như trước khi dùng app.",
        "After applying, the app waits for the TV to check the DNS server and puts the old DNS back if it fails, so the TV never goes offline. YouTube ads aren't blocked. If your router already filters (Pi-hole, AdGuard Home, router VPN), keep it Off. Restoring the TV puts the original DNS back."))
}
