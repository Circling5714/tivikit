package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.KenhFpt
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.dulieu.KenhVtvGo
import vn.tivikit.dulieu.XaPhuong
import vn.tivikit.t

/**
 * Màn Kênh nhanh (25/10/2026; 26/10/2026 thêm ô VTVgo; 31/10/2026 ô FPT Play; 1/11/2026 ô M3U, huy hiệu app ở góc ô, ô Auto,
 * rồi ô IPTV thay ô M3U): các ô kênh (VTV1, VTV3, HTV7…), mỗi ô mở thẳng một kênh trên TV360, VTVgo, FPT Play hoặc danh
 * sách IPTV, gán được một nút remote. Ô soạn chọn app của ô bằng ô chia đoạn TV360 · VTVgo · FPT Play · IPTV · Tự động.
 */
@Composable
internal fun ManKenhNhanh(vm: PhienLamViec) = Cuon {
    val k = vm.kenhNhanh
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Kênh nhanh", "Quick channel"), style = KieuTieuDeLon)
        Phu(t("Mỗi ô kênh là một ô trên Home sạch và TiviKit Launcher, mở thẳng một kênh (VTV1, VTV3, HTV7…) trên TV360, VTVgo, FPT Play hoặc danh sách IPTV (ô Auto: nguồn nào đang phát được), mang logo kênh và biểu tượng app đó ở góc, gán được một nút remote riêng. Tối đa ${KenhNhanh.SO_O} ô; mỗi ô là một app rất nhỏ TiviKit cài lên tivi.",
            "Each channel tile is a tile on the clean Home and TiviKit Launcher that opens one channel (VTV1, VTV3, HTV7…) in TV360, VTVgo, FPT Play or an IPTV playlist (Auto tiles: whichever source plays), with the channel's logo and that app's icon in the corner, and can get its own remote button. Up to ${KenhNhanh.SO_O} tiles; each is a tiny app TiviKit installs on the TV."))
    }
    if (vm.daCoHomeSach && k.homeCu) CanhBaoKenh(vm,
        t("Home sạch trên tivi là bản cũ nên ô chưa hiện logo kênh. Mở Home sạch, bấm Áp dụng lên tivi một lần (app tự cập nhật, giữ giao diện).",
            "The clean Home on the TV is an old version, so tiles don't show channel logos yet. Open Clean Home and press Apply to the TV once (the app updates it and keeps your look)."),
        t("Mở Home sạch", "Open Clean Home")) { k.moHomeSach() }
    if (k.kenhGoCu.isNotEmpty()) CanhBaoKenh(vm,
        t("Home sạch đang có ${k.kenhGoCu.size} ô kênh VTVgo kiểu cũ (${k.kenhGoCu.joinToString { it.first }}), không gán được nút remote. Chuyển sang ô Kênh nhanh để gán nút remote và hẹn giờ.",
            "The clean Home has ${k.kenhGoCu.size} old-style VTVgo tiles (${k.kenhGoCu.joinToString { it.first }}) that can't take a remote button. Move them to Quick channel tiles for remote buttons and the timer."),
        t("Chuyển sang ô Kênh nhanh", "Move to Quick channel tiles")) { k.chuyenKenhCu() }
    if (k.canVeLai) CanhBaoKenh(vm,
        t("Ô kênh giờ có biểu tượng app mở kênh (TV360, VTVgo, FPT Play, IPTV) ở góc phải dưới, nhìn là biết kênh của app nào. Các ô làm trước đây chưa có: vẽ lại ảnh các ô" +
            (if (vm.daCoHomeSach) " (Home sạch trên tivi mở lại một lần)." else "."),
            "Channel tiles now show the icon of the app that opens them (TV360, VTVgo, FPT Play, IPTV) in the bottom-right corner. Tiles made earlier don't have it yet: redraw them" +
                (if (vm.daCoHomeSach) " (the clean Home on the TV reopens once)." else ".")),
        t("Vẽ lại ảnh các ô", "Redraw the tiles")) { k.veLaiAnh() }

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
                        o.kenh?.let {
                            when {
                                it.laAuto -> t("Tự động: ", "Auto: ") + k.tenNguon(it) + (if (it.coDinh) t(" (thứ tự riêng)", " (your order)") else "")
                                !it.laTv360 -> it.tenApp
                                else -> t("TV360 số ${it.so.trimStart('0')}", "TV360 no. ${it.so.trimStart('0')}")
                            }
                        },
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
        ChonDoan(listOf("TV360", "VTVgo", "FPT Play", "IPTV", t("Tự động", "Auto")), k.app) { k.app = it }
        when (k.app) {
            0 -> SoanTv360(vm)
            1 -> SoanVtvGo(vm)
            2 -> SoanFpt(vm)
            3 -> SoanIptv(vm)
            else -> SoanAuto(vm)
        }
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

    Phu(t("Ô TV360 dùng cổng nhận lệnh giọng nói của TV360 (cùng đường với \"Xem VTV1 trên TV360\" của Google Assistant), không phải giao diện công khai: bản TV360 sau có thể đổi, khi đó ô chỉ mở TV360 như thường. Ô VTVgo mở bằng link kênh của VTVgo, ô FPT Play bằng link kênh của FPT Play (fptplay.vn/xem-truyen-hinh/…), ô IPTV bằng app IPTV của TiviKit (phát kênh trong danh sách theo link); ô Auto nhờ Map Voice thử các nguồn đó. Ô M3U làm trước đây vẫn chạy, sửa thì thành ô IPTV. Logo kênh tải từ VTVgo / FPT Play / danh sách IPTV lúc lưu ô (app không đóng sẵn logo của đài; kênh không có logo thì ô ghi tên kênh), biểu tượng app ở góc ô lấy từ chính app đó trên tivi. Hẹn giờ cũng mở được từng ô (VD 18:58 mở VTV1). Khôi phục tivi gỡ hết các ô.",
        "TV360 tiles use TV360's voice-command entry (the one Google Assistant uses for \"Watch VTV1 on TV360\"), not a public interface: a later TV360 may change it, and the tiles then just open TV360. VTVgo tiles open VTVgo's channel link, FPT Play tiles FPT Play's channel link (fptplay.vn/xem-truyen-hinh/…), IPTV tiles TiviKit's IPTV app (it plays the channel from the playlist at the link); Auto tiles let Map Voice try those sources. M3U tiles made earlier still work and become IPTV tiles when edited. Channel logos come from VTVgo / FPT Play / the IPTV playlist when a tile is saved (no station logos are bundled with the app; a channel without one gets its name on the tile), and the app icon in the corner comes from that app on the TV. The Timer can open each tile too (e.g. 18:58 opens VTV1). Restoring the TV removes all tiles."))
    if (vm.moChonKenh) HopChonKenhGo(vm)
    if (k.moChonFpt) HopChonKenhFpt(vm)
    if (k.moChonIptv) HopChonKenhIptv(vm)
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

