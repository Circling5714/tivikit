package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.dulieu.KenhVtvGo
import vn.tivikit.dulieu.XaPhuong
import vn.tivikit.t

/**
 * Màn Kênh nhanh (25/10/2026; 26/10/2026 thêm ô VTVgo): các ô kênh (VTV1, VTV3, HTV7…), mỗi ô mở thẳng một kênh trên TV360
 * hoặc VTVgo, gán được một nút remote. Ô soạn chọn app của ô bằng ô chia đoạn TV360 · VTVgo.
 */
@Composable
internal fun ManKenhNhanh(vm: PhienLamViec) = Cuon {
    val k = vm.kenhNhanh
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Kênh nhanh", "Quick channel"), style = KieuTieuDeLon)
        Phu(t("Mỗi ô kênh là một ô trên Home sạch và TiviKit Launcher, mở thẳng một kênh (VTV1, VTV3, HTV7…) trên TV360 hoặc VTVgo, mang logo kênh nếu VTVgo có kênh đó, gán được một nút remote riêng. Tối đa ${KenhNhanh.SO_O} ô; mỗi ô là một app rất nhỏ TiviKit cài lên tivi.",
            "Each channel tile is a tile on the clean Home and TiviKit Launcher that opens one channel (VTV1, VTV3, HTV7…) in TV360 or VTVgo, with the channel's logo when VTVgo has that channel, and can get its own remote button. Up to ${KenhNhanh.SO_O} tiles; each is a tiny app TiviKit installs on the TV."))
    }
    if (vm.daCoHomeSach && k.homeCu) CanhBaoKenh(vm,
        t("Home sạch trên tivi là bản cũ nên ô chưa hiện logo kênh. Mở Home sạch, bấm Áp dụng lên tivi một lần (app tự cập nhật, giữ giao diện).",
            "The clean Home on the TV is an old version, so tiles don't show channel logos yet. Open Clean Home and press Apply to the TV once (the app updates it and keeps your look)."),
        t("Mở Home sạch", "Open Clean Home")) { k.moHomeSach() }
    if (k.kenhGoCu.isNotEmpty()) CanhBaoKenh(vm,
        t("Home sạch đang có ${k.kenhGoCu.size} ô kênh VTVgo kiểu cũ (${k.kenhGoCu.joinToString { it.first }}), không gán được nút remote. Chuyển sang ô Kênh nhanh để gán nút remote và hẹn giờ.",
            "The clean Home has ${k.kenhGoCu.size} old-style VTVgo tiles (${k.kenhGoCu.joinToString { it.first }}) that can't take a remote button. Move them to Quick channel tiles for remote buttons and the timer."),
        t("Chuyển sang ô Kênh nhanh", "Move to Quick channel tiles")) { k.chuyenKenhCu() }

    The(t("Ô kênh trên tivi (${k.ds.size}/${KenhNhanh.SO_O})", "Channel tiles on the TV (${k.ds.size}/${KenhNhanh.SO_O})"), Icons.Rounded.LiveTv) {
        if (k.ds.isEmpty()) Phu(t("Chưa có ô nào. Chọn kênh ở ô bên dưới rồi bấm Thêm lên tivi.", "No tiles yet. Pick a channel below and tap Add to the TV."))
        k.ds.forEach { o ->
            val dangSua = k.dangSua == o.so
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2)
                .border(1.dp, if (dangSua) Nhan else Color.Transparent, RoundedCornerShape(14.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(o.kenh?.ten ?: t("Ô ${o.so} (chưa đọc được kênh)", "Tile ${o.so} (channel unreadable)"), fontWeight = FontWeight.SemiBold)
                    Phu(listOfNotNull(t("ô ${o.so}", "tile ${o.so}"),
                        o.kenh?.let { if (it.laVtvGo) "VTVgo" else t("TV360 số ${it.so.trimStart('0')}", "TV360 no. ${it.so.trimStart('0')}") },
                        if (o.trongDock) t("trong dock", "in the dock") else null).joinToString(" · "))
                }
                IconButton(onClick = { k.thu(o) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.PlayCircle, t("Thử trên tivi", "Test on the TV"), tint = Xanh) }
                if (vm.daCoHomeSach) IconButton(onClick = { k.ganNut(o) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.SmartButton, t("Gán nút remote", "Map a remote button"), tint = Nhan) }
                IconButton(onClick = { k.sua(o) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.Edit, t("Sửa", "Edit"), tint = ChuPhu) }
                IconButton(onClick = { k.bo(o) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.DeleteOutline, t("Bỏ ô", "Remove tile"), tint = ChuPhu) }
            }
        }
        if (k.ds.isNotEmpty()) Phu(t("▶ thử trên tivi · nút remote: gán một nút mở ô đó · bút: đổi kênh · thùng rác: bỏ ô.",
            "▶ test on the TV · remote: map a button to the tile · pencil: change the channel · bin: remove the tile."))
    }

    val sua = k.dangSua
    if (sua != null || k.ds.size < KenhNhanh.SO_O) The(if (sua != null) t("Sửa ô $sua", "Edit tile $sua") else t("Thêm ô kênh", "Add a channel tile"), Icons.Rounded.AddToQueue) {
        ChonDoan(listOf("TV360", "VTVgo"), k.app) { k.app = it }
        if (k.app == 0) SoanTv360(vm) else SoanVtvGo(vm)
        if (vm.daCoHomeSach) Hang(t("Thêm ô vào dock", "Put the tile in the dock"),
            t("Ô mang tên kênh ở hàng dock; tắt thì ô chỉ nằm trong danh sách app.", "A tile named after the channel in the dock; off keeps it in the app list only."),
            k.trongDock) { k.trongDock = it }
        else Phu(t("Tivi chưa có Home sạch: ô kênh hiện trong danh sách app của tivi; gán nút remote cần Home sạch.",
            "No clean Home on this TV: channel tiles show in the TV's app list; remote buttons need the clean Home."))
        NutChinh(if (sua != null) t("Lưu ô $sua", "Save tile $sua") else t("Thêm lên tivi", "Add to the TV"), enabled = !vm.dangBan, icon = Icons.Rounded.Check) { k.luu() }
        if (sua != null) TextButton(onClick = { k.thoiSua() }, enabled = !vm.dangBan) { Text(t("Thôi sửa", "Stop editing")) }
    } else Phu(t("Đã đủ ${KenhNhanh.SO_O} ô kênh. Muốn đổi kênh thì bấm bút ở một ô; muốn kênh khác thì bỏ bớt một ô.",
        "All ${KenhNhanh.SO_O} tiles are used. Tap the pencil on a tile to change its channel, or remove a tile for another channel."))
    if (k.bao.isNotEmpty()) Phu(k.bao, if (k.baoLech) Cam else Xanh)

    Phu(t("Ô TV360 dùng cổng nhận lệnh giọng nói của TV360 (cùng đường với \"Xem VTV1 trên TV360\" của Google Assistant), không phải giao diện công khai: bản TV360 sau có thể đổi, khi đó ô chỉ mở TV360 như thường. Ô VTVgo mở bằng link kênh của VTVgo. Logo kênh tải từ VTVgo lúc lưu ô (app không đóng sẵn logo của đài). Hẹn giờ cũng mở được từng ô (VD 18:58 mở VTV1). Khôi phục tivi gỡ hết các ô.",
        "TV360 tiles use TV360's voice-command entry (the one Google Assistant uses for \"Watch VTV1 on TV360\"), not a public interface: a later TV360 may change it, and the tiles then just open TV360. VTVgo tiles open VTVgo's channel link. Channel logos come from VTVgo when a tile is saved (no station logos are bundled with the app). The Timer can open each tile too (e.g. 18:58 opens VTV1). Restoring the TV removes all tiles."))
    if (vm.moChonKenh) HopChonKenhGo(vm)
}

