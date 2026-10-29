package vn.tivikit.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/*
 * Các lớp kính đè lên màn Home (28/10/2026): bảng lịch âm dương (OK ở đồng hồ), thẻ và cả câu kinh mỗi ngày, menu ⏻.
 * Bảng lịch và cả câu: nền tối dần, tấm kính mờ bật ra bằng lò xo; menu ⏻: nút ⏻ nở thành viên kính ngay tại chỗ. Back
 * để đóng (lựa chọn về lại ô đã mở nó).
 */

/** Đỏ / cam / xanh của iOS cho biểu tượng menu ⏻. */
private val DoIos = Color(0xFFFF453A)
private val CamIos = Color(0xFFFF9F0A)
private val XanhIos = Color(0xFF0A84FF)
private val NGAY_DAU = LocalDate.of(1901, 1, 1)
private val NGAY_CUOI = LocalDate.of(2199, 12, 31)

/** Lớp đè kiểu iOS: nền tối dần, khung kính bật ra bằng lò xo từ [goc] (giữa màn hình, hay góc của nút vừa bấm). */
@Composable
private fun LopPhu(toi: Float, canh: Alignment = Alignment.Center, goc: TransformOrigin = TransformOrigin.Center,
                   modifier: Modifier = Modifier, noiDung: @Composable () -> Unit) {
    // Ảnh xem trước (Paparazzi) chụp khung hình đầu: hiện sẵn, không chạy hiệu ứng.
    val xemTruoc = LocalInspectionMode.current
    val hien = remember { Animatable(if (xemTruoc) 1f else 0f) }
    LaunchedEffect(Unit) { hien.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 480f)) }
    Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = toi * hien.value.coerceIn(0f, 1f))) }, contentAlignment = canh) {
        Box(modifier.graphicsLayer {
            val v = hien.value
            alpha = v.coerceIn(0f, 1f)
            val s = 0.9f + 0.1f * v
            scaleX = s; scaleY = s; transformOrigin = goc
        }) { noiDung() }
    }
}

// ---------- Menu ⏻ ----------

private class MucNguon(val hinh: ImageVector, val mau: Color, val ten: String, val lam: () -> Unit)

/** Cỡ trong viên kính ⏻ (dp): nút bằng nút tròn hàng trên (NutTron), khoảng cách giữa các nút, lề trong. */
private const val O_NGUON = 40
private const val GIAN_NGUON = 8
private const val LE_NGUON = 5

/**
 * Menu ⏻ kiểu thanh công cụ Liquid Glass của macOS Tahoe (anh chọn mẫu "viên kính nở ra", 28/10/2026): bấm ⏻ thì hàng nút
 * góc phải mờ đi (GiaoDien.HangNut), nút ⏻ nở sang trái thành viên kính [khoá trẻ em ngay] [khởi động lại] [tắt tivi], ⏻
 * vẫn ở đúng chỗ cũ. Nút đang chọn là giọt kính sáng, tên nó hiện trong bong bóng kính nhỏ bên dưới. ◀ ▶ để chọn, Back
 * thu viên kính lại; không làm tối màn hình. Mục khoá trẻ em chỉ có khi tivi có app Khoá trẻ em.
 */