/** Ô soạn, kênh FPT Play (31/10/2026): kênh hay xem, hoặc kênh khác (địa phương, quốc tế…) trong danh sách kênh của FPT Play. */
@Composable
private fun SoanFpt(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    if (!k.coFpt) CanhBaoKenh(vm, t("Tivi chưa có FPT Play bản tivi: cài từ CH Play của tivi, mở FPT Play một lần và đăng nhập, rồi mở lại màn này.",
        "The TV doesn't have FPT Play for TV: install it from the TV's Play Store, open it once and sign in, then reopen this screen."),
        t("Mở FPT Play trên CH Play của tivi", "Open FPT Play in the TV's Play Store")) { k.moCHPlay(KenhNhanh.FPTPLAY) }
    Phu(t("Chọn kênh:", "Pick a channel:"))
    val dangChon = k.kenhFpt
    KenhFpt.hayXem(k.dsFpt).chunked(5).forEach { hang ->
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hang.forEach { g ->
                ChonChip(g.tenO + (if (g.canGoi) " 💎" else "") + (if (k.daCoFpt(g.ma)) " ✓" else ""), dangChon?.second == g.ma) { k.chonFpt(g.tenO, g.ma) }
            }
        }
    }
    if (dangChon != null) Text(t("Kênh của ô: ${dangChon.first}", "Tile channel: ${dangChon.first}"), fontWeight = FontWeight.SemiBold, color = ChuChinh)
    NutPhu(t("Kênh khác trên FPT Play (địa phương, quốc tế…)", "Other FPT Play channels (local, international…)"), Icons.Rounded.Search) { k.moChonFpt = true }
    Phu(t("Ô mở thẳng kênh trong app FPT Play. Xem kênh cần đăng nhập FPT Play trên tivi: chưa đăng nhập thì FPT Play hỏi đăng nhập. 💎 = kênh cần gói của FPT Play (chưa mua gói thì FPT Play hỏi mua); VTV1, ANTV, QPVN, HaNoiTV và đài địa phương xem được khi đã đăng nhập. ✓ = kênh đã có ô.",
        "The tile opens the channel straight in the FPT Play app. Watching needs an FPT Play login on the TV: if you're signed out, FPT Play asks you to sign in. 💎 = the channel needs an FPT Play package (without one, FPT Play offers to sell it); VTV1, ANTV, QPVN, HaNoiTV and local stations play once signed in. ✓ = already has a tile."))
}

