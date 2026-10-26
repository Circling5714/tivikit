package vn.tivikit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.t

/**
 * Màn Tivi LG (25/10/2026, xem LgTivi): chưa nối thì tivi LG đã nối trước đây, quét mạng, nhập IP; đã nối thì remote, gõ
 * chữ có dấu, mở app, link YouTube / trang web, nguồn vào, màn hình, tắt tivi, lời nhắn lên tivi.
 */
@Composable
internal fun ManLg(vm: PhienLamViec) = Cuon {
    val lg = vm.lg
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Tivi LG", "LG TV"), style = KieuTieuDeLon)
        Phu(t("Tivi LG chạy webOS: điều khiển bằng điện thoại, gõ tiếng Việt có dấu, mở app, YouTube, đổi nguồn HDMI, tắt màn hình. Không cần root hay chế độ nhà phát triển; lần đầu tivi hỏi Cho phép.",
            "LG TVs running webOS: control from the phone, type with accents, open apps and YouTube, switch HDMI, turn the screen off. No root or developer mode; the TV asks to Allow the first time."))
    }
    val tv = lg.dangNoi
    if (tv == null) ChuaNoiLg(vm) else DaNoiLg(vm, tv)
    if (lg.bao.isNotEmpty()) Phu(lg.bao, if (lg.baoLoi) DoTuoi else Xanh)
}

