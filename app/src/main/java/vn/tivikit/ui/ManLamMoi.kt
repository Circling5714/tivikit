package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.adb.TinhTrangTv
import vn.tivikit.dulieu.LamMoi
import vn.tivikit.t
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Màn Làm mới tivi (25/10/2026): tình trạng RAM / bộ nhớ / thời gian chạy, làm mới một chạm, gợi ý khởi động lại.
 * 26/10/2026: thêm tải CPU, nhiệt độ, ai đang ăn CPU và tivi đang bận việc gì.
 */
@Composable
internal fun ManLamMoi(vm: PhienLamViec) = Cuon {
    val lm = vm.lamMoi
    var hoiKhoiDong by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Làm mới tivi", "Refresh the TV"), style = KieuTieuDeLon)
        Phu(t("Xem RAM, CPU, bộ nhớ, app nào đang làm tivi chậm và tivi đã chạy bao lâu chưa khởi động lại. Làm mới đóng các app đang nằm chờ trong bộ nhớ; cache chỉ xoá khi bộ nhớ gần đầy.",
            "See RAM, CPU, storage, what's slowing the TV and how long it has run since it last restarted. Refresh closes apps waiting in memory; caches are only cleared when storage is nearly full."))
    }

    val tt = lm.tinhTrang
    val chayLau = tt?.chayGiay?.let { LamMoi.chayLau(it) } == true
    The(t("Tình trạng", "Status"), Icons.Rounded.Speed) {
        if (tt == null) Phu(t("Chưa đọc được.", "Not read yet."))
        else {
            val ram = tt.ram
            ChiSo("RAM", ram?.let { t("Trống ${kb(it.conKb)} / ${kb(it.tongKb)}", "${kb(it.conKb)} free of ${kb(it.tongKb)}") } ?: "?",
                ram?.let { 1f - it.conKb.toFloat() / it.tongKb }, ram != null && LamMoi.ramThap(ram))
            val bn = tt.boNho
            ChiSo(t("Bộ nhớ", "Storage"), bn?.let { t("Trống ${kb(it.conKb)} / ${kb(it.tongKb)}", "${kb(it.conKb)} free of ${kb(it.tongKb)}") } ?: "?",
                bn?.let { it.dungKb.toFloat() / it.tongKb }, bn != null && LamMoi.boNhoGanDay(bn.tongKb, bn.conKb))
            ChiSo(t("Chạy liên tục", "Running for"), tt.chayGiay?.let { thoiGian(it) } ?: "?", null, chayLau)
            tt.tai?.let { tai ->
                val nhan = tt.soNhan
                ChiSo("CPU", if (nhan != null) t("Tải ${so1(tai)} · $nhan nhân", "Load ${so1(tai)} · $nhan cores") else t("Tải ${so1(tai)}", "Load ${so1(tai)}"),
                    nhan?.let { (tai / (it * 2)).toFloat() }, nhan != null && LamMoi.quaTai(tai, nhan))
            }
            tt.nhietDo?.let { ChiSo(t("Nhiệt độ chip", "Chip temperature"), "$it°C", null, it >= LamMoi.NONG) }
        }
        TextButton(onClick = { lm.docLai() }, enabled = !vm.dangBan) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Đọc lại", "Read again"))
        }
    }

    // Quá tải: nói tivi đang bận việc gì và nên làm gì (VD CH Play cập nhật dồn sau khi đăng nhập lại: đợi rồi khởi động lại).
    if (tt != null && tt.tai != null && tt.soNhan != null && LamMoi.quaTai(tt.tai, tt.soNhan)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NenCanhBao).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = Cam); Spacer(Modifier.width(8.dp))
                Text(t("Tivi đang quá tải", "The TV is overloaded"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Phu(loiKhuyenQuaTai(tt), ChuChinh.copy(alpha = 0.85f))
        }
    }
    if (tt != null && tt.anCpu.isNotEmpty()) The(t("Đang ăn CPU", "Using the CPU"), Icons.Rounded.Memory) {
        tt.anCpu.take(5).forEach { DongCpu(it, tt.soNhan) }
        Phu(if (tt.soNhan != null) t("Phần trăm trên cả chip (${tt.soNhan} nhân), trong vài phút gần nhất.", "Share of the whole chip (${tt.soNhan} cores) over the last few minutes.")
            else t("Phần trăm theo một nhân, trong vài phút gần nhất.", "Percent of one core over the last few minutes."))
    }

    // Chạy liên tục lâu: khởi động lại mới là làm mới triệt để (dọn sạch bộ nhớ, dịch vụ chạy lại từ đầu).
    if (chayLau) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NenCanhBao).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.RestartAlt, null, tint = Cam); Spacer(Modifier.width(8.dp))
            Text(t("Tivi chưa khởi động lại ${thoiGian(tt!!.chayGiay!!)}", "No restart for ${thoiGian(tt!!.chayGiay!!)}"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Phu(t("Tắt bằng remote chỉ là chế độ chờ, tivi vẫn chạy. Khởi động lại là cách làm mới triệt để nhất: dọn sạch bộ nhớ, mất 1–3 phút, app tự nối lại.",
            "Turning off with the remote is only standby; the TV keeps running. A restart is the most thorough refresh: memory is cleared, it takes 1–3 minutes and the app reconnects."), ChuChinh.copy(alpha = 0.85f))
        NutPhu(t("Khởi động lại tivi", "Restart the TV"), Icons.Rounded.RestartAlt, enabled = !vm.dangBan) { hoiKhoiDong = true }
    }

    NutChinh(if (vm.dangBan) t("Đang làm mới…", "Refreshing…") else t("Làm mới ngay", "Refresh now"), enabled = !vm.dangBan, icon = Icons.Rounded.Bolt) { lm.lamMoi() }

    lm.ketQua?.let { kq ->
        The(t("Kết quả", "Result"), Icons.Rounded.CheckCircle) {
            val (a, b) = kq.ramTruoc to kq.ramSau
            DongKetQua(when {
                a == null || b == null -> t("Đã đóng các app nằm chờ trong bộ nhớ.", "Closed the apps waiting in memory.")
                b - a >= 20_480 -> t("Đã đóng app nằm chờ: RAM trống ${kb(a)} → ${kb(b)} (thêm ${kb(b - a)}).", "Closed waiting apps: free RAM ${kb(a)} → ${kb(b)} (${kb(b - a)} more).")
                else -> t("Không có nhiều app nằm chờ: RAM trống ${kb(b)}.", "Few apps were waiting in memory: ${kb(b)} RAM free.")
            })
            DongKetQua(when {
                kq.boNhoCon == null -> t("Không đọc được bộ nhớ, bỏ qua xoá cache.", "Couldn't read storage, cache left alone.")
                !kq.ganDay -> t("Bộ nhớ còn ${kb(kq.boNhoCon)}: chưa cần xoá cache.", "${kb(kq.boNhoCon)} of storage free: no need to clear caches.")
                else -> t("Bộ nhớ gần đầy nên đã xoá cache: trống thêm ${kb(kq.giaiPhong ?: 0)}, còn ${kb(kq.boNhoCon)}.",
                    "Storage was nearly full, so caches were cleared: ${kb(kq.giaiPhong ?: 0)} freed, ${kb(kq.boNhoCon)} free now.")
            })
            // Xoá cache rồi vẫn thiếu chỗ: app cài thêm / dữ liệu app mới là thứ chiếm.
            val bn = lm.tinhTrang?.boNho
            if (kq.ganDay && bn != null && LamMoi.boNhoGanDay(bn.tongKb, bn.conKb)) {
                Phu(t("Vẫn còn ít chỗ: xem app nào chiếm nhiều để gỡ hoặc xoá dữ liệu.", "Still short on space: see which apps take the most and remove them or clear their data."), Cam)
                NutPhu(t("Mở Quản lý app", "Open Manage apps"), Icons.Rounded.Apps, enabled = !vm.dangBan) { vm.qlApp.mo() }
            }
        }
    }

    Phu(t("Đóng app nằm chờ không đụng app đang mở, Home sạch, Hẹn giờ, Khoá trẻ em; app vừa đóng mở lại lần đầu chậm hơn chút. Cache giúp app mở nhanh nên chỉ xoá khi bộ nhớ trống dưới 1 GB hoặc dưới 15%; muốn xoá dù còn nhiều chỗ thì vào Quản lý app.",
        "Closing waiting apps leaves the open app, the clean Home, Timer and Kids lock alone; a closed app opens a little slower the first time. Caches make apps faster, so they're only cleared below 1 GB or 15% free; to clear them anyway, use Manage apps."))

    if (hoiKhoiDong) HoiKhoiDongLai(vm, dong = { hoiKhoiDong = false }) { vm.lamMoi.mo() }
}

