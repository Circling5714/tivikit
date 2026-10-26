package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.adb.ThaoTacTv
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.dulieu.PhimRieng
import vn.tivikit.dulieu.tenDep
import vn.tivikit.t

/**
 * Nút remote (25/10/2026, muốn nút YouTube trên remote mở SmartTube): dò mã một nút trên remote thật (getevent), chọn
 * app, ghi vào app_keys của Home sạch (ThaoTacTv.ghiPhimRieng), rồi bấm thử. Danh sách gán nằm ở vm.phimRieng để các
 * lần ghi cấu hình Home sạch sau đó giữ lại.
 */
class GanPhim(private val vm: PhienLamViec) {
    /** Nút vừa dò được, chờ chọn app. */
    var nut by mutableStateOf<PhimRieng.Nut?>(null)
        private set
    /** App nút đó sẽ mở. */
    var goi by mutableStateOf<String?>(null)
    /** App mở được trên tivi, SmartTube lên đầu. */
    var dsApp by mutableStateOf<List<String>>(emptyList())
        private set
    var bao by mutableStateOf("")
        private set
    /** Kết quả bấm thử: true mở đúng app, false không, null chưa thử. */
    var thuOk by mutableStateOf<Boolean?>(null)
        private set
    /** Bấm thử thấy YouTube gốc vẫn lên: gợi ý tắt nó ở Quản lý app. */
    var goiYTatYouTube by mutableStateOf(false)
        private set
    /** Tivi bo mạch CVTE: phải khởi động lại sau khi cài Home sạch rồi mới bật dịch vụ phím (upstream #741). */
    var laCvte by mutableStateOf(false)
        private set
    var daKhoiDongLai by mutableStateOf(false)
    /** Nút + app vừa lưu, để bấm thử. */
    var vuaGan by mutableStateOf<Pair<PhimRieng.Nut, String>?>(null)
        private set

    /** [goiSan]: app chọn sẵn cho nút mới (màn Kênh nhanh mở sang đây với Kênh nhanh). */
    fun mo(goiSan: String? = null) = vm.chay(t("Đang đọc nút đã gán trên tivi…", "Reading mapped buttons on the TV…")) {
        vm.damBaoKetNoi()
        nut = null; bao = ""; thuOk = null; goiYTatYouTube = false
        vm.trangThai = vm.io { vm.tv.docTrangThai() }
        if (vm.daCoHomeSach) {
            val j = vm.io { vm.tv.docCauHinhTv() }
            vm.phimRieng.clear(); j?.let { vm.phimRieng.addAll(PhimRieng.docTu(it)) }
            laCvte = vm.io { vm.tv.laBoMachCvte() }
        }
        val ds = vm.appChoDock()
        dsApp = ds.filter { it in PhimRieng.GOI_SMARTTUBE }.sortedBy { PhimRieng.GOI_SMARTTUBE.indexOf(it) } + ds.filter { it !in PhimRieng.GOI_SMARTTUBE }
        if (goiSan != null && goiSan in dsApp) goi = goiSan
        else if (goi !in dsApp) goi = dsApp.firstOrNull { it in PhimRieng.GOI_SMARTTUBE }
        vm.man = Man.GAN_PHIM
    }

