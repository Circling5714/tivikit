package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.adb.KetQuaMang
import vn.tivikit.dulieu.ChPlay
import vn.tivikit.dulieu.Mang
import vn.tivikit.t
import java.util.Locale
import kotlin.math.abs

/** Màn Kiểm tra mạng (25/10/2026): kết nối của tivi, độ trễ, nhận xét kèm cách khắc phục. */
@Composable
internal fun ManMang(vm: PhienLamViec) = Cuon {
    val kq = vm.mang.ketQua
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Kiểm tra mạng", "Network check"), style = KieuTieuDeLon)
        Phu(t("Sóng Wi-Fi, băng tần, tốc độ kết nối và độ trễ tới router, Internet, YouTube của chính tivi, rồi CH Play và tài khoản Google. Xem YouTube giật thường là do Wi-Fi tới tivi chứ không phải tivi yếu.",
            "The TV's own Wi-Fi signal, band, link speed and latency to the router, the Internet and YouTube, then the Play Store and Google account. Stuttering YouTube is usually the Wi-Fi to the TV, not a weak TV."))
    }
    if (kq == null) {
        The {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.dangBan) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Nhan, trackColor = NenO); Spacer(Modifier.width(12.dp)) }
                Phu(if (vm.dangBan) t("Đang đo, khoảng 10 giây…", "Measuring, about 10 seconds…") else t("Chưa đo.", "Not measured yet."))
            }
        }
        if (!vm.dangBan) NutChinh(t("Đo ngay", "Measure now"), icon = Icons.Rounded.NetworkCheck) { vm.mang.doLai() }
        return@Cuon
    }

    TheNhanXet(kq)
    TheKetNoi(kq)
    The(t("Độ trễ (ping)", "Latency (ping)"), Icons.Rounded.Timer) {
        if (kq.duong?.router != null) DongPing(t("Tới router", "To the router"), kq.router)
        DongPing(t("Tới Internet", "To the Internet"), kq.net)
        DongPing("YouTube", kq.youtube, if (kq.loiTenYoutube) t("Không tìm được địa chỉ (lỗi DNS)", "Address not found (DNS error)") else null)
        Phu(t("Dưới 30 ms là rất tốt, dưới 100 ms vẫn xem mượt. Mất gói hay lệch nhiều làm video tụt chất lượng, giật.",
            "Under 30 ms is great; under 100 ms still streams smoothly. Packet loss or high jitter makes video drop quality or stutter."))
    }
    kq.chPlay?.let { TheChPlay(it) }
    NutChinh(if (vm.dangBan) t("Đang đo…", "Measuring…") else t("Đo lại", "Measure again"), enabled = !vm.dangBan, icon = Icons.Rounded.Refresh) { vm.mang.doLai() }
}