@Composable
internal fun MenuNguon(tt: TrangThaiHome) {
    val muc = buildList {
        if (tt.coKhoa) add(MucNguon(Icons.Rounded.Lock, XanhIos, t("Khoá trẻ em ngay", "Kids lock now")) { tt.hd.khoaTreEm() })
        add(MucNguon(Icons.Rounded.RestartAlt, CamIos, t("Khởi động lại", "Restart")) { tt.hd.khoiDongLai() })
        add(MucNguon(Icons.Rounded.PowerSettingsNew, DoIos, t("Tắt tivi", "Turn off")) { tt.hd.tatTivi() })
    }
    val n = muc.size
    // Ảnh xem trước (Paparazzi) chụp khung hình đầu: nở sẵn.
    val xemTruoc = LocalInspectionMode.current
    val mo = remember { Animatable(if (xemTruoc) 1f else 0f) }
    var dong by remember { mutableStateOf(false) }
    var dangChon by remember { mutableIntStateOf(n - 1) }
    val dau = remember { FocusRequester() }
    BackHandler { dong = true }
    LaunchedEffect(dong) {
        if (!dong) {
            runCatching { dau.requestFocus() }
            mo.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f))
        } else {
            mo.animateTo(0f, tween(150))
            tt.menuNguon = false
            tt.traChon = TrangThaiHome.NGUON
        }
    }
    val day = n * O_NGUON + (n - 1) * GIAN_NGUON + 2 * LE_NGUON
    val gon = O_NGUON + 2 * LE_NGUON
    val vien = RoundedCornerShape(50)
    Box(Modifier.fillMaxSize()) {
        // Mép phải viên kính giữ đúng chỗ nút ⏻ (HangNut: top 34, end 46, nút 40 dp), bề ngang nở dần sang trái.
        Box(Modifier.align(Alignment.TopEnd).padding(top = (34 - LE_NGUON).dp, end = (46 - LE_NGUON).dp)
            .width((gon + (day - gon) * mo.value).coerceAtLeast(gon.toFloat()).dp).height(gon.dp)
            .kinh(LocalNen.current, vien, toi = 0.20f, sang = 0.12f, vien = 0.65f).anhKinh(vien, 0.22f).clip(vien)) {
            Row(Modifier.align(Alignment.CenterEnd).wrapContentWidth(Alignment.End, unbounded = true).padding(horizontal = LE_NGUON.dp),
                horizontalArrangement = Arrangement.spacedBy(GIAN_NGUON.dp)) {
                muc.forEachIndexed { i, m ->
                    val la = i == n - 1
                    GiotNguon(m, if (la) dau else null, Modifier.graphicsLayer { alpha = if (la) 1f else mo.value.coerceIn(0f, 1f) }) { if (it) dangChon = i }
                }
            }
        }
        // Bong bóng tên dưới nút đang chọn, trượt theo khi đổi nút.
        val giua = LE_NGUON + dangChon.coerceIn(0, n - 1) * (O_NGUON + GIAN_NGUON) + O_NGUON / 2
        val x by animateDpAsState((giua - 70).dp, tween(160), label = "bong-ten")
        Box(Modifier.align(Alignment.TopEnd).padding(top = (34 + O_NGUON + LE_NGUON + 9).dp, end = (46 - LE_NGUON).dp).width(day.dp)
            .graphicsLayer { alpha = mo.value.coerceIn(0f, 1f) }) {
            // Ô tên 140 dp rộng hơn viên kính 2 nút (98 dp, tivi không có Khoá trẻ em): không bó theo, kẻo tên bị cắt (TX3).
            Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset(x = x).width(140.dp), contentAlignment = Alignment.Center) {
                val bong = RoundedCornerShape(50)
                Box(Modifier.kinh(LocalNen.current, bong, toi = 0.30f, sang = 0.10f, vien = 0.5f).padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Nhan(muc[dangChon.coerceIn(0, n - 1)].ten, 12.sp, FontWeight.Medium, Color.White, bong = false)
                }
            }
        }
    }
}

/** Một nút trong viên kính ⏻: biểu tượng trắng; đang chọn thì thành giọt kính sáng, biểu tượng màu, to ra một chút. */
@Composable
private fun GiotNguon(m: MucNguon, dau: FocusRequester?, modifier: Modifier, onChon: (Boolean) -> Unit) {
    val fr = dau ?: remember { FocusRequester() }
    var chon by remember { mutableStateOf(false) }
    val to by animateFloatAsState(if (chon) 1.08f else 1f, tween(140), label = "giot")
    Box(modifier.focusRequester(fr)
        .nutTv(m.lam, onChon = { chon = it; onChon(it) })
        .graphicsLayer { scaleX = to; scaleY = to }.size(O_NGUON.dp)
        .then(if (chon) Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.34f))
            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape).anhKinh(CircleShape, 0.35f) else Modifier),
        contentAlignment = Alignment.Center) {
        Hinh(m.hinh, 19, if (chon) m.mau else Color.White.copy(alpha = 0.85f))
    }
}

// ---------- Câu kinh mỗi ngày ----------

