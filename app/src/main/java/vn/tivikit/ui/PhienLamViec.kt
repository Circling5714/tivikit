package vn.tivikit.ui

import vn.tivikit.t
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vn.tivikit.adb.DoMang
import vn.tivikit.adb.KetNoiAdb
import vn.tivikit.adb.ThaoTacTv
import vn.tivikit.dulieu.BanSaoLuu
import vn.tivikit.dulieu.DuLieu
import vn.tivikit.dulieu.FileApk
import vn.tivikit.dulieu.GoiApk
import vn.tivikit.dulieu.KeHoach
import vn.tivikit.dulieu.KhoSaoLuu
import vn.tivikit.dulieu.THANH_PHO
import vn.tivikit.dulieu.MauTv
import vn.tivikit.dulieu.MucGoi
import vn.tivikit.dulieu.ThongTinTv
import vn.tivikit.dulieu.TrangThaiGoi
import vn.tivikit.dulieu.tenDep
import java.net.Inet4Address

enum class Man { CHINH, KET_NOI, THONG_TIN, CHON_GOI, DANG_TAT, HOME_SACH, XONG, KHOI_PHUC, HUONG_DAN, GIAY_PHEP, KHOA_TRE, CAI_APK, DIEU_KHIEN, QUAN_LY_APP, GUI_FILE, BO_CAI, DNS, GAN_PHIM, KENH_NHANH, HEN_GIO, LAM_MOI, MANG, CAI_DAT_NHANH, CHUYEN_MAY, LG, DONG_LENH, LIEN_KET_APP, LAUNCHER }

/** Số app tối đa trong dock: bằng số cột lớn nhất của Home sạch (columns 1–8); dock dài hơn số cột thì tràn xuống hàng sau (22/10/2026, góp ý muốn hàng đầu nhiều app hơn). */
const val DOCK_TOI_DA = 8

/**
 * Bảng phím chung cho TCL không có mẫu riêng: Netflix 4062, YouTube 4065, Prime Video 4099, bánh răng 83 / 4077 — cùng số
 * trên mọi mẫu TCL có bảng trong upstream (tcl-43c645, tcl-55c725, tcl-65c735, tcl-g10…).
 */
/** doiHang đặc biệt: hàng dock nằm giữa màn hình (rows_middle của bản vá launcher), góp ý 24/10/2026. */
const val HANG_GIUA = Int.MIN_VALUE

private const val PHIM_TCL_CHUNG = """{"app_keys":["4062:com.netflix.ninja","4065:com.google.android.youtube.tv","4099:com.amazon.amazonvideo.livingroom"],"settings_key_keycodes":[83,4077]}"""

/**
 * Mẫu mà phím remote mặc định TẮT: TCL G09 Android 12 — bật dịch vụ lọc phím là giữ nút âm lượng chỉ nhảy một nấc
 * (upstream docs/devices/tcl-55p71b-g09-key-repeat.md). Người dùng vẫn tự bật được.
 */
private val PHIM_TAT_SAN = setOf("tcl-g09-google-tv")

/** Trình duyệt cho nút Web của remote TCL, theo thứ tự ưu tiên; tivi không có cái nào thì nút Web để nguyên. */
private val TRINH_DUYET_TV = listOf("com.android.chrome", "com.tcl.browser", "com.phlox.tvwebbrowser", "com.cloudmosa.puffinTV", "org.mozilla.tv.firefox")

class PhienLamViec(app: Application) : AndroidViewModel(app) {
    internal val ctx: Context get() = getApplication()
    internal val adb by lazy { KetNoiAdb.lay(ctx) }
    val kho by lazy { KhoSaoLuu(ctx) }
    internal val tv by lazy { ThaoTacTv(adb, kho) }
    private val prefs by lazy { ctx.getSharedPreferences("tvsach", Context.MODE_PRIVATE) }

    var man by mutableStateOf(Man.CHINH)
    /** Màn để quay về sau khi xem Hướng dẫn / Giấy phép. */
    var manTruoc by mutableStateOf(Man.CHINH)

    /** Remote + gõ chữ (ui/DieuKhien.kt). */
    val dieuKhien by lazy { DieuKhien(this) }
    /** Xem màn hình tivi trên màn Điều khiển (kiểu scrcpy). */
    val xemManHinh by lazy { XemManHinh(this) }
    /** Kênh điều khiển scrcpy không hình của màn Điều khiển: phím remote nhận ngay, dán chữ có dấu khi bộ gõ hỏng. */
    val kenhDieuKhien by lazy { KenhDieuKhien(this) }
    /** Thẻ Đang phát của màn Điều khiển (đọc `dumpsys media_session`). */
    val dangPhat by lazy { DangPhatTv(this) }
    /** Hộp Góp ý & liên hệ, hộp Ủng hộ tác giả (LienHeUngHo.kt), mở được từ mọi màn. */
    var moLienHe by mutableStateOf(false)
    var moUngHo by mutableStateOf(false)
    /** Gửi danh sách gói đã tắt cho CleanYourTV (ui/GopNguocTivi.kt). */
    val gopNguoc by lazy { GopNguocTivi(this) }
    /** Quản lý app trên tivi (ui/QuanLyApp.kt). */
    val qlApp by lazy { QuanLyApp(this) }
    /** Gửi ảnh / video / file lên tivi (ui/GuiFile.kt). */
    val guiFile by lazy { GuiFile(this) }
    /** Bộ cài đặt mẫu (ui/BoCaiMau.kt). */
    val boCai by lazy { BoCaiMau(this) }
    /** Nút remote gán riêng (ui/GanPhim.kt). */
    val ganPhim by lazy { GanPhim(this) }
    /**
     * Mục app_keys người dùng gán ở màn Nút remote (vgs_phim_rieng trong config.json của tivi đang nối): mọi lần ghi cấu
     * hình Home sạch (cauHinhHome) ghép lại vào, để Đổi giao diện không làm mất.
     */
    val phimRieng = mutableStateListOf<String>()
    /** Kênh nhanh: ô kênh TV360 và VTVgo (ui/KenhNhanhTivi.kt). */
    val kenhNhanh by lazy { KenhNhanhTivi(this) }
    /** Hẹn giờ bật / tắt tivi, mở app (ui/HenGioTivi.kt). */
    val henGio by lazy { HenGioTivi(this) }
    /** Làm mới tivi: RAM, bộ nhớ, đóng app nằm nền, xoá cache khi gần đầy (ui/LamMoiTivi.kt). */
    val lamMoi by lazy { LamMoiTivi(this) }
    /** Kiểm tra mạng tivi: sóng Wi-Fi, băng tần, ping, nhận xét (ui/MangTivi.kt). */
    val mang by lazy { MangTivi(this) }
    /** Cài đặt nhanh: hiệu ứng, tự tắt, màn hình chờ, tiếng bấm remote (ui/CaiDatNhanhTivi.kt). */
    val caiDatNhanh by lazy { CaiDatNhanhTivi(this) }
    /** Cập nhật app gợi ý đã cài (SmartTube, Kodi…) lên bản mới nhất (ui/CapNhatApp.kt). */
    val capNhatApp by lazy { CapNhatApp(this) }
    /** Sao lưu dữ liệu TiviKit ra file / nhập trên điện thoại khác (ui/ChuyenMay.kt). */
    val chuyenMay by lazy { ChuyenMay(this) }
    /** Giữ kết nối: rớt thì tự nối lại, mở app thì tự nối tivi lần trước (ui/GiuKetNoi.kt). */
    val giuKetNoi by lazy { GiuKetNoi(this) }
    /** Tivi LG webOS: điều khiển, gõ chữ, mở app… không qua ADB (ui/LgTivi.kt). */
    val lg by lazy { LgTivi(this) }
    /** Dòng lệnh ADB: gõ lệnh shell chạy trên tivi, lệnh mẫu có giải thích (ui/DongLenhTivi.kt). */
    val dongLenh by lazy { DongLenhTivi(this) }
    /** Set App Link: chọn app mở link của từng trang, VD link YouTube mở bằng SmartTube (ui/LienKetAppTivi.kt). */
    val lienKet by lazy { LienKetAppTivi(this) }
    /** Quản lý launcher: chọn launcher cho nút Home, bật / tắt, gỡ (ui/QuanLyLauncher.kt). */
    val qlLauncher by lazy { QuanLyLauncher(this) }
    /**
     * Tên các ô Kênh nhanh trên Home sạch (app_labels "vn.tivikit.kenh3=VTV3" trong config.json của tivi đang nối):
     * gói → tên. cauHinhHome ghép lại vào như phimRieng.
     */
    val tenKenh = mutableStateMapOf<String, String>()
    /** Logo các ô Kênh nhanh (app_banners "vn.tivikit.kenh3=kenh-o3" trên tivi đang nối): gói → tên ảnh; ghép lại như tenKenh. */
    val anhOKenh = mutableStateMapOf<String, String>()

    fun moHuongDan() { if (man != Man.HUONG_DAN) manTruoc = man; man = Man.HUONG_DAN }
    fun moGiayPhep() { if (man != Man.GIAY_PHEP) manTruoc = man; man = Man.GIAY_PHEP }
    var dangBan by mutableStateOf(false)
    var thongBao by mutableStateOf("")
    var loi by mutableStateOf("")

    // Kết nối
    var ip by mutableStateOf("")
    var cong by mutableStateOf("5555")
    val timThay = mutableStateListOf<String>()
    var dangQuet by mutableStateOf(false)
    var ghepIpCong by mutableStateOf("")
    var ghepMa by mutableStateOf("")

    // Tivi
    var thongTin by mutableStateOf<ThongTinTv?>(null)
    /** Máy đích không khai là tivi (ThaoTacTv.laTivi): chặn dọn / đổi Home cho tới khi người dùng xác nhận đúng là tivi. */
    var khongPhaiTivi by mutableStateOf(false)
    var xacNhanLaTivi by mutableStateOf(false)
    val chanKhongPhaiTivi get() = khongPhaiTivi && !xacNhanLaTivi
    internal var getprop = ""
        private set
    var trangThai by mutableStateOf<TrangThaiGoi?>(null)
    var ungVien by mutableStateOf<List<Pair<MauTv, Int>>>(emptyList())
    /** Mẫu cùng hãng tên máy gần giống (DuLieu.ganGiong): chỉ gợi ý, không tự chọn. */
    var ganGiong by mutableStateOf<List<MauTv>>(emptyList())
    /** Toàn bộ mẫu, cho hộp "Chọn mẫu khác". */
    var tatCaMau by mutableStateOf<List<MauTv>>(emptyList())
    var moChonMau by mutableStateOf(false)
    /** Màn Home sạch: thẻ đang mở (0 Giao diện, 1 Home, 2 Tuỳ chỉnh) và màn Nâng cao riêng. */
    var theHome by mutableStateOf(0)
    var moNangCao by mutableStateOf(false)
    var mauChon by mutableStateOf<MauTv?>(null)
    var saoLuu by mutableStateOf<BanSaoLuu?>(null)
    /** Bản sao lưu vừa lấy từ tivi (điện thoại này không có). */
    var saoLuuTuTv by mutableStateOf(false)

    // Chọn gói
    var muc by mutableStateOf<List<MucGoi>>(emptyList())
    val chon = mutableStateMapOf<String, Boolean>()
    var epSetupwraith by mutableStateOf(false)

    // Kết quả
    val nhatKy = mutableStateListOf<String>()
    var ketQua by mutableStateOf<ThaoTacTv.KetQuaTat?>(null)
    var anhTv by mutableStateOf<Bitmap?>(null)

    // Home sạch
    val dock = mutableStateListOf<String>()
    var tenChao by mutableStateOf("")
    var nutInput by mutableStateOf(false)
    var nutHome by mutableStateOf(false)
    var dnsChanQc by mutableStateOf(false)
    var nutManCho by mutableStateOf(true)
    var gonNut by mutableStateOf(true)
    /** Dời khối dock + hàng app lên (âm) / xuống (dương), dp — rows_offset_dp của bản vá launcher (góp ý 24/10/2026). */
    var doiHang by mutableStateOf(0)
    /** Nút "Đang phát" trên Home sạch (now_playing của bản vá launcher), mặc định bật. */
    var nutDangPhat by mutableStateOf(true)
    /** Cỡ chữ cụm đồng hồ / ngày / âm lịch / thời tiết (header_scale của bản vá launcher, %), góp ý 24/10/2026. */
    var coChuDau by mutableStateOf(100)
    /** Mọi app trên một hàng cuộn ngang, không lưới bên dưới (single_row của launcher 2.8, cần dock_row). */
    var motHang by mutableStateOf(false)
    /** Ẩn lời chào trên đồng hồ (show_greeting=false). */
    var anLoiChao by mutableStateOf(false)
    /** Ô icon đều nhau: icon trên nền màu của chính nó, không dùng ảnh banner (uniform_tiles). */
    var oDeuNhau by mutableStateOf(false)
    /**
     * Giữ nút âm lượng để tăng/giảm liên tục (key_filter=false): dịch vụ bắt phím của Home sạch làm Android thôi tự lặp
     * phím giữ trên Sony, Xiaomi, Hisense (upstream #1680, #944, #1326; CleanTV 2.10.10). Bật thì dịch vụ vẫn chạy để mở
     * đè Home cũ, chỉ thôi lọc phím: nút app / Input / Apps trên remote không còn do Home sạch lo. Nút gán ở màn Nút
     * remote cần lọc phím nên vẫn thắng (PhimRieng.apVao bỏ key_filter=false khi còn nút gán).
     */
    var giuAmLuong by mutableStateOf(false)
    /** Chọn giao diện: công tắc ô icon đều nhau theo giao diện mới, người dùng vẫn đổi lại được sau đó (CleanTV 3.0.0). */
    fun chonTheme(th: vn.tivikit.dulieu.Theme) { themeChon = th; oDeuNhau = th.cauHinh["uniform_tiles"] == true }
    /** Số app mỗi hàng (columns của launcher, 0 = theo theme), góp ý 24/10/2026: Sony chỉ có 4 app một hàng. */
    var soCot by mutableStateOf(0)
    /** Cho chọn app tự mở khi bật tivi (open_on_start_choice của launcher; CleanTV 2.10.2): chọn app ngay trên tivi. */
    var moKhiBat by mutableStateOf(false)
    /** Chữ giờ / ngày trắng sáng hẳn (header_white của launcher; CleanTV 2.10.12). */
    var chuTrang by mutableStateOf(false)
    /** Mở app đã chọn cả khi bật tivi từ chế độ chờ, không chỉ sau khởi động lại (wake_open_app của bản vá launcher, cần
     *  HomeKeyService; CleanTV 2.10.12). */
    var moKhiThuc by mutableStateOf(false)
    /** Bật tivi luôn về Home sạch (power_on_home_always của launcher 3.0, cần HomeKeyService; CleanTV 3.0.0). */
    var luonVeHome by mutableStateOf(false)
    /** HomeKeyService cần bật: phím Home lạ, phím app remote, nút gán riêng, mở app khi bật từ chế độ chờ, luôn về Home. */
    val canDichVuPhim get() = nutHome || dungPhimRemote || phimRieng.isNotEmpty() || (moKhiBat && moKhiThuc) || luonVeHome

    /**
     * Ô kênh VTVgo kiểu cũ ở đầu dock (ô link của Home sạch, KenhVtvGo): (tên trên ô, mã kênh), đọc từ tivi để Áp dụng Home
     * sạch giữ nguyên. Ô kênh VTVgo mới là ô Kênh nhanh (app phụ, KenhNhanhTivi), màn đó chuyển được ô kiểu cũ sang.
     */
    val kenhChon = mutableStateListOf<Pair<String, String>>()
    /** Danh sách kênh của VTVgo (tải khi mở hộp chọn kênh của Kênh nhanh); rỗng = chưa tải. */
    var dsKenhGo by mutableStateOf<List<vn.tivikit.dulieu.KenhVtvGo.Kenh>>(emptyList())
    var moChonKenh by mutableStateOf(false)
    var dangTaiKenh by mutableStateOf(false)
    val coVtvGo get() = trangThai?.caiDat?.contains(vn.tivikit.dulieu.KenhVtvGo.GOI) == true

    fun moHopKenh() {
        moChonKenh = true
        if (dsKenhGo.isNotEmpty() || dangTaiKenh) return
        dsKenhGo = vn.tivikit.dulieu.KenhVtvGo.docNho(ctx)
        dangTaiKenh = true
        viewModelScope.launch {
            val moi = io { vn.tivikit.dulieu.KenhVtvGo.tai(ctx) }
            if (moi.isNotEmpty()) dsKenhGo = moi
            dangTaiKenh = false
        }
    }
    /**
     * Nút app / Cài đặt trên remote (24/10/2026, góp ý TCL G09): tắt Home gốc của TCL là nút YouTube, Netflix… chết vì
     * hãng chỉ gửi chúng cho launcherx. null = tự chọn: bật khi mẫu có bảng phím, trừ mẫu trong PHIM_TAT_SAN.
     */
    var nutAppRemoteChon by mutableStateOf<Boolean?>(null)
    val nutAppRemote get() = nutAppRemoteChon ?: (mauChon?.id !in PHIM_TAT_SAN && !laTclGoogleTv)

    /**
     * TCL Google TV (có Home Google launcherx): dịch vụ trợ năng nào lọc phím cũng làm giữ nút âm lượng chỉ nhảy một nấc
     * (upstream docs/devices/tcl-55p71b-g09-key-repeat.md; góp ý nda2192 24/10/2026). Mặc định ưu tiên âm lượng: tắt nút
     * app remote và ghi key_filter=false — dịch vụ vẫn bật để mở đè Home (theo cửa sổ, không cần lọc phím).
     */
    // Chỉ khi launcherx CÒN BẬT (đường mở đè): nút app remote tự chạy qua nó, thôi lọc phím không mất gì. Đã tắt
    // launcherx thì nút app chỉ chạy nhờ app_keys → giữ như 2.6.7 (heavensky95, TCL G10 đã tắt launcherx).
    val laTclGoogleTv get() = thongTin?.brand?.equals("TCL", ignoreCase = true) == true &&
        trangThai?.caiDat?.contains("com.google.android.apps.tv.launcherx") == true &&
        trangThai?.daTat?.contains("com.google.android.apps.tv.launcherx") != true

    /** Bảng phím remote dùng cho tivi này: của mẫu đang chọn, không có thì bảng chung của TCL; null nếu không có. */
    fun phimRemote(): JSONObject? = mauChon?.phim
        ?: if (thongTin?.brand?.equals("TCL", ignoreCase = true) == true) JSONObject(PHIM_TCL_CHUNG) else null

