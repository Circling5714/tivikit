package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.tivikit.adb.ThaoTacTv
import vn.tivikit.dulieu.BanSaoLuu
import vn.tivikit.dulieu.KeHoach
import vn.tivikit.dulieu.Launcher
import vn.tivikit.dulieu.Launcher.Loai
import vn.tivikit.dulieu.Launcher.LyDoGiu
import vn.tivikit.dulieu.tenDep
import vn.tivikit.t

/**
 * Quản lý launcher (27/10/2026, anh chủ nhà muốn quản lý tập trung các launcher trên tivi, sắp dùng Projectivy): mọi
 * launcher kể cả đang tắt, nút Home đang mở gì (thử bằng nút Home thật), đặt launcher làm Home (thử thật, hỏng thì tự trả
 * lại; Google TV không nhường Home thì Home sạch mở launcher đó đè lên), mở thử, tắt / bật lại (tắt qua tatGoi: đủ lớp
 * bảo vệ, ghi sao lưu), gỡ launcher tự cài, về Home gốc, dịch vụ trợ năng của launcher (Projectivy tự thay Home bằng nó).
 */
class QuanLyLauncher(private val vm: PhienLamViec) {
    var tt by mutableStateOf<Launcher.TrangThai?>(null)
        private set
    /** versionName của từng launcher. */
    var ban by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    /** Home sạch trên tivi là bản đi kèm app (biết home_target: mở launcher đã chọn thay chính nó). */
    var homeSachMoi by mutableStateOf(false)
        private set
    /** Gói đang mở hàng thao tác. */
    var dangMo by mutableStateOf<String?>(null)
    var bao by mutableStateOf("")
    /** Lần bấm Thử nút Home gần nhất: gói hiện ra ("" = không đọc được); null = chưa thử. */
    var daThu by mutableStateOf<String?>(null)
        private set
    /** Hộp hỏi lại đang mở. */
    var hoi by mutableStateOf<Hoi?>(null)

    sealed interface Hoi {
        /**
         * Đặt [m] làm Home; [nhuong] = launcher mức ưu tiên cao hơn sẽ bị tắt trước; [moDe] = Home của hãng không nhường
         * được, Home sạch mở [m] đè lên nó; [coTheMoDe] = tắt được nhưng cũng có thể để Home sạch mở đè, giữ Home của hãng.
         */
        data class DatHome(val m: Launcher.Muc, val nhuong: List<String>, val moDe: Boolean = false, val coTheMoDe: Boolean = false) : Hoi
        data class Tat(val m: Launcher.Muc) : Hoi
        /** Bật lại [m] mà nó sẽ thành Home (mức ưu tiên cao hơn Home hiện tại). */
        data class BatGianh(val m: Launcher.Muc) : Hoi
        data class Go(val m: Launcher.Muc) : Hoi
        data object ThuNut : Hoi
        /** Bật dịch vụ trợ năng [dv] của launcher [m]. */
        data class BatTroNang(val m: Launcher.Muc, val dv: String) : Hoi
    }

    fun mo() = vm.chay(t("Đang đọc các launcher trên tivi…", "Reading the TV's launchers…")) {
        vm.damBaoKetNoi()
        bao = ""; daThu = null; dangMo = null
        doc()
        vm.man = Man.LAUNCHER
    }

    fun docLai() = vm.chay(t("Đang đọc các launcher trên tivi…", "Reading the TV's launchers…")) {
        vm.damBaoKetNoi()
        doc()
    }

    private suspend fun doc() {
        val moi = vm.io { vm.tv.docLauncher() }
        tt = moi
        ban = runCatching { vm.io { vm.tv.banCua(moi.ds.map { it.goi }) } }.getOrDefault(emptyMap())
        homeSachMoi = coHomeSach() && runCatching { vm.io { vm.tv.homeLaBanDiKem(apkHome()) } }.getOrDefault(false)
    }

    private fun apkHome(): ByteArray = vn.tivikit.CapNhat.moAsset(vm.ctx, vm.tv.apkHome).use { it.readBytes() }

