package vn.tivikit.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.tivikit.NgonNgu
import vn.tivikit.dulieu.DongLenh
import vn.tivikit.dulieu.XaPhuong
import vn.tivikit.t

// Khung kết quả luôn tối như Terminal, không theo chế độ sáng / tối của điện thoại.
private val NEN_KHUNG = Color(0xFF0D0D0F)
private val VIEN_KHUNG = Color(0xFF2C2C2E)
private val CHU_RA = Color(0xFFE5E5EA)
private val CHU_LENH = Color(0xFF64D2FF)
private val CHU_GHI = Color(0xFF98989F)
private val CHU_LOI = Color(0xFFFF6961)
private val KIEU_KHUNG = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
private val KIEU_LENH = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)

/**
 * Màn Dòng lệnh ADB (27/10/2026): lần đầu là trang cảnh báo; sau đó hai thẻ — Dòng lệnh (khung kết quả, lịch sử, ô gõ)
 * và Lệnh mẫu (tìm, nhảy theo nhóm, bấm để điền hoặc chạy ngay). Không bọc trong Cuon: khung kết quả tự cuộn và chiếm hết
 * phần cao còn lại, ô gõ luôn nằm dưới đáy (trên bàn phím).
 */
@Composable
internal fun ManDongLenh(vm: PhienLamViec) {
    val dl = vm.dongLenh
    if (!dl.daHieu) { TrangCanhBao(dl); return }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Dòng lệnh ADB", "ADB shell"), style = KieuTieuDeLon)
        ChonDoan(listOf(t("Dòng lệnh", "Shell"), t("Lệnh mẫu (${DongLenh.DS.size})", "Examples (${DongLenh.DS.size})")), dl.tab) { dl.tab = it }
        if (dl.tab == 0) KhungLenh(vm, dl, Modifier.weight(1f)) else DsMau(dl, Modifier.weight(1f))
    }
    dl.hoi?.let { HopHoi(dl, it) }
    dl.dien?.let { HopDien(vm, dl, it) }
    if (dl.moLichSu) HopLichSu(dl)
}

/** Lần đầu mở: nói rõ dòng lệnh đi thẳng tới tivi, không qua lớp bảo vệ nào, và đừng dán lệnh người lạ gửi. */
@Composable
private fun TrangCanhBao(dl: DongLenhTivi) = Cuon {
    DauTrang({ OIcon(Icons.Rounded.Terminal, 64.dp, MauO.xam) }, t("Dòng lệnh ADB", "ADB shell"),
        t("Gõ lệnh shell chạy thẳng trên tivi, như adb shell trên máy tính nhưng không cần máy tính. Kèm gần 200 lệnh mẫu có giải thích.",
            "Run shell commands right on the TV, like adb shell on a computer but without one. Comes with nearly 200 explained examples."))
    The(t("Trước khi dùng", "Before you start"), Icons.Rounded.WarningAmber) {
        BuocSo(1, t("Lệnh chạy với quyền ADB và đi thẳng tới tivi, không qua các lớp bảo vệ của TiviKit. Gõ sai có thể làm mất màn hình chính, xoá dữ liệu app hoặc mất kết nối.",
            "Commands run with ADB rights straight on the TV, past TiviKit's safety checks. A wrong one can remove the Home screen, wipe app data or cut the connection."))
        BuocSo(2, t("Chỉ chạy lệnh bạn hiểu. Đừng dán lệnh người lạ gửi qua Facebook, Zalo, Telegram, kể cả khi họ hứa \"xem kênh miễn phí\".",
            "Only run commands you understand. Don't paste commands strangers send on Facebook, Zalo or Telegram, even if they promise \"free channels\"."))
        BuocSo(3, t("Lệnh nguy hiểm được hỏi lại trước khi chạy. Lệnh tắt gói được ghi vào bản sao lưu để Khôi phục tivi bật lại; xoá file, đổi cài đặt thì không tự hoàn tác (đổi cài đặt có in giá trị cũ để bạn tự trả lại).",
            "Dangerous commands ask first. Disabled packages are recorded in the backup so Restore turns them back on; deleted files and changed settings aren't undone (changed settings print the old value so you can put it back)."))
        BuocSo(4, t("Lệnh phải gõ tương tác (vi, su không kèm -c) không chạy được ở đây; lệnh chạy liên tục như logcat thì bấm Dừng. Lệnh cd được giữ cho lệnh sau.",
            "Interactive commands (vi, su without -c) can't run here; for never-ending ones like logcat press Stop. cd carries over to the next command."))
    }
    NutChinh(t("Tôi hiểu, mở dòng lệnh", "I understand, open the shell"), icon = Icons.Rounded.Terminal) { dl.dongYCanhBao() }
}

