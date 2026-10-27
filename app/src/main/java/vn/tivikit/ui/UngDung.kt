package vn.tivikit.ui

import vn.tivikit.dulieu.GoiApk
import vn.tivikit.dulieu.KeHoach
import vn.tivikit.dulieu.KenhVtvGo
import vn.tivikit.dulieu.XaPhuong
import androidx.compose.foundation.layout.Row
import vn.tivikit.AnhTivi
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.tivikit.BuildConfig
import vn.tivikit.NgonNgu
import vn.tivikit.R
import vn.tivikit.dulieu.LoaiMuc
import vn.tivikit.dulieu.MucGoi
import vn.tivikit.dulieu.THEMES
import vn.tivikit.dulieu.HUONG_DAN
import vn.tivikit.dulieu.THANH_PHO
import vn.tivikit.dulieu.Theme
import vn.tivikit.dulieu.tenDep
import vn.tivikit.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Màu, chữ, công tắc: GiaoDien.kt (kiểu Apple, sáng / tối theo điện thoại).

// ---------------- Khung ----------------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UngDung(vm: PhienLamViec = viewModel()) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { NgonNgu.nap(ctx) }
    // Giữ kết nối tivi chỉ lúc app đang hiện (xem GiuKetNoi).
    val vongDoi = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(vongDoi) {
        val nghe = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) vm.giuKetNoi.hien()
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.giuKetNoi.an()
        }
        vongDoi.lifecycle.addObserver(nghe)
        onDispose { vongDoi.lifecycle.removeObserver(nghe) }
    }
    ChuDe {
        // Chế độ tivi: đi bằng remote nên ô đang chọn phải thấy rõ — lớp sáng khi focus đậm hơn hẳn mặc định.
        val gon = if (vn.tivikit.ManHinh.laTv)
            androidx.compose.material3.RippleConfiguration(Color.White, androidx.compose.material.ripple.RippleAlpha(0.3f, 0.35f, 0.3f, 0.2f))
        else androidx.compose.material3.LocalRippleConfiguration.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalRippleConfiguration provides gon) {
        Surface(Modifier.fillMaxSize(), color = Nen) {
            BackHandler(enabled = vm.man != Man.CHINH && !vm.dangBan) { quayLai(vm) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // enableEdgeToEdge: Android không còn tự co cửa sổ khi bàn phím mở (adjustResize vô tác dụng) — imePadding chừa
            // chỗ bàn phím, khung cuộn co lại và giữ ô đang gõ trong tầm nhìn (25/10/2026: ô gõ chữ của remote bị bàn phím che).
            Column(Modifier.fillMaxHeight().systemBarsPadding().imePadding().then(
                if (vn.tivikit.ManHinh.laTv) Modifier.widthIn(max = 820.dp).padding(top = 12.dp) else Modifier.fillMaxWidth())) {
                // Màn chính có tiêu đề lớn riêng (kèm nút ngôn ngữ), như màn gốc của app iPhone.
                if (vm.man != Man.CHINH) ThanhTren(vm)
                if (vm.dangDon) buoc(vm.man)?.let { ThanhBuoc(it) }
                Box(Modifier.weight(1f)) {
                    when (vm.man) {
                        Man.CHINH -> ManChinh(vm)
                        Man.KET_NOI -> ManKetNoi(vm)
                        Man.THONG_TIN -> ManThongTin(vm)
                        Man.CHON_GOI -> ManChonGoi(vm)
                        Man.DANG_TAT -> ManDangTat(vm)
                        Man.HOME_SACH -> ManHomeSach(vm)
                        Man.XONG -> ManXong(vm)
                        Man.KHOI_PHUC -> ManKhoiPhuc(vm)
                        Man.HUONG_DAN -> ManHuongDan(vm)
                        Man.GIAY_PHEP -> ManGiayPhep()
                        Man.KHOA_TRE -> ManKhoaTre(vm)
                        Man.CAI_APK -> ManCaiApk(vm)
                        Man.DIEU_KHIEN -> ManDieuKhien(vm)
                        Man.QUAN_LY_APP -> ManQuanLyApp(vm)
                        Man.GUI_FILE -> ManGuiFile(vm)
                        Man.BO_CAI -> ManBoCai(vm)
                        Man.DNS -> ManDns(vm)
                        Man.GAN_PHIM -> ManGanPhim(vm)
                        Man.KENH_NHANH -> ManKenhNhanh(vm)
                        Man.HEN_GIO -> ManHenGio(vm)
                        Man.LAM_MOI -> ManLamMoi(vm)
                        Man.MANG -> ManMang(vm)
                        Man.CAI_DAT_NHANH -> ManCaiDatNhanh(vm)
                        Man.CHUYEN_MAY -> ManChuyenMay(vm)
                        Man.LG -> ManLg(vm)
                        Man.DONG_LENH -> ManDongLenh(vm)
                    }
                }
                if (vm.loi.isNotEmpty() || (vm.dangBan && vm.man != Man.DANG_TAT)) ThanhTrangThai(vm)
            }
            }
            if (vm.moLienHe) HopLienHe(vm)
            if (vm.moUngHo) HopUngHo(vm)
            if (vm.gopNguoc.hien) HopGopNguoc(vm)
            vm.banMoi?.takeIf { vm.hoiCapNhat }?.let { b ->
                AlertDialog(
                    onDismissRequest = { vm.hoiCapNhat = false },
                    icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = Nhan) },
                    title = { Text(t("Có bản mới ${b.versionName}", "TiviKit ${b.versionName} is out")) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (b.ghiChu.isNotBlank()) Text(b.ghiChu)
                            Phu(t("Bạn đang dùng bản ${BuildConfig.VERSION_NAME}. Cập nhật mất chừng một phút, các tivi đã dọn vẫn giữ nguyên.",
                                "You're on ${BuildConfig.VERSION_NAME}. Updating takes about a minute; your cleaned TVs stay as they are."))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.hoiCapNhat = false; vm.capNhatApp() }) { Text(t("Cập nhật ngay", "Update now"), fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = { TextButton(onClick = { vm.hoiCapNhat = false }) { Text(t("Để sau", "Later")) } },
                    containerColor = The1,
                )
            }
            vm.anhTv?.let { anh ->
                val ctxAnh = LocalContext.current
                AlertDialog(
                    onDismissRequest = { vm.anhTv = null },
                    confirmButton = {
                        Row {
                            if (AnhTivi.luuDuoc) TextButton(onClick = {
                                val ok = AnhTivi.luu(ctxAnh, anh)
                                Toast.makeText(ctxAnh, if (ok) t("Đã lưu vào Ảnh › TiviKit", "Saved to Pictures › TiviKit") else t("Không lưu được ảnh", "Couldn't save the picture"), Toast.LENGTH_SHORT).show()
                            }) { Text(t("Lưu ảnh", "Save"), fontWeight = FontWeight.SemiBold) }
                            TextButton(onClick = { AnhTivi.chiaSe(ctxAnh, anh) }) { Text(t("Chia sẻ", "Share")) }
                            TextButton(onClick = { vm.anhTv = null }) { Text(t("Đóng", "Close")) }
                        }
                    },
                    title = { Text(t("Màn hình tivi", "TV screen")) },
                    text = { Image(anh.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))) },
                    containerColor = The1,
                )
            }
        }
    }
    }
}

private fun buoc(m: Man): Int? = when (m) {
    Man.KET_NOI -> 0
    Man.THONG_TIN, Man.CHON_GOI -> 1
    Man.DANG_TAT -> 2
    Man.HOME_SACH, Man.XONG -> 3
    else -> null
}

private fun quayLai(vm: PhienLamViec) {
    vm.loi = ""
    vm.man = when (vm.man) {
        // Chưa nối được tivi nào thì đặt lại phiên; đang nối sẵn (bấm Đổi tivi rồi thôi) thì giữ kết nối cũ.
        Man.KET_NOI -> { if (vm.thongTin == null) vm.veManChinh(); vm.veHub(); Man.CHINH }
        Man.THONG_TIN, Man.KHOI_PHUC -> { vm.veHub(); Man.CHINH }
        Man.CHON_GOI -> Man.THONG_TIN
        Man.DANG_TAT -> Man.CHON_GOI
        Man.HOME_SACH -> if (vm.moNangCao) { vm.moNangCao = false; Man.HOME_SACH } else vm.manTruocHome
        Man.XONG -> Man.HOME_SACH
        Man.HUONG_DAN, Man.GIAY_PHEP -> vm.manTruoc
        Man.KHOA_TRE, Man.CAI_APK, Man.DIEU_KHIEN, Man.QUAN_LY_APP, Man.GUI_FILE, Man.DNS, Man.GAN_PHIM, Man.KENH_NHANH, Man.HEN_GIO, Man.LAM_MOI, Man.MANG, Man.CAI_DAT_NHANH, Man.CHUYEN_MAY, Man.LG -> { vm.veHub(); Man.CHINH }
        // Rời Dòng lệnh thì dừng lệnh đang chạy (logcat…), không để chạy ngầm trên tivi.
        Man.DONG_LENH -> { vm.dongLenh.dung(); vm.veHub(); Man.CHINH }
        // Đang soạn bộ / chọn tivi thì về danh sách bộ; mở từ màn chính (chưa nối tivi) thì về màn chính.
        Man.BO_CAI -> if (vm.boCai.quayLai()) Man.BO_CAI else { vm.veHub(); Man.CHINH }
        Man.CHINH -> Man.CHINH
    }
}

@Composable
private fun ThanhTren(vm: PhienLamViec) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Nút quay lại kiểu iPhone: mũi tên nhọn + chữ, màu nhấn.
        Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (!vm.dangBan) quayLai(vm) }.padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowBackIosNew, null, Modifier.size(20.dp), tint = Nhan)
            Spacer(Modifier.width(4.dp))
            Text(t("Quay lại", "Back"), color = Nhan, fontSize = 17.sp)
        }
        Spacer(Modifier.weight(1f))
        // Màn Tivi LG: tên tivi Android (ADB) ở đây dễ nhầm, không hiện.
        val ten = if (vm.man == Man.LG) null else vm.thongTin?.tenHienThi
        val gk = vm.giuKetNoi
        if (ten != null && gk.tinhTrang != GiuKetNoi.TinhTrang.ON) {
            // Rớt kết nối giữa lúc dùng công cụ: báo ngay trên thanh, app đang tự nối lại.
            Text(if (gk.tinhTrang == GiuKetNoi.TinhTrang.DANG_NOI) t("Đang nối lại…", "Reconnecting…") else t("Mất kết nối", "Disconnected"),
                fontSize = 13.sp, color = if (gk.tinhTrang == GiuKetNoi.TinhTrang.DANG_NOI) Cam else DoTuoi, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
        } else if (ten != null) {
            Text(ten, fontSize = 13.sp, color = ChuPhu, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
            Spacer(Modifier.width(12.dp))
        } else {
            NutNgonNgu()
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Nút đổi tiếng Việt / tiếng Anh: viên thuốc xám nhỏ. */
@Composable
internal fun NutNgonNgu() {
    val ctx = LocalContext.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(NenO).clickable { NgonNgu.doi(ctx) }.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Language, null, Modifier.size(16.dp), tint = Nhan)
        Spacer(Modifier.width(6.dp))
        Text(if (NgonNgu.tiengViet) "VI" else "EN", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Nhan)
    }
}

/** Thanh 4 bước: Kết nối · Chọn · Dọn · Home. */
@Composable
private fun ThanhBuoc(dangO: Int) {
    val ten = listOf(t("Kết nối", "Connect"), t("Chọn", "Choose"), t("Dọn", "Clean"), "Home")
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ten.forEachIndexed { i, s ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= dangO) Nhan else NenO))
                Text(s, fontSize = 11.sp, color = if (i == dangO) ChuChinh else ChuPhu, modifier = Modifier.padding(top = 4.dp),
                    fontWeight = if (i == dangO) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ThanhTrangThai(vm: PhienLamViec) {
    val laLoi = vm.loi.isNotEmpty()
    Row(Modifier.fillMaxWidth().padding(12.dp).shadow(if (laLoi) 0.dp else 6.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
        .background(if (laLoi) NenLoi else The1).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (laLoi) Icon(Icons.Rounded.ErrorOutline, null, tint = DoTuoi)
        else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Nhan, trackColor = NenO)
        Spacer(Modifier.width(12.dp))
        Text(if (laLoi) vm.loi else vm.thongBao, Modifier.weight(1f), fontSize = 14.sp)
        if (laLoi && vm.man == Man.KET_NOI) TextButton(onClick = { vm.loi = ""; vm.moHuongDan() }) { Text(t("Hướng dẫn", "Guide")) }
        if (laLoi) TextButton(onClick = { vm.loi = "" }) { Text(t("Ẩn", "Hide")) }
    }
}

// ---------------- Mảnh dùng chung ----------------

@Composable
internal fun Cuon(noiDung: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = noiDung)
}

/** Thẻ bo góc không viền, như nhóm trong màn Cài đặt của iPhone; tiêu đề đậm kèm biểu tượng màu nhấn. */
@Composable
internal fun The(tieuDe: String? = null, icon: ImageVector? = null, noiDung: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tieuDe != null) Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = Nhan); Spacer(Modifier.width(8.dp)) }
            Text(tieuDe, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        noiDung()
    }
}

/** Nút chính: đặc màu nhấn, chữ trắng; tắt thì xám như iOS. */
@Composable
internal fun NutChinh(chu: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val mauChu = if (enabled) Color.White else ChuPhu
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) Nhan else NenO)
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = mauChu); Spacer(Modifier.width(8.dp)) }
        Text(chu, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = mauChu, textAlign = TextAlign.Center)
    }
}

