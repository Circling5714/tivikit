package vn.tivikit.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.ManHinh
import vn.tivikit.R

/**
 * Giao diện kiểu Apple (25/10/2026, cùng lúc đổi tên TiviKit): màu hệ thống của iOS, sáng / tối theo điện thoại (chạy
 * ngay trên tivi thì luôn tối, như tvOS), chữ Inter (gần SF Pro nhất; SF Pro không được dùng ngoài máy Apple), thẻ bo
 * góc không viền trên nền xám như màn Cài đặt của iPhone, công tắc xanh lá, nút đặc màu nhấn. Tên màu đọc theo chế độ
 * sáng / tối nên chỉ dùng được trong hàm @Composable (trong drawBehind… thì lấy ra biến trước).
 */
@Immutable
internal data class BangMau(
    val nen: Color, val the: Color, val the2: Color, val chu: Color, val chuPhu: Color, val vien: Color, val fill: Color,
    val doanChon: Color, val nhan: Color, val doTuoi: Color, val xanhLa: Color, val congTac: Color, val cam: Color,
    val cham: Color, val tim: Color, val hong: Color, val ngoc: Color, val vang: Color, val nenCanhBao: Color, val nenLoi: Color,
    val toi: Boolean,
)

/**
 * iOS sáng: systemGroupedBackground, secondarySystemGroupedBackground, label, secondaryLabel, separator,
 * secondarySystemFill, system colors.
 */
internal val MAU_SANG = BangMau(
    nen = Color(0xFFF2F2F7), the = Color(0xFFFFFFFF), the2 = Color(0xFFF2F2F7), chu = Color(0xFF000000), chuPhu = Color(0xFF8A8A8E),
    vien = Color(0xFFC6C6C8), fill = Color(0x29787880), doanChon = Color(0xFFFFFFFF), nhan = Color(0xFF007AFF), doTuoi = Color(0xFFFF3B30),
    // Xanh lá / cam để viết chữ: bản tương phản cao của iOS (bản thường quá nhạt trên nền trắng).
    xanhLa = Color(0xFF248A3D), congTac = Color(0xFF34C759), cam = Color(0xFFC93400),
    cham = Color(0xFF5856D6), tim = Color(0xFFAF52DE), hong = Color(0xFFFF2D55), ngoc = Color(0xFF30B0C7), vang = Color(0xFFFF9500),
    nenCanhBao = Color(0xFFFFF4E5), nenLoi = Color(0xFFFFEBEE), toi = false,
)

/** iOS tối. */
internal val MAU_TOI = BangMau(
    nen = Color(0xFF000000), the = Color(0xFF1C1C1E), the2 = Color(0xFF2C2C2E), chu = Color(0xFFFFFFFF), chuPhu = Color(0xFF98989F),
    vien = Color(0xFF38383A), fill = Color(0x52787880), doanChon = Color(0xFF636366), nhan = Color(0xFF0A84FF), doTuoi = Color(0xFFFF453A),
    xanhLa = Color(0xFF30D158), congTac = Color(0xFF30D158), cam = Color(0xFFFF9F0A),
    cham = Color(0xFF5E5CE6), tim = Color(0xFFBF5AF2), hong = Color(0xFFFF375F), ngoc = Color(0xFF40C8E0), vang = Color(0xFFFF9F0A),
    nenCanhBao = Color(0xFF3A2A10), nenLoi = Color(0xFF3A1320), toi = true,
)

internal val LocalMau = staticCompositionLocalOf { MAU_TOI }

// Tên màu dùng khắp các màn.
internal val Nen: Color @Composable @ReadOnlyComposable get() = LocalMau.current.nen
/** Nền thẻ (trắng / xám than). */
internal val The1: Color @Composable @ReadOnlyComposable get() = LocalMau.current.the
/** Nền ô lồng trong thẻ; trùng màu nền trang ở chế độ sáng nên đừng đặt thẳng lên trang. */
internal val The2: Color @Composable @ReadOnlyComposable get() = LocalMau.current.the2
internal val ChuChinh: Color @Composable @ReadOnlyComposable get() = LocalMau.current.chu
internal val ChuPhu: Color @Composable @ReadOnlyComposable get() = LocalMau.current.chuPhu
internal val Vien: Color @Composable @ReadOnlyComposable get() = LocalMau.current.vien
/** Nền xám trong suốt (nút phụ tắt, chip, rãnh tiến độ): thấy được cả trên nền trang lẫn trong thẻ. */
internal val NenO: Color @Composable @ReadOnlyComposable get() = LocalMau.current.fill
/** Màu nhấn: xanh dương hệ thống của Apple. */
internal val Nhan: Color @Composable @ReadOnlyComposable get() = LocalMau.current.nhan
/** Đỏ hệ thống: lỗi, tắt tivi. */
internal val DoTuoi: Color @Composable @ReadOnlyComposable get() = LocalMau.current.doTuoi
internal val Xanh: Color @Composable @ReadOnlyComposable get() = LocalMau.current.xanhLa
internal val Cam: Color @Composable @ReadOnlyComposable get() = LocalMau.current.cam
internal val NenCanhBao: Color @Composable @ReadOnlyComposable get() = LocalMau.current.nenCanhBao
internal val NenLoi: Color @Composable @ReadOnlyComposable get() = LocalMau.current.nenLoi
/** Nền nổi bật (logo, thẻ Dọn quảng cáo): xanh dương → chàm như biểu tượng app. */
internal val Gradient: Brush @Composable @ReadOnlyComposable get() = Brush.linearGradient(listOf(LocalMau.current.nhan, LocalMau.current.cham))
/** Ô biểu tượng xám (như biểu tượng Cài đặt chung của iPhone). */
internal val NenXam: Brush get() = SolidColor(Color(0xFF8E8E93))