    /** Kết nối tivi khác. */
    fun quen() { tt = null; ban = emptyMap(); homeSachMoi = false; dangMo = null; bao = ""; daThu = null; hoi = null }

    /** Home sạch có trên tivi và đang bật (để mở đè launcher khác lên Home của hãng). */
    private fun coHomeSach(): Boolean = tt?.ds?.any { it.loai == Loai.HOME_SACH && !it.dangTat } == true

    /** Home của hãng cho nút "Về Home gốc": Home trước khi dùng TiviKit nếu là Home của hãng, rồi Home gốc app biết. */
    fun homeGoc(): Launcher.Muc? {
        val goc = tt?.ds?.filter { it.loai == Loai.GOC }.orEmpty()
        val truoc = vm.saoLuu?.homeTruoc?.substringBefore('/')
        return goc.firstOrNull { it.goi == truoc } ?: ThaoTacTv.HOME_GOC_BIET.firstNotNullOfOrNull { g -> goc.firstOrNull { it.goi == g } } ?: goc.firstOrNull()
    }

    /** Launcher [m] là cái nút Home đang mở (Home mặc định, hoặc Home sạch mở đè lên nó): không đặt lại, không gỡ. */
    fun laHome(m: Launcher.Muc): Boolean = tt?.homeMo == m.goi

    fun lyDoGiu(m: Launcher.Muc): LyDoGiu? = tt?.let { Launcher.lyDoGiu(m.goi, it, ThaoTacTv.HOME_GOC_BIET) }

    /**
     * Có nút "Đặt làm Home" cho [m]: trừ khi nó đã là Home thật sự (Home mặc định và nút Home mở nó), hoặc nút Home đã mở
     * nó nhờ Home sạch mở đè mà Home của hãng không tắt được (bấm lại cũng y như cũ). Home sạch mở đè trên tivi tắt được
     * Home của hãng thì vẫn có nút: chuyển sang cách tắt Home của hãng.
     */
    fun coTheDatHome(m: Launcher.Muc): Boolean {
        val tt = tt ?: return false
        if (m.goi != tt.homeMo) return true
        if (m.goi == tt.goiMacDinh) return false
        val chan = Launcher.chan(m, tt)
        return chan.isNotEmpty() && chan.all { Launcher.tatDuocDeNhuong(it, tt, ThaoTacTv.HOME_GOC_BIET) }
    }

    /**
     * Bấm "Đặt làm Home": launcher mức ưu tiên cao hơn đang chặn thì hỏi tắt chúng (khi tắt được; có Home sạch thì cũng
     * cho chọn để Home sạch mở đè, giữ Home của hãng); không tắt được (Google TV) thì Home sạch mở đè lên; không có Home
     * sạch thì giải thích vì sao không được.
     */
    fun chonDatHome(m: Launcher.Muc) {
        val tt = tt ?: return
        val chan = Launcher.chan(m, tt)
        val moDeDuoc = m.loai == Loai.HOME_SACH || coHomeSach()
        hoi = when {
            chan.isEmpty() -> Hoi.DatHome(m, emptyList())
            // Đang được Home sạch mở đè rồi thì cách "không tắt" chính là hiện trạng: chỉ hỏi tắt.
            chan.all { Launcher.tatDuocDeNhuong(it, tt, ThaoTacTv.HOME_GOC_BIET) } -> Hoi.DatHome(m, chan, coTheMoDe = moDeDuoc && m.goi != tt.homeMo)
            moDeDuoc -> Hoi.DatHome(m, emptyList(), moDe = true)
            else -> { bao = khongDat(m, chan); null }
        }
    }

    /** Bấm "Bật lại": nó sẽ giành Home thì hỏi trước. */
    fun chonBat(m: Launcher.Muc) {
        val tt = tt ?: return
        if (Launcher.batLaGianhHome(m, tt)) hoi = Hoi.BatGianh(m) else bat(m)
    }

