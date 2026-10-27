package vn.tivikit.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
private val XanhChon = Color(0xFF6CB8FF)
private val BongChu = Shadow(Color(0x80000000), Offset(0f, 1.5f), 8f)
private val PHIM_OK = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Màn Home của TiviKit Launcher: ảnh nền, lời chào + đồng hồ + âm lịch + thông số ở góc trái, ba nút kính ở góc phải,
 * dock kính mờ ở dưới (app ghim | app mở gần đây | Tất cả ứng dụng), trang Tất cả ứng dụng và menu app đè lên.
 */
@Composable
fun ManHinhChinh(tt: TrangThaiHome) {
    // Màn Home: Back không làm gì (thư viện / menu đăng ký Back riêng, sau nên được ưu tiên).
    BackHandler { }
    ManAo {
        CompositionLocalProvider(LocalNen provides tt.nen) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Nen(tt.nen)
                CompositionLocalProvider(LocalKhoaChon provides (tt.thuVien || tt.menu != null)) {
                    KhoiGio(tt, Modifier.align(Alignment.TopStart).padding(start = 46.dp, top = 28.dp))
                    HangNut(tt, Modifier.align(Alignment.TopEnd).padding(end = 46.dp, top = 34.dp))
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val loi = when {
                            tt.dangDoi != null -> t("◀ ▶ để dời chỗ · OK để xong", "◀ ▶ to move · OK when done")
                            else -> tt.bao
                        }
                        if (loi != null) {
                            BaoNho(loi)
                            Spacer(Modifier.height(14.dp))
                        }
                        tt.dangPhat?.let { b ->
                            TheDangPhat(tt, b)
                            Spacer(Modifier.height(14.dp))
                        }
                        DockHome(tt)
                    }
                }
                if (tt.thuVien) CompositionLocalProvider(LocalKhoaChon provides (tt.menu != null)) { ThuVien(tt) }
                tt.menu?.let { MenuApp(tt, it) }
            }
        }
    }
}

/** Vẽ trên khung ảo rộng 960 dp rồi phóng theo màn hình thật: tivi 720p hay 4K bố cục cũng như nhau. */
@Composable
fun ManAo(rong: Float = 960f, noiDung: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val matDo = constraints.maxWidth / rong
        CompositionLocalProvider(LocalDensity provides Density(matDo, 1f)) { noiDung() }
    }
}