/** Nút phụ: nền màu nhấn nhạt, chữ màu nhấn (kiểu "tinted" của iOS). */
@Composable
internal fun NutPhu(chu: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val mau = if (enabled) Nhan else ChuPhu
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) Nhan.copy(alpha = 0.13f) else NenO)
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = mau); Spacer(Modifier.width(8.dp)) }
        Text(chu, color = mau, fontWeight = FontWeight.Medium, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun Phu(chu: String, mau: Color = ChuPhu) = Text(chu, fontSize = 13.sp, color = mau, lineHeight = 18.sp)

/** Ô biểu tượng bo góc như biểu tượng app iPhone, hình trắng. */
@Composable
internal fun OIcon(icon: ImageVector, size: Dp = 44.dp, nen: Brush = Gradient) {
    Box(Modifier.size(size).clip(RoundedCornerShape(23)).background(nen), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
internal fun OIcon(icon: ImageVector, size: Dp, mau: Color) = OIcon(icon, size, SolidColor(mau))

/** Nhãn viên thuốc: nền cùng màu chữ, nhạt. */
@Composable
internal fun Chip(chu: String, mau: Color = ChuPhu) {
    Text(chu, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = mau,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(mau.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Mục gập/mở: mũi tên xoay xuống khi mở. */
@Composable
internal fun Gap(tieuDe: String, icon: ImageVector, moSan: Boolean = false, noiDung: @Composable ColumnScope.() -> Unit) {
    var mo by rememberSaveable(icon.name) { mutableStateOf(moSan) }
    val xoay by animateFloatAsState(if (mo) 90f else 0f, label = "xoay")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1)) {
        Row(Modifier.fillMaxWidth().clickable { mo = !mo }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = Nhan); Spacer(Modifier.width(10.dp))
            Text(tieuDe, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp).rotate(xoay), tint = ChuPhu)
        }
        AnimatedVisibility(mo) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = noiDung)
        }
    }
}

@Composable
internal fun BuocSo(so: Int, chu: String) {
    Row {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Nhan.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text("$so", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Nhan)
        }
        Spacer(Modifier.width(10.dp))
        Text(chu, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** Màn kết quả lớn ở giữa: icon + tiêu đề + dòng phụ. */
@Composable
internal fun DauTrang(icon: @Composable () -> Unit, tieuDe: String, phu: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        icon()
        Text(tieuDe, style = KieuTieuDeLon, textAlign = TextAlign.Center)
        if (phu != null) Phu(phu)
    }
}

/** Mở hộp góp ý chung của vgs.zone (mục App CleanTV tự chọn theo chữ "CleanTV"), kèm bản app + model tivi. */
@Composable
internal fun NutGopY(vm: PhienLamViec) {
    // Góp ý về TiviKit (26/10/2026): nhóm Telegram / báo lỗi trên GitHub, thay cho trang góp ý của VGS.
    NutPhu(t("Góp ý / báo lỗi", "Feedback / report a bug"), Icons.Rounded.Feedback) { vm.moLienHe = true }
}

internal fun ngay(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

// ---------------- Kết nối ----------------

@Composable
private fun Radar(dangQuet: Boolean) {
    val tr = rememberInfiniteTransition(label = "radar")
    val p by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "p")
    val nhan = Nhan
    val vien = Vien
    Box(Modifier.size(170.dp).drawBehind {
        val r = size.minDimension / 2
        if (dangQuet) for (k in 0..2) {
            val f = (p + k / 3f) % 1f
            drawCircle(nhan.copy(alpha = (1f - f) * 0.5f), radius = r * (0.35f + 0.65f * f), style = Stroke(2.dp.toPx()))
        }
        drawCircle(vien, radius = r * 0.98f, style = Stroke(1.dp.toPx()))
        drawCircle(vien, radius = r * 0.66f, style = Stroke(1.dp.toPx()))
    }, contentAlignment = Alignment.Center) {
        OIcon(Icons.Rounded.Tv, 64.dp)
    }
}

@Composable
private fun ManKetNoi(vm: PhienLamViec) = Cuon {
    if (vn.tivikit.ManHinh.laTv) { KetNoiTiviNay(vm); return@Cuon }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NenCanhBao).clickable { vm.moHuongDan() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Tivi đã bật Gỡ lỗi USB chưa?", "Is USB debugging on?"), fontWeight = FontWeight.SemiBold)
            Phu(t("Chưa bật thì app không kết nối được. Bấm để xem hướng dẫn có hình.", "Without it the app can't connect. Tap for the picture guide."), ChuChinh.copy(alpha = 0.8f))
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Cam)
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Radar(vm.dangQuet || vm.dangBan)
        Text(
            when {
                vm.dangKhoiPhuc -> t("Kết nối lại tivi cần khôi phục", "Reconnect the TV to restore")
                vm.dangQuet -> t("Đang tìm tivi trong nhà…", "Looking for TVs…")
                else -> t("Tìm tivi trong nhà", "Find the TV")
            }, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Phu(t("Điện thoại và tivi chung Wi-Fi, tivi đã bật Gỡ lỗi USB.", "Phone and TV on the same Wi-Fi, USB debugging on."))
    }
    NutPhu(if (vm.dangQuet) t("Đang quét…", "Scanning…") else t("Quét mạng tìm tivi", "Scan the network"), Icons.Rounded.WifiFind, enabled = !vm.dangQuet) { vm.quetMang() }
    vm.timThay.forEach { dc ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OIcon(Icons.Rounded.Tv, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val ten = vm.tenGanDay(dc)
                Text(ten ?: dc, fontWeight = FontWeight.SemiBold)
                Phu(if (ten != null) "$dc · " + t("cổng 5555 mở", "port 5555 open") else t("Android TV · cổng 5555 mở", "Android TV · port 5555 open"))
            }
            Button(onClick = { vm.ip = dc; vm.cong = "5555"; vm.ketNoi() }, enabled = !vm.dangBan, shape = RoundedCornerShape(12.dp)) {
                Text(t("Kết nối", "Connect"))
            }
        }
    }
    Gap(t("Nhập IP tay", "Enter IP manually"), Icons.Rounded.Keyboard, moSan = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(vm.ip, { vm.ip = it.trim() }, label = { Text(t("IP tivi", "TV IP")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(vm.cong, { vm.cong = it.filter(Char::isDigit) }, label = { Text(t("Cổng", "Port")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp), shape = RoundedCornerShape(12.dp))
        }
        NutChinh(t("Kết nối", "Connect"), enabled = !vm.dangBan) { vm.ketNoi() }
        Phu(t("IP tivi: Cài đặt → Mạng → mạng đang dùng. Lần đầu tivi hỏi \"Cho phép gỡ lỗi?\": tích Luôn cho phép rồi bấm Cho phép.",
            "TV IP: Settings → Network → current network. The first time the TV asks \"Allow debugging?\": tick Always allow and press Allow."))
    }
    Gap(t("Ghép đôi bằng mã (Gỡ lỗi không dây)", "Pair with code (Wireless debugging)"), Icons.Rounded.Link) {
        Phu(t("Trên tivi: Tuỳ chọn nhà phát triển → Gỡ lỗi không dây → Ghép nối thiết bị bằng mã.", "On the TV: Developer options → Wireless debugging → Pair device with pairing code."))
        OutlinedTextField(vm.ghepIpCong, { vm.ghepIpCong = it.trim() }, label = { Text(t("IP:cổng ghép đôi", "Pairing IP:port")) }, placeholder = { Text("192.168.1.20:37123") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        OutlinedTextField(vm.ghepMa, { vm.ghepMa = it.filter(Char::isDigit).take(6) }, label = { Text(t("Mã 6 số", "6-digit code")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        NutPhu(t("Ghép đôi & kết nối", "Pair & connect"), Icons.Rounded.Link, enabled = !vm.dangBan) { vm.ghepDoi() }
    }
}

/** Chế độ tivi: app chạy ngay trên tivi, kết nối ADB vào chính nó. */
@Composable
private fun ColumnScope.KetNoiTiviNay(vm: PhienLamViec) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OIcon(Icons.Rounded.Tv, 64.dp)
        Text(if (vm.dangKhoiPhuc) t("Kết nối lại để khôi phục tivi này", "Reconnect to restore this TV") else t("Dọn chính tivi này", "Clean this TV"),
            fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center)
        Phu(t("TiviKit đang chạy ngay trên tivi, không cần điện thoại.", "TiviKit is running on the TV itself, no phone needed."))
    }
    The(t("Trước khi bấm", "Before you start"), Icons.Rounded.Warning) {
        BuocSo(1, t("Tivi đã bật Gỡ lỗi USB (Tùy chọn nhà phát triển).", "USB debugging is on (Developer options)."))
        BuocSo(2, t("Bấm Kết nối. Tivi hiện \"Cho phép gỡ lỗi USB?\": chọn Luôn cho phép rồi bấm Cho phép.", "Press Connect. When the TV asks \"Allow USB debugging?\", tick Always allow and press Allow."))
        BuocSo(3, t("Trong lúc dọn, màn hình có thể nháy sang Home vài giây rồi tự quay lại app.", "While it works the screen may jump to Home for a few seconds and then come back."))
    }
    NutChinh(t("Kết nối tivi này", "Connect this TV"), enabled = !vm.dangBan, icon = Icons.Rounded.Link) { vm.ketNoiTiviNay() }
    NutPhu(t("Hướng dẫn bật Gỡ lỗi USB có hình", "Picture guide to USB debugging"), Icons.Rounded.Warning) { vm.moHuongDan() }
    val ctxTv = androidx.compose.ui.platform.LocalContext.current
    NutPhu(t("Mở Tuỳ chọn nhà phát triển", "Open Developer options"), Icons.Rounded.Tune) {
        runCatching { ctxTv.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { ctxTv.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

// ---------------- Thông tin + chọn mẫu ----------------

@Composable
private fun ManThongTin(vm: PhienLamViec) = Cuon {
    val info = vm.thongTin ?: return@Cuon
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        OIcon(Icons.Rounded.Tv, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = Xanh); Spacer(Modifier.width(4.dp))
                Text(t("Đã kết nối", "Connected"), fontSize = 12.sp, color = Xanh)
            }
            Text(info.tenHienThi, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Android ${info.android}")
                vm.trangThai?.let { Chip(t("${it.caiDat.size} gói", "${it.caiDat.size} packages")) }
            }
        }
    }
    if (vm.khongPhaiTivi) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenLoi).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Icon(Icons.Rounded.Warning, null, tint = DoTuoi); Spacer(Modifier.width(8.dp))
            Phu(t("Máy này không khai là tivi Android (có thể là điện thoại hoặc máy tính bảng). TiviKit chỉ dọn tivi: tắt nhầm gói của điện thoại có thể làm máy không khởi động được.",
                "This device does not report itself as an Android TV (it may be a phone or tablet). TiviKit only cleans TVs: disabling a phone's packages can stop it from booting."), ChuChinh)
        }
        Hang(t("Tôi chắc đây là tivi / hộp tivi / máy chiếu", "I am sure this is a TV / TV box / projector"),
            t("Chỉ bật nếu đúng là thiết bị xem tivi", "Only if it really is a TV device"), vm.xacNhanLaTivi) { vm.xacNhanLaTivi = it }
    }
    if (vm.laAndroidCu && !vm.laFireTv) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Phu(t("Máy này chạy Android ${vm.thongTin?.android}: TiviKit dọn được quảng cáo, theo dõi và app rác, nhưng KHÔNG cài được Home sạch (cần Android 8 trở lên).",
            "This device runs Android ${vm.thongTin?.android}: TiviKit can clean ads, tracking and junk apps but can NOT install the clean Home (needs Android 8 or later)."))
    }
    if (vm.laFireTv) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Phu(t("Fire TV: TiviKit dọn được quảng cáo, theo dõi và app rác, nhưng KHÔNG thay được màn Home. Amazon khoá Home cho launcher của họ, không app nào thay được.",
            "Fire TV: TiviKit can clean ads, tracking and junk apps but can NOT replace Home. Amazon locks Home to its own launcher; no app can change it."))
    }
    if (info.adbSecure == "0") Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Phu(t("Tivi mở ADB không cần xác nhận: ai cùng mạng cũng điều khiển được. Nhớ tắt ADB khi xong.",
            "This TV accepts ADB without a prompt: anyone on the network can control it. Turn ADB off when done."), ChuChinh)
    }
    vm.saoLuu?.let {
        Phu(t("Tivi này đã có bản sao lưu từ ${ngay(it.luc)}", "A backup from ${ngay(it.luc)} exists") +
            (if (vm.saoLuuTuTv) t(" (lấy từ bản lưu trên tivi)", " (read from the copy on the TV)") else "") +
            t(". Làm tiếp vẫn giữ mốc khôi phục cũ.", ". Continuing keeps that restore point."))
    }
    The(t("Danh sách mẫu", "Model list"), Icons.Rounded.FactCheck) {
        if (vm.ungVien.isEmpty()) Phu(t("Chưa có mẫu nào khớp đúng tivi này. App dùng luật chung: chỉ tích sẵn gói đã tắt trên nhiều tivi khác và không ai ghi là phải giữ.",
            "No tested list matches this TV. The app uses general rules: it pre-ticks only packages disabled on many other TVs that nobody marked as needed."))
        vm.ungVien.forEach { (m, diem) ->
            LuaChon(vm.mauChon == m, m.ten.take(90), "${m.goi.size} " + t("gói", "packages") + " · " + t("độ khớp", "match") + " $diem") { vm.mauChon = m }
        }
        if (vm.ganGiong.isNotEmpty()) {
            Phu(t("Mẫu gần giống (cùng hãng, tên máy khác một chút). Chọn nếu đúng dòng tivi của bạn:", "Close matches (same brand, slightly different model name). Pick one if it is your TV's line:"))
            vm.ganGiong.forEach { m -> LuaChon(vm.mauChon == m, m.ten.take(90), "${m.goi.size} " + t("gói", "packages") + " · " + t("gần giống", "close match")) { vm.mauChon = m } }
        }
        // Mẫu chọn tay từ hộp "Chọn mẫu khác" (không nằm trong danh sách trên) vẫn hiện để thấy đang dùng mẫu nào.
        vm.mauChon?.takeIf { c -> vm.ungVien.none { it.first == c } && c !in vm.ganGiong }?.let { m ->
            LuaChon(true, m.ten.take(90), "${m.goi.size} " + t("gói", "packages") + " · " + t("bạn chọn", "your pick")) {}
        }
        LuaChon(vm.mauChon == null, t("Không dùng mẫu, chỉ luật chung", "No list, general rules only"), null) { vm.mauChon = null }
        NutPhu(t("Chọn mẫu khác (${vm.tatCaMau.size} mẫu)", "Pick another list (${vm.tatCaMau.size})"), Icons.Rounded.Search) { vm.moChonMau = true }
    }
    if (vm.moChonMau) HopChonMau(vm)
    TheMayChieu(vm)
    NutChinh(t("Xem danh sách gói", "See packages"), enabled = !vm.dangBan && !vm.chanKhongPhaiTivi, icon = Icons.AutoMirrored.Rounded.ArrowForward) { vm.lapKeHoach() }
}

@Composable
internal fun LuaChon(chon: Boolean, chu: String, phu: String?, onClick: () -> Unit) {
    // Chọn một trong nhiều kiểu danh sách iPhone: dòng đang chọn tô nhạt màu nhấn, dấu ✓ bên phải.
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (chon) Nhan.copy(alpha = 0.12f) else The2)
        .selectable(chon, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(chu, fontSize = 15.sp, fontWeight = if (chon) FontWeight.SemiBold else FontWeight.Normal)
            if (phu != null) Phu(phu)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (chon) Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = Nhan)
        }
    }
}