    /** Chờ người dùng bấm nút cần gán trên remote thật. */
    fun doNut() = vm.chay(t("Bấm nút cần gán trên remote của tivi (trong 20 giây)…", "Press the button on the TV's remote (within 20 seconds)…")) {
        vm.damBaoKetNoi()
        nut = null; bao = ""; thuOk = null; goiYTatYouTube = false
        val n = vm.io { vm.tv.doNutRemote(20) } ?: error(t(
            "Chưa thấy nút nào được bấm. Bấm Dò nút rồi bấm nút trên remote của tivi (remote Bluetooth đang ngủ thì bấm hai lần).",
            "No button press seen. Tap Detect, then press the button on the TV's remote (press twice if a Bluetooth remote was asleep)."))
        if (PhimRieng.laNutCoBan(n)) error(t(
            "Đó là nút ${tenNut(n)} (điều hướng, OK, Back, Home, âm lượng hoặc nguồn): không gán đè được. Dò lại và bấm đúng nút app, VD nút YouTube.",
            "That's ${tenNut(n)} (navigation, OK, Back, Home, volume or power), which can't be remapped. Detect again and press the app button, e.g. YouTube."))
        if (PhimRieng.muc(n, "a.b") == null) error(t(
            "Remote này gửi chung một mã (240) cho nhiều nút, và tivi không có bảng tên phím cho nút này nên app không phân biệt được. Không gán riêng nút này được.",
            "This remote sends one shared code (240) for several buttons and the TV has no key name for this one, so it can't be told apart or remapped."))
        nut = n
    }

    /** Ghi nút vừa dò + app đã chọn lên tivi. */
    fun luu() = vm.chay(t("Đang gán nút trên tivi…", "Mapping the button on the TV…")) {
        val n = nut ?: return@chay
        val g = goi ?: error(t("Chọn app cho nút này.", "Pick an app for this button."))
        if (laCvte && !daKhoiDongLai) error(t(
            "Tivi bo mạch CVTE: tắt mở tivi một lần sau khi cài Home sạch, thấy Home sạch lên bình thường rồi mới gán nút (đã có tivi CVTE treo ở màn khởi động khi đổi Home và bật dịch vụ phím cùng lượt).",
            "CVTE board: restart the TV once after installing the clean Home and check it comes up before mapping buttons (a CVTE TV hung on boot when Home changed and the key helper was turned on in one pass)."))
        val m = PhimRieng.muc(n, g) ?: return@chay
        vm.damBaoKetNoi()
        val k = PhimRieng.khoaCua(m)
        val ds = vm.phimRieng.filter { PhimRieng.khoaCua(it) != k } + m
        vm.io { vm.tv.ghiPhimRieng(ds) }
        vm.phimRieng.clear(); vm.phimRieng.addAll(ds)
        vuaGan = n to g; nut = null; thuOk = null; goiYTatYouTube = false
        bao = t("Đã gán: nút ${tenNut(n)} mở ${tenApp(g)}. Bấm Thử nút để kiểm tra trên tivi.", "Mapped: ${tenNut(n)} opens ${tenApp(g)}. Tap Test to check it on the TV.")
    }

    /** Bỏ một nút đã gán: nút trở lại việc cũ. */
    fun bo(muc: String) = vm.chay(t("Đang bỏ gán…", "Removing…")) {
        vm.damBaoKetNoi()
        val ds = vm.phimRieng.filter { it != muc }
        vm.io { vm.tv.ghiPhimRieng(ds) }
        vm.phimRieng.clear(); vm.phimRieng.addAll(ds)
        vuaGan?.let { (n, g) -> if (PhimRieng.muc(n, g) == muc) { vuaGan = null; thuOk = null } }
        goiYTatYouTube = false
        bao = t("Đã bỏ gán, nút trở lại như cũ.", "Removed; the button works as before.")
    }

