package vn.tivikit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.tivikit.dulieu.BoCai
import vn.tivikit.dulieu.DnsRieng
import vn.tivikit.dulieu.GoiApk
import vn.tivikit.t

/** Màn Bộ cài đặt mẫu (25/10/2026): danh sách bộ · soạn bộ · cài cho nhiều tivi. */
@Composable
internal fun ManBoCai(vm: PhienLamViec) {
    val bc = vm.boCai
    when {
        bc.dangSua != null -> SoanBo(vm, bc)
        bc.chayBo != null -> NhieuTivi(vm, bc, bc.chayBo!!)
        else -> DanhSachBo(vm, bc)
    }
}

@Composable
private fun DanhSachBo(vm: PhienLamViec, bc: BoCaiMau) = Cuon {
    var hoiXoa by remember { mutableStateOf<BoCai?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Bộ cài đặt mẫu", "Setup sets"), style = KieuTieuDeLon)
        Phu(t("Lưu sẵn bộ app hay cài (kèm DNS chặn quảng cáo), gặp tivi mới chỉ cần một nút. Cài được cho tivi đang nối hoặc lần lượt nhiều tivi trong cùng mạng.",
            "Keep the apps you always install (plus ad-blocking DNS) as a set and apply it in one tap, to the connected TV or to several TVs on the network."))
    }
    if (bc.ds.isEmpty()) Phu(t("Chưa có bộ nào.", "No sets yet."))
    bc.ds.forEach { b ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OIcon(Icons.Rounded.Inventory2, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.ten, fontWeight = FontWeight.SemiBold)
                    Phu(t("${b.apps.size} app", "${b.apps.size} apps") + if (b.dnsLoai != DnsRieng.Loai.TAT) " · DNS " + tenDns(b.dnsLoai) else "")
                }
            }
            if (b.apps.isNotEmpty()) Phu(b.apps.joinToString(" · ") { it.ten }, ChuPhu)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.thongTin != null) ChonChip(t("Cài cho tivi đang nối", "Apply to this TV"), true) { if (!vm.dangBan) bc.apDungTvNay(b) }
                if (!vn.tivikit.ManHinh.laTv) ChonChip(t("Cài cho nhiều tivi", "Apply to several TVs"), false) { if (!vm.dangBan) bc.moNhieuTv(b) }
                ChonChip(t("Sửa", "Edit"), false) { if (!vm.dangBan) bc.sua(b) }
                ChonChip(t("Xoá", "Delete"), false) { if (!vm.dangBan) hoiXoa = b }
            }
        }
    }
    NutChinh(t("Tạo bộ mới", "New set"), enabled = !vm.dangBan, icon = Icons.Rounded.Add) { bc.taoMoi() }
    hoiXoa?.let { b ->
        AlertDialog(
            onDismissRequest = { hoiXoa = null },
            title = { Text(t("Xoá bộ ${b.ten}?", "Delete ${b.ten}?")) },
            text = { Text(t("Bản chép các APK trong bộ cũng bị xoá khỏi điện thoại (app đã cài trên tivi giữ nguyên).", "The APK copies in the set are removed from the phone (apps already on TVs stay).")) },
            confirmButton = { TextButton(onClick = { bc.xoa(b); hoiXoa = null }) { Text(t("Xoá", "Delete"), fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { hoiXoa = null }) { Text(t("Thôi", "Cancel")) } },
            containerColor = The1,
        )
    }
}

