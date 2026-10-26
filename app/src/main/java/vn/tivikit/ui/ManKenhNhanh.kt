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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.t

/** Màn Kênh nhanh TV360 (25/10/2026): các ô kênh (VTV1, VTV3, HTV7…), mỗi ô mở thẳng một kênh, gán được một nút remote. */
@Composable
internal fun ManKenhNhanh(vm: PhienLamViec) = Cuon {
    val k = vm.kenhNhanh
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Kênh nhanh TV360", "TV360 quick channel"), style = KieuTieuDeLon)
        Phu(t("Mỗi ô kênh (VTV1, VTV3, HTV7…) là một ô trên Home sạch mở thẳng kênh đó trên TV360, gán được một nút remote riêng. Tối đa ${KenhNhanh.SO_O} ô; mỗi ô là một app rất nhỏ TiviKit cài lên tivi.",
            "Each channel tile (VTV1, VTV3, HTV7…) is a clean Home tile that opens that channel in TV360, and can get its own remote button. Up to ${KenhNhanh.SO_O} tiles; each is a tiny app TiviKit installs on the TV."))
    }
    if (!k.coTv360) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Phu(t("Tivi chưa có TV360 bản tivi (hoặc đang bị tắt ở Quản lý app).", "The TV doesn't have TV360 for TV (or it's disabled in Manage apps)."), ChuChinh)
            TextButton(onClick = { k.moCHPlay() }, enabled = !vm.dangBan) { Text(t("Mở TV360 trên CH Play của tivi", "Open TV360 in the TV's Play Store")) }
        }
    }

    The(t("Ô kênh trên tivi (${k.ds.size}/${KenhNhanh.SO_O})", "Channel tiles on the TV (${k.ds.size}/${KenhNhanh.SO_O})"), Icons.Rounded.LiveTv) {
        if (k.ds.isEmpty()) Phu(t("Chưa có ô nào. Chọn kênh ở ô bên dưới rồi bấm Thêm lên tivi.", "No tiles yet. Pick a channel below and tap Add to the TV."))
        k.ds.forEach { o ->
            val dangSua = k.dangSua == o.so
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2)
                .border(1.dp, if (dangSua) Nhan else Color.Transparent, RoundedCornerShape(14.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(o.kenh?.ten ?: t("Ô ${o.so} (chưa đọc được kênh)", "Tile ${o.so} (channel unreadable)"), fontWeight = FontWeight.SemiBold)
                    Phu(listOfNotNull(t("ô ${o.so}", "tile ${o.so}"), o.kenh?.let { t("số ${it.so.trimStart('0')}", "number ${it.so.trimStart('0')}") },
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
        Phu(t("Chọn nhanh:", "Pick one:"))
        KenhNhanh.GOI_Y.chunked(5).forEach { hang ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hang.forEach { g ->
                    val daCo = k.ds.any { it.so != sua && it.kenh?.so == g.so }
                    ChonChip(if (daCo) "${g.ten} ✓" else g.ten, KenhNhanh.chuanSo(k.so) == g.so) { k.chon(g) }
                }
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

    Phu(t("Kênh nhanh dùng cổng nhận lệnh giọng nói của TV360 (cùng đường với \"Xem VTV1 trên TV360\" của Google Assistant), không phải giao diện công khai: bản TV360 sau có thể đổi, khi đó ô chỉ mở TV360 như thường. Hẹn giờ cũng mở được từng ô (VD 18:58 mở VTV1). Khôi phục tivi gỡ hết các ô.",
        "Quick channel uses TV360's voice-command entry (the one Google Assistant uses for \"Watch VTV1 on TV360\"), not a public interface: a later TV360 may change it, and the tiles then just open TV360. The Timer can open each tile too (e.g. 18:58 opens VTV1). Restoring the TV removes all tiles."))
}
