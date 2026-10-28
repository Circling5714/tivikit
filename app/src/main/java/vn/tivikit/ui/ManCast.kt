package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
        Phu(t("App YouTube trên điện thoại (iPhone hay Android) cùng Wi-Fi với tivi thấy tivi trong nút Cast như YouTube gốc, không cần liên kết bằng mã TV. Video gửi sang mở bằng SmartTube; tạm dừng, tua, đổi âm lượng trên điện thoại được.",
            "The YouTube app on any phone (iPhone or Android) on the same Wi-Fi finds the TV under the Cast button, as with the official app, with no TV code. What you send plays in SmartTube; pause, seek and volume work from the phone."))
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
    Phu(t("Bật sẽ cài app nhỏ \"Cast SmartTube (TiviKit)\", bật dịch vụ trợ năng của nó (để chạy suốt và được mở SmartTube; không đọc màn hình, không bắt phím) và cho nó đọc phiên phát của SmartTube (để đồng bộ khi bấm remote tivi; tivi ít RAM không cho thì bỏ qua). App nói chuyện với máy chủ YouTube như app YouTube trên tivi (giao thức không công khai của YouTube: YouTube đổi thì có thể hỏng tới khi TiviKit cập nhật). Ai cùng Wi-Fi cũng cast được, như Chromecast. Gỡ hoặc Khôi phục tivi là xoá hết.",
        "Turning it on installs the small \"Cast SmartTube (TiviKit)\" app, turns on its accessibility service (to keep running and be allowed to open SmartTube; it doesn't read the screen or catch keys) and lets it read SmartTube's playback session (to stay in sync when you use the TV remote; skipped on low-RAM TVs that don't allow it). It talks to YouTube's servers the way the YouTube TV app does (YouTube's private protocol: if YouTube changes it, this may break until TiviKit is updated). Anyone on the same Wi-Fi can cast, as with a Chromecast. Remove or Restore deletes everything."))
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
