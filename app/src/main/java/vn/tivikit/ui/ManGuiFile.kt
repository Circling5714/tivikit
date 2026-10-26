package vn.tivikit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.tivikit.adb.FileTrenTv
import vn.tivikit.t

/** Màn Gửi ảnh / video / file lên tivi (25/10/2026). */
@Composable
internal fun ManGuiFile(vm: PhienLamViec) = Cuon {
    val gf = vm.guiFile
    val chonAnh = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris -> if (uris.isNotEmpty()) gf.them(uris) }
    val chonFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) gf.them(uris) }
    // Đang gửi thì giữ màn sáng: tắt màn là app xuống nền, nhiều máy cắt kết nối tới tivi.
    val view = LocalView.current
    DisposableEffect(vm.dangBan) {
        view.keepScreenOn = vm.dangBan
        onDispose { view.keepScreenOn = false }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Gửi ảnh, video, file lên tivi", "Send photos, videos, files to the TV"), style = KieuTieuDeLon)
        Phu(t("File được gửi qua Wi-Fi vào thư mục Download/TiviKit trên tivi, rồi mở bằng app xem ảnh / video của tivi.",
            "Files go over Wi-Fi to Download/TiviKit on the TV, then open with the TV's photo / video app."))
    }
    NutChinh(t("Chọn ảnh / video", "Pick photos / videos"), enabled = !vm.dangBan, icon = Icons.Rounded.PhotoLibrary) {
        runCatching { chonAnh.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    }
    NutPhu(t("Chọn file khác", "Pick other files"), Icons.Rounded.FolderOpen, enabled = !vm.dangBan) { runCatching { chonFile.launch(arrayOf("*/*")) } }
    gf.ds.forEach { f -> TheFileGui(vm, gf, f) }
    val soGui = gf.ds.count { it.trangThai == GuiFile.TrangThai.CHO || it.trangThai == GuiFile.TrangThai.LOI && it.co >= 0 }
    if (soGui > 0) NutChinh(t("Gửi $soGui file lên tivi", "Send $soGui file(s) to the TV"), enabled = !vm.dangBan, icon = Icons.AutoMirrored.Rounded.Send) { gf.guiTatCa() }
    if (gf.ds.any { it.trangThai == GuiFile.TrangThai.XONG }) Phu(t("Trên tivi: thư mục ${FileTrenTv.THU_MUC.removePrefix("/storage/emulated/0/")}.",
        "On the TV: folder ${FileTrenTv.THU_MUC.removePrefix("/storage/emulated/0/")}."))
}

@Composable
private fun TheFileGui(vm: PhienLamViec, gf: GuiFile, f: GuiFile.FileGui) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                f.kieu.startsWith("image/") -> Icons.Rounded.Image
                f.kieu.startsWith("video/") -> Icons.Rounded.Movie
                f.kieu.startsWith("audio/") -> Icons.Rounded.AudioFile
                else -> Icons.AutoMirrored.Rounded.InsertDriveFile
            }
            OIcon(icon, 40.dp, NenXam)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.ten, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Phu(if (f.co >= 0) coChu(f.co) else "?")
            }
            if (f.trangThai != GuiFile.TrangThai.DANG_GUI) IconButton(onClick = { gf.bo(f) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.Close, t("Bỏ", "Remove")) }
        }
        when (f.trangThai) {
            GuiFile.TrangThai.CHO -> Chip(t("Chờ gửi", "Waiting"))
            GuiFile.TrangThai.DANG_GUI -> LinearProgressIndicator(progress = { f.tienDo },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Nhan, trackColor = NenO, gapSize = 0.dp, drawStopIndicator = {})
            GuiFile.TrangThai.XONG -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = Xanh); Spacer(Modifier.width(6.dp))
                Text(t("Đã gửi", "Sent"), color = Xanh, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { gf.moTrenTv(f) }, enabled = !vm.dangBan) { Text(t("Mở trên tivi", "Open on the TV")) }
            }
            GuiFile.TrangThai.LOI -> Phu(f.bao, DoTuoi)
        }
    }
}