/** Thẻ Dòng lệnh: khung kết quả (thư mục, root, chép, xoá), các lệnh vừa chạy, ô gõ + nút Chạy / Dừng. */
@Composable
private fun KhungLenh(vm: PhienLamViec, dl: DongLenhTivi, modifier: Modifier) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Viền mảnh: ở chế độ tối khung gần trùng màu nền trang.
        Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NEN_KHUNG).border(0.5.dp, VIEN_KHUNG, RoundedCornerShape(14.dp))) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(vm.thongTin?.tenHienThi?.let { "$it  ${dl.thuMuc}" } ?: dl.thuMuc, style = KIEU_KHUNG, color = CHU_GHI, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (dl.laRoot) Chip("root", Cam)
                IconButton(onClick = {
                    clip.setText(AnnotatedString(dl.chepHet()))
                    // Android 13+ tự hiện thông báo đã chép.
                    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, t("Đã chép kết quả", "Output copied"), Toast.LENGTH_SHORT).show()
                }, enabled = dl.dong.isNotEmpty()) { Icon(Icons.Rounded.ContentCopy, t("Chép kết quả", "Copy output"), tint = CHU_GHI) }
                IconButton(onClick = { dl.xoaManHinh() }, enabled = dl.dong.isNotEmpty()) { Icon(Icons.Rounded.DeleteSweep, t("Xoá khung kết quả", "Clear the output"), tint = CHU_GHI) }
            }
            HorizontalDivider(thickness = 0.5.dp, color = VIEN_KHUNG)
            KetQua(dl, Modifier.weight(1f))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(Modifier.clip(RoundedCornerShape(50)).background(Nhan.copy(alpha = 0.13f)).clickable { dl.moLichSu = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, Modifier.size(16.dp), tint = Nhan); Spacer(Modifier.width(4.dp))
                    Text(t("Lịch sử", "History"), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Nhan)
                }
            }
            items(dl.lichSu.take(6)) { l ->
                Text(l, style = KIEU_KHUNG, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(50)).background(NenO).clickable { dl.dungLai(l) }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(dl.nhap, { dl.nhap = it }, Modifier.weight(1f), textStyle = KIEU_LENH.copy(fontSize = 14.sp), maxLines = 4, shape = RoundedCornerShape(12.dp),
                placeholder = { Text(t("Gõ lệnh, VD getprop ro.product.model", "Type a command, e.g. getprop ro.product.model"), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { dl.gui() }))
            Spacer(Modifier.width(8.dp))
            if (dl.dangChay) NutTron(Icons.Rounded.Stop, t("Dừng", "Stop"), DoTuoi, true) { dl.dung() }
            else NutTron(Icons.Rounded.PlayArrow, t("Chạy", "Run"), Nhan, dl.nhap.text.isNotBlank()) { dl.gui() }
        }
    }
}

/** Các dòng kết quả, chọn chép được; đang ở cuối thì cuộn theo chữ mới, kéo lên đọc thì đứng yên. */
@Composable
private fun KetQua(dl: DongLenhTivi, modifier: Modifier) {
    val ds = rememberLazyListState()
    val oCuoi by remember { derivedStateOf { val l = ds.layoutInfo; l.totalItemsCount == 0 || (l.visibleItemsInfo.lastOrNull()?.index ?: 0) >= l.totalItemsCount - 2 } }
    LaunchedEffect(dl.lanThem) {
        if (dl.dong.isNotEmpty() && (oCuoi || dl.dong.last().loai == DongLenhTivi.Loai.LENH)) ds.scrollToItem(dl.dong.lastIndex)
    }
    if (dl.dong.isEmpty()) {
        Column(modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Gõ lệnh ở ô bên dưới, hoặc chọn ở thẻ Lệnh mẫu.", "Type a command below, or pick one under Examples."), style = KIEU_KHUNG, color = CHU_GHI)
            Text(t("Thử: getprop ro.product.model", "Try: getprop ro.product.model"), style = KIEU_KHUNG, color = CHU_GHI)
        }
        return
    }
    SelectionContainer(modifier) {
        LazyColumn(state = ds, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            items(dl.dong.size) { i ->
                val d = dl.dong[i]
                Text(d.chu, style = KIEU_KHUNG, color = when (d.loai) {
                    DongLenhTivi.Loai.LENH -> CHU_LENH
                    DongLenhTivi.Loai.RA -> CHU_RA
                    DongLenhTivi.Loai.GHI_CHU -> CHU_GHI
                    DongLenhTivi.Loai.LOI -> CHU_LOI
                }, fontWeight = if (d.loai == DongLenhTivi.Loai.LENH) FontWeight.SemiBold else null,
                    modifier = if (d.loai == DongLenhTivi.Loai.LENH && i > 0) Modifier.padding(top = 8.dp) else Modifier)
            }
        }
    }
}

