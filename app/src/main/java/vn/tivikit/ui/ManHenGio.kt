package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.HenGio
import vn.tivikit.t

/** Màn Hẹn giờ (25/10/2026): bật tivi, tắt tivi, mở app / kênh theo lịch; tivi tự chạy, không cần điện thoại. */
@Composable
internal fun ManHenGio(vm: PhienLamViec) = Cuon {
    val h = vm.henGio
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Hẹn giờ", "Timer"), style = KieuTieuDeLon)
        Phu(t("Bật tivi, tắt tivi, mở app hoặc mở thẳng một kênh (Kênh nhanh) theo giờ và ngày trong tuần. Lưu xong tivi tự chạy theo lịch, điện thoại tắt hay đi vắng cũng được.",
            "Turn the TV on or off, open an app or a channel (Quick channel) by time and weekday. Once saved the TV runs it by itself, phone off or away."))
    }
    if (!h.duocCai) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Phu(t("Hẹn giờ cần Android 8 trở lên; tivi này là Android ${vm.thongTin?.android}.", "The timer needs Android 8 or later; this TV runs Android ${vm.thongTin?.android}."), ChuChinh)
    }

    h.trangThai?.let { tt ->
        The(t("Trên tivi", "On the TV"), Icons.Rounded.Alarm) {
            if (tt.bayGio > 0) {
                val (g, thu) = HenGio.gioCua(tt.bayGio, tt.muiGio)
                Phu(t("Giờ trên tivi: $g ${HenGioTivi.tenThu(thu)} (${tt.muiGio.id}). Lịch chạy theo giờ này: lệch thì chỉnh giờ / múi giờ trong Cài đặt của tivi.",
                    "TV clock: $g ${HenGioTivi.tenThu(thu)} (${tt.muiGio.id}). The schedule follows this clock; fix the TV's time / time zone if it's off."))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (tt.dichVuChay) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null, Modifier.size(16.dp), tint = if (tt.dichVuChay) Xanh else Cam)
                Spacer(Modifier.width(6.dp))
                Text(if (tt.dichVuChay) t("Dịch vụ Hẹn giờ đang chạy", "The Timer service is running")
                    else t("Dịch vụ Hẹn giờ đang tắt: bấm Lưu lên tivi để bật lại", "The Timer service is off: tap Save to the TV to turn it back on"),
                    fontSize = 13.sp, color = if (tt.dichVuChay) Xanh else Cam)
            }
            if (tt.keTiep > 0) Text(h.keTiepChu(tt), fontWeight = FontWeight.SemiBold)
            if (!tt.chinhXac) Phu(t("Tivi chưa cho báo thức đúng giờ: việc có thể chạy trễ vài phút.", "The TV doesn't allow exact alarms: entries may run a few minutes late."), Cam)
            if (tt.nhatKy.isNotEmpty()) Gap(t("Lần chạy gần đây", "Recent runs"), Icons.Rounded.History) {
                tt.nhatKy.takeLast(8).reversed().forEach { n ->
                    val (g, thu) = HenGio.gioCua(n.luc, tt.muiGio)
                    Phu("$g ${HenGioTivi.tenThu(thu)} · ${n.viec} · ${n.kq}")
                }
            }
            TextButton(onClick = { h.lamMoi() }, enabled = !vm.dangBan) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Làm mới", "Refresh"))
            }
        }
    }

    The(t("Lịch", "Schedule"), Icons.Rounded.Schedule) {
        if (h.ds.isEmpty()) Phu(t("Chưa có việc nào. Thêm ở ô bên dưới.", "Nothing yet. Add entries below."))
        h.ds.forEach { v ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(v.gioChu + " · " + h.moTa(v), fontWeight = FontWeight.SemiBold, color = if (v.bat) ChuChinh else ChuPhu)
                    Phu(if (v.ngay.size == 7) t("Mọi ngày", "Every day") else v.ngay.sorted().joinToString(" ") { t(TEN_THU[it - 1], TEN_THU_EN[it - 1]) })
                }
                CongTac(v.bat) { h.doiBat(v, it) }
                IconButton(onClick = { h.xoa(v) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.DeleteOutline, t("Xoá", "Delete"), tint = ChuPhu) }
            }
        }
        if (h.chuaLuu) Phu(t("Có thay đổi chưa lưu lên tivi.", "There are changes not saved to the TV yet."), Cam)
    }

    The(t("Thêm việc", "Add an entry"), Icons.Rounded.AddAlarm) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Bật tivi", "Turn on"), h.loai == HenGio.BAT) { h.loai = HenGio.BAT }
            if (h.duocTat) ChonChip(t("Tắt tivi", "Turn off"), h.loai == HenGio.TAT) { h.loai = HenGio.TAT; h.goi = null }
            ChonChip(t("Mở app / kênh", "Open an app / channel"), h.loai == HenGio.MO) { h.loai = HenGio.MO }
        }
        if (!h.duocTat) Phu(t("Tắt tivi theo giờ cần Android 9 trở lên.", "Timed turn-off needs Android 9 or later."))
        OutlinedTextField(h.gio, { h.gio = it.take(5) }, modifier = Modifier.width(150.dp), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(t("Giờ (VD 06:30)", "Time (e.g. 06:30)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { d -> ChonChip(t(TEN_THU[d - 1], TEN_THU_EN[d - 1]), d in h.ngay) { h.ngay = if (d in h.ngay) h.ngay - d else h.ngay + d } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Mọi ngày", "Every day"), h.ngay == (1..7).toSet()) { h.ngay = (1..7).toSet() }
            ChonChip(t("T2–T6", "Mon–Fri"), h.ngay == (1..5).toSet()) { h.ngay = (1..5).toSet() }
            ChonChip(t("T7, CN", "Sat, Sun"), h.ngay == setOf(6, 7)) { h.ngay = setOf(6, 7) }
        }
        if (h.loai != HenGio.TAT) {
            Phu(if (h.loai == HenGio.BAT) t("Bật xong thì mở (không bắt buộc):", "After turning on, open (optional):") else t("App cần mở (tivi phải đang bật):", "App to open (the TV must be on):"))
            if (h.loai == HenGio.BAT) LuaChon(h.goi == null, t("Không mở app nào", "Don't open anything"), t("Tivi lên màn hình như khi bấm nút nguồn.", "The TV comes up as with the power button.")) { h.goi = null }
            val dau = h.dsApp.take(4).let { if (h.goi != null && h.goi !in it) it + h.goi!! else it }
            dau.forEach { p -> LuaChon(h.goi == p, h.tenApp(p), p) { h.goi = p } }
            val khac = h.dsApp.filter { it !in dau }
            if (khac.isNotEmpty()) Gap(t("App khác (${khac.size})", "Other apps (${khac.size})"), Icons.Rounded.Apps) {
                khac.forEach { p -> LuaChon(h.goi == p, h.tenApp(p), p) { h.goi = p } }
            }
        } else Phu(t("Trước 1 phút tivi hiện lời báo có nút \"Hoãn 30 phút\"; không ai bấm thì tivi tắt đúng giờ.",
            "A minute before, the TV shows a notice with \"Snooze 30 min\"; if nobody presses anything it turns off on time."))
        NutPhu(t("Thêm vào lịch", "Add to the schedule"), Icons.Rounded.Add, enabled = !vm.dangBan) { h.them() }
    }

    NutChinh(t("Lưu lên tivi", "Save to the TV"), enabled = !vm.dangBan && h.duocCai && (h.chuaLuu || !h.daCai || h.trangThai?.dichVuChay == false), icon = Icons.Rounded.Check) { h.luu() }
    if (h.bao.isNotEmpty()) Phu(h.bao, Xanh)

    if (h.daCai) The(t("Thử bật tivi bằng hẹn giờ", "Test timed power-on"), Icons.Rounded.PowerSettingsNew) {
        Phu(t("Hẹn giờ bật chỉ chạy khi tivi tắt bằng remote vào chế độ chờ; tivi rút điện hay tắt sâu thì không app nào bật lại được. Thử một lần cho chắc.",
            "Timed power-on only works when the remote puts the TV in standby; if it's unplugged or powers down deeply, no app can turn it back on. Test it once."))
        NutPhu(t("Thử: tivi tự bật sau 2 phút", "Test: the TV turns on in 2 minutes"), Icons.Rounded.Timer, enabled = !vm.dangBan) { h.thu() }
        TextButton(onClick = { h.go() }, enabled = !vm.dangBan) {
            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Gỡ Hẹn giờ khỏi tivi", "Remove the Timer from the TV"))
        }
    }
    Phu(t("Lưu lên tivi sẽ cài app nhỏ \"Hẹn giờ (TiviKit)\", cho nó báo thức đúng giờ và bật dịch vụ trợ năng của nó (để tắt tivi và mở màn hình đúng giờ; không đọc màn hình, không bắt phím). Thấy và tắt được trong Cài đặt → Trợ năng của tivi; Gỡ hoặc Khôi phục tivi là xoá hết. Tivi có sẵn hẹn giờ bật / tắt của hãng thì dùng song song được.",
        "Saving installs the small \"Timer (TiviKit)\" app, allows it exact alarms and turns on its accessibility service (to turn the TV off and bring up screens on time; it doesn't read the screen or catch keys). It's visible and can be turned off in the TV's Settings → Accessibility; Remove or Restore deletes everything. The TV's own on / off timers keep working alongside."))
}