// ---------------- Chọn gói ----------------

@Composable
private fun ManChonGoi(vm: PhienLamViec) {
    val nhom = vm.muc.groupBy { it.loai }
    val so = vm.soDaChon()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$so", fontWeight = FontWeight.Bold, fontSize = 40.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(t("gói sẽ tắt", "to disable") + " / ${vm.muc.size}", color = ChuPhu, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    LinearProgressIndicator(progress = { if (vm.muc.isEmpty()) 0f else so.toFloat() / vm.muc.size },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Nhan, trackColor = NenO, gapSize = 0.dp, drawStopIndicator = {})
                    Phu(t("Đã tích sẵn gói an toàn. App có biểu tượng thì hỏi chủ nhà. Gói tivi cần (HDMI, remote, bàn phím, CH Play) app tự giữ, không hiện ở đây.",
                        "Safe packages are pre-ticked. Ask the owner about apps with icons. Packages the TV needs (HDMI, remote, keyboard, Play Store) are kept and not listed."))
                }
            }
            LoaiMuc.entries.forEach { loai ->
                val ds = nhom[loai].orEmpty()
                if (ds.isEmpty()) return@forEach
                item(key = "h-$loai") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(loai.tieuDe, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Phu(loai.moTa)
                        }
                        val tatCa = ds.all { vm.chon[it.pkg] == true }
                        TextButton(onClick = { ds.forEach { vm.chon[it.pkg] = !tatCa } }) {
                            Text(if (tatCa) t("Bỏ hết", "None") else t("Chọn hết", "All"))
                        }
                    }
                }
                items(ds, key = { it.pkg }) { m -> DongGoi(m, vm.chon[m.pkg] == true) { vm.chon[m.pkg] = it } }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp))
                    .toggleable(vm.epSetupwraith, role = androidx.compose.ui.semantics.Role.Checkbox) { vm.epSetupwraith = it }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OTich(vm.epSetupwraith)
                    Spacer(Modifier.width(10.dp))
                    Phu(t("Tivi cắm dây LAN (cho tắt setupwraith dù nó giữ Wi-Fi)", "TV is on Ethernet (allow disabling setupwraith even if it owns the Wi-Fi)"))
                }
            }
        }
        var hoi by remember { mutableStateOf(false) }
        Column(Modifier.background(Nen).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NutChinh(t("Sao lưu & dọn $so gói", "Back up & clean $so packages"), enabled = !vm.dangBan && so > 0, icon = Icons.Rounded.CleaningServices) { hoi = true }
            NutXemLenh { vm.lenhDon() }
            if (!vm.khongCaiHome) NutPhu(if (vm.daCoHomeSach) t("Bỏ qua, chỉ đổi giao diện", "Skip, just change the theme") else t("Bỏ qua, chỉ cài Home sạch", "Skip, just install the clean Home"),
                Icons.Rounded.Home, enabled = !vm.dangBan) { vm.boQuaDon() }
        }
        val nenGiu = vm.muc.filter { vm.chon[it.pkg] == true && KeHoach.nenGiu(it) }
        if (hoi && nenGiu.isNotEmpty()) AlertDialog(
            onDismissRequest = { hoi = false }, containerColor = The1,
            icon = { Icon(Icons.Rounded.Warning, null, tint = Cam) },
            title = { Text(t("Khoan đã: ${nenGiu.size} gói nhiều tivi phải giữ", "Wait: ${nenGiu.size} packages most TVs keep")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("Bạn tự tích các gói dưới đây, nhưng nhiều danh sách đã thử ghi là PHẢI GIỮ (tắt có thể mất nút Input, HDMI, remote, Cài đặt…):",
                        "You ticked these, but many tested lists say KEEP them (disabling can break the Input key, HDMI, the remote, Settings…):"))
                    nenGiu.take(8).forEach { Text("• ${tenDep(it.pkg)} (${it.pkg}) — " + t("${it.soMauGiu} danh sách giữ", "kept by ${it.soMauGiu} lists"), fontSize = 13.sp, color = ChuPhu) }
                    if (nenGiu.size > 8) Text(t("… và ${nenGiu.size - 8} gói nữa", "… and ${nenGiu.size - 8} more"), fontSize = 13.sp, color = ChuPhu)
                }
            },
            confirmButton = { TextButton(onClick = { nenGiu.forEach { vm.chon[it.pkg] = false }; hoi = false; vm.batDauTat() }) { Text(t("Bỏ tích các gói này rồi dọn", "Untick them and clean"), fontWeight = FontWeight.SemiBold) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { hoi = false; vm.batDauTat() }) { Text(t("Vẫn tắt cả", "Disable all anyway"), color = Cam) }
                    TextButton(onClick = { hoi = false }) { Text(t("Thôi", "Cancel")) }
                }
            },
        )
        else if (hoi) AlertDialog(
            onDismissRequest = { hoi = false }, containerColor = The1,
            icon = { Icon(Icons.Rounded.Shield, null, tint = Nhan) },
            title = { Text(t("Dọn $so gói?", "Clean $so packages?")) },
            text = { Text(t("App lưu trạng thái tivi vào điện thoại trước, rồi tắt từng gói (không gỡ). Khôi phục được bất cứ lúc nào.",
                "The app saves the TV's state to this phone first, then disables each package (nothing uninstalled). You can restore any time.")) },
            confirmButton = { TextButton(onClick = { hoi = false; vm.batDauTat() }) { Text(t("Dọn", "Clean")) } },
            dismissButton = { TextButton(onClick = { hoi = false }) { Text(t("Thôi", "Cancel")) } },
        )
    }
}

@Composable
private fun DongGoi(m: MucGoi, chon: Boolean, doi: (Boolean) -> Unit) {
    val ten = tenDep(m.pkg)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1)
        .toggleable(chon, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = doi).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(23)).background(if (chon) Nhan.copy(alpha = 0.14f) else The2), contentAlignment = Alignment.Center) {
            Text(ten.take(1).uppercase(), fontWeight = FontWeight.Bold, color = if (chon) Nhan else ChuPhu)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ten, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Text(m.pkg, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ChuPhu, maxLines = 1)
            val ghi = m.ghiChu.ifBlank { m.nhom }
            if (ghi.isNotBlank()) Text(ghi, fontSize = 12.sp, color = ChuPhu, maxLines = 2, lineHeight = 16.sp)
            if (m.soMauTat > 0 || m.soMauGiu > 0) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m.soMauTat > 0) Chip(t("tắt trên ${m.soMauTat} mẫu", "off on ${m.soMauTat} models"))
                if (m.soMauGiu > 0) Chip(t("⚠ ${m.soMauGiu} mẫu nên giữ", "⚠ kept on ${m.soMauGiu}"), Cam)
            }
        }
        Spacer(Modifier.width(10.dp))
        OTich(chon)
    }
}

// ---------------- Đang dọn / kết quả ----------------

@Composable
private fun ManDangTat(vm: PhienLamViec) = Cuon {
    val kq = vm.ketQua
    if (kq == null) {
        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                val p = vm.tienDo
                if (p <= 0f) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 10.dp, color = Nhan, trackColor = NenO, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                else CircularProgressIndicator(progress = { p }, modifier = Modifier.fillMaxSize(), strokeWidth = 10.dp, color = Nhan, trackColor = NenO,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp)
                Text(if (p <= 0f) "…" else "${(p * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.6).sp)
            }
            Text(t("Đang dọn tivi", "Cleaning the TV"), fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.4).sp)
            Text(vm.thongBao, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ChuPhu, textAlign = TextAlign.Center, maxLines = 2)
            Phu(t("Đừng tắt tivi hay đổi Wi-Fi lúc này.", "Don't turn off the TV or change Wi-Fi now."))
        }
    } else {
        DauTrang({
            Box(Modifier.size(84.dp).clip(CircleShape).background(Xanh.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(52.dp), tint = Xanh)
            }
        }, t("Đã dọn ${kq.daTat.size} gói", "Cleaned ${kq.daTat.size} packages"), null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Chip(t("${kq.boQua.size} giữ lại", "${kq.boQua.size} kept"), Cam)
            Spacer(Modifier.width(8.dp))
            Chip(t("${kq.loi.size} lỗi", "${kq.loi.size} failed"), if (kq.loi.isEmpty()) ChuPhu else DoTuoi)
        }
        if (kq.boQua.isNotEmpty()) Gap(t("Giữ lại để an toàn (${kq.boQua.size})", "Kept for safety (${kq.boQua.size})"), Icons.Rounded.Shield, moSan = true) {
            kq.boQua.forEach { (p, ly) -> Column { Text(tenDep(p), fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Phu(ly) } }
        }
        if (kq.loi.isNotEmpty()) Gap(t("Không tắt được (${kq.loi.size})", "Could not disable (${kq.loi.size})"), Icons.Rounded.ErrorOutline) {
            kq.loi.forEach { (p, ly) -> Text("$p: $ly", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
            Phu(t("Thường là gói hệ thống được bảo vệ, bỏ qua được.", "Usually protected system packages; safe to ignore."))
        }
        The(t("Thử trên tivi", "Check on the TV"), Icons.Rounded.Tv) {
            Phu(t("Bấm Home, mở YouTube/Netflix, chuyển HDMI, mở Cài đặt, thử remote và giọng nói.", "Press Home, open YouTube/Netflix, switch HDMI, open Settings, try the remote and voice."))
            NutPhu(t("Xem màn hình tivi", "View TV screen"), Icons.Rounded.Screenshot) { vm.chupManHinh() }
        }
        if (vm.khongCaiHome) NutChinh(if (vm.laFireTv) t("Xong (Fire TV không thay được Home)", "Done (Fire TV can't change Home)") else t("Xong (máy này không cài được Home sạch)", "Done (no clean Home on this device)"), icon = Icons.Rounded.CheckCircle) { vm.man = Man.XONG }
        else {
            NutChinh(t("Tiếp: Home sạch", "Next: clean Home"), icon = Icons.Rounded.Home) { vm.man = Man.HOME_SACH }
            NutPhu(t("Bỏ qua Home sạch", "Skip clean Home")) { vm.man = Man.XONG }
        }
        NutGopY(vm)
    }
}

// ---------------- Home sạch ----------------

/**
 * Màn Home sạch / Đổi giao diện (làm lại 23/10/2026 theo bản phác anh Alex duyệt): ảnh xem trước ở đầu, ba thẻ
 * Giao diện · Home · Tuỳ chỉnh, nút Áp dụng dính đáy; Nâng cao là màn riêng (ManNangCao).
 */
@Composable
private fun ManHomeSach(vm: PhienLamViec) {
    if (vm.moNangCao) { ManNangCao(vm); return }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            XemTruocHome(vm)
            ChonDoan(listOf(t("Giao diện", "Look"), t("Home", "Home"), t("Tuỳ chỉnh", "Options")), vm.theHome) { vm.theHome = it }
            when (vm.theHome) {
                0 -> TheGiaoDien(vm)
                1 -> TheHomeNoiDung(vm)
                else -> TheTuyChinh(vm)
            }
            vm.nhatKy.takeLast(4).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
        }
        // Nút dính đáy: đổi thẻ nào cũng bấm được, không phải cuộn hết trang.
        Column(Modifier.fillMaxWidth().background(The1).padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (vm.daCoHomeSach) NutChinh(if (vm.dangBan) t("Đang áp dụng…", "Applying…") else t("Áp dụng lên tivi", "Apply to the TV"),
                enabled = !vm.dangBan, icon = Icons.Rounded.Palette) { vm.apDungTheme() }
            else NutChinh(if (vm.dangBan) t("Đang cài…", "Installing…") else t("Cài Home sạch", "Install clean Home"), enabled = !vm.dangBan, icon = Icons.Rounded.Home) { vm.caiHomeSach() }
            NutXemLenh { vm.lenhHome() }
        }
    }
}

/** Các thẻ như ô chia đoạn (segmented control) của iOS: rãnh xám, đoạn đang chọn nổi lên. */
@Composable
internal fun ChonDoan(ds: List<String>, chon: Int, doi: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NenO).padding(2.dp)) {
        ds.forEachIndexed { i, ten ->
            val dangChon = chon == i
            Text(ten, fontSize = 14.sp, fontWeight = if (dangChon) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).then(if (dangChon) Modifier.shadow(1.dp, RoundedCornerShape(8.dp)) else Modifier)
                    .clip(RoundedCornerShape(8.dp)).background(if (dangChon) LocalMau.current.doanChon else Color.Transparent)
                    .clickable { doi(i) }.padding(vertical = 8.dp))
        }
    }
}

/**
 * Ảnh xem trước Home: ảnh riêng / ảnh theme / dải màu, đồng hồ, dòng âm lịch + thời tiết, dock giả. Ảnh riêng vẽ phần
 * ảnh gốc trong khung cắt; đang canh ảnh (25/10/2026) thì kéo để dời, chụm để phóng, kèm lưới chia ba.
 */
