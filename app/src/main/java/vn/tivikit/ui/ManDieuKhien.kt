package vn.tivikit.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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

/** Màn Điều khiển tivi (25/10/2026): remote trên điện thoại + gõ chữ vào ô nhập đang chọn trên tivi. */
@Composable
internal fun ManDieuKhien(vm: PhienLamViec) = Cuon {
    val dk = vm.dieuKhien
    var hoiTat by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Điều khiển tivi", "TV remote"), style = KieuTieuDeLon)
        Phu(t("Bấm như remote thật. Nhấn giữ OK để nhấn giữ trên tivi.", "Works like the real remote. Hold OK for a long press on the TV."))
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
