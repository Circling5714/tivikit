package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.BuildConfig
import vn.tivikit.NgonNgu
import vn.tivikit.dulieu.LienHe
import vn.tivikit.t

/**
 * Góp ý & liên hệ (26/10/2026): nhóm Telegram của TiviKit, báo lỗi trên GitHub điền sẵn bản app, điện thoại, tivi đang
 * nối và lỗi vừa gặp. App không gửi gì: chỉ mở trang khi người dùng bấm, người dùng xem lại rồi mới gửi.
 */
@Composable
internal fun HopLienHe(vm: PhienLamViec) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    AlertDialog(
        onDismissRequest = { vm.moLienHe = false },
        icon = { Icon(Icons.Rounded.Forum, null, tint = Nhan) },
        title = { Text(t("Góp ý & liên hệ", "Feedback & contact")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Phu(t("Hỏi đáp, góp ý tính năng, báo tivi nào chạy được hay không: vào nhóm Telegram của TiviKit.",
                    "Questions, feature ideas, which TVs work or not: join TiviKit's Telegram group."), ChuChinh)
                NutPhu(t("Nhóm Telegram t.me/tivikit", "Telegram group t.me/tivikit"), Icons.AutoMirrored.Rounded.Send) {
                    runCatching { uri.openUri(LienHe.TELEGRAM) }
                }
                Phu(t("Báo lỗi trên GitHub: app điền sẵn bản TiviKit, điện thoại, tivi đang nối và lỗi vừa gặp; bạn xem lại, viết thêm rồi mới gửi (cần tài khoản GitHub).",
                    "Report a bug on GitHub: the app fills in the TiviKit version, phone, connected TV and the last error; review it and add details before sending (needs a GitHub account)."), ChuChinh)
                NutPhu(t("Báo lỗi trên GitHub", "Report a bug on GitHub"), Icons.Rounded.BugReport) {
                    runCatching { uri.openUri(linkBaoLoi(vm)) }
                }
                Phu(t("TiviKit không tự gửi gì: các nút này chỉ mở trang khi bạn bấm.", "TiviKit sends nothing by itself: these only open a page when you tap."))
            }
        },
        confirmButton = { TextButton(onClick = { vm.moLienHe = false }) { Text(t("Đóng", "Close")) } },
        containerColor = The1,
    )
}

private fun linkBaoLoi(vm: PhienLamViec): String {
    val dt = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}"
    val tivi = vm.thongTin?.let { "${it.tenHienThi} (${it.device}, Android ${it.android})" }
    val noiDung = LienHe.noiDungBaoLoi(NgonNgu.tiengViet, BuildConfig.VERSION_NAME, dt, tivi, vm.loi.takeIf { it.isNotBlank() })
    return LienHe.linkBaoLoi("[TiviKit ${BuildConfig.VERSION_NAME}] ", noiDung)
}

/**
 * Ủng hộ tác giả (26/10/2026): tài khoản MB Bank, mỗi dòng một nút chép để dán vào app ngân hàng. Kèm dòng ghi công
 * CleanYourTV và CleanTV, hai dự án TiviKit dựa vào.
 */
@Composable
internal fun HopUngHo(vm: PhienLamViec) {
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var daChep by remember { mutableStateOf("") }
    fun chep(giaTri: String, bao: String) { clip.setText(AnnotatedString(giaTri)); daChep = bao }
    AlertDialog(
        onDismissRequest = { vm.moUngHo = false },
        icon = { Icon(Icons.Rounded.Favorite, null, tint = DoTuoi) },
        title = { Text(t("Ủng hộ tác giả", "Support the author")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Phu(t("TiviKit miễn phí, mã nguồn mở, không quảng cáo. Thấy hữu ích thì bạn có thể mời tác giả một ly cà phê qua chuyển khoản:",
                    "TiviKit is free, open source and ad-free. If it helps you, you can buy the author a coffee by bank transfer:"), ChuChinh)
                DongChep(t("Ngân hàng", "Bank"), LienHe.NGAN_HANG, null)
                DongChep(t("Số tài khoản", "Account number"), LienHe.SO_TAI_KHOAN) { chep(LienHe.SO_TAI_KHOAN, t("Đã chép số tài khoản.", "Account number copied.")) }
                DongChep(t("Chủ tài khoản", "Account name"), LienHe.CHU_TAI_KHOAN) { chep(LienHe.CHU_TAI_KHOAN, t("Đã chép tên chủ tài khoản.", "Account name copied.")) }
                DongChep(t("Nội dung", "Message"), LienHe.NOI_DUNG_CK) { chep(LienHe.NOI_DUNG_CK, t("Đã chép nội dung chuyển khoản.", "Message copied.")) }
                if (daChep.isNotEmpty()) Phu(daChep, Xanh)
                Phu(t("Mở app ngân hàng, chọn MB Bank rồi dán số tài khoản. Cảm ơn bạn!", "Open your banking app, pick MB Bank and paste the account number. Thank you!"))
                Phu(t("TiviKit dựa trên CleanYourTV của Gathin và cộng đồng, và CleanTV của AlexVGS.",
                    "TiviKit builds on CleanYourTV by Gathin and contributors, and CleanTV by AlexVGS."))
            }
        },
        confirmButton = { TextButton(onClick = { vm.moUngHo = false }) { Text(t("Đóng", "Close")) } },
        containerColor = The1,
    )
}

/** Một dòng thông tin chuyển khoản: nhãn nhỏ, giá trị đậm, nút Chép ([chep] null = không chép). */
@Composable
private fun DongChep(nhan: String, giaTri: String, chep: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(nhan, fontSize = 12.sp, color = ChuPhu)
            Text(giaTri, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        if (chep != null) TextButton(onClick = chep) {
            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Chép", "Copy"))
        }
    }
}