    /**
     * Đặt [m] làm Home; [nhuong] rỗng mà Home của hãng vẫn thắng thì Home sạch mở [m] đè lên — Home sạch bản cũ (chưa
     * biết home_target) thì cập nhật lên bản đi kèm trước, như bấm Áp dụng.
     */
    fun datHome(m: Launcher.Muc, nhuong: List<String>) = vm.chay(t("Đang đặt ${tenDep(m.goi)} làm Home…", "Making ${tenDep(m.goi)} the Home…")) {
        hoi = null
        vm.damBaoKetNoi()
        val sl = saoLuu() ?: return@chay
        val moDe = m.loai != Loai.HOME_SACH && nhuong.isEmpty() && tt?.let { Launcher.chan(m, it) }?.isNotEmpty() == true
        if (moDe && !homeSachMoi && coHomeSach()) {
            vm.thongBao = t("Đang cập nhật Home sạch…", "Updating the clean Home…")
            if (!vm.io { vm.tv.capNhatLauncher(apkHome()) }) error(t("Không cập nhật được Home sạch, nên chưa đổi gì. Thử lại.", "Couldn't update the clean Home, so nothing was changed. Try again."))
            homeSachMoi = true
        }
        val kq = vm.io { vm.tv.datHome(m.goi, sl, nhuong, homeSachMoi) { s -> baoViec(s) } }
        vm.veApp()
        daThu = null
        bao = baoDatHome(m, kq)
        sauKhiDoi()
    }

    fun tat(m: Launcher.Muc) = vm.chay(t("Đang tắt ${tenDep(m.goi)}…", "Disabling ${tenDep(m.goi)}…")) {
        hoi = null
        vm.damBaoKetNoi()
        lyDoGiu(m)?.let { error(moTaGiu(it, m)) }
        val sl = saoLuu() ?: return@chay
        val kq = vm.io { vm.tv.tatGoi(listOf(m.goi), sl, epSetupwraith = false) {} }
        (kq.boQua + kq.loi).firstOrNull()?.let { error(it.second) }
        if (m.goi !in kq.daTat) error(t("Tivi không cho tắt ${tenDep(m.goi)}.", "The TV refused to disable ${tenDep(m.goi)}."))
        bao = t("Đã tắt ${tenDep(m.goi)}: nó không chạy và không nhận nút Home nữa. Bật lại ở đây hoặc bằng Khôi phục tivi.",
            "${tenDep(m.goi)} disabled: it no longer runs or takes the Home button. Re-enable it here or by restoring the TV.")
        sauKhiDoi()
    }

    fun bat(m: Launcher.Muc) = vm.chay(t("Đang bật lại ${tenDep(m.goi)}…", "Re-enabling ${tenDep(m.goi)}…")) {
        hoi = null
        vm.damBaoKetNoi()
        val sl = saoLuu() ?: return@chay
        if (!vm.io { vm.tv.batLauncher(m.goi, sl) }) error(t("Tivi không bật lại được ${tenDep(m.goi)}.", "The TV couldn't re-enable ${tenDep(m.goi)}."))
        sauKhiDoi()
        bao = if (tt?.homeMo == m.goi) t("Đã bật lại ${tenDep(m.goi)}, nút Home giờ mở nó.", "${tenDep(m.goi)} re-enabled; the Home button opens it now.")
            else t("Đã bật lại ${tenDep(m.goi)}.", "${tenDep(m.goi)} re-enabled.")
    }

    fun go(m: Launcher.Muc) = vm.chay(t("Đang gỡ ${tenDep(m.goi)}…", "Uninstalling ${tenDep(m.goi)}…")) {
        hoi = null
        vm.damBaoKetNoi()
        if (laHome(m)) error(t("${tenDep(m.goi)} đang là Home: đặt launcher khác làm Home trước.", "${tenDep(m.goi)} is the Home: make another launcher the Home first."))
        val out = vm.io { vm.tv.goApp(m.goi) }
        if (!out.contains("Success")) error(t("Gỡ không được: ${out.trim()}", "Uninstall failed: ${out.trim()}"))
        // Gỡ rồi thì Khôi phục không còn gì để bật lại.
        vm.saoLuu?.let { sl -> if (sl.daTatBoiApp.remove(m.goi)) vm.io { vm.tv.luuSaoLuu(sl) } }
        bao = t("Đã gỡ ${tenDep(m.goi)}.", "${tenDep(m.goi)} uninstalled.")
        sauKhiDoi()
    }