@Composable
private fun XemTruocHome(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val th = vm.themeChon
    val goc = vm.anhGocRieng
    // Bản nhỏ của ảnh gốc (rộng ~1280) cùng cỡ thật: kéo / phóng chỉ vẽ lại phần trong khung, không cắt lại ảnh.
    val xemGoc = remember(goc) {
        goc?.let { g ->
            runCatching {
                val bien = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(g, 0, g.size, bien)
                val bm = android.graphics.BitmapFactory.decodeByteArray(g, 0, g.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = maxOf(1, bien.outWidth / 1280) })
                Triple(bm.asImageBitmap(), bien.outWidth, bien.outHeight)
            }.getOrNull()
        }
    }
    val anh = remember(th.id) {
        runCatching {
            (th.anhNen ?: th.anhXem)?.let { a -> ctx.assets.open(a).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) } }
        }.getOrNull()?.asImageBitmap()
    }
    val canh = vm.dangCanhAnh && xemGoc != null
    val nhan = Nhan
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(th.mau.map { Color(it) }))
        .border(if (canh) 2.dp else 1.dp, if (canh) nhan else Vien, RoundedCornerShape(16.dp))
        .then(if (canh) Modifier.pointerInput(goc) {
            val (_, w, h) = xemGoc ?: return@pointerInput
            detectTransformGestures { tam, keo, phong, _ ->
                var k = vm.khungAnh
                if (phong != 1f) k = vn.tivikit.dulieu.AnhNen.phong(w, h, k, phong, tam.x, tam.y, size.width.toFloat())
                vm.doiKhungAnh(vn.tivikit.dulieu.AnhNen.keo(w, h, k, keo.x, keo.y, size.width.toFloat()))
            }
        } else Modifier)) {
        if (xemGoc != null) {
            val (bm, w, h) = xemGoc
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val v = vn.tivikit.dulieu.AnhNen.vung(w, h, vm.khungAnh)
                val ty = w.toFloat() / bm.width // điểm ảnh gốc trên một điểm ảnh bản nhỏ
                val sx = (v[0] / ty).toInt().coerceIn(0, bm.width - 1); val sy = (v[1] / ty).toInt().coerceIn(0, bm.height - 1)
                val sw = (v[2] / ty).toInt().coerceIn(1, bm.width - sx); val sh = (v[3] / ty).toInt().coerceIn(1, bm.height - sy)
                drawImage(bm, androidx.compose.ui.unit.IntOffset(sx, sy), androidx.compose.ui.unit.IntSize(sw, sh),
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
            }
        } else if (anh != null) Image(anh, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        if (vm.lamToiNen > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = vm.lamToiNen / 100f)))
        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            if (vm.tenChao.isNotBlank()) Text(t("XIN CHÀO, ", "HELLO, ") + vm.tenChao.uppercase(), fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            Text(java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date()), fontSize = 26.sp, fontWeight = FontWeight.Light, color = Color.White)
            val dong = listOfNotNull(if (vm.amLich) t("âm lịch", "lunar date") else null, vm.thanhPho?.ten).joinToString(" · ")
            if (dong.isNotEmpty()) Text(dong, fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(5) { i -> Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(5.dp)).background(if (i == 0) Color.White else Color.White.copy(alpha = if (i < vm.dock.size) 0.6f else 0.25f))) }
        }
        // Đang canh: lưới chia ba để đặt mặt người, chữ trong ảnh tránh đồng hồ và dock.
        if (canh) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val net = Color.White.copy(alpha = 0.5f)
            for (i in 1..2) {
                drawLine(net, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.dp.toPx())
                drawLine(net, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.dp.toPx())
            }
        }
    }
    Phu(t(th.tenVi, th.tenEn) + " · " + t(th.moTaVi, th.moTaEn) + if (goc != null) t(" · ảnh của bạn", " · your picture") else "")
}

@Composable
private fun TheGiaoDien(vm: PhienLamViec) {
    The {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            THEMES.forEach { th -> TheTheme(th, th == vm.themeChon) { vm.chonTheme(th) } }
        }
        // Ảnh của chính mình làm hình nền (góp ý 23/10/2026): thay ảnh / bộ ảnh của theme, giữ màu và hiệu ứng theme.
        val chonAnh = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) vm.chonAnhNen(uri)
        }
        // Chạy CleanTV ngay trên tivi: thường không có trình chọn ảnh (TCL báo "không có ứng dụng phù hợp", 24/10/2026)
        // → chọn ảnh có sẵn trên tivi / USB qua shell.
        val trenTv = vn.tivikit.ManHinh.laTv || vm.ip.trim() == "127.0.0.1"
        // Vừa khung, không cắt (CleanTV 3.0.0): chọn trước hoặc sau khi chọn ảnh đều được.
        Hang(t("Ảnh riêng vừa khung, không cắt", "Fit your picture, no cropping"),
            t("Cả ảnh nằm trọn trên màn, hai bên là chính ảnh làm mờ (hợp ảnh dọc)", "The whole picture fits, with a blurred copy at the sides (good for portrait shots)"),
            vm.anhVuaKhung) { vm.doiVuaKhung(it) }
        if (vm.anhNenRieng == null) {
            if (!trenTv) NutPhu(t("Dùng ảnh của bạn làm hình nền", "Use your own picture as wallpaper"), Icons.Rounded.Image) {
                runCatching { chonAnh.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .onFailure { vm.moAnhTrenTv() }
            }
            NutPhu(t("Chọn ảnh có trên tivi / USB", "Pick a picture on the TV / USB"), Icons.Rounded.Image) { vm.moAnhTrenTv() }
        }
        vm.anhTrenTv?.let { ds ->
            AlertDialog(
                onDismissRequest = { vm.anhTrenTv = null }, containerColor = The1,
                title = { Text(t("Ảnh trên tivi / USB", "Pictures on the TV / USB")) },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        if (ds.isEmpty()) Phu(t("Không thấy ảnh nào trong Download, Pictures, DCIM hay USB. Chép ảnh JPG vào USB rồi cắm vào tivi, hoặc tải ảnh về tivi.",
                            "No pictures in Download, Pictures, DCIM or on USB. Copy a JPG to a USB stick and plug it into the TV, or download one to the TV."))
                        ds.forEach { f ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.chonAnhTrenTv(f) }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                                Text(f.substringAfterLast('/'), fontSize = 14.sp, maxLines = 1)
                                Text(f.substringBeforeLast('/'), fontSize = 11.sp, color = ChuPhu, maxLines = 1)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { vm.anhTrenTv = null }) { Text(t("Đóng", "Close")) } },
            )
        }
        vm.anhNenRieng?.let { rieng ->
            // Canh ảnh (25/10/2026, học LG TV Vibehack): chọn phần ảnh nằm trong khung tivi thay vì luôn cắt giữa.
            if (vm.dangCanhAnh) {
                Phu(t("Kéo ảnh trong khung xem trước để dời, chụm hai ngón để phóng to (tới 3 lần). Dock che phần dưới: để mặt người, chữ trong ảnh ở khoảng 1/3 phía trên.",
                    "Drag the picture in the preview to move it, pinch to zoom (up to 3×). The dock covers the bottom: keep faces and text in the upper third."), Nhan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChonChip(t("Canh giữa", "Center"), false) { vm.doiKhungAnh(vn.tivikit.dulieu.AnhNen.Khung()) }
                    ChonChip(t("Xong", "Done"), true) { vm.dangCanhAnh = false }
                }
            } else if (!vm.anhVuaKhung) NutPhu(t("Canh ảnh: dời, phóng to", "Position the picture: move, zoom"), Icons.Rounded.Crop) { vm.dangCanhAnh = true }
            NutPhu(t("Bỏ ảnh riêng (${rieng.size / 1024} KB), dùng ảnh của theme", "Remove your picture (${rieng.size / 1024} KB)"), Icons.Rounded.Close) { vm.boAnhRieng() }
        }
        Text(t("Làm tối hình nền", "Dim the wallpaper"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 20, 40, 60).forEach { p -> ChonChip(if (p == 0) t("Không", "None") else "$p%", vm.lamToiNen == p) { vm.lamToiNen = p } }
        }
    }
    if (vm.dsNangCao.isNotEmpty()) {
        val daDat = vm.nangCao.count { it.value.isNotBlank() }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1)
            .clickable { vm.moNangCao = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OIcon(Icons.Rounded.Tune, 32.dp, MauO.xam)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Nâng cao · ${vm.dsNangCao.size} tuỳ chọn", "Advanced · ${vm.dsNangCao.size} options"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Phu(if (daDat > 0) t("$daDat mục đã đổi", "$daDat changed") else t("Cho người thích tỉ mỉ: hiệu ứng, lưới app, remote…", "For tinkerers: effects, grid, remote…"))
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp), tint = ChuPhu)
        }
    }
}

