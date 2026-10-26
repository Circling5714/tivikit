package vn.tivikit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.BuildConfig
import vn.tivikit.ManHinh
import vn.tivikit.R
import vn.tivikit.dulieu.DuLieu
import vn.tivikit.t

/** Một dòng chức năng ở màn chính; [mau] = màu ô biểu tượng; [bat] = false thì mờ, không bấm được (VD Home sạch trên Fire TV). */
private class MucChucNang(val icon: ImageVector, val mau: Color, val ten: String, val moTa: String, val bat: Boolean = true, val mo: () -> Unit)

/**
 * Màn chính kiểu trung tâm (25/10/2026), dáng màn Cài đặt của iPhone: tiêu đề lớn, thẻ tivi (kết nối một lần cho mọi
 * chức năng), thẻ Dọn quảng cáo nổi bật, rồi các nhóm danh sách có ô biểu tượng màu. Bấm chức năng lúc chưa nối thì kết
 * nối trước, nối xong tự mở chức năng đó.
 */
@Composable
internal fun ManChinh(vm: PhienLamViec) = Cuon {
    // Tiêu đề lớn + nút ngôn ngữ cùng hàng (như ảnh đại diện cạnh tiêu đề ở App Store).
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo_tivikit), null, Modifier.size(42.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("TiviKit", style = KieuTieuDeLon)
            Text(t("Bộ công cụ cho Android TV", "The Android TV toolkit"), fontSize = 13.sp, color = ChuPhu)
        }
        NutNgonNgu()
    }
    TheBanCu(vm)
    if (vm.baoChuyen.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nhan.copy(alpha = 0.12f)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.SwapHoriz, null, tint = Nhan); Spacer(Modifier.width(10.dp))
        Text(vm.baoChuyen, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        IconButton(onClick = { vm.baoChuyen = "" }) { Icon(Icons.Rounded.Close, t("Ẩn", "Hide"), tint = ChuPhu) }
    }
    if (vm.daTuTatAdb || vm.daTatAdb) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nhan.copy(alpha = 0.12f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = Nhan); Spacer(Modifier.width(10.dp))
        Text(if (vm.daTuTatAdb) t("Đã tự tắt ADB trên tivi khi bạn rời app. Lần sau dùng lại thì bật Gỡ lỗi USB trên tivi.", "ADB was turned off on the TV when you left the app. Next time, turn USB debugging on again on the TV.")
            else t("Đã tắt ADB trên tivi: không ai cùng Wi-Fi điều khiển được tivi nữa. Lần sau dùng lại thì bật Gỡ lỗi USB trên tivi.", "ADB is off on the TV: nobody on the Wi-Fi can control it now. Next time, turn USB debugging on again on the TV."),
            fontSize = 13.sp, lineHeight = 18.sp)
    }
    vm.banMoi?.let { b ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nhan.copy(alpha = 0.12f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Nhan); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Có bản mới ${b.versionName}", "Update ${b.versionName} available"), fontWeight = FontWeight.SemiBold)
                // Nhật ký đầy đủ ở thông báo / trang GitHub; banner chỉ vài dòng đầu.
                if (b.ghiChu.isNotBlank()) Phu(b.ghiChu.lines().filter { it.isNotBlank() }.take(4).joinToString("\n"))
            }
            TextButton(onClick = { vm.capNhatApp() }, enabled = !vm.dangCapNhat) {
                Text(if (vm.dangCapNhat) t("Đang tải…", "Downloading…") else t("Cập nhật", "Update"))
            }
        }
    }

    TheTivi(vm)
    // Người dùng lần đầu: khung cam hướng dẫn bật gỡ lỗi. Đã từng nối tivi: chỉ một dòng nhắc.
    if (vm.thongTin == null) {
        if (vm.tiviGanDay.isEmpty() && vm.dsSaoLuu.isEmpty()) TheNhacNpt(vm)
        else TextButton(onClick = { vm.moHuongDan() }) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text(t("Tivi chưa bật Gỡ lỗi? Xem hướng dẫn 7 bước", "Debugging not on yet? See the 7-step guide"), fontSize = 13.sp)
        }
    }

    TieuDeMuc(t("Dọn tivi", "Clean up"))
    ODonQuangCao(vm)
    NhomChucNang(vm, listOf(
        MucChucNang(Icons.Rounded.Palette, MauO.cham, t("Home sạch", "Clean Home"),
            if (vm.daCoHomeSach) t("Đổi giao diện, dock, thời tiết", "Theme, dock, weather") else t("Màn Home gọn, lịch âm, thời tiết", "A tidy Home with weather"),
            bat = !vm.khongCaiHome) {
            vm.moChucNang {
                // Bấm lúc chưa nối thì chưa biết là Fire TV / Android cũ: xét lại sau khi nối.
                when {
                    vm.khongCaiHome -> vm.loi = if (vm.laFireTv) t("Fire TV không thay được màn Home.", "Fire TV can't change its Home.")
                        else t("Máy này cần Android 8 trở lên mới cài được Home sạch.", "This device needs Android 8 or later for the clean Home.")
                    vm.daCoHomeSach -> vm.moDoiTheme()
                    else -> vm.boQuaDon(Man.CHINH)
                }
            }
        },
        MucChucNang(Icons.Rounded.Restore, MauO.cam, t("Khôi phục", "Restore"), t("Trả tivi về như trước khi dọn", "Put the TV back as it was")) {
            vm.moChucNang { vm.man = Man.KHOI_PHUC }
        },
    ))

    // Công cụ chia hai nhóm như màn Cài đặt của iPhone: việc hằng ngày, và chăm sóc tivi.
    TieuDeMuc(t("Tiện ích", "Utilities"))
    val laTv = ManHinh.laTv
    NhomChucNang(vm, listOfNotNull(
        if (laTv) null else MucChucNang(Icons.Rounded.SettingsRemote, MauO.xam, t("Điều khiển + gõ chữ", "Remote + typing"),
            t("Remote trên điện thoại, gõ mật khẩu, tìm kiếm", "A remote on the phone; type passwords, searches")) { vm.moChucNang { vm.man = Man.DIEU_KHIEN } },
        MucChucNang(Icons.Rounded.AddToQueue, MauO.xanh, t("Cài app", "Install apps"), t("File APK, đường link, gợi ý app, cập nhật app", "APK files, links, suggestions, app updates")) { vm.moChucNang { vm.moCaiApk() } },
        MucChucNang(Icons.Rounded.SmartButton, MauO.tim, t("Nút remote", "Remote buttons"), t("Gán nút YouTube… sang app khác, VD SmartTube", "Make the YouTube button… open another app, e.g. SmartTube")) { vm.moChucNang { vm.ganPhim.mo() } },
        MucChucNang(Icons.Rounded.LiveTv, MauO.doTuoi, t("Kênh nhanh TV360", "TV360 quick channel"), t("Ô / nút remote mở thẳng VTV1, VTV3, HTV7…", "Tiles / remote buttons open VTV1, VTV3, HTV7… directly")) { vm.moChucNang { vm.kenhNhanh.mo() } },
        MucChucNang(Icons.Rounded.Alarm, MauO.cam, t("Hẹn giờ", "Timer"), t("Bật / tắt tivi, mở app theo giờ", "Turn the TV on / off, open apps on time")) { vm.moChucNang { vm.henGio.mo() } },
        MucChucNang(Icons.Rounded.Lock, MauO.cham, t("Khoá trẻ em", "Kids lock"), t("Hẹn giờ khoá tivi, mở bằng PIN", "Lock the TV on a schedule, PIN to unlock")) { vm.moChucNang { vm.moKhoaTre() } },
        if (laTv) null else MucChucNang(Icons.Rounded.PhotoLibrary, MauO.hong, t("Gửi ảnh, video", "Send photos, videos"), t("Gửi file lên tivi rồi mở xem", "Send files to the TV and open them")) { vm.moChucNang { vm.guiFile.mo() } },
        MucChucNang(Icons.Rounded.Screenshot, MauO.ngoc, t("Xem màn hình tivi", "TV screenshot"), t("Chụp màn hình đang hiện, lưu / chia sẻ", "Capture what's on screen; save or share")) { vm.moChucNang { vm.chupManHinh() } },
        MucChucNang(Icons.Rounded.Inventory2, MauO.tim, t("Bộ cài đặt mẫu", "Setup sets"), t("Cài một lượt cho một hay nhiều tivi", "Set up one or many TVs in one go")) { vm.boCai.mo() },
    ))

    TieuDeMuc(t("Chăm sóc tivi", "TV care"))
    NhomChucNang(vm, listOf(
        MucChucNang(Icons.Rounded.Speed, MauO.la, t("Làm mới tivi", "Refresh the TV"), t("RAM, CPU, bộ nhớ; app nào làm tivi chậm, đóng app nằm nền", "RAM, CPU, storage; what's slowing the TV, close background apps")) { vm.moChucNang { vm.lamMoi.mo() } },
        MucChucNang(Icons.Rounded.NetworkCheck, MauO.xanh, t("Kiểm tra mạng", "Network check"), t("Sóng Wi-Fi, độ trễ; vì sao YouTube giật, CH Play báo lỗi", "Wi-Fi signal, latency; why YouTube stutters or the Play Store fails")) { vm.moChucNang { vm.mang.mo() } },
        MucChucNang(Icons.Rounded.Tune, MauO.xam, t("Cài đặt nhanh", "Quick settings"), t("Hiệu ứng nhanh hơn, tự tắt sau 4 giờ, màn hình chờ", "Faster animations, 4-hour auto-off, screensaver")) { vm.moChucNang { vm.caiDatNhanh.mo() } },
        MucChucNang(Icons.Rounded.Apps, MauO.ngoc, t("Quản lý app", "Manage apps"), t("Dung lượng, app chạy ngầm tốn RAM, xoá cache, gỡ", "Size, background RAM, clear cache, uninstall")) { vm.moChucNang { vm.qlApp.mo() } },
        MucChucNang(Icons.Rounded.Shield, MauO.la, t("DNS chặn quảng cáo", "Ad-blocking DNS"), t("AdGuard, NextDNS… cho mọi app trên tivi", "AdGuard, NextDNS… for every app on the TV")) { vm.moChucNang { vm.dnsTivi.mo() } },
    ))

    // Tivi LG webOS (25/10/2026): không qua ADB, không cần nối tivi Android ở trên.
    if (!ManHinh.laTv) {
        TieuDeMuc(t("Tivi LG", "LG TVs"))
        NhomChucNang(vm, listOf(
            MucChucNang(Icons.Rounded.Tv, MauO.doTuoi, t("Tivi LG (webOS)", "LG TV (webOS)"),
                t("Điều khiển, gõ tiếng Việt có dấu, mở app, YouTube, nguồn HDMI, tắt màn hình", "Remote, typing with accents, apps, YouTube, HDMI inputs, screen off")) { vm.lg.mo() },
        ))
    }

    if (vm.dsSaoLuu.isNotEmpty()) {
        TieuDeMuc(t("Tivi đã dọn", "Cleaned TVs"))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1)) {
            vm.dsSaoLuu.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 62.dp), thickness = 0.5.dp, color = Vien)
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OIcon(Icons.Rounded.Tv, 34.dp, NenXam)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.ten, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Phu("${b.ip} · ${ngay(b.luc)}")
                        Phu(t("tắt ${b.daTatBoiApp.size} gói", "${b.daTatBoiApp.size} disabled") + if (b.launcherDaCai) t(" · Home sạch", " · Clean Home") else "")
                    }
                    TextButton(onClick = { vm.moKhoiPhuc(b) }, enabled = !vm.dangBan) { Text(t("Khôi phục", "Restore")) }
                }
            }
        }
    }
    // Nhóm của chính app: dữ liệu trên điện thoại, giấy phép. Không cần nối tivi.
    TieuDeMuc("TiviKit")
    NhomChucNang(vm, listOfNotNull(
        MucChucNang(Icons.Rounded.SettingsBackupRestore, MauO.xanh, t("Sao lưu & chuyển máy", "Back up & move phones"),
            t("Lưu bản sao lưu tivi, bộ cài đặt mẫu ra file, nhập trên điện thoại khác", "Save TV backups and setup sets to a file, import on another phone")) { vm.chuyenMay.mo() },
        MucChucNang(Icons.Rounded.SystemUpdate, MauO.la, t("Cập nhật TiviKit", "TiviKit updates"),
            t("Bản ${BuildConfig.VERSION_NAME} · tự cài bản mới: ", "Version ${BuildConfig.VERSION_NAME} · auto-install: ") + if (vm.tuCai) t("bật", "on") else t("tắt", "off")) { vm.moCapNhat = true },
        MucChucNang(Icons.Rounded.Forum, MauO.xanh, t("Góp ý & liên hệ", "Feedback & contact"),
            t("Nhóm Telegram t.me/tivikit, báo lỗi trên GitHub", "Telegram group t.me/tivikit, report bugs on GitHub")) { vm.moLienHe = true },
        // Chỉ hiện khi đã dọn ít nhất một tivi: danh sách lấy từ bản sao lưu, không cần nối tivi.
        if (vm.dsSaoLuu.none { it.daTatBoiApp.isNotEmpty() }) null else MucChucNang(Icons.Rounded.VolunteerActivism, MauO.la,
            t("Gửi danh sách cho CleanYourTV", "Share your list with CleanYourTV"),
            t("Các gói bạn đã tắt, giúp người cùng mẫu tivi", "The packages you disabled, for owners of the same TV")) { vm.gopNguoc.mo() },
        MucChucNang(Icons.Rounded.Favorite, MauO.hong, t("Ủng hộ tác giả", "Support the author"),
            t("Chuyển khoản MB Bank, có nút chép số tài khoản", "Bank transfer (MB Bank), with a copy button")) { vm.moUngHo = true },
        MucChucNang(Icons.Rounded.Gavel, MauO.xam, t("Giấy phép mã nguồn mở", "Open-source licenses"), t("CleanYourTV, CleanTV, thư viện, phông chữ", "CleanYourTV, CleanTV, libraries, fonts")) { vm.moGiayPhep() },
    ))
    if (vm.moCapNhat) HopCapNhat(vm)
    // Chân trang nhỏ, chữ xám ở giữa như cuối màn Cài đặt.
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NutGopY(vm)
        Text(t("Dựa trên CleanYourTV (github.com/Gathin23/cleanyourtv, MIT) của Gathin và cộng đồng: ${DuLieu.SO_MAU} danh sách tivi và màn Home sạch là của dự án đó.",
            "Built on CleanYourTV (github.com/Gathin23/cleanyourtv, MIT) by Gathin and contributors: the ${DuLieu.SO_MAU} TV lists and the clean Home come from that project.") +
            "\nTiviKit ${BuildConfig.VERSION_NAME} · " + t("dựa trên CleanTV 2.8.2 của AlexVGS (MIT), kèm các bản sửa đến 3.0.0",
                "based on CleanTV 2.8.2 by AlexVGS (MIT), with fixes up to 3.0.0"),
            fontSize = 12.sp, lineHeight = 17.sp, color = ChuPhu, textAlign = TextAlign.Center)
    }
}

