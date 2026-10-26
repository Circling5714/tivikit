package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.ThoiGianXem
import vn.tivikit.t

// ---------------- Khoá trẻ em: thời gian xem, giới hạn mỗi ngày (25/10/2026) ----------------

private val TEN_THU_DAI = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")
private val TEN_THU_DAI_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** "Hôm nay", "Hôm qua", "Thứ Ba, 21/10". */
private fun tenNgay(bc: ThoiGianXem, i: Int): String {
    val n = bc.ngay[i]
    return when (i) {
        bc.ngay.lastIndex -> if (n.ngay == bc.homNay) t("Hôm nay", "Today") else n.ngay
        bc.ngay.lastIndex - 1 -> t("Hôm qua", "Yesterday")
        else -> t(TEN_THU_DAI.getOrElse(n.thu - 1) { "" }, TEN_THU_DAI_EN.getOrElse(n.thu - 1) { "" }) + ", " + n.ngay.takeLast(5).split("-").reversed().joinToString("/")
    }
}

/** Giới hạn của ngày và còn / quá bao nhiêu. */
private fun dongGioiHan(bc: ThoiGianXem, i: Int): String {
    val n = bc.ngay[i]
    if (!n.daDem) return t("Chưa đếm ngày này: app khoá đếm giờ xem từ khi cập nhật bản mới.", "Not counted: the lock app counts from its update on.")
    if (n.gioiHanPhut <= 0) return t("Không giới hạn", "No limit")
    val han = n.gioiHanPhut * 60L
    val gh = t("Giới hạn ", "Limit ") + ThoiGianXem.chuGioiHan(n.gioiHanPhut)
    return when {
        n.giay > han -> "$gh · " + t("quá ", "over by ") + ThoiGianXem.chu(n.giay - han) + t(" (mở bằng PIN)", " (PIN unlocks)")
        i == bc.ngay.lastIndex && n.ngay == bc.homNay -> "$gh · " + t("còn ", "") + ThoiGianXem.chu(han - n.giay) + t("", " left")
        else -> "$gh · " + t("trong giới hạn", "within the limit")
    }
}