@Composable
private fun TheHomeNoiDung(vm: PhienLamViec) {
    The(t("Dock (tối đa $DOCK_TOI_DA)", "Dock (up to $DOCK_TOI_DA)"), Icons.Rounded.Apps) {
        // Thứ tự trên dock, dời bằng ↑ ↓ (góp ý daohoangthong1 23/10/2026: không biết cách đổi thứ tự).
        if (vm.dock.isNotEmpty()) {
            Text(t("Thứ tự trên dock", "Dock order"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            vm.dock.toList().forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nhan.copy(alpha = 0.15f)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Nhan, modifier = Modifier.width(22.dp))
                    Text(tenDep(p), fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { vm.dichDock(p, -1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, t("Lên", "Up")) }
                    IconButton(onClick = { vm.dichDock(p, 1) }, enabled = i < vm.dock.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, t("Xuống", "Down")) }
                }
            }
            Text(t("Chọn app cho dock", "Pick dock apps"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        }
        vm.appChoDock().forEach { p ->
            val viTri = vm.dock.indexOf(p)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (viTri >= 0) Nhan.copy(alpha = 0.12f) else The2)
                .clickable { vm.doiDock(p) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // Số thứ tự trên dock trong vòng đặc màu nhấn; chưa chọn thì vòng xám rỗng như ô tích.
                if (viTri >= 0) Box(Modifier.size(28.dp).clip(CircleShape).background(Nhan), contentAlignment = Alignment.Center) {
                    Text("${viTri + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                } else Box(Modifier.size(28.dp).border(1.5.dp, ChuPhu.copy(alpha = 0.55f), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tenDep(p), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(p, fontSize = 11.sp, color = ChuPhu, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }
        }
    }
    // Ô kênh truyền hình (TV360, VTVgo) chọn ở màn Kênh nhanh (26/10/2026: gom một chỗ); ở đây chỉ trỏ sang.
    The(t("Kênh truyền hình trên màn chính", "TV channels on Home"), Icons.Rounded.LiveTv) {
        Phu(t("Ô kênh mở thẳng VTV1, HTV7… trên TV360 hoặc VTVgo chọn ở màn Kênh nhanh.",
            "Tiles that open VTV1, HTV7… straight in TV360 or VTVgo are set up under Quick channel."))
        NutPhu(t("Mở Kênh nhanh", "Open Quick channel"), Icons.Rounded.LiveTv, enabled = !vm.dangBan) { vm.kenhNhanh.mo() }
    }
    The(t("Thời tiết", "Weather"), Icons.Rounded.WbSunny) {
        OutlinedTextField(vm.timXa, { vm.doiTimXa(it) }, label = { Text(t("Tìm phường / xã hoặc thành phố", "Find your ward or city")) },
            placeholder = { Text(t("vd: Bến Thành, Cửa Lò, Bali, Osaka", "e.g. Ben Thanh, Bali, Osaka")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        vm.ketQuaXa.forEach { x ->
            Text("${x.ten} · ${x.tinh}", fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .clickable { vm.chonXa(x) }.padding(horizontal = 12.dp, vertical = 10.dp))
        }
        Phu(t("Hoặc chọn nhanh thành phố:", "Or pick a city:"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Không hiện", "Off"), vm.thanhPho == null) { vm.thanhPho = null }
            vm.thanhPho?.takeIf { it !in THANH_PHO }?.let { tp -> ChonChip(tp.ten, true) { } }
            THANH_PHO.forEach { tp -> ChonChip(tp.ten, vm.thanhPho == tp) { vm.thanhPho = tp } }
        }
        Phu(t("Dữ liệu thời tiết: Open-Meteo.com (CC BY 4.0), tivi tự lấy nửa giờ một lần. Toạ độ phường/xã © OpenStreetMap.", "Weather data: Open-Meteo.com (CC BY 4.0), refreshed by the TV every 30 minutes. Ward locations © OpenStreetMap."))
    }
    The(t("Đồng hồ & lời chào", "Clock & greeting"), Icons.Rounded.Schedule) {
        OutlinedTextField(vm.tenChao, { vm.tenChao = it }, label = { Text(t("Tên chào (vd: Nhà bác Hùng)", "Greeting name (e.g. The Smiths)")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Hang(t("Chữ giờ, ngày trắng sáng", "Bright white clock and date"), t("Dòng ngày tháng trắng hẳn thay vì xám nhạt, dễ đọc từ xa", "The date line in full white instead of soft grey, easier to read from afar"), vm.chuTrang) { vm.chuTrang = it }
        Hang(t("Ẩn lời chào", "Hide the greeting"), t("Bỏ dòng \"Chào buổi tối\" phía trên đồng hồ", "Drops the \"Good evening\" line above the clock"), vm.anLoiChao) { vm.anLoiChao = it }
        Hang(t("Âm lịch dưới ngày dương", "Lunar date under the date"), t("Ví dụ: 2/9 âm lịch · Bính Ngọ", "e.g. Lunar 2/9 · Year of the Horse"), vm.amLich) { vm.amLich = it }
        Text(t("Đồng hồ khi để yên", "Clock when idle"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 5, 15, 30).forEach { p -> ChonChip(if (p == 0) t("Tắt", "Off") else t("$p phút", "$p min"), vm.dongHoKhiYen == p) { vm.dongHoKhiYen = p } }
        }
    }
    TheMayChieu(vm)
}

@Composable
private fun TheTuyChinh(vm: PhienLamViec) {
    The(t("Hiển thị", "Display"), Icons.Rounded.Visibility) {
        Hang(t("Tên app to phía trên dock", "Large app name above the dock"), t("Tắt để Home gọn hơn, chỉ còn viền sáng", "Turn off for a calmer Home, just the glow"), vm.tenToTrenDock) { vm.tenToTrenDock = it }
        Hang(t("Hiện tên dưới mọi app", "Names under every app"), t("Ông bà dễ nhận app hơn", "Easier to recognise apps"), vm.tenDuoiApp) { vm.tenDuoiApp = it }
        Hang(t("Tô màu ô app điện thoại", "Colour phone-app tiles"), t("App không có ảnh tivi (Chrome, Thư viện…) có ô màu theo icon thay vì nền xám", "Apps without a TV banner get a tile in their icon's colour"), vm.toMauOApp) { vm.toMauOApp = it }
        Hang(t("Tất cả app một hàng", "Every app in one row"), t("Một hàng cuộn ngang, không còn lưới bên dưới", "One sideways-scrolling row, no grid below"), vm.motHang) { vm.motHang = it }
        Hang(t("Ô icon đều nhau", "Uniform icon tiles"), t("Mọi ô là icon trên nền màu của app. Tắt để dùng lại ảnh banner của app (kể cả với giao diện Kính mờ)", "Every tile is the icon on its own colour. Turn off for the apps' banners (the Frosted look too)"), vm.oDeuNhau) { vm.oDeuNhau = it }
        Hang(t("Gom nút trên cùng thành icon", "Compact icon buttons"), t("Một cụm gọn, bỏ chữ", "One tidy cluster, no labels"), vm.gonNut) { vm.gonNut = it }
        Text(t("Cỡ chữ đồng hồ, lịch, thời tiết", "Clock, date and weather size"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100 to t("Vừa", "Normal"), 125 to t("Lớn", "Large"), 150 to t("Rất lớn", "Extra large")).forEach { (v, chu) ->
                ChonChip(chu, vm.coChuDau == v) { vm.coChuDau = v }
            }
        }
        Text(t("Số app mỗi hàng", "Apps per row"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to t("Theo theme", "Theme's"), 4 to "4", 5 to "5", 6 to "6", 7 to "7").forEach { (v, chu) ->
                ChonChip(chu, vm.soCot == v) { vm.soCot = v }
            }
        }
        Text(t("Vị trí hàng app", "App rows position"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(HANG_GIUA to t("Giữa màn hình", "Middle"), -120 to t("Cao hơn nhiều", "Much higher"), -60 to t("Cao hơn", "Higher"), 0 to t("Mặc định", "Default"), 60 to t("Thấp hơn", "Lower"), 300 to t("Thấp nhất", "Lowest")).forEach { (v, chu) ->
                ChonChip(chu, vm.doiHang == v) { vm.doiHang = v }
            }
        }
        Hang(t("Mở một app khi bật tivi", "Open an app at power-on"), t("Bật, Áp dụng lên tivi, rồi bấm nút bên dưới để chọn app ngay trên tivi", "Turn on, apply to the TV, then use the button below to pick the app on the TV"), vm.moKhiBat) { vm.moKhiBat = it }
        Hang(t("Luôn mở Home sạch khi bật tivi", "Always open the clean Home at power-on"),
            t("Cho tivi tự mở lại Home gốc, HDMI hay app cũ mỗi lần bật: khoảng 1 giây sau Home sạch hiện lên", "For TVs that come back to the stock Home, HDMI or the last app: the clean Home comes up a second after power-on"),
            vm.luonVeHome) { vm.luonVeHome = it }
        if (vm.moKhiBat) Hang(t("Cả khi bật lại từ chế độ chờ", "Also when waking from standby"),
            t("Bấm nút nguồn remote để bật là mở app luôn, không chỉ lúc khởi động lại tivi", "Turning the TV on with the remote opens the app too, not only after a restart"),
            vm.moKhiThuc) { vm.moKhiThuc = it }
        if (vm.moKhiBat) NutPhu(t("Mở màn chọn app trên tivi", "Open the app picker on the TV"), Icons.Rounded.Tv) { vm.moChonAppKhiBat() }
        if (vm.moKhiBat && vm.baoChonApp.isNotEmpty()) Phu(vm.baoChonApp, Xanh)
        Hang(t("Nút Đang phát", "Now playing button"), t("Hiện khi có app đang phát nhạc / video: tạm dừng từng app hay dừng tất cả", "Shows while apps play: pause each or stop them all"), vm.nutDangPhat) { vm.nutDangPhat = it }
        Hang(t("Nút màn hình chờ", "Screensaver button"), t("Bấm là bật ảnh / đồng hồ, tiện khi nghe nhạc", "Starts the photo / clock screensaver, handy with music"), vm.nutManCho) { vm.nutManCho = it }
        Hang(t("Thư viện app", "App library"), t("Nút xem mọi app trên tivi, thêm / bớt khỏi Home", "A button listing every app; add or remove from Home"), vm.thuVienApp) { vm.thuVienApp = it }
        Hang(t("Hiện CPU / bộ nhớ", "Show CPU / memory"), t("Một dòng nhỏ dưới đồng hồ", "One small line under the clock"), vm.hienThongSo) { vm.hienThongSo = it }
    }
    The(t("Remote", "Remote"), Icons.Rounded.SettingsRemote) {
        Hang(t("Nút Input trên remote mở bảng chọn HDMI", "Remote Input button opens the input picker"), t("Nên bật cho Google TV", "Recommended on Google TV"), vm.nutInput) { vm.nutInput = it }
        Hang(t("Remote gửi phím Home lạ", "Remote sends an odd Home key"), t("Que Xiaomi, hộp nhà mạng…", "Xiaomi sticks, carrier boxes…"), vm.nutHome) { vm.nutHome = it }
        if (vm.phimRemote() != null) Hang(t("Nút app trên remote", "Remote app buttons"),
            if (vm.mauChon?.id == "tcl-g09-google-tv" || vm.laTclGoogleTv) t("YouTube, Netflix, Cài đặt… mở được khi Home gốc tắt. Trên máy này bật thì giữ nút âm lượng chỉ nhảy một nấc", "YouTube, Netflix, Settings… work with the stock Home off. On this set, holding volume then steps only once")
            else t("YouTube, Netflix, Cài đặt… vẫn mở được khi Home gốc tắt", "YouTube, Netflix, Settings… keep working with the stock Home off"),
            vm.nutAppRemote) { vm.nutAppRemoteChon = it }
        Hang(t("Giữ nút âm lượng để tăng / giảm liên tục", "Hold volume to keep raising / lowering"),
            t("Bật nếu giữ nút chỉ nhảy một nấc (hay gặp trên Sony). Đổi lại, nút Input / Apps / nút app trên remote về như tivi gốc.", "Turn on if holding volume only moves one step (common on Sony). In return the remote's Input / Apps / app buttons work as the TV's own.") +
                (if (vm.phimRieng.isNotEmpty()) t(" Đang có nút gán ở màn Nút remote: các nút đó cần Home sạch lọc phím nên công tắc này chưa có tác dụng.",
                    " Buttons are assigned under Remote buttons: they need the clean Home to filter keys, so this switch has no effect yet.") else ""),
            vm.giuAmLuong) { vm.giuAmLuong = it }
    }
    The(t("File cấu hình", "Settings file"), Icons.Rounded.Save) {
        Phu(t("Lưu mọi lựa chọn (theme, dock, thời tiết, tuỳ chỉnh, Nâng cao, ảnh nền riêng) ra một file để giữ lại hoặc mang sang tivi khác.",
            "Save every choice (theme, dock, weather, options, Advanced, your wallpaper) to a file to keep or use on another TV."))
        val luu = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.xuatCauHinh(uri) }
        val mo = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.nhapCauHinh(uri) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { NutPhu(t("Lưu ra file", "Save to file"), Icons.Rounded.Save) { luu.launch("cleantv-" + (vm.thongTin?.model ?: "tivi").replace(Regex("[^A-Za-z0-9]+"), "-") + ".json") } }
            Box(Modifier.weight(1f)) { NutPhu(t("Mở file", "Open file"), Icons.Rounded.FolderOpen) { mo.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) } }
        }
        if (vm.cauHinhBao.isNotEmpty()) Phu(vm.cauHinhBao, Xanh)
    }
    The(t("Tivi yếu & mạng", "Slow TVs & network"), Icons.Rounded.Speed) {
        Hang(t("Chế độ tivi yếu", "Slow TV mode"), t("Bớt hiệu ứng chuyển động cho tivi cũ, ít RAM", "Calmer motion for old, low-memory TVs"), vm.tiviYeu) { vm.tiviYeu = it }
        Hang(t("Hiệu ứng kính mờ", "Frosted glass effects"), t("Dock kính mờ, ánh sáng theo màu app", "Frosted dock, glow in the app's colour"), vm.hieuUngKinh) { vm.hieuUngKinh = it }
        Hang(t("DNS chặn quảng cáo (AdGuard)", "Ad-blocking DNS (AdGuard)"), t("Là cài đặt DNS của tivi, không phải app (xem ở Cài đặt → Mạng → DNS riêng tư). Không chặn được quảng cáo YouTube. Nhà đã chặn ở router (Pi-hole, AdGuard Home, VPN router) thì đừng bật: tivi sẽ đi vòng qua router", "A TV DNS setting, not an app (Settings → Network → Private DNS). Does not block YouTube ads. If your router already filters (Pi-hole, AdGuard Home, router VPN), leave it off: the TV would bypass the router"), vm.dnsChanQc) { vm.dnsChanQc = it }
    }
}

/** Nhóm của một khoá Nâng cao, theo thứ tự hiện. */
private val NHOM_NANG_CAO = listOf(
    Triple("hinh-nen", "Hình nền & màu" to "Wallpaper & colour", Regex("^(background_color|sky_intensity|wallpaper_.*|accent_color|festival.*|glow_style|ambient_glow)$")),
    Triple("dock", "Dock & lưới app" to "Dock & app grid", Regex("^(fill_dock|hidden_apps|columns|rows_below_dock|dock_.*|library_apps|app_library_selection|app_labels|app_banners|module_cards|reorder_button|tile_menu|fade_rows|focus_overshoot)$")),
    Triple("thanh-tren", "Thanh trên & lời chào" to "Top bar & greeting", Regex("^(show_.*|icon_only_.*|top_menu|launcher_settings_button|wifi_.*|greeting|shamsi_date|vpn_app)$")),
    Triple("dien-anh", "Home điện ảnh" to "Cinematic Home", Regex("^cinematic_.*$")),
    Triple("remote", "Remote, HDMI & app hãng" to "Remote, HDMI & maker apps", Regex("^(live_tv_.*|source_panel_.*|coocaa_sources|launch_actions|input_.*|settings_key_.*|restore_key_services|app_keys|key_filter|stock_home_redirect|boot_to_live_tv)$")),
)

/** Màn Nâng cao riêng: tìm, lọc Đã đổi / Cẩn thận, chia nhóm, chấm tím ở mục đã đổi. */
@Composable
private fun ManNangCao(vm: PhienLamViec) {
    var tim by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf(0) } // 0 tất cả, 1 đã đổi, 2 cẩn thận
    val daDat = vm.nangCao.count { it.value.isNotBlank() }
    val q = XaPhuong.boDau(tim.trim())
    val ds = vm.dsNangCao.filter { tc ->
        (q.isEmpty() || tc.khoa.contains(q) || XaPhuong.boDau(tc.ten).contains(q) || XaPhuong.boDau(tc.moTaVi).contains(q) || tc.moTa.lowercase().contains(q)) &&
            when (loc) { 1 -> !vm.nangCao[tc.khoa].isNullOrBlank(); 2 -> tc.canThan; else -> true }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Nâng cao", "Advanced"), style = KieuTieuDeLon, modifier = Modifier.weight(1f))
                if (daDat > 0) TextButton(onClick = { vm.nangCao.clear() }) { Text(t("Trả mặc định", "Reset all")) }
            }
            Phu(t("Để \"Mặc định\" là Home sạch tự quyết. Mục \"Cẩn thận\" đụng tới remote, HDMI, app của hãng: đặt sai có thể làm remote hoặc nguồn vào không chạy — bấm Khôi phục là về như cũ.",
                "\"Default\" lets the clean Home decide. \"Careful\" items touch the remote, HDMI and the maker's apps: a wrong value can break them — Restore puts everything back."))
            OutlinedTextField(tim, { tim = it }, label = { Text(t("Tìm: tên app, đồng hồ, hình nền…", "Search: app name, clock, wallpaper…")) }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Tất cả", "All"), loc == 0) { loc = 0 }
                ChonChip(t("Đã đổi ($daDat)", "Changed ($daDat)"), loc == 1) { loc = 1 }
                ChonChip(t("Cẩn thận", "Careful"), loc == 2) { loc = 2 }
            }
            val conLai = ds.toMutableList()
            (NHOM_NANG_CAO.map { (id, ten, re) -> id to ten to re } + listOf("khac" to ("Khác" to "Other") to Regex(".*"))).forEach { (dau, re) ->
                val (_, ten) = dau
                val nhom = conLai.filter { re.matches(it.khoa) }
                conLai.removeAll(nhom)
                if (nhom.isEmpty()) return@forEach
                Text("${t(ten.first, ten.second)} · ${nhom.size}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ChuPhu, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
                nhom.forEach { tc -> DongNangCao(tc, vm.nangCao[tc.khoa] ?: "") { v -> if (v.isBlank()) vm.nangCao.remove(tc.khoa) else vm.nangCao[tc.khoa] = v } }
            }
            if (ds.isEmpty()) Phu(t("Không có tuỳ chọn nào khớp.", "No matching options."))
        }
        Column(Modifier.fillMaxWidth().background(The1).padding(horizontal = 16.dp, vertical = 10.dp)) {
            NutChinh(t("Xong, quay lại", "Done"), icon = Icons.Rounded.Check) { vm.moNangCao = false }
        }
    }
}

@Composable
private fun DongNangCao(tc: vn.tivikit.dulieu.TuyChon, gt: String, doi: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The1).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (gt.isNotBlank()) { Box(Modifier.size(8.dp).clip(CircleShape).background(Nhan)); Spacer(Modifier.width(8.dp)) }
            Text(tc.tieuDe(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (gt.isNotBlank()) Nhan else ChuChinh, modifier = Modifier.weight(1f))
            if (tc.canThan) Text(t("Cẩn thận", "Careful"), fontSize = 11.sp, color = Cam, modifier = Modifier.clip(RoundedCornerShape(50)).background(Cam.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        // Khoá kỹ thuật vẫn hiện nhỏ (tra tài liệu CleanYourTV, hỏi trên nhóm) khi đã có tên tiếng Việt.
        if (tc.tieuDe() != tc.khoa) Text(tc.khoa, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = ChuPhu)
        Phu(tc.moTaHien())
        when (tc.kieu) {
            "bool" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Mặc định", "Default"), gt.isBlank()) { doi("") }
                ChonChip(t("Bật", "On"), gt == "true") { doi("true") }
                ChonChip(t("Tắt", "Off"), gt == "false") { doi("false") }
            }
            "chon" -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Mặc định", "Default"), gt.isBlank()) { doi("") }
                tc.chon.forEach { c -> ChonChip(tc.nhanChon(c), gt == c) { doi(c) } }
            }
            else -> OutlinedTextField(gt, { doi(it) }, singleLine = tc.kieu == "so" || tc.kieu == "chu",
                placeholder = { Text(when (tc.kieu) {
                    "so" -> t("Số từ ${tc.min} đến ${tc.max}", "${tc.min} to ${tc.max}")
                    "ds", "ds-so" -> t("Cách nhau bằng dấu phẩy", "Comma-separated")
                    else -> t("Để trống = mặc định", "Empty = default")
                }, fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = if (tc.kieu == "so") KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
        }
    }
}

@Composable
internal fun ChonChip(chu: String, chon: Boolean, onClick: () -> Unit) {
    // Viên chọn kiểu iOS: đang chọn đặc màu nhấn chữ trắng, chưa chọn nền xám trong.
    Text(chu, fontSize = 14.sp, fontWeight = if (chon) FontWeight.SemiBold else FontWeight.Medium, color = if (chon) Color.White else ChuChinh,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (chon) Nhan else NenO)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

/** Thẻ xem trước theme: ảnh nền thật (nếu có) hoặc dải màu, kèm dock giả. */
@Composable
private fun TheTheme(th: Theme, chon: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val anh = remember(th.id) {
        (th.anhNen ?: th.anhXem)?.let { a -> runCatching {
            ctx.assets.open(a).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) }
        }.getOrNull()?.asImageBitmap() }
    }
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(th.mau.map { Color(it) }))
            .border(if (chon) 2.dp else 1.dp, if (chon) Nhan else Vien, RoundedCornerShape(12.dp))) {
            if (anh != null) Image(anh, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { i -> Box(Modifier.size(width = 20.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)).background(if (i == 0) Color.White else Color.White.copy(alpha = 0.55f))) }
            }
            Text("5:05", Modifier.align(Alignment.TopStart).padding(8.dp), fontSize = 13.sp, fontWeight = FontWeight.Light, color = Color.White)
            if (chon) Icon(Icons.Rounded.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(Color.White), tint = Nhan)
        }
        Text(t(th.tenVi, th.tenEn), fontSize = 13.sp, fontWeight = if (chon) FontWeight.SemiBold else FontWeight.Normal, color = if (chon) Nhan else ChuChinh,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    }
}

@Composable
internal fun Hang(chu: String, phu: String, bat: Boolean, doi: (Boolean) -> Unit) {
    // Cả hàng là một công tắc (remote tivi dừng một lần mỗi dòng), công tắc kiểu iOS bên phải.
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).toggleable(bat, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = doi)
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(chu, fontSize = 15.sp); Phu(phu) }
        Spacer(Modifier.width(12.dp))
        CongTac(bat, null)
    }
}

// ---------------- Xong ----------------

@Composable
private fun ManXong(vm: PhienLamViec) = Cuon {
    // Rời app khi đang ở màn này: hẹn tự tắt ADB sau 60 giây; quay lại trước đó thì huỷ.
    val vongDoi = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(vongDoi) {
        val nghe = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.henTuTatAdb()
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) vm.huyTuTatAdb()
        }
        vongDoi.lifecycle.addObserver(nghe)
        onDispose { vongDoi.lifecycle.removeObserver(nghe) }
    }
    DauTrang({
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(23)).background(Gradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(52.dp), tint = Color.White)
        }
    }, t("Tivi đã sạch!", "The TV is clean!"),
        if (vm.homeXong && vm.phimHomeOk) t("Home sạch đang chạy — bấm Home trên remote để xem.", "The clean Home is running — press Home on the remote.")
        else if (vm.homeXong) t("Home sạch đã cài nhưng nút Home trên remote vẫn mở Home cũ (xem lời báo bên dưới).", "The clean Home is installed, but the remote's Home button still opens the old Home (see the note below).")
        else null)
    The(t("Trước khi về", "Before you leave"), Icons.Rounded.Checklist) {
        BuocSo(1, t("Thử lại YouTube, Netflix, HDMI, Cài đặt, remote.", "Try YouTube, Netflix, HDMI, Settings and the remote."))
        BuocSo(2, t("Khởi động lại một lần xem gói nào tự bật lại.", "Reboot once to see if anything re-enables itself."))
        BuocSo(3, t("Tắt ADB: để mở là ai cùng mạng cũng điều khiển được tivi. Rời app quá 1 phút thì tivi tự tắt.", "Turn ADB off: left on, anyone on the network can control the TV. Leave the app for over a minute and the TV turns ADB off itself."))
        Hang(t("Giữ ADB bật cho lần sau", "Keep ADB on for next time"), t("Lần sau đổi giao diện / khôi phục không phải bật lại gỡ lỗi trên tivi", "Next time you change the look or restore, no need to turn debugging on again"), vm.giuAdb) { vm.giuAdb = it }
        NutPhu(t("Xem màn hình tivi", "View TV screen"), Icons.Rounded.Screenshot) { vm.chupManHinh() }
        NutPhu(t("Khởi động lại tivi", "Reboot the TV"), Icons.Rounded.RestartAlt) { vm.khoiDongLaiTv() }
    }
    // Mời vào cộng đồng VGS (22/10/2026, anh Alex duyệt): ?tu=cleantv-app để vgs.zone ghi nguồn đăng ký từ app.
    if (BuildConfig.LIEN_KET_VGS) The(t("Cộng đồng VGS", "The VGS community"), Icons.Rounded.Groups) {
        Phu(t("CleanTV miễn phí, do cộng đồng VGS làm. Tham gia VGSZone để hỏi đáp, nhận bản mới sớm và săn quà công nghệ.",
            "CleanTV is free, made by the VGS community. Join VGSZone for help, early builds and tech giveaways."))
        val uri = androidx.compose.ui.platform.LocalUriHandler.current
        NutPhu(t("Tham gia cộng đồng VGS", "Join the VGS community"), Icons.Rounded.Groups) {
            runCatching { uri.openUri("https://vgs.zone/dang-ky?tu=cleantv-app&next=/cleantv") }
        }
    }
    TheKichBan(vm)
    if (BuildConfig.GUI_SO_LIEU) TheGuiDanhSach(vm)
    // Lời mời ủng hộ chỉ ở đây, sau khi việc đã xong, một dòng nhỏ: không bật hộp chặn, không hỏi lúc mở app.
    TextButton(onClick = { vm.moUngHo = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp), tint = DoTuoi); Spacer(Modifier.width(6.dp))
        Text(t("TiviKit giúp được bạn? Ủng hộ tác giả", "Did TiviKit help? Support the author"))
    }
    NutGopY(vm)
    NutChinh(t("Tắt ADB & kết thúc", "Turn off ADB & finish"), enabled = !vm.dangBan, icon = Icons.Rounded.Lock) { vm.tatAdbVaKetThuc() }
    NutPhu(t("Về màn chính, dùng tiếp công cụ", "Back to the home screen, keep using the tools"), Icons.Rounded.Home, enabled = !vm.dangBan) { vm.veHub() }
    Phu(t("Sau đó trên tivi: Tuỳ chọn nhà phát triển → tắt Gỡ lỗi USB, chọn \"Thu hồi uỷ quyền gỡ lỗi USB\". Bản sao lưu vẫn trong điện thoại để khôi phục.",
        "Then on the TV: Developer options → turn off USB debugging and \"Revoke USB debugging authorisations\". The backup stays on this phone for restoring."))
}

