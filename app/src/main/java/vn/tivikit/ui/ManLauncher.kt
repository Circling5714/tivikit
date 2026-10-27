package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import vn.tivikit.dulieu.TiviKitLauncher
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
            TheTiviKitLauncher(vm, ql)
            TheNutHome(vm, ql, tt)
            // TiviKit Launcher có thẻ riêng ở trên.
            val ds = tt.ds.filter { it.goi != TiviKitLauncher.GOI }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t("${ds.size} launcher", "${ds.size} launchers"), fontSize = 13.sp, color = ChuPhu, modifier = Modifier.weight(1f))
                TextButton(onClick = { ql.docLai() }, enabled = !vm.dangBan) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Đọc lại", "Read again"))
                }
            }
            ds.forEach { m -> HangLauncher(vm, ql, tt, m) }
        }
        // Launcher gợi ý (giai đoạn 3): trang CH Play chính thức, cài bằng tài khoản Google của tivi.
        The(t("Launcher gợi ý", "Suggested launchers"), Icons.Rounded.Recommend) {
            Phu(t("Mở trang CH Play trên tivi để cài. Cài thêm launcher thì lần bấm Home sau tivi hỏi chọn Home: cài xong bấm Đọc lại, rồi Đặt làm Home ở dòng của launcher muốn dùng.",
                "Opens the Play Store page on the TV to install. After a new launcher is installed the TV asks which Home to use on the next Home press: tap Read again, then Make it Home on the launcher you want."))
            val caiDat = vm.trangThai?.caiDat.orEmpty()
            vn.tivikit.dulieu.GoiY.DS.filter { it.nhom == vn.tivikit.dulieu.GoiY.Nhom.LAUNCHER }.forEach { g -> HangGoiY(vm, g, g.goi.firstOrNull { it in caiDat }) }
            if (vm.baoGoiY.isNotEmpty()) Phu(vm.baoGoiY, Xanh)
        }
        Phu(t("Launcher của hãng thường được Android ưu tiên hơn launcher tự cài (mức ưu tiên ghi ở từng hàng): muốn launcher tự cài làm Home thì phải tắt launcher của hãng; Google TV còn setupwraith thì không tắt được, Home sạch mở đè lên thay. Mọi thay đổi ghi vào bản sao lưu: Khôi phục tivi trả Home như trước khi dùng TiviKit.",
            "Android usually favours the maker's launcher over ones you install (the priority is shown on each row): for an installed launcher to be Home, the maker's must be disabled; a Google TV with setupwraith can't, so the clean Home opens over it instead. Every change goes into the backup: restoring the TV puts Home back as it was before TiviKit."))
    }
    ql.hoi?.let { HopHoiLauncher(vm, ql, it) }
}

/**
 * Thẻ TiviKit Launcher (27/10/2026): màn Home kính mờ của TiviKit, APK đi kèm app, cài lên tivi từ điện thoại; mở thử, đặt
 * làm Home (qua đúng đường Đặt làm Home của các launcher khác), cập nhật, gỡ.
 */
