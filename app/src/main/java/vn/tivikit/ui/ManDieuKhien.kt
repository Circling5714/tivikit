package vn.tivikit.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.adb.PhimTv
import vn.tivikit.t

/**
 * Màn Điều khiển tivi (25/10/2026): remote trên điện thoại + gõ chữ vào ô nhập đang chọn trên tivi + xem màn hình tivi.
 * Đang xem màn hình tivi thì khung hình ghim trên cùng, phần remote bên dưới cuộn riêng (26/10/2026, góp ý: trượt xuống
 * bấm remote vẫn phải thấy tivi).
 */
@Composable
internal fun ManDieuKhien(vm: PhienLamViec) {
    val x = vm.xemManHinh
    // Chạy TiviKit ngay trên tivi thì tự xem chính mình là vô nghĩa.
    val xemDuoc = !vn.tivikit.ManHinh.laTv && vm.ip.trim() != "127.0.0.1"
    val vongDoi = androidx.compose.ui.platform.LocalLifecycleOwner.current
    // Kênh điều khiển scrcpy cho phím ăn ngay và thẻ Đang phát (27/10/2026): chạy khi màn hiện (addObserver gửi ngay ON_START
    // nếu app đang hiện), thôi khi app xuống nền hay rời màn.
    val kenh = vm.kenhDieuKhien
    val dangPhat = vm.dangPhat
    DisposableEffect(vongDoi) {
        val nghe = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) { kenh.mo(); dangPhat.mo() }
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) { kenh.dong(); dangPhat.dong() }
        }
        vongDoi.lifecycle.addObserver(nghe)
        onDispose { vongDoi.lifecycle.removeObserver(nghe); kenh.dong(); dangPhat.dong() }
    }
    // Theo dõi vòng đời ở cấp màn chứ không trong khung hình: khung chuyển từ phần cuộn lên chỗ ghim không được làm tắt
    // luồng. App xuống nền thì tạm dừng, rời màn Điều khiển thì tắt (đỡ tốn Wi-Fi và pin).
    if (xemDuoc) {
        DisposableEffect(vongDoi) {
            val nghe = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) x.tamDung()
                if (e == androidx.lifecycle.Lifecycle.Event.ON_START) x.tiepTuc()
            }
            vongDoi.lifecycle.addObserver(nghe)
            // Xoay máy (activity dựng lại) thì chỉ tạm dừng: dựng lại xong ON_START xem tiếp (28/10/2026: xoay là tắt hẳn).
            onDispose {
                vongDoi.lifecycle.removeObserver(nghe)
                if ((vongDoi as? android.app.Activity)?.isChangingConfigurations == true) x.tamDung() else x.tat()
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (xemDuoc && x.muonXem) KhungManHinhTv(x)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NoiDungDieuKhien(vm, moiXem = xemDuoc && !x.muonXem)
        }
    }
}