/** "Kinh Pháp Cú", tên bộ riêng TiviKit gửi, hay "Câu mỗi ngày" nếu bộ riêng không tên. */
private fun tenBo(c: CauHomNay): String = when {
    c.ten == null -> t("Kinh Pháp Cú", "Dhammapada")
    c.ten.isBlank() -> t("Câu mỗi ngày", "Daily verse")
    else -> c.ten
}

/** "Kinh Pháp Cú · câu 21–22" / "Tên bộ · 12". */
private fun tieuDeCau(c: CauHomNay): String = tenBo(c) + " · " + (if (c.ten == null) t("câu ", "verse ") else "") + c.cau.so

/** Thẻ câu kinh hôm nay dưới đồng hồ: hai dòng đầu, OK để đọc cả câu. */
@Composable
internal fun TheCau(tt: TrangThaiHome, c: CauHomNay) {
    val fr = remember { FocusRequester() }
    var chon by remember { mutableStateOf(false) }
    val to by animateFloatAsState(if (chon) 1.03f else 1f, tween(160), label = "the-cau")
    val khoa = LocalKhoaChon.current
    LaunchedEffect(tt.traChon, khoa) { if (tt.traChon == TrangThaiHome.CAU && !khoa) { runCatching { fr.requestFocus() }; tt.traChon = null } }
    val vien = RoundedCornerShape(20.dp)
    val cac = remember(c) { c.cau.dong.map(CauMoiNgay::boDauSao) }
    Column(Modifier.focusRequester(fr)
        .nutTv({ tt.xemCau = true }, onChon = { chon = it })
        .graphicsLayer { scaleX = to; scaleY = to; transformOrigin = TransformOrigin(0f, 0f) }
        .kinh(LocalNen.current, vien, toi = if (chon) 0.04f else 0.16f, sang = if (chon) 0.20f else 0.08f)
        .then(if (chon) Modifier.border(2.dp, XanhChon, vien) else Modifier)
        .widthIn(min = 260.dp, max = 430.dp).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hinh(Icons.Rounded.Spa, 14, Vang)
            Spacer(Modifier.width(7.dp))
            Nhan(tieuDeCau(c).uppercase(), 11.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.74f), gian = 1.2.sp)
        }
        Spacer(Modifier.height(5.dp))
        if (cac.size == 1) Nhan(cac[0], 14.sp, FontWeight.Medium, Color.White, dong = 3)
        else cac.take(2).forEachIndexed { i, s -> Nhan(if (i == 1 && cac.size > 2) "$s …" else s, 14.5.sp, FontWeight.Medium, Color.White) }
    }
}