@Composable
private fun SoanBo(vm: PhienLamViec, bc: BoCaiMau) = Cuon {
    val chon = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) bc.themFile(uris) }
    Text(t("Soạn bộ cài", "Edit set"), style = KieuTieuDeLon)
    OutlinedTextField(bc.suaTen, { bc.suaTen = it.take(60) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
        label = { Text(t("Tên bộ (VD: Nhà khách hàng cơ bản)", "Set name")) })
    The(t("App trong bộ", "Apps in the set"), Icons.Rounded.Apps) {
        if (bc.suaApps.isEmpty()) Phu(t("Chưa có app. Thêm file .apk / .apks / .xapk / .apkm từ điện thoại.", "No apps yet. Add .apk / .apks / .xapk / .apkm files from the phone."))
        bc.suaApps.forEach { m ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.ten, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Phu(if (m.goiY.isNotEmpty()) t("Gợi ý · luôn tải bản mới nhất", "Suggested · always the latest build")
                        else listOfNotNull(m.versionName.takeIf { it.isNotBlank() }?.let { "v$it" }, coChu(m.co), m.pkg.takeIf { it.isNotBlank() }).joinToString(" · "))
                }
                IconButton(onClick = { bc.suaApps.remove(m) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.Close, t("Bỏ", "Remove")) }
            }
        }
        NutPhu(t("Thêm file APK", "Add APK files"), Icons.Rounded.Add, enabled = !vm.dangBan) { runCatching { chon.launch(GoiApk.KIEU_FILE) } }
        // App chỉ có trên CH Play không tải sẵn được: bộ cài chỉ nhận app tải thẳng.
        val conGoiY = vn.tivikit.dulieu.GoiY.DS.filter { g -> !g.laCHPlay && bc.suaApps.none { it.goiY == g.id } }
        if (conGoiY.isNotEmpty()) {
            Phu(t("Hoặc thêm app gợi ý (mỗi lần áp dụng tải bản mới nhất, đúng chip từng tivi):", "Or add a suggested app (the latest build for each TV's chip is downloaded when applied):"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conGoiY.forEach { g -> ChonChip("+ ${g.ten}", false) { bc.themGoiY(g) } }
            }
        }
    }
    The(t("DNS chặn quảng cáo", "Ad-blocking DNS"), Icons.Rounded.Shield) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DnsRieng.Loai.entries.forEach { l ->
                ChonChip(if (l == DnsRieng.Loai.TAT) t("Không đổi", "Leave as is") else tenDns(l), bc.suaDnsLoai == l) {
                    if (bc.suaDnsLoai != l) { bc.suaDnsLoai = l; bc.suaDnsGiaTri = "" }
                }
            }
        }
        when (bc.suaDnsLoai) {
            DnsRieng.Loai.NEXTDNS -> {
                OutlinedTextField(bc.suaDnsGiaTri, { bc.suaDnsGiaTri = it.trim().lowercase().take(6) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp), label = { Text(t("ID NextDNS (6 ký tự, VD abc123)", "NextDNS ID (6 chars, e.g. abc123)")) })
                Phu(t("Mỗi tivi mang tên riêng trong nhật ký NextDNS (tên tivi + số cuối IP).", "Each TV shows up under its own name in NextDNS logs (TV name + last IP number)."))
            }
            DnsRieng.Loai.TU_NHAP -> OutlinedTextField(bc.suaDnsGiaTri, { bc.suaDnsGiaTri = it.trim().take(253) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp), label = { Text(t("Tên máy chủ DNS-over-TLS", "DNS-over-TLS host name")) })
            else -> {}
        }
        Phu(t("Đặt xong tivi tự kiểm tra máy chủ, không dùng được thì trả DNS cũ. Cần Android 9 trở lên. Khôi phục tivi trả DNS như trước.",
            "The TV checks the server after it's set and gets its old DNS back if it fails. Needs Android 9+. Restoring the TV brings the old DNS back."))
    }
    NutChinh(t("Lưu bộ", "Save set"), enabled = !vm.dangBan, icon = Icons.Rounded.Save) { bc.luuSua() }
    NutPhu(t("Huỷ", "Cancel"), enabled = !vm.dangBan) { bc.huySua() }
}

@Composable
private fun NhieuTivi(vm: PhienLamViec, bc: BoCaiMau, b: BoCai) = Cuon {
    // Cài nhiều tivi có thể lâu: giữ màn sáng, tắt màn là app xuống nền và mất kết nối.
    val view = LocalView.current
    DisposableEffect(vm.dangBan) {
        view.keepScreenOn = vm.dangBan
        onDispose { view.keepScreenOn = false }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Cài ${b.ten} cho nhiều tivi", "Apply ${b.ten} to several TVs"), style = KieuTieuDeLon)
        Phu(t("Tivi phải bật Gỡ lỗi USB / Gỡ lỗi qua mạng (cổng 5555) và cùng Wi-Fi. Tivi chưa từng cho phép điện thoại này sẽ hiện hộp hỏi: bấm Cho phép trong 20 giây. Tivi chỉ có Gỡ lỗi không dây (ghép mã) thì làm riêng từng cái.",
            "TVs need USB / Network debugging (port 5555) on the same Wi-Fi. A TV that never allowed this phone will ask: press Allow within 20 seconds. TVs with only Wireless debugging (pairing code) must be done one by one."))
    }
    if (bc.ketQua.isEmpty()) {
        The(t("Chọn tivi", "Pick TVs"), Icons.Rounded.Tv) {
            val goiY = (vm.dsSaoLuu.map { it.ip to it.ten } + vm.timThay.map { it to "" }).filter { it.first.isNotBlank() }.distinctBy { it.first }
            goiY.forEach { (ip, ten) -> LuaChon(ip in bc.chonIp, ten.ifBlank { ip }, if (ten.isBlank()) null else ip) { bc.doiChonIp(ip) } }
            bc.chonIp.filter { ip -> goiY.none { it.first == ip } }.forEach { ip -> LuaChon(true, ip, t("gõ tay", "typed")) { bc.doiChonIp(ip) } }
            NutPhu(if (vm.dangQuet) t("Đang quét mạng…", "Scanning…") else t("Quét mạng tìm tivi (cổng 5555)", "Scan the network (port 5555)"), Icons.Rounded.WifiFind, enabled = !vm.dangQuet && !vm.dangBan) { vm.quetMang() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(bc.ipTay, { bc.ipTay = it.trim().take(21) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    label = { Text(t("IP tivi", "TV IP")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                TextButton(onClick = { bc.themIpTay() }) { Text(t("Thêm", "Add")) }
            }
        }
        NutChinh(t("Bắt đầu cài cho ${bc.chonIp.size} tivi", "Start on ${bc.chonIp.size} TV(s)"), enabled = !vm.dangBan && bc.chonIp.isNotEmpty(), icon = Icons.Rounded.PlayArrow) { bc.chayNhieuTv() }
    } else {
        bc.ketQua.forEach { kq ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (kq.xong) {
                        null -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        true -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = Xanh)
                        false -> Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = DoTuoi)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(kq.ten, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                kq.nhatKy.forEach { Phu(it) }
            }
        }
        if (!vm.dangBan) NutPhu(t("Xong", "Done"), Icons.Rounded.Check) { bc.quayLai() }
    }
}