/**
 * Hộp Cập nhật TiviKit (26/10/2026): bản đang dùng, công tắc tự cài bản mới (Android 12+ không cần bấm), kiểm tra ngay,
 * nhật ký các bản trên GitHub. Có bản mới thì nút chính thành "Cập nhật lên …".
 */
@Composable
private fun HopCapNhat(vm: PhienLamViec) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val b = vm.banMoi
    AlertDialog(
        onDismissRequest = { vm.moCapNhat = false },
        icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = Nhan) },
        title = { Text(t("Cập nhật TiviKit", "TiviKit updates")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Đang dùng bản ${BuildConfig.VERSION_NAME}", "Running version ${BuildConfig.VERSION_NAME}"), fontWeight = FontWeight.SemiBold)
                Hang(t("Tự cài bản mới", "Install updates automatically"),
                    t("Android 12 trở lên: tự tải và cài lúc không mở app, không cần bấm. Máy cũ hơn: báo có bản mới, chạm một lần để cài.",
                        "Android 12+: downloads and installs while the app is closed, no tap needed. Older Android: you're notified and tap once to install."),
                    vm.tuCai) { vm.datTuCai(it) }
                if (b != null) Phu(t("Có bản mới ${b.versionName}", "Version ${b.versionName} is available") + if (b.ghiChu.isNotBlank()) ":\n" + b.ghiChu else "", ChuChinh)
                else if (vm.baoKiemTra.isNotEmpty()) Phu(vm.baoKiemTra)
                TextButton(onClick = { runCatching { uri.openUri(vn.tivikit.CapNhat.TRANG) } }) { Text(t("Nhật ký các bản trên GitHub", "Release notes on GitHub")) }
            }
        },
        confirmButton = {
            if (b != null) TextButton(onClick = { vm.moCapNhat = false; vm.capNhatApp() }, enabled = !vm.dangCapNhat) {
                Text(t("Cập nhật lên ${b.versionName}", "Update to ${b.versionName}"), fontWeight = FontWeight.SemiBold)
            } else TextButton(onClick = { vm.kiemTraBanMoi() }, enabled = !vm.dangKiemTraBan) {
                Text(if (vm.dangKiemTraBan) t("Đang kiểm tra…", "Checking…") else t("Kiểm tra ngay", "Check now"), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = { vm.moCapNhat = false }) { Text(t("Đóng", "Close")) } },
        containerColor = The1,
    )
}