@Composable
private fun Nen(nen: NenKinh?) {
    if (nen != null) Image(nen.anh, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    // Tối nhẹ ở trên và dưới để chữ trắng đọc rõ trên ảnh sáng.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.40f), 0.42f to Color.Transparent, 0.66f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.28f))))
}

@Composable
private fun Nhan(s: String, co: TextUnit, dam: FontWeight, mau: Color, modifier: Modifier = Modifier, gian: TextUnit = TextUnit.Unspecified,
                 dong: Int = 1, bong: Boolean = true) {
    BasicText(s, modifier, style = TextStyle(color = mau, fontSize = co, fontWeight = dam, fontFamily = Inter, letterSpacing = gian,
        shadow = if (bong) BongChu else null), maxLines = dong, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Hinh(v: ImageVector, co: Int, mau: Color = Color.White) =
    Image(v, null, Modifier.size(co.dp), colorFilter = ColorFilter.tint(mau))

@Composable
private fun KhoiGio(tt: TrangThaiHome, modifier: Modifier) {
    val viet = NgonNgu.tiengViet
    val luc = tt.bayGio
    val ngay = luc.toLocalDate()
    val amLich = remember(ngay, viet) { AmLich.chu(ngay, viet) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Nhan(Chu.loiChao(luc.hour, tt.ten, viet), 14.sp, FontWeight.Medium, Color.White.copy(alpha = 0.92f), gian = 0.8.sp)
            Spacer(Modifier.width(7.dp))
            when (Chu.buoi(luc.hour)) {
                Chu.Buoi.SANG, Chu.Buoi.TRUA, Chu.Buoi.CHIEU -> Hinh(Icons.Rounded.WbSunny, 16, Color(0xFFFFD25A))
                Chu.Buoi.TOI -> Hinh(Icons.Rounded.WbTwilight, 16, Color(0xFFFFB27A))
                Chu.Buoi.DEM -> Hinh(Icons.Rounded.NightsStay, 16, Color(0xFFCBD5FF))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("%02d:%02d".format(luc.hour, luc.minute), style = TextStyle(color = Color.White, fontSize = 64.sp,
                fontWeight = FontWeight.Light, fontFamily = FontFamily.SansSerif, letterSpacing = (-1).sp, shadow = BongChu))
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(1.dp).height(46.dp).background(Color.White.copy(alpha = 0.42f)))
            Spacer(Modifier.width(16.dp))
            Column {
                Nhan(Chu.ngay(ngay, viet), 17.sp, FontWeight.SemiBold, Color.White, gian = 2.4.sp)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val nhat = Color.White.copy(alpha = 0.86f)
                    Nhan(amLich, 12.5.sp, FontWeight.Medium, nhat)
                    tt.thoiTiet?.let { h ->
                        Nhan("  ·  ", 12.5.sp, FontWeight.Medium, nhat)
                        Hinh(hinhThoiTiet(h.ma, luc.hour), 14, Color.White.copy(alpha = 0.92f))
                        Spacer(Modifier.width(4.dp))
                        Nhan(ThoiTiet.dong(h, viet), 12.5.sp, FontWeight.Medium, nhat)
                    }
                }
            }
        }
        if (tt.ramTrong > 0) Nhan(Chu.thongSo(tt.ramTrong, tt.boNhoTrong, viet), 14.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.95f))
    }
}

@Composable
private fun HangNut(tt: TrangThaiHome, modifier: Modifier) {
    val viet = NgonNgu.tiengViet
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        if (tt.coDonRam) NutKinh(Icons.Rounded.RocketLaunch, if (tt.dangDon) t("Đang dọn…", "Cleaning…") else t("Dọn RAM", "Free RAM")) { tt.hd.donRam() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val m = tt.mang
            val (hinh, chu) = when (m?.loai) {
                HeThong.LoaiMang.WIFI -> Icons.Rounded.Wifi to (m.ten ?: "Wi-Fi")
                HeThong.LoaiMang.DAY -> Icons.Rounded.SettingsEthernet to t("Mạng dây", "Ethernet")
                HeThong.LoaiMang.KHAC -> Icons.Rounded.Wifi to t("Mạng", "Network")
                else -> Icons.Rounded.WifiOff to t("Không có mạng", "Offline")
            }
            NutKinh(hinh, chu) { tt.hd.moMang() }
            if (tt.tocXuong >= 0) {
                Spacer(Modifier.height(7.dp))
                Nhan("↓ ${Chu.tocDo(tt.tocXuong, viet)} · ↑ ${Chu.tocDo(tt.tocLen.coerceAtLeast(0), viet)}", 11.5.sp, FontWeight.Medium,
                    Color.White.copy(alpha = 0.86f))
            }
        }
        NutKinh(Icons.Rounded.Settings, t("Cài đặt", "Settings")) { tt.hd.moCaiDat() }
    }
}