/**
 * Ô soạn, kênh IPTV (1/11/2026): kênh trong danh sách IPTV (.m3u) theo link của người dùng (link rút gọn cũng được), phát
 * bằng app IPTV của TiviKit. App IPTV tự tải lại danh sách theo link, nên danh sách đổi nguồn kênh thì ô vẫn mở được.
 */
@Composable
private fun SoanIptv(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    // Gõ xong (✓ trên bàn phím) hay bấm Tải danh sách thì bỏ chọn ô link: ô còn được chọn thì bàn phím tự hiện lại mỗi lần
    // màn này hiện lại (thử trên Skyworth 1/11/2026).
    val focus = LocalFocusManager.current
    OutlinedTextField(k.linkIptv, { k.linkIptv = it.trim().take(2000) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
        label = { Text(t("Link danh sách IPTV (.m3u, link rút gọn)", "IPTV playlist link (.m3u or short link)")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); k.taiIptv() }))
    NutPhu(t("Tải danh sách", "Load playlist"), Icons.Rounded.Refresh, enabled = !vm.dangBan) { focus.clearFocus(); k.taiIptv() }
    val dangChon = k.kenhIptv
    if (k.dsIptv.isNotEmpty()) {
        Phu(t("Chọn kênh (danh sách có ${k.dsIptv.size} kênh):", "Pick a channel (the playlist has ${k.dsIptv.size}):"))
        // Chọn nhanh: kênh nhóm đầu của danh sách (thường là VTV), còn lại ở hộp tìm kênh.
        val dau = remember(k.dsIptv) { k.dsIptv.filter { it.nhom == k.dsIptv.first().nhom }.take(15) }
        dau.chunked(5).forEach { hang ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hang.forEach { g -> ChonChip(if (k.daCoIptv(g)) "${g.ten} ✓" else g.ten, dangChon == g) { k.chonIptv(g) } }
            }
        }
        NutPhu(t("Kênh khác trong danh sách", "Other channels in the playlist"), Icons.Rounded.Search) { k.moChonIptv = true }
    }
    if (dangChon != null) Text(t("Kênh của ô: ${dangChon.ten}", "Tile channel: ${dangChon.ten}"), fontWeight = FontWeight.SemiBold, color = ChuChinh)
    Phu(t("Dán link danh sách IPTV (link .m3u, link rút gọn cũng được), bấm Tải danh sách rồi chọn kênh. Tivi phát kênh bằng app IPTV của TiviKit (cài lúc lưu ô), theo User-Agent / Referer danh sách ghi cho từng kênh. App IPTV tự tải lại danh sách theo link này (khi danh sách đã cũ hơn 2 giờ, hay khi kênh không phát được), nên nguồn kênh trong danh sách đổi thì ô vẫn mở đúng kênh. Đang xem: lên / xuống đổi kênh trong danh sách, OK xem tên kênh. ✓ = kênh đã có ô.",
        "Paste the IPTV playlist link (an .m3u link or a short link), tap Load playlist, then pick a channel. The TV plays it in TiviKit's IPTV app (installed when the tile is saved), with each channel's User-Agent / Referer as the playlist gives them. The IPTV app reloads the playlist from this link (when it's over 2 hours old, or when a channel won't play), so the tile keeps opening the right channel when the playlist's sources change. While watching: up / down changes channel within the playlist, OK shows the channel name. ✓ = already has a tile."))
}