/** Phần cuộn của màn Điều khiển; [moiXem] = hiện thẻ mời bật xem màn hình tivi. */
@Composable
private fun ColumnScope.NoiDungDieuKhien(vm: PhienLamViec, moiXem: Boolean) {
    val dk = vm.dieuKhien
    var hoiTat by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Điều khiển tivi", "TV remote"), style = KieuTieuDeLon)
        Phu(t("Bấm như remote thật. Nhấn giữ OK để nhấn giữ trên tivi.", "Works like the real remote. Hold OK for a long press on the TV."))
    }
    vm.dangPhat.bai?.let { TheDangPhat(dk, it) }
    if (moiXem) The(t("Màn hình tivi", "TV screen"), Icons.Rounded.Cast) {
        Phu(t("Xem màn hình tivi ngay trên điện thoại, trễ chưa tới nửa giây; chạm lên hình để bấm đúng chỗ đó. Khung hình ghim trên cùng, trượt xuống bấm remote vẫn thấy tivi. Phim có bản quyền (Netflix…) và cổng HDMI sẽ ra màn đen.",
            "See the TV screen live on the phone, under half a second behind; tap the picture to press that spot. The picture stays pinned at the top while you scroll the remote. Copy-protected films (Netflix…) and HDMI inputs show black."))
        NutPhu(t("Xem màn hình tivi", "Show the TV screen"), Icons.Rounded.Visibility) { vm.xemManHinh.bat() }
    }
    // Ô gõ chữ ở trên cùng (25/10/2026): bàn phím điện thoại mở ra là chiếm nửa dưới màn — ô gõ, nút Gửi và dòng "Đã gửi"
    // phải nằm phía trên nó.
    The(t("Gõ chữ lên tivi", "Type on the TV"), Icons.Rounded.Keyboard) {
        OutlinedTextField(dk.chu, { dk.chu = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(t("Chọn ô nhập trên tivi trước, rồi gõ ở đây", "Select the TV's text box first, then type here")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { dk.guiChu(enter = true) }))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NutRemote(Icons.AutoMirrored.Rounded.Backspace, t("Xoá 1 chữ", "Delete")) { dk.bam(PhimTv.XOA) }
            NutRemote(Icons.AutoMirrored.Rounded.KeyboardReturn, "Enter") { dk.bam(PhimTv.ENTER) }
            NutRemote(Icons.AutoMirrored.Rounded.Send, t("Gửi chữ", "Send"), mau = Nhan) { dk.guiChu(enter = false) }
        }
        if (dk.bao.isNotEmpty()) Phu(dk.bao, when (dk.loaiBao) {
            DieuKhien.LoaiBao.DANG -> ChuPhu
            DieuKhien.LoaiBao.XONG -> Xanh
            DieuKhien.LoaiBao.MOT_PHAN -> Cam
            DieuKhien.LoaiBao.LOI -> DoTuoi
        })
        else Phu(t("Gõ được tiếng Việt có dấu: lần đầu gửi chữ có dấu, app cài thêm bộ gõ nhỏ lên tivi. Nút Gửi trên bàn phím = gửi rồi Enter.",
            "Vietnamese accents work: the first time you send accented text, the app adds a small typing helper to the TV. The keyboard's Send key sends, then presses Enter."))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.AutoMirrored.Rounded.Input, t("Nguồn vào", "Input")) { dk.bam(PhimTv.NGUON_VAO) }
        NutRemote(Icons.Rounded.Settings, t("Cài đặt", "Settings")) { dk.bam(PhimTv.CAI_DAT) }
        NutRemote(Icons.Rounded.PowerSettingsNew, t("Tắt tivi", "Power"), mau = DoTuoi) { hoiTat = true }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DPad(dk) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.AutoMirrored.Rounded.ArrowBack, t("Quay lại", "Back")) { dk.bam(PhimTv.QUAY_LAI) }
        NutRemote(Icons.Rounded.Home, "Home") { dk.bam(PhimTv.HOME) }
        NutRemote(Icons.Rounded.Menu, "Menu") { dk.bam(PhimTv.MENU) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.AutoMirrored.Rounded.VolumeDown, t("Nhỏ", "Vol −")) { dk.bam(PhimTv.GIAM_AM) }
        NutRemote(Icons.AutoMirrored.Rounded.VolumeOff, t("Tắt tiếng", "Mute")) { dk.bam(PhimTv.TAT_TIENG) }
        NutRemote(Icons.AutoMirrored.Rounded.VolumeUp, t("To", "Vol +")) { dk.bam(PhimTv.TANG_AM) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.Rounded.FastRewind, t("Tua lùi", "Rewind")) { dk.bam(PhimTv.TUA_LUI) }
        NutRemote(Icons.Rounded.PlayArrow, t("Phát / dừng", "Play / pause")) { dk.bam(PhimTv.PHAT_DUNG) }
        NutRemote(Icons.Rounded.FastForward, t("Tua tới", "Forward")) { dk.bam(PhimTv.TUA_TOI) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutRemote(Icons.Rounded.ExpandMore, t("Kênh −", "Ch −")) { dk.bam(PhimTv.KENH_XUONG) }
        NutRemote(Icons.Rounded.ExpandLess, t("Kênh +", "Ch +")) { dk.bam(PhimTv.KENH_LEN) }
    }
    if (hoiTat) AlertDialog(
        onDismissRequest = { hoiTat = false },
        icon = { Icon(Icons.Rounded.PowerSettingsNew, null, tint = DoTuoi) },
        title = { Text(t("Tắt tivi?", "Turn the TV off?")) },
        text = { Text(t("Tivi về chế độ chờ và kết nối với điện thoại mất. Bật lại bằng remote thật.",
            "The TV goes to standby and the phone loses the connection. Turn it back on with the real remote.")) },
        confirmButton = { TextButton(onClick = { hoiTat = false; dk.bam(PhimTv.NGUON) }) { Text(t("Tắt", "Turn off"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { hoiTat = false }) { Text(t("Thôi", "Cancel")) } },
        containerColor = The1,
    )
}

/**
 * Thẻ Đang phát (27/10/2026, DangPhatTv): app, trạng thái, thời gian đã phát, tên bài; nút bài trước / phát-dừng / bài sau là
 * phím media của remote. Bản dump không có độ dài bài nên không có thanh tiến độ.
 */
@Composable
private fun TheDangPhat(dk: DieuKhien, b: DangPhatTv.Bai) {
    var bayGio by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    if (b.dangPhat) LaunchedEffect(b) {
        while (true) { bayGio = android.os.SystemClock.elapsedRealtime(); kotlinx.coroutines.delay(1000) }
    }
    val tt = when (b.tt) {
        vn.tivikit.dulieu.DangPhat.PHAT -> t("đang phát", "playing")
        vn.tivikit.dulieu.DangPhat.CHO -> t("đang tải", "loading")
        else -> t("đã dừng", "paused")
    }
    val app = vn.tivikit.dulieu.tenDep(b.goi)
    val gio = vn.tivikit.dulieu.DangPhat.dongHo(b.viTriLuc(bayGio))
    The(t("Đang phát", "Now playing"), Icons.Rounded.MusicNote) {
        // App không gửi tên bài (SmartTube trên Skyworth: description=null, null, null): tên app lên làm dòng chính.
        Phu(if (b.tieuDe != null) "$app · $tt · $gio" else "${tt.replaceFirstChar { it.uppercase() }} · $gio")
        Text(b.tieuDe ?: app, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        b.phu?.let { Text(it, fontSize = 13.sp, color = ChuPhu, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NutRemote(Icons.Rounded.SkipPrevious, t("Bài trước", "Previous")) { dk.bam(PhimTv.BAI_TRUOC) }
            NutRemote(if (b.dangPhat) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (b.dangPhat) t("Dừng", "Pause") else t("Phát", "Play"),
                mau = Nhan) { dk.bam(PhimTv.PHAT_DUNG) }
            NutRemote(Icons.Rounded.SkipNext, t("Bài sau", "Next")) { dk.bam(PhimTv.BAI_SAU) }
        }
    }
}

/**
 * Khung màn hình tivi trực tiếp (26/10/2026, kiểu scrcpy — XemManHinh), ghim trên cùng màn Điều khiển khi đang xem: chạm
 * lên hình là bấm đúng chỗ đó trên tivi. Cao tối đa 40% màn điện thoại để phần remote bên dưới còn chỗ.
 */
@Composable
private fun KhungManHinhTv(x: XemManHinh) {
    val tiLe = x.rong.toFloat() / x.cao.coerceAtLeast(1)
    val caoToiDa = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.4f).dp
    Column(Modifier.fillMaxWidth().background(Nen).padding(start = 16.dp, end = 16.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.widthIn(max = caoToiDa * tiLe).fillMaxWidth().aspectRatio(tiLe).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
            if (!x.cheDoAnh) androidx.compose.ui.viewinterop.AndroidView(
                factory = { c -> android.view.TextureView(c).apply { surfaceTextureListener = x.ngheBeMat } },
                modifier = Modifier.matchParentSize())
            val a = x.anh
            if (a != null && (x.cheDoAnh || !x.coHinh)) androidx.compose.foundation.Image(a.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            if (!x.coHinh && a == null && !x.laLoi) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 3.dp)
            Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { o -> x.cham(o.x / size.width, o.y / size.height) } })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Phu(x.trangThai, if (x.laLoi) DoTuoi else ChuPhu) }
            if (x.laLoi) TextButton(onClick = { x.bat() }) { Text(t("Xem lại", "Retry")) }
            TextButton(onClick = { x.tat() }) { Text(t("Tắt", "Stop")) }
        }
    }
}

