package vn.tivikit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.tivikit.dulieu.HenGio
import vn.tivikit.dulieu.KenhNhanh
import vn.tivikit.dulieu.tenDep
import vn.tivikit.t

/**
 * Hẹn giờ (25/10/2026): soạn lịch bật tivi / tắt tivi / mở app trên điện thoại, lưu lên app phụ Hẹn giờ (module hengio/)
 * — tivi tự chạy theo lịch, không cần điện thoại — và thử xem tivi có tự bật được từ chế độ chờ không.
 */
class HenGioTivi(private val vm: PhienLamViec) {
    /** Lịch đang soạn (đọc từ tivi lúc mở màn). */
    val ds = mutableStateListOf<HenGio.Viec>()
    /** Có thay đổi chưa lưu lên tivi. */
    var chuaLuu by mutableStateOf(false)
        private set
    var trangThai by mutableStateOf<HenGio.TrangThai?>(null)
        private set
    /** App phụ đã có trên tivi. */
    var daCai by mutableStateOf(false)
        private set
    var bao by mutableStateOf("")
        private set
    /** App mở được trên tivi (cho việc mở app / bật tivi rồi mở app), Kênh nhanh lên đầu. */
    var dsApp by mutableStateOf<List<String>>(emptyList())
        private set

    // Ô soạn: thêm việc mới ([dangSua] null) hay sửa việc [dangSua].
    var loai by mutableStateOf(HenGio.BAT)
    var gio by mutableStateOf("")
    var ngay by mutableStateOf((1..7).toSet())
    var goi by mutableStateOf<String?>(null)
    /** Việc đang sửa ở ô soạn (bút ở một dòng lịch); null = ô soạn thêm việc mới. */
    var dangSua by mutableStateOf<HenGio.Viec?>(null)
        private set

    /** Android của tivi: app phụ cần 8+, tắt tivi cần 9+. */
    val sdk: Int get() = vm.thongTin?.sdk ?: 0
    val duocCai: Boolean get() = sdk >= 26
    val duocTat: Boolean get() = sdk >= 28

    fun mo() = vm.chay(t("Đang đọc hẹn giờ trên tivi…", "Reading the timer on the TV…")) {
        vm.damBaoKetNoi()
        bao = ""
        val tt = vm.io { vm.tv.docTrangThai() }
        vm.trangThai = tt
        val apps = vm.appChoDock()
        dsApp = apps.filter { KenhNhanh.laGoiKenh(it) }.sortedBy { KenhNhanh.oCua(it) } + apps.filter { !KenhNhanh.laGoiKenh(it) }
        docLaiTv()
        vm.man = Man.HEN_GIO
    }

    /** Đọc lại lịch + trạng thái từ tivi (bỏ thay đổi chưa lưu). */
    private suspend fun docLaiTv() {
        daCai = vm.io { vm.tv.henGioDaCai() }
        val j = if (daCai) vm.io { vm.tv.docHenGio() } else null
        trangThai = j?.let { HenGio.trangThai(it) }
        ds.clear(); trangThai?.let { ds.addAll(HenGio.sapXep(it.lich)) }
        chuaLuu = false; dangSua = null
    }

    fun lamMoi() = vm.chay(t("Đang đọc hẹn giờ trên tivi…", "Reading the timer on the TV…")) {
        vm.damBaoKetNoi(); bao = ""; docLaiTv()
    }

