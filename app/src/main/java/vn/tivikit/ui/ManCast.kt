package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.CastSmartTube
import vn.tivikit.t

/** Màn Cast SmartTube (28/10/2026): app YouTube trên điện thoại cùng Wi-Fi thấy tivi trong nút Cast, video mở bằng SmartTube. */
@Composable
internal fun ManCast(vm: PhienLamViec) = Cuon {
    val c = vm.cast
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Cast SmartTube", "Cast to SmartTube"), style = KieuTieuDeLon)
        Phu(t("App YouTube trên điện thoại (iPhone hay Android) cùng Wi-Fi với tivi thấy tivi trong nút Cast như YouTube gốc, thường không cần liên kết bằng mã TV. Video gửi sang mở bằng SmartTube; tạm dừng, tua, đổi âm lượng trên điện thoại được. SmartTube đang nghe nền (đã bấm Home) thì video mới cũng về nghe nền.",
            "The YouTube app on any phone (iPhone or Android) on the same Wi-Fi finds the TV under the Cast button, as with the official app, usually with no TV code. What you send plays in SmartTube; pause, seek and volume work from the phone. If SmartTube is playing in the background (after Home), the new video goes back to the background too."))
    }
    if (!c.duocCai) CanhBao(t("Cast SmartTube cần Android 8 trở lên; tivi này là Android ${vm.thongTin?.android}.",
        "Cast to SmartTube needs Android 8 or later; this TV runs Android ${vm.thongTin?.android}."))
    if (!c.coSmartTube) {
        CanhBao(t("Tivi chưa có SmartTube: video sẽ mở bằng app YouTube nào có trên tivi. Cài SmartTube ở Cài app → Gợi ý.",
            "SmartTube isn't on this TV: videos would open in whatever YouTube app the TV has. Install SmartTube from Install apps → Suggestions."))
        NutPhu(t("Mở Cài app", "Open Install apps"), Icons.Rounded.AddToQueue, enabled = !vm.dangBan) { vm.moCaiApk() }
    }

    c.trangThai?.let { tt ->
        The(t("Trên tivi", "On the TV"), Icons.Rounded.Cast) {
            Dong(tt.dichVuChay, t("Dịch vụ Cast đang chạy", "The Cast service is running"),
                t("Dịch vụ Cast đang tắt: bấm Bật lại bên dưới", "The Cast service is off: tap Turn on again below"))
            if (tt.dichVuChay) Dong(tt.daNoi, t("Đã nối YouTube, điện thoại thấy tivi", "Connected to YouTube; phones can find the TV"),
                t("Chưa nối được YouTube (tivi có vào mạng không?)", "Not connected to YouTube yet (is the TV online?)"))
            if (tt.ten.isNotEmpty()) Text(t("Tên trong nút Cast: ", "Name under Cast: ") + tt.ten, fontWeight = FontWeight.SemiBold)
            if (tt.dienThoai > 0) Phu(t("${tt.dienThoai} điện thoại đang nối", "${tt.dienThoai} phone(s) connected"))
            if (tt.nhatKy.isNotEmpty()) Gap(t("Việc gần đây", "Recent activity"), Icons.Rounded.History) {
                tt.nhatKy.takeLast(10).reversed().forEach { n -> Phu("${vn.tivikit.dulieu.HenGio.gioCua(n.luc, java.time.ZoneId.systemDefault()).first} · ${n.viec}") }
            }
            TextButton(onClick = { c.lamMoi() }, enabled = !vm.dangBan) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Làm mới", "Refresh"))
            }
        }
    }

    c.trangThai?.takeIf { it.dichVuChay }?.let { tt ->
        The(t("Liên kết bằng mã TV", "Link with a TV code"), Icons.Rounded.Pin) {
            if (tt.biChe) CanhBao(t("Tivi này có sẵn Cast của hãng trả lời trước, nên app YouTube trên điện thoại không thấy \"${tt.ten}\" qua Wi-Fi. Liên kết bằng mã TV một lần là xong.",
                "This TV has its maker's own Cast, which answers first, so the YouTube app on phones won't find \"${tt.ten}\" over Wi-Fi. Link it once with a TV code."))
            else Phu(t("Điện thoại không thấy \"${tt.ten}\" trong nút Cast? Liên kết bằng mã TV, chỉ cần một lần.",
                "A phone doesn't see \"${tt.ten}\" under Cast? Link it with a TV code, just once."))
            if (c.maTv.isNotEmpty()) {
                Text(CastSmartTube.hienMa(c.maTv), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Phu(t("Mã lấy lúc ${vn.tivikit.dulieu.HenGio.gioCua(c.maTvLuc, java.time.ZoneId.systemDefault()).first}, dùng được chừng 5 phút.",
                    "Code from ${vn.tivikit.dulieu.HenGio.gioCua(c.maTvLuc, java.time.ZoneId.systemDefault()).first}; it works for about 5 minutes."))
            }
            NutPhu(if (c.maTv.isEmpty()) t("Lấy mã TV", "Get a TV code") else t("Lấy mã mới", "Get a new code"), Icons.Rounded.Pin,
                enabled = !vm.dangBan && tt.daNoi) { c.layMaTv() }
            Phu(t("Trên điện thoại: YouTube → nút Cast → Thêm thiết bị / Liên kết bằng mã TV (hoặc Bạn → Cài đặt → Xem trên TV → Nhập mã TV), gõ mã ở trên. Xong thì \"${tt.ten}\" luôn có trong nút Cast, không cần mở SmartTube trước.",
                "On the phone: YouTube → Cast button → Add device / Link with TV code (or You → Settings → Watch on TV → Enter TV code), then type the code above. After that \"${tt.ten}\" is always under Cast, with no need to open SmartTube first."))
            if (tt.coQuanLy) Phu(t("Không cầm điện thoại có TiviKit: mở app \"Cast SmartTube\" trong danh sách app của tivi, mã TV hiện chữ to ngay trên tivi.",
                "Without this phone: open the \"Cast SmartTube\" app from the TV's app list; the TV code shows in large type on the TV."))
        }
    }

    c.trangThai?.takeIf { it.dichVuChay }?.let { tt -> TheDienThoai(vm, c, tt) }

    The(t("Tên hiện trong nút Cast", "Name under the Cast button"), Icons.Rounded.Edit) {
        OutlinedTextField(c.ten, { c.ten = it.take(CastSmartTube.TEN_TOI_DA) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp), label = { Text(t("VD SmartTube phòng khách", "e.g. SmartTube living room")) })
        Phu(t("Để trống thì dùng \"SmartTube · <tên tivi>\".", "Leave it empty for \"SmartTube · <TV name>\"."))
        if (c.daCai) NutPhu(t("Lưu tên", "Save the name"), Icons.Rounded.Check, enabled = !vm.dangBan) { c.luuTen() }
    }

    val canBat = !c.daCai || c.trangThai?.dichVuChay != true
    NutChinh(if (!c.daCai) t("Bật Cast SmartTube", "Turn on Cast to SmartTube") else if (canBat) t("Bật lại", "Turn on again") else t("Cài lại / cập nhật", "Reinstall / update"),
        enabled = !vm.dangBan && c.duocCai, icon = Icons.Rounded.Cast) { c.bat() }
    if (c.bao.isNotEmpty()) Phu(c.bao, Xanh)

    The(t("Cách dùng", "How to use"), Icons.Rounded.PhoneIphone) {
        Phu(t("1. Điện thoại nối cùng Wi-Fi với tivi (cùng mạng; Wi-Fi khách / mạng khác thì không thấy).", "1. Put the phone on the same Wi-Fi as the TV (a guest network or another network won't see it)."))
        Phu(t("2. Mở YouTube, bấm nút Cast (hình màn hình có sóng), chọn tên tivi ở trên.", "2. Open YouTube, tap the Cast button (screen with waves) and pick the name above."))
        Phu(t("3. Chọn video: tivi tự tắt màn bảo vệ và mở video bằng SmartTube.", "3. Pick a video: the TV leaves its screensaver and plays it in SmartTube."))
        Phu(t("Tivi đang tắt (chế độ chờ) thì phải bật bằng remote trước. Điện thoại cũ đã liên kết bằng mã TV vẫn dùng được như trước.",
            "A TV in standby must be turned on with the remote first. Phones linked earlier with a TV code keep working as before."))
    }

    if (c.daCai) TextButton(onClick = { c.go() }, enabled = !vm.dangBan) {
        Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Gỡ Cast SmartTube khỏi tivi", "Remove Cast to SmartTube from the TV"))
    }
    Phu(t("Bật sẽ cài app nhỏ \"Cast SmartTube (TiviKit)\", bật dịch vụ trợ năng của nó (để chạy suốt, được mở SmartTube và biết app nào đang hiện để SmartTube đang nghe nền thì giữ nghe nền; không đọc nội dung màn hình, không bắt phím), cho nó đọc thống kê dùng app (cũng để biết app nào đang hiện) và phiên phát của SmartTube (để đồng bộ khi bấm remote tivi và biết tên bài; tivi ít RAM không cho thì bỏ qua). App nói chuyện với máy chủ YouTube như app YouTube trên tivi (giao thức không công khai của YouTube: YouTube đổi thì có thể hỏng tới khi TiviKit cập nhật). Ai cùng Wi-Fi cũng cast được, như Chromecast. Gỡ hoặc Khôi phục tivi là xoá hết.",
        "Turning it on installs the small \"Cast SmartTube (TiviKit)\" app, turns on its accessibility service (to keep running, be allowed to open SmartTube and know which app is showing, so SmartTube playing in the background stays there; it doesn't read what's on screen or catch keys) and lets it read app usage stats (also to know which app is showing) and SmartTube's playback session (to stay in sync when you use the TV remote and to know the title; skipped on low-RAM TVs that don't allow it). It talks to YouTube's servers the way the YouTube TV app does (YouTube's private protocol: if YouTube changes it, this may break until TiviKit is updated). Anyone on the same Wi-Fi can cast, as with a Chromecast. Remove or Restore deletes everything."))
}