/** Vòng điều hướng của remote Android: nhấn giữ OK = `--longpress`. */
@Composable
private fun DPad(dk: DieuKhien) = VongDieuHuong({ dk.bam(PhimTv.LEN) }, { dk.bam(PhimTv.XUONG) }, { dk.bam(PhimTv.TRAI) }, { dk.bam(PhimTv.PHAI) },
    { dk.bam(PhimTv.OK) }, { dk.bam(PhimTv.OK, giu = true) })

/** Vòng điều hướng: 4 mũi tên quanh nút OK, dùng chung cho tivi Android (ADB) và tivi LG. [giuOk] null = OK không nhấn giữ. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VongDieuHuong(len: () -> Unit, xuong: () -> Unit, trai: () -> Unit, phai: () -> Unit, ok: () -> Unit, giuOk: (() -> Unit)? = null) {
    val rung = LocalHapticFeedback.current
    fun bam(viec: () -> Unit) { rung.performHapticFeedback(HapticFeedbackType.TextHandleMove); viec() }
    Box(Modifier.size(250.dp).clip(CircleShape).background(The1)) {
        MuiTen(Icons.Rounded.KeyboardArrowUp, t("Lên", "Up"), Modifier.align(Alignment.TopCenter)) { bam(len) }
        MuiTen(Icons.Rounded.KeyboardArrowDown, t("Xuống", "Down"), Modifier.align(Alignment.BottomCenter)) { bam(xuong) }
        MuiTen(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, t("Trái", "Left"), Modifier.align(Alignment.CenterStart)) { bam(trai) }
        MuiTen(Icons.AutoMirrored.Rounded.KeyboardArrowRight, t("Phải", "Right"), Modifier.align(Alignment.CenterEnd)) { bam(phai) }
        Box(Modifier.align(Alignment.Center).size(100.dp).clip(CircleShape).background(Gradient)
            .combinedClickable(onClick = { bam(ok) }, onLongClick = giuOk?.let { g -> { bam(g) } }),
            contentAlignment = Alignment.Center) {
            Text("OK", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
        }
    }
}

@Composable
private fun MuiTen(icon: ImageVector, ten: String, modifier: Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(76.dp)) { Icon(icon, ten, Modifier.size(44.dp), tint = ChuChinh) }
}

@Composable
internal fun RowScope.NutRemote(icon: ImageVector, nhan: String, mau: Color = ChuChinh, onClick: () -> Unit) {
    val rung = LocalHapticFeedback.current
    Surface(onClick = { rung.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        modifier = Modifier.weight(1f).height(66.dp), shape = RoundedCornerShape(16.dp), color = NenO) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = mau)
            Text(nhan, fontSize = 11.sp, color = ChuPhu, maxLines = 1)
        }
    }
}