/** Nút tròn đặc màu cạnh ô gõ (Chạy / Dừng). */
@Composable
private fun NutTron(icon: ImageVector, moTa: String, mau: Color, bat: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(if (bat) mau else NenO).clickable(enabled = bat, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, moTa, tint = if (bat) Color.White else ChuPhu)
    }
}

/** Thẻ Lệnh mẫu: tìm (không cần gõ dấu), hàng nhóm để nhảy tới, mỗi nhóm một thẻ — hay dùng ở trên, ít dùng ở dưới. */
@Composable
private fun DsMau(dl: DongLenhTivi, modifier: Modifier) {
    var tim by rememberSaveable { mutableStateOf("") }
    val ds = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tiengViet = NgonNgu.tiengViet
    val loc = remember(tim, tiengViet) {
        val k = XaPhuong.boDau(tim.trim())
        if (k.isEmpty()) DongLenh.DS else DongLenh.DS.filter { m -> XaPhuong.boDau("${m.lenh} ${m.vi} ${m.en}").contains(k) }
    }
    val nhom = remember(loc) { loc.groupBy { it.nhom } }
    // Vị trí tiêu đề mỗi nhóm trong danh sách (tiêu đề + các dòng của nhóm trước).
    val viTri = remember(nhom) { var i = 0; nhom.mapValues { (_, ms) -> i.also { i += 1 + ms.size } } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(tim, { tim = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (tim.isNotEmpty()) IconButton(onClick = { tim = "" }) { Icon(Icons.Rounded.Close, t("Xoá", "Clear")) } },
            placeholder = { Text(t("Tìm: ip, cache, mở app, wifi…", "Search: ip, cache, open app, wifi…")) })
        if (tim.isBlank()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(nhom.keys.toList()) { n ->
                Text(n.ten, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Nhan,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Nhan.copy(alpha = 0.12f))
                        .clickable { scope.launch { ds.animateScrollToItem(viTri.getValue(n)) } }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        if (loc.isEmpty()) Phu(t("Không có lệnh mẫu nào khớp.", "No examples match."))
        LazyColumn(state = ds, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            nhom.forEach { (n, ms) ->
                item(key = "nhom-" + n.name) {
                    Text(n.ten.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ChuPhu, letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
                }
                itemsIndexed(ms, key = { _, m -> m.lenh }) { i, m -> DongMau(dl, m, i == 0, i == ms.lastIndex) }
            }
            item(key = "chu-thich") {
                Phu(t("⚠ cam: hỏi lại trước khi chạy · ⚠ đỏ: nguy hiểm, phải tích xác nhận. Bấm một lệnh để đưa vào ô gõ (lệnh có chỗ trống như <gói> thì điền trước); nút ▶ chạy ngay.",
                    "Orange ⚠: asks before running · red ⚠: dangerous, needs a tick. Tap a command to put it in the box (fill blanks like <package> first); ▶ runs it right away."))
            }
        }
    }
}

/** Một lệnh mẫu: lệnh chữ đơn cách, giải thích, dấu cảnh báo, nút chạy ngay (khi không có chỗ trống). */
@Composable
private fun DongMau(dl: DongLenhTivi, m: DongLenh.Mau, dau: Boolean, cuoi: Boolean) {
    val bo = 16.dp
    val goc = RoundedCornerShape(topStart = if (dau) bo else 0.dp, topEnd = if (dau) bo else 0.dp, bottomStart = if (cuoi) bo else 0.dp, bottomEnd = if (cuoi) bo else 0.dp)
    Column(Modifier.fillMaxWidth().clip(goc).background(The1)) {
        if (!dau) HorizontalDivider(Modifier.padding(start = 14.dp), thickness = 0.5.dp, color = Vien)
        Row(Modifier.fillMaxWidth().clickable { dl.chonMau(m) }.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(DongLenh.hien(m.lenh), style = KIEU_LENH, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Phu(m.moTa)
            }
            when (m.mucDo) {
                DongLenh.MucDo.NGUY_HIEM, DongLenh.MucDo.KHONG_CHAY -> Icon(Icons.Rounded.WarningAmber, t("Nguy hiểm", "Dangerous"), Modifier.padding(start = 6.dp).size(18.dp), tint = DoTuoi)
                DongLenh.MucDo.CAN_THAN -> Icon(Icons.Rounded.WarningAmber, t("Hỏi lại trước khi chạy", "Asks before running"), Modifier.padding(start = 6.dp).size(18.dp), tint = Cam)
                else -> {}
            }
            if (m.coCho) Icon(Icons.Rounded.Edit, t("Cần điền", "Needs input"), Modifier.padding(horizontal = 14.dp).size(18.dp), tint = ChuPhu)
            else IconButton(onClick = { dl.chayMau(m) }, enabled = !dl.dangChay) {
                Icon(Icons.Rounded.PlayArrow, t("Chạy ngay", "Run now"), tint = if (dl.dangChay) ChuPhu else Nhan)
            }
        }
    }
}

/** Hộp hỏi lại: lệnh, từng cảnh báo; lệnh nguy hiểm phải tích "tôi hiểu" mới bấm Vẫn chạy được. */
@Composable
private fun HopHoi(dl: DongLenhTivi, h: DongLenhTivi.Hoi) {
    var chiu by remember(h) { mutableStateOf(false) }
    val nguy = h.nguyHiem
    val duoc = !nguy || chiu
    AlertDialog(
        onDismissRequest = { dl.hoi = null },
        icon = { Icon(Icons.Rounded.WarningAmber, null, tint = if (nguy) DoTuoi else Cam) },
        title = { Text(if (nguy) t("Lệnh nguy hiểm", "Dangerous command") else t("Chạy lệnh này?", "Run this command?")) },
        text = { NoiDungHoi(h, chiu) { chiu = it } },
        confirmButton = {
            TextButton(onClick = { dl.dongY() }, enabled = duoc) { Text(t("Vẫn chạy", "Run anyway"), color = if (duoc) DoTuoi else ChuPhu, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = { dl.hoi = null }) { Text(t("Thôi", "Cancel")) } },
        containerColor = The1,
    )
}

/** Thân hộp hỏi lại (tách riêng để xem trước được): lệnh, từng cảnh báo, ô tích "tôi hiểu" khi nguy hiểm. */
@Composable
internal fun NoiDungHoi(h: DongLenhTivi.Hoi, chiu: Boolean, doiChiu: (Boolean) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(h.lenh, style = KIEU_LENH, color = ChuChinh, maxLines = 8, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(The2).padding(10.dp))
        h.canhBao.filter { it.mucDo != DongLenh.MucDo.BAO }.forEach { cb ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (cb.mucDo == DongLenh.MucDo.NGUY_HIEM) DoTuoi else Cam))
                Spacer(Modifier.width(10.dp))
                Text(cb.chu, fontSize = 14.sp, lineHeight = 20.sp, color = ChuChinh)
            }
        }
        if (h.canhBao.any { it.matKetNoi }) Phu(t("Sau lệnh này TiviKit có thể mất kết nối với tivi.", "TiviKit may lose the TV after this command."), Cam)
        if (h.nguyHiem) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).toggleable(chiu, role = Role.Checkbox, onValueChange = doiChiu).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OTich(chiu); Spacer(Modifier.width(10.dp))
            Text(t("Tôi hiểu hậu quả và vẫn muốn chạy", "I understand the risk and still want to run it"), fontSize = 14.sp, color = ChuChinh)
        }
    }
}