/**
 * Điện thoại đã cast (app Cast 2.0 trên tivi, 28/10/2026): duyệt, khách có hạn, chặn, xoá; tạm ngừng nhận cast; huỷ mọi
 * liên kết. App Cast bản cũ thì nhắc cập nhật.
 */
@Composable
private fun TheDienThoai(vm: PhienLamViec, c: CastTivi, tt: CastSmartTube.TrangThai) = The(t("Điện thoại đã cast", "Phones that cast"), Icons.Rounded.PhoneIphone) {
    if (!tt.coQuanLy) {
        Phu(t("App Cast trên tivi là bản cũ: bấm Cài lại / cập nhật bên dưới để có danh sách điện thoại, duyệt khách, chặn và màn mã TV trên tivi.",
            "The Cast app on the TV is outdated: tap Reinstall / update below to get the phone list, guest approval, blocking and the on-TV code screen."))
        return@The
    }
    Phu(t("Máy lạ cast lần đầu thì tivi hỏi trước khi phát (cho phép luôn, tới hết hôm nay, 24 giờ hay chặn). YouTube không cho tivi biết mọi máy đã liên kết, chỉ biết máy đã nối vào để cast.",
        "A new phone casting for the first time is asked about on the TV first (allow always, until the end of today, 24 hours, or block). YouTube doesn't tell the TV every linked phone, only phones that connected to cast."))
    val bay = System.currentTimeMillis()
    if (tt.dsMay.isEmpty()) Phu(t("Chưa có điện thoại nào cast lên tivi này.", "No phone has cast to this TV yet."))
    tt.dsMay.sortedWith(compareByDescending<CastSmartTube.May> { it.id in tt.dangNoi }.thenByDescending { it.choDuyet }.thenByDescending { it.lanCuoi })
        .forEach { m ->
            val (chu, mau) = trangThaiMay(m, bay)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !vm.dangBan) { c.chonMay = m }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (m.id in tt.dangNoi) Xanh else ChuPhu.copy(alpha = 0.35f)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.tenHien.ifEmpty { t("Điện thoại", "Phone") }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(chu + " · " + t("lần cuối ", "last ") + gio(m.lanCuoi), fontSize = 13.sp, color = mau, maxLines = 1)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = ChuPhu)
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t("Nhận cast", "Take casts"), fontWeight = FontWeight.SemiBold)
            Phu(if (tt.nhanCast) t("Đang bật", "On") else t("Đang tạm ngừng: tivi bỏ qua video điện thoại gửi sang, liên kết vẫn giữ.", "Paused: the TV ignores videos sent from phones; links are kept."))
        }
        CongTac(tt.nhanCast) { if (!vm.dangBan) c.doiNhanCast(it) }
    }
    TextButton(onClick = { c.hoiHuy = true }, enabled = !vm.dangBan) {
        Icon(Icons.Rounded.LinkOff, null, Modifier.size(18.dp), tint = Cam); Spacer(Modifier.width(6.dp))
        Text(t("Huỷ mọi liên kết", "Unlink all phones"), color = Cam)
    }

    c.chonMay?.let { m ->
        AlertDialog(
            onDismissRequest = { c.chonMay = null },
            title = { Text(m.tenHien.ifEmpty { t("Điện thoại", "Phone") }) },
            text = { Text(trangThaiMay(m, bay).first + " · " + t("cast lần đầu ", "first cast ") + gio(m.lanDau)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { c.lamMay(m, CastSmartTube.Lam.CHO_PHEP) }) { Text(t("Cho phép luôn", "Allow always")) }
                    TextButton(onClick = { c.lamMay(m, CastSmartTube.Lam.HOM_NAY) }) { Text(t("Cho phép tới hết hôm nay", "Allow until the end of today")) }
                    TextButton(onClick = { c.lamMay(m, CastSmartTube.Lam.MOT_NGAY) }) { Text(t("Cho phép 24 giờ", "Allow for 24 hours")) }
                    TextButton(onClick = { c.lamMay(m, CastSmartTube.Lam.CHAN) }) { Text(t("Chặn máy này", "Block this phone"), color = Cam) }
                    TextButton(onClick = { c.lamMay(m, CastSmartTube.Lam.XOA) }) { Text(t("Xoá khỏi danh sách (lần sau hỏi lại)", "Remove (ask again next time)")) }
                    TextButton(onClick = { c.chonMay = null }) { Text(t("Đóng", "Close")) }
                }
            },
        )
    }
    if (c.hoiHuy) AlertDialog(
        onDismissRequest = { c.hoiHuy = false },
        icon = { Icon(Icons.Rounded.LinkOff, null, tint = Cam) },
        title = { Text(t("Huỷ mọi liên kết?", "Unlink all phones?")) },
        text = { Text(t("Mọi điện thoại đã liên kết, kể cả máy này, mất tivi và phải nhập mã TV mới. Danh sách điện thoại xoá hết. Trên điện thoại có thể còn tên tivi cũ (mờ) tới khi YouTube làm mới.",
            "Every linked phone, this one included, loses the TV and needs a new TV code. The phone list is cleared. Phones may still show the old TV name (greyed out) until YouTube refreshes.")) },
        confirmButton = { TextButton(onClick = { c.huyLienKet() }) { Text(t("Huỷ mọi liên kết", "Unlink all"), color = Cam) } },
        dismissButton = { TextButton(onClick = { c.hoiHuy = false }) { Text(t("Thôi", "Cancel")) } },
    )
}