/** Nút viên thuốc bằng kính: chọn thì sáng lên, viền xanh, to ra một chút. */
@Composable
private fun NutKinh(hinh: ImageVector, chu: String, onClick: () -> Unit) {
    var chon by remember { mutableStateOf(false) }
    val to by animateFloatAsState(if (chon) 1.06f else 1f, tween(140), label = "nut")
    val vien = RoundedCornerShape(50)
    Row(Modifier.graphicsLayer { scaleX = to; scaleY = to }
        .nutTv(onClick, onChon = { chon = it })
        .kinh(LocalNen.current, vien, toi = if (chon) 0.04f else 0.20f, sang = if (chon) 0.22f else 0.07f)
        .then(if (chon) Modifier.border(2.dp, XanhChon, vien) else Modifier)
        .height(40.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Hinh(hinh, 18)
        Spacer(Modifier.width(9.dp))
        Nhan(chu, 15.sp, FontWeight.Medium, Color.White, modifier = Modifier.widthIn(max = 190.dp))
    }
}

/** Biểu tượng thời tiết theo mã WMO; trời quang / ít mây buổi tối thì là mặt trăng. */
private fun hinhThoiTiet(ma: Int, gio: Int): ImageVector {
    val dem = gio >= 18 || gio < 6
    return when (ThoiTiet.loai(ma)) {
        ThoiTiet.Loai.QUANG -> if (dem) Icons.Rounded.NightsStay else Icons.Rounded.WbSunny
        ThoiTiet.Loai.IT_MAY -> if (dem) Icons.Rounded.NightsStay else Icons.Rounded.WbCloudy
        ThoiTiet.Loai.MAY -> Icons.Rounded.Cloud
        ThoiTiet.Loai.SUONG -> Icons.Rounded.Dehaze
        ThoiTiet.Loai.MUA_PHUN -> Icons.Rounded.Grain
        ThoiTiet.Loai.MUA -> Icons.Rounded.Umbrella
        ThoiTiet.Loai.TUYET -> Icons.Rounded.AcUnit
        ThoiTiet.Loai.DONG -> Icons.Rounded.Thunderstorm
        ThoiTiet.Loai.KHAC -> Icons.Rounded.Thermostat
    }
}

/**
 * Thẻ Đang phát (như ảnh mẫu): ảnh bìa / biểu tượng app, tên bài, tên app · nghệ sĩ, sóng nhạc khi đang phát. OK mở đúng
 * màn đang phát, giữ OK để tạm dừng / phát tiếp.
 */
@Composable
private fun TheDangPhat(tt: TrangThaiHome, b: BaiDangPhat) {
    var chon by remember { mutableStateOf(false) }
    val to by animateFloatAsState(if (chon) 1.05f else 1f, tween(140), label = "dang-phat")
    val vien = RoundedCornerShape(16.dp)
    LaunchedEffect(b.goi) { tt.hd.canAnh(b.goi) }
    Row(Modifier.graphicsLayer { scaleX = to; scaleY = to }
        .nutTv({ tt.hd.moDangPhat() }, onGiu = { tt.hd.dungPhat() }, onChon = { chon = it })
        .kinh(LocalNen.current, vien, toi = if (chon) 0.05f else 0.16f, sang = if (chon) 0.22f else 0.10f)
        .then(if (chon) Modifier.border(2.dp, XanhChon, vien) else Modifier)
        .widthIn(min = 250.dp, max = 400.dp).padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val anh = b.anh ?: tt.anh[b.goi]?.bieuTuong
        if (anh != null) Image(anh, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        else Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Hinh(Icons.Rounded.MusicNote, 18)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Nhan(b.tieuDe, 13.sp, FontWeight.SemiBold, Color.White)
            if (b.phu.isNotBlank()) Nhan(b.phu, 11.sp, FontWeight.Medium, XanhChon.copy(alpha = 0.95f))
        }
        Spacer(Modifier.width(12.dp))
        if (b.dangPhat) SongNhac() else Hinh(Icons.Rounded.Pause, 16, Color.White.copy(alpha = 0.85f))
    }
}

/** Ba vạch nhạc nhảy (chỉ vẽ lại, không dựng lại giao diện mỗi khung hình). */
@Composable
private fun SongNhac() {
    val chay = rememberInfiniteTransition(label = "song")
    val pha by chay.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "pha")
    Canvas(Modifier.size(16.dp, 14.dp)) {
        val w = size.width / 5
        for (i in 0..2) {
            val h = size.height * (0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin((pha + i * 0.33f) * Math.PI)).toFloat())
            drawRoundRect(XanhChon, Offset(i * 2 * w, size.height - h), Size(w, h), CornerRadius(w / 2))
        }
    }
}

