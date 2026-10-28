package vn.tivikit.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nayuki.qrcodegen.QrCode
import org.json.JSONObject
import vn.tivikit.ManHinh
import vn.tivikit.dulieu.MaOtp
import vn.tivikit.dulieu.tenDep
import vn.tivikit.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

// ---------------- Khoá trẻ em (23/10/2026; tách khỏi UngDung.kt, thêm mã OTP 28/10/2026) ----------------

internal val TEN_THU = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
internal val TEN_THU_EN = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@Composable
internal fun ManKhoaTre(vm: PhienLamViec) = Cuon {
    val k = vm.khoaTre
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Khoá trẻ em", "Kids lock"), style = KieuTieuDeLon)
        Phu(t("Tới giờ khoá, hay xem hết giờ trong ngày, tivi hiện màn khoá che mọi thứ (cả HDMI, YouTube) và chặn phím remote. Bố mẹ nhập mã trong app xác thực trên điện thoại thì mở ${k.MO_TAM} phút. Tắt tivi bật lại vẫn khoá.",
            "During the lock periods, or once the day's time is used up, the TV shows a lock screen over everything (HDMI and YouTube too) and blocks the remote. A code from the parent's authenticator app unlocks it for ${k.MO_TAM} minutes. It stays locked after a restart."))
        Phu(t("Khoá bật thì giờ tivi luôn tự động theo mạng: ai tắt giờ tự động để chỉnh giờ né khoá, app bật lại ngay. Mở Cài đặt hay gỡ / cài app trên tivi cũng phải nhập mã, nên trẻ không xoá dữ liệu hay gỡ được app khoá.",
            "While the lock is on, the TV keeps its clock automatic: turning automatic time off to dodge the lock gets switched back at once. Opening Settings or installing / removing apps on the TV asks for the code too, so a child can't clear or remove the lock app."))
        Phu(t("Không cầm điện thoại: trên tivi mở ô Khoá trẻ em trong danh sách app (hay nút Phụ huynh dưới bàn phím số của màn khoá), nhập mã là có bảng phụ huynh để mở tivi thêm, khoá / mở từng app, hẹn giờ khoá. Muốn mở nhanh thì gán ô đó vào một nút ở mục Nút remote.",
            "No phone at hand: on the TV open the Kids lock tile in the app list (or the Parent button under the lock screen's keypad); a code opens the parent panel to unlock the TV for longer, lock or open single apps and set lock timers. Map that tile to a remote button under Remote buttons for quick access."))
    }
    k.trenTv?.let { j ->
        TheTrenTv(k, j)
        k.thoiGian?.let { TheThoiGianXem(it) }
    }
    The {
        Hang(t("Bật Khoá trẻ em", "Kids lock on"), t("Tắt thì tivi dùng bình thường, giữ nguyên giờ khoá, giới hạn và mã", "Off: the TV works normally; periods, limits and the code are kept"), k.bat) { k.bat = it }
    }
    TheGioKhoa(k)
    TheGioiHan(vm)
    TheKhoaApp(vm)
    TheHenKhoa(vm)
    TheMaMo(vm)
    if (k.bao.isNotEmpty()) Phu(k.bao, Xanh)
    NutChinh(t("Lưu & áp dụng lên tivi", "Save & apply to the TV"), enabled = !vm.dangBan, icon = Icons.Rounded.Lock) { k.luu() }
    var hoiTat by remember { mutableStateOf(false) }
    if (k.trenTv != null) NutPhu(t("Tắt và gỡ Khoá trẻ em", "Turn off and remove"), Icons.Rounded.LockOpen) { hoiTat = true }
    if (hoiTat) AlertDialog(
        onDismissRequest = { hoiTat = false },
        icon = { Icon(Icons.Rounded.LockOpen, null, tint = Cam) },
        title = { Text(t("Gỡ Khoá trẻ em?", "Remove the Kids lock?")) },
        text = { Text(t("Tivi thôi khoá và gỡ app khoá: giờ khoá, giới hạn, khoá theo app và mã OTP trên tivi mất hết, muốn dùng lại phải cài và quét mã mới.",
            "The TV stops locking and the lock app is removed: its periods, limits, app locks and OTP code are gone; using it again needs a fresh setup and code.")) },
        confirmButton = { TextButton(onClick = { hoiTat = false; k.tat() }) { Text(t("Gỡ khoá", "Remove"), color = Cam) } },
        dismissButton = { TextButton(onClick = { hoiTat = false }) { Text(t("Thôi", "Cancel")) } },
    )
    Phu(t("Lưu ý: khoá không chống được khôi phục cài đặt gốc hay đổi sang thiết bị khác cắm HDMI. Với hộp Android cắm vào tivi, trẻ vẫn đổi nguồn tivi được. Bấm Khôi phục tivi này cũng gỡ khoá (phải nhập mã).",
        "Note: it can't stop a factory reset or another device on HDMI; with a box plugged into a TV, the TV's own input switch still works. Restore this TV also removes the lock (it asks for the code)."))
}

