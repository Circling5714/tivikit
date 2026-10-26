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
import vn.tivikit.dulieu.KenhVtvGo
import vn.tivikit.t

/**
 * Màn Kênh nhanh (25/10/2026; 26/10/2026 gom cả ô kênh VTVgo từ màn Home sạch về): ô kênh mở thẳng một kênh, mỗi app
 * xem truyền hình một thẻ — TV360 (ô là app phụ, gán được nút remote) và VTVgo (ô link của Home sạch, có logo kênh).
 */
@Composable
internal fun ManKenhNhanh(vm: PhienLamViec) = Cuon {
    val k = vm.kenhNhanh
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Kênh nhanh", "Quick channel"), style = KieuTieuDeLon)
        Phu(t("Ô kênh trên tivi mở thẳng VTV1, HTV7… trong app xem truyền hình, khỏi phải tìm. Chọn thẻ theo app tivi đang dùng.",
            "Tiles on the TV that open VTV1, HTV7… straight in a TV app, no searching. Pick the tab for the app the TV uses."))
    }
    ChonDoan(listOf("TV360", "VTVgo"), k.nguon) { k.nguon = it }
    if (k.nguon == 0) PhanTv360(vm) else PhanVtvGo(vm)
}

/** Thẻ TV360: các ô kênh (VTV1, VTV3, HTV7…), mỗi ô mở thẳng một kênh, gán được một nút remote. */
@Composable
private fun PhanTv360(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    Phu(t("Mỗi ô kênh (VTV1, VTV3, HTV7…) là một ô trên Home sạch mở thẳng kênh đó trên TV360, gán được một nút remote riêng. Tối đa ${KenhNhanh.SO_O} ô; mỗi ô là một app rất nhỏ TiviKit cài lên tivi.",
        "Each channel tile (VTV1, VTV3, HTV7…) is a clean Home tile that opens that channel in TV360, and can get its own remote button. Up to ${KenhNhanh.SO_O} tiles; each is a tiny app TiviKit installs on the TV."))
    if (!k.coTv360) CanhBaoKenh(vm, t("Tivi chưa có TV360 bản tivi (hoặc đang bị tắt ở Quản lý app).", "The TV doesn't have TV360 for TV (or it's disabled in Manage apps)."),
        t("Mở TV360 trên CH Play của tivi", "Open TV360 in the TV's Play Store")) { k.moCHPlay() }

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

/**
 * Thẻ VTVgo: chọn kênh (VTV1–9, hay kênh địa phương, VTVcab… từ danh sách của VTVgo), mỗi kênh một ô có logo ở đầu dock
 * Home sạch; bấm Lưu mới ghi lên tivi.
 */
@Composable
private fun PhanVtvGo(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    Phu(t("Mỗi kênh chọn thành một ô có logo kênh ở đầu dock Home sạch, bấm là xem luôn kênh đó trên VTVgo. Tối đa ${KenhVtvGo.TOI_DA} ô; ô VTVgo chưa gán được nút remote (ô TV360 thì được).",
        "Each channel you pick becomes a tile with the channel's logo at the start of the clean Home's dock, opening it straight in VTVgo. Up to ${KenhVtvGo.TOI_DA} tiles; VTVgo tiles can't take a remote button yet (TV360 tiles can)."))
    when {
        vm.khongCaiHome -> CanhBaoKenh(vm, t("Máy này không cài được Home sạch nên không có ô kênh VTVgo. Dùng thẻ TV360.",
            "This device can't take the clean Home, so VTVgo tiles aren't possible. Use the TV360 tab."))
        !vm.daCoHomeSach -> CanhBaoKenh(vm, t("Ô kênh VTVgo nằm trên Home sạch, mà tivi này chưa có Home sạch.", "VTVgo tiles live on the clean Home, which this TV doesn't have yet."),
            t("Cài Home sạch", "Install the clean Home")) { k.moHomeSach() }
        k.homeCu -> CanhBaoKenh(vm, t("Home sạch trên tivi là bản cũ, chưa có ô kênh. Mở Home sạch, bấm Áp dụng lên tivi một lần (app tự cập nhật, giữ giao diện), rồi quay lại đây.",
            "The clean Home on the TV is an old version without channel tiles. Open Clean Home, press Apply to the TV once (the app updates it and keeps your look), then come back here."),
            t("Mở Home sạch", "Open Clean Home")) { k.moHomeSach() }
        !vm.coVtvGo -> {
            CanhBaoKenh(vm, t("Tivi chưa có VTVgo bản tivi: cài từ CH Play của tivi, mở VTVgo một lần, rồi mở lại màn này.",
                "The TV doesn't have VTVgo for TV: install it from the TV's Play Store, open it once, then reopen this screen."),
                t("Mở VTVgo trên CH Play của tivi", "Open VTVgo in the TV's Play Store")) { vm.moVtvGoChPlay() }
            if (vm.baoVtvGo.isNotEmpty()) Phu(vm.baoVtvGo, Xanh)
        }
        else -> The(t("Ô kênh VTVgo (${vm.kenhChon.size}/${KenhVtvGo.TOI_DA})", "VTVgo tiles (${vm.kenhChon.size}/${KenhVtvGo.TOI_DA})"), Icons.Rounded.LiveTv) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KenhVtvGo.VTV.forEach { (ten, ma, _) -> ChonChip(ten, vm.coKenh(ma)) { vm.batTatKenh(ten, ma) } }
            }
            // Kênh khác (địa phương, VTVcab…) đã chọn từ hộp danh sách: hiện riêng để bỏ chọn được.
            val khac = vm.kenhChon.filter { (_, ma) -> KenhVtvGo.VTV.none { it.second == ma } }
            if (khac.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                khac.forEach { (ten, ma) -> ChonChip(ten, true) { vm.batTatKenh(ten, ma) } }
            }
            NutPhu(t("Thêm kênh khác trên VTVgo (địa phương, VTVcab…)", "More VTVgo channels (local, VTVcab…)"), Icons.Rounded.Search) { vm.moHopKenh() }
            if (vm.kenhChon.size >= KenhVtvGo.TOI_DA) Phu(t("Tối đa ${KenhVtvGo.TOI_DA} kênh.", "Up to ${KenhVtvGo.TOI_DA} channels."))
            Phu(t("Ô đứng ở đầu dock theo thứ tự chọn. Ảnh ô tải từ VTVgo lúc lưu; không tải được thì ô hiện tên kênh.",
                "Tiles sit at the start of the dock in the order picked. Pictures come from VTVgo when you save; without them the tile shows the channel name."))
            NutChinh(when {
                k.goChuaLuu -> t("Lưu lên tivi", "Save to the TV")
                vm.kenhChon.isEmpty() -> t("Chưa chọn kênh nào", "No channel picked")
                else -> t("Đã lưu trên tivi", "Saved on the TV")
            }, enabled = !vm.dangBan && k.goChuaLuu, icon = Icons.Rounded.Check) { k.luuVtvGo() }
        }
    }
    if (k.baoGo.isNotEmpty()) Phu(k.baoGo, Xanh)
    if (vm.moChonKenh) HopChonKenh(vm)
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