/** Ô soạn, kênh TV360: chọn nhanh hoặc gõ số kênh và tên. */
@Composable
private fun SoanTv360(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    if (!k.coTv360) CanhBaoKenh(vm, t("Tivi chưa có TV360 bản tivi (hoặc đang bị tắt ở Quản lý app).", "The TV doesn't have TV360 for TV (or it's disabled in Manage apps)."),
        t("Mở TV360 trên CH Play của tivi", "Open TV360 in the TV's Play Store")) { k.moCHPlay(KenhNhanh.TV360) }
    Phu(t("Chọn nhanh:", "Pick one:"))
    KenhNhanh.GOI_Y.chunked(5).forEach { hang ->
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hang.forEach { g -> ChonChip(if (k.daCoTv360(g)) "${g.ten} ✓" else g.ten, KenhNhanh.chuanSo(k.so) == g.so) { k.chon(g) } }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(k.so, { k.so = it.filter(Char::isDigit).take(3) }, modifier = Modifier.width(120.dp), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(t("Số kênh", "Number")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(k.ten, { k.ten = it.take(30) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(t("Tên kênh", "Channel name")) })
    }
    Phu(t("Số kênh là số TV360 hiện cạnh tên kênh trong danh sách kênh (VTV1 là 1, HTV7 là 73). TV360 tìm theo số trước, không thấy thì theo tên; tên cũng là tên ô trên Home sạch. ✓ = kênh đã có ô.",
        "The number is the one TV360 shows next to the channel in its list (VTV1 is 1, HTV7 is 73). TV360 looks up the number first, then the name; the name is also the tile's name on the clean Home. ✓ = already has a tile."))
}

/** Ô soạn, kênh VTVgo: VTV1–9, hoặc kênh khác (địa phương, VTVcab…) từ danh sách của VTVgo. */
@Composable
private fun SoanVtvGo(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    if (!vm.coVtvGo) CanhBaoKenh(vm, t("Tivi chưa có VTVgo bản tivi: cài từ CH Play của tivi, mở VTVgo một lần, rồi mở lại màn này.",
        "The TV doesn't have VTVgo for TV: install it from the TV's Play Store, open it once, then reopen this screen."),
        t("Mở VTVgo trên CH Play của tivi", "Open VTVgo in the TV's Play Store")) { k.moCHPlay(KenhNhanh.VTVGO) }
    Phu(t("Chọn kênh:", "Pick a channel:"))
    val dangChon = k.kenhGo
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KenhVtvGo.VTV.forEach { (ten, ma, _) -> ChonChip(if (k.daCoGo(ma)) "$ten ✓" else ten, dangChon?.second == ma) { k.chonGo(ten, ma) } }
    }
    // Hàng chip cuộn ngang nên kênh đang chọn (hay kênh khác chọn từ hộp danh sách) có thể khuất: ghi rõ ra.
    if (dangChon != null) Text(t("Kênh của ô: ${dangChon.first}", "Tile channel: ${dangChon.first}"), fontWeight = FontWeight.SemiBold, color = ChuChinh)
    NutPhu(t("Kênh khác trên VTVgo (địa phương, VTVcab…)", "Other VTVgo channels (local, VTVcab…)"), Icons.Rounded.Search) { vm.moHopKenh() }
    Phu(t("Ô mở thẳng kênh trong app VTVgo; kênh có ổ khoá cần đăng nhập VTVgo trên tivi. ✓ = kênh đã có ô.",
        "The tile opens the channel straight in the VTVgo app; locked channels need a VTVgo login on the TV. ✓ = already has a tile."))
}

/** Chọn một kênh VTVgo bất kỳ cho ô soạn (CleanTV 3.0.0): danh sách từ API của VTVgo, nhóm theo gói, tìm theo tên không dấu. */
@Composable
private fun HopChonKenhGo(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    var tim by remember { mutableStateOf("") }
    val khoa = XaPhuong.boDau(tim.trim().lowercase())
    val ds = remember(khoa, vm.dsKenhGo) {
        if (khoa.isEmpty()) vm.dsKenhGo else vm.dsKenhGo.filter { g -> khoa.split(" ").all { it in XaPhuong.boDau(g.ten.lowercase()) } }
    }
    AlertDialog(
        onDismissRequest = { vm.moChonKenh = false },
        confirmButton = { TextButton(onClick = { vm.moChonKenh = false }) { Text(t("Đóng", "Close")) } },
        title = { Text(t("Kênh trên VTVgo", "Channels on VTVgo")) },
        text = {
            Column {
                OutlinedTextField(tim, { tim = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Tìm kênh (vd: vĩnh long, htv9, quảng ngãi)", "Search (e.g. hanoi, vtv5)")) })
                if (vm.dsKenhGo.isEmpty()) Text(if (vm.dangTaiKenh) t("Đang tải danh sách kênh…", "Loading channels…")
                    else t("Không tải được danh sách kênh. Kiểm tra mạng của điện thoại rồi mở lại.", "Could not load the channel list. Check the phone's internet and try again."),
                    fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                Text(t("Bấm một kênh để chọn cho ô · ✓ = đã có ô · kênh có ổ khoá cần đăng nhập VTVgo trên tivi", "Tap a channel for the tile · ✓ = already has a tile · locked channels need a VTVgo login on the TV"),
                    fontSize = 12.sp, color = ChuPhu, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    var nhomTruoc = ""
                    ds.forEach { g ->
                        if (g.nhom != nhomTruoc) { nhomTruoc = g.nhom; val n = g.nhom; item(key = "n:" + n + g.ma) { Text(n, fontSize = 12.sp, color = Nhan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) } }
                        item(key = g.nhom + g.ma) {
                            val chon = k.kenhGo?.second == g.ma
                            Row(Modifier.fillMaxWidth().clickable { k.chonGo(g.tenO, g.ma); vm.moChonKenh = false }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (chon) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (chon) Xanh else ChuPhu, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(g.ten + (if (g.canDangNhap) " 🔒" else "") + (if (k.daCoGo(g.ma)) " ✓" else ""), fontSize = 14.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        containerColor = The1,
    )
}

/** Khung cam báo điều kiện còn thiếu, kèm một nút làm việc đó ([nut] null = không nút). */
@Composable
private fun CanhBaoKenh(vm: PhienLamViec, chu: String, nut: String? = null, bam: () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Phu(chu, ChuChinh)
            if (nut != null) TextButton(onClick = bam, enabled = !vm.dangBan) { Text(nut) }
        }
    }
