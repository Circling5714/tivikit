package vn.tivikit.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.BuildConfig
import vn.tivikit.dulieu.BanSaoLuu
import vn.tivikit.dulieu.DuLieu
import vn.tivikit.dulieu.GopNguoc
import vn.tivikit.dulieu.ThongTinTv
import vn.tivikit.t

/**
 * Gửi danh sách cho CleanYourTV (26/10/2026, dulieu/GopNguoc): chọn một tivi đã dọn, soạn debloat.txt + tv.json từ bản
 * sao lưu; đang nối đúng tivi đó thì đối chiếu lại (gói tivi đã bật lại, app người dùng tự cài). Không cần nối tivi.
 */
class GopNguocTivi(private val vm: PhienLamViec) {
    var hien by mutableStateOf(false)
        private set
    var chon by mutableStateOf<BanSaoLuu?>(null)
        private set
    var ban by mutableStateOf<GopNguoc.BanXuat?>(null)
        private set

    /** Tivi đã dọn còn gói TiviKit tắt (bản sao lưu trên điện thoại này). */
    val dsTv: List<BanSaoLuu> get() = vm.dsSaoLuu.filter { it.daTatBoiApp.isNotEmpty() }

    /** Mở hộp, chọn sẵn tivi đang nối (nếu đã dọn), không thì tivi sao lưu gần nhất. */
    fun mo() {
        vm.taiLaiSaoLuu()
        val ds = dsTv
        hien = true
        chonTv(ds.firstOrNull { it.khoa == vm.thongTin?.khoaLuu } ?: ds.maxByOrNull { it.luc })
    }

    fun dong() { hien = false }

    fun chonTv(b: BanSaoLuu?) {
        chon = b; ban = null
        if (b != null) vm.chay(t("Đang soạn danh sách…", "Preparing the list…")) { ban = soan(b) }
    }

    private suspend fun soan(b: BanSaoLuu): GopNguoc.BanXuat {
        val noi = vm.thongTin?.khoaLuu == b.khoa && vm.io { vm.adb.conSong() }
        val (conTat, tuCai) = if (noi) vm.io { vm.tv.goiTatVaTuCai() } else null to null
        // Bản sao lưu lấy từ tivi không có getprop / danh sách gói: dùng của lần nối này.
        val gp = b.getprop.ifBlank { if (noi) vm.getprop else "" }
        val goiCai = b.goiTruoc.ifEmpty { if (noi) vm.trangThai?.caiDat?.toList().orEmpty() else emptyList() }
        return vm.io {
            val du = DuLieu.nap(vm.ctx)
            val mau = du.khopMau(ThongTinTv.tuGetprop(gp)).firstOrNull()?.first
            GopNguoc.tao(b, gp, goiCai, du, mau, conTat, tuCai, BuildConfig.VERSION_NAME, System.currentTimeMillis())
        }
    }
}

/**
 * Hộp gửi danh sách: tóm tắt, mở trang tạo issue "My TV" của CleanYourTV điền sẵn (chép luôn danh sách để dán nếu trang
 * để trống), chép debloat.txt / tv.json, chia sẻ qua Telegram, Zalo… App không gửi gì: người dùng tự bấm gửi.
 */
@Composable
internal fun HopGopNguoc(vm: PhienLamViec) = AlertDialog(
    onDismissRequest = { vm.gopNguoc.dong() },
    icon = { Icon(Icons.Rounded.VolunteerActivism, null, tint = Nhan) },
    title = { Text(t("Gửi danh sách cho CleanYourTV", "Share your list with CleanYourTV")) },
    text = { NoiDungGopNguoc(vm) },
    confirmButton = { TextButton(onClick = { vm.gopNguoc.dong() }) { Text(t("Đóng", "Close")) } },
    containerColor = The1,
)