// ---------------- Khôi phục ----------------

@Composable
private fun ManKhoiPhuc(vm: PhienLamViec) = Cuon {
    val sl = vm.saoLuu
    if (vm.khoiPhucXong) {
        DauTrang({ Icon(Icons.Rounded.CheckCircle, null, Modifier.size(72.dp), tint = Xanh) },
            t("Đã khôi phục", "Restored"), t("Tivi đã về như trước khi dọn.", "The TV is back to how it was."))
        NutChinh(t("Tiếp: tắt ADB", "Next: turn off ADB"), icon = Icons.Rounded.Lock) { vm.man = Man.XONG }
        return@Cuon
    }
    if (sl == null) {
        // Không có bản sao lưu cả trên điện thoại lẫn trên tivi: bật lại mọi gói đang tắt, hỏi xác nhận rõ (22/10/2026).
        DauTrang({ OIcon(Icons.Rounded.Restore, 72.dp) }, t("Khôi phục không cần sao lưu", "Restore without a backup"),
            t("Không thấy bản sao lưu của ${vm.thongTin?.tenHienThi} trên điện thoại này lẫn trên tivi.",
                "No backup of ${vm.thongTin?.tenHienThi} on this phone or on the TV."))
        val ds = vm.goiTatKhongSaoLuu
        if (ds == null) {
            The {
                Text(t("App có thể bật lại MỌI gói đang bị tắt trên tivi, gỡ Home sạch và trả Home gốc của hãng. Gói chính bạn tự tắt từ trước cũng sẽ bật lại.",
                    "The app can re-enable EVERY disabled package on the TV, remove the clean Home and bring back the maker's Home. Packages you disabled yourself earlier come back too."))
            }
            NutChinh(if (vm.dangBan) t("Đang đọc…", "Reading…") else t("Xem gói đang tắt", "See disabled packages"), enabled = !vm.dangBan, icon = Icons.Rounded.Search) { vm.docGoiTatKhongSaoLuu() }
            return@Cuon
        }
        var dongY by remember { mutableStateOf(false) }
        var goHomeKsl by remember { mutableStateOf(true) }
        The(t("${ds.size} gói đang tắt", "${ds.size} disabled packages"), Icons.Rounded.Restore) {
            if (ds.isEmpty()) Phu(t("Không có gói nào đang tắt.", "No packages are disabled."))
            ds.take(12).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
            if (ds.size > 12) Phu(t("…và ${ds.size - 12} gói nữa.", "…and ${ds.size - 12} more."))
        }
        The {
            Hang(t("Gỡ luôn Home sạch", "Also uninstall the clean Home"), t("Bỏ chọn để giữ Home sạch trên tivi", "Untick to keep it on the TV"), goHomeKsl) { goHomeKsl = it }
            Hang(t("Tôi hiểu: bật lại tất cả", "I understand: re-enable all"), t("Kể cả gói tôi tự tắt trước khi dùng TiviKit", "Including ones I disabled before using TiviKit"), dongY) { dongY = it }
        }
        NutChinh(if (vm.dangBan) t("Đang khôi phục…", "Restoring…") else t("Khôi phục", "Restore"), enabled = dongY && !vm.dangBan, icon = Icons.Rounded.Restore) {
            vm.chayKhoiPhucKhongSaoLuu(goHomeKsl)
        }
        vm.nhatKy.takeLast(4).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
        TheBatTungApp(vm)
        return@Cuon
    }
    DauTrang({ OIcon(Icons.Rounded.Restore, 72.dp) }, t("Khôi phục ${sl.ten}", "Restore ${sl.ten}"),
        t("Bật lại ${sl.daTatBoiApp.size} gói", "Re-enable ${sl.daTatBoiApp.size} packages") +
            (if (sl.launcherDaCai) t(", trả Home gốc", ", restore the stock Home") else "") +
            (if (sl.dnsDaDoi) t(", trả DNS", ", restore DNS") else "") + ".")
    var goHome by remember { mutableStateOf(true) }
    if (sl.launcherDaCai) The {
        Hang(t("Gỡ luôn Home sạch", "Also uninstall the clean Home"), t("Bỏ chọn để giữ Home sạch trên tivi", "Untick to keep it on the TV"), goHome) { goHome = it }
    }
    NutChinh(if (vm.dangBan) t("Đang khôi phục…", "Restoring…") else t("Khôi phục", "Restore"), enabled = !vm.dangBan, icon = Icons.Rounded.Restore) {
        vm.chayKhoiPhuc(sl.launcherDaCai && goHome)
    }
    vm.nhatKy.takeLast(4).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
    TheKichBan(vm)
    TheBatTungApp(vm)
}

/** Chữ nhỏ "Xem lệnh sẽ chạy": mở hộp liệt kê các lệnh chính app sắp gửi lên tivi (24/10/2026). */
@Composable
private fun NutXemLenh(lenh: () -> List<String>) {
    var mo by remember { mutableStateOf(false) }
    Text(t("Xem lệnh sẽ chạy trên tivi", "See the commands it will run"), fontSize = 13.sp, color = ChuPhu,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { mo = true }.padding(6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    if (mo) AlertDialog(
        onDismissRequest = { mo = false }, containerColor = The1,
        title = { Text(t("Lệnh sẽ chạy trên tivi", "Commands for the TV")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Phu(t("Các lệnh chính, theo thứ tự. Gói lõi Android và Home đang dùng app vẫn tự bỏ qua khi chạy.", "The main commands, in order. Core Android packages and the Home in use are still skipped when it runs."))
                lenh().forEach { d ->
                    Text(d, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (d.startsWith("#")) Nhan else ChuPhu, modifier = Modifier.padding(top = if (d.startsWith("#")) 8.dp else 1.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { mo = false }) { Text(t("Đóng", "Close")) } },
    )
}

/** Lưu kịch bản khôi phục cho máy tính (24/10/2026): mất điện thoại hay gỡ CleanTV vẫn trả tivi về như cũ bằng adb. */
@Composable
private fun TheKichBan(vm: PhienLamViec) {
    val sl = vm.saoLuu ?: return
    The(t("Kịch bản khôi phục cho máy tính", "Restore script for a computer"), Icons.Rounded.Save) {
        Phu(t("Lưu một file lệnh adb để đưa tivi về như cũ từ máy tính, kể cả khi mất điện thoại hay đã gỡ TiviKit. Windows bấm đúp file, Mac / Linux chạy sh tên-file.bat.",
            "Save a file of adb commands that puts the TV back from a computer, even without this phone or TiviKit. Windows: double-click it; Mac / Linux: sh file-name.bat."))
        val luu = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.xuatKichBan(it) } }
        NutPhu(t("Lưu kịch bản khôi phục", "Save restore script"), Icons.Rounded.Save) {
            luu.launch("cleantv-khoi-phuc-" + sl.ten.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30) + ".bat")
        }
        if (vm.cauHinhBao.isNotEmpty()) Phu(vm.cauHinhBao, Xanh)
    }
}

/** Bật lại từng app (issue vgs-cleantv#3): danh sách gói đang tắt đọc thẳng từ tivi, tích cái nào bật cái đó. */
@Composable
private fun TheBatTungApp(vm: PhienLamViec) {
    The(t("Bật lại từng app", "Re-enable single apps"), Icons.Rounded.Apps) {
        Phu(t("Chỉ muốn bật lại vài app lỡ tắt? App đọc từ tivi mọi app đang tắt (kể cả khi đã cài lại TiviKit), bạn tích cái cần bật.",
            "Only need a few apps back? The app reads every disabled app from the TV (even after reinstalling TiviKit); tick the ones to turn on."))
        val ds = vm.dsTatTv
        if (ds == null) {
            NutPhu(t("Xem các app đang tắt", "See disabled apps"), Icons.Rounded.Search) { vm.docDsTat() }
            return@The
        }
        if (ds.isEmpty()) Phu(t("Tivi không có app nào đang tắt.", "No apps are disabled on the TV."))
        val cuaCleanTv = vm.saoLuu?.daTatBoiApp.orEmpty()
        ds.forEach { p ->
            val chon = vm.chonBatLai[p] == true
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(chon, role = androidx.compose.ui.semantics.Role.Checkbox) { vm.chonBatLai[p] = it }
                .padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OTich(chon)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tenDep(p), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(p + if (p in cuaCleanTv) t(" · do TiviKit tắt", " · disabled by TiviKit") else "", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = ChuPhu, maxLines = 1)
                }
            }
        }
        if (vm.batLaiBao.isNotEmpty()) Phu(vm.batLaiBao, Xanh)
        val so = vm.chonBatLai.count { it.value }
        if (ds.isNotEmpty()) NutPhu(t("Bật lại $so app đã chọn", "Re-enable $so selected"), Icons.Rounded.Restore, enabled = so > 0 && !vm.dangBan) { vm.batLaiDaChon() }
    }
}