    fun moThu(m: Launcher.Muc) = vm.chay(t("Đang mở ${tenDep(m.goi)} trên tivi…", "Opening ${tenDep(m.goi)} on the TV…")) {
        vm.damBaoKetNoi()
        val cmp = m.cmp ?: error(t("Chưa biết màn Home của ${tenDep(m.goi)}.", "${tenDep(m.goi)}'s Home screen is unknown."))
        vm.io { vm.tv.moThu(cmp) }?.let { error(t("Tivi không mở được: $it", "The TV couldn't open it: $it")) }
        bao = t("Đã mở ${tenDep(m.goi)} trên tivi; bấm Home trên remote là về Home đang dùng.", "${tenDep(m.goi)} is open on the TV; the remote's Home button goes back to the current Home.")
    }

    fun thuNut() = vm.chay(t("Đang bấm thử nút Home trên tivi…", "Pressing Home on the TV…")) {
        hoi = null
        bao = ""
        vm.damBaoKetNoi()
        val g = vm.io { vm.tv.thuNutHome(tt?.homeMo.orEmpty()) }
        vm.veApp()
        doc()
        daThu = g
    }

    /** Dịch vụ trợ năng của launcher [m] (thường chỉ một). */
    fun troNangCua(m: Launcher.Muc): List<String> = tt?.troNangCua?.get(m.goi).orEmpty()

    fun troNangDangBat(dv: String): Boolean = tt?.troNangDangBat?.contains(dv) == true

    /**
     * Bật ([lamMoi] false) hay làm mới (gỡ ra rồi bật lại) dịch vụ trợ năng [dv] của [m]. Home sạch đang tự mở đè lên Home
     * của hãng mà không mở [m]: nhắc kẻo hai bên giành nhau.
     */
    fun batTroNang(m: Launcher.Muc, dv: String, lamMoi: Boolean) = vm.chay(t("Đang bật trợ năng của ${tenDep(m.goi)}…", "Turning on ${tenDep(m.goi)}'s accessibility service…")) {
        hoi = null
        vm.damBaoKetNoi()
        vm.io { vm.tv.batTroNang(dv, lamMoi) }
        doc()
        val tt = tt
        bao = (if (lamMoi) t("Đã làm mới dịch vụ trợ năng của ${tenDep(m.goi)}.", "${tenDep(m.goi)}'s accessibility service restarted.")
            else t("Đã bật dịch vụ trợ năng của ${tenDep(m.goi)}.", "${tenDep(m.goi)}'s accessibility service is on.")) +
            (if (m.goi in Launcher.CAI_DAT) t(" Muốn nó tự thay Home của hãng: mở cài đặt của nó, bật \"Ghi đè trình khởi chạy hiện tại\".",
                " For it to take over the maker's Home: open its settings and turn on \"Override current launcher\".") else "") +
            (if (tt != null && tt.chuyenDe.isNotEmpty() && tt.homeMo != m.goi) t(" Lưu ý: Home sạch đang tự mở đè lên ${tenDep(tt.chuyenDe)}; bật cả ghi đè của ${tenDep(m.goi)} thì hai bên giành nhau — đặt ${tenDep(m.goi)} làm Home ở đây (Home sạch sẽ mở nó) cho gọn.",
                " Note: the clean Home opens over ${tenDep(tt.chuyenDe)}; with ${tenDep(m.goi)}'s override on too, the two fight — make ${tenDep(m.goi)} the Home here instead (the clean Home then opens it).") else "")
    }

