package vn.tivikit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.tivikit.dulieu.CaiDatNhanh
import vn.tivikit.t

/** Màn Cài đặt nhanh (25/10/2026): hiệu ứng, tự tắt, màn hình chờ, tiếng bấm remote. */
@Composable
internal fun ManCaiDatNhanh(vm: PhienLamViec) = Cuon {
    val cd = vm.caiDatNhanh
    val gt = cd.giaTri.orEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Cài đặt nhanh", "Quick settings"), style = KieuTieuDeLon)
        Phu(t("Vài cài đặt hay cần chỉnh mà menu tivi giấu sâu hoặc không có. Bấm là có tác dụng ngay; \"Mặc định\" trả về như hãng đặt.",
            "A few settings people often need that the TV menu hides or lacks. A tap takes effect right away; \"Default\" goes back to the maker's value."))
    }

    The(t("Hiệu ứng chuyển màn", "Animation speed"), Icons.Rounded.Animation) {
        val h = CaiDatNhanh.hieuUng(gt)
        Hang3(listOf(CaiDatNhanh.HieuUng.MAC_DINH to t("Mặc định", "Default"), CaiDatNhanh.HieuUng.NHANH to t("Nhanh 0,5×", "Fast 0.5×"),
            CaiDatNhanh.HieuUng.TAT to t("Tắt", "Off")), h, vm.dangBan) { cd.datHieuUng(it) }
        if (h == CaiDatNhanh.HieuUng.KHAC) Phu(t("Tivi đang dùng mức tự chỉnh khác.", "The TV uses a custom value."))
        Phu(t("Tivi chậm: chọn Nhanh là mở menu, chuyển app thấy mượt hơn hẳn. Tắt là nhanh nhất nhưng vài app trông giật cục.",
            "Slow TV: Fast makes menus and app switching feel much snappier. Off is fastest but some apps look jumpy."))
    }

    if ((vm.thongTin?.sdk ?: 0) >= 29) The(t("Tự tắt khi không bấm remote", "Turn off when idle"), Icons.Rounded.PowerSettingsNew) {
        Hang3(CaiDatNhanh.TU_TAT_CHON.map { it to tenThoiGian(it, t("Không tự tắt", "Never")) },
            CaiDatNhanh.dangChon(gt[CaiDatNhanh.TU_TAT], CaiDatNhanh.TU_TAT_CHON), vm.dangBan) { cd.datTuTat(it) }
        Phu(t("Google TV tự tắt sau 4 giờ không bấm remote để tiết kiệm điện: xem phim dài, mở nhạc, camera dễ bị tắt ngang. Vài hãng có cài đặt riêng ghi đè mục này.",
            "Google TV turns off after 4 hours without a remote press to save power, cutting off long films, music or cameras. Some makers have their own setting that overrides this."))
    }

    The(t("Màn hình chờ", "Screensaver"), Icons.Rounded.Wallpaper) {
        Hang3(CaiDatNhanh.MAN_CHO_CHON.map { it to tenThoiGian(it, "") },
            CaiDatNhanh.dangChon(gt[CaiDatNhanh.MAN_CHO], CaiDatNhanh.MAN_CHO_CHON), vm.dangBan) { cd.datManCho(it) }
        Phu(t("Không bấm remote bao lâu thì hiện ảnh / đồng hồ của màn hình chờ.", "How long without a remote press before the photo / clock screensaver starts."))
    }

    The(t("Âm thanh", "Sound"), Icons.AutoMirrored.Rounded.VolumeUp) {
        Hang(t("Tiếng bấm remote", "Remote click sound"), t("Tiếng \"tít\" mỗi lần bấm nút trên remote", "The click on every remote press"),
            gt[CaiDatNhanh.TIENG_BAM] != "0") { if (!vm.dangBan) cd.datTiengBam(it) }
    }

    if (cd.bao.isNotEmpty()) Phu(cd.bao, Xanh)
    Phu(t("Khôi phục tivi không đổi lại các mục này; muốn trả như cũ thì chọn Mặc định.",
        "Restoring the TV doesn't touch these; choose Default to put them back."))
}

/** Các viên chọn, hết chỗ thì xuống dòng (thấy đủ mọi lựa chọn), viên [dang] sáng. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Hang3(ds: List<Pair<T, String>>, dang: T?, ban: Boolean, chon: (T) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ds.forEach { (v, ten) -> ChonChip(ten, v == dang) { if (!ban && v != dang) chon(v) } }
    }
}

/** null = "Mặc định", số âm = [khong], còn lại "15 phút" / "4 giờ". */
private fun tenThoiGian(ms: Long?, khong: String): String = when {
    ms == null -> t("Mặc định", "Default")
    ms <= 0 -> khong
    ms >= CaiDatNhanh.GIO -> t("${ms / CaiDatNhanh.GIO} giờ", "${ms / CaiDatNhanh.GIO} h")
    else -> t("${ms / CaiDatNhanh.PHUT} phút", "${ms / CaiDatNhanh.PHUT} min")
}