    /** Có ghép bảng phím vào cấu hình lần này không (cần HomeKeyService). */
    val dungPhimRemote get() = nutAppRemote && phimRemote() != null
    // Tuỳ chỉnh thêm (1.7, các "switch" của launcher — upstream themes/README.md).
    var amLich by mutableStateOf(true)
    var tenDuoiApp by mutableStateOf(false)
    /** Tên app to phía trên dock (dock_title, launcher mặc định bật); góp ý 22/10/2026 muốn tắt cho đỡ rối. */
    var tenToTrenDock by mutableStateOf(true)
    /** Ô app điện thoại (không có ảnh tivi) tô màu theo icon (launcher icon_tile_color, 23/10/2026). */
    var toMauOApp by mutableStateOf(true)
    var thuVienApp by mutableStateOf(false)
    var dongHoKhiYen by mutableStateOf(0)
    var tiviYeu by mutableStateOf(false)
    var hieuUngKinh by mutableStateOf(false)
    var lamToiNen by mutableStateOf(0)
    var hienThongSo by mutableStateOf(false)
    /** Mục Nâng cao: danh sách tuỳ chọn + giá trị người dùng đặt (khoá → chữ; thiếu = mặc định). */
    val dsNangCao: List<vn.tivikit.dulieu.TuyChon> by lazy { vn.tivikit.dulieu.NangCao.nap(ctx) }
    val nangCao = androidx.compose.runtime.mutableStateMapOf<String, String>()
    var homeXong by mutableStateOf(false)
    /** Lần thử nút Home thật gần nhất ra Home sạch (thuPhimHome). */
    var phimHomeOk by mutableStateOf(true)
    /** Đổi giao diện lúc nút Home đang mở launcher khác người dùng chọn (gói của nó): Home không bị đổi. */
    var homeKhac by mutableStateOf<String?>(null)
    /** Kết quả bấm thử nút Home lần gần nhất, gửi kèm danh sách gói để chẩn đoán. */
    var phimHomeGhi = ""
    var themeChon by mutableStateOf(vn.tivikit.dulieu.THEMES.first())

    // Máy chiếu: màn của hãng dò được + những màn đã ghim vào nút "Máy chiếu" trên Home sạch (comp → nhãn).
    var laMayChieu by mutableStateOf(false)
    var mayChieu by mutableStateOf<List<String>?>(null)
    val ghimMayChieu = mutableStateMapOf<String, String>()
    var dangDoMayChieu by mutableStateOf(false)

    fun doMayChieu() {
        if (dangDoMayChieu) return
        dangDoMayChieu = true; loi = ""
        viewModelScope.launch {
            try {
                val ds = io { tv.doMayChieu() }
                mayChieu = ds
                if (ds.isEmpty()) loi = t("Không thấy màn lấy nét / chỉnh hình nào của hãng trên máy này.",
                    "No focus / keystone screens found on this device.")
            } catch (e: Throwable) { loi = moTaLoi(e) } finally { dangDoMayChieu = false }
        }
    }

    fun moThuMayChieu(comp: String) = chay(t("Đang mở trên máy chiếu…", "Opening on the projector…")) {
        io { tv.moThu(comp) }?.let { error(t("Máy chiếu không mở được màn này: $it", "The projector refused this screen: $it")) }
    }

    fun doiGhim(comp: String) {
        if (comp in ghimMayChieu) ghimMayChieu.remove(comp) else if (ghimMayChieu.size < 6) ghimMayChieu[comp] = nhanMayChieu(comp)
    }

    /** Đoán nhãn theo tên màn: focus → Lấy nét, keystone/trapez… → Chỉnh hình, zoom/scale → Thu phóng. */
    fun nhanMayChieu(comp: String): String {
        val c = comp.substringAfter('/').lowercase()
        return when {
            Regex("focus|duijiao").containsMatchIn(c) -> t("Lấy nét", "Focus")
            Regex("keystone|trapez|tixing|jiaozheng|correct|geometr|ladder").containsMatchIn(c) -> t("Chỉnh hình", "Keystone")
            Regex("zoom|scale|size").containsMatchIn(c) -> t("Thu phóng", "Zoom")
            Regex("calibr").containsMatchIn(c) -> t("Hiệu chỉnh", "Calibrate")
            else -> c.substringAfterLast('.').removeSuffix("activity").replaceFirstChar { it.uppercase() }
        }
    }

    /** Danh sách màn dò được, để dán vào hộp góp ý vgs.zone. */
    fun chuDanhSachMayChieu(): String = buildString {
        thongTin?.let { append("${it.tenHienThi} · ${it.device} · Android ${it.android}\n") }
        (mayChieu ?: emptyList()).forEach { append(it).append('\n') }
    }
    var thanhPho by mutableStateOf<vn.tivikit.dulieu.ThanhPho?>(null)
    /** Ô tìm phường/xã cho thời tiết (góp ý 23/10/2026). */
    var timXa by mutableStateOf("")
        private set
    /**
     * Kết quả tìm phường/xã: lọc 3.321 xã sau khi ngừng gõ 0,35 giây, ở luồng phụ (CleanTV 2.10.2: chạy ngay trên tivi,
     * lọc lại mỗi lần vẽ màn hình làm ô gõ giật). Ít kết quả trong nước thì tìm thêm thành phố khắp thế giới (2.10.7).
     */
    var ketQuaXa by mutableStateOf<List<vn.tivikit.dulieu.XaPhuong.Xa>>(emptyList())
        private set
    private var viecTimXa: kotlinx.coroutines.Job? = null