/** Kết luận ở đầu: mạng ổn, hay từng vấn đề kèm cách sửa. */
@Composable
private fun TheNhanXet(kq: KetQuaMang) {
    val ds = kq.vanDe
    if (ds.isEmpty()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Xanh.copy(alpha = 0.12f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Xanh); Spacer(Modifier.width(10.dp))
            Column {
                Text(t("Mạng của tivi ổn", "The TV's network is fine"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Phu(t("Đủ xem YouTube, phim 4K. Còn giật thì thường do chính app hoặc máy chủ của app.", "Enough for YouTube and 4K. Any stutter left usually comes from the app or its servers."))
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NenCanhBao).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
            Text(if (ds.size == 1) t("Có một chỗ cần xem", "One thing to look at") else t("Có ${ds.size} chỗ cần xem", "${ds.size} things to look at"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        ds.forEach { v ->
            val (tieuDe, cach) = loiKhuyen(v)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tieuDe, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Phu(cach, ChuChinh.copy(alpha = 0.8f))
            }
        }
    }
}

private fun loiKhuyen(v: Mang.VanDe): Pair<String, String> = when (v) {
    Mang.VanDe.MAT_MANG -> t("Tivi không ra được Internet", "The TV can't reach the Internet") to
        t("Kiểm tra router còn mạng (thử trên điện thoại), tắt bật lại router và tivi. Nếu tivi đặt DNS riêng, thử tắt ở mục DNS chặn quảng cáo.",
            "Check the router has Internet (try on the phone), restart the router and the TV. If the TV uses a private DNS, try turning it off under Ad-blocking DNS.")
    Mang.VanDe.LOI_DNS -> t("Lỗi DNS: tivi không tìm được địa chỉ YouTube", "DNS error: the TV can't look up YouTube") to
        t("Thường do DNS riêng đặt sai hoặc máy chủ DNS đang hỏng. Vào DNS chặn quảng cáo, chọn Tắt rồi đo lại.",
            "Usually a wrong private DNS or a broken DNS server. Open Ad-blocking DNS, choose Off and measure again.")
    Mang.VanDe.SONG_YEU -> t("Sóng Wi-Fi yếu", "Weak Wi-Fi signal") to
        t("Video dễ giật, tụt nét. Dời router lại gần tivi, bỏ vật cản (tường, tủ kim loại), thêm bộ phát Mesh, hoặc cắm dây LAN.",
            "Video will stutter or drop quality. Move the router closer, remove obstacles (walls, metal cabinets), add a mesh node, or use an Ethernet cable.")
    Mang.VanDe.SONG_VUA -> t("Sóng Wi-Fi trung bình", "Average Wi-Fi signal") to
        t("Xem thường ổn nhưng 4K có thể giật lúc mạng đông. Dời router gần hơn hoặc cắm dây LAN nếu được.",
            "Usually fine, but 4K may stutter when the network is busy. Move the router closer or use a cable if you can.")
    Mang.VanDe.BANG_24 -> t("Tivi đang dùng Wi-Fi 2,4 GHz", "The TV is on 2.4 GHz Wi-Fi") to
        t("Băng 2,4 GHz chậm và nhiễu (nhà hàng xóm, lò vi sóng). Nếu router có mạng 5 GHz (tên thường có đuôi 5G), cho tivi nối mạng đó.",
            "2.4 GHz is slow and crowded (neighbours, microwaves). If the router has a 5 GHz network (often named …5G), connect the TV to it.")
    Mang.VanDe.TOC_DO_THAP -> t("Tốc độ kết nối Wi-Fi thấp", "Low Wi-Fi link speed") to
        t("Dưới 50 Mbps giữa tivi và router: không đủ chắc cho 4K. Thường do sóng yếu hoặc băng 2,4 GHz.",
            "Under 50 Mbps between TV and router isn't enough headroom for 4K. Usually weak signal or 2.4 GHz.")
    Mang.VanDe.NHIEU_TRONG_NHA -> t("Wi-Fi trong nhà chập chờn", "In-home Wi-Fi is unstable") to
        t("Ngay tới router đã chậm hoặc rớt gói: lỗi nằm ở Wi-Fi giữa tivi và router, không phải nhà mạng. Dời router, đổi kênh Wi-Fi, hoặc cắm dây.",
            "Even the router is slow or dropping packets: the problem is the Wi-Fi between TV and router, not your provider. Move the router, change the Wi-Fi channel or use a cable.")
    Mang.VanDe.MANG_CHAM -> t("Mạng ra Internet chậm", "Slow connection to the Internet") to
        t("Wi-Fi trong nhà ổn nhưng ra ngoài chậm: có thể giờ cao điểm hoặc lỗi nhà mạng. Thử lại lúc khác, khởi động lại modem, báo nhà mạng nếu kéo dài.",
            "Home Wi-Fi is fine but the outside link is slow: maybe peak hours or a provider issue. Try later, restart the modem, call your provider if it persists.")
    Mang.VanDe.MAT_GOI -> t("Mạng ra Internet rớt gói", "Packet loss to the Internet") to
        t("Video dễ dừng tải. Khởi động lại modem; vẫn bị thì báo nhà mạng kiểm tra đường dây.",
            "Video may pause to buffer. Restart the modem; if it continues, ask your provider to check the line.")
    Mang.VanDe.CHAP_CHON -> t("Độ trễ dao động mạnh", "Latency jumps around") to
        t("Có lúc nhanh lúc chậm, thường do có máy khác đang tải nặng (camera, tải game, cập nhật). Thử lại khi mạng rảnh.",
            "Fast one moment, slow the next, often another device downloading heavily (cameras, game downloads, updates). Try again when the network is quiet.")
}

/** CH Play và tài khoản Google (26/10/2026): CH Play hay báo "kiểm tra Wi-Fi" cả khi lỗi nằm ở tài khoản. */
@Composable
private fun TheChPlay(cp: ChPlay.TrangThai) {
    val ds = ChPlay.danhGia(cp)
    The(t("CH Play và tài khoản Google", "Play Store and Google account"), Icons.Rounded.Shop) {
        if (ds.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Xanh, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(t("CH Play ổn", "The Play Store looks fine"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Phu(t("Có tài khoản Google, không thấy lỗi đăng nhập trong nhật ký gần đây.", "A Google account is signed in, with no recent sign-in errors in the log."))
        } else ds.forEach { v ->
            val (tieuDe, cach) = loiKhuyenChPlay(v, cp)
            val chiBao = v == ChPlay.VanDe.KHONG_CO_CH_PLAY || v == ChPlay.VanDe.ROM_CHUA_CHUNG_NHAN
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tieuDe, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (chiBao) ChuChinh else Cam)
                Phu(cach)
            }
        }
        Phu(t("CH Play báo “Đã xảy ra lỗi, kiểm tra Wi-Fi” trong khi mạng ở trên vẫn ổn thì thường là do tài khoản, không phải do mạng.",
            "If the Play Store says “Something went wrong, check your Wi-Fi” while the network above is fine, it's usually the account, not the network."))
    }
}

private fun loiKhuyenChPlay(v: ChPlay.VanDe, cp: ChPlay.TrangThai): Pair<String, String> = when (v) {
    ChPlay.VanDe.KHONG_CO_CH_PLAY -> t("Tivi không có CH Play", "No Play Store on this TV") to
        t("Một số máy (Fire TV, box nhà mạng) không có CH Play. Cài app bằng mục Cài app của TiviKit.",
            "Some devices (Fire TV, operator boxes) have no Play Store. Install apps with Install apps in TiviKit.")
    ChPlay.VanDe.GOI_GOOGLE_TAT -> t("Gói Google mà CH Play cần đang bị tắt", "A Google package the Play Store needs is disabled") to
        t("Đang tắt: ${cp.goiTat.joinToString { tenGoiGoogle(it) }}. Bật lại trong Quản lý app rồi mở lại CH Play.",
            "Disabled: ${cp.goiTat.joinToString { tenGoiGoogle(it) }}. Re-enable it under Manage apps, then reopen the Play Store.")
    ChPlay.VanDe.LECH_GIO -> (abs(cp.lechGio ?: 0L) / 60).let { phut ->
        t("Giờ trên tivi lệch $phut phút", "The TV's clock is $phut minutes off") to
            t("Giờ sai làm CH Play và nhiều app không kết nối được (lỗi bảo mật). Trên tivi vào Cài đặt → Ngày và giờ, bật giờ tự động.",
                "A wrong clock breaks secure connections for the Play Store and many apps. On the TV, open Settings → Date & time and turn on automatic time.")
    }
    ChPlay.VanDe.CAN_DANG_NHAP_LAI -> ((cp.giayTuLoi ?: 0L) / 60).let { phut ->
        val luc = if (phut < 1) t("vừa xong", "just now") else t("$phut phút trước", "$phut min ago")
        t("Tài khoản Google trên tivi cần đăng nhập lại", "The Google account on the TV needs signing in again") to
            t("Google từ chối phiên đăng nhập đang lưu (lỗi BAD_AUTHENTICATION, $luc), nên CH Play báo lỗi dù mạng ổn. Trên tivi vào Cài đặt → Tài khoản, xoá tài khoản Google rồi thêm lại. Xoá dữ liệu CH Play không giúp được.",
                "Google rejected the saved sign-in (BAD_AUTHENTICATION, $luc), so the Play Store fails even though the network is fine. On the TV, open Settings → Accounts, remove the Google account and add it again. Clearing the Play Store's data won't help.")
    }
    ChPlay.VanDe.CHUA_CO_TAI_KHOAN -> t("Tivi chưa có tài khoản Google", "No Google account on the TV") to
        t("CH Play cần một tài khoản Google: trên tivi vào Cài đặt → Tài khoản → Thêm tài khoản.",
            "The Play Store needs a Google account: on the TV, open Settings → Accounts → Add account.")
    ChPlay.VanDe.ROM_CHUA_CHUNG_NHAN -> t("Phần mềm tivi chưa được Google chứng nhận", "The TV's software isn't Google-certified") to
        t("Đây là bản phần mềm tự dựng (test-keys), hay gặp ở box Android giá rẻ. Nếu đăng nhập bị chặn với thông báo thiết bị chưa được chứng nhận, cần đăng ký mã GSF ID của box tại google.com/android/uncertified.",
            "This is self-built firmware (test-keys), common on cheap Android boxes. If sign-in is blocked as an uncertified device, register the box's GSF ID at google.com/android/uncertified.")
}

private fun tenGoiGoogle(pkg: String) = when (pkg) {
    "com.android.vending" -> t("CH Play", "Play Store")
    "com.google.android.gms" -> t("Dịch vụ Google Play", "Google Play services")
    "com.google.android.gsf" -> "Google Services Framework"
    else -> pkg
}

/** Thẻ kết nối: Wi-Fi (tên, sóng, băng, chuẩn, tốc độ) hoặc dây LAN, địa chỉ tivi. */
@Composable
private fun TheKetNoi(kq: KetQuaMang) {
    val d = kq.duong
    val w = kq.wifi
    val lan = d != null && Mang.laLan(d.dev)
    The(t("Kết nối", "Connection"), if (lan) Icons.Rounded.SettingsEthernet else Icons.Rounded.Wifi) {
        DongTT(t("Mạng", "Network"), when {
            d == null -> t("Không có", "None")
            lan -> t("Dây LAN", "Ethernet") + (kq.lanMbps?.let { " · $it Mbps" } ?: "")
            w?.ssid != null -> "Wi-Fi “${w.ssid}”"
            else -> "Wi-Fi"
        })
        if (!lan && w != null) {
            w.rssi?.let { r ->
                val s = Mang.song(r)
                val mau = when (s) { Mang.Song.RAT_TOT, Mang.Song.TOT -> Xanh; Mang.Song.TRUNG_BINH -> Cam; Mang.Song.YEU -> DoTuoi }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Sóng", "Signal"), fontSize = 15.sp, color = ChuPhu, modifier = Modifier.weight(1f))
                    VachSong(s, mau); Spacer(Modifier.width(8.dp))
                    Text(when (s) { Mang.Song.RAT_TOT -> t("Rất tốt", "Excellent"); Mang.Song.TOT -> t("Tốt", "Good"); Mang.Song.TRUNG_BINH -> t("Trung bình", "Fair"); Mang.Song.YEU -> t("Yếu", "Weak") } + " · $r dBm",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = mau)
                }
            }
            val bang = w.tanSo?.let { Mang.bang(it) }
            if (bang != null || w.chuan != null) DongTT(t("Băng tần", "Band"), listOfNotNull(
                when (bang) { Mang.Bang.B24 -> "2,4 GHz"; Mang.Bang.B5 -> "5 GHz"; Mang.Bang.B6 -> "6 GHz"; null -> null },
                Mang.theHe(w.chuan)?.let { "Wi-Fi $it" },
            ).joinToString(" · "), if (bang == Mang.Bang.B24) Cam else null)
            w.tocDo?.let { DongTT(t("Tốc độ kết nối", "Link speed"), "$it Mbps", if (it < 50) Cam else null) }
        }
        d?.ip?.let { DongTT(t("Địa chỉ tivi", "TV address"), it) }
    }
}

/** Bốn vạch sóng, tô theo mức. */
@Composable
private fun VachSong(s: Mang.Song, mau: Color) {
    val so = when (s) { Mang.Song.RAT_TOT -> 4; Mang.Song.TOT -> 3; Mang.Song.TRUNG_BINH -> 2; Mang.Song.YEU -> 1 }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..4).forEach { i -> Box(Modifier.width(4.dp).height((4 + i * 3).dp).clip(RoundedCornerShape(1.dp)).background(if (i <= so) mau else NenO)) }
    }
}