/** Hộp Điền lệnh: một ô cho mỗi chỗ trống; ô gói có danh sách gói đang có trên tivi, ô bảng chọn global / secure / system. */
@Composable
private fun HopDien(vm: PhienLamViec, dl: DongLenhTivi, m: DongLenh.Mau) {
    val gt = remember(m) { mutableStateMapOf<DongLenh.Cho, String>() }
    val du = DongLenh.choTrong(m.lenh).all { it.hopLe(gt[it].orEmpty()) }
    AlertDialog(
        onDismissRequest = { dl.dien = null },
        icon = { Icon(Icons.Rounded.Edit, null, tint = Nhan) },
        title = { Text(t("Điền lệnh", "Fill in the command")) },
        text = { NoiDungDien(vm, m, gt) },
        confirmButton = { TextButton(onClick = { dl.dienXong(m, gt.toMap()) }, enabled = du) { Text(t("Xong", "Done"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { dl.dien = null }) { Text(t("Thôi", "Cancel")) } },
        containerColor = The1,
    )
}

/** Thân hộp Điền lệnh (tách riêng để xem trước được). */
@Composable
internal fun NoiDungDien(vm: PhienLamViec, m: DongLenh.Mau, gt: MutableMap<DongLenh.Cho, String>) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(DongLenh.hien(m.lenh), style = KIEU_LENH, color = ChuChinh, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(The2).padding(10.dp))
        Phu(m.moTa)
        DongLenh.choTrong(m.lenh).forEach { c ->
            val v = gt[c].orEmpty()
            if (c == DongLenh.Cho.BANG) {
                val bang = listOf("global", "secure", "system")
                Text(c.nhan, fontSize = 13.sp, color = ChuPhu)
                ChonDoan(bang, bang.indexOf(v)) { gt[c] = bang[it] }
            } else {
                OutlinedTextField(v, { gt[c] = it }, Modifier.fillMaxWidth(), textStyle = KIEU_LENH.copy(fontSize = 14.sp), shape = RoundedCornerShape(12.dp),
                    label = { Text(c.hien) }, supportingText = { Text(c.nhan) }, isError = c.saiRo(v),
                    singleLine = c != DongLenh.Cho.CHU && c != DongLenh.Cho.GIA_TRI,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                        keyboardType = if (c == DongLenh.Cho.X || c == DongLenh.Cho.Y || c == DongLenh.Cho.SO) KeyboardType.Number else KeyboardType.Ascii))
                if (c == DongLenh.Cho.GOI) GoiTrenTv(vm, v) { gt[c] = it }
            }
        }
    }
}

