package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.LienKetApp.MoBang
import vn.tivikit.dulieu.tenDep
import vn.tivikit.t

/**
 * Màn Set App Link (27/10/2026): mỗi trang một dòng — tên, tên miền, tivi đang mở link bằng gì; bấm để chọn app mở hoặc
 * Hỏi mỗi lần. Ô thêm trang khác bên dưới.
 */
@Composable
internal fun ManLienKetApp(vm: PhienLamViec) = Cuon {
    val lk = vm.lienKet
    TieuDeLon("Set App Link", t("Chọn app mở link của từng trang trên tivi, VD link YouTube gửi qua Telegram, Zalo, trình duyệt mở thẳng bằng SmartTube thay vì hỏi chọn app. Trang khác giữ nguyên.",
        "Choose the app that opens each site's links on the TV, e.g. YouTube links from Telegram, Zalo or a browser open straight in SmartTube instead of asking. Other sites stay as they are."))
    if (lk.bao.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nhan.copy(alpha = 0.12f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Link, null, tint = Nhan); Spacer(Modifier.width(10.dp))
        Text(lk.bao, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
    if (lk.daQuet && lk.ds.isEmpty()) Phu(t("Không thấy app nào trên tivi nhận link các trang quen thuộc; link đều mở bằng trình duyệt. Thử thêm trang khác bên dưới.",
        "No app on the TV takes links from the usual sites; links open in a browser. Try another site below."))
    if (lk.ds.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1)) {
        lk.ds.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = Vien)
            DongTrang(lk, d)
        }
    }
    The(t("Trang khác", "Another site"), Icons.Rounded.AddLink) {
        OutlinedTextField(lk.nhapLink, { lk.nhapLink = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(t("Tên miền hoặc link, VD vimeo.com", "Domain or link, e.g. vimeo.com")) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (!vm.dangBan) lk.themTrang() }))
        NutPhu(t("Xem app nào mở được", "See which apps open it"), Icons.Rounded.Search, enabled = !vm.dangBan && lk.nhapLink.isNotBlank()) { lk.themTrang() }
    }
    TextButton(onClick = { lk.docLai() }, enabled = !vm.dangBan) {
        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Đọc lại", "Read again"))
    }
    Phu(t("Áp dụng khi app khác mở link: Telegram, Zalo, trình duyệt, tin nhắn… Nút mic và Trợ lý Google không theo cài đặt này. Android 11 trở xuống đặt theo app: app được chọn mở mọi link mà nó nhận. Muốn trả về như cũ thì chọn Hỏi mỗi lần.",
        "Applies when another app opens a link: Telegram, Zalo, a browser, messages… The mic button and Google Assistant don't follow it. On Android 11 and older it's per app: the chosen app opens every link it handles. To undo, pick Ask every time."))
    lk.chon?.let { HopChonApp(vm, lk, it) }
}

/** Một trang: tên, tên miền, đang mở bằng gì (xanh khi đã có app riêng). */
@Composable
private fun DongTrang(lk: LienKetAppTivi, d: LienKetAppTivi.Dong) {
    val mo = d.moBang
    Row(Modifier.fillMaxWidth().clickable(enabled = d.ungVien.isNotEmpty()) { lk.chon = d }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.trang.ten, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Phu(d.trang.mien)
            Text(if (d.ungVien.isEmpty()) t("Chưa có app nào trên tivi nhận link này", "No app on the TV takes this link") else trangThai(mo),
                fontSize = 13.sp, lineHeight = 18.sp, color = if (mo is MoBang.App) Xanh else ChuPhu)
        }
        if (d.ungVien.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp), tint = ChuPhu.copy(alpha = 0.7f))
        }
    }
}

/** Dòng trạng thái dưới tên trang. */
private fun trangThai(mo: MoBang): String = when (mo) {
    MoBang.Hoi -> t("Tivi hỏi chọn app mỗi lần", "The TV asks which app each time")
    is MoBang.App -> t("Mở bằng ${tenDep(mo.goi)}", "Opens in ${tenDep(mo.goi)}")
    is MoBang.TrinhDuyet -> t("Mở bằng trình duyệt ${tenDep(mo.goi)}", "Opens in the ${tenDep(mo.goi)} browser")
    MoBang.KhongCo -> t("Không mở được", "Can't be opened")
}

/** Hộp chọn app mở link của một trang: các app mở được + Hỏi mỗi lần. */
@Composable
private fun HopChonApp(vm: PhienLamViec, lk: LienKetAppTivi, d: LienKetAppTivi.Dong) {
    AlertDialog(
        onDismissRequest = { lk.chon = null },
        icon = { Icon(Icons.Rounded.Link, null, tint = Nhan) },
        title = { Text(t("Mở link ${d.trang.ten} bằng", "Open ${d.trang.ten} links in")) },
        text = { NoiDungChonApp(lk, d, !vm.dangBan) },
        confirmButton = { TextButton(onClick = { lk.chon = null }) { Text(t("Đóng", "Close")) } },
        containerColor = The1,
    )
}

/** Thân hộp chọn app (tách riêng để xem trước được): từng app mở được, rồi Hỏi mỗi lần. */
@Composable
internal fun NoiDungChonApp(lk: LienKetAppTivi, d: LienKetAppTivi.Dong, bat: Boolean) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        d.ungVien.forEach { g -> LuaChon(tenDep(g), g, d.moBang == MoBang.App(g), bat) { lk.dat(d, g) } }
        LuaChon(t("Hỏi mỗi lần", "Ask every time"), t("Mặc định của tivi", "The TV's default"), d.moBang !is MoBang.App, bat) { lk.dat(d, null) }
    }
}

@Composable
private fun LuaChon(ten: String, phu: String, chon: Boolean, bat: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = bat, onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        OTich(chon); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ten, fontSize = 15.sp, color = ChuChinh)
            Phu(phu)
        }
    }
}