/** Một chỉ số: tên bên trái, giá trị bên phải (cam khi đáng lo), thanh dùng / tổng nếu có. */
@Composable
private fun ChiSo(ten: String, giaTri: String, tiLe: Float?, canhBao: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ten, fontSize = 15.sp, color = ChuPhu, modifier = Modifier.weight(1f))
            Text(giaTri, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (canhBao) Cam else ChuChinh)
        }
        if (tiLe != null) LinearProgressIndicator(progress = { tiLe.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (canhBao) Cam else Nhan, trackColor = NenO, gapSize = 0.dp, drawStopIndicator = {})
    }
}

/** Một tiến trình ăn CPU: tên dễ hiểu, tên thật bên dưới, phần trăm (trên cả chip nếu biết số nhân). */
@Composable
private fun DongCpu(p: LamMoi.TienTrinh, soNhan: Int?) {
    val pt = (if (soNhan != null) p.phanTram / soNhan else p.phanTram).roundToInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tenTienTrinh(p.ten), fontSize = 15.sp)
            if (LamMoi.nhom(p.ten) != LamMoi.Nhom.APP) Text(p.ten, fontSize = 12.sp, color = ChuPhu)
        }
        Text("$pt%", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (pt >= 25) Cam else ChuChinh)
    }
}