@Composable
private fun DongTT(ten: String, giaTri: String, mau: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ten, fontSize = 15.sp, color = ChuPhu, modifier = Modifier.weight(1f))
        Text(giaTri, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = mau ?: ChuChinh)
    }
}

/** Một dòng ping: trung bình, mất gói, độ lệch; [loi] thay cho số khi đo không được. */
@Composable
private fun DongPing(ten: String, p: Mang.Ping?, loi: String? = null) {
    val chu = when {
        loi != null -> loi
        p == null -> t("Không đo được", "Couldn't measure")
        p.nhan == 0 -> t("Không phản hồi", "No reply")
        else -> listOfNotNull(
            p.tb?.let { "${String.format(Locale.US, "%.0f", it)} ms" },
            if (p.matPhanTram > 0) t("mất ${p.matPhanTram}%", "${p.matPhanTram}% lost") else null,
            p.lech?.takeIf { it >= 10 }?.let { t("lệch ${String.format(Locale.US, "%.0f", it)} ms", "±${String.format(Locale.US, "%.0f", it)} ms") },
        ).joinToString(" · ")
    }
    val xau = loi != null || p == null || p.nhan == 0 || p.matPhanTram >= 3 || (p.tb ?: 0.0) > 100
    val vua = !xau && ((p?.tb ?: 0.0) > 30 || (p?.lech ?: 0.0) > 30)
    DongTT(ten, chu, if (xau) DoTuoi else if (vua) Cam else Xanh)
}