@Composable
private fun TheTrenTv(k: KhoaTreTivi, j: JSONObject) = The(t("Trên tivi", "On the TV"), Icons.Rounded.Lock) {
    val bat = j.optBoolean("bat"); val chay = j.optBoolean("dichVuChay"); val dangKhoa = j.optBoolean("dangKhoa")
    val moDen = j.optLong("moTamDen")
    val gio = { ms: Long -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms)) }
    Phu(if (!bat) t("Khoá đang tắt.", "The lock is off.")
        else if (!chay) t("Khoá đang bật nhưng dịch vụ chưa chạy (tắt tivi bật lại).", "On, but the service isn't running (restart the TV).")
        else if (dangKhoa && j.optString("lyDo") == "het-gio") t("Tivi đang khoá: đã xem hết giờ hôm nay.", "The TV is locked: today's time is used up.")
        else if (dangKhoa && j.optString("lyDo") == "gio-sai") t("Tivi đang khoá: giờ tivi chưa đúng (chưa lấy được giờ từ mạng).", "The TV is locked: its clock isn't right yet (no network time).")
        else if (dangKhoa) t("Tivi đang khoá.", "The TV is locked now.")
        else if (moDen > System.currentTimeMillis()) t("Đang mở tạm tới ${gio(moDen)}.", "Unlocked until ${gio(moDen)}.")
        else t("Khoá đang bật, ngoài giờ khoá.", "On, outside the lock periods."), if (bat && chay) Xanh else ChuPhu)
    if (bat) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChonChip(t("Mở tạm ${k.MO_TAM} phút", "Unlock ${k.MO_TAM} min"), false) { k.moTam(k.MO_TAM) }
        ChonChip(t("Khoá lại ngay", "Lock now"), false) { k.moTam(0) }
    }
    val appKhoa = j.optJSONArray("appKhoa")?.let { a -> (0 until a.length()).map { tenDep(a.getString(it)) } }.orEmpty()
    if (appKhoa.isNotEmpty()) Phu(t("App đang khoá: ${appKhoa.joinToString(", ")}.", "Apps locked now: ${appKhoa.joinToString(", ")}."), Cam)
    if (j.optBoolean("baoVe")) Phu(t("Khoá đang bật nên trên tivi, mở Cài đặt, gỡ / cài app hay cho phép gỡ lỗi ADB đều phải nhập mã.",
        "With the lock on, opening Settings, installing or removing apps and allowing ADB debugging on the TV all ask for the code."))
    // App khoá cài từ trước khi đổi tên gói (com.icm.cleantv.khoa), hay bản trước 28/10/2026: còn mở bằng PIN.
    if (j.optBoolean("banCu")) Phu(t("Đây là Khoá trẻ em của TiviKit bản cũ, vẫn khoá bằng PIN cũ. Cài mã OTP ở thẻ Mã mở khoá rồi bấm Lưu: app cài bản mới với giờ khoá này và gỡ bản cũ.",
        "This is the Kids lock from the old TiviKit and it still works with the old PIN. Set up the OTP code in the Unlock code card and tap Save: the app installs the new version with these periods and removes the old one."), Cam)
    else if (!j.has("coOtp")) Phu(t("App khoá trên tivi là bản cũ, mở bằng PIN. Khoá trẻ em giờ dùng mã OTP: cài mã ở thẻ Mã mở khoá rồi bấm Lưu & áp dụng (giữ giờ khoá; tới lúc đó PIN cũ vẫn mở được).",
        "The lock app on the TV is an older version that uses a PIN. The Kids lock now uses an OTP code: set it up in the Unlock code card and tap Save & apply (the schedule is kept; the old PIN works until then)."), Cam)
    if (k.goDuoc) Phu(t("Đang vào không cần mã vì yêu cầu Quên mã đã quá 24 giờ. Cài mã OTP mới ở thẻ Mã mở khoá rồi Lưu.",
        "You're in without the code because the Forgot code request is over 24 hours old. Set up a new OTP code in the Unlock code card and Save."), Cam)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheGioKhoa(k: KhoaTreTivi) = The(t("Giờ khoá", "Lock periods"), Icons.Rounded.Schedule) {
    if (k.khung.isEmpty()) Phu(t("Chưa có khung giờ khoá.", "No lock periods."))
    k.khung.forEachIndexed { i, kh ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${kh.tu} – ${kh.den}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (kh.ngay.size == 7) t("Mọi ngày", "Every day") else kh.ngay.sorted().joinToString(" ") { t(TEN_THU[it - 1], TEN_THU_EN[it - 1]) }, fontSize = 12.sp, color = ChuPhu)
            }
            IconButton(onClick = { k.khung.removeAt(i) }) { Icon(Icons.Rounded.Close, t("Xoá", "Remove")) }
        }
    }
    Phu(t("Thêm nhanh:", "Quick add:"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChonChip(t("Tối 21:00–06:00", "Night 21:00–06:00"), false) { k.khung += KhoaTreTivi.Khung("21:00", "06:00", (1..7).toSet()) }
        ChonChip(t("Giờ học 14:00–16:30 (T2–T6)", "Study 14:00–16:30 (Mon–Fri)"), false) { k.khung += KhoaTreTivi.Khung("14:00", "16:30", (1..5).toSet()) }
        ChonChip(t("Trưa 12:00–13:30", "Nap 12:00–13:30"), false) { k.khung += KhoaTreTivi.Khung("12:00", "13:30", (1..7).toSet()) }
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
        if (k.themKhung(tu, den, ngay)) { tu = ""; den = "" }
    }
    Phu(t("Khung qua đêm (21:00–06:00) tính theo ngày bắt đầu: chọn T2 là khoá từ tối thứ Hai tới sáng thứ Ba.", "Overnight periods count from the starting day."))
}

