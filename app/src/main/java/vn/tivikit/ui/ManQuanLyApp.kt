package vn.tivikit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.ChayNgam
import vn.tivikit.t

/** Màn Quản lý app trên tivi (25/10/2026). */
@Composable
internal fun ManQuanLyApp(vm: PhienLamViec) {
    val ql = vm.qlApp
    val luuApk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        if (uri != null) ql.luu(uri) else ql.huyLuu()
    }
    val luuApks = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) ql.luu(uri) else ql.huyLuu()
    }
    LaunchedEffect(ql.yeuCauLuu) {
        val (ten, nhieuPhan) = ql.yeuCauLuu ?: return@LaunchedEffect
        ql.yeuCauLuu = null
        runCatching { (if (nhieuPhan) luuApks else luuApk).launch(ten) }.onFailure { ql.huyLuu() }
    }
    /** ("go" | "xoa", app) đang chờ người dùng xác nhận. */
    var hoi by remember { mutableStateOf<Pair<String, QuanLyApp.App>?>(null) }

    Cuon {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Quản lý app trên tivi", "Apps on the TV"), style = KieuTieuDeLon)
            Phu(t("Xem app đã cài và dung lượng, app nào đang chạy ngầm tốn RAM, mở / buộc dừng / xoá cache / tắt / gỡ app, lưu APK về điện thoại để cài sang tivi khác.",
                "See installed apps and their size, which apps use RAM in the background; open, force-stop, clear cache, disable or uninstall; save an APK to the phone for another TV."))
        }
        The(t("Bộ nhớ tivi", "TV storage"), Icons.Rounded.Storage) {
            val bn = ql.boNho
            if (bn == null) Phu(t("Chưa đọc được.", "Not read yet."))
            else {
                Text(t("Còn trống ${coChu(bn.conKb * 1024)} / ${coChu(bn.tongKb * 1024)}", "${coChu(bn.conKb * 1024)} free of ${coChu(bn.tongKb * 1024)}"), fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { if (bn.tongKb > 0) bn.dungKb.toFloat() / bn.tongKb else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (bn.conKb * 10 < bn.tongKb) DoTuoi else Nhan, trackColor = NenO, gapSize = 0.dp, drawStopIndicator = {})
            }
            NutPhu(t("Dọn cache tất cả app", "Clear all app caches"), Icons.Rounded.CleaningServices, enabled = !vm.dangBan) { ql.donCache() }
        }
        if (ql.chayNgam.isNotEmpty()) TheChayNgam(vm, ql) { hoi = "tat" to it }
        if (ql.bao.isNotEmpty()) Phu(ql.bao, Xanh)
        OutlinedTextField(ql.tim, { ql.tim = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, label = { Text(t("Tìm app", "Find an app")) })
        Hang(t("Hiện cả app hệ thống", "Show system apps too"), t("App của hãng có biểu tượng và app đang bị tắt (không gỡ được; tắt / bật lại, xoá cache / dữ liệu được)",
            "The maker's apps with an icon and disabled apps (can't be uninstalled; can be disabled / re-enabled, cache / data cleared)"), ql.hienHeThong) { if (!vm.dangBan) { ql.hienHeThong = it; ql.tai() } }
        val tim = ql.tim.trim().lowercase()
        val loc = ql.ds.filter { tim.isEmpty() || tim in it.tenHien.lowercase() || tim in it.tv.pkg.lowercase() }.sortedBy { it.tenHien.lowercase() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val conDoc = ql.ds.count { it.ten == null }
            Text(t("${loc.size} app", "${loc.size} apps") + if (conDoc > 0) t(" · đang đọc tên ($conDoc)…", " · reading names ($conDoc)…") else "",
                fontSize = 13.sp, color = ChuPhu, modifier = Modifier.weight(1f))
            TextButton(onClick = { ql.tai() }, enabled = !vm.dangBan) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Tải lại", "Reload"))
            }
        }
        loc.forEach { a -> HangApp(vm, ql, a) { hoi = it to a } }
    }

    hoi?.let { (viec, a) ->
        AlertDialog(
            onDismissRequest = { hoi = null },
            icon = { Icon(Icons.Rounded.Warning, null, tint = Cam) },
            title = { Text(when (viec) {
                "go" -> t("Gỡ ${a.tenHien}?", "Uninstall ${a.tenHien}?")
                "tat" -> t("Tắt ${a.tenHien}?", "Disable ${a.tenHien}?")
                else -> t("Xoá dữ liệu ${a.tenHien}?", "Clear ${a.tenHien}'s data?")
            }) },
            text = { Text(when (viec) {
                "go" -> t("App và dữ liệu của nó bị xoá khỏi tivi.", "The app and its data are removed from the TV.")
                "tat" -> t("App ẩn khỏi tivi và không chạy được (cả nút remote hay cast vào app này). Dữ liệu giữ nguyên; bật lại ở đây hoặc bằng Khôi phục tivi.",
                    "The app is hidden and can't run (remote buttons and casts to it stop too). Its data is kept; re-enable it here or by restoring the TV.") +
                    if (a.tv.pkg == "com.google.android.youtube.tv") t("\n\nCast YouTube từ điện thoại qua Google Cast sẽ không mở được nữa: chọn SmartTube (đã liên kết bằng mã TV) trong danh sách cast.",
                        "\n\nCasting YouTube from a phone over Google Cast will stop working: pick SmartTube (linked with a TV code) in the cast list.") else ""
                else -> t("Đăng nhập, cài đặt và nội dung tải về trong app bị xoá; app như mới cài.", "Sign-ins, settings and downloads inside the app are erased; it's like freshly installed.")
            }) },
            confirmButton = { TextButton(onClick = { hoi = null; when (viec) { "go" -> ql.go(a); "tat" -> ql.tatApp(a); else -> ql.xoaDuLieu(a) } }) {
                Text(when (viec) { "go" -> t("Gỡ", "Uninstall"); "tat" -> t("Tắt", "Disable"); else -> t("Xoá", "Clear") }, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { hoi = null }) { Text(t("Thôi", "Cancel")) } },
            containerColor = The1,
        )
    }
}