private fun tenTienTrinh(ten: String): String = when (LamMoi.nhom(ten)) {
    LamMoi.Nhom.CAP_NHAT -> when {
        ten.startsWith("dex2oat") -> t("Tối ưu app vừa cài / cập nhật", "Optimising newly installed apps")
        ten == "installd" -> t("Cài app", "Installing apps")
        else -> t("CH Play (tải, cập nhật app)", "Play Store (downloads, updates)")
    }
    LamMoi.Nhom.GOOGLE -> t("Dịch vụ Google Play", "Google Play services")
    LamMoi.Nhom.MAN_HINH -> t("Vẽ màn hình", "Drawing the screen")
    LamMoi.Nhom.VIDEO -> t("Giải mã video", "Video decoding")
    LamMoi.Nhom.AM_THANH -> t("Âm thanh", "Audio")
    LamMoi.Nhom.THIEU_RAM -> t("Dọn RAM (tivi thiếu RAM)", "Reclaiming RAM (the TV is short of RAM)")
    LamMoi.Nhom.HE_THONG -> if (ten == "system_server") t("Hệ thống Android", "Android system") else t("Dịch vụ hệ thống", "System service")
    LamMoi.Nhom.APP -> ten.substringBefore(':')
}

/** Tivi quá tải vì việc gì (nhóm bận nhất) và nên làm gì. */
private fun loiKhuyenQuaTai(tt: TinhTrangTv): String = when (LamMoi.banViec(tt.anCpu)) {
    LamMoi.Nhom.CAP_NHAT -> t("CH Play đang tải, cài và tối ưu app (hay gặp sau khi đăng nhập lại hoặc lâu không cập nhật). Mở video, chuyển app sẽ chậm cho tới khi xong, thường 10–30 phút: để tivi bật, xong thì khởi động lại một lần.",
        "The Play Store is downloading, installing and optimising apps (common after signing in again or a long time without updates). Videos and app switching stay slow until it finishes, usually 10–30 minutes: leave the TV on, then restart it once.")
    LamMoi.Nhom.GOOGLE -> t("Dịch vụ Google Play đang chạy nặng, hay gặp ngay sau khi cập nhật hoặc đăng nhập lại; để vài phút sẽ hạ.",
        "Google Play services is busy, typically right after an update or signing in again; it settles within a few minutes.")
    LamMoi.Nhom.THIEU_RAM -> t("Tivi thiếu RAM nên phải dọn bộ nhớ liên tục. Bấm Làm mới ngay để đóng app nằm chờ; app chạy ngầm không dùng tới thì tắt trong Quản lý app.",
        "The TV is short of RAM and keeps reclaiming memory. Press Refresh now to close waiting apps, and disable background apps you don't use under Manage apps.")
    LamMoi.Nhom.APP -> (tt.anCpu.firstOrNull { LamMoi.nhom(it.ten) == LamMoi.Nhom.APP }?.ten?.substringBefore(':') ?: "").let { app ->
        t("App $app đang dùng nhiều CPU. Nếu không mở app này, buộc dừng nó trong Quản lý app.",
            "The app $app is using a lot of CPU. If you aren't using it, force-stop it under Manage apps.")
    }
    LamMoi.Nhom.MAN_HINH, LamMoi.Nhom.VIDEO, LamMoi.Nhom.AM_THANH -> t("Tivi đang bận vẽ màn hình, phát video: bình thường lúc đang xem, nhưng tải cao thế này thì nên bấm Làm mới ngay để đóng bớt app nằm chờ.",
        "The TV is busy drawing the screen and playing video: normal while watching, but with this much load press Refresh now to close waiting apps.")
    else -> t("Hệ thống Android đang bận. Bấm Làm mới ngay; vẫn chậm thì khởi động lại tivi.", "Android itself is busy. Press Refresh now; if it stays slow, restart the TV.")
}

private fun so1(x: Double) = String.format(Locale.US, "%.1f", x)

@Composable
private fun DongKetQua(chu: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp).padding(top = 1.dp), tint = Xanh); Spacer(Modifier.width(8.dp))
        Text(chu, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** KB (df, meminfo: khối 1024 byte) → "1.2 GB" / "620.0 MB". */
private fun kb(kb: Long): String = coChu(kb * 1024)

private fun thoiGian(giay: Long): String {
    val (ngay, gio, phut) = LamMoi.tach(giay)
    return when {
        ngay > 0 -> t("$ngay ngày $gio giờ", "$ngay d $gio h")
        gio > 0 -> t("$gio giờ $phut phút", "$gio h $phut min")
        else -> t("$phut phút", "$phut min")
    }
}