/** Cả câu kinh (OK ở thẻ câu): chữ to ở giữa, lời chú bên dưới; câu dài thì ▲ ▼ để cuộn. OK hay Back để đóng. */
@Composable
internal fun XemCau(tt: TrangThaiHome, c: CauHomNay) {
    val fr = remember { FocusRequester() }
    val cuon = rememberScrollState()
    val scope = rememberCoroutineScope()
    val buoc = with(LocalDensity.current) { 90.dp.toPx() }
    val dong = { tt.xemCau = false; tt.traChon = TrangThaiHome.CAU }
    BackHandler { dong() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    val n = c.cau.dong.size
    val co = when { n <= 6 -> 20.sp; n <= 8 -> 18.sp; n <= 12 -> 16.sp; else -> 14.sp }
    LopPhu(toi = 0.45f) {
        Column(Modifier.widthIn(min = 420.dp, max = 620.dp).heightIn(max = 480.dp)
            .kinh(LocalNen.current, RoundedCornerShape(30.dp), toi = 0.36f, sang = 0.08f)
            .focusRequester(fr)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key in PHIM_OK
                when (e.key) {
                    Key.DirectionDown -> { scope.launch { cuon.animateScrollBy(buoc) }; true }
                    Key.DirectionUp -> { scope.launch { cuon.animateScrollBy(-buoc) }; true }
                    Key.DirectionLeft, Key.DirectionRight -> true
                    in PHIM_OK -> { dong(); true }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 36.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hinh(Icons.Rounded.Spa, 16, Vang)
                Spacer(Modifier.width(8.dp))
                Nhan(tieuDeCau(c).uppercase(), 12.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.74f), gian = 1.4.sp, bong = false)
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(cuon), horizontalAlignment = Alignment.CenterHorizontally) {
                val kieu = TextStyle(color = Color.White, fontSize = co, fontWeight = FontWeight.Normal, fontFamily = Inter,
                    lineHeight = co * 1.42f, textAlign = TextAlign.Center)
                c.cau.dong.forEach { BasicText(it, style = kieu) }
                if (c.cau.chuThich.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.width(120.dp).height(1.dp).background(Color.White.copy(alpha = 0.25f)))
                    Spacer(Modifier.height(10.dp))
                    val kieuChu = TextStyle(color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp, fontFamily = Inter, lineHeight = 17.sp)
                    c.cau.chuThich.forEach { BasicText(it, style = kieuChu) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Nhan(if (cuon.maxValue > 0) t("▲ ▼ để cuộn · OK hoặc Back để đóng", "▲ ▼ to scroll · OK or Back to close")
                else t("OK hoặc Back để đóng", "OK or Back to close"), 11.5.sp, FontWeight.Medium, Color.White.copy(alpha = 0.5f), bong = false)
        }
    }
}

// ---------- Bảng lịch ----------

/**
 * Bảng lịch (OK ở đồng hồ): lịch tháng bên trái (ngày dương, ngày âm, chấm vàng ngày hoàng đạo, ngày lễ / Chủ Nhật màu
 * đỏ nhạt, hôm nay khoanh xanh), chi tiết ngày đang chọn bên phải. ◀ ▶ ▲ ▼ chọn ngày (qua tháng tự đổi), CH+ / CH− đổi
 * tháng, OK về hôm nay, Back để đóng.
 */
@Composable
internal fun BangLich(tt: TrangThaiHome) {
    val viet = NgonNgu.tiengViet
    val homNay = tt.bayGio.toLocalDate()
    var chon by remember { mutableStateOf(homNay) }
    val thang = YearMonth.from(chon)
    val o = remember(thang) { LichNgay.oThang(thang).map(LichNgay::tinh) }
    val ct = remember(chon) { LichNgay.tinh(chon) }
    val fr = remember { FocusRequester() }
    BackHandler { tt.lich = false; tt.traChon = TrangThaiHome.GIO }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    fun doi(d: LocalDate) { chon = if (d < NGAY_DAU) NGAY_DAU else if (d > NGAY_CUOI) NGAY_CUOI else d }
    LopPhu(toi = 0.45f) {
        Column(Modifier.width(884.dp)
            .kinh(LocalNen.current, RoundedCornerShape(30.dp), toi = 0.38f, sang = 0.08f)
            .focusRequester(fr)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key in PHIM_OK
                when (e.key) {
                    Key.DirectionLeft -> doi(chon.minusDays(1))
                    Key.DirectionRight -> doi(chon.plusDays(1))
                    Key.DirectionUp -> doi(chon.minusWeeks(1))
                    Key.DirectionDown -> doi(chon.plusWeeks(1))
                    Key.ChannelUp, Key.PageDown, Key.MediaFastForward, Key.MediaNext -> doi(chon.plusMonths(1))
                    Key.ChannelDown, Key.PageUp, Key.MediaRewind, Key.MediaPrevious -> doi(chon.minusMonths(1))
                    in PHIM_OK -> doi(homNay)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable()
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 16.dp)) {
            Row {
                Column(Modifier.width(484.dp)) {
                    DauThang(thang, o, viet)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val thu = if (viet) listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN") else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        thu.forEachIndexed { i, s ->
                            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                                Nhan(s, 12.sp, FontWeight.SemiBold, if (i == 6) DoNhat.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.55f), bong = false)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    o.chunked(7).forEach { tuan ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tuan.forEach { n -> OLich(n, YearMonth.from(n.duong) == thang, n.duong == homNay, n.duong == chon) }
                        }
                    }
                }
                Spacer(Modifier.width(22.dp))
                Box(Modifier.width(1.dp).height(354.dp).background(Color.White.copy(alpha = 0.16f)))
                Spacer(Modifier.width(22.dp))
                ChiTietNgay(ct, viet)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                val mo = Color.White.copy(alpha = 0.5f)
                Box(Modifier.size(5.dp).background(Vang, CircleShape))
                Spacer(Modifier.width(6.dp))
                Nhan(t("ngày hoàng đạo", "auspicious day"), 11.5.sp, FontWeight.Medium, Vang.copy(alpha = 0.8f), bong = false)
                Nhan(t("  ·  ◀ ▶ ▲ ▼ chọn ngày  ·  CH+ / CH− đổi tháng  ·  OK về hôm nay  ·  Back để đóng",
                    "  ·  ◀ ▶ ▲ ▼ pick a day  ·  CH+ / CH− change month  ·  OK for today  ·  Back to close"), 11.5.sp, FontWeight.Medium, mo, bong = false)
            }
        }
    }
}