/**
 * App đang chạy ngầm, RAM nhiều trước (26/10/2026). App chắc chắn thừa (microG không còn app dùng) tô cam "Nên tắt"; app chỉ
 * thừa khi không dùng tính năng đó (cập nhật ROM, DLNA, trợ lý giọng nói) ghi "Có thể tắt" kèm điều kiện.
 */
@Composable
private fun TheChayNgam(vm: PhienLamViec, ql: QuanLyApp, tat: (QuanLyApp.App) -> Unit) {
    val nenTat = ql.chayNgam.count { ChayNgam.nenTat(it.pkg, ql.goiDaCai)?.chac == true }
    The(t("Đang chạy ngầm", "Running in the background") + if (nenTat > 0) t(" · $nenTat app nên tắt", " · $nenTat to disable") else "", Icons.Rounded.Memory) {
        ql.chayNgam.take(8).forEach { c ->
            val a = ql.appCua(c.pkg)
            val ly = ChayNgam.nenTat(c.pkg, ql.goiDaCai)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.tenHien, fontSize = 15.sp, fontWeight = if (ly != null) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(coChu(c.ramKb * 1024), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (ly?.chac == true) Cam else ChuChinh)
                }
                if (ly != null) {
                    Phu(lyDoTat(ly), if (ly.chac) Cam else ChuPhu)
                    if (!a.dangTat && ql.duocTat(a)) ChonChip(t("Tắt app", "Disable"), false) { if (!vm.dangBan) tat(a) }
                }
            }
        }
        Phu(t("RAM từng app đang dùng lúc này. Tivi ít RAM (1–2 GB) tắt bớt app chạy ngầm không dùng thì xem video, chuyển app nhẹ hơn; bật lại ở danh sách bên dưới (bật Hiện cả app hệ thống) hoặc bằng Khôi phục tivi.",
            "RAM each app is using right now. On TVs with little RAM (1–2 GB), disabling unused background apps makes video and app switching lighter; re-enable them in the list below (turn on Show system apps) or by restoring the TV."))
    }
}