/** Màu ô biểu tượng ở màn chính, như ô biểu tượng màu của màn Cài đặt trên iPhone. */
internal object MauO {
    val xanh: Color @Composable @ReadOnlyComposable get() = LocalMau.current.nhan
    val cham: Color @Composable @ReadOnlyComposable get() = LocalMau.current.cham
    val tim: Color @Composable @ReadOnlyComposable get() = LocalMau.current.tim
    val hong: Color @Composable @ReadOnlyComposable get() = LocalMau.current.hong
    val cam: Color @Composable @ReadOnlyComposable get() = LocalMau.current.vang
    val la: Color @Composable @ReadOnlyComposable get() = LocalMau.current.congTac
    val ngoc: Color @Composable @ReadOnlyComposable get() = LocalMau.current.ngoc
    val doTuoi: Color @Composable @ReadOnlyComposable get() = LocalMau.current.doTuoi
    val xam = Color(0xFF8E8E93)
}

internal val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold),
)

/** Tiêu đề lớn đầu màn (Large Title của iOS). */
internal val KieuTieuDeLon = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp)

/** Bộ chữ Material đổi sang Inter, khoảng chữ sát như iOS (Material mặc định giãn 0,25–0,5sp). */
private val KIEU_CHU: Typography = Typography().let { g ->
    fun TextStyle.i(gian: Float = 0f) = copy(fontFamily = Inter, letterSpacing = gian.sp)
    Typography(
        displayLarge = g.displayLarge.i(-0.5f), displayMedium = g.displayMedium.i(-0.5f), displaySmall = g.displaySmall.i(-0.4f),
        headlineLarge = g.headlineLarge.i(-0.4f), headlineMedium = g.headlineMedium.i(-0.3f), headlineSmall = g.headlineSmall.i(-0.2f),
        titleLarge = g.titleLarge.i(-0.2f), titleMedium = g.titleMedium.i(-0.1f), titleSmall = g.titleSmall.i(),
        bodyLarge = g.bodyLarge.i(-0.1f), bodyMedium = g.bodyMedium.i(), bodySmall = g.bodySmall.i(),
        labelLarge = g.labelLarge.i(), labelMedium = g.labelMedium.i(), labelSmall = g.labelSmall.i(),
    )
}

/** Bọc toàn app: chọn bảng màu sáng / tối, đưa vào MaterialTheme (ô nhập, hộp thoại, nút chữ dùng theo). */
@Composable
internal fun ChuDe(noiDung: @Composable () -> Unit) {
    val m = if (ManHinh.laTv || isSystemInDarkTheme()) MAU_TOI else MAU_SANG
    val trang = Color.White
    val sc = if (m.toi) darkColorScheme(
        primary = m.nhan, onPrimary = trang, secondary = m.cham, onSecondary = trang, background = m.nen, onBackground = m.chu,
        surface = m.the, onSurface = m.chu, surfaceVariant = m.the2, onSurfaceVariant = m.chuPhu, outline = m.vien, outlineVariant = m.vien,
        error = m.doTuoi, surfaceContainerHigh = m.the, surfaceContainerHighest = m.the2, surfaceContainer = m.the, surfaceContainerLow = m.the,
    ) else lightColorScheme(
        primary = m.nhan, onPrimary = trang, secondary = m.cham, onSecondary = trang, background = m.nen, onBackground = m.chu,
        surface = m.the, onSurface = m.chu, surfaceVariant = m.the2, onSurfaceVariant = m.chuPhu, outline = m.vien, outlineVariant = m.vien,
        error = m.doTuoi, surfaceContainerHigh = m.the, surfaceContainerHighest = m.the2, surfaceContainer = m.the, surfaceContainerLow = m.the,
    )
    CompositionLocalProvider(LocalMau provides m) { MaterialTheme(colorScheme = sc, typography = KIEU_CHU, content = noiDung) }
}

/** Tiêu đề lớn đầu màn kèm dòng mô tả. */
@Composable
internal fun TieuDeLon(chu: String, moTa: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(chu, style = KieuTieuDeLon)
        if (moTa != null) Phu(moTa)
    }
}

/**
 * Công tắc kiểu iOS: rãnh xanh lá khi bật, núm trắng có bóng, cùng cỡ hai trạng thái. [doi] = null khi cả hàng chứa nó
 * đã bấm được (Hang), để remote tivi không dừng hai lần trên một dòng.
 */
@Composable
internal fun CongTac(bat: Boolean, doi: ((Boolean) -> Unit)?) {
    val ranh by animateColorAsState(if (bat) LocalMau.current.congTac else NenO, label = "ranh")
    val x by animateDpAsState(if (bat) 22.dp else 2.dp, label = "num")
    val bam = if (doi != null) Modifier.toggleable(bat, role = Role.Switch, onValueChange = doi) else Modifier
    Box(Modifier.size(width = 51.dp, height = 31.dp).clip(CircleShape).then(bam).background(ranh)) {
        Box(Modifier.align(Alignment.CenterStart).offset(x = x).size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** Ô tích tròn kiểu iOS (chọn nhiều): đặc màu nhấn có dấu ✓ khi chọn, vòng xám khi chưa. Chỉ để nhìn, hàng chứa nó lo việc bấm. */
@Composable
internal fun OTich(chon: Boolean) {
    if (chon) Box(Modifier.size(24.dp).clip(CircleShape).background(Nhan), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color.White)
    } else Box(Modifier.size(24.dp).border(1.5.dp, ChuPhu.copy(alpha = 0.55f), CircleShape))
}