/**
 * Ô soạn Auto (1/11/2026): một kênh, nhiều nguồn. TiviKit tìm kênh cùng tên trên VTVgo, TV360 (số kênh đã biết), FPT Play,
 * danh sách IPTV và phím tắt M3U; bấm ô thì Map Voice mở lần lượt tới khi có tiếng, nhớ nguồn lên nhanh, mất tiếng 9 giây
 * thì đổi nguồn.
 */
@Composable
private fun SoanAuto(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    if (!k.coMapVoice) CanhBaoKenh(vm, t("Ô Auto cần Map Voice trên tivi để tự chọn và đổi nguồn; chưa có thì ô chỉ mở nguồn đầu như ô thường.",
        "Auto tiles need Map Voice on the TV to pick and switch sources; without it the tile just opens its first source like a normal tile."),
        t("Mở Map Voice", "Open Map Voice")) { vm.mapVoice.mo() }
    else if (k.mapVoiceCu) CanhBaoKenh(vm, t("Map Voice trên tivi là bản cũ, chưa biết nguồn IPTV và thứ tự riêng: mở mục Map Voice, bấm Cài lại / cập nhật.",
        "Map Voice on the TV is an old version that doesn't know IPTV sources or custom orders yet: open Map Voice and tap Reinstall / update."),
        t("Mở Map Voice", "Open Map Voice")) { vm.mapVoice.mo() }
    Phu(t("Chọn nhanh:", "Pick one:"))
    val ten = remember(k.dsM3u) { (KenhNhanh.GOI_Y.map { it.ten } + k.dsM3u.map { it.first }).distinctBy { KenhNhanh.khoaTen(it) } }
    ten.chunked(5).forEach { hang ->
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hang.forEach { g -> ChonChip(if (k.daCoAuto(g)) "$g ✓" else g, KenhNhanh.khoaTen(k.tenAuto) == KenhNhanh.khoaTen(g)) { k.chonAuto(g) } }
        }
    }
    OutlinedTextField(k.tenAuto, { k.tenAuto = it.take(30) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
        label = { Text(t("Tên kênh", "Channel name")) })
    val nguon = k.nguonAuto()
    if (nguon.isEmpty()) Phu(t("Chưa thấy kênh này trên VTVgo, TV360, FPT Play, danh sách IPTV hay phím tắt M3U." +
        (if (k.dsIptv.isEmpty()) " Có danh sách IPTV thì tải ở thẻ IPTV để ô Auto dùng làm nguồn." else ""),
        "This channel isn't on VTVgo, TV360, FPT Play, the IPTV playlist or an M3U shortcut." +
            (if (k.dsIptv.isEmpty()) " If you have an IPTV playlist, load it on the IPTV tab so Auto tiles can use it." else "")), Cam)
    else {
        val rieng = k.thuTuAuto != null
        Text(if (rieng) t("Nguồn, theo thứ tự riêng của ô:", "Sources, in this tile's own order:") else t("Nguồn, thử lần đầu theo thứ tự:", "Sources, first tried in this order:"),
            fontWeight = FontWeight.SemiBold, color = ChuChinh)
        // Dời bằng ↑ ↓ như thứ tự dock, theo loại nguồn (11/2026, anh muốn mỗi kênh một thứ tự: VTV1 mở TV360 trước, VTV6 mở VTVgo trước).
        val loai = nguon.map { it.loai }.distinct()
        nguon.forEachIndexed { i, n ->
            val co = k.coNguon(n.loai)
            val vt = loai.indexOf(n.loai)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nhan.copy(alpha = if (rieng) 0.15f else 0.08f)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Nhan, modifier = Modifier.width(22.dp))
                Text(KenhNhanh.tenAppCua(n.app) + ": " + (if (n.laTv360) t("số ${n.so.trimStart('0')}", "no. ${n.so.trimStart('0')}") else n.ten) +
                    (if (!co) t(" (tivi chưa có app này)", " (not on the TV)") else ""), fontSize = 14.sp, color = if (co) ChuChinh else ChuPhu, modifier = Modifier.weight(1f))
                IconButton(onClick = { k.doiThuTu(n.loai, len = true) }, enabled = vt > 0) { Icon(Icons.Rounded.KeyboardArrowUp, t("Lên", "Up")) }
                IconButton(onClick = { k.doiThuTu(n.loai, len = false) }, enabled = vt < loai.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, t("Xuống", "Down")) }
            }
        }
        if (rieng) NutPhu(t("Về thứ tự tự động", "Back to automatic order"), Icons.Rounded.Refresh) { k.thuTuTuDong() }
    }
    Phu(t("Bấm ô thì Map Voice mở nguồn đầu và chờ có tiếng; không có tiếng thì đóng app đó, mở nguồn kế (trên tivi báo đang chuyển). Để tự động thì Map Voice nhớ nguồn nào lên tiếng nhanh để lần sau mở trước, nguồn vừa lỗi để sau; bấm ↑ ↓ để đặt thứ tự riêng cho kênh này (VD VTV1 mở TV360 trước, VTV6 mở VTVgo trước): Map Voice luôn thử đúng thứ tự đó, cả khi nói \"mở VTV1\" lúc Map Voice để app kênh Tự động. Đang xem mà mất tiếng 9 giây, không ai bấm remote, thì tự đổi sang nguồn kế (tắt được ở mục Map Voice). TV360 chỉ có khi biết số kênh. ✓ = kênh đã có ô Auto.",
        "Pressing the tile makes Map Voice open the first source and wait for sound; if none comes, it closes that app and opens the next (the TV says it's switching). Left automatic, Map Voice remembers which source starts fastest and tries it first next time, leaving failed ones for later; tap ↑ ↓ to give this channel its own order (e.g. VTV1 opens TV360 first, VTV6 opens VTVgo first) and Map Voice always tries exactly that order, also when you say \"mở VTV1\" with Map Voice's channel app set to Auto. If sound stops for 9 seconds while watching and nobody presses the remote, it moves to the next source (can be turned off under Map Voice). TV360 is only used when its channel number is known. ✓ = already has an Auto tile."))
}