/** Gói trên tivi khớp chữ đang gõ, app có biểu tượng (app người dùng thấy) trước; bấm để điền. */
@Composable
private fun GoiTrenTv(vm: PhienLamViec, dangGo: String, chon: (String) -> Unit) {
    val tt = vm.trangThai ?: return
    val ds = remember(tt, dangGo) {
        val k = dangGo.trim().lowercase()
        tt.caiDat.filter { k.isEmpty() || it.lowercase().contains(k) }.sortedWith(compareBy({ it !in tt.coBieuTuong }, { it })).take(40)
    }
    if (ds.isEmpty() || ds == listOf(dangGo.trim())) return
    Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)).background(The2).verticalScroll(rememberScrollState())) {
        ds.forEach { p ->
            Text(p, style = KIEU_LENH, color = if (p in tt.coBieuTuong) ChuChinh else ChuPhu, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { chon(p) }.padding(horizontal = 12.dp, vertical = 9.dp))
        }
    }
}

/** Hộp Lịch sử: lệnh đã chạy (lưu trên điện thoại này), bấm để lấy lại vào ô gõ. */
@Composable
private fun HopLichSu(dl: DongLenhTivi) {
    AlertDialog(
        onDismissRequest = { dl.moLichSu = false },
        icon = { Icon(Icons.Rounded.History, null, tint = Nhan) },
        title = { Text(t("Lệnh đã chạy", "Command history")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Phu(if (dl.lichSu.isEmpty()) t("Chưa chạy lệnh nào. Lệnh đã chạy chỉ lưu trên điện thoại này.", "No commands yet. Commands you run are kept on this phone only.")
                    else t("Chỉ lưu trên điện thoại này (${dl.lichSu.size} lệnh gần nhất).", "Kept on this phone only (last ${dl.lichSu.size} commands)."))
                if (dl.lichSu.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(dl.lichSu.toList()) { l ->
                        Text(l, style = KIEU_LENH, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { dl.dungLai(l) }.padding(horizontal = 4.dp, vertical = 9.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = Vien)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { dl.moLichSu = false }) { Text(t("Đóng", "Close")) } },
        dismissButton = { if (dl.lichSu.isNotEmpty()) TextButton(onClick = { dl.xoaLichSu() }) { Text(t("Xoá lịch sử", "Clear history"), color = DoTuoi) } },
        containerColor = The1,
    )
}
