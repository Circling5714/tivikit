package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.Launcher
import vn.tivikit.dulieu.Launcher.Loai
import vn.tivikit.dulieu.tenDep
import vn.tivikit.t

/**
 * Màn Quản lý launcher (27/10/2026): thẻ Nút Home (đang mở gì, thử bằng nút Home thật, về Home gốc), rồi từng launcher
 * một hàng — bấm để mở các thao tác.
 */
@Composable
internal fun ManLauncher(vm: PhienLamViec) {
    val ql = vm.qlLauncher
    Cuon {
        TieuDeLon(t("Quản lý launcher", "Launchers"), t("Các màn Home trên tivi: chọn launcher cho nút Home, mở thử, tắt / bật lại, gỡ launcher tự cài. Đổi gì cũng thử bằng nút Home thật và tự trả lại nếu hỏng.",
            "The TV's Home screens: pick the launcher for the Home button, open one, disable / re-enable, remove ones you installed. Every change is tested with the real Home button and undone if it fails."))
        if (ql.bao.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nhan.copy(alpha = 0.12f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = Nhan); Spacer(Modifier.width(10.dp))
            Text(ql.bao, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        }
        val tt = ql.tt
        if (tt != null) {
            TheNutHome(vm, ql, tt)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t("${tt.ds.size} launcher", "${tt.ds.size} launchers"), fontSize = 13.sp, color = ChuPhu, modifier = Modifier.weight(1f))
                TextButton(onClick = { ql.docLai() }, enabled = !vm.dangBan) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Đọc lại", "Read again"))
                }
            }
            tt.ds.forEach { m -> HangLauncher(vm, ql, tt, m) }
        }
        Phu(t("Launcher của hãng thường được Android ưu tiên hơn launcher tự cài (mức ưu tiên ghi ở từng hàng): muốn launcher tự cài làm Home thì phải tắt launcher của hãng; Google TV còn setupwraith thì không tắt được, Home sạch mở đè lên thay. Mọi thay đổi ghi vào bản sao lưu: Khôi phục tivi trả Home như trước khi dùng TiviKit.",
            "Android usually favours the maker's launcher over ones you install (the priority is shown on each row): for an installed launcher to be Home, the maker's must be disabled; a Google TV with setupwraith can't, so the clean Home opens over it instead. Every change goes into the backup: restoring the TV puts Home back as it was before TiviKit."))
    }
    ql.hoi?.let { HopHoiLauncher(vm, ql, it) }
}