@Composable
private fun BaoNho(s: String) {
    Box(Modifier.kinh(LocalNen.current, RoundedCornerShape(50), toi = 0.32f, sang = 0.06f).padding(horizontal = 18.dp, vertical = 9.dp)) {
        Nhan(s, 13.sp, FontWeight.Medium, Color.White, dong = 2)
    }
}

@Composable
private fun DockHome(tt: TrangThaiHome) {
    // 7 ô banner + 2 vạch + nút Tất cả vừa khít 920 dp; ghim thêm thì dock cuộn ngang theo ô đang chọn.
    Box(Modifier.widthIn(max = 920.dp).kinh(LocalNen.current, RoundedCornerShape(26.dp), toi = 0.10f, sang = 0.14f)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            tt.dock.forEachIndexed { i, goi -> androidx.compose.runtime.key(goi) { ODock(tt, goi, dauDock = i == 0) } }
            if (tt.ganDay.isNotEmpty()) {
                VachNgan()
                tt.ganDay.forEach { goi -> androidx.compose.runtime.key("gd:$goi") { ODock(tt, goi, dauDock = false) } }
            }
            VachNgan()
            NutTatCa(tt)
        }
    }
}

@Composable
private fun VachNgan() = Box(Modifier.width(1.dp).height(38.dp).background(Color.White.copy(alpha = 0.32f)))

/** Tấm kính sáng sau ô đang chọn và vạch nhỏ bên dưới, như dock của máy Mac. */
private fun Modifier.tamChon(chon: Boolean, vach: Boolean): Modifier = drawBehind {
    if (!chon) return@drawBehind
    val p = 6.dp.toPx()
    drawRoundRect(Color.White.copy(alpha = 0.26f), Offset(-p, -p), Size(size.width + 2 * p, size.height + 2 * p), CornerRadius(15.dp.toPx()))
    if (vach) {
        val w = 28.dp.toPx()
        val h = 3.dp.toPx()
        drawRoundRect(Color.White.copy(alpha = 0.92f), Offset((size.width - w) / 2, size.height + 8.dp.toPx()), Size(w, h), CornerRadius(h / 2))
    }
}

/** Ô app trong dock: mở bằng OK, giữ OK để hiện menu; đang dời chỗ thì ◀ ▶ dời, OK / Back để xong. */
@Composable
private fun ODock(tt: TrangThaiHome, goi: String, dauDock: Boolean) {
    val app = tt.theoGoi[goi] ?: return
    val fr = remember { FocusRequester() }
    var chon by remember { mutableStateOf(false) }
    val doi = tt.dangDoi == goi
    val to by animateFloatAsState(if (chon) 1.12f else 1f, tween(150), label = "o")
    if (dauDock) LaunchedEffect(tt.lanVeDau) { runCatching { fr.requestFocus() } }
    LaunchedEffect(doi) { if (doi) runCatching { fr.requestFocus() } }
    LaunchedEffect(tt.traChon, tt.thuVien) {
        if (tt.traChon == goi && !tt.thuVien) { runCatching { fr.requestFocus() }; tt.traChon = null }
    }
    Box(Modifier.focusRequester(fr)
        .onPreviewKeyEvent { e ->
            if (!doi) return@onPreviewKeyEvent false
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key in PHIM_OK || e.key == Key.Back
            when (e.key) {
                Key.DirectionLeft -> { tt.hd.doi(goi, -1); true }
                Key.DirectionRight -> { tt.hd.doi(goi, 1); true }
                Key.DirectionUp, Key.DirectionDown -> true
                Key.Back, Key.Escape, in PHIM_OK -> { tt.hd.xongDoi(); true }
                else -> false
            }
        }
        .nutTv({ tt.hd.mo(goi) }, onGiu = { tt.menu = goi }, onChon = { chon = it })
        .graphicsLayer { scaleX = to; scaleY = to }
        .tamChon(chon, vach = true)
        .size(96.dp, 54.dp)) {
        AnhO(tt, app, Modifier.fillMaxSize())
        if (doi) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 2.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f))) { Hinh(Icons.Rounded.ChevronLeft, 20) }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f))) { Hinh(Icons.Rounded.ChevronRight, 20) }
        }
    }
}

