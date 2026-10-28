package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import vn.tivikit.dulieu.CastSmartTube
import vn.tivikit.t

/**
 * Cast SmartTube (28/10/2026): cài app phụ vn.tivikit.cast lên tivi để app YouTube trên điện thoại cùng Wi-Fi thấy tivi
 * trong nút Cast (không cần liên kết bằng mã TV), video gửi sang mở bằng SmartTube; đặt tên hiện trong nút Cast, xem
 * điện thoại nào đã nối, xin mã TV cho tivi có máy Cast của hãng chen trước, gỡ.
 */
class CastTivi(private val vm: PhienLamViec) {
    var trangThai by mutableStateOf<CastSmartTube.TrangThai?>(null)
        private set
    /** App phụ đã có trên tivi. */
    var daCai by mutableStateOf(false)
        private set
    var coSmartTube by mutableStateOf(true)
        private set
    /** Tên hiện trong nút Cast (ô nhập). */
    var ten by mutableStateOf("")
    var bao by mutableStateOf("")
        private set
    /** Mã TV vừa xin (12 số) và lúc xin (mã sống chừng 5 phút); "" = chưa xin. */
    var maTv by mutableStateOf("")
        private set
    var maTvLuc by mutableStateOf(0L)
        private set

    /** App phụ cần Android 8+. */
    val duocCai: Boolean get() = (vm.thongTin?.sdk ?: 0) >= 26