// ---------------- Khoá theo app (28/10/2026) ----------------

/** "SmartTube, YouTube và 3 app khác". */
private fun tenNhom(goi: Collection<String>): String {
    val ten = goi.map { tenDep(it) }.sorted()
    return if (ten.size <= 3) ten.joinToString(", ") else ten.take(3).joinToString(", ") + t(" và ${ten.size - 3} app khác", " and ${ten.size - 3} more")
}

private fun chuNgay(ngay: Set<Int>) = if (ngay.size == 7) t("mọi ngày", "every day") else ngay.sorted().joinToString(" ") { t(TEN_THU[it - 1], TEN_THU_EN[it - 1]) }

/** Một dòng tả luật: "20:00–06:00 mọi ngày", "Tối đa 1 giờ mỗi ngày · hôm nay 35 phút", "Khoá luôn". */
private fun moTaLuat(l: KhoaTreTivi.LuatApp, dung: JSONObject?): String = when {
    l.luon -> t("Khoá luôn (bố mẹ nhập mã thì mở tạm)", "Always locked (a parent's code opens it for a while)")
    l.khung.isNotEmpty() -> l.khung.joinToString("; ") { "${it.tu}–${it.den} ${chuNgay(it.ngay)}" }
    else -> t("Tối đa ", "At most ") + vn.tivikit.dulieu.ThoiGianXem.chuGioiHan(l.phutNgay) + t(" mỗi ngày", " a day") +
        (dung?.optLong(l.id)?.takeIf { dung.has(l.id) }?.let { t(" · hôm nay ", " · today ") + vn.tivikit.dulieu.ThoiGianXem.chu(it) } ?: "")
}

