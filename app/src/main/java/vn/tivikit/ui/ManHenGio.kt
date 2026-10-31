package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
                    Phu("$g ${HenGioTivi.tenThu(thu)} · ${h.moTa(n)}")
                }
            }
            Row {
                TextButton(onClick = { h.lamMoi() }, enabled = !vm.dangBan) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Làm mới", "Refresh"))
                }
                // App phụ 1.2 có màn Hẹn giờ trên tivi (dùng remote).
                if (tt.coManTv) TextButton(onClick = { h.moTrenTv() }, enabled = !vm.dangBan) {
                    Icon(Icons.Rounded.Tv, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Mở trên tivi", "Open on the TV"))
                }
            }
        }
    }

    // Hẹn một lần (31/10/2026): tắt tivi sau X phút, việc một lần đang chờ trên tivi.
    if (h.duocCai) The(t("Hẹn một lần", "One-off"), Icons.Rounded.Timer) {
        if (h.duocTat) {
            Phu(t("Tắt tivi sau (không lặp lại):", "Turn the TV off in (once):"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90, 120).forEach { p -> ChonChip(HenGioTivi.phutChu(p), false) { if (!vm.dangBan) h.tatSau(p) } }
                ChonChip(t("Khác", "Other"), h.khac) { h.khac = !h.khac }
            }
            if (h.khac) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(h.phutKhac, { h.phutKhac = it.filter(Char::isDigit).take(3) }, modifier = Modifier.width(130.dp), singleLine = true,
                    shape = RoundedCornerShape(12.dp), label = { Text(t("Số phút", "Minutes")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                NutPhu(t("Hẹn tắt", "Set"), Icons.Rounded.Timer, enabled = !vm.dangBan && h.phutKhac.isNotEmpty()) { h.tatSau(h.phutKhac.toIntOrNull() ?: 0) }
            }
        } else Phu(t("Tắt tivi theo giờ cần Android 9 trở lên.", "Timed turn-off needs Android 9 or later."))
        val tt = h.trangThai
        val ml = tt?.motLan.orEmpty()
        if (tt != null && ml.isNotEmpty()) {
            Phu(t("Đang chờ trên tivi:", "Pending on the TV:"))
            ml.forEach { m ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(h.moTa(m, tt), fontWeight = FontWeight.SemiBold)
                        Phu(h.con(m, tt))
                    }
                    IconButton(onClick = { h.huyMotLan(m) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.Close, t("Huỷ", "Cancel"), tint = ChuPhu) }
                }
            }
        }
        Phu(t("Bật tivi hay mở app một lần lúc HH:MM: chọn Một lần ở ô Thêm việc bên dưới.", "To turn the TV on or open an app once at a set time, pick Once under Add an entry."))
    }

    The(t("Lịch", "Schedule"), Icons.Rounded.Schedule) {
        if (h.ds.isEmpty()) Phu(t("Chưa có việc nào. Thêm ở ô bên dưới.", "Nothing yet. Add entries below."))
        h.ds.forEach { v ->
            val dangSua = v == h.dangSua
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2)
                .border(1.dp, if (dangSua) Nhan else Color.Transparent, RoundedCornerShape(14.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(v.gioChu + " · " + h.moTa(v), fontWeight = FontWeight.SemiBold, color = if (v.bat) ChuChinh else ChuPhu)
                    Phu(if (v.ngay.size == 7) t("Mọi ngày", "Every day") else v.ngay.sorted().joinToString(" ") { t(TEN_THU[it - 1], TEN_THU_EN[it - 1]) })
                }
                CongTac(v.bat) { h.doiBat(v, it) }
                IconButton(onClick = { h.sua(v) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.Edit, t("Sửa", "Edit"), tint = ChuPhu) }
                IconButton(onClick = { h.xoa(v) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.DeleteOutline, t("Xoá", "Delete"), tint = ChuPhu) }
            }
        }
        if (h.chuaLuu) Phu(t("Có thay đổi chưa lưu lên tivi.", "There are changes not saved to the TV yet."), Cam)
    }

    val sua = h.dangSua
    The(if (sua != null) t("Sửa việc ${sua.gioChu}", "Edit the ${sua.gioChu} entry") else t("Thêm việc", "Add an entry"),
        if (sua != null) Icons.Rounded.Edit else Icons.Rounded.AddAlarm) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Bật tivi", "Turn on"), h.loai == HenGio.BAT) { h.loai = HenGio.BAT }
            if (h.duocTat) ChonChip(t("Tắt tivi", "Turn off"), h.loai == HenGio.TAT) { h.loai = HenGio.TAT; h.goi = null }
            ChonChip(t("Mở app / kênh", "Open an app / channel"), h.loai == HenGio.MO) { h.loai = HenGio.MO }
        }
        if (!h.duocTat) Phu(t("Tắt tivi theo giờ cần Android 9 trở lên.", "Timed turn-off needs Android 9 or later."))
        // Chỉ nhận số, tự chèn dấu hai chấm (HenGio.oGio); con trỏ luôn ở cuối vì ô chỉ có 5 ký tự.
        OutlinedTextField(TextFieldValue(h.gio, TextRange(h.gio.length)), { h.gio = HenGio.oGio(it.text) }, modifier = Modifier.width(150.dp),
            singleLine = true, shape = RoundedCornerShape(12.dp), isError = HenGio.gioSai(h.gio), placeholder = { Text("06:30") },
            label = { Text(t("Giờ (VD 06:30)", "Time (e.g. 06:30)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (HenGio.gioSai(h.gio)) Phu(t("Giờ từ 00 đến 23, phút từ 00 đến 59.", "Hours 00 to 23, minutes 00 to 59."), Cam)
        // Một lần: hẹn ngay lên tivi, không vào lịch hằng tuần (việc đang sửa thì không đổi thành một lần).
        val motLan = h.motLan && sua == null
        if (!motLan) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { d -> ChonChip(t(TEN_THU[d - 1], TEN_THU_EN[d - 1]), d in h.ngay) { h.ngay = if (d in h.ngay) h.ngay - d else h.ngay + d } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Mọi ngày", "Every day"), !motLan && h.ngay == (1..7).toSet()) { h.ngay = (1..7).toSet(); h.motLan = false }
            ChonChip(t("T2–T6", "Mon–Fri"), !motLan && h.ngay == (1..5).toSet()) { h.ngay = (1..5).toSet(); h.motLan = false }
            ChonChip(t("T7, CN", "Sat, Sun"), !motLan && h.ngay == setOf(6, 7)) { h.ngay = setOf(6, 7); h.motLan = false }
            if (sua == null) ChonChip(t("Một lần", "Once"), motLan) { h.motLan = !h.motLan }
        }
        if (motLan) Phu(t("Chạy một lần lúc giờ đó (hôm nay, hay ngày mai nếu đã qua giờ) rồi tự xoá. Bấm Hẹn một lần là gửi ngay lên tivi, không cần Lưu lên tivi.",
            "Runs once at that time (today, or tomorrow if it has passed), then removes itself. Set once sends it to the TV right away; no Save needed."))
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
        NutPhu(when { sua != null -> t("Cập nhật việc", "Update the entry"); motLan -> t("Hẹn một lần", "Set once"); else -> t("Thêm vào lịch", "Add to the schedule") },
            when { sua != null -> Icons.Rounded.Check; motLan -> Icons.Rounded.Timer; else -> Icons.Rounded.Add }, enabled = !vm.dangBan) { h.them() }
        if (sua != null) TextButton(onClick = { h.thoiSua() }, enabled = !vm.dangBan) { Text(t("Thôi sửa", "Stop editing")) }
    }

    NutChinh(t("Lưu lên tivi", "Save to the TV"), enabled = !vm.dangBan && h.duocCai && (h.chuaLuu || !h.daCai || h.trangThai?.dichVuChay == false), icon = Icons.Rounded.Check) { h.luu() }
    if (h.bao.isNotEmpty()) Phu(h.bao, Xanh)

    if (h.daCai) The(t("Thử bật tivi bằng hẹn giờ", "Test timed power-on"), Icons.Rounded.PowerSettingsNew) {
        Phu(t("Hẹn giờ bật chỉ chạy khi tivi tắt bằng remote vào chế độ chờ; tivi rút điện hay tắt sâu thì không app nào bật lại được. Thử một lần cho chắc.",
            "Timed power-on only works when the remote puts the TV in standby; if it's unplugged or powers down deeply, no app can turn it back on. Test it once."))
        NutPhu(t("Thử: tivi tự bật sau 2 phút", "Test: the TV turns on in 2 minutes"), Icons.Rounded.Timer, enabled = !vm.dangBan) { h.thu() }
        var hoiGo by remember { mutableStateOf(false) }
        TextButton(onClick = { hoiGo = true }, enabled = !vm.dangBan) {
            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Gỡ Hẹn giờ khỏi tivi", "Remove the Timer from the TV"))
        }
        if (hoiGo) AlertDialog(
            onDismissRequest = { hoiGo = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Cam) },
            title = { Text(t("Gỡ Hẹn giờ?", "Remove the Timer?")) },
            text = { Text(t("Mọi giờ bật / tắt / mở app đã hẹn trên tivi bị xoá.", "Every scheduled power on / off / app opening on the TV is deleted.")) },
            confirmButton = { TextButton(onClick = { hoiGo = false; h.go() }) { Text(t("Gỡ", "Remove"), color = Cam) } },
            dismissButton = { TextButton(onClick = { hoiGo = false }) { Text(t("Thôi", "Cancel")) } },
        )
    }
    Phu(t("Lưu lên tivi sẽ cài app nhỏ \"Hẹn giờ (TiviKit)\", cho nó báo thức đúng giờ và bật dịch vụ trợ năng của nó (để tắt tivi và mở màn hình đúng giờ; không đọc màn hình, không bắt phím). Thấy và tắt được trong Cài đặt → Trợ năng của tivi; Gỡ hoặc Khôi phục tivi là xoá hết. Tivi có sẵn hẹn giờ bật / tắt của hãng thì dùng song song được. Trên tivi, ô \"Hẹn giờ (TiviKit)\" trong danh sách app mở màn Hẹn giờ dùng remote (xem, sửa lịch, hẹn tắt); Khoá trẻ em đang bật thì đổi lịch ở đó cần mã bố mẹ.",
        "Saving installs the small \"Timer (TiviKit)\" app, allows it exact alarms and turns on its accessibility service (to turn the TV off and bring up screens on time; it doesn't read the screen or catch keys). It's visible and can be turned off in the TV's Settings → Accessibility; Remove or Restore deletes everything. The TV's own on / off timers keep working alongside. On the TV, the \"Timer (TiviKit)\" tile in the app list opens a remote-friendly Timer screen (view and change the schedule, set a turn-off); with the Kids lock on, changes there need the parent code."))
}
