package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.GoiY
import vn.tivikit.t

/** Thẻ "Từ đường link" của màn Cài app (25/10/2026). */
@Composable
internal fun TheTuLink(vm: PhienLamViec) {
    The(t("Từ đường link", "From a link"), Icons.Rounded.Link) {
        Phu(t("Dán link tải file APK (GitHub Releases, trang chính thức của app…). Điện thoại tải về rồi gửi lên tivi.",
            "Paste a download link to an APK (GitHub Releases, the app's official site…). The phone downloads it, then sends it to the TV."))
        OutlinedTextField(vm.linkTai, { vm.linkTai = it.trim() }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text("https://…") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (!vm.dangBan) vm.themTuLink() }))
        NutPhu(t("Tải về", "Download"), Icons.Rounded.Download, enabled = !vm.dangBan && vm.linkTai.isNotBlank()) { vm.themTuLink() }
    }
}

/**
 * Gợi ý app theo nhóm (25/10/2026): app tải thẳng (mã nguồn mở, chính thức hoặc do cộng đồng làm) thì Thêm vào danh
 * sách cài — bản mới nhất đúng chip của tivi; app chỉ có trên CH Play thì mở trang của nó trên tivi. Tivi đã có app thì
 * hiện "đã cài", nút thành Cập nhật / Mở.
 */
@Composable
internal fun TheGoiY(vm: PhienLamViec) {
    Gap(t("Gợi ý app hay cho tivi (${GoiY.DS.size})", "Suggested TV apps (${GoiY.DS.size})"), Icons.Rounded.Recommend) {
        Phu(t("App mã nguồn mở, kể cả app không chính thức do cộng đồng làm như SmartTube, tải bản mới nhất từ chính nơi tác giả phát hành (GitHub…), đúng chip của tivi. App chính thức chỉ có trên CH Play thì mở trang CH Play trên tivi để cài. Không có app xem phim lậu hay bản bẻ khoá.",
            "Open-source apps, including unofficial community apps like SmartTube, download as the latest build from where the author publishes them (GitHub…), for the TV's chip. Official apps that are only on the Play Store open their Play Store page on the TV. No pirate streaming apps or cracked builds."))
        val caiDat = vm.trangThai?.caiDat.orEmpty()
        GoiY.Nhom.entries.forEach { nhom ->
            val ds = GoiY.DS.filter { it.nhom == nhom }
            if (ds.isNotEmpty()) {
                Text(nhom.ten.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ChuPhu, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
                ds.forEach { g -> HangGoiY(vm, g, g.goi.firstOrNull { it in caiDat }) }
            }
        }
        if (vm.baoGoiY.isNotEmpty()) Phu(vm.baoGoiY, Xanh)
    }
}

/** Một app gợi ý; [daCai] = tên gói của nó đang có trên tivi. */
@Composable
private fun HangGoiY(vm: PhienLamViec, g: GoiY.App, daCai: String?) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Tên, nguồn, "đã cài" là một dòng chữ tự xuống dòng (27/10/2026): để trong Row thì máy chữ to ép phần cuối
            // thành một cột từng chữ. Khoảng trắng không ngắt trong mỗi nhãn: xuống dòng thì cả nhãn xuống cùng nhau.
            val nguon = when { g.laCHPlay -> "CH Play"; g.khongChinhThuc -> t("cộng đồng · GitHub", "community · GitHub"); else -> t("mã nguồn mở", "open source") }
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g.ten) }
                withStyle(SpanStyle(fontSize = 11.sp, color = ChuPhu)) { append("  " + nguon.replace(' ', '\u00A0')) }
                if (daCai != null) withStyle(SpanStyle(fontSize = 11.sp, color = Xanh)) { append("  " + t("đã cài", "installed").replace(' ', '\u00A0')) }
            })
            Phu(g.moTa)
        }
        when {
            daCai != null && g.laCHPlay -> TextButton(onClick = { vm.moAppTrenTv(daCai) }, enabled = !vm.dangBan) { Text(t("Mở", "Open")) }
            g.laCHPlay -> TextButton(onClick = { vm.moCHPlay(g) }, enabled = !vm.dangBan) { Text("CH Play") }
            else -> TextButton(onClick = { vm.themGoiY(g) }, enabled = !vm.dangBan) { Text(if (daCai != null) t("Cập nhật", "Update") else t("Thêm", "Add")) }
        }
    }
}
