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
 * điện thoại nào đã nối, gỡ.
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
        docLai()
        bao = t("Đã đổi tên thành \"${trangThai?.ten.orEmpty()}\". Điện thoại thấy tên mới ở lần bấm nút Cast sau (có thể cần mở lại YouTube).",
            "Renamed to \"${trangThai?.ten.orEmpty()}\". Phones see the new name next time they open the Cast button (reopening YouTube may help).")
    }

    fun go() = vm.chay(t("Đang gỡ Cast SmartTube…", "Removing Cast to SmartTube…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.goCast() }
        daCai = false; trangThai = null
        bao = t("Đã gỡ Cast SmartTube khỏi tivi.", "Cast to SmartTube is removed from the TV.")
    }

    fun quen() { trangThai = null; daCai = false; coSmartTube = true; ten = ""; bao = "" }
}