    /** Thêm việc ở ô soạn vào lịch đang soạn, hoặc thay việc đang sửa [dangSua] (chưa lưu lên tivi). */
    fun them() {
        val (g, p) = HenGio.docGio(gio) ?: run { vm.loi = t("Giờ dạng 06:30 (0:00 đến 23:59).", "Time like 06:30 (0:00 to 23:59)."); return }
        if (ngay.isEmpty()) { vm.loi = t("Chọn ít nhất một ngày.", "Pick at least one day."); return }
        if (loai == HenGio.MO && goi == null) { vm.loi = t("Chọn app cần mở.", "Pick the app to open."); return }
        if (loai == HenGio.TAT && !duocTat) { vm.loi = t("Tắt tivi theo giờ cần Android 9 trở lên.", "Turning the TV off on a timer needs Android 9 or later."); return }
        val cu = dangSua
        if (cu == null && ds.size >= HenGio.TOI_DA) { vm.loi = t("Tối đa ${HenGio.TOI_DA} việc.", "At most ${HenGio.TOI_DA} entries."); return }
        // Việc đang sửa giữ công tắc bật / tắt của nó.
        val v = HenGio.Viec(g, p, ngay, loai, if (loai == HenGio.TAT) null else goi, cu?.bat ?: true)
        if (v != cu && v in ds) { vm.loi = t("Việc này đã có trong lịch.", "This entry is already in the schedule."); return }
        vm.loi = ""
        val moi = HenGio.sapXep(ds.filter { it != cu } + v)
        ds.clear(); ds.addAll(moi)
        dangSua = null
        gio = ""; chuaLuu = true; bao = ""
    }

    /** Đưa việc [v] lên ô soạn để đổi giờ, ngày, loại việc hay app (31/10/2026); Cập nhật việc thì thay đúng việc đó. */
    fun sua(v: HenGio.Viec) {
        dangSua = v
        loai = v.loai; gio = v.gioChu; ngay = v.ngay; goi = v.goi
        vm.loi = ""; bao = ""
    }

    fun thoiSua() { dangSua = null; gio = ""; vm.loi = "" }

    fun xoa(v: HenGio.Viec) {
        ds.remove(v); chuaLuu = true; bao = ""
        if (v == dangSua) thoiSua()
    }

    fun doiBat(v: HenGio.Viec, bat: Boolean) {
        val i = ds.indexOf(v); if (i < 0) return
        val moi = v.copy(bat = bat)
        ds[i] = moi; chuaLuu = true; bao = ""
        if (v == dangSua) dangSua = moi
    }

    /** Cài / cập nhật app phụ, bật dịch vụ của nó, gửi lịch; đọc lại để hiện việc kế tiếp. */
    fun luu() = vm.chay(t("Đang lưu hẹn giờ lên tivi…", "Saving the timer to the TV…")) {
        if (!duocCai) error(t("Hẹn giờ cần Android 8 trở lên, tivi này là Android ${vm.thongTin?.android}.", "The timer needs Android 8 or later; this TV runs Android ${vm.thongTin?.android}."))
        dangSua?.let { error(t("Đang sửa việc ${it.gioChu}: bấm Cập nhật việc hoặc Thôi sửa trước.", "You're editing the ${it.gioChu} entry: tap Update the entry or Stop editing first.")) }
        vm.damBaoKetNoi()
        bao = ""
        val apk = vm.io { vn.tivikit.CapNhat.moAsset(vm.ctx, "hengio.apk").readBytes() }
        vm.io { vm.tv.caiHenGio(apk, HenGio.json(ds.toList())) { s -> vm.thongBao = s } }
        docLaiTv()
        val tt = trangThai
        bao = when {
            tt == null -> t("Đã gửi lịch nhưng chưa đọc lại được từ tivi. Bấm Làm mới.", "Sent, but couldn't read it back from the TV. Tap Refresh.")
            !tt.dichVuChay -> t("Đã lưu, nhưng dịch vụ Hẹn giờ chưa chạy trên tivi: vào Cài đặt → Trợ năng của tivi, bật \"Hẹn giờ (TiviKit)\", rồi bấm Làm mới.",
                "Saved, but the Timer service isn't running on the TV: open the TV's Settings → Accessibility, turn on \"Timer (TiviKit)\", then tap Refresh.")
            tt.keTiep > 0 -> t("Đã lưu lên tivi. ", "Saved to the TV. ") + keTiepChu(tt)
            else -> t("Đã lưu lên tivi. Lịch đang trống hoặc đang tắt hết.", "Saved to the TV. The schedule is empty or all off.")
        }
    }