/**
 * Một dòng trong hộp chọn kênh: nhóm (gói kênh của VTVgo, nhóm kênh của FPT Play), mã kênh, tên đầy đủ, tên trên ô, dấu sau
 * tên (🔒 kênh VTVgo cần đăng nhập, 💎 kênh FPT Play cần gói).
 */
private data class MucKenh(val nhom: String, val ma: String, val ten: String, val tenO: String, val dau: String = "")

/** Chọn một kênh VTVgo bất kỳ cho ô soạn (CleanTV 3.0.0): danh sách từ API của VTVgo, nhóm theo gói, tìm theo tên không dấu. */
@Composable
private fun HopChonKenhGo(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    val ds = remember(vm.dsKenhGo) { vm.dsKenhGo.map { MucKenh(it.nhom, it.ma, it.ten, it.tenO, if (it.canDangNhap) " 🔒" else "") } }
    HopChonKenh(t("Kênh trên VTVgo", "Channels on VTVgo"), ds, k.kenhGo?.second, k::daCoGo,
        trong = if (vm.dangTaiKenh) t("Đang tải danh sách kênh…", "Loading channels…")
            else t("Không tải được danh sách kênh. Kiểm tra mạng của điện thoại rồi mở lại.", "Could not load the channel list. Check the phone's internet and try again."),
        chuThich = t("Bấm một kênh để chọn cho ô · ✓ = đã có ô · kênh có ổ khoá cần đăng nhập VTVgo trên tivi", "Tap a channel for the tile · ✓ = already has a tile · locked channels need a VTVgo login on the TV"),
        dong = { vm.moChonKenh = false }) { k.chonGo(it.tenO, it.ma) }
}