// ---------------- Nhắc + hướng dẫn chế độ nhà phát triển ----------------

/** Thẻ nổi bật ở màn chính: việc BẮT BUỘC làm trên tivi trước khi dùng app. */
@Composable
internal fun TheNhacNpt(vm: PhienLamViec) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NenCanhBao).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
            Text(t("Trước tiên: bật chế độ nhà phát triển trên tivi", "First: turn on developer mode on the TV"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        Phu(t("Không có bước này app không kết nối được tivi. Làm một lần, khoảng 1 phút: Cài đặt → Giới thiệu → bấm Bản dựng 7 lần → bật Gỡ lỗi qua USB.",
            "The app can't reach the TV without this. One time, about a minute: Settings → About → press Build 7 times → turn on USB debugging."), ChuChinh.copy(alpha = 0.85f))
        // Nút cam đặc: chữ trắng trên cam đậm (sáng), chữ đen trên cam tươi (tối) cho dễ đọc.
        val chuNut = if (LocalMau.current.toi) Color.Black else Color.White
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(Cam).clickable { vm.moHuongDan() }.padding(10.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MenuBook, null, tint = chuNut); Spacer(Modifier.width(8.dp))
            Text(t("Xem hướng dẫn có hình (7 bước)", "Picture guide (7 steps)"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = chuNut)
        }
        Phu(t("Chỉ Android TV / Google TV. Samsung (Tizen), LG (webOS) không làm được.", "Android TV / Google TV only. Samsung (Tizen) and LG (webOS) are not supported."))
    }
}