/** "HH:mm dd/MM" theo giờ điện thoại; "—" nếu chưa có. */
private fun gio(luc: Long): String = if (luc <= 0) "—" else java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.US).format(java.util.Date(luc))

/** Trạng thái một điện thoại và màu chữ. */
@Composable
private fun trangThaiMay(m: CastSmartTube.May, bay: Long): Pair<String, androidx.compose.ui.graphics.Color> = when {
    m.biChan -> t("Đã chặn", "Blocked") to Cam
    m.quaHan(bay) -> t("Khách đã hết hạn", "Guest expired") to Cam
    m.trangThai == "CHO_PHEP" && m.hetHan > 0 -> t("Khách · tới ", "Guest · until ") + gio(m.hetHan) to Nhan
    m.trangThai == "CHO_PHEP" -> t("Được phép", "Allowed") to Xanh
    else -> t("Chờ duyệt", "Waiting for approval") to Cam
}

@Composable
private fun CanhBao(chu: String) = Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp)) {
    Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
    Phu(chu, ChuChinh)
}

@Composable
private fun Dong(ok: Boolean, coChu: String, khongChu: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null, Modifier.size(16.dp), tint = if (ok) Xanh else Cam)
    Spacer(Modifier.width(6.dp))
    Text(if (ok) coChu else khongChu, fontSize = 13.sp, color = if (ok) Xanh else Cam)
}