@Composable
private fun TheTiviKitLauncher(vm: PhienLamViec, ql: QuanLyLauncher) {
    val k = ql.tkl
    val laHome = ql.tklLaHome()
    The("TiviKit Launcher", Icons.Rounded.AutoAwesome) {
        Phu(t("Màn Home kính mờ của TiviKit: đồng hồ lớn, âm lịch, lời chào theo tên, RAM / bộ nhớ trống, nút Dọn RAM, Wi-Fi, dock app như máy Mac. Cài từ điện thoại, không cần cài TiviKit lên tivi.",
            "TiviKit's frosted-glass Home: big clock, lunar date, greeting by name, free RAM / storage, a Free RAM button, Wi-Fi, a Mac-style app dock. Installed from the phone; TiviKit itself isn't needed on the TV."))
        if (k?.daCai == true) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Bản ${k.ban} trên tivi", "Version ${k.ban} on the TV"), fontWeight = FontWeight.SemiBold)
            if (laHome) Chip(t("Nút Home mở", "Home opens it"), Xanh)
            if (k.canCapNhat) Chip(t("Có bản mới", "Update available"), Cam)
        } else Text(t("Chưa cài trên tivi", "Not on the TV yet"), fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fun lam(viec: () -> Unit) { if (!vm.dangBan) viec() }
            if (k?.daCai != true) ChonChip(t("Cài lên tivi", "Install on the TV"), true) { lam { ql.caiTkl() } }
            else {
                if (k.canCapNhat) ChonChip(t("Cập nhật", "Update"), true) { lam { ql.caiTkl() } }
                if (!laHome) ChonChip(t("Đặt làm Home", "Make it Home"), !k.canCapNhat) { lam { ql.datHomeTkl() } }
                ChonChip(t("Mở thử", "Open"), false) { lam { ql.moThuTkl() } }
                if (!laHome) ChonChip(t("Gỡ", "Uninstall"), false) { lam { ql.hoi = QuanLyLauncher.Hoi.GoTkl } }
            }
        }
        if (k?.daCai == true) Phu(t("Trên tivi: OK để mở app, giữ OK để ghim vào dock, dời chỗ, gỡ; ô lưới cuối dock là Tất cả ứng dụng.",
            "On the TV: OK opens an app; hold OK to pin it to the dock, move or uninstall it; the grid at the end of the dock shows all apps."))
    }
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
        if (tt.homeMo != tt.goiMacDinh && tt.goiMacDinh.isNotBlank() && !tt.dangHoiChon) {
            val goc = tenDep(tt.goiMacDinh)
            Phu(if (tt.cua(tt.homeMo)?.loai == Loai.HOME_SACH) t("Android vẫn giữ $goc làm Home mặc định (mức ưu tiên cao hơn); Home sạch tự mở đè lên mỗi khi nó hiện ra.",
                "Android keeps $goc as the default Home (higher priority); the clean Home opens over it whenever it comes up.")
            else t("Android vẫn giữ $goc làm Home mặc định (mức ưu tiên cao hơn); Home sạch mở ${tenDep(tt.homeMo)} đè lên mỗi khi nó hiện ra.",
                "Android keeps $goc as the default Home (higher priority); the clean Home opens ${tenDep(tt.homeMo)} over it whenever it comes up."))
        }
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
                if (ql.coTheDatHome(m)) ChonChip(if (m.dangTat) t("Bật và đặt làm Home", "Enable and make Home") else t("Đặt làm Home", "Make it Home"), true) { lam { ql.chonDatHome(m) } }
                if (!m.dangTat && m.cmp != null) ChonChip(t("Mở thử", "Open"), false) { lam { ql.moThu(m) } }
                if (m.dangTat) ChonChip(t("Bật lại", "Re-enable"), false) { lam { ql.chonBat(m) } }
                else if (ql.lyDoGiu(m) == null) ChonChip(t("Tắt", "Disable"), false) { lam { ql.hoi = QuanLyLauncher.Hoi.Tat(m) } }
                if (m.loai == Loai.CAI_THEM && !laHome && m.goi != tt.goiMacDinh) ChonChip(t("Gỡ", "Uninstall"), false) { lam { ql.hoi = QuanLyLauncher.Hoi.Go(m) } }
            }
            // Dịch vụ trợ năng của launcher (Projectivy tự thay Home của hãng bằng nó) và màn cài đặt riêng của nó.
            val troNang = ql.troNangCua(m)
            if (!m.dangTat && (troNang.isNotEmpty() || m.goi in Launcher.CAI_DAT || m.goi in Launcher.WIDGET)) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun lam(viec: () -> Unit) { if (!vm.dangBan) viec() }
                troNang.forEach { dv ->
                    if (ql.troNangDangBat(dv)) {
                        ChonChip(t("Tắt trợ năng", "Accessibility off"), false) { lam { ql.tatTroNang(m, dv) } }
                        ChonChip(t("Làm mới trợ năng", "Restart accessibility"), false) { lam { ql.batTroNang(m, dv, lamMoi = true) } }
                    } else ChonChip(t("Bật trợ năng", "Accessibility on"), false) { lam { ql.hoi = QuanLyLauncher.Hoi.BatTroNang(m, dv) } }
                }
                if (m.goi in Launcher.CAI_DAT) ChonChip(t("Cài đặt của ${tenDep(m.goi)}", "${tenDep(m.goi)} settings"), false) { lam { ql.moCaiDat(m) } }
                if (m.goi in Launcher.WIDGET) ChonChip(t("Cho phép widget", "Allow widgets"), false) { lam { ql.choPhepWidget(m) } }
            }
            if (!m.dangTat) ql.lyDoGiu(m)?.let { Phu(ql.moTaGiu(it, m)) }
            meoCua(m.goi)?.let { Phu(it) }
        }
    }
}

/**
 * Mẹo riêng cho launcher tự cài (dòng chữ nhỏ dưới hàng thao tác). Tên tuỳ chọn lấy đúng từ Projectivy 4.72
 * (app_settings_launcher_override_home, bản tiếng Việt và tiếng Anh).
 */
