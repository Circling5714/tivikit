package vn.tivikit.cast

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Màn Cast SmartTube trên tivi (28/10/2026, biểu tượng trong danh sách app): mã TV chữ to cho khách liên kết (không cần
 * cầm điện thoại có TiviKit), điện thoại đã cast (duyệt, chặn, xoá), tạm ngừng nhận cast, đổi tên, huỷ mọi liên kết.
 * Việc quản lý cần mã bố mẹ khi Khoá trẻ em đang chạy (ManCoMa); xem mã thì ai cũng xem được.
 */
class ManCast : ManCoMa() {
    private val kho by lazy { Kho(this) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { ManHinh() }
    }

    private fun bao(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    @Composable
    private fun ManHinh() {
        var chay by remember { mutableStateOf(true) }
        var ten by remember { mutableStateOf("") }
        var daNoi by remember { mutableStateOf(false) }
        var biChe by remember { mutableStateOf(false) }
        var nhanCast by remember { mutableStateOf(true) }
        var dsMay by remember { mutableStateOf(emptyList<DienThoai>()) }
        var dangNoi by remember { mutableStateOf(emptySet<String>()) }
        var bay by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var ma by remember { mutableStateOf("") }
        var maLuc by remember { mutableLongStateOf(0L) }
        var lanLayMa by remember { mutableIntStateOf(0) }
        var chon by remember { mutableStateOf<DienThoai?>(null) }
        var hoiHuy by remember { mutableStateOf(false) }
        var doiTen by remember { mutableStateOf(false) }
        val frDau = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            while (true) {
                val may = CastDichVu.dangChay?.may
                chay = may != null
                ten = may?.tenHien() ?: kho.ten.orEmpty()
                daNoi = may?.daNoi == true
                biChe = may?.biChe == true || kho.biChe
                nhanCast = kho.nhanCast
                dsMay = withContext(Dispatchers.IO) { kho.dienThoai }
                dangNoi = may?.dangNoi ?: emptySet()
                bay = System.currentTimeMillis()
                delay(1_000)
            }
        }
        // Mã TV sống chừng 5 phút: lấy mã mới mỗi 4,5 phút; chưa nối được YouTube (hay vừa huỷ mọi liên kết, đang đổi
        // screen) thì thử lại sau 2 giây.
        LaunchedEffect(lanLayMa) {
            ma = ""
            while (true) {
                val m = withContext(Dispatchers.IO) { runCatching { CastDichVu.dangChay?.may?.xinMaTv() }.getOrNull() }
                if (m != null) { ma = m; maLuc = System.currentTimeMillis(); delay(270_000) } else delay(2_000)
            }
        }
        LaunchedEffect(Unit) { delay(300); runCatching { frDau.requestFocus() } }

        val sapXep = dsMay.sortedWith(compareByDescending<DienThoai> { it.id in dangNoi }
            .thenByDescending { it.trangThai == DienThoai.TrangThai.CHO }.thenByDescending { it.lanCuoi })

        NenCast {
            Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chu("Cast SmartTube", 30.sp, dam = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(10.dp).background(if (daNoi) Mau.xanh else Mau.cam, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Chu(when {
                        !chay -> t("Dịch vụ Cast đang tắt: bật bằng TiviKit trên điện thoại", "The Cast service is off: turn it on with TiviKit on a phone")
                        daNoi -> "$ten · " + t("đã nối YouTube", "connected to YouTube")
                        else -> "$ten · " + t("đang nối YouTube…", "connecting to YouTube…")
                    }, 16.sp, Mau.phu, dong = 1)
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TheMa(Modifier.weight(1.4f).fillMaxHeight(), ma, maLuc, bay, ten, biChe, nhanCast)
                    The(Modifier.weight(1f).fillMaxHeight()) {
                        Chu(t("Điện thoại đã cast", "Phones that cast"), 22.sp, dam = FontWeight.SemiBold)
                        if (sapXep.isEmpty()) Chu(t("Chưa có điện thoại nào cast lên tivi này. Máy lạ cast lần đầu thì tivi hỏi trước khi phát.",
                            "No phone has cast to this TV yet. A new phone casting for the first time is asked about on the TV first."), 15.sp, Mau.phu)
                        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(sapXep, key = { _, d -> d.id }) { i, d ->
                                DongMay(d, d.id in dangNoi, bay, if (i == 0) frDau else null) { chon = d }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Nut(if (nhanCast) t("Nhận cast: đang bật", "Casting: on") else t("Nhận cast: đang tắt", "Casting: off"), {
                        canMa(t("bật / tắt nhận cast", "turn casting on / off")) { kho.nhanCast = !kho.nhanCast; nhanCast = kho.nhanCast }
                    }, mau = if (nhanCast) Mau.xanh else Mau.cam, fr = if (sapXep.isEmpty()) frDau else null)
                    Nut(t("Đổi tên", "Rename"), { canMa(t("đổi tên tivi trong nút Cast", "rename the TV under Cast")) { doiTen = true } })
                    Nut(t("Huỷ mọi liên kết", "Unlink all phones"), { canMa(t("huỷ mọi liên kết", "unlink all phones")) { hoiHuy = true } }, mau = Mau.do_)
                }
            }
        }

        chon?.let { d -> HopMay(d, bay) { chon = null } }
        if (hoiHuy) HopHuy({ hoiHuy = false }) {
            hoiHuy = false
            val may = CastDichVu.dangChay?.may
            if (may == null) bao(t("Dịch vụ Cast đang tắt.", "The Cast service is off.")) else {
                may.huyLienKet(); dsMay = emptyList(); lanLayMa++
                bao(t("Đã huỷ mọi liên kết. Điện thoại muốn cast lại thì nhập mã TV mới.", "All phones are unlinked. Phones need the new TV code to cast again."))
            }
        }
        if (doiTen) HopTen(kho.ten.orEmpty(), { doiTen = false }) { moi ->
            doiTen = false
            kho.ten = moi.trim().take(60).ifEmpty { null }
            ten = CastDichVu.dangChay?.may?.tenHien() ?: kho.ten.orEmpty()
            // Mã đang hiện xin theo tên cũ (YouTube gửi tên kèm mã cho máy nhập mã): xin mã mới theo tên mới.
            lanLayMa++
            bao(t("Đã đổi tên. Điện thoại thấy tên mới ở lần mở nút Cast sau.", "Renamed. Phones see the new name next time they open Cast."))
        }
    }

    /** Khung mã TV: mã chữ to, đếm ngược tới mã mới, cách nhập trên điện thoại. */
    @Composable
    private fun TheMa(modifier: Modifier, ma: String, maLuc: Long, bay: Long, ten: String, biChe: Boolean, nhanCast: Boolean) = The(modifier) {
        Chu(t("Liên kết điện thoại", "Link a phone"), 22.sp, dam = FontWeight.SemiBold)
        Chu(t("Mã TV", "TV code"), 15.sp, Mau.mo)
        // 12 số cỡ 46sp vừa khung trên màn 960dp (720p hay 1080p đều vậy).
        Chu(if (ma.isEmpty()) t("Đang lấy mã…", "Getting a code…") else ma.chunked(3).joinToString(" "),
            if (ma.isEmpty()) 30.sp else 46.sp, if (ma.isEmpty()) Mau.phu else Mau.chu, FontWeight.Bold, cachChu = 2.sp, dong = 1)
        if (ma.isNotEmpty()) {
            val con = ((maLuc + 300_000 - bay) / 1000).coerceAtLeast(0)
            Chu(t("Mã dùng được thêm ", "Valid for another ") + "%d:%02d".format(con / 60, con % 60) + t(", tự đổi mã mới.", ", then a new one appears."), 14.sp, Mau.mo)
        }
        Spacer(Modifier.height(4.dp))
        Chu(t("Trên điện thoại (iPhone hay Android): mở YouTube → nút Cast → Thêm thiết bị / Liên kết bằng mã TV → nhập mã trên. Liên kết một lần là điện thoại nhớ \"$ten\".",
            "On a phone (iPhone or Android): open YouTube → Cast button → Add device / Link with TV code → type the code above. Linked once, the phone remembers \"$ten\"."), 15.sp, Mau.phu)
        Chu(if (biChe) t("Tivi này có sẵn Cast của hãng chen trước nên điện thoại không tự thấy tivi qua Wi-Fi: dùng mã TV.",
            "This TV's own Cast answers first, so phones won't find it over Wi-Fi by themselves: use the TV code.")
            else t("Điện thoại cùng Wi-Fi thường tự thấy \"$ten\" trong nút Cast, không cần mã.", "Phones on the same Wi-Fi usually find \"$ten\" under Cast without a code."), 14.sp, Mau.mo)
        Chu(t("Máy lạ cast lần đầu: tivi hỏi trước khi phát (cho phép luôn, hôm nay, 24 giờ hay chặn).",
            "A new phone casting for the first time: the TV asks first (allow always, today, 24 hours, or block)."), 14.sp, Mau.mo)
        if (!nhanCast) Chu(t("Tivi đang tạm ngừng nhận cast: video điện thoại gửi sang sẽ bị bỏ qua.", "Casting is paused: videos sent from phones are ignored."), 15.sp, Mau.cam)
    }

    /** Một dòng điện thoại: chấm xanh khi đang nối, tên, trạng thái. */
    @Composable
    private fun DongMay(d: DienThoai, noi: Boolean, bay: Long, fr: FocusRequester?, onClick: () -> Unit) =
        ONhan(onClick, Modifier.fillMaxWidth(), fr) {
            Box(Modifier.size(10.dp).background(if (noi) Mau.xanh else Color.White.copy(alpha = 0.25f), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Chu(d.tenHien ?: t("Điện thoại", "Phone"), 17.sp, dam = FontWeight.SemiBold, dong = 1)
                val (s, mau) = trangThai(d, bay)
                Chu(s + " · " + t("lần cuối ", "last ") + gio(d.lanCuoi), 14.sp, mau, dong = 1)
            }
        }

    /** Hộp việc với một máy: cho phép (luôn / hôm nay / 24 giờ), chặn, xoá khỏi danh sách. */
    @Composable
    private fun HopMay(d: DienThoai, bay: Long, dong: () -> Unit) = Hop(dong) { fr ->
        Chu(d.tenHien ?: t("Điện thoại", "Phone"), 24.sp, dam = FontWeight.Bold)
        Chu(trangThai(d, bay).first + " · " + t("cast lần đầu ", "first cast ") + gio(d.lanDau), 15.sp, Mau.phu)
        Spacer(Modifier.height(6.dp))
        val lam = { l: DanhBa.Lam, viec: String ->
            canMa(viec) {
                val may = CastDichVu.dangChay?.may
                if (may != null) may.lamDienThoai(d.id, l) else kho.doiDienThoai { DanhBa.lam(it, d.id, l, System.currentTimeMillis()) }
                dong()
            }
        }
        Nut(t("Cho phép luôn", "Allow always"), { lam(DanhBa.Lam.CHO_PHEP, t("cho phép máy này cast", "allow this phone")) }, Modifier.fillMaxWidth(), Mau.xanh, fr)
        Nut(t("Cho phép tới hết hôm nay", "Allow until the end of today"), { lam(DanhBa.Lam.HOM_NAY, t("cho phép máy này cast", "allow this phone")) }, Modifier.fillMaxWidth())
        Nut(t("Cho phép 24 giờ", "Allow for 24 hours"), { lam(DanhBa.Lam.MOT_NGAY, t("cho phép máy này cast", "allow this phone")) }, Modifier.fillMaxWidth())
        Nut(t("Chặn máy này", "Block this phone"), { lam(DanhBa.Lam.CHAN, t("chặn máy này", "block this phone")) }, Modifier.fillMaxWidth(), Mau.do_)
        Nut(t("Xoá khỏi danh sách (lần sau hỏi lại)", "Remove from the list (ask again next time)"), { lam(DanhBa.Lam.XOA, t("xoá máy này", "remove this phone")) }, Modifier.fillMaxWidth())
        Chu(t("Bấm Back để đóng.", "Press Back to close."), 13.sp, Mau.mo)
    }

    /** Hỏi lại trước khi huỷ mọi liên kết. */
    @Composable
    private fun HopHuy(dong: () -> Unit, huy: () -> Unit) = Hop(dong) { fr ->
        Chu(t("Huỷ mọi liên kết?", "Unlink all phones?"), 24.sp, dam = FontWeight.Bold)
        Chu(t("Mọi điện thoại đã liên kết, kể cả máy của bạn, mất tivi này và phải nhập mã TV mới. Danh sách điện thoại xoá hết. Trên điện thoại có thể còn tên tivi cũ (mờ) tới khi YouTube làm mới.",
            "Every linked phone, yours included, loses this TV and needs the new TV code. The phone list is cleared. Phones may still show the old TV name (greyed out) until YouTube refreshes."), 16.sp, Mau.phu)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Nut(t("Thôi", "Cancel"), dong, fr = fr)
            Nut(t("Huỷ mọi liên kết", "Unlink all"), huy, mau = Mau.do_)
        }
    }

    /** Đổi tên hiện trong nút Cast ("" = tên mặc định "SmartTube · <tên tivi>"). */
    @Composable
    private fun HopTen(cu: String, dong: () -> Unit, luu: (String) -> Unit) = Hop(dong) { fr ->
        var moi by remember { mutableStateOf(cu) }
        Chu(t("Tên trong nút Cast", "Name under Cast"), 24.sp, dam = FontWeight.Bold)
        BasicTextField(moi, { moi = it.take(60) }, Modifier.fillMaxWidth().focusRequester(fr)
            .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(14.dp),
            textStyle = TextStyle(color = Mau.chu, fontSize = 20.sp), singleLine = true, cursorBrush = SolidColor(Mau.chu))
        Chu(t("Để trống thì dùng \"SmartTube · <tên tivi>\".", "Leave it empty for \"SmartTube · <TV name>\"."), 14.sp, Mau.mo)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Nut(t("Lưu", "Save"), { luu(moi) })
            Nut(t("Thôi", "Cancel"), dong)
        }
    }

    /** Hộp nổi giữa màn, nền tối mờ; Back để đóng; ô đầu nhận [fr] để được chọn sẵn. */
    @Composable
    private fun Hop(dong: () -> Unit, content: @Composable (FocusRequester) -> Unit) =
        Dialog(dong, DialogProperties(usePlatformDefaultWidth = false)) {
            val fr = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(100); runCatching { fr.requestFocus() } }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                The(Modifier.width(600.dp).background(Mau.nenDuoi.copy(alpha = 0.92f), RoundedCornerShape(24.dp))) { content(fr) }
            }
        }

    companion object {
        private val DINH_DANG = SimpleDateFormat("HH:mm dd/MM", Locale.US)

        fun gio(luc: Long): String = if (luc <= 0) "—" else synchronized(DINH_DANG) { DINH_DANG.format(Date(luc)) }

        /** Trạng thái một máy và màu: được phép, khách tới lúc nào, hết hạn, đã chặn, chờ duyệt. */
        fun trangThai(d: DienThoai, bay: Long): Pair<String, Color> = when {
            d.trangThai == DienThoai.TrangThai.CHAN -> t("Đã chặn", "Blocked") to Mau.do_
            d.quaHan(bay) -> t("Khách đã hết hạn", "Guest expired") to Mau.cam
            d.trangThai == DienThoai.TrangThai.CHO_PHEP && d.hetHan > 0 -> t("Khách · tới ", "Guest · until ") + gio(d.hetHan) to Mau.nhan
            d.trangThai == DienThoai.TrangThai.CHO_PHEP -> t("Được phép", "Allowed") to Mau.xanh
            else -> t("Chờ duyệt", "Waiting for approval") to Mau.cam
        }
    }
}