/**
 * Nhắc gỡ TiviKit bản cũ (tên gói com.icm.cleantv) còn trên máy (25/10/2026, đổi tên gói sang vn.tivikit): hai app cùng
 * tên dễ mở nhầm. Mở trang thông tin app của bản cũ để bấm Gỡ (không cần quyền gỡ app); quay lại app thì xem lại.
 */
@Composable
private fun TheBanCu(vm: PhienLamViec) {
    val vongDoi = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(vongDoi) {
        val nghe = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.kiemTraBanCu() }
        vongDoi.lifecycle.addObserver(nghe)
        onDispose { vongDoi.lifecycle.removeObserver(nghe) }
    }
    if (!vm.conBanCu) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OIcon(Icons.Rounded.DeleteSweep, 34.dp, MauO.cam)
            Spacer(Modifier.width(12.dp))
            Text(t("Máy còn TiviKit bản cũ", "The old TiviKit is still installed"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        Phu(t("Bản mới đổi tên gói (vn.tivikit) nên cài riêng, hai app cùng tên dễ mở nhầm. Bản sao lưu vẫn còn trên từng tivi: nối lại bằng bản mới là có, app phụ trên tivi cũng tự chuyển sang bản mới.",
            "The new version has a new package name (vn.tivikit), so it installs separately and the two look alike. Backups are also kept on each TV: reconnect with the new version to get them; helper apps on the TV move over automatically."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NutVien(t("Gỡ bản cũ", "Uninstall the old one"), Icons.Rounded.Delete, Modifier.weight(1f), true) {
                runCatching {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${vn.tivikit.dulieu.GoiCu.APP}"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            NutVien(t("Để sau", "Later"), Icons.Rounded.Schedule, Modifier.weight(1f), true) { vm.anBanCu() }
        }
    }
}

/** Thẻ tivi: đã nối thì tên / Android / IP + Đổi tivi / Ngắt; chưa nối thì nút Kết nối + tivi gần đây nối lại một chạm. */
@Composable
private fun TheTivi(vm: PhienLamViec) {
    val info = vm.thongTin
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OIcon(Icons.Rounded.Tv, 50.dp, if (info != null) Gradient else NenXam)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (info != null) {
                    Text(info.tenHienThi, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val gk = vm.giuKetNoi
                    Row(verticalAlignment = Alignment.Top) {
                        // Chấm xanh lá "đang nối" như trạng thái Wi-Fi / Bluetooth của iPhone; cam: đang tự nối lại; đỏ: mất kết nối.
                        val cham = when (gk.tinhTrang) { GiuKetNoi.TinhTrang.ON -> LocalMau.current.congTac; GiuKetNoi.TinhTrang.DANG_NOI -> Cam; GiuKetNoi.TinhTrang.CHO -> DoTuoi }
                        Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(cham)); Spacer(Modifier.width(6.dp))
                        Text(when (gk.tinhTrang) {
                            GiuKetNoi.TinhTrang.ON -> t("Đã kết nối", "Connected") + " · Android ${info.android} · ${vm.ip.trim()}"
                            GiuKetNoi.TinhTrang.DANG_NOI -> t("Rớt kết nối, đang tự nối lại…", "Connection dropped, reconnecting…")
                            GiuKetNoi.TinhTrang.CHO -> t("Mất kết nối: ", "Disconnected: ") + gk.lyDo
                        }, fontSize = 13.sp, lineHeight = 18.sp, color = ChuPhu, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(t("Chưa kết nối tivi", "No TV connected"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Phu(t("Tivi và điện thoại chung Wi-Fi, tivi đã bật Gỡ lỗi USB.", "TV and phone on the same Wi-Fi, USB debugging on."))
                    DongTuNoi(vm)
                }
            }
        }
        if (info != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutVien(t("Đổi tivi", "Switch TV"), Icons.Rounded.SwapHoriz, Modifier.weight(1f), !vm.dangBan) { vm.moKetNoi() }
                NutVien(t("Ngắt kết nối", "Disconnect"), Icons.Rounded.LinkOff, Modifier.weight(1f), !vm.dangBan) { vm.ngatKetNoi() }
            }
            // Khởi động lại tivi (25/10/2026): hỏi lại trước, xong app tự nối lại khi tivi lên. Tắt ADB: dùng xong công cụ thì
            // khoá cửa lại — để mở là ai cùng Wi-Fi cũng điều khiển được tivi.
            var hoiKhoiDong by remember { mutableStateOf(false) }
            var hoiTatAdb by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutVien(t("Khởi động lại", "Reboot"), Icons.Rounded.RestartAlt, Modifier.weight(1f), !vm.dangBan) { hoiKhoiDong = true }
                NutVien(t("Tắt ADB", "Turn off ADB"), Icons.Rounded.Lock, Modifier.weight(1f), !vm.dangBan) { hoiTatAdb = true }
            }
            Hang(t("Tự nối lại", "Auto-reconnect"), t("Rớt kết nối thì tự nối lại; mở app là tự nối tivi này", "Reconnects when the link drops, and when you open the app"),
                vm.giuKetNoi.bat) { vm.giuKetNoi.doiBat(it) }
            if (hoiKhoiDong) HoiKhoiDongLai(vm, dong = { hoiKhoiDong = false })
            if (hoiTatAdb) AlertDialog(
                onDismissRequest = { hoiTatAdb = false },
                icon = { Icon(Icons.Rounded.Lock, null, tint = Nhan) },
                title = { Text(t("Tắt ADB trên tivi?", "Turn off ADB on the TV?")) },
                text = { Text(t("Từ giờ không ai cùng Wi-Fi điều khiển được tivi bằng ADB, kể cả TiviKit. Hẹn giờ, Khoá trẻ em, Kênh nhanh, Home sạch vẫn chạy bình thường. Lần sau dùng TiviKit: vào Cài đặt → Tuỳ chọn nhà phát triển trên tivi, bật lại Gỡ lỗi USB.",
                    "From now on nobody on the Wi-Fi can control the TV over ADB, TiviKit included. Timer, Kids lock, Quick channel and the clean Home keep working. To use TiviKit again: on the TV open Settings → Developer options and turn USB debugging back on.")) },
                confirmButton = { TextButton(onClick = { hoiTatAdb = false; vm.tatAdbVaKetThuc() }) { Text(t("Tắt ADB", "Turn off ADB"), fontWeight = FontWeight.SemiBold) } },
                dismissButton = { TextButton(onClick = { hoiTatAdb = false }) { Text(t("Thôi", "Cancel")) } },
                containerColor = The1,
            )
        } else {
            NutChinh(t("Kết nối tivi", "Connect a TV"), enabled = !vm.dangBan, icon = Icons.Rounded.WifiFind) { vm.moKetNoi() }
            vm.tiviGanDay.take(4).forEach { g ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = ChuPhu)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.ten.ifBlank { g.ip }, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (g.cong == "5555") g.ip else "${g.ip}:${g.cong}", fontSize = 12.sp, color = ChuPhu)
                    }
                    TextButton(onClick = { vm.noiNhanh(g) }, enabled = !vm.dangBan) { Text(t("Nối lại", "Reconnect")) }
                }
            }
        }
    }
}