private fun meoCua(goi: String): String? = when (goi) {
    "com.spocky.projengmenu" -> t("Projectivy tự thay Home của hãng được: bật trợ năng ở đây, rồi trong cài đặt của nó bật \"Ghi đè trình khởi chạy hiện tại\". Tivi có Home sạch thì chỉ cần Đặt làm Home ở đây: Home sạch mở Projectivy đè lên Home của hãng.",
        "Projectivy can take over the maker's Home by itself: turn its accessibility on here, then \"Override current launcher\" in its settings. With the clean Home on the TV, Make it Home here is enough: the clean Home opens Projectivy over the maker's Home.")
    "me.efesser.flauncher" -> t("FLauncher không tự thay Home của hãng: tivi không cho tắt Home của hãng thì Đặt làm Home ở đây để Home sạch mở FLauncher đè lên.",
        "FLauncher can't take over the maker's Home by itself: where that Home can't be disabled, Make it Home here so the clean Home opens FLauncher over it.")
    "ca.dstudio.atvlauncher.pro" -> t("Thêm widget (đồng hồ, thời tiết) không được vì tivi không có hộp hỏi cho phép: bấm Cho phép widget ở đây một lần rồi thêm lại trong ATV Launcher.",
        "Can't add widgets (clock, weather) because the TV has no allow prompt: tap Allow widgets here once, then add them again in ATV Launcher.")
    "eu.chainfire.tv.sideloadlauncher" -> t("Sideload Launcher liệt kê mọi app trên tivi, cả app cài ngoài không có biểu tượng trên Home.",
        "Sideload Launcher lists every app on the TV, including sideloaded ones with no icon on the Home.")
    else -> null
}