@Composable
private fun ColumnScope.ChuaNoiLg(vm: PhienLamViec) {
    val lg = vm.lg
    if (lg.dsTivi.isNotEmpty()) The(t("Tivi LG đã nối", "Your LG TVs"), Icons.Rounded.Tv) {
        lg.dsTivi.forEach { g ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.ten.ifBlank { g.ip }, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(g.ip, fontSize = 12.sp, color = ChuPhu)
                }
                if (g.mac.isNotBlank()) TextButton(onClick = { lg.batTivi(g) }) { Text(t("Bật", "Wake")) }
                TextButton(onClick = { lg.noi(g.ip) }, enabled = !vm.dangBan) { Text(t("Nối lại", "Reconnect")) }
                IconButton(onClick = { lg.quen(g) }) { Icon(Icons.Rounded.Close, t("Quên tivi này", "Forget this TV"), tint = ChuPhu) }
            }
        }
        Phu(t("Nút Bật gửi lệnh bật qua mạng: tivi cần bật \"Bật TV qua Wi-Fi\" (Cài đặt → Chung → Thiết bị, tên tuỳ đời tivi).",
            "Wake sends a network wake-up: the TV needs \"Turn on via Wi-Fi\" (Settings → General → Devices; names vary)."))
    }
    The(t("Tìm tivi LG", "Find an LG TV"), Icons.Rounded.WifiFind) {
        NutChinh(if (lg.dangQuet) t("Đang quét…", "Scanning…") else t("Quét mạng tìm tivi LG", "Scan for LG TVs"), enabled = !lg.dangQuet && !vm.dangBan,
            icon = Icons.Rounded.WifiFind) { lg.quet() }
        lg.timThay.forEach { dc ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lg.tenCua(dc) ?: dc, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(if (lg.tenCua(dc) != null) dc else t("Tivi LG webOS", "LG webOS TV"), fontSize = 12.sp, color = ChuPhu)
                }
                TextButton(onClick = { lg.noi(dc) }, enabled = !vm.dangBan) { Text(t("Kết nối", "Connect")) }
            }
        }
        var ip by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(ip, { ip = it.trim() }, label = { Text(t("Hoặc nhập IP tivi", "Or enter the TV's IP")) }, singleLine = true, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Button(onClick = { lg.noi(ip) }, enabled = ip.isNotBlank() && !vm.dangBan, shape = RoundedCornerShape(12.dp)) { Text(t("Nối", "Connect")) }
        }
        Phu(t("Tivi đang bật, cùng Wi-Fi với điện thoại. IP tivi: Cài đặt → Mạng → Wi-Fi / Có dây → Nâng cao. Lần đầu tivi hiện hộp hỏi: bấm Cho phép.",
            "The TV is on and on the same Wi-Fi. TV IP: Settings → Network → Wi-Fi / Wired → Advanced. The first time the TV asks: press Allow."))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.DaNoiLg(vm: PhienLamViec, tv: LgTivi.TiviLg) {
    val lg = vm.lg
    var hoiTat by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        OIcon(Icons.Rounded.Tv, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tv.ten, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(t("Đã kết nối", "Connected"), lg.phienBan, tv.ip).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = ChuPhu, maxLines = 2)
        }
        TextButton(onClick = { lg.ngat() }) { Text(t("Ngắt", "Disconnect")) }
    }
    // Tivi đã root (Homebrew Channel + SSH): app tự nhận ra sau khi nối, hiện thêm chức năng.
    TheRootLg(vm)

    The(t("Gõ chữ lên tivi", "Type on the TV"), Icons.Rounded.Keyboard) {
        OutlinedTextField(lg.chu, { lg.chu = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(t("Chọn ô nhập trên tivi trước, rồi gõ ở đây", "Select the TV's text box first, then type here")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { lg.guiChu(enter = true) }))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NutRemote(Icons.AutoMirrored.Rounded.Backspace, t("Xoá 1 chữ", "Delete")) { lg.xoaChu() }
            NutRemote(Icons.AutoMirrored.Rounded.KeyboardReturn, "Enter") { lg.guiChu(enter = true) }
            NutRemote(Icons.AutoMirrored.Rounded.Send, t("Gửi chữ", "Send"), mau = Nhan) { lg.guiChu(enter = false) }
        }
        Phu(t("Gõ tiếng Việt có dấu được (tivi LG nhận nguyên chữ). Nút Gửi trên bàn phím = gửi rồi Enter.",
            "Vietnamese accents work (LG TVs take the text as is). The keyboard's Send key sends, then presses Enter."))
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.AutoMirrored.Rounded.Input, t("Nguồn vào", "Input")) { lg.nut("INPUT_HUB") }
        NutRemote(Icons.Rounded.Settings, t("Cài đặt", "Settings")) { lg.nut("QMENU") }
        NutRemote(Icons.Rounded.PowerSettingsNew, t("Tắt tivi", "Power"), mau = DoTuoi) { hoiTat = true }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        VongDieuHuong({ lg.nut("UP") }, { lg.nut("DOWN") }, { lg.nut("LEFT") }, { lg.nut("RIGHT") }, { lg.nut("ENTER") })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.AutoMirrored.Rounded.ArrowBack, t("Quay lại", "Back")) { lg.nut("BACK") }
        NutRemote(Icons.Rounded.Home, "Home") { lg.nut("HOME") }
        NutRemote(Icons.Rounded.Close, t("Thoát", "Exit")) { lg.nut("EXIT") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.AutoMirrored.Rounded.VolumeDown, t("Nhỏ", "Vol −")) { lg.nut("VOLUMEDOWN") }
        NutRemote(Icons.AutoMirrored.Rounded.VolumeOff, t("Tắt tiếng", "Mute")) { lg.nut("MUTE") }
        NutRemote(Icons.AutoMirrored.Rounded.VolumeUp, t("To", "Vol +")) { lg.nut("VOLUMEUP") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.Rounded.FastRewind, t("Tua lùi", "Rewind")) { lg.nut("REWIND") }
        NutRemote(Icons.Rounded.PlayArrow, t("Phát", "Play")) { lg.nut("PLAY") }
        NutRemote(Icons.Rounded.Pause, t("Dừng", "Pause")) { lg.nut("PAUSE") }
        NutRemote(Icons.Rounded.FastForward, t("Tua tới", "Forward")) { lg.nut("FASTFORWARD") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.Rounded.ExpandMore, t("Kênh −", "Ch −")) { lg.nut("CHANNELDOWN") }
        NutRemote(Icons.Rounded.ExpandLess, t("Kênh +", "Ch +")) { lg.nut("CHANNELUP") }
    }

    if (lg.apps.isNotEmpty()) The(t("Mở app", "Open an app"), Icons.Rounded.Apps) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lg.apps.forEach { a ->
                Column(Modifier.width(74.dp).clip(RoundedCornerShape(12.dp)).clickable { lg.moApp(a) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val anh = lg.icon[a.id]
                    if (anh != null) Image(anh, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                    else Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(NenO), contentAlignment = Alignment.Center) {
                        Text(a.ten.take(1).uppercase(), fontWeight = FontWeight.Bold, color = ChuPhu)
                    }
                    Text(a.ten, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    The(t("YouTube, trang web", "YouTube, web pages"), Icons.Rounded.SmartDisplay) {
        OutlinedTextField(lg.link, { lg.link = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(t("Dán link YouTube hoặc trang web", "Paste a YouTube or web link")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        NutPhu(t("Mở trên tivi", "Open on the TV"), Icons.AutoMirrored.Rounded.OpenInNew) { lg.moLink() }
        Phu(t("Link YouTube mở bằng app YouTube của tivi, link khác mở bằng trình duyệt của tivi. Chia sẻ từ app YouTube trên điện thoại → Sao chép đường liên kết.",
            "YouTube links open in the TV's YouTube app, other links in its browser. In the YouTube app on the phone: Share → Copy link."))
    }

    if (lg.nguon.isNotEmpty()) The(t("Nguồn vào", "Inputs"), Icons.AutoMirrored.Rounded.Input) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lg.nguon.forEach { n -> ChonChip(n.ten + if (n.coThietBi) " ●" else "", false) { lg.doiNguon(n) } }
        }
        Phu(t("● = cổng đang cắm thiết bị.", "● = something is plugged in."))
    }

    The(t("Màn hình & nguồn điện", "Screen & power"), Icons.Rounded.TvOff) {
        NutPhu(t("Tắt màn hình, vẫn nghe tiếng", "Screen off, keep the sound"), Icons.Rounded.TvOff) { lg.manHinh(false) }
        NutPhu(t("Bật lại màn hình", "Screen back on"), Icons.Rounded.Tv) { lg.manHinh(true) }
        Phu(t("Tắt màn hình hợp nghe nhạc, podcast: đỡ tốn điện, đỡ chói. Bấm phím bất kỳ trên remote là màn hình sáng lại.",
            "Screen off suits music and podcasts: saves power and glare. Any remote key turns it back on."))
    }

    The(t("Lời nhắn lên tivi", "Message on the TV"), Icons.Rounded.Notifications) {
        OutlinedTextField(lg.loiNhan, { lg.loiNhan = it.take(120) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(t("VD: Cơm chín rồi, xuống ăn nhé!", "E.g. Dinner's ready!")) })
        NutPhu(t("Hiện trên tivi", "Show on the TV"), Icons.AutoMirrored.Rounded.Send) { lg.guiLoiNhan() }
    }

    if (hoiTat) AlertDialog(
        onDismissRequest = { hoiTat = false },
        icon = { Icon(Icons.Rounded.PowerSettingsNew, null, tint = DoTuoi) },
        title = { Text(t("Tắt tivi?", "Turn the TV off?")) },
        text = { Text(t("Tivi về chế độ chờ. Bật lại bằng remote, hoặc nút Bật nếu tivi đã bật \"Bật TV qua Wi-Fi\".",
            "The TV goes to standby. Turn it back on with the remote, or Wake if the TV has \"Turn on via Wi-Fi\" on.")) },
        confirmButton = { TextButton(onClick = { hoiTat = false; lg.tatTivi() }) { Text(t("Tắt", "Turn off"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { hoiTat = false }) { Text(t("Thôi", "Cancel")) } },
        containerColor = The1,
    )
}