/** Chưa nối: dòng nhỏ về việc tự nối tivi lần trước (đang tìm / chưa thấy vì sao). */
@Composable
private fun DongTuNoi(vm: PhienLamViec) {
    val gk = vm.giuKetNoi
    val dich = gk.dich ?: return
    if (!gk.bat) return
    val ip = dich.substringBeforeLast(':')
    val ten = vm.tenGanDay(ip) ?: dich.removeSuffix(":5555")
    val (chu, mau) = when (gk.tinhTrang) {
        GiuKetNoi.TinhTrang.DANG_NOI -> t("Đang tìm $ten để tự nối lại…", "Looking for $ten to reconnect…") to Nhan
        GiuKetNoi.TinhTrang.CHO -> t("Chưa tự nối lại được $ten: ", "Can't reconnect $ten yet: ") + gk.lyDo to Cam
        GiuKetNoi.TinhTrang.ON -> t("Tivi $ten có mặt trên mạng là app tự nối lại.", "The app reconnects $ten once it's on the network.") to ChuPhu
    }
    Phu(chu, mau)
}

/**
 * Hỏi lại trước khi khởi động lại tivi (thẻ tivi ở màn chính, màn Làm mới tivi). Đồng ý thì khởi động lại, đợi tivi lên,
 * tự nối lại rồi chạy [sau] nếu có.
 */