/** "Tháng 10, 2026" và các tháng âm trong tháng: "Tháng 8 – tháng 9 năm Bính Ngọ". */
@Composable
private fun DauThang(thang: YearMonth, o: List<LichNgay.Ngay>, viet: Boolean) {
    val trong = o.filter { YearMonth.from(it.duong) == thang }
    val dau = trong.first().am
    val cuoi = trong.last().am
    val am = if (viet) {
        val a = LichNgay.tenThangAm(dau.thang, dau.nhuan)
        val b = LichNgay.tenThangAm(cuoi.thang, cuoi.nhuan)
        (if (dau.nam == cuoi.nam) "$a – $b năm ${AmLich.canChi(cuoi.nam)}" else "$a năm ${AmLich.canChi(dau.nam)} – $b năm ${AmLich.canChi(cuoi.nam)}")
            .replaceFirstChar(Char::uppercase)
    } else "Lunar months ${dau.thang}–${cuoi.thang} · ${AmLich.canChi(cuoi.nam)}"
    Row(verticalAlignment = Alignment.Bottom) {
        Nhan(if (viet) "Tháng ${thang.monthValue}, ${thang.year}" else "${THANG_EN[thang.monthValue - 1]} ${thang.year}",
            24.sp, FontWeight.SemiBold, Color.White, bong = false)
        Spacer(Modifier.width(12.dp))
        Nhan(am, 13.sp, FontWeight.Medium, Vang.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 3.dp), bong = false)
    }
}

private val THANG_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
    "November", "December")

/** Một ô ngày: số dương lịch (hôm nay khoanh xanh), số âm lịch nhỏ (mùng 1 kèm tháng), chấm vàng nếu hoàng đạo. */
@Composable
private fun OLich(n: LichNgay.Ngay, trongThang: Boolean, homNay: Boolean, chon: Boolean) {
    val vien = RoundedCornerShape(14.dp)
    val mauDuong = when {
        !trongThang -> Color.White.copy(alpha = 0.28f)
        n.le.isNotEmpty() || n.duong.dayOfWeek == DayOfWeek.SUNDAY -> DoNhat
        else -> Color.White
    }
    Box(Modifier.size(64.dp, 49.dp)
        .then(if (chon) Modifier.background(Color.White.copy(alpha = 0.20f), vien).border(1.5.dp, Color.White.copy(alpha = 0.8f), vien) else Modifier),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp).then(if (homNay) Modifier.background(XanhIos, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                Nhan("${n.duong.dayOfMonth}", 16.sp, if (homNay || chon) FontWeight.SemiBold else FontWeight.Medium,
                    if (homNay) Color.White else mauDuong, bong = false)
            }
            val am = if (n.am.ngay == 1) "1/${n.am.thang}" else "${n.am.ngay}"
            Nhan(am, 10.5.sp, FontWeight.Medium, when {
                !trongThang -> Color.White.copy(alpha = 0.22f)
                n.soc -> Vang
                else -> Color.White.copy(alpha = 0.55f)
            }, bong = false)
        }
        if (n.hoangDao && trongThang) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp).size(5.dp).background(Vang, CircleShape))
    }
}