private fun lyDoTat(ly: ChayNgam.LyDo) = when (ly) {
    ChayNgam.LyDo.MICROG_THUA -> t("Nên tắt: microG dành cho YouTube ReVanced / Vanced, mà tivi không còn app nào dùng nó.",
        "Disable it: microG for YouTube ReVanced / Vanced, but no app on the TV uses it any more.")
    ChayNgam.LyDo.CAP_NHAT_ROM -> t("Có thể tắt: trình cập nhật phần mềm của box, chạy suốt mà box hiếm khi có bản mới. Muốn cập nhật thì bật lại.",
        "Can be disabled: the box's firmware updater runs all the time though updates are rare. Re-enable it to update.")
    ChayNgam.LyDo.DLNA -> t("Có thể tắt: nhận video chiếu từ điện thoại qua DLNA. Không chiếu kiểu này thì tắt.",
        "Can be disabled: receives videos cast from phones over DLNA. Disable it if you don't cast this way.")
    ChayNgam.LyDo.TRO_LY_GIONG_NOI -> t("Có thể tắt: Trợ lý giọng nói Google. Remote không có mic thì tắt cho nhẹ (mất tìm kiếm bằng giọng nói).",
        "Can be disabled: Google's voice assistant. If the remote has no mic, disable it to save RAM (voice search stops).")
}

@Composable
private fun HangApp(vm: PhienLamViec, ql: QuanLyApp, a: QuanLyApp.App, hoi: (String) -> Unit) {
    val mo = ql.dangMo == a.tv.pkg
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).border(1.5.dp, if (mo) Nhan else Color.Transparent, RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxWidth().clickable { ql.dangMo = if (mo) null else a.tv.pkg }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val anh = a.anh
            if (anh != null) Image(anh, null, Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
            else Box(Modifier.size(width = 64.dp, height = 40.dp), contentAlignment = Alignment.Center) { OIcon(Icons.Rounded.Android, 36.dp, NenXam) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.tenHien, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (a.tenHien != a.tv.pkg) Text(a.tv.pkg, fontSize = 11.sp, color = ChuPhu, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val du = a.duLieu
                Phu(listOfNotNull(
                    a.tv.versionName.takeIf { it.isNotBlank() }?.let { "v$it" },
                    coChu(a.tv.coApk),
                    du?.let { t("dữ liệu ${coChu(it.duLieu)}", "data ${coChu(it.duLieu)}") },
                    du?.takeIf { it.cache > 0 }?.let { t("cache ${coChu(it.cache)}", "cache ${coChu(it.cache)}") },
                    if (a.heThong) t("hệ thống", "system") else null,
                    if (a.dangTat) t("đang tắt", "disabled") else null,
                ).joinToString(" · "))
            }
            Icon(if (mo) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = ChuPhu)
        }
        if (mo) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun lam(viec: () -> Unit) { if (!vm.dangBan) viec() }
                if (a.dangTat) ChonChip(t("Bật lại", "Re-enable"), true) { lam { ql.batLaiApp(a) } }
                else {
                    ChonChip(t("Mở", "Open"), false) { lam { ql.moApp(a) } }
                    // Buộc dừng app của TiviKit là tắt luôn dịch vụ của nó (Android bỏ khỏi danh sách trợ năng, Hẹn giờ mất báo
                    // thức): không cho ở đây.
                    if (!ql.baoVe(a)) ChonChip(t("Buộc dừng", "Force stop"), false) { lam { ql.dung(a) } }
                }
                ChonChip(t("Xoá cache", "Clear cache"), false) { lam { ql.xoaCache(a) } }
                if (!ql.baoVe(a)) ChonChip(t("Xoá dữ liệu", "Clear data"), false) { lam { hoi("xoa") } }
                ChonChip(t("Lưu APK", "Save APK"), false) { lam { ql.chuanBiLuu(a) } }
                if (!a.dangTat && ql.duocTat(a)) ChonChip(t("Tắt app", "Disable"), false) { lam { hoi("tat") } }
                if (!a.heThong && !ql.baoVe(a) && !ql.laHome(a)) ChonChip(t("Gỡ", "Uninstall"), false) { lam { hoi("go") } }
            }
            if (!a.heThong && !ql.baoVe(a) && ql.laHome(a)) Phu(t("Đang là màn Home: muốn gỡ thì đổi Home ở Quản lý launcher trước.",
                "This is the Home screen: change Home under Launchers before removing it."))
            if (ql.baoVe(a)) Phu(t("App của TiviKit: gỡ / xoá dữ liệu ở mục Home sạch, Khoá trẻ em, Kênh nhanh, Hẹn giờ, Quản lý launcher, Cast SmartTube hoặc Khôi phục.",
                "Part of TiviKit: remove it from the clean Home, Kids lock, Quick channel, Timer, Launchers, Cast to SmartTube or Restore screens."))
        }
    }
}
