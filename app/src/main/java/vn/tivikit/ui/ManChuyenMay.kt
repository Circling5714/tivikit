package vn.tivikit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.tivikit.t

/** Màn Sao lưu & chuyển máy (25/10/2026). */
@Composable
internal fun ManChuyenMay(vm: PhienLamViec) = Cuon {
    val cm = vm.chuyenMay
    val luu = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) cm.luu(uri) }
    val mo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) cm.moFile(uri) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Sao lưu & chuyển máy", "Back up & move phones"), style = KieuTieuDeLon)
        Phu(t("Lưu dữ liệu của TiviKit ra một file để giữ lại hoặc mang sang điện thoại khác: bản sao lưu từng tivi, bộ cài đặt mẫu, danh sách tivi gần đây.",
            "Save TiviKit's data to one file to keep it or move it to another phone: each TV's backup, setup sets and recent TVs."))
    }

    The(t("Trên điện thoại này", "On this phone"), Icons.Rounded.PhoneAndroid) {
        DongSo(t("Bản sao lưu tivi", "TV backups"), "${vm.dsSaoLuu.size}")
        DongSo(t("Bộ cài đặt mẫu", "Setup sets"), "${cm.soBoCai}" + if (cm.coBoCai > 0) " · ${coChu(cm.coBoCai)}" else "")
        DongSo(t("Tivi gần đây", "Recent TVs"), "${vm.tiviGanDay.size}")
    }

    The(t("Lưu ra file", "Save to a file"), Icons.Rounded.SaveAlt) {
        Hang(t("Kèm khoá kết nối", "Include the connection key"),
            t("Điện thoại mới dùng ngay với các tivi đã cho phép, không phải bấm Cho phép lại. Ai có file này và vào cùng Wi-Fi với tivi đang bật gỡ lỗi là điều khiển được tivi đó: giữ file cẩn thận.",
                "The new phone works right away with TVs that already allowed this one. Anyone with this file on the same Wi-Fi as a TV with debugging on can control it: keep the file safe."),
            cm.kemKhoa) { cm.kemKhoa = it }
        NutChinh(t("Lưu ra file", "Save to a file"), enabled = !vm.dangBan, icon = Icons.Rounded.SaveAlt) { luu.launch(cm.tenFile()) }
        Phu(t("Gửi file qua Zalo, Drive, email… sang máy mới rồi mở ở mục Nhập bên dưới.", "Send the file to the new phone by chat, Drive, email… then open it under Import below."))
    }

    The(t("Nhập từ file", "Import from a file"), Icons.Rounded.FileOpen) {
        NutPhu(t("Chọn file sao lưu", "Pick a backup file"), Icons.Rounded.FolderOpen, enabled = !vm.dangBan) { mo.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
        Phu(t("Gộp vào dữ liệu đang có: bản sao lưu cùng tivi được gộp lại, bộ cài trùng thì giữ bộ đang có.", "Merged with what's here: backups of the same TV are combined; duplicate setup sets keep the existing one."))
    }

    if (cm.bao.isNotEmpty()) Phu(cm.bao, Xanh)
    if (cm.canMoLai) NutChinh(t("Mở lại app", "Reopen the app"), icon = Icons.Rounded.RestartAlt) { cm.moLaiApp() }

    if (cm.choNhap != null) AlertDialog(
        onDismissRequest = { cm.boNhap() },
        icon = { Icon(Icons.Rounded.Key, null, tint = Nhan) },
        title = { Text(t("Dùng khoá kết nối trong file?", "Use the connection key in the file?")) },
        text = { Text(t("File có khoá kết nối của điện thoại cũ. Dùng khoá này thì các tivi đã cho phép điện thoại cũ không hỏi lại; tivi đã cho phép điện thoại này sẽ phải bấm Cho phép lại một lần. Nên dùng nếu đây là điện thoại mới.",
            "The file has the old phone's connection key. With it, TVs that allowed the old phone won't ask again; TVs that allowed this phone will ask once more. Use it if this is a new phone.")) },
        confirmButton = { TextButton(onClick = { cm.xacNhan(dungKhoa = true) }) { Text(t("Dùng khoá này", "Use this key"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { cm.xacNhan(dungKhoa = false) }) { Text(t("Giữ khoá hiện tại", "Keep the current key")) } },
        containerColor = The1,
    )
}

@Composable
private fun DongSo(ten: String, giaTri: String) {
    androidx.compose.foundation.layout.Row {
        Text(ten, color = ChuPhu, modifier = Modifier.weight(1f))
        Text(giaTri, fontWeight = FontWeight.SemiBold)
    }
}