/** Hộp hỏi lại trước khi đổi Home, tắt, bật lại (khi nó giành Home), gỡ, bấm thử nút Home, bật trợ năng của launcher. */
@Composable
private fun HopHoiLauncher(vm: PhienLamViec, ql: QuanLyLauncher, hoi: QuanLyLauncher.Hoi) {
    val tt = ql.tt
    val (tieuDe, noiDung, nut) = when (hoi) {
        is QuanLyLauncher.Hoi.DatHome -> {
            val ten = tenDep(hoi.m.goi)
            val chan = tt?.let { Launcher.chan(hoi.m, it) }.orEmpty()
            val goc = chan.firstOrNull()?.let { tenDep(it) }.orEmpty()
            Triple(t("Đặt $ten làm Home?", "Make $ten the Home?"),
                t("Tivi sẽ mở Cài đặt rồi bấm Home để thử; không được thì app tự trả lại như cũ.", "The TV will open Settings and press Home to test it; if it fails, the app puts everything back.") +
                    (if (hoi.nhuong.isNotEmpty()) t("\n\nAndroid luôn ưu tiên ${hoi.nhuong.joinToString { tenDep(it) }} hơn $ten, nên app tắt ${hoi.nhuong.joinToString { tenDep(it) }} trước. Khôi phục tivi bật lại được; rớt kết nối giữa chừng thì tivi tự bật lại sau 3 phút.",
                        "\n\nAndroid always favours ${hoi.nhuong.joinToString { tenDep(it) }} over $ten, so the app disables it first. Restoring the TV turns it back on; if the connection drops midway, the TV turns it back on by itself after 3 minutes.") else "") +
                    (if (hoi.m.loai == Loai.HOME_SACH && hoi.nhuong.isEmpty() && chan.isNotEmpty()) t("\n\n$goc vẫn là Home mặc định của Android; Home sạch tự mở đè lên nó.",
                        "\n\n$goc stays Android's default Home; the clean Home opens over it.") else "") +
                    (if (hoi.moDe) t("\n\n$goc giữ quyền Home và không tắt được. Home sạch sẽ mở $ten đè lên $goc mỗi khi nó hiện ra, như Home sạch vẫn tự mở chính nó." +
                        if (!ql.homeSachMoi) " App cập nhật Home sạch lên bản mới trước (giữ nguyên giao diện, nút remote)." else "",
                        "\n\n$goc keeps the Home role and can't be disabled. The clean Home will open $ten over $goc whenever it comes up, the way it opens itself now." +
                        if (!ql.homeSachMoi) " The app first updates the clean Home (look and remote buttons are kept)." else "") else "") +
                    (if (hoi.m.loai != Loai.HOME_SACH && !hoi.moDe && !ql.homeSachMoi && tt?.khoaGianh?.isNotEmpty() == true) t("\n\nHome sạch vẫn ở trên tivi, nút remote gán riêng vẫn chạy, nhưng thôi tự mở đè lên Home và thôi tự mở khi bật tivi.",
                        "\n\nThe clean Home stays on the TV and its remote buttons keep working, but it stops opening over the Home and when the TV turns on.") else "") +
                    (if (hoi.m.loai != Loai.HOME_SACH && ql.homeSachMoi && tt?.chuyenDe == hoi.m.goi) t("\n\nHome sạch thôi mở đè lên $ten; nút remote gán riêng vẫn chạy.",
                        "\n\nThe clean Home stops opening over $ten; its remote buttons keep working.") else ""),
                if (hoi.nhuong.isNotEmpty()) t("Tắt và đặt", "Disable and set") else t("Đặt làm Home", "Make it Home"))
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
        QuanLyLauncher.Hoi.GoTkl -> Triple(t("Gỡ TiviKit Launcher?", "Uninstall TiviKit Launcher?"),
            t("Dock đã sắp xếp trên tivi bị xoá; cài lại từ đây lúc nào cũng được.", "The dock you arranged on the TV is removed; you can install it again from here any time."), t("Gỡ", "Uninstall"))
        QuanLyLauncher.Hoi.ThuNut -> Triple(t("Bấm thử nút Home?", "Test the Home button?"),
            t("Tivi sẽ mở Cài đặt rồi bấm Home, như khi bạn bấm Home trên remote: app đang mở trên tivi sẽ bị đóng.", "The TV will open Settings and press Home, like the remote's Home button: whatever is open on the TV gets closed."),
            t("Bấm thử", "Test"))
        is QuanLyLauncher.Hoi.BatTroNang -> Triple(t("Bật trợ năng của ${tenDep(hoi.m.goi)}?", "Turn on ${tenDep(hoi.m.goi)}'s accessibility?"),
            t("Dịch vụ trợ năng cho ${tenDep(hoi.m.goi)} đọc được màn hình và bắt phím remote: launcher dùng nó để tự thay Home của hãng, phím tắt, khoá trẻ em. Chỉ bật cho launcher bạn tin; tắt lại ở đây lúc nào cũng được. Các dịch vụ đang bật (nút remote của Home sạch…) giữ nguyên.",
                "An accessibility service lets ${tenDep(hoi.m.goi)} read the screen and catch remote keys: launchers use it to take over the maker's Home, for shortcuts, kids locks. Only turn it on for a launcher you trust; turn it off here any time. Services already on (the clean Home's remote buttons…) stay on."),
            t("Bật", "Turn on"))
    }
    val chinh = hoi is QuanLyLauncher.Hoi.ThuNut || hoi is QuanLyLauncher.Hoi.DatHome
    AlertDialog(
        onDismissRequest = { ql.hoi = null },
        icon = { Icon(if (chinh) Icons.Rounded.Home else Icons.Rounded.Warning, null, tint = if (chinh) Nhan else Cam) },
        title = { Text(tieuDe) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(noiDung)
                // Tắt được launcher của hãng, nhưng có Home sạch thì cũng có thể giữ nó bật (menu riêng của hãng nằm trong nó)
                // và để Home sạch mở đè — mở launcher đã chọn, hay chính Home sạch như trên Google TV.
                if (hoi is QuanLyLauncher.Hoi.DatHome && hoi.coTheMoDe) {
                    val goc = hoi.nhuong.joinToString { tenDep(it) }
                    TextButton(onClick = { ql.datHome(hoi.m, emptyList()) }, enabled = !vm.dangBan) {
                        Text(if (hoi.m.loai == Loai.HOME_SACH) t("Không tắt $goc: để Home sạch tự mở đè lên nó", "Keep $goc on: let the clean Home open over it")
                            else t("Không tắt $goc: để Home sạch mở ${tenDep(hoi.m.goi)} đè lên", "Keep $goc on: let the clean Home open ${tenDep(hoi.m.goi)} over it"))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (hoi) {
                    is QuanLyLauncher.Hoi.DatHome -> ql.datHome(hoi.m, hoi.nhuong)
                    is QuanLyLauncher.Hoi.Tat -> ql.tat(hoi.m)
                    is QuanLyLauncher.Hoi.BatGianh -> ql.bat(hoi.m)
                    is QuanLyLauncher.Hoi.Go -> ql.go(hoi.m)
                    QuanLyLauncher.Hoi.ThuNut -> ql.thuNut()
                    is QuanLyLauncher.Hoi.BatTroNang -> ql.batTroNang(hoi.m, hoi.dv, lamMoi = false)
                    QuanLyLauncher.Hoi.GoTkl -> ql.goTkl()
                }
            }, enabled = !vm.dangBan) { Text(nut, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = { ql.hoi = null }) { Text(t("Thôi", "Cancel")) } },
        containerColor = The1,
    )
}