/** Chi tiết ngày đang chọn: thứ, ngày âm, can chi, hoàng đạo / hắc đạo, tiết khí, trực, giờ hoàng đạo, lễ, ngày kiêng. */
@Composable
private fun ChiTietNgay(n: LichNgay.Ngay, viet: Boolean) {
    val d = n.duong
    val thu = if (viet) listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")[d.dayOfWeek.value - 1]
        else listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[d.dayOfWeek.value - 1]
    val nhat = Color.White.copy(alpha = 0.55f)
    Column(Modifier.width(290.dp)) {
        Nhan(thu.uppercase(), 13.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.72f), gian = 2.sp, bong = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("${d.dayOfMonth}", style = TextStyle(color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Light,
                fontFamily = Inter, letterSpacing = (-1.5).sp))
            Spacer(Modifier.width(12.dp))
            Nhan(if (viet) "Tháng ${d.monthValue}, ${d.year}" else "${THANG_EN[d.monthValue - 1]} ${d.year}", 15.sp, FontWeight.Medium,
                Color.White.copy(alpha = 0.9f), bong = false)
        }
        val am = n.am
        Nhan(if (viet) "Ngày ${am.ngay} ${LichNgay.tenThangAm(am.thang, am.nhuan)} năm ${AmLich.canChi(am.nam)}"
            else "Lunar ${am.ngay}/${am.thang}${if (am.nhuan) " (leap)" else ""}, ${AmLich.canChi(am.nam)} year",
            16.sp, FontWeight.SemiBold, Vang, bong = false)
        Spacer(Modifier.height(3.dp))
        Nhan(t("Ngày ${LichNgay.canChi(n.canNgay, n.chiNgay)} · tháng ${LichNgay.canChi(n.canThang, n.chiThang)}",
            "Day ${LichNgay.canChi(n.canNgay, n.chiNgay)} · month ${LichNgay.canChi(n.canThang, n.chiThang)}"),
            13.sp, FontWeight.Medium, Color.White.copy(alpha = 0.85f), bong = false)
        Spacer(Modifier.height(10.dp))
        val tot = n.hoangDao
        Row(Modifier.clip(RoundedCornerShape(50)).background(if (tot) Vang.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (tot) Vang else Color.White.copy(alpha = 0.45f), CircleShape))
            Spacer(Modifier.width(7.dp))
            Nhan(LichNgay.than(n.than) + " · " + (if (tot) t("Ngày hoàng đạo", "Auspicious day") else t("Ngày hắc đạo", "Inauspicious day")),
                12.5.sp, FontWeight.SemiBold, if (tot) Vang else Color.White.copy(alpha = 0.8f), bong = false)
        }
        Spacer(Modifier.height(8.dp))
        Nhan(t("Tiết ${LichNgay.tiet(n.tiet, true)} · Trực ${LichNgay.truc(n.truc)}", "${LichNgay.tiet(n.tiet, false)} · Trực ${LichNgay.truc(n.truc)}"),
            13.sp, FontWeight.Medium, Color.White.copy(alpha = 0.8f), bong = false)
        Spacer(Modifier.height(12.dp))
        Nhan(t("GIỜ HOÀNG ĐẠO", "AUSPICIOUS HOURS"), 11.sp, FontWeight.SemiBold, nhat, gian = 1.2.sp, bong = false)
        Spacer(Modifier.height(5.dp))
        n.gioTot.chunked(2).forEach { hang ->
            Row {
                hang.forEach { c ->
                    Row(Modifier.width(140.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Nhan(LichNgay.chi(c), 13.5.sp, FontWeight.SemiBold, Color.White, bong = false)
                        Spacer(Modifier.width(6.dp))
                        Nhan(LichNgay.khungGio(c), 12.5.sp, FontWeight.Medium, nhat, bong = false)
                    }
                }
            }
        }
        if (n.le.isNotEmpty() || n.tamNuong || n.nguyetKy) Spacer(Modifier.height(10.dp))
        n.le.forEach { le ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Hinh(Icons.Rounded.Celebration, 15, DoNhat)
                Spacer(Modifier.width(7.dp))
                Nhan(if (viet) le.vi else le.en, 13.sp, FontWeight.SemiBold, DoNhat, bong = false)
            }
        }
        if (n.tamNuong) Nhan(t("Ngày Tam nương: kiêng việc lớn", "Tam Nương day: avoid big undertakings"), 12.5.sp, FontWeight.Medium,
            CamIos, modifier = Modifier.padding(vertical = 2.dp), bong = false)
        if (n.nguyetKy) Nhan(t("Ngày Nguyệt kỵ: kiêng việc lớn", "Nguyệt Kỵ day: avoid big undertakings"), 12.5.sp, FontWeight.Medium,
            CamIos, modifier = Modifier.padding(vertical = 2.dp), bong = false)
    }
}