    fun doiTimXa(q: String) {
        timXa = q
        viecTimXa?.cancel()
        if (q.trim().length < 2) { ketQuaXa = emptyList(); return }
        viecTimXa = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            val trongNuoc = withContext(Dispatchers.Default) { vn.tivikit.dulieu.XaPhuong.timXa(vn.tivikit.dulieu.XaPhuong.nap(ctx), q) }
            ketQuaXa = trongNuoc
            if (trongNuoc.size < 3 && q.trim().length >= 3) {
                val theGioi = withContext(Dispatchers.IO) { vn.tivikit.dulieu.XaPhuong.timTheGioi(q, vn.tivikit.NgonNgu.tiengViet) }
                if (timXa == q) ketQuaXa = trongNuoc + theGioi.take(8 - trongNuoc.size)
            }
        }
    }
    fun chonXa(x: vn.tivikit.dulieu.XaPhuong.Xa) { thanhPho = vn.tivikit.dulieu.ThanhPho(x.tenNgan, x.lat, x.lon); doiTimXa("") }

    /** Mở thẳng màn chọn app "Mở khi khởi động" trên tivi (CleanTV 2.10.4: không tìm thấy chỗ chọn trên Xiaomi). */
    var baoChonApp by mutableStateOf("")
    fun moChonAppKhiBat() = chay(t("Đang mở trên tivi…", "Opening on the TV…")) {
        io { tv.moCaiDatLauncher() }
        baoChonApp = t("Đã mở Cài đặt launcher trên tivi: chọn \"Mở khi khởi động\" rồi chọn app.", "Launcher settings are open on the TV: pick \"Open on start\", then the app.")
    }

    /** Tivi đã có Home sạch: chỉ cần đẩy theme, không cài lại. */
    /** Tên gói Home sạch trên tivi đang nối (Xiaomi bản Trung Quốc: com.ts.launcher), dò lúc kết nối. */
    private var goiHome = ThaoTacTv.PKG_HOME
    val daCoHomeSach: Boolean get() = trangThai?.let { tt -> goiHome in tt.caiDat && goiHome !in tt.daTat } == true

    var dsSaoLuu by mutableStateOf<List<BanSaoLuu>>(emptyList())

    var banMoi by mutableStateOf<vn.tivikit.CapNhat.BanMoi?>(null)
    var dangCapNhat by mutableStateOf(false)
    /** Hộp mời nâng cấp: hiện một lần cho mỗi bản mới lúc mở app. */
    var hoiCapNhat by mutableStateOf(false)
    /** Hộp "Cập nhật TiviKit" ở màn chính (26/10/2026): bản đang dùng, công tắc tự cài, nút kiểm tra ngay. */
    var moCapNhat by mutableStateOf(false)
    var tuCai by mutableStateOf(vn.tivikit.CapNhat.tuCai(ctx))
        private set
    var dangKiemTraBan by mutableStateOf(false)
        private set
    var baoKiemTra by mutableStateOf("")
        private set

    init {
        vn.tivikit.dulieu.TenTrenTv.nap(ctx)
        if (vn.tivikit.BuildConfig.TU_CAP_NHAT) viewModelScope.launch {
            val b = runCatching { io { vn.tivikit.CapNhat.kiemTra(ctx) } }.getOrNull()
            banMoi = b
            if (b != null) xuLyBanMoi(b)
        }
        // Chạm thông báo khi app đang mở sẵn: kiểm tra lại cờ mỗi khi quay về.
        if (vn.tivikit.BuildConfig.TU_CAP_NHAT) viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1500)
                if (!vn.tivikit.CapNhat.moTuThongBao) continue
                // Lúc mở app có thể đang mất mạng nên chưa biết bản mới → hỏi lại máy chủ.
                val b = banMoi ?: runCatching { io { vn.tivikit.CapNhat.kiemTraApp() } }.getOrNull().also { banMoi = it }
                if (b != null) xuLyBanMoi(b) else vn.tivikit.CapNhat.moTuThongBao = false
            }
        }
        ip = prefs.getString("ip", "") ?: ""
        cong = prefs.getString("cong", "5555") ?: "5555"
        taiLaiSaoLuu()
    }

    // ---------- Màn chính kiểu trung tâm (25/10/2026) ----------
    // Màn chính là nơi chọn chức năng; kết nối một lần dùng cho mọi chức năng. Luồng dọn (Kết nối · Chọn · Dọn · Home)
    // chỉ là một chức năng trong đó: thanh bước chỉ hiện khi dangDon.

    /** Đang trong luồng dọn tivi (hiện thanh 4 bước, kết nối xong vào màn chọn mẫu). */
    var dangDon by mutableStateOf(false)
    /** Chức năng bấm lúc chưa kết nối: kết nối xong thì mở. */
    private var sauKetNoi: (() -> Unit)? = null

    data class TiviGanDay(val ip: String, val cong: String, val ten: String, val luc: Long)
    /**
     * Tivi kết nối gần đây (kể cả chỉ để cài app), nối lại một chạm từ màn chính. Nạp ngay ở đây, không trong init:
     * init nằm phía trên trong file nên chạy trước khi biến này được tạo.
     */
    var tiviGanDay by mutableStateOf(napTiviGanDay())
        private set

    private fun napTiviGanDay(): List<TiviGanDay> = runCatching {
        val a = JSONArray(prefs.getString("tivi_gan_day", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> TiviGanDay(o.getString("ip"), o.optString("cong", "5555"), o.optString("ten"), o.optLong("luc")) } }
    }.getOrDefault(emptyList())

    private fun nhoTiviGanDay(ip: String, cong: String, ten: String) {
        luuTiviGanDay(listOf(TiviGanDay(ip, cong, ten, System.currentTimeMillis())) + tiviGanDay.filter { it.ip != ip })
    }

    private fun luuTiviGanDay(ds: List<TiviGanDay>) {
        tiviGanDay = ds.take(5)
        prefs.edit().putString("tivi_gan_day", JSONArray(tiviGanDay.map {
            JSONObject().put("ip", it.ip).put("cong", it.cong).put("ten", it.ten).put("luc", it.luc)
        }).toString()).apply()
    }

    /** Cài đặt đưa vào file sao lưu (màn Sao lưu & chuyển máy): tivi gần đây. */
    internal fun caiDatChoSaoLuu(): JSONObject = JSONObject().put("tivi_gan_day", JSONArray(prefs.getString("tivi_gan_day", "[]")))

    /** Gộp tivi gần đây từ file sao lưu: cùng IP giữ lần mới hơn. */
    internal fun gopTiviGanDay(a: JSONArray) {
        val nhap = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            o.optString("ip").takeIf { it.isNotBlank() }?.let { TiviGanDay(it, o.optString("cong", "5555"), o.optString("ten"), o.optLong("luc")) }
        }
        luuTiviGanDay((tiviGanDay + nhap).groupBy { it.ip }.map { (_, ds) -> ds.maxBy { it.luc } }.sortedByDescending { it.luc })
    }

    /** Ô chức năng ở màn chính: đang nối tivi thì mở luôn, chưa thì kết nối trước rồi mở. */
    fun moChucNang(viec: () -> Unit) {
        if (thongTin != null) { dangDon = false; viec() }
        else { sauKetNoi = viec; dangDon = false; dangKhoiPhuc = false; man = Man.KET_NOI }
    }

    /** Nút Kết nối / Đổi tivi ở màn chính: kết nối xong về lại màn chính. */
    fun moKetNoi() { sauKetNoi = null; dangDon = false; dangKhoiPhuc = false; man = Man.KET_NOI }

    fun noiNhanh(g: TiviGanDay) { ip = g.ip; cong = g.cong; sauKetNoi = null; dangDon = false; dangKhoiPhuc = false; ketNoi(timLaiNeuIm = true) }

    /** Tên tivi gần đây ở địa chỉ [dc] (hiện cạnh tivi quét thấy). */
    fun tenGanDay(dc: String): String? = tiviGanDay.firstOrNull { it.ip == dc && it.cong == "5555" }?.ten?.takeIf { it.isNotBlank() }

    /** Ô "Dọn quảng cáo": đang nối thì đọc lại trạng thái tivi (có thể đã dọn trước đó) rồi vào màn chọn mẫu. */
    fun batDauDon() {
        dangDon = true; sauKetNoi = null; dangKhoiPhuc = false
        if (thongTin == null) { man = Man.KET_NOI; return }
        chay(t("Đang đọc tivi…", "Reading the TV…")) {
            damBaoKetNoi()
            muc = emptyList(); chon.clear(); ketQua = null; nhatKy.clear(); homeXong = false; epSetupwraith = false
            trangThai = io { tv.docTrangThai() }
            man = Man.THONG_TIN
        }
    }

    /** Về màn chính, giữ kết nối. */
    fun veHub() { dangDon = false; dangKhoiPhuc = false; sauKetNoi = null; man = Man.CHINH }

    private fun xuLyBanMoi(b: vn.tivikit.CapNhat.BanMoi) {
        val p = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        if (vn.tivikit.CapNhat.moTuThongBao) {
            vn.tivikit.CapNhat.moTuThongBao = false
            hoiCapNhat = false
            capNhatApp()
        } else if (p.getInt("da_hoi", 0) < b.versionCode) {
            p.edit().putInt("da_hoi", b.versionCode).putInt("da_bao", maxOf(p.getInt("da_bao", 0), b.versionCode)).apply()
            hoiCapNhat = true
        }
    }

    /**
     * Tải bản mới, kiểm tra cỡ + SHA-256, cài. Android 12+ cài luôn không hỏi: app đóng lại thành bản mới (báo trước bằng
     * Toast kẻo tưởng app tắt ngang); máy cũ hơn hiện hộp cài của Android.
     */
    fun capNhatApp() {
        val b = banMoi ?: return
        if (dangCapNhat) return
        dangCapNhat = true
        viewModelScope.launch {
            try {
                val f = io { vn.tivikit.CapNhat.taiVe(ctx, b) }
                if (android.os.Build.VERSION.SDK_INT >= 31) android.widget.Toast.makeText(ctx,
                    t("Đang cài TiviKit ${b.versionName}: app sẽ tự đóng, mở lại là bản mới.", "Installing TiviKit ${b.versionName}: the app will close; reopen it for the new version."),
                    android.widget.Toast.LENGTH_LONG).show()
                io { vn.tivikit.CapNhat.cai(ctx, b, f, ngam = false) }
            } catch (e: Throwable) { loi = t("Cập nhật không được: ${e.message}", "Couldn't update: ${e.message}") }
            finally { dangCapNhat = false }
        }
    }

    fun datTuCai(bat: Boolean) { vn.tivikit.CapNhat.datTuCai(ctx, bat); tuCai = bat }

    /** Nút Kiểm tra ngay trong hộp Cập nhật TiviKit. */
    fun kiemTraBanMoi() {
        if (dangKiemTraBan) return
        dangKiemTraBan = true; baoKiemTra = ""
        viewModelScope.launch {
            try {
                val b = io { vn.tivikit.CapNhat.kiemTraApp() }
                banMoi = b
                baoKiemTra = if (b == null) t("Đang dùng bản mới nhất (${vn.tivikit.BuildConfig.VERSION_NAME}).", "You're on the latest version (${vn.tivikit.BuildConfig.VERSION_NAME}).")
                    else t("Có bản mới ${b.versionName}.", "Version ${b.versionName} is available.")
            } catch (e: Throwable) {
                baoKiemTra = t("Không kiểm tra được (mạng hoặc GitHub): ${e.message}", "Couldn't check (network or GitHub): ${e.message}")
            } finally { dangKiemTraBan = false }
        }
    }

    fun taiLaiSaoLuu() { dsSaoLuu = kho.tatCa() }

    internal fun chay(viec: String, khoi: suspend () -> Unit) {
        if (dangBan) return
        dangBan = true; loi = ""; thongBao = viec
        viewModelScope.launch {
            try { khoi() } catch (e: Throwable) {
                loi = moTaLoi(e)
            } finally { dangBan = false; thongBao = "" }
        }
    }

    private fun laLoiTuChoi(e: Throwable) = listOfNotNull(e.message, e.cause?.message).any { it.contains("ECONNREFUSED") || it.contains("refused") }

    private fun moTaLoi(e: Throwable): String = when {
        e is ThaoTacTv.TiviKhoiDongLaiException ->
            t("Tivi vừa tự khởi động lại ngay sau khi tắt ${e.goi}. App đã dừng dọn để an toàn (${e.daTat} gói đã tắt, đều có trong sao lưu). Chờ tivi lên lại, kết nối lại: tivi chạy bình thường thì dọn tiếp và bỏ tích ${e.goi}; có gì lạ thì bấm Khôi phục tivi này. Báo model tivi và gói ${e.goi} cho CleanYourTV (github.com/Gathin23/cleanyourtv) để gói này được ghi vào danh sách giữ.",
                "The TV restarted right after ${e.goi} was disabled. The app stopped cleaning to be safe (${e.daTat} packages disabled, all in the backup). Wait for the TV, reconnect: if it works, clean again without ${e.goi}; if anything is wrong, press Restore this TV. Report the TV model and ${e.goi} to CleanYourTV (github.com/Gathin23/cleanyourtv) so the package is marked as keep.")
        e is ThaoTacTv.MatKetNoiTvException ->
            t("Mất kết nối với tivi giữa lúc dọn và không nối lại được (${e.daTat} gói đã tắt, đều có trong sao lưu). Kiểm tra tivi còn bật và cùng Wi-Fi, rồi kết nối lại để dọn tiếp hoặc bấm Khôi phục tivi này. Không nối được: tắt tivi ở công tắc nguồn 10 giây rồi bật lại.",
                "Lost the connection to the TV while cleaning and couldn't reconnect (${e.daTat} packages disabled, all in the backup). Check the TV is on and on the same Wi-Fi, then reconnect to continue or press Restore this TV. Still nothing: switch the TV off at the wall for 10 seconds.")
        ThaoTacTv.laMatKetNoi(e) && !laLoiTuChoi(e) ->
            t("Mất kết nối với tivi. Những gì đã làm đều có trong sao lưu: kết nối lại để làm tiếp hoặc bấm Khôi phục tivi này.",
                "Lost the connection to the TV. Everything done so far is in the backup: reconnect to continue or press Restore this TV.")
        e is AdbPairingRequiredException -> t("Tivi đòi ghép đôi (Gỡ lỗi không dây). Dùng phần \"Ghép đôi bằng mã\" bên dưới.", "The TV needs pairing (Wireless debugging). Use \"Pair with code\" below.")
        // Chế độ chạy trên tivi: nhiều tivi (TCL…) bật Gỡ lỗi USB chỉ mở ADB qua dây, cổng 5555 chưa nghe (22/10/2026).
        laLoiTuChoi(e) && ip.trim() == "127.0.0.1" ->
            t("Tivi chưa mở ADB qua mạng (cổng 5555), nên app trên tivi chưa tự nối vào được. Cách sửa: trong Tuỳ chọn nhà phát triển bật thêm \"Gỡ lỗi qua mạng\" / \"ADB qua mạng\" (nếu có) rồi Kết nối lại. Tivi chỉ có \"Gỡ lỗi không dây\" thì dùng TiviKit trên điện thoại, chọn Ghép bằng mã. Hoặc cắm tivi vào máy tính, chạy adb tcpip 5555 một lần.",
                "The TV hasn't opened ADB over the network (port 5555), so the app on the TV can't connect to itself. Fix: in Developer options also turn on \"Network debugging\" / \"ADB over network\" (if there) and Connect again. If the TV only has \"Wireless debugging\", use TiviKit on a phone with Pair with code. Or plug the TV into a computer and run adb tcpip 5555 once.")
        laLoiTuChoi(e) ->
            t("Tivi từ chối kết nối ở cổng này. Kiểm tra đã bật Gỡ lỗi USB / Gỡ lỗi qua mạng chưa.", "The TV refused the connection on this port. Check USB debugging / Network debugging is on.")
        e.message?.contains("EHOSTUNREACH") == true || e.message?.contains("No route") == true ->
            t("Không tới được tivi. Điện thoại và tivi phải chung một mạng Wi-Fi.", "Cannot reach the TV. The phone and TV must be on the same Wi-Fi.")
        e is java.net.SocketTimeoutException || e.message?.contains("timed out") == true ->
            t("Hết thời gian chờ. Có thể tivi đang hiện hộp \"Cho phép gỡ lỗi?\" — bấm Cho phép rồi thử lại.", "Timed out. The TV may be showing \"Allow debugging?\" — press Allow and try again.")
        else -> listOfNotNull(e.message, e.cause?.message).firstOrNull()
            ?: t("Lỗi kết nối (${e.javaClass.simpleName}). Kiểm tra IP, cổng và tivi còn bật.", "Connection error (${e.javaClass.simpleName}). Check the IP, port and that the TV is on.")
    }

    internal suspend fun <T> io(khoi: () -> T): T = withContext(Dispatchers.IO) { khoi() }

    // ---------- Kết nối ----------

    /**
     * Quét cả mạng /24 của điện thoại tìm máy mở cổng 5555. Wi-Fi tivi đang ngủ trả lời chậm (xem DoMang), nên: tivi gần
     * đây dò riêng, chờ lâu, hai lần; cả mạng quét lượt nhanh (lời hỏi ARP của lượt này cũng đánh thức tivi) rồi quét lại
     * chờ lâu những địa chỉ chưa trả lời, nếu chưa thấy tivi nào hoặc còn tivi gần đây chưa thấy. Không thấy thì báo tivi
     * gần đây đang thế nào: cổng đóng (ADB tắt) hay im (Wi-Fi ngủ, đổi IP).
     */
    fun quetMang() {
        if (dangQuet) return
        dangQuet = true; timThay.clear(); loi = ""
        viewModelScope.launch {
            try {
                val mang = io { mangNoiBo() }
                val goc = ipDienThoai(mang) ?: run { loi = t("Điện thoại chưa vào Wi-Fi.", "The phone is not on Wi-Fi."); return@launch }
                val dau = goc.substringBeforeLast('.')
                val luong = Dispatchers.IO.limitedParallelism(128)
                suspend fun thay(dc: String) = withContext(Dispatchers.Main) { if (dc !in timThay) timThay += dc }
                val ganDay = tiviGanDay.filter { it.cong == "5555" && it.ip.startsWith("$dau.") && it.ip != goc }.distinctBy { it.ip }
                val doGanDay = ganDay.map { g -> async(luong) { g to DoMang.doLau(g.ip, 5555, mang).also { if (it == DoMang.Cong.MO) thay(g.ip) } } }
                val chuaTraLoi = java.util.Collections.synchronizedSet(mutableSetOf<String>())
                suspend fun luot(ds: Collection<String>, ms: Int) = ds.map { dc ->
                    async(luong) {
                        when (DoMang.doCong(dc, 5555, ms, mang)) {
                            DoMang.Cong.MO -> { chuaTraLoi.remove(dc); thay(dc) }
                            DoMang.Cong.DONG -> chuaTraLoi.remove(dc)
                            DoMang.Cong.IM -> chuaTraLoi.add(dc)
                        }
                    }
                }.awaitAll()
                luot((1..254).map { "$dau.$it" }.filter { it != goc }, 700)
                val kqGanDay = doGanDay.awaitAll()
                if (timThay.isEmpty() || ganDay.any { it.ip !in timThay }) luot(chuaTraLoi.toList(), 2000)
                if (timThay.isEmpty()) loi = chanDoanKhongThay(kqGanDay)
            } finally { dangQuet = false }
        }
    }

    /** Lời báo khi quét không thấy tivi nào, theo tình trạng tivi gần đây (nếu có). */
    private fun chanDoanKhongThay(kq: List<Pair<TiviGanDay, DoMang.Cong>>): String {
        fun ten(g: TiviGanDay) = g.ten.ifBlank { t("Tivi", "The TV") } + " (${g.ip})"
        kq.firstOrNull { it.second == DoMang.Cong.DONG }?.let { (g, _) ->
            return t("${ten(g)} vẫn có trên mạng nhưng cổng 5555 đóng: ADB qua mạng trên tivi đã tắt (thường sau khi tivi khởi động lại hoặc rút điện). Vào Tuỳ chọn nhà phát triển trên tivi, bật lại Gỡ lỗi USB / Gỡ lỗi qua mạng rồi quét lại.",
                "${ten(g)} is on the network but port 5555 is closed: network ADB on the TV is off (usually after a reboot or power cut). In Developer options on the TV turn USB / Network debugging back on, then scan again.")
        }
        kq.firstOrNull()?.let { (g, _) ->
            return t("Không thấy ${ten(g)}: Wi-Fi của tivi có thể đang ngủ sâu, hoặc tivi đã đổi IP. Thử mở YouTube trên tivi vài giây rồi quét lại; vẫn không thấy thì tắt bật Wi-Fi trên tivi. Hay bị vậy: đặt IP cố định cho tivi trong modem (DHCP tĩnh), hoặc cắm dây mạng cho tivi — dây mạng không ngủ như Wi-Fi.",
                "${ten(g)} not found: the TV's Wi-Fi may be in deep sleep, or the TV got a new IP. Open YouTube on the TV for a few seconds and scan again; still nothing, turn the TV's Wi-Fi off and on. If it keeps happening: give the TV a fixed IP in the router (DHCP reservation), or use a network cable — cables don't sleep like Wi-Fi.")
        }
        return t("Không thấy tivi nào mở cổng 5555. Google TV đời mới thường chỉ có Gỡ lỗi không dây: dùng phần ghép đôi.", "No TV with port 5555 open. Newer Google TVs often only offer Wireless debugging: use pairing.")
    }

    /** Các cổng TCP đang mở trên [host] trong [dai], quét song song (256 luồng, chờ 300 ms mỗi cổng). */
    private suspend fun quetCongMo(host: String, dai: IntRange, boQua: Set<Int> = emptySet()): List<Int> = withContext(Dispatchers.IO) {
        val sem = Semaphore(256)
        val mang = mangNoiBo()
        dai.filter { it !in boQua }.map { c -> async { sem.withPermit { if (DoMang.doCong(host, c, 300, mang) == DoMang.Cong.MO) c else null } } }.awaitAll().filterNotNull()
    }

    /**
     * Mạng Wi-Fi (hoặc dây LAN) của điện thoại. Wi-Fi báo "không có Internet" (modem mất mạng ngoài) thì Android lấy 4G
     * làm mạng mặc định: socket không buộc mạng đi 4G và không bao giờ tới tivi.
     */
    @Suppress("DEPRECATION") // allNetworks: chỉ đọc danh sách, không có bản thay thế gọn hơn
    private fun mangNoiBo(): Network? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        fun noiBo(n: Network) = cm.getNetworkCapabilities(n)?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } == true
        return cm.activeNetwork?.takeIf { noiBo(it) } ?: cm.allNetworks.firstOrNull { noiBo(it) }
    }

    /**
     * Mạng mặc định không phải Wi-Fi / dây LAN (Wi-Fi "không có Internet") thì buộc cả app đi mạng Wi-Fi để kết nối ADB
     * (libadb tự mở socket, không buộc riêng được); Wi-Fi là mặc định thì bỏ buộc. Trả mạng Wi-Fi.
     */
    private fun buocMangNoiBo(): Network? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val mang = mangNoiBo()
        runCatching { cm.bindProcessToNetwork(if (mang != null && mang != cm.activeNetwork) mang else null) }
        return mang
    }

    private fun ipDienThoai(mang: Network? = mangNoiBo()): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val lp: LinkProperties = cm.getLinkProperties(mang ?: cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
    }

    private fun moCong(host: String, port: Int, ms: Int): Boolean = DoMang.doCong(host, port, ms, mangNoiBo()) == DoMang.Cong.MO

    /** IPv4 của điện thoại trong mạng Wi-Fi (quét tìm tivi LG). */
    internal fun ipWifi(): String? = ipDienThoai()

    /** Mạng để dò [host]: Wi-Fi của điện thoại, trừ khi là chính máy này (TiviKit chạy trên tivi). */
    internal fun mangDo(host: String): Network? = if (host == "127.0.0.1") null else mangNoiBo()

    /**
     * Kết nối tivi ở [ip]:[cong]. Dò cổng trước, chờ lâu (tivi Wi-Fi đang ngủ trả lời chậm; libadb nối thẳng vào máy không
     * trả lời thì treo cả phút): cổng đóng / không trả lời thì báo rõ. [timLaiNeuIm]: nút Nối lại của tivi gần đây —
     * không trả lời ở IP cũ thì sang màn kết nối quét tìm lại (tivi có thể đã đổi IP).
     */
    fun ketNoi(timLaiNeuIm: Boolean = false) = chay(t("Đang kết nối… Nếu tivi hiện \"Cho phép gỡ lỗi USB?\" hãy tích \"Luôn cho phép\" rồi bấm Cho phép.", "Connecting… If the TV shows \"Allow USB debugging?\" tick \"Always allow\" and press Allow.")) {
        val host = ip.trim(); val port = cong.trim().toIntOrNull() ?: 5555
        daTuTatAdb = false; daTatAdb = false
        if (host.isEmpty()) error(t("Nhập địa chỉ IP của tivi.", "Enter the TV's IP address."))
        if (host != "127.0.0.1") when (io { DoMang.doLau(host, port, buocMangNoiBo()) }) {
            DoMang.Cong.MO -> {}
            DoMang.Cong.DONG -> error(if (port == 5555)
                t("Tivi ở $host có trên mạng nhưng cổng 5555 đóng: ADB qua mạng trên tivi đang tắt (thường sau khi tivi khởi động lại hoặc rút điện). Vào Tuỳ chọn nhà phát triển trên tivi, bật lại Gỡ lỗi USB / Gỡ lỗi qua mạng.",
                    "The TV at $host is on the network but port 5555 is closed: network ADB is off (usually after a reboot or power cut). In Developer options on the TV turn USB / Network debugging back on.")
            else t("Tivi ở $host có trên mạng nhưng cổng $port đã đóng. Gỡ lỗi không dây đổi cổng sau mỗi lần tivi khởi động: xem cổng mới trên màn Gỡ lỗi không dây của tivi rồi nhập lại.",
                    "The TV at $host is on the network but port $port is closed. Wireless debugging changes its port on every boot: check the new port on the TV's Wireless debugging screen and enter it."))
            DoMang.Cong.IM -> if (timLaiNeuIm && port == 5555) { man = Man.KET_NOI; quetMang(); return@chay }
                else error(t("Không thấy tivi ở $host. Kiểm tra tivi còn bật và cùng Wi-Fi với điện thoại; tivi có thể đã đổi IP (bấm Quét mạng tìm tivi).",
                    "No answer from $host. Check the TV is on and on the same Wi-Fi as the phone; it may have a new IP (tap Scan the network)."))
        }
        io {
            runCatching { adb.disconnect() }
            if (!adb.connect(host, port)) error(t("Kết nối không thành công. Thử lại và để ý hộp xác nhận trên tivi.", "Connection failed. Try again and watch for the prompt on the TV."))
        }
        prefs.edit().putString("ip", host).putString("cong", port.toString()).apply()
        sauKhiKetNoi(host)
    }

    /** Chế độ tivi: kết nối ADB vào chính máy này. */
    fun ketNoiTiviNay() { ip = "127.0.0.1"; cong = "5555"; ketNoi() }

    /** Chế độ tivi: các bước bấm Home / khởi động lại Home đẩy app xuống nền — mở lại app cho người dùng thấy kết quả. */
    internal suspend fun veApp() {
        if (vn.tivikit.ManHinh.laTv) io { runCatching { adb.shell("am start -n ${ctx.packageName}/vn.tivikit.MainActivity") } }
    }

    /** Ghép đôi Gỡ lỗi không dây (Android 11+), rồi tìm cổng kết nối qua mDNS. */
    fun ghepDoi() = chay(t("Đang ghép đôi…", "Pairing…")) {
        val (h, p) = tachIpCong(ghepIpCong) ?: error(t("Nhập dạng 192.168.1.20:37123 (IP và cổng trong hộp ghép đôi).", "Enter it as 192.168.1.20:37123 (IP and port from the pairing dialog)."))
        val ma = ghepMa.trim()
        if (ma.length != 6) error(t("Mã ghép đôi có 6 số.", "The pairing code has 6 digits."))
        io { if (!adb.pair(h, p, ma)) error(t("Ghép đôi không thành công, mã có thể đã đổi. Mở lại hộp ghép đôi trên tivi.", "Pairing failed, the code may have changed. Reopen the pairing dialog on the TV.")) }
        thongBao = t("Đã ghép đôi. Đang tìm cổng kết nối của tivi…", "Paired. Looking for the TV's connect port…")
        val ok = io {
            runCatching { adb.disconnect() }
            runCatching { adb.connectTls(ctx, 15_000) }.getOrDefault(false)
        }
        // mDNS không thấy (mạng chặn multicast, Chromecast Android 14 — góp ý buivansaobg 24/10/2026): quét cổng
        // ngẫu nhiên của Gỡ lỗi không dây (dải cổng tạm 32768–60999 của Linux) rồi thử kết nối từng cổng mở.
        val ok2 = ok || run {
            thongBao = t("Chưa thấy cổng qua mạng, đang dò cổng của tivi (khoảng 30 giây)…", "Port not announced, probing the TV's ports (about 30 seconds)…")
            val mo = quetCongMo(h, 32768..60999, boQua = setOf(p))
            mo.any { c -> io { runCatching { adb.disconnect() }; runCatching { adb.connect(h, c) }.getOrDefault(false) } }
        }
        if (!ok2) {
            ip = h
            error(t("Đã ghép đôi nhưng chưa tự tìm được cổng. Nhập IP và cổng ở dòng \"Địa chỉ IP & cổng\" ", "Paired, but the port was not found automatically. Enter the IP and port from the \"IP address & Port\" line ") +
                t("trên màn Gỡ lỗi không dây của tivi (không phải cổng ghép đôi) rồi bấm Kết nối.", "on the TV's Wireless debugging screen (not the pairing port) and press Connect."))
        }
        ip = h
        prefs.edit().putString("ip", h).apply()
        sauKhiKetNoi(h)
    }

    private fun tachIpCong(s: String): Pair<String, Int>? {
        val t = s.trim(); val i = t.lastIndexOf(':')
        if (i <= 0) return null
        val p = t.substring(i + 1).toIntOrNull() ?: return null
        return t.substring(0, i) to p
    }

    /**
     * Tên mọi app trên tivi, hỏi PackageManager của tivi qua TrenTv (một lệnh, ~2 giây trên TX3), nhớ vào TenTrenTv. Gói
     * trong [caiDat] mà tivi không báo tên (app đang tắt…) cũng ghi là đã hỏi, để lần nối sau khỏi hỏi lại.
     */
    internal suspend fun docTenTrenTv(caiDat: Set<String> = emptySet()): Map<String, vn.tivikit.adb.TrenTv.TenTv> {
        val m = io { vn.tivikit.adb.TrenTv(adb).tenApp { vn.tivikit.CapNhat.moAsset(ctx, vn.tivikit.adb.TrenTv.ASSET).readBytes() } }
        if (m.isNotEmpty()) vn.tivikit.dulieu.TenTrenTv.them(ctx, (caiDat - m.keys).associateWith { "" } + m.mapValues { it.value.ten })
        return m
    }

    private suspend fun sauKhiKetNoi(host: String) {
        daNapCu = false
        // Nút gán riêng / ô Kênh nhanh thuộc về tivi trước: tivi này đọc lại từ config.json của nó (napTuJson, màn Nút
        // remote, màn Kênh nhanh).
        phimRieng.clear(); ganPhim.quen(); tenKenh.clear(); anhOKenh.clear(); kenhNhanh.quen(); henGio.quen(); lamMoi.quen(); mang.quen(); caiDatNhanh.quen(); capNhatApp.quen()
        dongLenh.quen(); lienKet.quen(); qlLauncher.quen()
        xemManHinh.tat(); kenhDieuKhien.dong(); dangPhat.dong()
        tv.quenTivi()
        thongBao = t("Đang đọc thông tin tivi…", "Reading the TV…")
        val (info, gp) = io { tv.docThongTin() }
        goiHome = io { tv.pkgHome }
        thongTin = info; getprop = gp
        khongPhaiTivi = !io { tv.laTivi() }
        xacNhanLaTivi = false
        // Danh sách cài app thuộc về một tivi: nối lại đúng tivi đó (rớt Wi-Fi giữa lúc gửi) thì giữ để Thử lại.
        abiTvDaDo = null
        if (tiviCuaDsCai != info.khoaLuu) { dsCai.forEach { it.file?.takeIf { f -> f.tam }?.file?.delete() }; dsCai.clear(); apkTrenTv = null; qlApp.quen(); guiFile.quen() }
        tiviCuaDsCai = info.khoaLuu
        var tt = io { tv.docTrangThai() }
        trangThai = tt
        // Tên app thật từ chính tivi cho tenDep (TrenTv, 27/10/2026): chạy nền, chỉ khi có gói chưa hỏi (app mới cài).
        if (vn.tivikit.dulieu.TenTrenTv.thieu(tt.caiDat)) { val goi = tt.caiDat; viewModelScope.launch { runCatching { docTenTrenTv(goi) } } }
        // Đổi tên gói (25/10/2026): app phụ cài từ bản cũ (com.icm.cleantv.*) chép sang gói mới rồi gỡ.
        baoChuyen = ""
        if (vn.tivikit.dulieu.GoiCu.APP_PHU.any { it in tt.caiDat }) {
            chuyenAppPhuCu()
            tt = trangThai ?: tt
        }
        // Công tắc DNS AdGuard theo đúng tivi (24/10/2026, góp ý kimhuenguyen1964: kết nối lại thì công tắc luôn tắt).
        dnsChanQc = io { tv.dnsDangChan() }
        val du = io { DuLieu.nap(ctx) }
        ungVien = du.khopMau(info)
        mauChon = ungVien.firstOrNull()?.first
        ganGiong = du.ganGiong(info, ungVien.map { it.first.id }.toSet())
        tatCaMau = du.mau.sortedBy { it.ten.lowercase() }
        // Điện thoại không có bản sao lưu (gỡ app, đổi máy, người khác dọn) thì lấy bản trên tivi.
        val slMay = kho.doc(info.khoaLuu)
        saoLuu = (slMay ?: io { tv.docSaoLuuTrenTv(info.khoaLuu) })?.also { b ->
            b.ip = host; io { tv.luuSaoLuu(b) }
        }
        saoLuuTuTv = slMay == null && saoLuu != null
        // Xiaomi: sau khi đổi Home, nút INPUT của remote không mở bảng nguồn nữa (upstream #1368) — bật sẵn để Home sạch nhận.
        nutInput = tt.homeHienTai == "com.google.android.apps.tv.launcherx" || info.brand.equals("Xiaomi", ignoreCase = true)
        // Sony 2015 (bo SVP-DTV15): Cài đặt dễ mở ra trống mà muốn bật lại Gỡ lỗi USB lại phải qua Cài đặt — tự tắt ADB là
        // khoá chủ nhà ở ngoài, không sửa được nữa (CleanTV 2.10.4: 2 máy phải reset cứng). Mặc định giữ ADB, vẫn tắt được tay.
        giuAdb = info.device == "SVP-DTV15"
        laMayChieu = tv.coVeMayChieu(tt, info)
        nhoTiviGanDay(host, cong.trim().ifEmpty { "5555" }, info.tenHienThi)
        adb.diemCuoi?.let { (h, p) -> giuKetNoi.daNoi(h, p) }
        val viec = sauKetNoi
        sauKetNoi = null
        man = when { dangKhoiPhuc -> Man.KHOI_PHUC; dangDon -> Man.THONG_TIN; else -> Man.CHINH }
        // Chức năng bấm lúc chưa nối (thường cũng chạy qua chay): đợi việc kết nối này nhả dangBan rồi mới mở.
        if (viec != null) viewModelScope.launch { androidx.compose.runtime.snapshotFlow { dangBan }.first { !it }; viec() }
    }

    /** Lời báo sau khi chuyển app phụ bản cũ trên tivi đang nối (hiện ở màn chính). */
    var baoChuyen by mutableStateOf("")

    /**
     * TiviKit bản cũ (tên gói com.icm.cleantv) còn trên máy này: bản mới khác tên gói nên cài riêng, hai app cùng tên dễ
     * mở nhầm — màn chính nhắc gỡ, tới khi gỡ hoặc bấm Để sau.
     */
    var conBanCu by mutableStateOf(false)
        private set

    fun kiemTraBanCu() {
        conBanCu = !prefs.getBoolean("an_ban_cu", false) &&
            runCatching { ctx.packageManager.getPackageInfo(vn.tivikit.dulieu.GoiCu.APP, 0) }.isSuccess
    }

    fun anBanCu() { prefs.edit().putBoolean("an_ban_cu", true).apply(); conBanCu = false }

    /**
     * Chuyển app phụ bản cũ trên tivi vừa nối sang gói vn.tivikit.* (ThaoTacTv.chuyenAppPhuCu). Lỗi ở đây không chặn kết
     * nối: app bản cũ vẫn chạy, lần nối sau thử lại.
     */
    private suspend fun chuyenAppPhuCu() {
        thongBao = t("Tivi còn app phụ của TiviKit bản cũ, đang chuyển sang bản mới…", "The TV has helper apps from the old TiviKit, moving them to the new one…")
        val kq = runCatching {
            io { tv.chuyenAppPhuCu({ ten -> vn.tivikit.CapNhat.moAsset(ctx, ten).readBytes() }) { s -> thongBao = s } }
        }.getOrElse { e ->
            baoChuyen = t("Chưa chuyển được app phụ bản cũ (${moTaLoi(e)}). Chúng vẫn chạy; lần kết nối sau app thử lại.",
                "Couldn't move the old helper apps (${moTaLoi(e)}). They keep working; the app tries again next time.")
            return
        }
        trangThai = io { tv.docTrangThai() }
        if (kq.kenh.isNotEmpty()) veApp()
        val xong = listOfNotNull(
            if (kq.kenh.isNotEmpty()) t("ô Kênh nhanh ${kq.kenh.joinToString(", ")}", "Quick channel tiles ${kq.kenh.joinToString(", ")}") else null,
            if (kq.henGio) t("lịch Hẹn giờ", "the Timer schedule") else null,
        )
        baoChuyen = listOfNotNull(
            if (xong.isNotEmpty()) t("Đã chuyển ${xong.joinToString(" và ")} sang TiviKit mới (tên gói vn.tivikit), nút remote và dock giữ nguyên.",
                "Moved ${xong.joinToString(" and ")} to the new TiviKit (package vn.tivikit); remote buttons and the dock are kept.") else null,
            if (kq.khoaCho) t("Khoá trẻ em vẫn là bản cũ và vẫn khoá: mở Khoá trẻ em, đặt lại PIN rồi Lưu để chuyển.",
                "The Kids lock is still the old version and keeps working: open Kids lock, set the PIN again and Save to move it.") else null,
            if (kq.loi.isNotEmpty()) t("Chưa chuyển được: ${kq.loi.joinToString("; ")}. Bản cũ vẫn chạy, lần kết nối sau app thử lại.",
                "Not moved yet: ${kq.loi.joinToString("; ")}. The old ones keep working; the app tries again next time.") else null,
        ).joinToString(" ")
    }

    var dangKhoiPhuc by mutableStateOf(false)
    var khoiPhucXong by mutableStateOf(false)

    // ---------- Lập kế hoạch ----------

    fun lapKeHoach() = chay(t("Đang so với ${DuLieu.SO_MAU} mẫu tivi…", "Comparing with ${DuLieu.SO_MAU} TV models…")) {
        val tt = trangThai ?: return@chay
        if (chanKhongPhaiTivi) return@chay
        val du = io { DuLieu.nap(ctx) }
        val ds = io { KeHoach.lap(du, mauChon, tt) }
        muc = ds
        chon.clear(); ds.forEach { chon[it.pkg] = it.chonSan }
        man = Man.CHON_GOI
    }

    fun soDaChon() = chon.count { it.value }

    // ---------- Tắt gói ----------

    fun batDauTat() = chay(t("Đang sao lưu…", "Backing up…")) {
        val info = thongTin ?: return@chay
        if (chanKhongPhaiTivi) return@chay
        nhatKy.clear(); ketQua = null; tienDo = 0f
        manTruocHome = Man.DANG_TAT
        man = Man.DANG_TAT
        val sl = io { tv.damBaoSaoLuu(info, getprop, ip) }
        saoLuu = sl
        ghi(t("Đã lưu bản sao lưu (${sl.goiTruoc.size} gói, ${sl.daTatTruoc.size} gói vốn đã tắt).", "Backup saved (${sl.goiTruoc.size} packages, ${sl.daTatTruoc.size} already disabled)."))
        val ds = muc.filter { chon[it.pkg] == true }.map { it.pkg }
        val kq = try {
            io { tv.tatGoi(ds, sl, epSetupwraith) { s -> ghiTuIo(s) } }
        } catch (e: ThaoTacTv.TiviKhoiDongLaiException) {
            vn.tivikit.Dem.gui(ctx, "don-loi", tenTiviDem(), e.daTat, "khoi-dong-lai:${e.goi}")
            saoLuu = kho.doc(info.khoaLuu); taiLaiSaoLuu(); throw e
        } catch (e: ThaoTacTv.MatKetNoiTvException) {
            vn.tivikit.Dem.gui(ctx, "don-loi", tenTiviDem(), e.daTat, "mat-ket-noi:${e.goi}")
            saoLuu = kho.doc(info.khoaLuu); taiLaiSaoLuu(); throw e
        }
        ketQua = kq
        vn.tivikit.Dem.gui(ctx, "don", tenTiviDem(), kq.daTat.size, if (mauChon != null) "mau:${mauChon!!.id}" else "luat-chung")
        ghi(t("Xong: tắt ${kq.daTat.size}, giữ lại ${kq.boQua.size}, lỗi ${kq.loi.size}.", "Done: disabled ${kq.daTat.size}, kept ${kq.boQua.size}, failed ${kq.loi.size}."))
        trangThai = io { tv.docTrangThai() }
        saoLuu = kho.doc(info.khoaLuu)
        taiLaiSaoLuu()
        chuanBiDock()
    }

    /**
     * Bỏ qua bước dọn, chỉ cài / đổi Home sạch (góp ý github.com/vgscity/vgs-cleantv/issues/1, 22/10/2026). Sao lưu VẪN
     * chạy (vài giây): đó là đường lui duy nhất để Khôi phục.
     */
    /** Bỏ dọn, sang Home sạch; [veLai] = màn khi bấm quay lại (màn chọn gói, hoặc màn chính khi mở từ ô Home sạch). */
    fun boQuaDon(veLai: Man = Man.CHON_GOI) = chay(t("Đang sao lưu…", "Backing up…")) {
        val info = thongTin ?: return@chay
        saoLuu = io { tv.damBaoSaoLuu(info, getprop, ip) }
        taiLaiSaoLuu()
        chuanBiDock()
        manTruocHome = veLai
        man = Man.HOME_SACH
        if (daCoHomeSach) napCauHinhCu()
    }

    // Đi qua hàng đợi Main như ghiTuIo, để dòng tổng kết không chen lên trước dòng tiến độ.
    /** Tên tivi gửi kèm số liệu ẩn danh: hãng + model + device + Android. */
    private fun tenTiviDem(): String = thongTin?.let { "${it.tenHienThi} (${it.device}) Android ${it.android}" } ?: ""

    private fun ghi(s: String) { viewModelScope.launch(Dispatchers.Main) { nhatKy += s } }
    /** 0..1 khi đang tắt/bật lại từng gói, -1 khi không đếm được. */
    var tienDo by mutableStateOf(-1f)

    private fun ghiTuIo(s: String) {
        viewModelScope.launch(Dispatchers.Main) {
            nhatKy += s; thongBao = s
            Regex("""(\d+)/(\d+)""").find(s)?.let { m ->
                val (a, b) = m.destructured
                if (b.toInt() > 0) tienDo = a.toFloat() / b.toFloat()
            }
        }
    }

    // ---------- Khoá trẻ em (23/10/2026) ----------

    /** Một khung giờ khoá: "HH:MM", ngay 1 = Thứ Hai … 7 = Chủ Nhật. */
    data class KhungKhoa(val tu: String, val den: String, val ngay: Set<Int>)
    var khoaTrenTv by mutableStateOf<org.json.JSONObject?>(null)
    var khoaBat by mutableStateOf(true)
    var khoaPin by mutableStateOf("")
    var khoaPin2 by mutableStateOf("")
    val khoaKhung = androidx.compose.runtime.mutableStateListOf<KhungKhoa>()
    var khoaBao by mutableStateOf("")
    /** Đúng PIN trên tivi thì mở bao lâu (anh Alex chốt 60 phút). */
    val KHOA_MO_TAM = 60
    /** Giới hạn xem mỗi ngày, phút (0 = không giới hạn): thứ Hai–Sáu và thứ Bảy, Chủ Nhật (25/10/2026). */
    var khoaPhutThuong by mutableStateOf(0)
    var khoaPhutCuoiTuan by mutableStateOf(0)
    /** Báo cáo 7 ngày đọc từ app khoá; null = tivi chưa có app khoá bản đếm giờ. */
    var khoaThoiGian by mutableStateOf<vn.tivikit.dulieu.ThoiGianXem?>(null)

    private suspend fun docThoiGianKhoa(j: org.json.JSONObject?): vn.tivikit.dulieu.ThoiGianXem? =
        if (j == null || j.optBoolean("banCu")) null
        else io { runCatching { tv.docThoiGianKhoa()?.let { vn.tivikit.dulieu.ThoiGianXem.tuJson(it) } }.getOrNull() }

    fun moKhoaTre() = chay(t("Đọc cài đặt khoá trên tivi…", "Reading the lock settings…")) {
        val j = io { tv.docKhoa() }
        khoaTrenTv = j; khoaBao = ""; khoaPin = ""; khoaPin2 = ""
        khoaKhung.clear()
        khoaPhutThuong = j?.optInt("phutThuong") ?: 0; khoaPhutCuoiTuan = j?.optInt("phutCuoiTuan") ?: 0
        khoaThoiGian = docThoiGianKhoa(j)
        if (j != null) {
            khoaBat = j.optBoolean("bat")
            val ks = j.optJSONArray("khung") ?: org.json.JSONArray()
            for (i in 0 until ks.length()) {
                val k = ks.getJSONObject(i); val ng = k.optJSONArray("ngay") ?: org.json.JSONArray()
                khoaKhung += KhungKhoa(k.optString("tu"), k.optString("den"), (0 until ng.length()).map { ng.getInt(it) }.toSet())
            }
        }
        if (khoaKhung.isEmpty() && khoaPhutThuong == 0 && khoaPhutCuoiTuan == 0) { khoaBat = true; khoaKhung += KhungKhoa("21:00", "06:00", (1..7).toSet()) }
        man = Man.KHOA_TRE
    }

    fun luuKhoaTre() = chay(t("Đang cài khoá lên tivi…", "Setting up the lock on the TV…")) {
        // Khoá bản cũ (trước khi đổi tên gói): PIN của nó không đọc ra được, bản mới cần PIN mới.
        val coPin = khoaTrenTv?.optBoolean("coPin") == true && khoaTrenTv?.optBoolean("banCu") != true
        if (!coPin || khoaPin.isNotEmpty()) {
            if (!Regex("^[0-9]{4}$").matches(khoaPin)) error(t("Mã PIN phải đúng 4 chữ số.", "The PIN must be exactly 4 digits."))
            if (khoaPin != khoaPin2) error(t("Hai lần nhập PIN không giống nhau.", "The two PINs don't match."))
        }
        if (khoaKhung.isEmpty() && khoaPhutThuong == 0 && khoaPhutCuoiTuan == 0) error(t("Thêm ít nhất một khung giờ khoá hoặc đặt giới hạn mỗi ngày.", "Add a lock period or a daily limit."))
        val gio = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
        khoaKhung.forEach { if (!gio.matches(it.tu) || !gio.matches(it.den) || it.ngay.isEmpty()) error(t("Khung giờ ${it.tu}–${it.den} chưa đúng (giờ dạng 21:00, chọn ít nhất một ngày).", "Period ${it.tu}–${it.den} is not valid.")) }
        val j = org.json.JSONObject().put("bat", khoaBat).put("moTamPhut", KHOA_MO_TAM)
            .put("khung", org.json.JSONArray(khoaKhung.map { org.json.JSONObject().put("tu", it.tu).put("den", it.den).put("ngay", org.json.JSONArray(it.ngay.sorted())) }))
            .put("phutThuong", khoaPhutThuong).put("phutCuoiTuan", khoaPhutCuoiTuan)
        if (khoaPin.isNotEmpty()) {
            val muoi = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val bam = java.security.MessageDigest.getInstance("SHA-256").digest((muoi + khoaPin).toByteArray()).joinToString("") { "%02x".format(it) }
            j.put("muoi", muoi).put("pinBam", bam)
        }
        val apk = io { vn.tivikit.CapNhat.moAsset(ctx, "khoa.apk").readBytes() }
        io { tv.caiKhoa(apk, j.toString()) { thongBao = it } }
        kotlinx.coroutines.delay(2500)
        khoaTrenTv = io { tv.docKhoa() }
        khoaThoiGian = docThoiGianKhoa(khoaTrenTv)
        khoaPin = ""; khoaPin2 = ""
        val chay = khoaTrenTv?.optBoolean("dichVuChay") == true
        val coHan = khoaPhutThuong > 0 || khoaPhutCuoiTuan > 0
        khoaBao = if (!khoaBat) t("Đã lưu. Khoá đang tắt.", "Saved. The lock is off.")
            else if (chay && coHan) t("Đã bật khoá. Trong khung giờ khoá hoặc khi đã xem hết giờ trong ngày, tivi hiện màn khoá (còn 5 phút thì báo trước); nhập đúng PIN thì mở $KHOA_MO_TAM phút.",
                "The lock is on. During the periods, or once today's time is used up, the TV shows the lock screen (with a 5-minute warning); the PIN unlocks it for $KHOA_MO_TAM minutes.")
            else if (chay) t("Đã bật khoá. Trong khung giờ, tivi hiện màn khoá; nhập đúng PIN thì mở $KHOA_MO_TAM phút.", "The lock is on. During the periods the TV shows the lock screen; the PIN unlocks it for $KHOA_MO_TAM minutes.")
            else t("Đã lưu nhưng dịch vụ khoá chưa chạy. Tắt tivi bật lại rồi mở lại màn này kiểm tra.", "Saved, but the lock service isn't running yet. Restart the TV and check again.")
        vn.tivikit.Dem.gui(ctx, "khoa", tenTiviDem(), khoaKhung.size, if (khoaBat) "bat" else "tat")
    }

    fun moTamKhoaTre(phut: Int) = chay(t("Đang gửi lệnh…", "Sending…")) {
        io { tv.moTamKhoa(phut) }
        kotlinx.coroutines.delay(800)
        khoaTrenTv = io { tv.docKhoa() }
        khoaBao = if (phut > 0) t("Đã mở tạm $phut phút.", "Unlocked for $phut minutes.") else t("Đã khoá lại (nếu đang trong giờ khoá hoặc đã hết giờ xem).", "Locked again (if within a period or out of time for today).")
    }

    fun tatKhoaTre() = chay(t("Đang tắt khoá…", "Turning the lock off…")) {
        io { tv.tatKhoa(go = true) }
        khoaTrenTv = null; khoaThoiGian = null; khoaBao = t("Đã tắt và gỡ Khoá trẻ em khỏi tivi.", "The Kids lock is off and removed from the TV.")
    }

    // ---------- Cài app lên tivi (25/10/2026) ----------

    enum class TrangThaiCai { CHO, DANG_CAI, XONG, LOI }

    /** Một app chờ cài: file chọn trên điện thoại ([file], đã chép vào bộ nhớ đệm) hoặc file .apk nằm sẵn trên tivi ([duongDanTv]). */
    class AppCai(val ten: String, val co: Long, val file: FileApk?, val duongDanTv: String?) {
        val thongTin get() = file?.thongTin
        var trangThai by mutableStateOf(TrangThaiCai.CHO)
        var bao by mutableStateOf("")
        var tienDo by mutableStateOf(0f)
        /** Bản đang có trên tivi: đọc lúc thêm vào danh sách và sau khi cài. */
        var banTv by mutableStateOf<String?>(null)
        var dangTatTrenTv by mutableStateOf(false)
        /** Lỗi khác chữ ký / hạ phiên bản trên app không phải hệ thống: gỡ bản trên tivi rồi cài lại được. */
        var canGo by mutableStateOf(false)
        var pkgDaCai by mutableStateOf<String?>(null)
    }
    val dsCai = mutableStateListOf<AppCai>()
    /** File .apk tìm thấy trên tivi / USB; null = chưa tìm. */
    var apkTrenTv by mutableStateOf<List<Pair<String, Long>>?>(null)
    private var abiTvDaDo: List<String>? = null
    private var tiviCuaDsCai: String? = null
    @Volatile private var phanTramCu = -1

    /** Màn Cài app: đọc lại app trên tivi để ô gợi ý biết app nào đã cài (Cập nhật / Mở thay cho Thêm / CH Play). */
    fun moCaiApk() = chay(t("Đang đọc app trên tivi…", "Reading the TV's apps…")) {
        damBaoKetNoi()
        trangThai = io { tv.docTrangThai() }
        baoGoiY = ""
        man = Man.CAI_APK
    }

    /** Lời báo của ô gợi ý app (đã mở trang CH Play…). */
    var baoGoiY by mutableStateOf("")

    /** App gợi ý chỉ có trên CH Play: mở trang của nó trên CH Play của tivi, người dùng bấm Cài đặt bằng remote. */
    fun moCHPlay(g: vn.tivikit.dulieu.GoiY.App) = chay(t("Đang mở ${g.ten} trên CH Play của tivi…", "Opening ${g.ten} in the TV's Play Store…")) {
        damBaoKetNoi()
        baoGoiY = ""
        val out = io { tv.moCHPlay(g.goi.first()) }
        if (out.contains("Error")) error(t("Tivi không mở được CH Play (tivi không có CH Play?): ${out.trim().lines().lastOrNull()}",
            "The TV couldn't open the Play Store (no Play Store on this TV?): ${out.trim().lines().lastOrNull()}"))
        baoGoiY = t("Đã mở ${g.ten} trên CH Play của tivi: bấm Cài đặt bằng remote. Cài xong mở lại màn này sẽ thấy \"đã cài\".",
            "${g.ten} is open in the TV's Play Store: press Install with the remote. Reopen this screen afterwards to see it as installed.")
    }

    /** Cài [f] lên tivi đang nối (gọi trên luồng IO): một phần như adb install, gói chia nhỏ đọc từng phần trong zip. */
    internal fun caiFileApk(f: FileApk, guiDuoc: (Long) -> Unit): String =
        if (!f.laGoi) tv.caiApp(f.phan, { f.file.inputStream() }, guiDuoc)
        else java.util.zip.ZipFile(f.file).use { z -> tv.caiApp(f.phan, { p -> z.getInputStream(z.getEntry(p.muc)) }, guiDuoc) }

    /**
     * Công tắc "DNS chặn quảng cáo (AdGuard)" của Home sạch: đặt rồi chờ tivi kiểm tra; mạng chặn DNS-over-TLS (cổng 853)
     * thì tivi đã được trả DNS cũ — tắt công tắc và báo, thay vì để tivi mất mạng (25/10/2026).
     */
    private suspend fun batDnsAdGuardAnToan(sl: BanSaoLuu) {
        thongBao = t("Bật DNS chặn quảng cáo, chờ tivi kiểm tra…", "Turning on ad-blocking DNS, waiting for the TV to check it…")
        if (io { tv.datDnsAnToan(sl, vn.tivikit.dulieu.DnsRieng.ADGUARD) } == false) {
            dnsChanQc = false
            val bao = t("Tivi không dùng được DNS AdGuard (mạng chặn DNS-over-TLS cổng 853?): đã trả lại DNS cũ, tivi vẫn có mạng.",
                "The TV can't use AdGuard DNS (DNS-over-TLS port 853 blocked?): the old DNS is back, the TV stays online.")
            ghi(bao); loi = bao
        }
    }

    /** DNS riêng: AdGuard / NextDNS / tự nhập (ui/DnsTivi.kt). */
    val dnsTivi by lazy { DnsTivi(this) }

    /** ABI của tivi đang nối (nhớ tới lần kết nối sau). */
    internal suspend fun layAbi(): List<String> = abiTvDaDo ?: io { tv.abiTv() }.also { abiTvDaDo = it }

    /**
     * Trước mỗi việc ADB của màn Cài app: kết nối hay đứt trong lúc người dùng ở trình chọn file (app nằm nền) — đứt
     * thì tự nối lại đúng tivi đó, không bắt người dùng quay về màn Kết nối.
     */
    internal suspend fun damBaoKetNoi() {
        if (io { adb.conSong() }) return
        val viec = thongBao
        thongBao = t("Kết nối tivi bị đứt, đang nối lại…", "The TV connection dropped, reconnecting…")
        if (!io { adb.ketNoiLai() && adb.conSong() }) error(t(
            "Mất kết nối với tivi và nối lại không được. Kiểm tra tivi còn bật, cùng mạng Wi-Fi với điện thoại và vẫn bật Gỡ lỗi, rồi bấm mũi tên quay lại để kết nối lại.",
            "Lost the TV and couldn't get it back. Check the TV is on, on the same Wi-Fi as the phone and still has debugging on, then go back and connect again."))
        thongBao = viec
    }

    fun themFileApk(uris: List<android.net.Uri>) = chay(t("Đang đọc file…", "Reading the file…")) {
        damBaoKetNoi()
        val abi = layAbi()
        val loiDoc = mutableListOf<String>()
        uris.forEachIndexed { i, uri ->
            val viec = t("Đang đọc file ${i + 1}/${uris.size}…", "Reading file ${i + 1}/${uris.size}…")
            phanTramCu = -1
            val f = runCatching { io { GoiApk.doc(ctx, uri, abi) { p -> baoTienDo(viec, p) } } }
            f.onFailure { loiDoc += it.message ?: it.javaClass.simpleName }
            f.onSuccess { fa -> themVaoDs(fa) }
        }
        if (loiDoc.isNotEmpty()) loi = loiDoc.joinToString("\n")
    }

    /** Đưa một file APK vào danh sách chờ cài, kèm bản đang có trên tivi. */
    internal suspend fun themVaoDs(fa: FileApk): AppCai {
        val a = AppCai(fa.thongTin?.ten ?: fa.ten, fa.co, fa, null)
        a.thongTin?.pkg?.let { p ->
            a.banTv = io { tv.banTrenTv(p) }
            a.dangTatTrenTv = a.banTv != null && io { tv.dangTat(p) }
        }
        dsCai += a
        return a
    }

    /** Ô "Từ đường link" của màn Cài app. */
    var linkTai by mutableStateOf("")

    /** Tải APK từ [url] về điện thoại rồi đưa vào danh sách chờ cài (25/10/2026). */
    fun themTuLink(url: String = linkTai) = chay(t("Đang tải…", "Downloading…")) {
        val u = url.trim()
        if (!Regex("""^https?://\S+$""").matches(u)) error(t("Dán đường link bắt đầu bằng https://", "Paste a link starting with https://"))
        damBaoKetNoi()
        themVaoDs(taiVe(u, t("Đang tải từ link…", "Downloading from the link…")))
        linkTai = ""
    }

    /** Tìm bản mới nhất của app gợi ý cho chip của tivi, tải về rồi đưa vào danh sách chờ cài. */
    fun themGoiY(g: vn.tivikit.dulieu.GoiY.App) = chay(t("Đang tìm bản mới nhất của ${g.ten}…", "Finding the latest ${g.ten}…")) {
        damBaoKetNoi()
        val abi = layAbi()
        val url = io { vn.tivikit.dulieu.GoiY.timLink(g, abi) }
        themVaoDs(taiVe(url, t("Đang tải ${g.ten}…", "Downloading ${g.ten}…"), g.ten))
    }

    /** Tải [url] về bộ nhớ đệm (hiện %), đọc thành FileApk theo ABI của tivi đang nối. */
    internal suspend fun taiVe(url: String, viec: String, ten: String? = null): FileApk {
        thongBao = viec; phanTramCu = -1
        val batDau = System.nanoTime()
        val f = io {
            vn.tivikit.dulieu.TaiLink.tai(ctx, url) { da, tong ->
                if (tong > 0) baoTienDo(viec, da.toFloat() / tong, them = tocDo(da, tong, batDau))
            }
        }
        return try {
            val abi = layAbi()
            io { GoiApk.tuFile(ctx, ten ?: f.name.substringAfter('-'), f, abi, tam = true) }
        } catch (e: Throwable) { f.delete(); throw e }
    }

    fun timApkTrenTv() = chay(t("Đang tìm file .apk trên tivi / USB…", "Looking for .apk files on the TV / USB…")) {
        damBaoKetNoi()
        apkTrenTv = io { tv.timApkTrenTv() }
    }

    fun themApkTrenTv(duongDan: String, co: Long) {
        if (dsCai.none { it.duongDanTv == duongDan }) dsCai += AppCai(duongDan.substringAfterLast('/'), co, null, duongDan)
    }

    fun boApkKhoiDs(a: AppCai) { a.file?.takeIf { it.tam }?.file?.delete(); dsCai.remove(a) }

    fun caiTatCa() = chay(t("Đang cài…", "Installing…")) {
        damBaoKetNoi()
        for (a in dsCai.filter { it.trangThai == TrangThaiCai.CHO }) if (!caiMot(a, goTruoc = false)) break
        // Danh sách gói mới cho dock / Home sạch.
        runCatching { trangThai = io { tv.docTrangThai() } }
    }

    /** Thử lại một app lỗi; [goTruoc] = gỡ bản trên tivi trước (người dùng đã đồng ý mất dữ liệu app đó). */
    fun caiLai(a: AppCai, goTruoc: Boolean) = chay(t("Đang cài…", "Installing…")) {
        damBaoKetNoi()
        caiMot(a, goTruoc)
        runCatching { trangThai = io { tv.docTrangThai() } }
    }

    fun moAppTrenTv(pkg: String) = chay(t("Đang mở app trên tivi…", "Opening the app on the TV…")) {
        damBaoKetNoi()
        if (!io { tv.moApp(pkg) }) error(t("App này không có màn mở trên tivi (có thể là dịch vụ hay bàn phím chạy ngầm).",
            "This app has no launch screen on the TV (it may be a background service or a keyboard)."))
    }

    /** Cài một app; false nếu mất kết nối với tivi mà nối lại không được (dừng cả lượt). */
    internal suspend fun caiMot(a: AppCai, goTruoc: Boolean, lanThu: Int = 0): Boolean {
        val sdk = thongTin?.sdk ?: 0
        val tt = a.thongTin
        a.bao = ""; a.canGo = false; a.tienDo = 0f
        if (tt != null && sdk > 0 && tt.minSdk > sdk) {
            a.trangThai = TrangThaiCai.LOI
            a.bao = t("App cần Android API ${tt.minSdk} trở lên, tivi đang là API $sdk.", "The app needs Android API ${tt.minSdk} or later; the TV is API $sdk.")
            return true
        }
        a.trangThai = TrangThaiCai.DANG_CAI
        val viec = t("Đang gửi ${a.ten} lên tivi…", "Sending ${a.ten} to the TV…")
        thongBao = viec; phanTramCu = -1
        return try {
            if (goTruoc && tt != null) {
                thongBao = t("Đang gỡ bản cũ của ${a.ten} trên tivi…", "Removing the old ${a.ten} from the TV…")
                io { tv.goApp(tt.pkg) }
                thongBao = viec
            }
            // File nằm trên tivi không đọc được tên gói trước: so danh sách gói trước / sau để biết app nào mới.
            val truoc = if (tt == null) io { tv.goiDaCai() } else emptySet()
            val f = a.file
            val batDau = System.nanoTime()
            val guiDuoc: (Long) -> Unit = { n -> baoTienDo(viec, n.toFloat() / a.co, a, tocDo(n, a.co, batDau)) }
            val out = io { if (f == null) tv.caiApkTrenTv(a.duongDanTv!!) else caiFileApk(f, guiDuoc) }
            if (out.contains("Success")) {
                a.trangThai = TrangThaiCai.XONG; a.tienDo = 1f
                a.pkgDaCai = tt?.pkg ?: (io { tv.goiDaCai() } - truoc).singleOrNull()
                a.pkgDaCai?.let { p -> a.banTv = io { tv.banTrenTv(p) }; a.dangTatTrenTv = io { tv.dangTat(p) } }
                a.file?.takeIf { it.tam }?.file?.delete()
            } else {
                a.trangThai = TrangThaiCai.LOI
                a.bao = GoiApk.giaiThich(out, sdk)
                a.canGo = tt != null && GoiApk.canGoTruoc(out) && !io { tv.laAppHeThong(tt.pkg) }
            }
            true
        } catch (e: Throwable) {
            a.trangThai = TrangThaiCai.LOI
            if (ThaoTacTv.laMatKetNoi(e)) {
                // Đứt giữa lúc gửi: nối lại đúng tivi đó rồi gửi lại từ đầu một lần (phiên cài dở tivi tự bỏ). Bản trên
                // tivi nếu vừa gỡ thì lần này khỏi gỡ nữa.
                thongBao = t("Kết nối tivi bị đứt, đang nối lại…", "The TV connection dropped, reconnecting…")
                if (lanThu == 0 && io { adb.ketNoiLai() && adb.conSong() }) return caiMot(a, goTruoc = false, lanThu = 1)
                a.bao = t("Mất kết nối với tivi lúc đang gửi và nối lại không được. Kiểm tra tivi còn bật và cùng Wi-Fi, rồi bấm Thử lại.",
                    "Lost the TV while sending and couldn't reconnect. Check the TV is on and on the same Wi-Fi, then press Retry.")
                false
            } else {
                a.bao = GoiApk.giaiThich(e.message ?: "", sdk)
                true
            }
        }
    }

    /** Gọi từ luồng IO: chỉ đẩy lên giao diện khi số phần trăm đổi. */
    private fun baoTienDo(viec: String, p: Float, a: AppCai? = null, them: String = "") {
        val pt = (p * 100).toInt().coerceIn(0, 100)
        if (pt == phanTramCu) return
        phanTramCu = pt
        viewModelScope.launch(Dispatchers.Main) { a?.tienDo = p.coerceIn(0f, 1f); thongBao = "$viec $pt%$them" }
    }

    /** " · 3,2 MB/s · còn 20 giây", từ giây thứ 2 (trước đó số còn nhảy lung tung); người dùng thấy và báo lại được tốc độ thật. */
    internal fun tocDo(da: Long, tong: Long, batDau: Long): String {
        val giay = (System.nanoTime() - batDau) / 1e9
        if (giay < 2 || da <= 0) return ""
        val bps = da / giay
        val con = ((tong - da) / bps).toInt()
        val conChu = if (con >= 90) t("còn ${(con + 30) / 60} phút", "${(con + 30) / 60} min left") else t("còn $con giây", "$con s left")
        return String.format(java.util.Locale.getDefault(), " · %.1f MB/s · ", bps / 1e6) + conChu
    }

    // ---------- Gửi danh sách gói cho VGS (màn Xong, 23/10/2026) ----------
    var dsDanhGia by mutableStateOf("on")
    var dsGhiChu by mutableStateOf("")
    var dsLienHe by mutableStateOf("")
    var dsDaGui by mutableStateOf(false)

    /** Đọc lại gói đang tắt trên tivi rồi gửi kèm model + đánh giá (người dùng tự bấm). */
    fun guiDanhSach() = chay(t("Đang gửi danh sách…", "Sending the list…")) {
        val tt = io { tv.docTrangThai() }
        val tv0 = thongTin
        val du = org.json.JSONObject()
            .put("hang", tv0?.manufacturer?.ifBlank { tv0.brand } ?: "").put("model", tv0?.model ?: "").put("device", tv0?.device ?: "")
            .put("android", tv0?.android ?: "").put("mau", mauChon?.id ?: "")
            .put("danhGia", dsDanhGia).put("ghiChu", dsGhiChu.trim()).put("lienHe", dsLienHe.trim())
            .put("goi", org.json.JSONArray(tt.daTat.sorted()))
            .put("giu", org.json.JSONArray((ketQua?.boQua ?: emptyList()).map { it.first }))
        du.put("chanDoan", (io { tv.chanDoanHome() } + "\n# phim-home " + phimHomeGhi).take(3000))
        val ok = io { vn.tivikit.Dem.guiDanhSach(ctx, du) }
        if (!ok) error(t("Chưa gửi được (mạng?). Thử lại sau nhé.", "Couldn't send (network?). Try again later."))
        dsDaGui = true
    }

    fun chupManHinh() = chay(t("Đang chụp màn hình tivi…", "Taking a TV screenshot…")) {
        val bytes = io { tv.chupManHinh() }
        anhTv = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error(t("Không chụp được (một số tivi cấm chụp khi đang xem HDMI — bấm Home trên remote rồi thử lại).", "Could not capture (some TVs block it on an HDMI input — press Home on the remote and retry)."))
    }

    // ---------- Home sạch ----------

    /** App có biểu tượng còn bật, để chọn vào dock. */
    fun appChoDock(): List<String> {
        val tt = trangThai ?: return emptyList()
        val khongDua = ThaoTacTv.HOME_SACH + setOf("com.android.tv.settings", "com.android.settings") + ThaoTacTv.HOME_GOC_BIET
        return tt.coBieuTuong.filter { it in tt.caiDat && it !in tt.daTat && it !in khongDua }
            .sortedWith(compareBy({ p -> UU_TIEN_DOCK.indexOf(p).let { if (it < 0) 99 else it } }, { it }))
    }

    /** Từ màn thông tin: đổi theme cho tivi đã có Home sạch. */
    /** Màn mà nút Back ở Home sạch quay về: vừa dọn → màn dọn; Đổi giao diện → thông tin tivi; Bỏ qua → chọn gói. */
    var manTruocHome by mutableStateOf(Man.DANG_TAT)

    fun moDoiTheme() { chuanBiDock(); manTruocHome = if (man == Man.CHINH) Man.CHINH else Man.THONG_TIN; man = Man.HOME_SACH; viewModelScope.launch { napCauHinhCu() } }

    /** "Đổi giao diện": mở ra đúng các lựa chọn đang có trên tivi thay vì mặc định. */
    /** Đã nạp config.json của tivi vào các lựa chọn chưa (mỗi lần kết nối một lần — quay lại màn không xoá chỉnh sửa). */
    private var daNapCu = false

    private suspend fun napCauHinhCu() {
        if (daNapCu) return
        daNapCu = true
        val j = runCatching { io { tv.docCauHinhTv() } }.getOrNull() ?: return
        napTuJson(j)
        // Ảnh nền riêng đã đặt từ trước: đọc lại từ tivi để Áp dụng lần sau không thay bằng ảnh theme (23/10/2026).
        // Nhận ra bằng cờ vgs_anh_rieng (từ 2.5.6) hoặc ảnh trên tivi không trùng ảnh của theme nào (bản cũ hơn).
        // Ảnh VGS cũ (đã bỏ khỏi app) nhận ra theo SHA-256: là ảnh theme, Áp dụng lần sau thay bằng ảnh TiviKit.
        if (anhNenRieng == null && j.optBoolean("wallpaper")) {
            val anh = runCatching { io { tv.docAnhNenTv() } }.getOrNull()
            if (anh != null) {
                val laAnhTheme = !j.optBoolean("vgs_anh_rieng") && io {
                    vn.tivikit.dulieu.laAnhNenVgsCu(anh) ||
                        vn.tivikit.dulieu.THEMES.mapNotNull { it.anhNen }.any { a -> runCatching { ctx.assets.open(a).readBytes().contentEquals(anh) }.getOrDefault(false) }
                }
                if (!laAnhTheme) datAnhCoSan(anh)
            }
        }
    }

    /** Đưa một config.json (của tivi hoặc file cấu hình đã lưu) vào các lựa chọn trên màn Home sạch. */
    private fun napTuJson(j: JSONObject) {
        // Khoá do giao diện đang dùng ghi (vgs_theme, CleanTV 2.9) không phải lựa chọn của người dùng: đọc như mặc định,
        // không thì đổi sang giao diện khác vẫn dính (Kính mờ bật dock_glass, header_white, ẩn lời chào, dời hàng app).
        val theme = vn.tivikit.dulieu.themeTheoId(j.optString("vgs_theme"))
        theme?.let { themeChon = it }
        fun cuaTheme(k: String) = theme?.cauHinh?.containsKey(k) == true
        amLich = j.optBoolean("lunar_date", amLich)
        tenDuoiApp = j.optBoolean("always_show_names", false)
        tenToTrenDock = j.optBoolean("dock_title", true)
        toMauOApp = j.optBoolean("icon_tile_color", true)
        thuVienApp = j.optBoolean("app_library", false)
        dongHoKhiYen = j.optInt("home_idle_clock_minutes", 0)
        tiviYeu = j.optBoolean("reduced_motion", false)
        hieuUngKinh = !cuaTheme("dock_glass") && j.optBoolean("dock_glass", false)
        lamToiNen = j.optInt("wallpaper_dim", 0)
        hienThongSo = j.optBoolean("show_stats", false)
        doiHang = if (j.optBoolean("rows_middle", false)) HANG_GIUA else if (cuaTheme("rows_offset_dp")) 0 else j.optInt("rows_offset_dp", 0)
        nutDangPhat = j.optBoolean("now_playing", true)
        coChuDau = j.optInt("header_scale", 100)
        motHang = j.optBoolean("single_row", false)
        anLoiChao = !cuaTheme("show_greeting") && !j.optBoolean("show_greeting", true)
        // key_filter=false do nút gán riêng cất đi (PhimRieng.KHOA_BO_TAT_LOC) vẫn là lựa chọn của người dùng; TCL Google TV
        // thì app tự ghi, không tính.
        giuAmLuong = !cuaTheme("key_filter") && (!j.optBoolean("key_filter", true) || j.optBoolean(vn.tivikit.dulieu.PhimRieng.KHOA_BO_TAT_LOC)) && !laTclGoogleTv
        // Công tắc hiện đúng trạng thái thật, kể cả khi giao diện bật, và tắt được đè lên giao diện (CleanTV 3.0.0).
        oDeuNhau = j.optBoolean("uniform_tiles", false)
        moKhiBat = j.optBoolean("open_on_start_choice", false)
        moKhiThuc = j.optBoolean("wake_open_app", false)
        luonVeHome = j.optBoolean("power_on_home_always", false)
        chuTrang = !cuaTheme("header_white") && j.optBoolean("header_white", false)
        // Chỉ nạp số cột do người dùng chọn ở đây (vgs_so_cot), không lấy columns của theme.
        soCot = if (j.optBoolean("vgs_so_cot", false)) j.optInt("columns", 0) else 0
        if (j.has("handle_view_inputs")) nutInput = j.optBoolean("handle_view_inputs", nutInput)
        // Chỉ nạp lại khoá NGƯỜI DÙNG đặt ở Nâng cao (ghi trong vgs_nang_cao), không lấy khoá do theme ghi
        // (background_color, sky_intensity…) — nếu không, đổi theme sau đó sẽ bị kẹt giá trị của theme cũ.
        nangCao.clear()
        val daDat = j.optJSONArray("vgs_nang_cao")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
        dsNangCao.filter { it.khoa in daDat }.forEach { tc -> vn.tivikit.dulieu.NangCao.docChu(tc, j)?.let { nangCao[tc.khoa] = it } }
        // Nút gán riêng (màn Nút remote) cũng nằm trong app_keys: app_keys đặt ở Nâng cao đọc lại từ đó thì bỏ chúng ra,
        // không thì bỏ gán ở màn Nút remote xong chúng vẫn sống trong Nâng cao.
        phimRieng.clear(); phimRieng.addAll(vn.tivikit.dulieu.PhimRieng.docTu(j))
        nangCao["app_keys"]?.let { chu ->
            val con = chu.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() && it !in phimRieng }
            if (con.isEmpty()) nangCao.remove("app_keys") else nangCao["app_keys"] = con.joinToString(", ")
        }
        // Tên và logo các ô Kênh nhanh cũng vậy, trong app_labels và app_banners.
        tenKenh.clear(); tenKenh.putAll(vn.tivikit.dulieu.KenhNhanh.tenTrong(j))
        anhOKenh.clear(); anhOKenh.putAll(vn.tivikit.dulieu.KenhNhanh.anhTrong(j))
        for (khoa in listOf("app_labels", "app_banners")) nangCao[khoa]?.let { chu ->
            val con = chu.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() && !vn.tivikit.dulieu.KenhNhanh.laGoiKenh(it.substringBefore('=').trim()) }
            if (con.isEmpty()) nangCao.remove(khoa) else nangCao[khoa] = con.joinToString(", ")
        }
        nutManCho = j.optBoolean("dream_button", nutManCho)
        gonNut = j.optBoolean("top_compact", gonNut)
        j.optJSONArray("dock_apps")?.let { a ->
            val ds = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() && !it.startsWith(vn.tivikit.dulieu.KenhVtvGo.TIEN_TO) }
            if (ds.isNotEmpty()) { dock.clear(); dock.addAll(ds.take(DOCK_TOI_DA)) }
        }
        kenhChon.clear(); kenhChon.addAll(vn.tivikit.dulieu.KenhVtvGo.docTu(j))
        j.optString("greeting_name").takeIf { it.isNotBlank() }?.let { tenChao = it }
        j.optString("weather_place").takeIf { it.isNotBlank() }?.let { ten ->
            thanhPho = THANH_PHO.firstOrNull { it.ten == ten }
                ?: vn.tivikit.dulieu.ThanhPho(ten, j.optDouble("weather_lat"), j.optDouble("weather_lon")).takeIf { !it.lat.isNaN() && !it.lon.isNaN() }
        }
    }

    private fun chuanBiDock() {
        if (dock.isNotEmpty()) return
        dock.addAll(appChoDock().filter { it in UU_TIEN_DOCK }.take(4))
    }

    /** Dời app trong dock lên (-1) / xuống (+1). */
    fun dichDock(p: String, buoc: Int) {
        val i = dock.indexOf(p); val j = i + buoc
        if (i < 0 || j !in dock.indices) return
        dock[i] = dock[j]; dock[j] = p
    }

    fun doiDock(p: String) {
        if (p in dock) dock.remove(p) else if (dock.size < DOCK_TOI_DA) dock.add(p)
    }

    fun caiHomeSach() = chay(t("Đang cài Home sạch…", "Installing the clean Home…")) {
        if (laAndroidCu) error(t("Home sạch cần Android 8 trở lên, tivi/hộp này là Android ${thongTin?.android}. TiviKit chỉ dọn được quảng cáo và app rác trên máy này.", "The clean Home needs Android 8 or later; this TV/box runs Android ${thongTin?.android}. TiviKit can only clean ads and junk apps here."))
        if (laFireTv) error(t("Fire TV không cho app nào thay màn Home (Amazon khoá). TiviKit chỉ dọn được quảng cáo và app rác trên Fire TV.", "Fire TV does not let any app replace Home (Amazon locks it). On Fire TV TiviKit can only clean ads and junk apps."))
        val sl = saoLuu ?: io { tv.damBaoSaoLuu(thongTin!!, getprop, ip) }.also { saoLuu = it }
        nhatKy.clear()
        homeKhac = null
        val apk = io { vn.tivikit.CapNhat.moAsset(ctx, tv.apkHome).readBytes() }
        val cauHinh = cauHinhHome()
        val anh = anhNenTheme()
        val bo = boAnhTheme()
        napAnhKenh()
        val loiCai = io {
            tv.caiLauncher(apk, sl, cauHinh, anh, bo, nutInput, canDichVuPhim,
                { vn.tivikit.CapNhat.moAsset(ctx, "tvhome-xiaomi.apk").readBytes() }) { s -> ghiTuIo(s) }
        }
        // caiLauncher báo lỗi là đã tự trả Home gốc: huỷ canh gác trên tivi.
        if (loiCai != null) { io { tv.boGac() }; error(loiCai) }
        // Tivi Xiaomi gỡ bản com.gathin.tvhome thì caiLauncher đã cài lại bằng com.ts.launcher: nhớ tên gói mới.
        goiHome = io { tv.pkgHome }
        if (nutDangPhat) io { tv.choPhepDangPhat() }
        if (dnsChanQc) batDnsAdGuardAnToan(sl)
        if (nutManCho) io { tv.chuanBiManCho(sl) }
        thuPhimHome(sl)
        vn.tivikit.Dem.gui(ctx, "home", tenTiviDem(), dock.size, "theme:${themeChon.id}")
        if (ghimMayChieu.isNotEmpty()) vn.tivikit.Dem.gui(ctx, "may-chieu", tenTiviDem(), ghimMayChieu.size, ghimMayChieu.keys.joinToString(",").take(110))
        homeXong = true
        ghi(t("Home sạch đã chạy. Bấm Home trên remote để xem.", "The clean Home is running. Press Home on the remote to see it."))
        taiLaiSaoLuu()
        man = Man.XONG
    }

    /**
     * Bấm Home thật trên tivi, tắt gói còn giành nút Home (ThaoTacTv.damBaoPhimHome). Không được thì báo rõ, không
     * báo "xong" suông; gửi tên gói giành Home về bộ đếm để biết hãng nào bị (22/10/2026).
     */
    private suspend fun thuPhimHome(sl: vn.tivikit.dulieu.BanSaoLuu?) {
        val kq = io { tv.damBaoPhimHome(sl) { s -> ghiTuIo(s) } }
        // Đã thử xong (được, hoặc đã tự bật lại Home gốc): tivi thôi canh gác. Rớt kết nối trước dòng này thì
        // canh gác trên tivi tự trả Home gốc sau ThaoTacTv.GAC_GIAY giây.
        io { tv.boGac() }
        veApp()
        phimHomeOk = kq.ok
        phimHomeGhi = (if (kq.ok) "ok" else if (kq.gianh.isNotEmpty()) "lui" else "loi") + " gianh=" + kq.gianh.joinToString(",") + " conLai=" + kq.conLai + " lyDo=" + kq.lyDo
        if (kq.lyDo == "chuyen") { nutHome = true; ghi(t("Tivi không cho tắt Home gốc nên Home gốc vẫn bật, nhưng mỗi khi nó hiện ra Home sạch sẽ tự mở đè lên (bật \"Remote gửi phím Home lạ\"). Khôi phục là trả lại như cũ.", "The TV won't let the stock Home be disabled, so it stays on, but whenever it comes up the clean Home opens over it (\"Remote sends an odd Home key\" is on). Restore puts things back.")) }
        else if (kq.daBatBatPhim) { nutHome = true; ghi(t("Đã bật \"Remote gửi phím Home lạ\" vì nút Home của tivi bị app khác giành.", "Turned on \"Remote sends an odd Home key\" because another app held the Home button.")) }
        if (kq.gianh.isNotEmpty() || !kq.ok || kq.lyDo.isNotBlank()) {
            // ok: đã tắt và chạy; lui: đã tắt nhưng không được nên bật lại; loi: không tắt gì. lyDo (chuyen, khong-tat,
            // setupwraith, cvte) gắn sau dấu # để biết nhánh nào.
            val dau = if (kq.ok) "ok:" else if (kq.gianh.isNotEmpty()) "lui:" else "loi:"
            vn.tivikit.Dem.gui(ctx, "home-phim", tenTiviDem(), kq.gianh.size,
                dau + (kq.gianh + kq.conLai).filter { it.isNotBlank() }.distinct().joinToString(",").take(90) + (if (kq.lyDo.isNotBlank()) "#" + kq.lyDo else ""))
        }
        if (kq.ok && kq.gianh.isNotEmpty()) {
            ghi(t("Đã tắt ${kq.gianh.joinToString()} vì nó giành nút Home. Menu hình ảnh / âm thanh của hãng (nếu nằm trong Home cũ) vào bằng nút Cài đặt trên Home sạch; muốn trả lại thì bấm Khôi phục.",
                "Disabled ${kq.gianh.joinToString()} because it kept the Home button. Reach the maker's picture / sound menus with the Settings button on the clean Home; Restore brings it back."))
            taiLaiSaoLuu()
        }
        if (!kq.ok) {
            val goi = kq.conLai.ifBlank { t("không rõ", "unknown") }
            loi = if (kq.lyDo == "cvte")
                t("Nút Home trên tivi vẫn mở Home gốc ($goi). Tivi bo mạch CVTE: app không tắt Home gốc vì đã có tivi treo ở màn khởi động sau khi tắt. Tắt mở tivi một lần rồi bấm Home thử lại; vẫn về Home cũ thì Home sạch vẫn mở được từ danh sách ứng dụng.",
                    "The TV's Home button still opens the stock Home ($goi). CVTE board: the app never disables it, since one TV hung on its boot animation after that. Restart the TV once and press Home again; if it still opens the old Home, launch the clean Home from the app list.")
            else if (kq.lyDo == "setupwraith")
                t("Nút Home trên tivi vẫn mở Home Google ($goi). Trên Google TV phải tắt cả setupwraith thì mới thay được Home, mà mạng Wi-Fi lưu lúc cài tivi thuộc về nó nên app không tự tắt (tắt là rớt Wi-Fi). Tivi vẫn dùng bình thường như cũ. Muốn thay hẳn: trên tivi vào Cài đặt > Mạng, quên mạng Wi-Fi rồi kết nối lại (hoặc cắm dây LAN), sau đó kết nối lại app, ở bước chọn gói tích thêm setupwraith (nhóm Google Home) rồi dọn lại.",
                    "The TV's Home button still opens Google's Home ($goi). On Google TV setupwraith must be disabled too before Home can change, and the Wi-Fi saved during setup belongs to it, so the app leaves it (disabling it would drop the Wi-Fi). The TV works as before. To take over Home fully: on the TV, Settings > Network, forget the Wi-Fi and join it again (or plug in Ethernet), reconnect the app, tick setupwraith (Google Home group) in the package list and clean again.")
            else if (kq.lyDo == "khong-tat")
                t("Tivi không cho tắt Home gốc ($goi): tắt xong nó tự bật lại, và cách mở đè Home sạch cũng không ăn. App đã để tivi như cũ. Home sạch vẫn mở được từ danh sách ứng dụng. Báo hãng, model tivi cho CleanYourTV (github.com/Gathin23/cleanyourtv) để được thêm hỗ trợ.",
                    "The TV won't keep the stock Home ($goi) disabled: it turns itself back on, and opening the clean Home over it didn't work either. The app left the TV as it was. Open the clean Home from the app list, and report the brand and model to CleanYourTV (github.com/Gathin23/cleanyourtv) to get support added.")
            else
                t("Nút Home trên tivi vẫn mở '$goi' thay vì Home sạch. App đã để tivi như cũ, không tắt thêm gì. Báo hãng, model tivi cho CleanYourTV (github.com/Gathin23/cleanyourtv) để được thêm hỗ trợ.",
                    "The TV's Home button still opens '$goi' instead of the clean Home. The app left the TV as it was. Report the brand and model to CleanYourTV (github.com/Gathin23/cleanyourtv) to get support added.")
        }
    }

    private fun cauHinhHome(): String = JSONObject().apply {
        // Ô kênh VTVgo (chọn ở màn Kênh nhanh) đứng đầu dock, rồi tới app người dùng chọn.
        put("dock_apps", JSONArray(dock.toList()))
        vn.tivikit.dulieu.KenhVtvGo.datVao(this, if (coVtvGo) kenhChon.toList() else emptyList())
        if (tenChao.isNotBlank()) put("greeting_name", tenChao.trim())
        themeChon.ghiVao(this)
        if (anhNenRieng != null) { put("wallpaper", true); put("vgs_anh_rieng", true) }
        // Tivi Xiaomi bản Trung Quốc chỉ chuyển nguồn qua trình phát Xiaomi (upstream examples/xiaomi-mitv-astp0): Home sạch
        // (bản vá CleanTV) đổi tên dòng HDMI1-3 / AV ra số cổng 23-25 / 2 khi input_action là EXTSRC_PLAY (23/10/2026).
        if (trangThai?.caiDat?.contains("com.xiaomi.mitv.tvplayer") == true) put("input_action", "com.xiaomi.mitv.tvplayer.EXTSRC_PLAY")
        put("dream_button", nutManCho)
        put("top_compact", gonNut)
        thanhPho?.let { put("weather_lat", it.lat); put("weather_lon", it.lon); put("weather_place", it.ten) }
        if (ghimMayChieu.isNotEmpty()) put("projector_actions", JSONArray(ghimMayChieu.map { (comp, nhan) -> "$nhan=$comp" }))
        put("lunar_date", amLich)
        // Âm lịch tính theo múi giờ: launcher tự chọn theo ngôn ngữ tivi, tivi để tiếng Anh ở VN thì lệch một ngày quanh
        // ngày sóc (Formovie L206, 22/10/2026: 12/9 thay vì 13/9). Ghi múi giờ của điện thoại khi là +7 / +8.
        if (amLich) (java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000).takeIf { it == 7 || it == 8 }?.let { put("lunar_utc_offset", it) }
        // Launcher 2.6: nút Input trên remote (VIEW_INPUTS) mở bảng chọn nguồn của Home sạch — đi cùng công tắc nút Input.
        if (nutInput) put("handle_view_inputs", true)
        if (tenDuoiApp) put("always_show_names", true)
        if (!tenToTrenDock) put("dock_title", false)
        if (!toMauOApp) put("icon_tile_color", false)
        if (thuVienApp) { put("app_library", true); put("library_button", true) }
        if (dongHoKhiYen > 0) put("home_idle_clock_minutes", dongHoKhiYen)
        if (tiviYeu) put("reduced_motion", true)
        if (hieuUngKinh) { put("dock_glass", true); put("app_glow", true) }
        if (lamToiNen > 0) put("wallpaper_dim", lamToiNen)
        if (hienThongSo) put("show_stats", true)
        // Giữ OK trên ô app: menu Di chuyển / Thêm vào dock / Bỏ khỏi Home / Thông tin (upstream #464, #746; tắt mặc định
        // ở upstream). CleanTV bật sẵn vì người dùng xin đổi chỗ dock ngay trên tivi (22/10/2026); Nâng cao vẫn tắt được.
        put("tile_menu", true)
        // Giữa màn hình bỏ khoảng dời của giao diện (Kính mờ hạ dock 90dp) để hàng app nằm đúng giữa.
        if (doiHang == HANG_GIUA) { put("rows_middle", true); put("rows_offset_dp", 0) } else if (doiHang != 0) put("rows_offset_dp", doiHang)
        if (!nutDangPhat) put("now_playing", false)
        if (coChuDau != 100) put("header_scale", coChuDau)
        if (motHang) { put("dock_row", true); put("single_row", true) }
        if (anLoiChao) put("show_greeting", false)
        put("uniform_tiles", oDeuNhau)
        if (moKhiBat) put("open_on_start_choice", true)
        if (moKhiBat && moKhiThuc) put("wake_open_app", true)
        if (luonVeHome) put("power_on_home_always", true)
        if (chuTrang) put("header_white", true)
        // Một hàng + thư viện app: viên Thư viện chen vào hàng app, đã có nút trên thanh trên cùng (CleanTV 2.10.4).
        if (motHang && thuVienApp) put("show_library_pill", false)
        if (soCot > 0) { put("columns", soCot); put("vgs_so_cot", true) }
        // Phím APPS (KEYCODE_ALL_APPS) chỉ là nút Home với que / tivi Xiaomi hoặc khi bật "Remote gửi phím Home lạ";
        // tivi khác (Sony VH2: nút Apps mở Home sạch như Back, abc999roses 24/10/2026) để phím cho tivi.
        put("all_apps_home", nutHome || thongTin?.brand?.equals("Xiaomi", ignoreCase = true) == true)
        // Ảnh riêng làm nền: tắt ánh sáng sau dock theo màu app (ambient_glow), không thì ảnh gia đình bị ám đỏ / vàng /
        // xanh mỗi lần đổi ô (góp ý dangngoctri2010, 23/10/2026). Nâng cao ghi sau nên người dùng vẫn bật lại được.
        if (anhNenRieng != null) put("ambient_glow", false)
        // Ảnh riêng hiện đúng như ảnh (CleanTV 2.10.9: ảnh bị phủ mờ, loang màu): bỏ lớp phủ bầu trời theo giờ của giao
        // diện, giải mã màu 32-bit thay vì RGB_565. Nâng cao ghi sau nên vẫn đổi lại được.
        if (anhNenRieng != null) { put("sky_intensity", 0); put("wallpaper_true_color", true) }
        // Tivi Xiaomi bản Trung Quốc không có tiếng Việt: Home sạch hiện chữ Trung (CleanTV 2.10.9). Tivi đang để tiếng Trung
        // mà app dùng tiếng Việt thì ép Home sạch sang tiếng Việt (locale của launcher 3.0).
        if (vn.tivikit.NgonNgu.tiengViet && Regex("""\[(persist\.sys\.locale|ro\.product\.locale)\]: \[zh""").containsMatchIn(getprop)) put("locale", "vi")
        // Phím remote của mẫu (app_keys, settings_key_keycodes…), HomeKeyService đọc. LUÔN ghi app_keys, kể cả khi bật
        // nút Input: 2.6.5 bỏ app_keys lúc đó (InputKeyService cũng đọc, sợ mở app hai lần — upstream tcl-55c725) mà nút
        // Input tự bật trên mọi Google TV, nên TCL Google TV mất hẳn nút YouTube (góp ý heavensky95, TCL G10, 24/10/2026).
        // Mở cùng một app hai lần chỉ là đưa nó lên trước.
        // TCL Google TV không dùng nút app remote: thôi lọc phím để giữ âm lượng lặp được (Nâng cao đặt key_filter thì đè sau).
        val tclGoogleTv = thongTin?.brand?.equals("TCL", ignoreCase = true) == true && trangThai?.caiDat?.contains("com.google.android.apps.tv.launcherx") == true
        if ((tclGoogleTv && !dungPhimRemote) || giuAmLuong) put("key_filter", false)
        if (dungPhimRemote) phimRemote()?.let { ph ->
            ph.keys().forEach { k -> if (!has(k)) put(k, ph.get(k)) }
            // Nút Web của remote TCL (4159, upstream tcl-g10-google-tv): mở trình duyệt nếu tivi có.
            if (thongTin?.brand?.equals("TCL", ignoreCase = true) == true) {
                val web = TRINH_DUYET_TV.firstOrNull { trangThai?.caiDat?.contains(it) == true }
                val ds = optJSONArray("app_keys")
                if (web != null && ds != null && (0 until ds.length()).none { ds.optString(it).startsWith("4159:") }) ds.put("4159:$web")
            }
        }
        // Nâng cao ghi sau cùng: người dùng đã chủ động đặt thì đè lên giá trị theme.
        val daDat = dsNangCao.mapNotNull { tc -> nangCao[tc.khoa]?.let { chu -> vn.tivikit.dulieu.NangCao.giaTri(tc, chu)?.let { put(tc.khoa, it); tc.khoa } } }
        if (daDat.isNotEmpty()) put("vgs_nang_cao", JSONArray(daDat))
        // Nút gán riêng (màn Nút remote) sau cả Nâng cao: cùng nút thì nó thắng, và nó cần dịch vụ lọc phím.
        if (phimRieng.isNotEmpty()) vn.tivikit.dulieu.PhimRieng.apVao(this, phimRieng.toList())
        tenKenh.forEach { (goi, ten) -> vn.tivikit.dulieu.KenhNhanh.datTen(this, goi, ten) }
        anhOKenh.forEach { (goi, anh) -> vn.tivikit.dulieu.KenhNhanh.datAnh(this, goi, anh) }
    }.toString()

    /**
     * Màn Kênh nhanh vừa thêm / bỏ ô [goi] trong dock trên tivi: sửa luôn dock đang soạn ở màn Home sạch nếu đã nạp từ
     * tivi, để lần Áp dụng sau không ghi đè mất. Chưa nạp thì thôi: lần mở Home sạch sau đọc dock mới từ tivi.
     */
    internal fun dongBoDock(goi: String, co: Boolean) {
        if (!daNapCu && dock.isEmpty()) return
        if (co) { if (goi !in dock && dock.size < DOCK_TOI_DA) dock.add(goi) } else dock.remove(goi)
    }

    // ---------- Lưu / mở file cấu hình (góp ý 9000000, 23/10/2026) ----------
    var cauHinhBao by mutableStateOf("")

    /** Ghi mọi lựa chọn Home sạch (config.json + theme, nút Home, DNS, ảnh nền riêng) ra một file JSON người dùng chọn chỗ lưu. */
    fun xuatCauHinh(uri: android.net.Uri) = chay(t("Đang lưu file cấu hình…", "Saving the settings file…")) {
        val j = JSONObject(cauHinhHome())
        j.put("vgs_file", 1).put("vgs_theme", themeChon.id).put("vgs_nut_home", nutHome).put("vgs_dns", dnsChanQc)
        thongTin?.let { j.put("vgs_tivi", it.tenHienThi) }
        catLai?.join()
        anhNenRieng?.let { j.put("vgs_anh_nen", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
        io { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(j.toString(1).toByteArray()) } ?: error("không mở được file") }
        cauHinhBao = t("Đã lưu file cấu hình.", "Settings file saved.")
    }

    /**
     * Xem trước lệnh (24/10/2026, học cleanyourtv-app "Customise shows every command"): các lệnh chính app sẽ gửi lên
     * tivi, theo thứ tự, trước khi bấm. Dòng "#" là chú thích. Gói lõi Android / Home đang dùng app vẫn tự bỏ qua lúc chạy.
     */
    fun lenhDon(): List<String> = buildList {
        add(t("# 1. Sao lưu (chỉ đọc, không đổi gì)", "# 1. Backup (read-only, changes nothing)"))
        add("pm list packages -d")
        add("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        add("settings get secure enabled_accessibility_services")
        add("settings get global private_dns_mode")
        val ds = muc.filter { chon[it.pkg] == true }.map { it.pkg }
        add(t("# 2. Tắt ${ds.size} gói (chỉ tắt, không gỡ; Khôi phục bật lại được)", "# 2. Disable ${ds.size} packages (disable only, never uninstall; Restore turns them back on)"))
        ds.forEach { add("pm disable-user --user 0 $it") }
    }

    fun lenhHome(): List<String> = buildList {
        val pk = tv.pkgHome
        val goc = trangThai?.homeHienTai?.substringBefore('/').orEmpty()
        if (!daCoHomeSach) {
            add(t("# Cài Home sạch (APK đi kèm app)", "# Install the clean Home (APK bundled in the app)"))
            add("pm install -r ${tv.apkHome}")
            add("cmd package compile -m speed -f $pk")
        }
        add(t("# Ghi giao diện", "# Write the look"))
        add("content write --uri content://$pk.theme/config.json")
        if (!daCoHomeSach && goc.isNotBlank() && !goc.startsWith(pk)) {
            add(t("# Tắt Home gốc, đặt Home sạch làm Home (tivi tự canh gác: rớt kết nối là tự bật lại Home gốc)", "# Disable the stock Home and make the clean Home default (the TV watches: if the connection drops it restores the stock Home)"))
            add("pm disable-user --user 0 $goc")
        }
        add("cmd package set-home-activity --user 0 ${tv.cmpHome}")
        if (canDichVuPhim) add("settings put secure enabled_accessibility_services $pk/com.gathin.tvhome.HomeKeyService")
        if (nutInput) add("settings put secure enabled_accessibility_services $pk/com.gathin.tvhome.InputKeyService")
        if (dnsChanQc) add("settings put global private_dns_specifier dns.adguard-dns.com")
        add(t("# Thử nút Home thật", "# Test the real Home button"))
        add("input keyevent KEYCODE_HOME")
    }

    /** Lưu kịch bản khôi phục cho máy tính (dulieu/KichBanKhoiPhuc) từ bản sao lưu của tivi đang mở. */
    fun xuatKichBan(uri: android.net.Uri) = chay(t("Đang lưu kịch bản khôi phục…", "Saving the restore script…")) {
        val sl = saoLuu ?: error(t("Chưa có bản sao lưu của tivi này.", "No backup for this TV yet."))
        val chu = vn.tivikit.dulieu.KichBanKhoiPhuc.tao(sl, vn.tivikit.NgonNgu.tiengViet)
        io { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(chu.toByteArray()) } ?: error("không mở được file") }
        cauHinhBao = t("Đã lưu kịch bản khôi phục.", "Restore script saved.")
    }

    /** Mở file cấu hình đã lưu: điền lại mọi lựa chọn, bấm Áp dụng mới đưa lên tivi. */
    fun nhapCauHinh(uri: android.net.Uri) = chay(t("Đang mở file cấu hình…", "Opening the settings file…")) {
        val chu = io { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } ?: error(t("Không đọc được file.", "Couldn't read the file."))
        val j = runCatching { JSONObject(chu) }.getOrNull()?.takeIf { it.has("vgs_file") || it.has("dock_apps") }
            ?: error(t("File này không phải file cấu hình TiviKit.", "This isn't a TiviKit settings file."))
        napTuJson(j)
        vn.tivikit.dulieu.themeTheoId(j.optString("vgs_theme"))?.let { themeChon = it }
        if (j.has("vgs_nut_home")) nutHome = j.optBoolean("vgs_nut_home")
        if (j.has("vgs_dns")) dnsChanQc = j.optBoolean("vgs_dns")
        datAnhCoSan(j.optString("vgs_anh_nen").takeIf { it.isNotBlank() }?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull() })
        cauHinhBao = t("Đã mở file cấu hình", "Settings file loaded") + (j.optString("vgs_tivi").takeIf { it.isNotBlank() }?.let { t(" (của $it)", " (from $it)") } ?: "") +
            t(". Bấm \"Áp dụng lên tivi\" để đưa lên tivi.", ". Press \"Apply to the TV\" to send it.")
    }

    private suspend fun napAnhKenh() { tv.anhKenh = anhKenhChon() }

    /**
     * Ảnh các ô kênh VTVgo kiểu cũ (thumbnail của VTVgo, tải một lần rồi cất): (tên file sau "kenh-", ảnh). Launcher không
     * có mạng nên gửi kèm khi Áp dụng Home sạch.
     */
    private suspend fun anhKenhChon(): List<Pair<String, ByteArray>> {
        if (!coVtvGo || kenhChon.isEmpty()) return emptyList()
        val ds = dsKenhGo.ifEmpty { io { vn.tivikit.dulieu.KenhVtvGo.docNho(ctx).ifEmpty { vn.tivikit.dulieu.KenhVtvGo.tai(ctx) } } }
        return kenhChon.toList().mapNotNull { (ten, ma) ->
            io { vn.tivikit.dulieu.KenhVtvGo.anhO(ctx, ma, ds.firstOrNull { it.ma == ma }?.anh) }?.let { vn.tivikit.dulieu.KenhVtvGo.slug(ten) + ".jpg" to it }
        }
    }

    private suspend fun anhNenTheme(): ByteArray? {
        catLai?.join() // đang canh ảnh: đợi bản cắt theo khung mới nhất
        return anhNenRieng ?: themeChon.anhNen?.let { a -> io { ctx.assets.open(a).readBytes() } }
    }

    /** Ảnh nền người dùng chọn từ điện thoại (góp ý 23/10/2026), đã thu về 1920×1080 JPEG; null = theo theme. */
    var anhNenRieng by mutableStateOf<ByteArray?>(null)
        private set
    /**
     * Canh ảnh nền (25/10/2026, học LG TV Vibehack): ảnh gốc đã thu nhỏ (null = không có ảnh riêng) và khung cắt đang
     * chọn; anhNenRieng là phần ảnh gốc trong khung, cắt lại khi tay dừng kéo.
     */
    var anhGocRieng by mutableStateOf<ByteArray?>(null)
        private set
    var khungAnh by mutableStateOf(vn.tivikit.dulieu.AnhNen.Khung())
        private set
    /** Khung xem trước đang nhận kéo / chụm để canh ảnh. */
    var dangCanhAnh by mutableStateOf(false)
    private var catLai: kotlinx.coroutines.Job? = null

    /** Ảnh nền riêng có sẵn, đã đúng 16:9 (đọc từ tivi, file cấu hình); null = bỏ ảnh riêng, theo theme. */
    private fun datAnhCoSan(anh: ByteArray?) {
        catLai?.cancel()
        anhGocRieng = anh; khungAnh = vn.tivikit.dulieu.AnhNen.Khung(); anhNenRieng = anh; dangCanhAnh = false
    }

    /** Ảnh mới chọn: giữ ảnh gốc, cắt giữa trước, mở luôn chế độ canh ảnh (vừa khung thì không cần canh). */
    private suspend fun datAnhMoi(goc: ByteArray) {
        val vuaKhung = anhVuaKhung
        val anh = io { if (vuaKhung) vn.tivikit.dulieu.AnhNen.vuaKhung(goc) else vn.tivikit.dulieu.AnhNen.cat(goc, vn.tivikit.dulieu.AnhNen.Khung()) }
            ?: error(t("Không cắt được ảnh này, thử ảnh khác.", "Couldn't crop this picture, try another one."))
        catLai?.cancel()
        anhGocRieng = goc; khungAnh = vn.tivikit.dulieu.AnhNen.Khung(); anhNenRieng = anh; dangCanhAnh = !vuaKhung
    }

    fun boAnhRieng() = datAnhCoSan(null)

    /** Ảnh riêng vừa khung, không cắt: phần trống là chính ảnh làm mờ (CleanTV 3.0.0). Bật thì không canh ảnh. */
    var anhVuaKhung by mutableStateOf(false)
        private set

    /** Đổi giữa vừa khung và cắt theo khung cho ảnh đang chọn, không phải chọn lại ảnh. */
    fun doiVuaKhung(v: Boolean) = chay(t("Đang chuẩn bị ảnh…", "Preparing the picture…")) {
        anhVuaKhung = v
        if (v) dangCanhAnh = false
        val goc = anhGocRieng ?: return@chay
        catLai?.cancel()
        val k = khungAnh
        val anh = io { if (v) vn.tivikit.dulieu.AnhNen.vuaKhung(goc) else vn.tivikit.dulieu.AnhNen.cat(goc, k) }
            ?: error(t("Không xử lý được ảnh này, thử ảnh khác.", "Couldn't process this picture, try another one."))
        if (anhGocRieng === goc) anhNenRieng = anh
    }

    /** Kéo / chụm trên khung xem trước: đổi khung, cắt lại khi tay dừng 0,3 giây. */
    fun doiKhungAnh(k: vn.tivikit.dulieu.AnhNen.Khung) {
        if (anhVuaKhung) return
        val goc = anhGocRieng ?: return
        khungAnh = k
        catLai?.cancel()
        catLai = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            io { vn.tivikit.dulieu.AnhNen.cat(goc, k) }?.let { if (anhGocRieng === goc) anhNenRieng = it }
        }
    }

    /** Danh sách ảnh trên tivi / USB (null = chưa mở hộp chọn). */
    var anhTrenTv by mutableStateOf<List<String>?>(null)

    fun moAnhTrenTv() = chay(t("Đang tìm ảnh trên tivi…", "Looking for pictures on the TV…")) {
        anhTrenTv = io { tv.timAnhTrenTv() }
    }

    fun chonAnhTrenTv(duongDan: String) = chay(t("Đang đọc ảnh từ tivi…", "Reading the picture from the TV…")) {
        anhTrenTv = null
        val du = io { tv.docFileTv(duongDan) } ?: error(t("Không đọc được ảnh này (quá lớn hoặc hỏng), thử ảnh khác.", "Couldn't read this picture (too big or broken), try another one."))
        datAnhMoi(io { vn.tivikit.dulieu.AnhNen.gocByte(du) } ?: error(t("Không mở được ảnh này (HEIC thì đổi sang JPG), thử ảnh khác.", "Couldn't open this picture (convert HEIC to JPG), try another one.")))
    }

    fun chonAnhNen(uri: android.net.Uri) = chay(t("Đang chuẩn bị ảnh…", "Preparing the picture…")) {
        datAnhMoi(io { vn.tivikit.dulieu.AnhNen.goc(ctx, uri) } ?: error(t("Không đọc được ảnh này, thử ảnh khác.", "Couldn't read this picture, try another one.")))
    }

    /** Bộ ảnh xoay vòng của theme: mọi file ảnh trong thư mục assets boAnh. */
    private suspend fun boAnhTheme(): List<Pair<String, ByteArray>>? = if (anhNenRieng != null) null else themeChon.boAnh?.let { thu ->
        io { (ctx.assets.list(thu) ?: emptyArray()).sorted().map { it to ctx.assets.open("$thu/$it").readBytes() } }
    }

    /** Đổi theme / dock cho tivi đã có Home sạch. */
    fun apDungTheme() = chay(t("Đang đổi giao diện…", "Applying the theme…")) {
        val json = cauHinhHome()
        val anh = anhNenTheme()
        // Cài đè launcher đi kèm app (bản mới có dòng thời tiết…), giữ dữ liệu và dịch vụ trợ năng.
        thongBao = t("Đang cập nhật Home sạch…", "Updating the clean Home…")
        if (!io { tv.capNhatLauncher(vn.tivikit.CapNhat.moAsset(ctx, tv.apkHome).readBytes()) }) {
            loi = t("Không cập nhật được Home sạch trên tivi, giao diện mới sẽ không hiện đủ. Thử lại hoặc gửi góp ý kèm hãng, model.", "Couldn't update the clean Home on the TV, so the new look won't fully show. Try again or send feedback with the brand and model.")
            return@chay
        }
        if (anh != null) thongBao = t("Đang gửi ảnh nền (${anh.size / 1024} KB)…", "Sending the wallpaper (${anh.size / 1024} KB)…")
        val bo = boAnhTheme()
        if (bo != null) thongBao = t("Đang gửi ${bo.size} ảnh phong cảnh…", "Sending ${bo.size} landscape pictures…")
        if (coVtvGo && kenhChon.isNotEmpty()) thongBao = t("Đang lấy ảnh ${kenhChon.size} ô kênh…", "Fetching ${kenhChon.size} channel tile pictures…")
        napAnhKenh()
        io { tv.dayCauHinh(json, anh, bo) }
        if (dungPhimRemote || phimRieng.isNotEmpty() || (moKhiBat && moKhiThuc) || luonVeHome) io { tv.batBatPhimHome() }
        // Đổi giao diện cũng bật / tắt DNS AdGuard theo công tắc (trước 2.7.3 chỉ lúc cài Home sạch mới đặt).
        val dangChan = io { tv.dnsDangChan() }
        if (dnsChanQc && !dangChan) saoLuu?.let { sl -> batDnsAdGuardAnToan(sl) }
        if (!dnsChanQc && dangChan) io { tv.tatDnsChanQc(saoLuu) }
        if (nutDangPhat) io { tv.choPhepDangPhat() }
        if (nutManCho) io { tv.chuanBiManCho(saoLuu) }
        // Người dùng đang dùng launcher khác làm Home (Quản lý launcher, 27/10/2026): chỉ đổi giao diện Home sạch, không
        // giành lại Home — damBaoPhimHome sẽ coi launcher đó là app giành nút Home và tắt nó.
        homeKhac = io { tv.launcherKhacDangDung() }
        homeKhac?.let { g ->
            // Cài đè Home sạch làm nó nhảy lên trước: TiviKit chạy ngay trên tivi thì đưa app về lại (thuPhimHome cũng làm).
            veApp()
            ghi(t("Nút Home đang mở ${tenDep(g)} (launcher bạn chọn) nên app không đổi Home. Muốn Home sạch làm Home: vào Quản lý launcher.",
                "The Home button opens ${tenDep(g)} (the launcher you chose), so Home was left alone. To make the clean Home the Home, use Launchers."))
        } ?: run {
            thongBao = t("Đang thử nút Home trên tivi…", "Testing the TV's Home button…")
            val sl = saoLuu ?: thongTin?.let { tt -> runCatching { io { tv.damBaoSaoLuu(tt, getprop, ip) } }.getOrNull() }?.also { saoLuu = it }
            thuPhimHome(sl)
        }
        vn.tivikit.Dem.gui(ctx, "home", tenTiviDem(), dock.size, "doi:${themeChon.id}")
        if (ghimMayChieu.isNotEmpty()) vn.tivikit.Dem.gui(ctx, "may-chieu", tenTiviDem(), ghimMayChieu.size, ghimMayChieu.keys.joinToString(",").take(110))
        homeXong = true
        man = Man.XONG
    }

    /**
     * Nút Khởi động lại ở thẻ tivi (màn chính, 25/10/2026): khởi động lại rồi chờ tivi lên và tự nối lại đúng địa chỉ cũ.
     * Chỉ tự nối được với ADB qua mạng cổng cố định (5555); Gỡ lỗi không dây đổi cổng sau khởi động nên báo nối lại bằng
     * tay. TiviKit chạy ngay trên tivi thì tắt theo tivi.
     */
    /** Khởi động lại tivi, đợi tivi lên rồi tự nối lại; nối được thì làm tiếp [sau] (VD mở lại màn Làm mới tivi). */
    fun khoiDongLaiVaNoiLai(sau: (() -> Unit)? = null) = chay(t("Đang khởi động lại tivi…", "Rebooting the TV…")) {
        val host = ip.trim()
        val port = cong.trim().toIntOrNull() ?: 5555
        io { runCatching { adb.shell("reboot") } }
        runCatching { io { adb.disconnect() } }
        datLai()
        sauKetNoi = sau
        if (port != 5555) {
            loi = t("Tivi đang khởi động lại. Gỡ lỗi không dây đổi cổng sau mỗi lần khởi động: khi tivi lên, bấm Kết nối tivi và nhập cổng mới.",
                "The TV is rebooting. Wireless debugging changes its port on every boot: when the TV is back, tap Connect a TV and enter the new port.")
            return@chay
        }
        thongBao = t("Tivi đang khởi động lại, đợi tivi lên rồi app tự nối lại (1–3 phút)…", "The TV is rebooting; the app reconnects once it's back (1–3 minutes)…")
        // Cổng còn mở vài giây đầu (tivi đang tắt dần): đợi tivi tắt hẳn rồi mới dò.
        kotlinx.coroutines.delay(20_000)
        val het = System.currentTimeMillis() + 180_000
        var noi = false
        while (!noi && System.currentTimeMillis() < het) {
            noi = io { moCong(host, port, 1500) && runCatching { adb.connect(host, port) }.getOrDefault(false) }
            if (!noi) kotlinx.coroutines.delay(5_000)
        }
        if (!noi) {
            loi = t("Tivi chưa lên lại sau 3 phút (hoặc đã tắt ADB qua mạng). Khi tivi lên, bấm Kết nối tivi.",
                "The TV isn't back after 3 minutes (or network ADB is off). When it's up, tap Connect a TV.")
            return@chay
        }
        sauKhiKetNoi(host)
    }

    fun khoiDongLaiTv() = chay(t("Đang khởi động lại tivi…", "Rebooting the TV…")) {
        io { runCatching { adb.shell("reboot") } }
        runCatching { io { adb.disconnect() } }
        thongBao = ""
        loi = t("Tivi đang khởi động lại. Đợi tivi lên rồi bấm Kết nối lại để kiểm tra (gói nào tự bật lại sau khởi động sẽ hiện ra).", "The TV is rebooting. When it is back, press Connect again to check (packages that re-enable at boot will show up).")
        man = Man.KET_NOI
    }

    /**
     * Tự tắt ADB khi xong (24/10/2026, học cleanyourtv-app): đang ở màn Xong mà người dùng rời app thì hẹn TIVI tự tắt
     * ADB sau 60 giây (hẹn giờ phải nằm trên tivi: Android 14 đóng băng app nằm nền). Quay lại trước đó thì huỷ; quá
     * hạn thì về màn chính, báo đã tự tắt. Bật "Giữ ADB bật cho lần sau" thì không hẹn.
     */
    var giuAdb by mutableStateOf(false)
    var daTuTatAdb by mutableStateOf(false)
    /** Vừa tắt ADB bằng nút (thẻ tivi ở màn chính, màn Xong): màn chính báo đã tắt. */
    var daTatAdb by mutableStateOf(false)
    private var daHenTatAdb = false

    fun henTuTatAdb() {
        if (giuAdb || man != Man.XONG || dangBan) return
        daHenTatAdb = true
        viewModelScope.launch { runCatching { io { tv.henTatAdbTrenTv(60) } }.onFailure { daHenTatAdb = false } }
    }

    fun huyTuTatAdb() {
        if (!daHenTatAdb) return
        daHenTatAdb = false
        viewModelScope.launch {
            // Huỷ không được (tivi đã tắt ADB, kết nối mất): coi như đã tự tắt.
            if (runCatching { io { tv.huyTatAdbTrenTv() } }.isFailure) {
                giuKetNoi.boDich()
                runCatching { io { adb.disconnect() } }
                datLai()
                daTuTatAdb = true
                man = Man.CHINH
            }
        }
    }

    fun tatAdbVaKetThuc() = chay(t("Đang tắt ADB trên tivi…", "Turning ADB off on the TV…")) {
        giuKetNoi.boDich()
        io { tv.tatAdb() }
        runCatching { io { adb.disconnect() } }
        val host = ip
        val conMo = io { Thread.sleep(2500); moCong(host, 5555, 1500) }
        loi = if (conMo) t("Cổng 5555 vẫn mở. Vào Tuỳ chọn nhà phát triển trên tivi, tắt Gỡ lỗi USB / Gỡ lỗi qua mạng rồi khởi động lại tivi.", "Port 5555 is still open. In Developer options on the TV, turn off USB / Network debugging and reboot.")
        else ""
        thongBao = ""
        datLai()
        daTatAdb = !conMo
        man = Man.CHINH
    }

    // ---------- Khôi phục ----------

    fun moKhoiPhuc(b: BanSaoLuu) {
        dangKhoiPhuc = true
        ip = b.ip
        man = Man.KET_NOI
    }

    fun chayKhoiPhuc(goLauncher: Boolean) = chay(t("Đang khôi phục…", "Restoring…")) {
        val info = thongTin ?: return@chay
        val sl = kho.doc(info.khoaLuu) ?: error(t("Tivi này chưa có bản sao lưu trong máy.", "This phone has no backup for this TV."))
        nhatKy.clear()
        val loiKp = io { tv.khoiPhuc(sl, goLauncher) { s -> ghiTuIo(s) } }
        veApp()
        vn.tivikit.Dem.gui(ctx, "khoi-phuc", tenTiviDem(), sl.daTatBoiApp.size)
        loiKp.forEach { ghi(t("Lỗi: $it", "Error: $it")) }
        if (loiKp.isEmpty() && sl.daTatBoiApp.isEmpty() && !sl.launcherDaCai && !sl.dnsDaDoi && !sl.homeDaDoi) {
            kho.xoa(sl.khoa)
            io { tv.xoaSaoLuuTrenTv() }
            khoiPhucXong = true
            ghi(t("Đã khôi phục xong, xoá bản sao lưu.", "Restore complete, backup removed."))
        } else ghi(t("Khôi phục xong một phần, bản sao lưu vẫn giữ để chạy lại.", "Partly restored; the backup is kept so you can run it again."))
        saoLuu = kho.doc(info.khoaLuu)
        taiLaiSaoLuu()
    }

    /**
     * Fire TV / Fire OS (23/10/2026, Fire TV Cube 2 AFTR): Amazon giữ Home cho launcher của họ — đặt Home bị bỏ qua,
     * launcher là gói được bảo vệ không tắt được, dịch vụ trợ năng không nhận phím Home (upstream TROUBLESHOOTING
     * "Fire TV"). Chỉ dọn được, không thay Home.
     */
    val laFireTv: Boolean get() = (thongTin?.model?.startsWith("AFT") == true) ||
        trangThai?.caiDat?.contains("com.amazon.tv.launcher") == true

    /** Android 7 trở xuống (SDK < 26): Home sạch cần Android 8+ (23/10/2026, hộp MXQ Pro Android 7.1). Chỉ dọn được. */
    val laAndroidCu: Boolean get() = thongTin?.sdk?.let { it in 1..25 } == true

    /** Không cài được Home sạch trên tivi này: chỉ dọn. */
    val khongCaiHome: Boolean get() = laFireTv || laAndroidCu

    /** Tivi có Home sạch (bản nào cũng được): đã từng dọn bằng CleanTV / CleanYourTV. */
    val coDauCleanTv: Boolean get() = trangThai?.let { tt -> ThaoTacTv.HOME_SACH.any { it in tt.caiDat } } == true

    /** Gói đang tắt trên tivi khi không có bản sao lưu (null = chưa đọc). */
    var goiTatKhongSaoLuu by mutableStateOf<List<String>?>(null)

    fun docGoiTatKhongSaoLuu() = chay(t("Đang đọc gói đang tắt…", "Reading disabled packages…")) {
        goiTatKhongSaoLuu = io { tv.goiTatBoiNguoiDung() }
    }

    // ---------- Bật lại từng app (issue vgs-cleantv#3, 23/10/2026) ----------
    /** Gói đang tắt kiểu disable-user, đọc thẳng từ tivi (không cần bản sao lưu); null = chưa đọc. */
    var dsTatTv by mutableStateOf<List<String>?>(null)
    val chonBatLai = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    var batLaiBao by mutableStateOf("")

    fun docDsTat() = chay(t("Đang đọc app đang tắt trên tivi…", "Reading disabled apps on the TV…")) {
        dsTatTv = io { tv.goiTatBoiNguoiDung() }; chonBatLai.clear(); batLaiBao = ""
    }

    fun batLaiDaChon() = chay(t("Đang bật lại…", "Re-enabling…")) {
        val ds = chonBatLai.filterValues { it }.keys.sorted()
        val sl = saoLuu
        var ok = 0
        ds.forEachIndexed { i, p ->
            thongBao = t("Bật lại ${i + 1}/${ds.size}: $p", "Re-enabling ${i + 1}/${ds.size}: $p")
            if (io { tv.batLaiGoi(p, sl) }) ok++
        }
        dsTatTv = io { tv.goiTatBoiNguoiDung() }; chonBatLai.clear()
        batLaiBao = if (ok == ds.size) t("Đã bật lại $ok app.", "Re-enabled $ok apps.") else t("Bật lại được $ok/${ds.size} app; app còn lại tivi không cho bật.", "Re-enabled $ok/${ds.size}; the TV refused the rest.")
        vn.tivikit.Dem.gui(ctx, "khoi-phuc", tenTiviDem(), ok, "tung-app")
        if (sl != null) saoLuu = sl
    }

    fun chayKhoiPhucKhongSaoLuu(goLauncher: Boolean) = chay(t("Đang khôi phục…", "Restoring…")) {
        nhatKy.clear()
        val loiKp = io { tv.khoiPhucKhongSaoLuu(goLauncher) { s -> ghiTuIo(s) } }
        veApp()
        vn.tivikit.Dem.gui(ctx, "khoi-phuc", tenTiviDem(), goiTatKhongSaoLuu?.size ?: 0, "khong-sao-luu")
        loiKp.forEach { ghi(t("Lỗi: $it", "Error: $it")) }
        if (loiKp.isEmpty()) khoiPhucXong = true
        else ghi(t("Khôi phục xong một phần, chạy lại để thử tiếp.", "Partly restored; run it again to retry."))
        goiTatKhongSaoLuu = null
    }

    fun datLai() {
        thongTin = null; trangThai = null; muc = emptyList(); chon.clear(); ketQua = null
        dock.clear(); tenChao = ""; homeXong = false; dnsChanQc = false; dangKhoiPhuc = false; khoiPhucXong = false; anhTv = null
        laMayChieu = false; mayChieu = null; ghimMayChieu.clear()
        nhatKy.clear(); saoLuu = null; saoLuuTuTv = false; goiTatKhongSaoLuu = null; epSetupwraith = false
        dsTatTv = null; chonBatLai.clear(); batLaiBao = ""
        dsCai.clear(); apkTrenTv = null; abiTvDaDo = null; GoiApk.donDep(ctx); qlApp.quen(); guiFile.quen()
        phimRieng.clear(); ganPhim.quen(); tenKenh.clear(); anhOKenh.clear(); kenhNhanh.quen(); henGio.quen(); lamMoi.quen(); mang.quen(); caiDatNhanh.quen(); capNhatApp.quen()
        dongLenh.quen(); lienKet.quen(); qlLauncher.quen()
        xemManHinh.tat(); kenhDieuKhien.dong(); dangPhat.dong()
        dangDon = false; sauKetNoi = null
        taiLaiSaoLuu()
    }

    fun veManChinh() {
        viewModelScope.launch { runCatching { io { adb.disconnect() } } }
        datLai(); loi = ""; man = Man.CHINH
    }

    /** Nút Ngắt kết nối: ngắt và thôi tự nối lại tivi này. */
    fun ngatKetNoi() { giuKetNoi.boDich(); veManChinh() }

    companion object {
        val UU_TIEN_DOCK = listOf(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.fptplay.tv", "net.fptplay.ottbox",
            "com.vieon.tv", "vn.vieon.tv", "com.viettel.tv360", "com.vnpt.mytv", "com.galaxyplay.tv",
            "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.spotify.tv.android",
            "com.android.vending",
        )
    }
}