@Composable
private fun ManHuongDan(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val thuMuc = if (NgonNgu.tiengViet) "vi" else "en"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Bật chế độ nhà phát triển", "Turn on developer mode"), style = KieuTieuDeLon)
                Phu(t("Cầm remote, làm lần lượt 7 bước. Ảnh chụp từ Android TV — tên mục có thể khác chút tuỳ hãng (Hệ thống / Giới thiệu / Thông tin…).",
                    "Grab the remote and follow the 7 steps. Pictures are from Android TV — names may differ slightly by brand (System / About / Information…)."))
            }
        }
        itemsIndexed(HUONG_DAN) { i, b ->
            val anh = remember(thuMuc, b.anh) {
                runCatching { ctx.assets.open("huongdan/$thuMuc/${b.anh}.webp").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Nhan), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(t(b.tieuDeVi, b.tieuDeEn), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (anh != null) Image(anh, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Vien, RoundedCornerShape(12.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                Text(t(b.chuVi, b.chuEn), fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        item {
            The(t("Muốn đổi giao diện sau này", "Changing the look later"), Icons.Rounded.Palette) {
                BuocSo(1, t("Trên tivi bật lại \"Gỡ lỗi qua USB\" (Tùy chọn nhà phát triển vẫn còn, không phải bấm Bản dựng nữa).",
                    "On the TV turn \"USB debugging\" back on (Developer options stays, no need to press Build again)."))
                BuocSo(2, t("Mở app → Dọn một tivi → kết nối lại tivi đó. Đã tích \"Luôn cho phép\" thì tivi không hỏi lại.",
                    "Open the app → Clean a TV → connect to that TV again. If you ticked \"Always allow\" the TV won't ask again."))
                BuocSo(3, t("Bấm \"Đổi giao diện Home sạch\", chọn theme, dock, thời tiết, tên chào → Áp dụng giao diện. Các gói đã tắt giữ nguyên, không phải dọn lại.",
                    "Tap \"Change the clean Home theme\", pick a theme, dock, weather and greeting → Apply theme. Disabled packages stay as they are."))
                BuocSo(4, t("Xong thì bấm Tắt ADB như lần đầu.", "When done, press Turn off ADB as before."))
                Phu(t("Chỉnh nhanh không cần điện thoại: trên tivi giữ OK ở nút Cài đặt của Home sạch để đổi số cột, hiệu ứng, sắp xếp app (theme và hình nền vẫn phải đổi qua app).",
                    "Quick tweaks without the phone: on the TV hold OK on the clean Home's Settings button to change columns, motion and app order (themes and wallpapers still need the app)."))
            }
        }
        item {
            The(t("Dọn xong thì tắt lại", "Turn it off when you're done"), Icons.Rounded.Lock) {
                Phu(t("Bước cuối trong app sẽ tắt ADB. Cho chắc, vào lại \"Tùy chọn nhà phát triển\": tắt \"Gỡ lỗi qua USB\" và chọn \"Thu hồi các lượt ủy quyền gỡ lỗi qua USB\".",
                    "The app's last step turns ADB off. To be sure, go back to \"Developer options\": turn off \"USB debugging\" and choose \"Revoke USB debugging authorizations\"."))
            }
        }
        item {
            NutChinh(t("Đã bật xong — kết nối tivi", "Done — connect to the TV"), icon = Icons.Rounded.WifiFind) { vm.man = Man.KET_NOI }
        }
    }
}

@Composable
private fun ManGiayPhep() {
    val ctx = LocalContext.current
    val chu = remember { runCatching { ctx.assets.open("giay-phep.txt").bufferedReader().readText() }.getOrDefault("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Giấy phép mã nguồn mở", "Open-source licenses"), style = KieuTieuDeLon)
        Text(chu, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = ChuPhu)
    }
}

// ---------------- Máy chiếu ----------------

/**
 * Dò màn lấy nét / chỉnh hình của hãng, mở thử trên máy chiếu, ghim vào nút "Máy chiếu" trên Home sạch.
 * Tivi thường không hiện thẻ này, trừ khi bấm dòng "Máy chiếu?" nhỏ.
 */
@Composable
private fun TheMayChieu(vm: PhienLamViec) {
    var moTay by rememberSaveable { mutableStateOf(false) }
    if (!vm.laMayChieu && vm.mayChieu == null && !moTay) {
        TextButton(onClick = { moTay = true }) {
            Icon(Icons.Rounded.Videocam, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(t("Đây là máy chiếu? Thêm nút lấy nét, chỉnh hình", "Is this a projector? Add focus / keystone buttons"))
        }
        return
    }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    The(t("Máy chiếu: lấy nét, chỉnh hình", "Projector: focus & keystone"), Icons.Rounded.Videocam) {
        Phu(t("App dò các màn chỉnh của hãng trên máy chiếu. Bấm Mở thử để xem đúng màn chưa, rồi Ghim: Home sạch sẽ có nút \"Máy chiếu\" mở thẳng các màn đó. Gói lo lấy nét, chỉnh hình không bao giờ bị tắt.",
            "The app looks for the maker's adjustment screens. Tap Try to check one, then Pin it: the clean Home gets a \"Projector\" button that opens them. Focus / keystone packages are never disabled."))
        val ds = vm.mayChieu
        if (ds == null) {
            NutPhu(if (vm.dangDoMayChieu) t("Đang dò… (khoảng 30 giây)", "Scanning… (about 30 seconds)") else t("Dò màn lấy nét / chỉnh hình", "Find focus / keystone screens"),
                Icons.Rounded.Search, enabled = !vm.dangDoMayChieu) { vm.doMayChieu() }
        } else {
            ds.forEach { comp ->
                val ghim = comp in vm.ghimMayChieu
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ghim) Nhan.copy(alpha = 0.2f) else The2)
                    .border(1.dp, if (ghim) Nhan else Color.Transparent, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(vm.ghimMayChieu[comp] ?: vm.nhanMayChieu(comp), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(comp, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ChuPhu, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        TextButton(onClick = { vm.moThuMayChieu(comp) }, enabled = !vm.dangBan) { Text(t("Mở thử", "Try")) }
                        TextButton(onClick = { vm.doiGhim(comp) }) { Text(if (ghim) t("Bỏ ghim", "Unpin") else t("Ghim", "Pin")) }
                    }
                }
            }
            if (vm.ghimMayChieu.isNotEmpty()) Phu(t("Đã ghim ${vm.ghimMayChieu.size}. Bấm Cài Home sạch / Áp dụng giao diện để đưa lên máy chiếu.",
                "${vm.ghimMayChieu.size} pinned. Install the clean Home / Apply theme to send them to the projector."), Xanh)
        }
        if (BuildConfig.LIEN_KET_VGS) {
            NutPhu(t("Gửi danh sách cho VGS", "Send the list to VGS"), Icons.Rounded.Feedback) {
                clip.setText(androidx.compose.ui.text.AnnotatedString(vm.chuDanhSachMayChieu()))
                val tu = "CleanTV máy chiếu · " + (vm.thongTin?.tenHienThi ?: "")
                uri.openUri("https://vgs.zone/gop-y?tu=" + java.net.URLEncoder.encode(tu, "UTF-8"))
            }
            Phu(t("Danh sách đã chép sẵn — dán vào ô nội dung trong hộp góp ý.", "The list is copied — paste it into the feedback box."))
        }
    }
}

/** Hộp chọn tay một mẫu trong toàn bộ danh sách, có ô tìm (23/10/2026). */
@Composable
private fun HopChonMau(vm: PhienLamViec) {
    var tim by remember { mutableStateOf("") }
    val khoa = XaPhuong.boDau(tim.trim().lowercase())
    val ds = remember(khoa, vm.tatCaMau) {
        if (khoa.isEmpty()) vm.tatCaMau else vm.tatCaMau.filter { m -> khoa.split(" ").all { it in XaPhuong.boDau(m.ten.lowercase()) || it in m.id } }
    }
    AlertDialog(
        onDismissRequest = { vm.moChonMau = false },
        confirmButton = { TextButton(onClick = { vm.moChonMau = false }) { Text(t("Đóng", "Close")) } },
        title = { Text(t("Chọn mẫu tivi", "Pick a TV list")) },
        text = {
            Column {
                OutlinedTextField(tim, { tim = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Tìm hãng, model (vd: sony vu3, tcl c7)", "Search brand, model (e.g. sony vu3)")) })
                Text(t("${ds.size} mẫu", "${ds.size} lists"), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(ds.take(120), key = { it.id }) { m ->
                        Text(m.ten.take(110), fontSize = 14.sp, modifier = Modifier.fillMaxWidth()
                            .clickable { vm.mauChon = m; vm.moChonMau = false }.padding(vertical = 10.dp))
                    }
                }
            }
        },
        containerColor = The1,
    )
}

/** Gửi danh sách gói cho VGS (23/10/2026): người dùng tự bấm, để làm mẫu cho tivi chưa có trong danh sách. */
@Composable
private fun TheGuiDanhSach(vm: PhienLamViec) {
    The(t("Giúp người dùng cùng dòng tivi", "Help owners of the same TV"), Icons.Rounded.Share) {
        if (vm.dsDaGui) {
            Phu(t("Đã gửi, cảm ơn bạn! VGS sẽ xem và thêm tivi này vào danh sách.", "Sent, thank you! VGS will review it and add this TV to the lists."), Xanh)
            return@The
        }
        Phu(t("Gửi cho VGS model tivi và danh sách gói đang tắt, để làm mẫu cho người dùng cùng dòng tivi. Không gửi tên, email hay danh sách app bạn cài — trừ khi bạn tự ghi chỗ liên hệ.",
            "Send VGS this TV's model and the packages that are off, to build a list for owners of the same TV. No name, email or app list — unless you add a contact."))
        Text(t("Dùng thử rồi, tivi chạy:", "After trying it, the TV:"), fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Ổn hết", "Works fine"), vm.dsDanhGia == "on") { vm.dsDanhGia = "on" }
            ChonChip(t("Có chỗ chưa ổn", "Has issues"), vm.dsDanhGia == "chua-on") { vm.dsDanhGia = "chua-on" }
        }
        OutlinedTextField(vm.dsGhiChu, { if (it.length <= 1000) vm.dsGhiChu = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            label = { Text(if (vm.dsDanhGia == "on") t("Ghi chú (không bắt buộc)", "Note (optional)") else t("Chưa ổn ở đâu? (HDMI, remote, giọng nói…)", "What's wrong? (HDMI, remote, voice…)")) })
        OutlinedTextField(vm.dsLienHe, { if (it.length <= 120) vm.dsLienHe = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            label = { Text(t("Email / Zalo để VGS hỏi lại (không bắt buộc)", "Email to reach you (optional)")) })
        NutPhu(t("Gửi danh sách gói cho VGS", "Send the package list to VGS"), Icons.Rounded.Share) { if (!vm.dangBan) vm.guiDanhSach() }
    }
}

// ---------------- Khoá trẻ em ----------------

internal val TEN_THU = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
internal val TEN_THU_EN = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManKhoaTre(vm: PhienLamViec) = Cuon {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Khoá trẻ em", "Kids lock"), style = KieuTieuDeLon)
        Phu(t("Tới giờ khoá, hay xem hết giờ trong ngày, tivi hiện màn khoá che mọi thứ (cả HDMI, YouTube) và chặn phím remote. Nhập đúng mã PIN trên tivi thì mở ${vm.KHOA_MO_TAM} phút. Tắt tivi bật lại vẫn khoá.",
            "During the lock periods, or once the day's time is used up, the TV shows a lock screen over everything (HDMI and YouTube too) and blocks the remote. The right PIN unlocks it for ${vm.KHOA_MO_TAM} minutes. It stays locked after a restart."))
    }
    vm.khoaTrenTv?.let { j ->
        The(t("Trên tivi", "On the TV"), Icons.Rounded.Lock) {
            val bat = j.optBoolean("bat"); val chay = j.optBoolean("dichVuChay"); val dangKhoa = j.optBoolean("dangKhoa")
            val moDen = j.optLong("moTamDen")
            Phu(if (!bat) t("Khoá đang tắt.", "The lock is off.")
                else if (!chay) t("Khoá đang bật nhưng dịch vụ chưa chạy (tắt tivi bật lại).", "On, but the service isn't running (restart the TV).")
                else if (dangKhoa && j.optString("lyDo") == "het-gio") t("Tivi đang khoá: đã xem hết giờ hôm nay.", "The TV is locked: today's time is used up.")
                else if (dangKhoa) t("Tivi đang khoá.", "The TV is locked now.")
                else if (moDen > System.currentTimeMillis()) t("Đang mở tạm tới ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(moDen))}.", "Unlocked until ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(moDen))}.")
                else t("Khoá đang bật, ngoài giờ khoá.", "On, outside the lock periods."), if (bat && chay) Xanh else ChuPhu)
            if (bat) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Mở tạm ${vm.KHOA_MO_TAM} phút", "Unlock ${vm.KHOA_MO_TAM} min"), false) { vm.moTamKhoaTre(vm.KHOA_MO_TAM) }
                ChonChip(t("Khoá lại ngay", "Lock now"), false) { vm.moTamKhoaTre(0) }
            }
            // App khoá cài từ trước khi đổi tên gói (com.icm.cleantv.khoa): tivi không cho đọc PIN của nó.
            if (j.optBoolean("banCu")) Phu(t("Đây là Khoá trẻ em của TiviKit bản cũ, vẫn đang khoá bình thường. Đặt PIN mới bên dưới rồi bấm Lưu: app cài bản mới với giờ khoá này và gỡ bản cũ.",
                "This is the Kids lock from the old TiviKit and it still works. Set a new PIN below and tap Save: the app installs the new version with these periods and removes the old one."), Cam)
            // App khoá bản trước 25/10/2026: chưa đếm giờ xem, Lưu lại là cài bản mới (giữ giờ khoá và PIN).
            else if (!j.has("giayHomNay")) Phu(t("App khoá trên tivi là bản cũ, chưa có giới hạn mỗi ngày và báo cáo thời gian xem. Bấm Lưu & áp dụng để cập nhật (giữ giờ khoá và PIN).",
                "The lock app on the TV is an older version without daily limits or screen time. Tap Save & apply to update it (the schedule and PIN are kept)."), Cam)
        }
        vm.khoaThoiGian?.let { TheThoiGianXem(it) }
    }
    The {
        Hang(t("Bật Khoá trẻ em", "Kids lock on"), t("Tắt thì tivi dùng bình thường, giữ nguyên giờ khoá, giới hạn và PIN", "Off: the TV works normally; periods, limits and PIN are kept"), vm.khoaBat) { vm.khoaBat = it }
    }
    The(t("Giờ khoá", "Lock periods"), Icons.Rounded.Schedule) {
        if (vm.khoaKhung.isEmpty()) Phu(t("Chưa có khung giờ khoá.", "No lock periods."))
        vm.khoaKhung.forEachIndexed { i, k ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${k.tu} – ${k.den}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (k.ngay.size == 7) t("Mọi ngày", "Every day") else k.ngay.sorted().joinToString(" ") { t(TEN_THU[it - 1], TEN_THU_EN[it - 1]) }, fontSize = 12.sp, color = ChuPhu)
                }
                IconButton(onClick = { vm.khoaKhung.removeAt(i) }) { Icon(Icons.Rounded.Close, t("Xoá", "Remove")) }
            }
        }
        Phu(t("Thêm nhanh:", "Quick add:"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Tối 21:00–06:00", "Night 21:00–06:00"), false) { vm.khoaKhung += PhienLamViec.KhungKhoa("21:00", "06:00", (1..7).toSet()) }
            ChonChip(t("Giờ học 14:00–16:30 (T2–T6)", "Study 14:00–16:30 (Mon–Fri)"), false) { vm.khoaKhung += PhienLamViec.KhungKhoa("14:00", "16:30", (1..5).toSet()) }
            ChonChip(t("Trưa 12:00–13:30", "Nap 12:00–13:30"), false) { vm.khoaKhung += PhienLamViec.KhungKhoa("12:00", "13:30", (1..7).toSet()) }
        }
        var tu by remember { mutableStateOf("") }
        var den by remember { mutableStateOf("") }
        var ngay by remember { mutableStateOf((1..7).toSet()) }
        Text(t("Hoặc tự đặt:", "Or set your own:"), fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(tu, { tu = it.take(5) }, label = { Text(t("Từ (21:00)", "From")) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(den, { den = it.take(5) }, label = { Text(t("Đến (06:00)", "To")) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { d -> ChonChip(t(TEN_THU[d - 1], TEN_THU_EN[d - 1]), d in ngay) { ngay = if (d in ngay) ngay - d else ngay + d } }
        }
        NutPhu(t("Thêm khung này", "Add this period"), Icons.Rounded.Add) {
            fun chuan(s: String) = s.trim().replace('.', ':').replace('h', ':').let { if (Regex("^[0-9]:").containsMatchIn(it)) "0$it" else it }.let { if (Regex("^[0-9]{2}$").matches(it)) "$it:00" else it }
            vm.khoaKhung += PhienLamViec.KhungKhoa(chuan(tu), chuan(den), ngay); tu = ""; den = ""
        }
        Phu(t("Khung qua đêm (21:00–06:00) tính theo ngày bắt đầu: chọn T2 là khoá từ tối thứ Hai tới sáng thứ Ba.", "Overnight periods count from the starting day."))
    }
    TheGioiHan(vm)
    The(t("Mã PIN", "PIN"), Icons.Rounded.Key) {
        if (vm.khoaTrenTv?.optBoolean("coPin") == true && vm.khoaTrenTv?.optBoolean("banCu") != true) Phu(t("Tivi đã có PIN. Để trống nếu không đổi.", "The TV already has a PIN. Leave empty to keep it."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(vm.khoaPin, { vm.khoaPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("PIN 4 số", "4-digit PIN")) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            OutlinedTextField(vm.khoaPin2, { vm.khoaPin2 = it.filter(Char::isDigit).take(4) }, label = { Text(t("Nhập lại", "Repeat")) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        }
        Phu(t("Sai 5 lần thì tivi bắt chờ 1 phút. Quên PIN: kết nối lại tivi ở đây và đặt PIN mới. Tivi chỉ giữ dạng mã hoá của PIN.", "5 wrong tries: wait 1 minute. Forgot it: reconnect here and set a new one. The TV only keeps a hash."))
    }
    if (vm.khoaBao.isNotEmpty()) Phu(vm.khoaBao, Xanh)
    NutChinh(t("Lưu & áp dụng lên tivi", "Save & apply to the TV"), enabled = !vm.dangBan, icon = Icons.Rounded.Lock) { vm.luuKhoaTre() }
    if (vm.khoaTrenTv != null) NutPhu(t("Tắt và gỡ Khoá trẻ em", "Turn off and remove"), Icons.Rounded.LockOpen) { vm.tatKhoaTre() }
    Phu(t("Lưu ý: khoá không chống được khôi phục cài đặt gốc hay đổi sang thiết bị khác cắm HDMI. Với hộp Android cắm vào tivi, trẻ vẫn đổi nguồn tivi được. Bấm Khôi phục tivi này cũng gỡ khoá.",
        "Note: it can't stop a factory reset or another device on HDMI; with a box plugged into a TV, the TV's own input switch still works. Restore this TV also removes the lock."))
}

// ---------------- Cài app lên tivi (25/10/2026) ----------------

internal fun coChu(b: Long) = when {
    b >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1f GB", b / 1e9)
    b >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", b / 1e6)
    else -> "${(b + 999) / 1000} KB"
}

@Composable
private fun ManCaiApk(vm: PhienLamViec) = Cuon {
    val ctx = LocalContext.current
    val chonFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.themFileApk(uris)
    }
    // Đang gửi / cài thì giữ màn điện thoại sáng: tắt màn là app xuống nền, nhiều máy cắt luôn kết nối tới tivi.
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(vm.dangBan) {
        view.keepScreenOn = vm.dangBan
        onDispose { view.keepScreenOn = false }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Cài app lên tivi", "Install apps on the TV"), style = KieuTieuDeLon)
        Phu(t("Chọn file APK, app gửi qua Wi-Fi và cài thẳng lên tivi, không cần USB hay app Downloader. Nhận file .apk và gói chia nhỏ .apks / .xapk / .apkm.",
            "Pick an APK: the app sends it over Wi-Fi and installs it straight on the TV, no USB stick or Downloader needed. Takes .apk files and split packages (.apks / .xapk / .apkm)."))
    }
    TheCapNhat(vm)
    The(t("Chọn file", "Pick files"), Icons.Rounded.FolderOpen) {
        NutChinh(if (vn.tivikit.ManHinh.laTv) t("Chọn file APK", "Pick APK files") else t("Chọn file APK trên điện thoại", "Pick APK files on the phone"),
            enabled = !vm.dangBan, icon = Icons.Rounded.FileOpen) {
            // Nhiều tivi không có trình chọn file ("không có ứng dụng phù hợp"): chỉ còn đường tìm file trên tivi / USB.
            runCatching { chonFile.launch(GoiApk.KIEU_FILE) }.onFailure {
                Toast.makeText(ctx, t("Máy này không có trình chọn file. Dùng nút Tìm file trên tivi / USB.", "No file picker on this device. Use Find files on the TV / USB."), Toast.LENGTH_LONG).show()
            }
        }
        NutPhu(t("Tìm file .apk trên tivi / USB", "Find .apk files on the TV / USB"), Icons.Rounded.Usb, enabled = !vm.dangBan) { vm.timApkTrenTv() }
        vm.apkTrenTv?.let { ds ->
            if (ds.isEmpty()) Phu(t("Không thấy file .apk nào trong bộ nhớ tivi hay USB đang cắm.", "No .apk files in the TV's storage or on a plugged-in USB stick."))
            ds.forEach { (p, co) ->
                val daThem = vm.dsCai.any { it.duongDanTv == p }
                LuaChon(daThem, p.substringAfterLast('/'), p.substringBeforeLast('/') + " · " + coChu(co)) { if (!daThem) vm.themApkTrenTv(p, co) }
            }
        }
    }
    TheTuLink(vm)
    TheGoiY(vm)
    vm.dsCai.forEach { a -> TheAppCai(vm, a) }
    val soCho = vm.dsCai.count { it.trangThai == PhienLamViec.TrangThaiCai.CHO }
    if (vm.dsCai.isNotEmpty()) NutChinh(if (soCho > 0) t("Cài $soCho app lên tivi", "Install $soCho app(s) on the TV") else t("Không còn app chờ cài", "Nothing left to install"),
        enabled = !vm.dangBan && soCho > 0, icon = Icons.Rounded.Download) { vm.caiTatCa() }
    Phu(t("Chỉ cài app từ nguồn bạn tin. Tivi có thể hiện hộp hỏi (Play Protect / Xác minh ứng dụng): thấy chờ lâu thì nhìn lên tivi và bấm Cho phép.",
        "Only install apps from sources you trust. The TV may show a prompt (Play Protect / Verify apps): if it seems stuck, look at the TV and allow it."))
}

@Composable
private fun TheAppCai(vm: PhienLamViec, a: PhienLamViec.AppCai) {
    var hoiGo by remember { mutableStateOf(false) }
    val tt = a.thongTin
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OIcon(Icons.Rounded.Android, 42.dp, NenXam)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.ten, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Phu(listOfNotNull(tt?.pkg, tt?.versionName?.takeIf { it.isNotBlank() }?.let { "v$it" }, coChu(a.co)).joinToString(" · "))
                a.duongDanTv?.let { Phu(t("File trên tivi: ", "File on the TV: ") + it) }
                a.file?.takeIf { it.laGoi }?.let { Phu(t("Gói chia nhỏ: ${it.phan.size} phần", "Split package: ${it.phan.size} parts")) }
            }
            if (a.trangThai != PhienLamViec.TrangThaiCai.DANG_CAI) IconButton(onClick = { vm.boApkKhoiDs(a) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.Close, t("Bỏ", "Remove")) }
        }
        if (tt != null) Phu(a.banTv?.let { t("Tivi đang có bản $it", "The TV has version $it") } ?: t("Tivi chưa có app này", "Not on the TV yet"))
        if (a.dangTatTrenTv) Phu(t("App này đang bị tắt trên tivi (có thể do lần dọn trước). Bật lại ở mục Khôi phục / Bật lại app đã tắt.",
            "This app is disabled on the TV (maybe by an earlier clean-up). Re-enable it under Restore / Re-enable disabled apps."), Cam)
        a.file?.coObb?.takeIf { it > 0 }?.let {
            Phu(t("Gói kèm ${coChu(it)} dữ liệu OBB: chỉ cài app, chưa gửi dữ liệu này. Game có thể tự tải lại khi mở.",
                "The package carries ${coChu(it)} of OBB data: only the app is installed, not that data. The game may download it again."), Cam)
        }
        when (a.trangThai) {
            PhienLamViec.TrangThaiCai.CHO -> Chip(t("Chờ cài", "Waiting"))
            PhienLamViec.TrangThaiCai.DANG_CAI -> {
                val m = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                // File trên tivi do pm tự đọc, không đếm được byte: thanh chạy liên tục.
                if (a.file != null) LinearProgressIndicator(progress = { a.tienDo }, modifier = m, color = Nhan, trackColor = NenO, gapSize = 0.dp, drawStopIndicator = {})
                else LinearProgressIndicator(modifier = m, color = Nhan, trackColor = NenO, gapSize = 0.dp)
            }
            PhienLamViec.TrangThaiCai.XONG -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = Xanh); Spacer(Modifier.width(6.dp))
                Text(a.bao.ifEmpty { t("Đã cài", "Installed") }, color = Xanh, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                a.pkgDaCai?.let { p -> TextButton(onClick = { vm.moAppTrenTv(p) }, enabled = !vm.dangBan) { Text(t("Mở trên tivi", "Open on the TV")) } }
            }
            PhienLamViec.TrangThaiCai.LOI -> {
                Phu(a.bao, DoTuoi)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (a.canGo) ChonChip(t("Gỡ bản trên tivi rồi cài", "Remove from TV and install"), false) { if (!vm.dangBan) hoiGo = true }
                    ChonChip(t("Thử lại", "Retry"), false) { if (!vm.dangBan) vm.caiLai(a, goTruoc = false) }
                }
            }
        }
    }
    if (hoiGo) AlertDialog(
        onDismissRequest = { hoiGo = false },
        icon = { Icon(Icons.Rounded.Warning, null, tint = Cam) },
        title = { Text(t("Gỡ ${a.ten} trên tivi?", "Remove ${a.ten} from the TV?")) },
        text = { Text(t("Bản trên tivi bị gỡ cùng dữ liệu của nó (đăng nhập, cài đặt trong app), rồi cài bản trong file.",
            "The copy on the TV is removed together with its data (sign-ins, in-app settings), then the file is installed.")) },
        confirmButton = { TextButton(onClick = { hoiGo = false; vm.caiLai(a, goTruoc = true) }) { Text(t("Gỡ & cài", "Remove & install"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { hoiGo = false }) { Text(t("Thôi", "Cancel")) } },
        containerColor = The1,
    )
}