    /** "Việc kế tiếp: 06:30 Thứ Hai." theo giờ của tivi. */
    fun keTiepChu(tt: HenGio.TrangThai): String {
        if (tt.keTiep <= 0) return ""
        val (g, thu) = HenGio.gioCua(tt.keTiep, tt.muiGio)
        return t("Việc kế tiếp: $g ${tenThu(thu)}.", "Next: $g ${tenThu(thu)}.")
    }

    /** Hẹn tivi tự bật sau 2 phút: người dùng tắt tivi bằng remote ngay, xem tivi có tự lên không. */
    fun thu() = vm.chay(t("Đang hẹn thử…", "Setting up the test…")) {
        vm.damBaoKetNoi()
        if (!daCai || trangThai == null) error(t("Bấm Lưu lên tivi trước (cài app Hẹn giờ), rồi thử.", "Tap Save to the TV first (installs the Timer app), then test."))
        if (!vm.io { vm.tv.thuDanhThuc(120) }) error(t("Tivi không nhận lệnh thử.", "The TV didn't accept the test."))
        bao = t("Đã hẹn: bấm nút nguồn trên remote tắt tivi NGAY bây giờ. Khoảng 2 phút nữa tivi phải tự bật và hiện \"Hẹn giờ: tivi đã tự bật\". Không bật: tivi này tắt sâu khi chờ, hẹn giờ bật không chạy được (tắt và mở app vẫn chạy).",
            "Done: press the power button on the remote NOW to turn the TV off. In about 2 minutes it should turn itself on and show \"Timer: the TV turned itself on\". If not, this TV powers down deeply in standby and timed power-on can't work (turning off and opening apps still do).")
    }

    fun go() = vm.chay(t("Đang gỡ Hẹn giờ…", "Removing the Timer…")) {
        vm.damBaoKetNoi()
        vm.io { vm.tv.goHenGio() }
        daCai = false; trangThai = null; ds.clear(); chuaLuu = false
        bao = t("Đã gỡ Hẹn giờ khỏi tivi, lịch đã xoá.", "The Timer is removed from the TV; the schedule is gone.")
    }

    fun quen() { ds.clear(); trangThai = null; daCai = false; chuaLuu = false; bao = ""; dsApp = emptyList(); goi = null; dangSua = null }

    /** Tên app hiện trong lịch: ô Kênh nhanh kèm kênh của nó, tên quen, hoặc đuôi tên gói. */
    fun tenApp(p: String): String = vm.kenhNhanh.tenO(p)
        ?: vm.qlApp.ds.firstOrNull { it.tv.pkg == p }?.ten?.takeIf { it.isNotEmpty() } ?: tenDep(p)

    /** Mô tả một việc: "Bật tivi rồi mở Kênh nhanh (VTV1)", "Tắt tivi", "Mở SmartTube". */
    fun moTa(v: HenGio.Viec): String = when (v.loai) {
        HenGio.BAT -> t("Bật tivi", "Turn the TV on") + (v.goi?.let { t(" rồi mở ", ", then open ") + tenApp(it) } ?: "")
        HenGio.TAT -> t("Tắt tivi", "Turn the TV off")
        else -> t("Mở ", "Open ") + (v.goi?.let { tenApp(it) } ?: "?")
    }

    /** Một dòng nhật ký trên tivi, nói cho người dùng: "Mở Kênh nhanh: VTV1 – đã mở". */
    fun moTa(n: HenGio.NhatKy): String = HenGio.moTa(n, ::tenApp)

    companion object {
        private val THU = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")
        private val THU_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        fun tenThu(d: Int): String = if (d in 1..7) t(THU[d - 1], THU_EN[d - 1]) else ""
    }
}