/** Chọn một kênh bất kỳ trong danh sách IPTV cho ô soạn (1/11/2026), nhóm theo group-title của danh sách. */
@Composable
private fun HopChonKenhIptv(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    // Mã dòng là thứ tự trong danh sách: nhiều kênh IPTV có khi chung một tvg-id. Danh sách có khi xen nhóm (VTV1–4, một
    // kênh nhóm khác, rồi VTV5…): gom kênh cùng nhóm theo thứ tự nhóm xuất hiện, giữ thứ tự trong nhóm.
    val ds = remember(k.dsIptv) {
        val thuTu = HashMap<String, Int>()
        k.dsIptv.forEach { thuTu.putIfAbsent(it.nhom, thuTu.size) }
        k.dsIptv.mapIndexed { i, g -> MucKenh(g.nhom, "$i", g.ten, g.ten) }.sortedBy { thuTu[it.nhom] }
    }
    HopChonKenh(t("Kênh trong danh sách IPTV", "Channels in the IPTV playlist"), ds, k.kenhIptv?.let { "${k.dsIptv.indexOf(it)}" },
        { i -> k.dsIptv.getOrNull(i.toInt())?.let(k::daCoIptv) == true },
        trong = t("Chưa tải danh sách IPTV: dán link rồi bấm Tải danh sách.", "The IPTV playlist isn't loaded: paste the link and tap Load playlist."),
        chuThich = t("Bấm một kênh để chọn cho ô · ✓ = đã có ô", "Tap a channel for the tile · ✓ = already has a tile"),
        dong = { k.moChonIptv = false }) { g -> k.dsIptv.getOrNull(g.ma.toInt())?.let(k::chonIptv) }
}

/** Chọn một kênh FPT Play bất kỳ cho ô soạn (31/10/2026): danh sách đóng sẵn của FPT Play, nhóm theo nhóm kênh của FPT Play. */
@Composable
private fun HopChonKenhFpt(vm: PhienLamViec) {
    val k = vm.kenhNhanh
    val ds = remember(k.dsFpt) { k.dsFpt.map { MucKenh(it.nhom, it.ma, it.ten, it.tenO, if (it.canGoi) " 💎" else "") } }
    HopChonKenh(t("Kênh trên FPT Play", "Channels on FPT Play"), ds, k.kenhFpt?.second, k::daCoFpt,
        trong = t("Không đọc được danh sách kênh FPT Play.", "Couldn't read the FPT Play channel list."),
        chuThich = t("Bấm một kênh để chọn cho ô · ✓ = đã có ô · 💎 = cần gói FPT Play · xem kênh cần đăng nhập FPT Play trên tivi",
            "Tap a channel for the tile · ✓ = already has a tile · 💎 = needs an FPT Play package · watching needs an FPT Play login on the TV"),
        dong = { k.moChonFpt = false }) { k.chonFpt(it.tenO, it.ma) }
}

/** Hộp chọn một kênh trong [ds] (nhóm liền nhau), tìm theo tên không dấu; [maChon] = kênh đang chọn, [daCo] = kênh đã có ô. */
@Composable
private fun HopChonKenh(
    tieuDe: String, ds: List<MucKenh>, maChon: String?, daCo: (String) -> Boolean, trong: String, chuThich: String,
    dong: () -> Unit, chon: (MucKenh) -> Unit,
) {
    var tim by remember { mutableStateOf("") }
    val khoa = XaPhuong.boDau(tim.trim().lowercase())
    val loc = remember(khoa, ds) {
        if (khoa.isEmpty()) ds else ds.filter { g -> khoa.split(" ").all { it in XaPhuong.boDau(g.ten.lowercase()) } }
    }
    AlertDialog(
        onDismissRequest = dong,
        confirmButton = { TextButton(onClick = dong) { Text(t("Đóng", "Close")) } },
        title = { Text(tieuDe) },
        text = {
            Column {
                OutlinedTextField(tim, { tim = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Tìm kênh (vd: vĩnh long, htv9, quảng ngãi)", "Search (e.g. hanoi, vtv5)")) })
                if (ds.isEmpty()) Text(trong, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                Text(chuThich, fontSize = 12.sp, color = ChuPhu, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    var nhomTruoc = ""
                    loc.forEach { g ->
                        if (g.nhom != nhomTruoc) { nhomTruoc = g.nhom; val n = g.nhom; item(key = "n:" + n + g.ma) { Text(n, fontSize = 12.sp, color = Nhan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) } }
                        item(key = g.nhom + g.ma) {
                            val dangChon = maChon == g.ma
                            Row(Modifier.fillMaxWidth().clickable { chon(g); dong() }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (dangChon) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (dangChon) Xanh else ChuPhu, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(g.ten + g.dau + (if (daCo(g.ma)) " ✓" else ""), fontSize = 14.sp, modifier = Modifier.weight(1f))
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