@Composable
private fun NutTatCa(tt: TrangThaiHome) {
    val fr = remember { FocusRequester() }
    var chon by remember { mutableStateOf(false) }
    val to by animateFloatAsState(if (chon) 1.12f else 1f, tween(150), label = "tat-ca")
    LaunchedEffect(tt.lanVeDau, tt.dock.isEmpty()) { if (tt.dock.isEmpty()) runCatching { fr.requestFocus() } }
    LaunchedEffect(tt.traChon) { if (tt.traChon == TrangThaiHome.TAT_CA) { runCatching { fr.requestFocus() }; tt.traChon = null } }
    Box(Modifier.focusRequester(fr)
        .nutTv({ tt.hd.moThuVien() }, onChon = { chon = it })
        .graphicsLayer { scaleX = to; scaleY = to }
        .tamChon(chon, vach = true)
        .size(54.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = if (chon) 0.30f else 0.16f)),
        contentAlignment = Alignment.Center) {
        Hinh(Icons.Rounded.Apps, 28)
    }
}

/** Banner của app, hoặc ô màu lấy từ biểu tượng (biểu tượng + tên) cho app không có banner. */
@Composable
private fun AnhO(tt: TrangThaiHome, app: AppTv, modifier: Modifier) {
    LaunchedEffect(app.goi, app.capNhat) { tt.hd.canAnh(app.goi) }
    val a = tt.anh[app.goi]
    val vien = RoundedCornerShape(10.dp)
    val banner = a?.banner
    if (banner != null) {
        Image(banner, app.ten, modifier.clip(vien), contentScale = ContentScale.Crop)
        return
    }
    val mau = a?.mau ?: Color(0xFF3A4060)
    Column(modifier.clip(vien).background(Brush.linearGradient(listOf(mau.sang(0.18f), mau))).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        a?.bieuTuong?.let { Image(it, null, Modifier.size(24.dp)) }
        Spacer(Modifier.height(3.dp))
        Nhan(app.ten, 10.sp, FontWeight.SemiBold, Color.White)
    }
}

private fun Color.sang(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

@Composable
private fun ThuVien(tt: TrangThaiHome) {
    val dau = remember { FocusRequester() }
    BackHandler { tt.hd.dongThuVien() }
    LaunchedEffect(Unit) { runCatching { dau.requestFocus() } }
    Box(Modifier.fillMaxSize().kinh(LocalNen.current, RectangleShape, toi = 0.45f, sang = 0.03f, vien = 0f)) {
        Column(Modifier.fillMaxSize().padding(start = 46.dp, end = 46.dp, top = 30.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Nhan(t("Ứng dụng", "Apps"), 26.sp, FontWeight.SemiBold, Color.White)
                Spacer(Modifier.width(12.dp))
                Nhan("${tt.apps.size}", 15.sp, FontWeight.Medium, Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 3.dp))
                Spacer(Modifier.weight(1f))
                Nhan(t("Giữ OK để ghim vào dock hoặc gỡ", "Hold OK to pin to the dock or uninstall"), 12.5.sp, FontWeight.Medium,
                    Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(GridCells.Fixed(6), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                itemsIndexed(tt.apps, key = { _, a -> a.goi }) { i, a -> OThuVien(tt, a, if (i == 0) dau else null) }
            }
        }
    }
}

@Composable
private fun OThuVien(tt: TrangThaiHome, app: AppTv, dau: FocusRequester?) {
    val fr = dau ?: remember { FocusRequester() }
    var chon by remember { mutableStateOf(false) }
    val to by animateFloatAsState(if (chon) 1.08f else 1f, tween(140), label = "tv")
    LaunchedEffect(tt.traChon) { if (tt.traChon == app.goi) { runCatching { fr.requestFocus() }; tt.traChon = null } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.focusRequester(fr)
            .nutTv({ tt.hd.mo(app.goi) }, onGiu = { tt.menu = app.goi }, onChon = { chon = it })
            .graphicsLayer { scaleX = to; scaleY = to }
            .tamChon(chon, vach = false)
            .fillMaxWidth().aspectRatio(16f / 9f)) {
            AnhO(tt, app, Modifier.fillMaxSize())
            if (app.goi in tt.dock) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.5f)).padding(3.dp)) { Hinh(Icons.Rounded.PushPin, 11) }
        }
        Spacer(Modifier.height(8.dp))
        Nhan(app.ten, 12.sp, if (chon) FontWeight.SemiBold else FontWeight.Medium, Color.White.copy(alpha = if (chon) 1f else 0.82f))
    }
}