/** Thẻ Nút Home: nút Home đang mở gì, vì sao, kết quả lần thử gần nhất; Thử nút Home, Về Home gốc. */
@Composable
private fun TheNutHome(vm: PhienLamViec, ql: QuanLyLauncher, tt: Launcher.TrangThai) {
    The(t("Nút Home trên remote", "The remote's Home button"), Icons.Rounded.Home) {
        Text(when {
            tt.homeMo == "android" -> t("Tivi đang hỏi chọn Home mỗi lần", "The TV asks which Home to use")
            tt.homeMo.isBlank() -> t("Không đọc được Home của tivi", "Couldn't read the TV's Home")
            else -> t("Mở ${tenDep(tt.homeMo)}", "Opens ${tenDep(tt.homeMo)}")
        }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (tt.homeMo != tt.goiMacDinh && tt.goiMacDinh.isNotBlank() && !tt.dangHoiChon)
            Phu(t("Android vẫn giữ ${tenDep(tt.goiMacDinh)} làm Home mặc định (mức ưu tiên cao hơn); Home sạch tự mở đè lên mỗi khi nó hiện ra.",
                "Android keeps ${tenDep(tt.goiMacDinh)} as the default Home (higher priority); the clean Home opens over it whenever it comes up."))
        if (tt.dangHoiChon) Phu(t("Thường do vừa cài hay bật thêm một launcher. Chọn một launcher bên dưới, bấm Đặt làm Home.",
            "Usually because a launcher was just installed or enabled. Pick one below and tap Make it Home."))
        tt.troNangKhac.forEach { g ->
            Phu(t("${tenDep(g)} đang bật dịch vụ trợ năng: nó có thể tự bắt nút Home.", "${tenDep(g)} has its accessibility service on: it may take the Home button itself."), Cam)
        }
        ql.daThu?.let { g ->
            val ten = if (g.isBlank()) t("một màn không rõ", "an unknown screen") else tenDep(g)
            Phu(t("Vừa bấm thử: nút Home mở $ten.", "Just tested: Home opened $ten.") +
                if (g != tt.homeMo) t(" Khác với cài đặt của tivi: có app khác đang bắt nút Home (xem các dòng trên).", " That differs from the TV's settings: another app is catching the Home button (see above).") else "",
                if (g == tt.homeMo) Xanh else Cam)
        }
        NutPhu(t("Bấm thử nút Home", "Test the Home button"), Icons.Rounded.TouchApp, enabled = !vm.dangBan) { ql.hoi = QuanLyLauncher.Hoi.ThuNut }
        val goc = ql.homeGoc()
        if (goc != null && goc.goi != tt.homeMo) TextButton(onClick = { ql.chonDatHome(goc) }, enabled = !vm.dangBan) {
            Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(t("Về ${tenDep(goc.goi)}", "Back to ${tenDep(goc.goi)}"))
        }
    }
}

/** Một launcher: tên, loại, bản, mức ưu tiên, trạng thái; bấm để mở thao tác. */
@Composable
private fun HangLauncher(vm: PhienLamViec, ql: QuanLyLauncher, tt: Launcher.TrangThai, m: Launcher.Muc) {
    val mo = ql.dangMo == m.goi
    val laHome = ql.laHome(m)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).border(1.5.dp, if (mo) Nhan else Color.Transparent, RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxWidth().clickable { ql.dangMo = if (mo) null else m.goi }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, mau) = when (m.loai) {
                Loai.HOME_SACH -> Icons.Rounded.Palette to MauO.cham
                Loai.CAI_THEM -> Icons.Rounded.Dashboard to MauO.tim
                Loai.GOC -> Icons.Rounded.Tv to MauO.xam
            }
            OIcon(icon, 40.dp, if (m.dangTat) MauO.xam.copy(alpha = 0.5f) else mau)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tenDep(m.goi), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (m.dangTat) ChuPhu else ChuChinh)
                if (tenDep(m.goi) != m.goi) Text(m.goi, fontSize = 11.sp, color = ChuPhu, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Phu(listOfNotNull(
                    when (m.loai) { Loai.HOME_SACH -> "TiviKit"; Loai.CAI_THEM -> t("tự cài", "installed"); Loai.GOC -> t("của hãng", "maker's") },
                    ql.ban[m.goi]?.let { "v$it" },
                    t("ưu tiên ${m.uuTien}", "priority ${m.uuTien}"),
                ).joinToString(" · "))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        m.dangTat -> Chip(t("Đang tắt", "Disabled"), Cam)
                        laHome -> Chip(t("Nút Home mở", "Home opens it"), Xanh)
                        m.goi == tt.goiMacDinh -> Chip(t("Home mặc định", "Default Home"))
                    }
                    if (m.goi in tt.troNangKhac) Chip(t("Trợ năng bật", "Accessibility on"), Cam)
                }
            }
            Icon(if (mo) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = ChuPhu)
        }
        if (mo) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun lam(viec: () -> Unit) { if (!vm.dangBan) viec() }
                if (!laHome) ChonChip(if (m.dangTat) t("Bật và đặt làm Home", "Enable and make Home") else t("Đặt làm Home", "Make it Home"), true) { lam { ql.chonDatHome(m) } }
                if (!m.dangTat && m.cmp != null) ChonChip(t("Mở thử", "Open"), false) { lam { ql.moThu(m) } }
                if (m.dangTat) ChonChip(t("Bật lại", "Re-enable"), false) { lam { ql.chonBat(m) } }
                else if (ql.lyDoGiu(m) == null) ChonChip(t("Tắt", "Disable"), false) { lam { ql.hoi = QuanLyLauncher.Hoi.Tat(m) } }
                if (m.loai == Loai.CAI_THEM && !laHome && m.goi != tt.goiMacDinh) ChonChip(t("Gỡ", "Uninstall"), false) { lam { ql.hoi = QuanLyLauncher.Hoi.Go(m) } }
            }
            if (!m.dangTat) ql.lyDoGiu(m)?.let { Phu(ql.moTaGiu(it, m)) }
        }
    }
}