@Composable
internal fun HoiKhoiDongLai(vm: PhienLamViec, dong: () -> Unit, sau: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = dong,
        icon = { Icon(Icons.Rounded.RestartAlt, null, tint = Nhan) },
        title = { Text(t("Khởi động lại tivi?", "Reboot the TV?")) },
        text = { Text(if (ManHinh.laTv) t("Tivi tắt rồi bật lại trong 1–3 phút; TiviKit đang chạy trên tivi nên cũng tắt theo, mở lại sau khi tivi lên.",
                "The TV turns off and back on in 1–3 minutes; TiviKit runs on this TV, so it closes too — open it again afterwards.")
            else t("Tivi tắt rồi bật lại trong 1–3 phút. App chờ tivi lên rồi tự nối lại; đang xem gì trên tivi sẽ bị ngắt.",
                "The TV turns off and back on in 1–3 minutes. The app waits and reconnects; whatever is playing stops.")) },
        confirmButton = { TextButton(onClick = { dong(); vm.khoiDongLaiVaNoiLai(sau) }) { Text(t("Khởi động lại", "Reboot"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = dong) { Text(t("Thôi", "Cancel")) } },
        containerColor = The1,
    )
}

/** Thẻ nổi bật của chức năng gốc (dọn quảng cáo + Home sạch, luồng 4 bước), dáng thẻ "Hôm nay" của App Store. */
@Composable
private fun ODonQuangCao(vm: PhienLamViec) {
    Surface(onClick = { vm.batDauDon() }, enabled = !vm.dangBan, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
        Column(Modifier.background(Gradient).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("DỌN QUẢNG CÁO", "AD CLEAN-UP"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = Color.White.copy(alpha = 0.75f))
            Text(t("Tivi hết quảng cáo", "No more TV ads"), fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.5).sp, color = Color.White)
            Text(t("Tắt quảng cáo, theo dõi và app rác của hãng, thay màn Home sạch. Khôi phục được.",
                "Switch off the maker's ads, tracking and junk, get a clean Home. Fully restorable."),
                fontSize = 14.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = 0.88f))
            Spacer(Modifier.height(6.dp))
            // Nút viên thuốc trắng chữ xanh như nút "Nhận" của App Store.
            Text(t("Bắt đầu", "Get started"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF007AFF),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }
}

/** Tiêu đề nhóm: chữ đậm cỡ vừa, như "Mục ưa thích" của app Sức khoẻ. */
@Composable
private fun TieuDeMuc(chu: String) {
    Text(chu, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
}

/** Nhóm dòng trong một thẻ, kẻ mảnh giữa các dòng (lệch vào bằng ô biểu tượng), như danh sách Cài đặt của iPhone. */
@Composable
private fun NhomChucNang(vm: PhienLamViec, ds: List<MucChucNang>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1)) {
        ds.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 62.dp), thickness = 0.5.dp, color = Vien)
            DongChucNang(m, !vm.dangBan)
        }
    }
}

@Composable
private fun DongChucNang(m: MucChucNang, ranh: Boolean) {
    Row(Modifier.fillMaxWidth().clickable(enabled = m.bat && ranh, onClick = m.mo).padding(horizontal = 14.dp, vertical = 11.dp).alpha(if (m.bat) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically) {
        OIcon(m.icon, 34.dp, m.mau)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(m.ten, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(m.moTa, fontSize = 13.sp, color = ChuPhu, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp), tint = ChuPhu.copy(alpha = 0.7f))
    }
}

/** Nút nhỏ trong thẻ tivi: nền màu nhấn nhạt, chữ màu nhấn. */
@Composable
private fun NutVien(chu: String, icon: ImageVector, modifier: Modifier, bat: Boolean, onClick: () -> Unit) {
    val mau = if (bat) Nhan else ChuPhu
    Row(modifier.heightIn(min = 42.dp).clip(RoundedCornerShape(10.dp)).background(if (bat) Nhan.copy(alpha = 0.13f) else NenO)
        .clickable(enabled = bat, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = mau); Spacer(Modifier.width(6.dp))
        Text(chu, color = mau, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