    /** Bấm thử nút vừa gán trên remote thật, xem app nào lên. */
    fun thu() = vm.chay(t("Bấm nút vừa gán trên remote (trong 20 giây)…", "Press the button you just mapped (within 20 seconds)…")) {
        val (n, g) = vuaGan ?: return@chay
        vm.damBaoKetNoi()
        thuOk = null; goiYTatYouTube = false; bao = ""
        // Kênh nhanh mở xong là tự đóng, TV360 lên trước mặt.
        val mongDoi = if (KenhNhanh.laGoiKenh(g)) KenhNhanh.TV360 else g
        val kq = vm.io { vm.tv.thuNut(n, mongDoi, 20, veHome = !vn.tivikit.ManHinh.laTv) }
        thuOk = kq.daBam && kq.goi == mongDoi
        bao = when {
            !kq.daBam -> t("Chưa thấy bấm nút. Bấm Thử nút rồi bấm đúng nút đó trên remote.", "No press seen. Tap Test, then press that button on the remote.")
            kq.goi == mongDoi -> t("Được rồi: nút ${tenNut(n)} mở ${tenApp(g)}.", "Works: ${tenNut(n)} opens ${tenApp(g)}.")
            kq.goi == YOUTUBE -> {
                goiYTatYouTube = true
                t("Nút vẫn mở YouTube gốc: tivi tự xử lý nút này trước Home sạch. Tắt YouTube gốc (Quản lý app → Tắt app) rồi thử lại: khi đó tivi không mở được nó nữa và nút sẽ mở ${tenApp(g)}.",
                    "The button still opens the stock YouTube: the TV handles it before the clean Home. Disable the stock YouTube (Manage apps → Disable) and test again; the button then opens ${tenApp(g)}.")
            }
            kq.goi.isBlank() || kq.goi in ThaoTacTv.HOME_SACH ->
                if (!vm.io { vm.tv.dichVuPhimDangBat() })
                    t("Nút chưa mở gì: dịch vụ phím của Home sạch không chạy. Trên tivi vào Cài đặt → Trợ năng, bật Home sạch (HomeKeyService), rồi thử lại.",
                        "The button opened nothing: the clean Home's key service isn't running. On the TV open Settings → Accessibility, turn on the clean Home (HomeKeyService), then test again.")
                else t("Nút chưa mở ${tenApp(g)}. Bấm Thử nút lần nữa; vẫn không được thì tivi này có thể chặn nút trước Home sạch — gửi góp ý kèm hãng, model và tên remote.",
                    "The button didn't open ${tenApp(g)}. Test once more; if it still fails the TV may grab the button before the clean Home — send feedback with the brand, model and remote name.")
            else -> t("Nút mở ${tenApp(kq.goi)} thay vì ${tenApp(g)}. Nếu tivi có sẵn việc cho nút này, tắt app đó ở Quản lý app rồi thử lại.",
                "The button opened ${tenApp(kq.goi)} instead of ${tenApp(g)}. If the TV has its own action for this button, disable that app in Manage apps and test again.")
        }
    }

    /** Kết nối tivi khác. */
    fun quen() { nut = null; vuaGan = null; thuOk = null; bao = ""; goiYTatYouTube = false; laCvte = false; daKhoiDongLai = false; dsApp = emptyList() }

    /** Tên app: ô Kênh nhanh kèm kênh của nó, nhãn thật nếu Quản lý app đã đọc, không thì tên quen / đuôi tên gói. */
    fun tenApp(p: String): String = vm.kenhNhanh.tenO(p)
        ?: vm.qlApp.ds.firstOrNull { it.tv.pkg == p }?.ten?.takeIf { it.isNotEmpty() } ?: tenDep(p)

    companion object {
        const val YOUTUBE = "com.google.android.youtube.tv"

        /** Tên nút cho người đọc: nhãn phím Android trong keylayout (BUTTON_3), không có thì mã quét. */
        fun tenNut(n: PhimRieng.Nut): String = n.tenAndroid ?: t("mã ${n.maQuet}", "code ${n.maQuet}")

        /** Tên nút của một mục app_keys: "scan:490" → mã 490, "KEYCODE_BUTTON_3" → BUTTON_3, "190" → phím 190. */
        fun tenNut(muc: String): String {
            val k = PhimRieng.khoaCua(muc).removePrefix("hold:")
            return when {
                k.startsWith("scan:") -> t("mã ${k.removePrefix("scan:")}", "code ${k.removePrefix("scan:")}")
                k.startsWith("KEYCODE_") -> k.removePrefix("KEYCODE_")
                else -> t("phím $k", "key $k")
            }
        }
    }
}