/** Hộp hỏi lại trước khi đổi Home, tắt, bật lại (khi nó giành Home), gỡ, bấm thử nút Home. */
@Composable
private fun HopHoiLauncher(vm: PhienLamViec, ql: QuanLyLauncher, hoi: QuanLyLauncher.Hoi) {
    val tt = ql.tt
    val (tieuDe, noiDung, nut) = when (hoi) {
        is QuanLyLauncher.Hoi.DatHome -> {
            val ten = tenDep(hoi.m.goi)
            val chan = tt?.let { Launcher.chan(hoi.m, it) }.orEmpty()
            Triple(t("Đặt $ten làm Home?", "Make $ten the Home?"),
                t("Tivi sẽ mở Cài đặt rồi bấm Home để thử; không được thì app tự trả lại như cũ.", "The TV will open Settings and press Home to test it; if it fails, the app puts everything back.") +
                    (if (hoi.nhuong.isNotEmpty()) t("\n\nAndroid luôn ưu tiên ${hoi.nhuong.joinToString { tenDep(it) }} hơn $ten, nên app tắt ${hoi.nhuong.joinToString { tenDep(it) }} trước. Khôi phục tivi bật lại được; rớt kết nối giữa chừng thì tivi tự bật lại sau 3 phút.",
                        "\n\nAndroid always favours ${hoi.nhuong.joinToString { tenDep(it) }} over $ten, so the app disables it first. Restoring the TV turns it back on; if the connection drops midway, the TV turns it back on by itself after 3 minutes.") else "") +
                    (if (hoi.m.loai == Loai.HOME_SACH && hoi.nhuong.isEmpty() && chan.isNotEmpty()) t("\n\n${chan.joinToString { tenDep(it) }} vẫn là Home mặc định của Android; Home sạch tự mở đè lên nó.",
                        "\n\n${chan.joinToString { tenDep(it) }} stays Android's default Home; the clean Home opens over it.") else "") +
                    (if (hoi.m.loai != Loai.HOME_SACH && tt?.khoaGianh?.isNotEmpty() == true) t("\n\nHome sạch vẫn ở trên tivi, nút remote gán riêng vẫn chạy, nhưng thôi tự mở đè lên Home và thôi tự mở khi bật tivi.",
                        "\n\nThe clean Home stays on the TV and its remote buttons keep working, but it stops opening over the Home and when the TV turns on.") else ""),
                t("Đặt làm Home", "Make it Home"))
        }
        is QuanLyLauncher.Hoi.Tat -> Triple(t("Tắt ${tenDep(hoi.m.goi)}?", "Disable ${tenDep(hoi.m.goi)}?"),
            t("Launcher này không chạy và không nhận nút Home nữa. Bật lại ở đây hoặc bằng Khôi phục tivi.", "This launcher stops running and no longer takes the Home button. Re-enable it here or by restoring the TV."),
            t("Tắt", "Disable"))
        is QuanLyLauncher.Hoi.BatGianh -> {
            val cu = tt?.homeMo?.takeIf { it.isNotBlank() && it != "android" }?.let { tenDep(it) } ?: t("Home hiện tại", "the current Home")
            Triple(t("Bật lại ${tenDep(hoi.m.goi)}?", "Re-enable ${tenDep(hoi.m.goi)}?"),
                t("${tenDep(hoi.m.goi)} được Android ưu tiên hơn $cu (mức ${hoi.m.uuTien}): bật lên là nút Home mở nó thay vì $cu. Muốn giữ Home như bây giờ thì để nó tắt.",
                    "Android favours ${tenDep(hoi.m.goi)} over $cu (priority ${hoi.m.uuTien}): once enabled, Home opens it instead of $cu. To keep Home as it is, leave it disabled."),
                t("Bật lại", "Re-enable"))
        }
        is QuanLyLauncher.Hoi.Go -> Triple(t("Gỡ ${tenDep(hoi.m.goi)}?", "Uninstall ${tenDep(hoi.m.goi)}?"),
            t("Launcher và dữ liệu của nó (bố cục, cài đặt) bị xoá khỏi tivi.", "The launcher and its data (layout, settings) are removed from the TV."), t("Gỡ", "Uninstall"))
        QuanLyLauncher.Hoi.ThuNut -> Triple(t("Bấm thử nút Home?", "Test the Home button?"),
            t("Tivi sẽ mở Cài đặt rồi bấm Home, như khi bạn bấm Home trên remote: app đang mở trên tivi sẽ bị đóng.", "The TV will open Settings and press Home, like the remote's Home button: whatever is open on the TV gets closed."),
            t("Bấm thử", "Test"))
    }
    AlertDialog(
        onDismissRequest = { ql.hoi = null },
        icon = { Icon(if (hoi is QuanLyLauncher.Hoi.ThuNut || hoi is QuanLyLauncher.Hoi.DatHome) Icons.Rounded.Home else Icons.Rounded.Warning, null, tint = if (hoi is QuanLyLauncher.Hoi.ThuNut || hoi is QuanLyLauncher.Hoi.DatHome) Nhan else Cam) },
        title = { Text(tieuDe) },
        text = { Text(noiDung) },
        confirmButton = {
            TextButton(onClick = {
                when (hoi) {
                    is QuanLyLauncher.Hoi.DatHome -> ql.datHome(hoi.m, hoi.nhuong)
                    is QuanLyLauncher.Hoi.Tat -> ql.tat(hoi.m)
                    is QuanLyLauncher.Hoi.BatGianh -> ql.bat(hoi.m)
                    is QuanLyLauncher.Hoi.Go -> ql.go(hoi.m)
                    QuanLyLauncher.Hoi.ThuNut -> ql.thuNut()
                }
            }, enabled = !vm.dangBan) { Text(nut, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = { ql.hoi = null }) { Text(t("Thôi", "Cancel")) } },
        containerColor = The1,
    )
}