    fun tatTroNang(m: Launcher.Muc, dv: String) = vm.chay(t("Đang tắt trợ năng của ${tenDep(m.goi)}…", "Turning off ${tenDep(m.goi)}'s accessibility service…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.tatTroNang(dv) }
        doc()
        bao = t("Đã tắt dịch vụ trợ năng của ${tenDep(m.goi)}: nó thôi bắt phím remote và thôi tự thay Home.",
            "${tenDep(m.goi)}'s accessibility service is off: it stops catching remote keys and taking over Home.")
    }

    fun moCaiDat(m: Launcher.Muc) = vm.chay(t("Đang mở cài đặt của ${tenDep(m.goi)}…", "Opening ${tenDep(m.goi)}'s settings…")) {
        vm.damBaoKetNoi()
        if (!vm.io { vm.tv.moCaiDatCua(m.goi) }) error(t("Tivi không mở được cài đặt của ${tenDep(m.goi)}.", "The TV couldn't open ${tenDep(m.goi)}'s settings."))
        bao = t("Đã mở cài đặt của ${tenDep(m.goi)} trên tivi.", "${tenDep(m.goi)}'s settings are open on the TV.")
    }

    private suspend fun saoLuu(): BanSaoLuu? {
        val info = vm.thongTin ?: return null
        return vm.saoLuu ?: vm.io { vm.tv.damBaoSaoLuu(info, vm.getprop, vm.ip.trim()) }.also { vm.saoLuu = it }
    }

    /** Đọc lại launcher, danh sách gói (màn khác dùng) và danh sách tivi đã dọn sau mỗi lần đổi. */
    private suspend fun sauKhiDoi() {
        doc()
        runCatching { vm.trangThai = vm.io { vm.tv.docTrangThai() } }
        vm.taiLaiSaoLuu()
    }

    private fun baoViec(s: String) { vm.viewModelScope.launch(Dispatchers.Main) { vm.thongBao = s } }

    /** Vì sao không tắt được [m] (dòng chữ nhỏ dưới hàng thao tác). */
    fun moTaGiu(ly: LyDoGiu, m: Launcher.Muc): String = when (ly) {
        LyDoGiu.HOME_SACH -> t("Home sạch giữ cả nút remote gán riêng, ô Kênh nhanh: không tắt ở đây. Gỡ hẳn ở Khôi phục.",
            "The clean Home also runs remote buttons and Quick channel tiles: it isn't disabled here. Remove it from Restore.")
        LyDoGiu.DANG_LA_HOME -> t("Đang là Home: đặt launcher khác làm Home trước rồi mới tắt.", "It's the Home now: make another launcher the Home first.")
        LyDoGiu.CON_MOT -> t("Launcher bật cuối cùng: tắt là tivi không còn Home.", "It's the last launcher on: disabling it leaves the TV with no Home.")
        LyDoGiu.GOOGLE_TV -> t("Google TV còn trình cài đặt setupwraith: tắt Home Google là tivi kẹt ở màn cài đặt ban đầu.",
            "This Google TV still has the setupwraith setup app: disabling Google's Home leaves the TV stuck on the setup screen.")
        LyDoGiu.CVTE -> t("Home của bo mạch CVTE: đã có tivi treo ở màn khởi động sau khi tắt nó.", "The CVTE board's Home: one TV hung at boot after it was disabled.")
        LyDoGiu.HE_THONG -> t("${tenDep(m.goi)} là phần của hệ thống, không tắt ở đây.", "${tenDep(m.goi)} is part of the system and isn't disabled here.")
    }

    /** Không đặt được [m] làm Home mặc định vì [chan] (mức ưu tiên cao hơn) không tắt được, mà tivi không có Home sạch. */
    private fun khongDat(m: Launcher.Muc, chan: List<String>): String {
        val tt = tt ?: return ""
        val ten = tenDep(m.goi)
        val uu = tt.batNay.filter { it.goi in chan }.maxOfOrNull { it.uuTien } ?: 0
        val lyDo = when {
            tt.setupBat && chan.any { it in ThaoTacTv.HOME_GOC_BIET || it == ThaoTacTv.SETUP } ->
                t("Google TV còn trình cài đặt setupwraith nên Home Google không tắt được (tắt là tivi kẹt ở màn cài đặt ban đầu)",
                    "this Google TV still has the setupwraith setup app, so Google's Home can't be disabled (the TV would get stuck on the setup screen)")
            chan.any { KeHoach.laHomeCvte(it) } -> t("Home của bo mạch CVTE không được tắt (đã có tivi treo ở màn khởi động)", "the CVTE board's Home must stay on (one TV hung at boot)")
            else -> t("app không tắt được nó", "the app can't disable it")
        }
        // Tên tuỳ chọn lấy đúng từ Projectivy 4.72 (app_settings_launcher_override_home, bản tiếng Việt và tiếng Anh).
        return t("Chưa đặt được $ten làm Home: Android luôn chọn ${chan.joinToString { tenDep(it) }} vì mức ưu tiên cao hơn ($uu so với ${m.uuTien}), mà $lyDo. " +
            "Cách khác: cài Home sạch (nó mở $ten đè lên Home của hãng), hoặc launcher tự bắt được nút Home — Projectivy: bật dịch vụ trợ năng của nó ở đây, rồi trong cài đặt của nó bật \"Ghi đè trình khởi chạy hiện tại\".",
            "Can't make $ten the Home: Android always picks ${chan.joinToString { tenDep(it) }} for its higher priority ($uu vs ${m.uuTien}), and $lyDo. " +
            "Instead: install the clean Home (it opens $ten over the maker's Home), or use a launcher that takes the Home button itself — Projectivy: turn on its accessibility service here, then \"Override current launcher\" in its settings.")
    }

    private fun baoDatHome(m: Launcher.Muc, kq: ThaoTacTv.KetQuaDatHome): String {
        val ten = tenDep(m.goi)
        if (kq.ok) return t("Đã đặt $ten làm Home: bấm Home trên remote là ra $ten.", "$ten is now the Home: the remote's Home button opens it.") +
            (if (kq.quaHomeSach) t(" Home của hãng vẫn giữ quyền Home (không tắt được), Home sạch mở $ten đè lên mỗi khi nó hiện ra.",
                " The maker's Home keeps the Home role (it can't be disabled); the clean Home opens $ten over it whenever it comes up.") else "") +
            (if (kq.daTat.isNotEmpty()) t(" Đã tắt ${kq.daTat.joinToString { tenDep(it) }} để nhường Home; Khôi phục tivi bật lại được.",
                " Disabled ${kq.daTat.joinToString { tenDep(it) }} so it gives up Home; restoring the TV turns it back on.") else "") +
            (if (kq.boKhoa.any { it == "stock_home_redirect" || it == "home_long_press_home" }) t(" Home sạch thôi tự mở đè lên Home.", " The clean Home no longer opens over the Home.") else "") +
            (if (kq.boKhoa.any { it.startsWith("power_on_home") }) t(" Home sạch thôi tự mở khi bật tivi.", " The clean Home no longer opens when the TV turns on.") else "")
        val ly = kq.lyDo
        return when {
            ly == "khong-co-man" -> t("$ten không có màn Home dùng được.", "$ten has no usable Home screen.")
            ly.startsWith("chan:") -> t("Android vẫn chọn ${tenDep(ly.removePrefix("chan:"))} làm Home (mức ưu tiên cao hơn). Tivi đã về như trước.",
                "Android still picks ${tenDep(ly.removePrefix("chan:"))} as Home (higher priority). The TV is back as it was.")
            else -> {
                val g = ly.removePrefix("mo:")
                val moGi = if (g.isBlank()) t("một màn không rõ", "an unknown screen") else tenDep(g)
                t("Bấm Home vẫn mở $moGi thay vì $ten. Tivi đã về như trước.", "Home still opens $moGi instead of $ten. The TV is back as it was.") +
                    if (g in tt?.troNangKhac.orEmpty()) t(" ${tenDep(g)} đang bật dịch vụ trợ năng tự bắt nút Home: tắt nó ở dòng ${tenDep(g)} bên dưới rồi thử lại.",
                        " ${tenDep(g)} has an accessibility service that takes the Home button: turn it off on ${tenDep(g)}'s row below and try again.") else ""
            }
        }
    }
}
