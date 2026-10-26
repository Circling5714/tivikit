package vn.tivikit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.lg.Vibehack
import vn.tivikit.t

private val THANH_PHO_LG = listOf("Ho Chi Minh City", "Hanoi", "Da Nang", "Hai Phong", "Can Tho", "Nha Trang", "Da Lat", "Hue")

/**
 * Phần tivi LG đã root (25/10/2026): app tự kiểm tra sau khi nối (LgTivi.kiemTraRoot). Chưa root thì chỉ một dòng giải
 * thích; có Homebrew Channel mà chưa bật SSH thì chỉ cách bật; đã root thì hiện cài màn hình chính kiểu Apple TV
 * (LG TV Vibehack), nút Home, thời tiết, ảnh nền, khởi động lại. Telnet mở thì cảnh báo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.TheRootLg(vm: PhienLamViec) {
    val lg = vm.lg
    if (lg.telnetMo) Phu(t("Cảnh báo: tivi đang mở Telnet, ai cùng Wi-Fi cũng vào được quyền root mà không cần mật khẩu. Mở Homebrew Channel → Settings, tắt Telnet.",
        "Warning: the TV has Telnet open, so anyone on the Wi-Fi gets root without a password. Open Homebrew Channel → Settings and turn Telnet off."), DoTuoi)
    when (lg.root) {
        LgTivi.Root.CHUA_BIET -> Phu(t("Đang xem tivi đã root chưa…", "Checking whether the TV is rooted…"))
        LgTivi.Root.CHUA_ROOT -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Phu(t("Tivi chưa root (hoặc chưa bật SSH): dùng các chức năng dưới đây. Tivi đã root thì app tự hiện thêm màn hình chính kiểu Apple TV, nút Home, ảnh nền.",
                    "Not rooted (or SSH is off): use the tools below. On a rooted TV the app also offers an Apple TV-style home screen, Home button and wallpaper."))
            }
            TextButton(onClick = { lg.kiemTraRoot() }, enabled = !lg.dangKiemTra) { Text(t("Kiểm tra lại", "Check again")) }
        }
        LgTivi.Root.HOMEBREW_CHUA_SSH -> The(t("Tivi có Homebrew Channel", "Homebrew Channel found"), Icons.Rounded.Info) {
            Phu(t("Để dùng thêm chức năng của tivi đã root: mở Homebrew Channel trên tivi → Settings (⚙), bật SSH Server, xem Root status là ok, rồi bấm Kiểm tra lại. Root status chưa ok là tivi chưa root thật.",
                "For the rooted-TV tools: open Homebrew Channel on the TV → Settings (⚙), turn on SSH Server, check Root status says ok, then tap Check again. If Root status isn't ok, the TV isn't really rooted."))
            NutPhu(t("Kiểm tra lại", "Check again"), Icons.Rounded.Refresh, enabled = !lg.dangKiemTra) { lg.kiemTraRoot() }
        }
        LgTivi.Root.SAI_MAT_KHAU -> The(t("Tivi đã root, cần mật khẩu SSH", "Rooted TV, SSH password needed"), Icons.Rounded.Key) {
            Phu(t("Mật khẩu mặc định alpine không vào được. Nhập mật khẩu root bạn đã đặt (hoặc bật lại đăng nhập bằng mật khẩu trong Homebrew Channel).",
                "The default password alpine didn't work. Enter the root password you set (or allow password logins again in Homebrew Channel)."))
            var mk by remember { mutableStateOf("") }
            OutlinedTextField(mk, { mk = it }, label = { Text(t("Mật khẩu root", "Root password")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            NutPhu(t("Thử mật khẩu này", "Try this password"), Icons.Rounded.Key, enabled = mk.isNotEmpty() && !lg.dangKiemTra) { lg.datMatKhau(mk) }
        }
        LgTivi.Root.DA_ROOT -> TheVibehack(vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TheVibehack(vm: PhienLamViec) {
    val lg = vm.lg
    val tc = lg.tuyChon
    val chonAnh = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) lg.chonAnhNen(uri) }
    // Đã cài thì thu gọn (bấm để mở): vào màn này thường là để dùng remote.
    Gap(t("Tivi đã root: màn hình chính kiểu Apple TV", "Rooted TV: Apple TV-style home"), Icons.Rounded.Verified, moSan = !lg.coVibehack) {
        Phu(if (lg.coVibehack) t("Đã cài LG TV Vibehack. Đổi tuỳ chọn rồi bấm Cập nhật.", "LG TV Vibehack is installed. Change options and tap Update.")
            else t("Thay màn Home đầy quảng cáo của LG bằng màn hình chính gọn kiểu Apple TV (LG TV Vibehack ${Vibehack.BAN}, MIT): dock app, thời tiết, ảnh nền, tự mở khi bật tivi.",
                "Replace LG's ad-filled Home with a tidy Apple TV-style home (LG TV Vibehack ${Vibehack.BAN}, MIT): app dock, weather, wallpaper, opens when the TV turns on."))
        Hang(t("Tiếng Việt", "Vietnamese"), t("Chữ trên màn hình chính", "Home screen language"), tc.tiengViet) { lg.tuyChon = tc.copy(tiengViet = it) }
        Hang(t("Đồng hồ 24 giờ", "24-hour clock"), t("14:45 thay vì 2:45 pm", "14:45 instead of 2:45 pm"), tc.dongHo24) { lg.tuyChon = tc.copy(dongHo24 = it) }
        Hang(t("Nút Home mở màn hình mới", "Home button opens it"),
            if (lg.coInputHook) t("Bấm Home trên remote là vào thẳng màn hình mới", "Pressing Home goes straight to it")
            else t("Cần cài LG Input Hook trong Homebrew Channel; chưa có thì Home LG vẫn hiện thoáng qua", "Needs LG Input Hook from Homebrew Channel; without it LG's Home flashes first"),
            tc.nutHome) { lg.tuyChon = tc.copy(nutHome = it) }
        Text(t("Số app mỗi hàng", "Apps per row"), fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 6, 7, 8).forEach { n -> ChonChip("$n", tc.cot == n) { lg.tuyChon = tc.copy(cot = n) } }
        }
        Text(t("Thời tiết", "Weather"), fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            THANH_PHO_LG.forEach { tp -> ChonChip(tp, tc.thanhPho == tp) { lg.tuyChon = tc.copy(thanhPho = tp) } }
            ChonChip(t("Không hiện", "Off"), tc.thanhPho.isBlank()) { lg.tuyChon = tc.copy(thanhPho = "") }
        }
        OutlinedTextField(tc.thanhPho, { lg.tuyChon = tc.copy(thanhPho = it.take(60)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            label = { Text(t("Hoặc gõ tên thành phố (không dấu, VD Vung Tau)", "Or type a city (e.g. Vung Tau)")) })
        Text(t("Ảnh nền", "Wallpaper"), fontSize = 14.sp)
        lg.anhNen?.let { du ->
            val anh = remember(du) { android.graphics.BitmapFactory.decodeByteArray(du, 0, du.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }
            if (anh != null) Image(anh, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Chọn ảnh từ điện thoại", "Pick a photo"), lg.anhNen != null) {
                chonAnh.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            ChonChip(t("Nền đen", "Plain black"), tc.boAnhNen) { lg.boAnhNen() }
        }
        NutChinh(if (lg.coVibehack) t("Cập nhật màn hình chính", "Update the home screen") else t("Cài màn hình chính mới", "Install the new home screen"),
            enabled = !vm.dangBan, icon = Icons.Rounded.Home) { lg.caiVibehack() }
        if (lg.coVibehack) NutPhu(t("Gỡ, trả lại Home của LG", "Remove, back to LG's Home"), Icons.Rounded.Delete, enabled = !vm.dangBan) { lg.goVibehack() }
        NutPhu(t("Khởi động lại tivi", "Restart the TV"), Icons.Rounded.RestartAlt, enabled = !vm.dangBan) { lg.khoiDongLaiTv() }
        if (lg.nhatKy.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).padding(vertical = 4.dp)) {
            lg.nhatKy.takeLast(8).forEach { Text(it, fontSize = 11.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace, color = ChuPhu, maxLines = 2) }
        }
        Phu(t("Tivi cần có Internet để tải bản cài. Cài bằng quyền root qua SSH; gỡ được bất cứ lúc nào bằng nút Gỡ.",
            "The TV needs Internet to download the installer. Installs as root over SSH; remove it any time with Remove."))
    }
}