private class MucMenu(val hinh: ImageVector, val chu: String, val lam: () -> Unit)

/** Menu của một app (giữ OK): mở, ghim / bỏ khỏi dock, dời chỗ, thông tin, gỡ. Back để đóng. */
@Composable
private fun MenuApp(tt: TrangThaiHome, goi: String) {
    val app = tt.theoGoi[goi]
    val dau = remember { FocusRequester() }
    BackHandler { tt.hd.dongMenu() }
    LaunchedEffect(goi) { runCatching { dau.requestFocus() } }
    if (app == null) return
    val trongDock = goi in tt.dock
    val muc = buildList {
        add(MucMenu(Icons.Rounded.PlayArrow, t("Mở", "Open")) { tt.menu = null; tt.hd.mo(goi) })
        if (trongDock) {
            add(MucMenu(Icons.Rounded.SwapHoriz, t("Dời chỗ trong dock", "Move in the dock")) { tt.menu = null; tt.thuVien = false; tt.dangDoi = goi })
            add(MucMenu(Icons.Rounded.RemoveCircleOutline, t("Bỏ khỏi dock", "Remove from the dock")) { tt.hd.boGhim(goi) })
        } else add(MucMenu(Icons.Rounded.PushPin, t("Ghim vào dock", "Pin to the dock")) { tt.hd.ghim(goi) })
        add(MucMenu(Icons.Rounded.Info, t("Thông tin ứng dụng", "App info")) { tt.menu = null; tt.hd.thongTin(goi) })
        if (!app.heThong) add(MucMenu(Icons.Rounded.Delete, t("Gỡ cài đặt", "Uninstall")) { tt.menu = null; tt.hd.go(goi) })
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(340.dp).kinh(LocalNen.current, RoundedCornerShape(22.dp), toi = 0.34f, sang = 0.10f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnhO(tt, app, Modifier.size(80.dp, 45.dp))
                Spacer(Modifier.width(12.dp))
                Nhan(app.ten, 16.sp, FontWeight.SemiBold, Color.White, dong = 2)
            }
            Spacer(Modifier.height(12.dp))
            muc.forEachIndexed { i, m -> DongMenu(m, if (i == 0) dau else null) }
        }
    }
}

@Composable
private fun DongMenu(m: MucMenu, dau: FocusRequester?) {
    val fr = dau ?: remember { FocusRequester() }
    var chon by remember { mutableStateOf(false) }
    val mau = if (chon) Color(0xFF15171C) else Color.White
    Row(Modifier.fillMaxWidth().focusRequester(fr)
        .nutTv(m.lam, onChon = { chon = it })
        .clip(RoundedCornerShape(12.dp)).background(if (chon) Color.White.copy(alpha = 0.94f) else Color.Transparent)
        .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Hinh(m.hinh, 20, mau)
        Spacer(Modifier.width(12.dp))
        Nhan(m.chu, 15.sp, FontWeight.Medium, mau, bong = false)
    }
}