/** Thân hộp Gửi danh sách: chọn tivi, tóm tắt, các nút gửi / chép / chia sẻ, xem trước debloat.txt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoiDungGopNguoc(vm: PhienLamViec) {
    val g = vm.gopNguoc
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = LocalContext.current
    var bao by remember { mutableStateOf("") }
    var xem by remember { mutableStateOf(false) }
    fun chep(chu: String, thongBao: String) { clip.setText(AnnotatedString(chu)); bao = thongBao }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Phu(t("${DuLieu.SO_MAU} danh sách tivi trong TiviKit là của CleanYourTV, do người dùng khắp nơi góp. Gửi lại các gói bạn đã tắt để người cùng mẫu tivi dọn được an toàn.",
            "TiviKit's ${DuLieu.SO_MAU} TV lists come from CleanYourTV, shared by owners everywhere. Send back the packages you disabled so owners of the same TV can clean it safely."), ChuChinh)
        val ds = g.dsTv
        if (ds.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ds.forEach { b -> ChonChip(b.ten, b.khoa == g.chon?.khoa) { if (!vm.dangBan) { bao = ""; xem = false; g.chonTv(b) } } }
        }
        val b = g.ban
        when {
            g.chon == null -> Phu(t("Điện thoại này chưa dọn tivi nào bằng TiviKit.", "No TV has been cleaned with TiviKit on this phone yet."))
            b == null && vm.dangBan -> Phu(t("Đang soạn danh sách…", "Preparing the list…"))
            b == null -> {
                if (vm.loi.isNotBlank()) Phu(vm.loi, DoTuoi)
                NutPhu(t("Soạn lại", "Try again"), Icons.Rounded.Refresh) { g.chonTv(g.chon) }
            }
            else -> {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(b.tv, fontWeight = FontWeight.SemiBold, color = ChuChinh)
                    Phu("${b.os} · " + t("${b.soGoi} gói · đã tắt ${b.soNgay} ngày", "${b.soGoi} packages · off for ${b.soNgay} days"))
                    Phu("examples/${b.thuMuc}/")
                }
                Phu(b.mau?.let { t("CleanYourTV đã có tivi này (examples/${it.id}): gửi khi danh sách của bạn khác họ, hoặc để báo thêm một máy chạy ổn.",
                    "CleanYourTV already has this TV (examples/${it.id}): send yours if it differs, or to confirm one more working unit.") }
                    ?: t("CleanYourTV chưa có tivi này: danh sách của bạn giúp người cùng mẫu.", "CleanYourTV doesn't have this TV yet: your list helps owners of the same model."), ChuChinh)
                Phu(if (b.daKiemTra) listOfNotNull(
                    t("Đã đối chiếu với tivi.", "Checked against the TV."),
                    if (b.daBatLai.isEmpty()) null else t("${b.daBatLai.size} gói tivi đã bật lại: chỉ ghi chú, không tính.", "${b.daBatLai.size} came back enabled: noted, not listed."),
                    if (b.boTuCai.isEmpty()) null else t("Bỏ ${b.boTuCai.size} app bạn tự cài.", "Left out ${b.boTuCai.size} apps you installed yourself."),
                ).joinToString(" ")
                else t("Tivi này chưa nối nên danh sách lấy theo bản sao lưu. Nối tivi rồi mở lại để đối chiếu (gói đã bật lại, app bạn tự cài).",
                    "This TV isn't connected, so the list comes from the backup. Connect it and reopen to check (re-enabled packages, apps you installed)."))
                if (b.soNgay < 3) Phu(t("Mới tắt ${b.soNgay} ngày: CleanYourTV chỉ nhận danh sách đã dùng vài ngày không lỗi, nên dùng thêm rồi gửi.",
                    "Only ${b.soNgay} days so far: CleanYourTV wants lists people have lived with for a few days, so better wait a little."), Cam)
                if (b.soGoi == 0) Phu(t("Không còn gói nào để gửi.", "Nothing left to send."), Cam) else {
                    NutPhu(t("Mở trang gửi trên GitHub", "Open the GitHub form"), Icons.AutoMirrored.Rounded.OpenInNew) {
                        chep(GopNguoc.chiTiet(b), t("Đã chép danh sách: trang GitHub để trống ô What happened thì dán vào đó.", "List copied: if the GitHub form leaves What happened empty, paste it there."))
                        runCatching { uri.openUri(GopNguoc.linkIssue(b)) }
                    }
                    Phu(t("Cần tài khoản GitHub. Trang điền sẵn mẫu tivi và danh sách; ở mục How far did you get? chọn \"Both worked, here is my list\" nếu tivi chạy ổn (có gì hỏng thì chọn \"Something broke after the debloat\" và ghi thêm) rồi bấm Create.",
                        "Needs a GitHub account. The form comes filled in; under How far did you get? pick \"Both worked, here is my list\" if the TV runs fine (or \"Something broke after the debloat\" and say what), then press Create."))
                    NutPhu(t("Chia sẻ (Telegram, Zalo, email…)", "Share (Telegram, email…)"), Icons.Rounded.Share) {
                        val gui = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, b.tieuDe)
                            .putExtra(Intent.EXTRA_TEXT, b.tieuDe + "\n\n" + GopNguoc.chiTiet(b))
                        runCatching { ctx.startActivity(Intent.createChooser(gui, t("Gửi danh sách", "Share the list"))) }
                    }
                    FlowRow {
                        TextButton(onClick = { xem = !xem }, contentPadding = NUT_NHO) { Text(if (xem) t("Ẩn danh sách", "Hide the list") else t("Xem danh sách", "See the list")) }
                        NutChep("debloat.txt") { chep(b.debloat, t("Đã chép debloat.txt.", "debloat.txt copied.")) }
                        NutChep("tv.json") { chep(b.tvJson, t("Đã chép tv.json.", "tv.json copied.")) }
                    }
                    if (xem) Text(b.debloat, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(The2).padding(10.dp).horizontalScroll(rememberScrollState()))
                    if (bao.isNotEmpty()) Phu(bao, Xanh)
                    Phu(t("Không dùng GitHub? Gửi vào nhóm Telegram t.me/tivikit, tác giả TiviKit chuyển giúp. Danh sách chỉ có tên gói, mẫu tivi, bản Android và firmware: không có IP, số serial hay app bạn tự cài.",
                        "No GitHub? Post it in the Telegram group t.me/tivikit and TiviKit's author will pass it on. The list holds package names, the TV model, Android version and firmware only: no IP, serial number or apps you installed."))
                }
            }
        }
    }
}

/** Đệm nhỏ cho hàng nút Xem / chép: ba nút vừa một hàng trong hộp thoại điện thoại. */
private val NUT_NHO = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

@Composable
private fun NutChep(ten: String, onClick: () -> Unit) = TextButton(onClick = onClick, contentPadding = NUT_NHO) {
    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(ten, fontSize = 14.sp)
}