/** Chọn app trên tivi (nhiều app): nút "Nhóm xem video" chọn / bỏ cả nhóm app xem video có trên tivi. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChonApp(ds: List<String>, video: List<String>, chon: Set<String>, doi: (Set<String>) -> Unit) {
    if (ds.isEmpty()) { Phu(t("Chưa đọc được danh sách app trên tivi.", "Couldn't read the TV's app list.")); return }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (video.isNotEmpty()) {
            val du = chon.containsAll(video)
            ChonChip(t("Nhóm xem video (${video.size})", "Video apps (${video.size})"), du) { doi(if (du) chon - video.toSet() else chon + video) }
        }
        ds.forEach { g -> ChonChip(tenDep(g), g in chon) { doi(if (g in chon) chon - g else chon + g) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheKhoaApp(vm: PhienLamViec) = The(t("Khoá theo app", "App locks"), Icons.Rounded.Apps) {
    val k = vm.khoaTre
    Phu(t("Khoá riêng từng app, ví dụ SmartTube, YouTube, TV360. Mở app đang khoá thì tivi về Home và hiện màn khoá của app đó; bố mẹ nhập mã thì mở app ${k.MO_TAM} phút. Nên khoá cả nhóm: chỉ khoá SmartTube thì trẻ chuyển sang YouTube hay trình duyệt.",
        "Lock single apps, e.g. SmartTube, YouTube, TV360. Opening a locked app sends the TV Home and shows that app's lock screen; a parent's code opens it for ${k.MO_TAM} minutes. Lock the whole group: lock only SmartTube and a child switches to YouTube or a browser."))
    val dung = k.trenTv?.optJSONObject("dungApp")
    k.luatApp.forEachIndexed { i, l ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tenNhom(l.goi), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(moTaLuat(l, dung), fontSize = 12.sp, color = ChuPhu)
            }
            IconButton(onClick = { k.luatApp.removeAt(i) }) { Icon(Icons.Rounded.Close, t("Xoá", "Remove")) }
        }
    }
    Text(t("Thêm luật", "Add a rule"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    ChonApp(k.dsApp, k.videoTrenTv, k.chonLuat) { k.chonLuat = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChonChip(t("Theo giờ", "By time"), k.kieuLuat == KhoaTreTivi.KieuLuat.GIO) { k.kieuLuat = KhoaTreTivi.KieuLuat.GIO }
        ChonChip(t("Giới hạn mỗi ngày", "Daily limit"), k.kieuLuat == KhoaTreTivi.KieuLuat.PHUT) { k.kieuLuat = KhoaTreTivi.KieuLuat.PHUT }
        ChonChip(t("Khoá luôn", "Always"), k.kieuLuat == KhoaTreTivi.KieuLuat.LUON) { k.kieuLuat = KhoaTreTivi.KieuLuat.LUON }
    }
    var tu by remember { mutableStateOf("20:00") }
    var den by remember { mutableStateOf("06:00") }
    var ngay by remember { mutableStateOf((1..7).toSet()) }
    when (k.kieuLuat) {
        KhoaTreTivi.KieuLuat.GIO -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(tu, { tu = it.take(5) }, label = { Text(t("Khoá từ", "Lock from")) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(den, { den = it.take(5) }, label = { Text(t("Đến", "To")) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d -> ChonChip(t(TEN_THU[d - 1], TEN_THU_EN[d - 1]), d in ngay) { ngay = if (d in ngay) ngay - d else ngay + d } }
            }
        }
        KhoaTreTivi.KieuLuat.PHUT -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 60, 90, 120, 180).forEach { p -> ChonChip(vn.tivikit.dulieu.ThoiGianXem.chuGioiHan(p), k.phutLuat == p) { k.phutLuat = p } }
        }
        KhoaTreTivi.KieuLuat.LUON -> Phu(t("Khoá tới khi bố mẹ xoá luật này; nhập mã trên tivi thì mở tạm từng lần.", "Locked until a parent removes this rule; a code on the TV opens it for a while."))
    }
    NutPhu(t("Thêm luật này", "Add this rule"), Icons.Rounded.Add) { k.themLuat(tu, den, ngay) }
    Phu(t("Luật mới có hiệu lực sau khi bấm Lưu & áp dụng.", "New rules take effect after Save & apply."))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheHenKhoa(vm: PhienLamViec) = The(t("Hẹn giờ khoá app", "App lock timer"), Icons.Rounded.Timer) {
    val k = vm.khoaTre
    Phu(t("Khoá app sau một lúc, tới hết hôm nay. Ví dụ cho con xem SmartTube thêm 30 phút: chọn SmartTube, 30 phút, bấm Hẹn khoá. Áp dụng ngay, không cần Lưu; còn 5 phút thì tivi báo trước.",
        "Lock apps after a while, until the end of today. E.g. 30 more minutes of SmartTube: pick SmartTube, 30 minutes, Set the timer. It applies at once, no Save needed; the TV warns 5 minutes ahead."))
    val hen = k.trenTv?.optJSONArray("hen")
    val bay = System.currentTimeMillis()
    if (hen != null) for (i in 0 until hen.length()) {
        val h = hen.getJSONObject(i)
        val goi = h.optJSONArray("goi")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val tu = h.optLong("tu")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tenNhom(goi), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (tu <= bay) t("Đang khoá tới hết hôm nay", "Locked until the end of today")
                    else t("Khoá lúc ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tu))}, tới hết hôm nay", "Locks at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tu))}, until the end of today"),
                    fontSize = 12.sp, color = ChuPhu)
            }
            IconButton(onClick = { if (!vm.dangBan) k.boHen(tu, goi.toSet()) }) { Icon(Icons.Rounded.Close, t("Bỏ hẹn", "Remove")) }
        }
    }
    ChonApp(k.dsApp, k.videoTrenTv, k.chonHen) { k.chonHen = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0, 15, 30, 45, 60, 90, 120).forEach { p ->
            ChonChip(if (p == 0) t("Khoá ngay", "Now") else t("Sau ", "In ") + vn.tivikit.dulieu.ThoiGianXem.chuGioiHan(p), k.sauPhut == p) { k.sauPhut = p }
        }
    }
    val duoc = k.trenTv != null && (k.coOtp || k.goDuoc)
    NutPhu(t("Hẹn khoá", "Set the timer"), Icons.Rounded.Timer, enabled = !vm.dangBan && duoc && k.chonHen.isNotEmpty()) { k.henKhoa() }
    if (!duoc) Phu(t("Lưu khoá lên tivi (với mã OTP) trước rồi mới hẹn khoá được.", "Save the lock to the TV (with the OTP code) first, then set timers."))
    else if (k.trenTv?.optBoolean("bat") != true) Phu(t("Khoá trẻ em đang tắt: hẹn khoá chỉ chạy khi bật.", "The Kids lock is off: timers only work while it's on."), Cam)
}

// ---------------- Mã mở khoá (28/10/2026) ----------------

/** Chép chữ nhạy cảm (mã gốc, mã dự phòng): Android 13+ không hiện nội dung trong ô xem trước bộ nhớ tạm. */
private fun chepKin(ctx: Context, chu: String) {
    val clip = ClipData.newPlainText("TiviKit", chu)
    if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    runCatching { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip) }
    Toast.makeText(ctx, t("Đã chép", "Copied"), Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheMaMo(vm: PhienLamViec) = The(t("Mã mở khoá", "Unlock code"), Icons.Rounded.Key) {
    val k = vm.khoaTre
    val ctx = LocalContext.current
    when (k.buoc) {
        KhoaTreTivi.Buoc.KHONG -> if (k.coOtp && !k.goDuoc) {
            val con = k.trenTv?.optInt("soDuPhong") ?: 0
            Phu(t("Đã cài mã OTP: mở khoá bằng mã 6 số trong app xác thực, mỗi mã dùng một lần. Còn $con mã dự phòng.",
                "OTP code is set: unlock with the 6-digit code from the authenticator app, each code works once. $con backup codes left."), if (con <= 2) Cam else ChuPhu)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Tạo mã dự phòng mới", "New backup codes"), false) { k.taoDuPhong() }
                ChonChip(t("Đổi mã gốc", "Change the root key"), false) { k.taoMa() }
            }
        } else {
            Phu(t("Khoá mở bằng mã OTP thay PIN: mã 6 số đổi mỗi 30 giây trong app xác thực trên điện thoại của bố mẹ (Google Authenticator, Microsoft Authenticator, app Mật khẩu của iPhone…), mỗi mã dùng một lần. Trẻ có nhìn thấy bố mẹ bấm cũng không dùng lại được.",
                "The lock opens with an OTP code instead of a PIN: a 6-digit code that changes every 30 seconds in the parent's authenticator app (Google Authenticator, Microsoft Authenticator, iPhone Passwords…), each one works once. A child watching can't reuse it."))
            NutChinh(t("Tạo mã OTP", "Create the OTP code"), enabled = !vm.dangBan, icon = Icons.Rounded.Key) { k.taoMa() }
            Phu(t("Nhà đã cài mã cho tivi khác? Dán mã gốc đã cất để dùng chung một mã:", "Already set up another TV at home? Paste the root key you stored to share one code:"))
            OutlinedTextField(k.khoaDan, { k.khoaDan = it.take(64) }, label = { Text(t("Mã gốc (32 chữ)", "Root key (32 characters)")) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            if (k.khoaDan.isNotBlank()) NutPhu(t("Dùng mã gốc này", "Use this root key"), Icons.Rounded.Check) { k.dungKhoaDan() }
        }
        KhoaTreTivi.Buoc.QUET -> {
            val khoa = k.khoaMoi ?: return@The
            BuocSo(1, t("Quét mã QR này bằng app xác thực (dấu + / Thêm mã / Quét QR).", "Scan this QR code with the authenticator app (+ / Add code / Scan a QR code)."))
            MaQr(MaOtp.lienKet(khoa), Modifier.align(Alignment.CenterHorizontally).widthIn(max = 260.dp).fillMaxWidth())
            if (!ManHinh.laTv) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Thêm vào app xác thực trên máy này", "Add to the authenticator on this phone"), false) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MaOtp.lienKet(khoa)))) }.onFailure {
                        Toast.makeText(ctx, t("Điện thoại này chưa có app xác thực: cài Google Authenticator, hoặc quét QR bằng điện thoại khác.",
                            "No authenticator app on this phone: install Google Authenticator, or scan the QR with another phone."), Toast.LENGTH_LONG).show()
                    }
                }
                ChonChip(t("Chép mã gốc", "Copy the root key"), false) { chepKin(ctx, khoa) }
            }
            Text(t("Mã gốc", "Root key"), fontSize = 13.sp, color = ChuPhu)
            SelectionContainer { Text(MaOtp.nhom(khoa), fontFamily = FontFamily.Monospace, fontSize = 16.sp, letterSpacing = 1.sp) }
            Phu(t("Cất mã gốc này (ghi giấy, trình quản lý mật khẩu): đổi điện thoại thì nhập lại vào app xác thực mới, tivi khác trong nhà dùng chung được. Đừng để ảnh chụp ở nơi trẻ xem được.",
                "Keep this root key (on paper, in a password manager): add it again on a new phone, and share it with other TVs at home. Don't leave a screenshot where a child can see it."))
            BuocSo(2, t("Nhập mã 6 số app xác thực đang hiện cho TiviKit.", "Enter the 6-digit code the authenticator shows for TiviKit."))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(k.maThu, { k.maThu = it.filter(Char::isDigit).take(MaOtp.SO_CHU_SO) }, label = { Text(t("Mã 6 số", "6-digit code")) }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { k.thuMa() }))
                Button(onClick = { k.thuMa() }, enabled = k.maThu.length == MaOtp.SO_CHU_SO) { Text(t("Kiểm tra", "Check")) }
            }
            Phu(t("Gợi ý: app xác thực nằm trên chính điện thoại này thì bật khoá cho nó (vân tay / mã máy), không thì trẻ cầm điện thoại vẫn xem được mã.",
                "Tip: if the authenticator is on this same phone, lock it (fingerprint / phone PIN), otherwise a child holding the phone can still read the code."))
            NutPhu(t("Huỷ", "Cancel"), Icons.Rounded.Close) { k.huyMaMoi() }
        }
        KhoaTreTivi.Buoc.CAT -> {
            val chu = k.duPhongMoi.joinToString("\n") { MaOtp.chuDuPhong(it) }
            Text(t("${MaOtp.SO_DU_PHONG} mã dự phòng", "${MaOtp.SO_DU_PHONG} backup codes"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Phu(t("Mỗi mã mở được một lần, dùng khi không có điện thoại hay giờ tivi sai. Ghi ra giấy, cất nơi trẻ không lấy được. Chỉ hiện lần này.",
                "Each one works once, for when there's no phone or the TV's clock is wrong. Write them down and keep them out of a child's reach. Shown only now."))
            SelectionContainer {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(12.dp)) {
                    k.duPhongMoi.chunked(2).forEach { hang ->
                        Row(Modifier.fillMaxWidth()) {
                            hang.forEach { Text(MaOtp.chuDuPhong(it), Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 16.sp, lineHeight = 26.sp) }
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Chép", "Copy"), false) { chepKin(ctx, chu) }
                if (!ManHinh.laTv) ChonChip(t("Chia sẻ / in", "Share / print"), false) {
                    val gui = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, t("Mã dự phòng Khoá trẻ em (TiviKit)", "Kids lock backup codes (TiviKit)"))
                        .putExtra(Intent.EXTRA_TEXT, t("Mã dự phòng Khoá trẻ em (TiviKit), mỗi mã dùng một lần:\n", "Kids lock backup codes (TiviKit), each works once:\n") + chu)
                    runCatching { ctx.startActivity(Intent.createChooser(gui, t("Cất mã dự phòng", "Store the backup codes"))) }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).toggleable(k.daCat, role = Role.Checkbox) { k.daCat = it }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OTich(k.daCat)
                Spacer(Modifier.width(10.dp))
                Text(if (k.khoaMoi != null) t("Đã cất mã gốc và mã dự phòng", "I've stored the root key and the backup codes") else t("Đã cất mã dự phòng", "I've stored the backup codes"), fontSize = 15.sp)
            }
            Phu(if (k.coOtp && !k.goDuoc) t("Bấm Lưu & áp dụng để tivi dùng mã mới; tới lúc đó mã cũ vẫn dùng.", "Tap Save & apply for the TV to use the new codes; the old ones work until then.")
                else t("Bấm Lưu & áp dụng để cài khoá với mã này.", "Tap Save & apply to set up the lock with this code."))
            NutPhu(t("Huỷ", "Cancel"), Icons.Rounded.Close) { k.huyMaMoi() }
        }
    }
}

/**
 * Mã QR của [noiDung] (thư viện qrcodegen của Project Nayuki, MIT): ô đen trên nền trắng, viền trắng 4 ô — giao diện tối
 * máy quét vẫn đọc được. Ô vẽ khít điểm ảnh cho khỏi lằn trắng giữa các ô.
 */
@Composable
internal fun MaQr(noiDung: String, modifier: Modifier = Modifier) {
    val qr = remember(noiDung) { QrCode.encodeText(noiDung, QrCode.Ecc.MEDIUM) }
    Canvas(modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        val o = floor(size.width / (qr.size + 8))
        val le = floor((size.width - o * qr.size) / 2)
        for (y in 0 until qr.size) for (x in 0 until qr.size) if (qr.getModule(x, y))
            drawRect(Color.Black, Offset(le + x * o, le + y * o), Size(o, o))
    }
}

/** Hộp nhập mã Khoá trẻ em trước khi vào màn khoá, Dòng lệnh ADB hay Khôi phục (tivi kiểm tra mã). */
@Composable
internal fun HopNhapMa(vm: PhienLamViec) {
    val k = vm.khoaTre
    val h = k.hoiMa ?: return
    AlertDialog(
        onDismissRequest = { if (!vm.dangBan) k.dongHoiMa() },
        icon = { Icon(Icons.Rounded.Lock, null, tint = Nhan) },
        title = { Text(if (k.hoiQuen) t("Quên mã?", "Forgot the code?") else t("Nhập mã Khoá trẻ em", "Kids lock code")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (k.hoiQuen) {
                    Phu(t("Mất cả app xác thực lẫn mã dự phòng thì gửi yêu cầu cho tivi: 24 giờ sau vào được Khoá trẻ em không cần mã để cài mã mới.",
                        "Lost both the authenticator and the backup codes? Send a request to the TV: after 24 hours you can open the Kids lock without the code and set a new one."))
                    Phu(t("Trong lúc chờ, tivi hiện lời nhắc; ai nhập đúng mã thì yêu cầu bị huỷ. Trẻ cầm điện thoại bấm thử thì bố mẹ thấy và huỷ được.",
                        "Meanwhile the TV shows a reminder, and entering the right code cancels the request. If a child tries this with your phone, you'll see it and can cancel."))
                } else {
                    Text(h.lyDo, fontSize = 15.sp)
                    OutlinedTextField(k.maNhap, { k.maNhap = it.filter(Char::isDigit).take(MaOtp.SO_CHU_SO_DU_PHONG) },
                        label = { Text(t("Mã 6 hoặc 8 số", "6- or 8-digit code")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { k.nhapMa() }))
                    Phu(t("Mã 6 số của TiviKit trong app xác thực (Google Authenticator, Microsoft Authenticator, Mật khẩu trên iPhone…), hoặc một mã dự phòng 8 số.",
                        "TiviKit's 6-digit code in the authenticator app (Google Authenticator, Microsoft Authenticator, iPhone Passwords…), or an 8-digit backup code."))
                }
                if (vm.dangBan) Phu(vm.thongBao)
                if (k.baoMa.isNotEmpty()) Phu(k.baoMa, Cam)
                if (vm.loi.isNotEmpty()) Phu(vm.loi, DoTuoi)
            }
        },
        confirmButton = {
            if (k.hoiQuen) TextButton(onClick = { k.quenMa() }, enabled = !vm.dangBan) { Text(t("Gửi yêu cầu", "Send the request"), fontWeight = FontWeight.SemiBold) }
            else TextButton(onClick = { k.nhapMa() }, enabled = !vm.dangBan) { Text(t("Mở", "Open"), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                if (!k.hoiQuen) TextButton(onClick = { k.hoiQuen = true }) { Text(t("Quên mã?", "Forgot?")) }
                TextButton(onClick = { if (k.hoiQuen) k.hoiQuen = false else k.dongHoiMa() }) { Text(if (k.hoiQuen) t("Quay lại", "Back") else t("Huỷ", "Cancel")) }
            }
        },
        containerColor = The1,
    )
}