    fun mo() = vm.chay(t("Đang đọc Cast SmartTube trên tivi…", "Reading Cast to SmartTube on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""
        docLai()
        vm.man = Man.CAST
    }

    private suspend fun docLai() {
        coSmartTube = vm.io { vm.tv.coSmartTube() }
        daCai = vm.io { vm.tv.castDaCai() }
        trangThai = if (daCai) vm.io { vm.tv.docCast() }?.let { CastSmartTube.trangThai(it) } else null
        ten = trangThai?.ten.orEmpty()
    }

    fun lamMoi() = vm.chay(t("Đang đọc Cast SmartTube trên tivi…", "Reading Cast to SmartTube on the TV…")) {
        vm.damBaoKetNoi(); bao = ""; docLai()
    }

    /** Cài / cập nhật app phụ và bật dịch vụ; chờ máy nhận nối được YouTube rồi đọc lại. */
    fun bat() = vm.chay(t("Đang cài Cast SmartTube lên tivi…", "Installing Cast to SmartTube on the TV…")) {
        if (!duocCai) error(t("Cast SmartTube cần Android 8 trở lên, tivi này là Android ${vm.thongTin?.android}.",
            "Cast to SmartTube needs Android 8 or later; this TV runs Android ${vm.thongTin?.android}."))
        vm.damBaoKetNoi()
        bao = ""
        val apk = vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, "cast.apk").readBytes() }
        val tenMoi = CastSmartTube.chuanTen(ten).takeIf { it.isNotEmpty() && it != trangThai?.ten }
        vm.io { vm.tv.caiCast(apk, tenMoi) { s -> vm.thongBao = s } }
        // Dịch vụ vừa bật mới xin YouTube cấp screen: chờ tối đa chừng 12 giây (chỉ đọc trạng thái, đọc danh sách app chậm).
        daCai = true
        for (i in 1..8) {
            delay(1_500)
            trangThai = vm.io { vm.tv.docCast() }?.let { CastSmartTube.trangThai(it) }
            if (trangThai?.daNoi == true) break
        }
        ten = trangThai?.ten.orEmpty()
        val tt = trangThai
        bao = when {
            tt == null -> t("Đã cài nhưng chưa đọc lại được từ tivi. Bấm Làm mới.", "Installed, but couldn't read it back from the TV. Tap Refresh.")
            !tt.dichVuChay -> t("Đã cài, nhưng dịch vụ Cast chưa chạy trên tivi: vào Cài đặt → Trợ năng của tivi, bật \"Cast SmartTube (TiviKit)\", rồi bấm Làm mới.",
                "Installed, but the Cast service isn't running on the TV: open the TV's Settings → Accessibility, turn on \"Cast SmartTube (TiviKit)\", then tap Refresh.")
            !tt.daNoi -> t("Dịch vụ đang chạy nhưng chưa nối được YouTube (tivi có vào mạng không?). Đợi ít giây rồi bấm Làm mới.",
                "The service is running but hasn't reached YouTube yet (is the TV online?). Wait a few seconds and tap Refresh.")
            else -> t("Xong. Trên điện thoại cùng Wi-Fi với tivi: mở YouTube → nút Cast → chọn \"${tt.ten}\".",
                "Done. On a phone on the same Wi-Fi as the TV: open YouTube → Cast button → pick \"${tt.ten}\".")
        }
    }

    fun luuTen() = vm.chay(t("Đang đổi tên…", "Renaming…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.datTenCast(ten) }
        // Mã đang hiện xin theo tên cũ (YouTube gửi tên kèm mã cho máy nhập mã).
        maTv = ""; maTvLuc = 0L
        docLai()
        bao = t("Đã đổi tên thành \"${trangThai?.ten.orEmpty()}\". Điện thoại thấy tên mới ở lần bấm nút Cast sau (có thể cần mở lại YouTube).",
            "Renamed to \"${trangThai?.ten.orEmpty()}\". Phones see the new name next time they open the Cast button (reopening YouTube may help).")
    }

    /**
     * Xin mã TV để điện thoại liên kết thủ công (tivi có máy Cast của hãng trả lời trước, điện thoại không thấy tivi qua
     * Wi-Fi). Liên kết một lần là điện thoại nhớ: tivi luôn có trong nút Cast, kể cả khi SmartTube chưa mở.
     */
    fun layMaTv() = vm.chay(t("Đang xin mã TV…", "Getting a TV code…")) {
        vm.damBaoKetNoi()
        bao = ""
        maTv = vm.io { vm.tv.maTvCast() }
        maTvLuc = System.currentTimeMillis()
        if (maTv.isEmpty()) bao = t("Chưa xin được mã: dịch vụ Cast trên tivi chưa nối được YouTube. Đợi ít giây rồi thử lại.",
            "Couldn't get a code: the Cast service on the TV hasn't reached YouTube yet. Wait a few seconds and try again.")
    }

    /** Điện thoại đang chọn để làm việc (hộp hỏi), và hỏi lại trước khi huỷ mọi liên kết. */
    var chonMay by mutableStateOf<CastSmartTube.May?>(null)
    var hoiHuy by mutableStateOf(false)

    /**
     * Sau một việc với điện thoại: chỉ đọc lại trạng thái app Cast (docLai còn đọc danh sách app tìm SmartTube, mất chừng
     * 10 giây trên TX3).
     */
    private suspend fun docTrangThai() {
        trangThai = vm.io { vm.tv.docCast() }?.let { CastSmartTube.trangThai(it) }
    }

    /** Cho phép / khách / chặn / xoá một điện thoại (app Cast 2.0 trên tivi). */
    fun lamMay(m: CastSmartTube.May, lam: CastSmartTube.Lam) = vm.chay(t("Đang đổi trên tivi…", "Updating the TV…")) {
        chonMay = null
        vm.damBaoKetNoi()
        if (!vm.io { vm.tv.lamDienThoaiCast(m.id, lam) }) error(t("Tivi không nhận lệnh. Bấm Làm mới rồi thử lại.", "The TV didn't take it. Tap Refresh and try again."))
        docTrangThai()
        val ten = m.tenHien.ifEmpty { t("Điện thoại", "Phone") }
        bao = when (lam) {
            CastSmartTube.Lam.CHO_PHEP -> t("$ten được cast lên tivi.", "$ten can cast to the TV.")
            CastSmartTube.Lam.HOM_NAY -> t("$ten được cast tới hết hôm nay.", "$ten can cast until the end of today.")
            CastSmartTube.Lam.MOT_NGAY -> t("$ten được cast trong 24 giờ.", "$ten can cast for 24 hours.")
            CastSmartTube.Lam.CHAN -> t("Đã chặn $ten: tivi bỏ qua video máy đó gửi.", "$ten is blocked: the TV ignores what it sends.")
            CastSmartTube.Lam.XOA -> t("Đã xoá $ten khỏi danh sách: lần sau máy đó cast thì tivi hỏi lại.", "$ten is removed: the TV asks again next time it casts.")
        }
    }

    /** Bật / tạm ngừng nhận cast. */
    fun doiNhanCast(bat: Boolean) = vm.chay(t("Đang đổi trên tivi…", "Updating the TV…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.datNhanCast(bat) }
        docTrangThai()
        bao = if (bat) t("Tivi nhận cast lại như thường.", "The TV takes casts again.")
            else t("Tivi tạm ngừng nhận cast: video điện thoại gửi sang bị bỏ qua, liên kết vẫn giữ.", "Casting is paused: videos sent from phones are ignored; links are kept.")
    }

    /** Huỷ mọi liên kết: mọi điện thoại (kể cả máy này) mất tivi, phải nhập mã TV mới. */
    fun huyLienKet() = vm.chay(t("Đang huỷ mọi liên kết…", "Unlinking all phones…")) {
        hoiHuy = false
        vm.damBaoKetNoi()
        if (!vm.io { vm.tv.huyLienKetCast() }) error(t("Dịch vụ Cast trên tivi đang tắt.", "The Cast service on the TV is off."))
        maTv = ""; maTvLuc = 0L
        // Tivi bỏ screen cũ, xin screen mới rồi mới nối lại YouTube: chờ tối đa chừng 12 giây rồi xin luôn mã TV mới
        // (mã xin trước lúc đó là của screen cũ, điện thoại nhập vào là liên kết với tivi đã chết).
        for (i in 1..8) {
            delay(1_500)
            docTrangThai()
            if (trangThai?.daNoi == true) break
        }
        if (trangThai?.daNoi == true) { maTv = vm.io { vm.tv.maTvCast() }; maTvLuc = System.currentTimeMillis() }
        bao = if (maTv.isNotEmpty()) t("Đã huỷ mọi liên kết. Điện thoại muốn cast lại thì nhập mã TV mới bên dưới.",
            "All phones are unlinked. Phones need the new TV code below to cast again.")
        else t("Đã huỷ mọi liên kết. Điện thoại muốn cast lại thì nhập mã TV mới (đợi vài giây cho tivi nối lại YouTube rồi Lấy mã TV).",
            "All phones are unlinked. Phones need a new TV code to cast again (wait a few seconds for the TV to reconnect, then Get a TV code).")
    }

    fun go() = vm.chay(t("Đang gỡ Cast SmartTube…", "Removing Cast to SmartTube…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.goCast() }
        daCai = false; trangThai = null
        bao = t("Đã gỡ Cast SmartTube khỏi tivi.", "Cast to SmartTube is removed from the TV.")
    }

    fun quen() { trangThai = null; daCai = false; coSmartTube = true; ten = ""; bao = ""; maTv = ""; maTvLuc = 0L; chonMay = null; hoiHuy = false }
}