/** Thẻ báo cáo: thời gian xem ngày đang chọn, cột 7 ngày (chạm để chọn ngày), app đã mở ngày đó. */
@Composable
internal fun TheThoiGianXem(bc: ThoiGianXem) {
    if (bc.ngay.isEmpty()) return
    var chon by remember(bc) { mutableIntStateOf(bc.ngay.lastIndex) }
    val n = bc.ngay[chon]
    The(t("Thời gian xem", "Screen time"), Icons.Rounded.BarChart) {
        Column {
            Text(tenNgay(bc, chon), fontSize = 13.sp, color = ChuPhu)
            Text(if (n.daDem) ThoiGianXem.chu(n.giay) else "—", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp)
            Phu(dongGioiHan(bc, chon))
        }
        CotTuan(bc, chon) { chon = it }
        if (bc.ngay.any { it.gioiHanPhut > 0 }) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = 14.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(ChuChinh))
            Text(t("Giới hạn mỗi ngày", "Daily limit"), fontSize = 12.sp, color = ChuPhu)
        }
        if (!bc.coQuyen) Phu(t("Tivi chưa cho đọc thống kê ứng dụng.", "The TV doesn't allow reading app usage."), Cam)
        else if (n.apps.isEmpty()) Phu(t("Không có app nào mở quá 1 phút ngày này.", "No app was open for over a minute that day."))
        else {
            Text(t("Ứng dụng đã mở", "Apps opened"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            val lon = n.apps.maxOf { it.giay }.coerceAtLeast(1)
            val nhan = Nhan
            n.apps.forEach { a ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(a.ten, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(ThoiGianXem.chu(a.giay), fontSize = 13.sp, color = ChuPhu, modifier = Modifier.padding(start = 8.dp))
                    }
                    Box(Modifier.fillMaxWidth((a.giay.toFloat() / lon).coerceIn(0.02f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(nhan))
                }
            }
        }
        Phu(t("Giờ xem tính lúc màn hình tivi sáng, không tính màn hình chờ và lúc đang khoá. Ứng dụng theo thống kê của tivi, không tính màn hình chính.",
            "Screen time counts while the TV screen is on, not the screensaver or while locked. Apps come from the TV's usage stats, home screen excluded."))
    }
}

/** Bước lưới trục giờ: bước gọn nhỏ nhất cho tối đa 3 vạch. */
private fun buocLuoi(tran: Long): Long =
    listOf(15, 30, 60, 120, 180, 240, 360, 480, 720).map { it * 60L }.firstOrNull { (tran + it - 1) / it <= 3 } ?: (24 * 3600L)

/** Cột thời gian xem 7 ngày: một màu, cột đang chọn đậm; vạch ngang là giới hạn ngày đó; chạm cột để chọn ngày. */
@Composable
private fun CotTuan(bc: ThoiGianXem, chon: Int, doiChon: (Int) -> Unit) {
    val nhan = Nhan; val vien = Vien; val chuPhu = ChuPhu; val vach = ChuChinh
    val tm = rememberTextMeasurer()
    val kieuTruc = TextStyle(fontSize = 11.sp, color = chuPhu)
    val lon = maxOf(bc.ngay.maxOf { if (it.daDem) it.giay else 0L }, bc.ngay.maxOf { it.gioiHanPhut * 60L }, 3600L)
    val buoc = buocLuoi(lon)
    val tran = (lon + buoc - 1) / buoc * buoc
    val nhanTruc = remember(tran, buoc, kieuTruc, vn.tivikit.NgonNgu.tiengViet) { (1..(tran / buoc).toInt()).map { it * buoc to tm.measure(ThoiGianXem.ngan(it * buoc), kieuTruc) } }
    Column(Modifier.fillMaxWidth()) {
        // Vùng vẽ: lưới mảnh + nhãn trục bên phải; trên cùng chừa chỗ cho nhãn vạch cao nhất.
        Box(Modifier.fillMaxWidth().height(140.dp).drawBehind {
            val tren = 8.dp.toPx(); val rongTruc = 36.dp.toPx(); val cao = size.height - tren
            val rong = size.width - rongTruc
            drawLine(vien, Offset(0f, size.height - 0.5f), Offset(rong, size.height - 0.5f), 1f)
            nhanTruc.forEach { (v, chu) ->
                val y = tren + cao * (1f - v.toFloat() / tran)
                drawLine(vien.copy(alpha = 0.6f), Offset(0f, y), Offset(rong, y), 1f)
                drawText(chu, topLeft = Offset(rong + 6.dp.toPx(), y - chu.size.height / 2f))
            }
        }) {
            Row(Modifier.fillMaxSize().padding(end = 36.dp)) {
                bc.ngay.forEachIndexed { i, n ->
                    val dangChon = i == chon
                    val mo = tenNgay(bc, i) + ": " + (if (n.daDem) ThoiGianXem.chu(n.giay) else t("chưa đếm", "not counted")) +
                        (if (n.gioiHanPhut > 0) t(", giới hạn ", ", limit ") + ThoiGianXem.chuGioiHan(n.gioiHanPhut) else "")
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .semantics { contentDescription = mo; selected = dangChon }
                        .clickable(role = Role.Tab) { doiChon(i) }
                        .drawBehind {
                            val tren = 8.dp.toPx(); val cao = size.height - tren
                            val cot = minOf(24.dp.toPx(), size.width * 0.55f); val giua = size.width / 2f
                            if (n.daDem && n.giay > 0) {
                                val h = maxOf(cao * n.giay / tran, 2.dp.toPx())
                                val r = minOf(4.dp.toPx(), h)
                                drawPath(Path().apply {
                                    addRoundRect(RoundRect(giua - cot / 2, size.height - h, giua + cot / 2, size.height, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r)))
                                }, if (dangChon) nhan else nhan.copy(alpha = 0.4f))
                            }
                            if (n.gioiHanPhut > 0) {
                                val y = tren + cao * (1f - n.gioiHanPhut * 60f / tran)
                                drawLine(vach, Offset(giua - cot / 2 - 3.dp.toPx(), y), Offset(giua + cot / 2 + 3.dp.toPx(), y), 2.dp.toPx(), StrokeCap.Round)
                            }
                        })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 36.dp)) {
            bc.ngay.forEachIndexed { i, n ->
                Text(t(TEN_THU.getOrElse(n.thu - 1) { "" }, TEN_THU_EN.getOrElse(n.thu - 1) { "" }), Modifier.weight(1f),
                    fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (i == chon) ChuChinh else ChuPhu, fontWeight = if (i == chon) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

private val MUC_GIOI_HAN = listOf(0, 30, 60, 90, 120, 180, 240)

/** Thẻ giới hạn giờ xem mỗi ngày: ngày thường và cuối tuần riêng. */
@Composable
internal fun TheGioiHan(vm: PhienLamViec) = The(t("Giới hạn mỗi ngày", "Daily limit"), Icons.Rounded.HourglassBottom) {
    DongMucGioiHan(t("Thứ Hai – Thứ Sáu", "Monday – Friday"), vm.khoaPhutThuong) { vm.khoaPhutThuong = it }
    DongMucGioiHan(t("Thứ Bảy, Chủ Nhật", "Saturday, Sunday"), vm.khoaPhutCuoiTuan) { vm.khoaPhutCuoiTuan = it }
    Phu(t("Tính lúc màn hình tivi sáng (không tính màn hình chờ, lúc đang khoá). Còn 5 phút tivi báo trước; hết giờ thì khoá tới hết ngày, nhập đúng PIN mở thêm ${vm.KHOA_MO_TAM} phút.",
        "Counts while the TV screen is on (not the screensaver or while locked). The TV warns 5 minutes ahead; when time is up it stays locked for the rest of the day, and the PIN adds ${vm.KHOA_MO_TAM} minutes."))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DongMucGioiHan(ten: String, phut: Int, doi: (Int) -> Unit) {
    Text(ten, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (MUC_GIOI_HAN + listOf(phut).filter { it !in MUC_GIOI_HAN }).sorted().forEach { p ->
            ChonChip(ThoiGianXem.chuGioiHan(p), p == phut) { doi(p) }
        }
    }
}
