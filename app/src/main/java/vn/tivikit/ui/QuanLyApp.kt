package vn.tivikit.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vn.tivikit.adb.AppTrenTv
import vn.tivikit.adb.AppTv
import vn.tivikit.adb.BoNho
import vn.tivikit.adb.CoDuLieu
import vn.tivikit.adb.ThaoTacTv
import vn.tivikit.dulieu.ChayNgam
import vn.tivikit.dulieu.KeHoach
import vn.tivikit.dulieu.TenApk
import vn.tivikit.t
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Quản lý app trên tivi (25/10/2026): danh sách app kèm tên thật / ảnh (đọc dần ở nền, nhớ theo phiên bản), bộ nhớ,
 * mở / buộc dừng / xoá cache / xoá dữ liệu / gỡ, dọn cache cả máy, lưu APK về điện thoại.
 */
class QuanLyApp(private val vm: PhienLamViec) {
    class App(val tv: AppTv, val heThong: Boolean, dangTat: Boolean, duLieu: CoDuLieu?) {
        var dangTat by mutableStateOf(dangTat)
        /** null = chưa đọc; "" = không đọc được (hiện tên gói). */
        var ten by mutableStateOf<String?>(null)
        var anh by mutableStateOf<ImageBitmap?>(null)
        var duLieu by mutableStateOf(duLieu)
        val tenHien: String get() = ten?.takeIf { it.isNotEmpty() } ?: tv.pkg
    }

    val ds = mutableStateListOf<App>()
    var boNho by mutableStateOf<BoNho?>(null)
    var hienHeThong by mutableStateOf(false)
    var tim by mutableStateOf("")
    /** Gói đang mở hàng thao tác. */
    var dangMo by mutableStateOf<String?>(null)
    var bao by mutableStateOf("")
    /** Màn mở hộp "Lưu file" với (tên gợi ý, nhiều phần?) rồi đặt lại null. */
    var yeuCauLuu by mutableStateOf<Pair<String, Boolean>?>(null)
    /** App đang chạy ngầm (RAM, nhiều trước) và mọi gói đã cài, đọc cùng danh sách (26/10/2026). */
    var chayNgam by mutableStateOf<List<ChayNgam.App>>(emptyList())
        private set
    var goiDaCai by mutableStateOf<Set<String>>(emptySet())
        private set

    private var docTen: Job? = null
    private var dangLuu: Pair<App, List<Pair<String, Long>>>? = null
    @Volatile private var phanTramCu = -1
    private val tvApp by lazy { AppTrenTv(vm.adb) }
    private val kho by lazy { vm.ctx.getSharedPreferences("ten-app", Context.MODE_PRIVATE) }
    private val thuMucAnh by lazy { File(vm.ctx.filesDir, "anh-app").apply { mkdirs() } }

    /**
     * Gói của chính app này (Home sạch, Khoá trẻ em — cả bản trước khi đổi tên gói, đang chờ đặt lại PIN — Kênh nhanh,
     * Hẹn giờ, bộ gõ tiếng Việt, TiviKit khi chạy trên tivi): không gỡ / xoá dữ liệu ở đây.
     */
    fun baoVe(a: App) = a.tv.pkg in ThaoTacTv.HOME_SACH || a.tv.pkg == ThaoTacTv.PKG_KHOA || a.tv.pkg == vn.tivikit.dulieu.GoiCu.KHOA ||
        vn.tivikit.dulieu.KenhNhanh.laGoiKenh(a.tv.pkg) || a.tv.pkg == vn.tivikit.dulieu.HenGio.GOI || a.tv.pkg == vn.tivikit.dulieu.GoChu.GOI ||
        a.tv.pkg == vm.ctx.packageName

    fun mo() { vm.man = Man.QUAN_LY_APP; bao = ""; if (ds.isEmpty()) tai() }

    /** Kết nối tivi khác / làm lại từ đầu. */
    fun quen() { docTen?.cancel(); ds.clear(); boNho = null; dangMo = null; bao = ""; dangLuu = null; chayNgam = emptyList(); goiDaCai = emptySet() }

    /** App trong danh sách, hoặc một bản tối thiểu cho gói hệ thống chưa hiện (để tắt từ thẻ Đang chạy ngầm). */
    fun appCua(pkg: String): App = ds.firstOrNull { it.tv.pkg == pkg } ?: App(AppTv(pkg, "", 0, 0), heThong = true, dangTat = false, duLieu = null)

    fun tai() = vm.chay(t("Đang đọc danh sách app trên tivi…", "Reading the apps on the TV…")) {
        vm.damBaoKetNoi()
        docTen?.cancel()
        bao = ""
        boNho = vm.io { tvApp.boNho() }
        val nguoiDung = vm.io { tvApp.goiNguoiDung() }
        val tt = vm.trangThai
        val tat = vm.io { tvApp.goiDangTat() }
        // App hệ thống: có biểu tượng (gói đang tắt không còn trong danh sách biểu tượng) + gói người dùng / luồng dọn đã
        // tắt, để tắt YouTube gốc xong vẫn thấy nó ở đây mà bật lại.
        val heThong = if (hienHeThong) ((tt?.coBieuTuong ?: emptySet()) + vm.io { vm.tv.goiTatBoiNguoiDung() }) - nguoiDung else emptySet()
        val apps = vm.io { tvApp.danhSach(nguoiDung + heThong) }
        val du = runCatching { vm.io { tvApp.coDuLieu() } }.getOrDefault(emptyMap())
        ds.clear()
        ds += apps.map { App(it, it.pkg !in nguoiDung, it.pkg in tat, du[it.pkg]).also { a -> napTuKho(a) } }
        runCatching { vm.io { tvApp.chayNgam() } }.getOrNull()?.let { (cn, goi) -> chayNgam = cn; goiDaCai = goi }
        docTen = vm.viewModelScope.launch { docTenAnh() }
    }

    private fun khoa(a: App) = "${a.tv.pkg}:${a.tv.versionCode}"
    private fun fileAnh(a: App) = File(thuMucAnh, "${a.tv.pkg}_${a.tv.versionCode}.png")

    private fun napTuKho(a: App) {
        a.ten = kho.getString(khoa(a), null)
        fileAnh(a).takeIf { it.isFile }?.let { f -> BitmapFactory.decodeFile(f.path)?.let { a.anh = it.asImageBitmap() } }
    }

    /**
     * Đọc tên + ảnh các app chưa có trong kho ở nền (màn vẫn bấm được). Trước hết hỏi thẳng tivi (TrenTv, 27/10/2026): tên cả
     * danh sách một lệnh, ảnh 12 app một lệnh. App nào tivi không trả thì đọc APK như trước (TenApk), từng app một. Chỉ nhớ
     * khi đọc được; mất kết nối thì dừng, lần mở sau đọc tiếp.
     */
    private suspend fun docTenAnh() {
        val chua = ds.filter { it.ten == null }
        if (chua.isEmpty()) return
        try {
            val ten = vm.docTenTrenTv()
            val tren = vn.tivikit.adb.TrenTv(vm.adb)
            for (lo in chua.chunked(12)) {
                val anh = vm.io { tren.anhApp({ vn.tivikit.CapNhat.moAsset(vm.ctx, vn.tivikit.adb.TrenTv.ASSET).readBytes() }, lo.map { it.tv.pkg }) }
                for (a in lo) {
                    val t = ten[a.tv.pkg]?.ten?.takeIf { it.isNotBlank() } ?: continue
                    val bm = anh[a.tv.pkg]?.webp?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    a.ten = t
                    bm?.let { a.anh = it.asImageBitmap() }
                    luuKho(a, t, bm)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (ThaoTacTv.laMatKetNoi(e)) return
        }
        for (a in ds.toList()) {
            if (a.ten != null) continue
            val kq: TenApk.KetQua? = try {
                vm.io {
                    val fs = tvApp.fileApk(a.tv.pkg)
                    val f = fs.firstOrNull { it.first.endsWith("/base.apk") } ?: fs.firstOrNull() ?: return@io null
                    TenApk.doc(vm.ctx, f.second, tvApp.docDoan(f.first, f.second))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (ThaoTacTv.laMatKetNoi(e)) return
                null
            }
            a.ten = kq?.ten?.takeIf { it.isNotBlank() } ?: ""
            kq?.anh?.let { a.anh = it.asImageBitmap() }
            if (!kq?.ten.isNullOrBlank()) luuKho(a, kq!!.ten!!, kq.anh)
        }
    }

    private suspend fun luuKho(a: App, ten: String, anh: Bitmap?) {
        kho.edit().putString(khoa(a), ten).apply()
        if (anh != null) vm.io { runCatching { fileAnh(a).outputStream().use { anh.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
    }

    fun moApp(a: App) = vm.chay(t("Đang mở ${a.tenHien}…", "Opening ${a.tenHien}…")) {
        vm.damBaoKetNoi()
        if (!vm.io { vm.tv.moApp(a.tv.pkg) }) error(t("${a.tenHien} không có màn mở trên tivi (dịch vụ / bàn phím chạy ngầm).",
            "${a.tenHien} has no launch screen on the TV (a background service or keyboard)."))
    }

    fun dung(a: App) = vm.chay(t("Đang buộc dừng ${a.tenHien}…", "Force-stopping ${a.tenHien}…")) {
        vm.damBaoKetNoi()
        vm.io { tvApp.dung(a.tv.pkg) }
        bao = t("Đã buộc dừng ${a.tenHien}.", "${a.tenHien} force-stopped.")
    }

    fun xoaCache(a: App) = vm.chay(t("Đang xoá cache ${a.tenHien}…", "Clearing ${a.tenHien}'s cache…")) {
        vm.damBaoKetNoi()
        bao = if (vm.io { tvApp.xoaCache(a.tv.pkg) }) { capNhatBoNho(); t("Đã xoá cache ${a.tenHien}.", "${a.tenHien}'s cache cleared.") }
            else t("Tivi này chưa xoá được cache riêng từng app (cần Android 12). Dùng nút \"Dọn cache tất cả app\" ở trên.",
                "This TV can't clear one app's cache (needs Android 12). Use \"Clear all app caches\" above.")
    }

    fun xoaDuLieu(a: App) = vm.chay(t("Đang xoá dữ liệu ${a.tenHien}…", "Clearing ${a.tenHien}'s data…")) {
        vm.damBaoKetNoi()
        if (!vm.io { tvApp.xoaDuLieu(a.tv.pkg) }) error(t("Tivi không cho xoá dữ liệu ${a.tenHien}.", "The TV refused to clear ${a.tenHien}'s data."))
        capNhatBoNho()
        bao = t("Đã xoá dữ liệu ${a.tenHien}: app như mới cài.", "${a.tenHien}'s data cleared: it's like freshly installed.")
    }

    /** Tắt được ở màn này: không phải app của chính TiviKit, gói lõi Android hay app tivi cần để chạy (KeHoach). */
    fun duocTat(a: App): Boolean = !baoVe(a) && !KeHoach.laGoiLoiAndroid(a.tv.pkg) &&
        vm.trangThai?.let { KeHoach.laGoiCanGiu(a.tv.pkg, it) } != true

    /**
     * Tắt app (disable-user, 25/10/2026 — VD tắt YouTube gốc khi đã dùng SmartTube). Đi qua tatGoi như luồng dọn: đủ các
     * lớp bảo vệ (Home, Wi-Fi, cổng HDMI…) và ghi vào bản sao lưu, Khôi phục tivi bật lại. Dữ liệu app giữ nguyên.
     */
    fun tatApp(a: App) = vm.chay(t("Đang tắt ${a.tenHien}…", "Disabling ${a.tenHien}…")) {
        vm.damBaoKetNoi()
        val info = vm.thongTin ?: return@chay
        if (!duocTat(a)) error(t("Tivi cần ${a.tenHien} để chạy, không tắt ở đây.", "The TV needs ${a.tenHien}; it can't be disabled here."))
        val sl = vm.saoLuu ?: vm.io { vm.tv.damBaoSaoLuu(info, vm.getprop, vm.ip.trim()) }.also { vm.saoLuu = it }
        val kq = vm.io { vm.tv.tatGoi(listOf(a.tv.pkg), sl, epSetupwraith = false) {} }
        (kq.boQua + kq.loi).firstOrNull()?.let { error(it.second) }
        if (a.tv.pkg !in kq.daTat) error(t("Tivi không cho tắt ${a.tenHien}.", "The TV refused to disable ${a.tenHien}."))
        a.dangTat = true
        chayNgam = chayNgam.filter { it.pkg != a.tv.pkg }
        runCatching { vm.trangThai = vm.io { vm.tv.docTrangThai() } }
        bao = t("Đã tắt ${a.tenHien}: app ẩn khỏi tivi và không chạy, dữ liệu vẫn giữ. Bật lại ở đây hoặc bằng Khôi phục tivi.",
            "${a.tenHien} disabled: hidden from the TV and not running, data kept. Re-enable it here or by restoring the TV.")
    }

    fun batLaiApp(a: App) = vm.chay(t("Đang bật lại ${a.tenHien}…", "Re-enabling ${a.tenHien}…")) {
        vm.damBaoKetNoi()
        if (!vm.io { vm.tv.batLaiGoi(a.tv.pkg, vm.saoLuu) }) error(t("Tivi không bật lại được ${a.tenHien}.", "The TV couldn't re-enable ${a.tenHien}."))
        a.dangTat = false
        runCatching { vm.trangThai = vm.io { vm.tv.docTrangThai() } }
        bao = t("Đã bật lại ${a.tenHien}.", "${a.tenHien} re-enabled.")
    }

    fun go(a: App) = vm.chay(t("Đang gỡ ${a.tenHien}…", "Uninstalling ${a.tenHien}…")) {
        vm.damBaoKetNoi()
        val out = vm.io { vm.tv.goApp(a.tv.pkg) }
        if (!out.contains("Success")) error(t("Gỡ không được: ${out.trim()}", "Uninstall failed: ${out.trim()}"))
        ds.remove(a)
        capNhatBoNho()
        runCatching { vm.trangThai = vm.io { vm.tv.docTrangThai() } }
        bao = t("Đã gỡ ${a.tenHien}.", "${a.tenHien} uninstalled.")
    }

    fun donCache() = vm.chay(t("Đang dọn cache mọi app trên tivi…", "Clearing every app's cache on the TV…")) {
        vm.damBaoKetNoi()
        val truoc = vm.io { tvApp.boNho() }
        vm.io { tvApp.donCacheTatCa() }
        val sau = vm.io { tvApp.boNho() }
        boNho = sau
        val them = if (truoc != null && sau != null) (sau.conKb - truoc.conKb).coerceAtLeast(0) * 1024 else 0
        bao = t("Đã dọn cache, tivi trống thêm ${coChu(them)}.", "Caches cleared, ${coChu(them)} freed.")
    }

    private suspend fun capNhatBoNho() { boNho = runCatching { vm.io { tvApp.boNho() } }.getOrNull() ?: boNho }

    /** Bước 1 của Lưu APK: hỏi tivi app có mấy file, rồi để màn mở hộp lưu với tên gợi ý. */
    fun chuanBiLuu(a: App) = vm.chay(t("Đang xem file của ${a.tenHien}…", "Looking at ${a.tenHien}'s files…")) {
        vm.damBaoKetNoi()
        val fs = vm.io { tvApp.fileApk(a.tv.pkg) }
        if (fs.isEmpty()) error(t("Không thấy file APK của ${a.tenHien}.", "Couldn't find ${a.tenHien}'s APK."))
        dangLuu = a to fs
        val ten = (a.tenHien + "_" + a.tv.versionName).replace(Regex("""[\\/:*?"<>|\s]+"""), "_").take(80)
        yeuCauLuu = (if (fs.size == 1) "$ten.apk" else "$ten.apks") to (fs.size > 1)
    }

    /**
     * Bước 2: tải về [uri] người dùng chọn. Một file thì chép nguyên; nhiều phần (gói chia nhỏ) thì gói thành .apks (zip
     * base.apk + split_*.apk) — màn Cài app cài lại được.
     */
    fun luu(uri: Uri) {
        val (a, fs) = dangLuu ?: return
        val tong = fs.sumOf { it.second }
        val viec = t("Đang tải ${a.tenHien} về điện thoại…", "Downloading ${a.tenHien} to the phone…")
        vm.chay(viec) {
            vm.damBaoKetNoi()
            phanTramCu = -1
            try {
                vm.io {
                    val ra = vm.ctx.contentResolver.openOutputStream(uri) ?: error(t("Không ghi được file.", "Couldn't write the file."))
                    ra.use { o ->
                        if (fs.size == 1) tvApp.taiVe(fs[0].first, fs[0].second, o) { n -> baoTienDo(viec, n, tong) }
                        else ZipOutputStream(o).use { z ->
                            z.setLevel(0) // APK đã nén sẵn: nén lại chỉ tốn thời gian
                            var truoc = 0L
                            for ((p, co) in fs) {
                                z.putNextEntry(ZipEntry(p.substringAfterLast('/')))
                                tvApp.taiVe(p, co, z) { n -> baoTienDo(viec, truoc + n, tong) }
                                z.closeEntry()
                                truoc += co
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                runCatching { android.provider.DocumentsContract.deleteDocument(vm.ctx.contentResolver, uri) }
                throw e
            }
            dangLuu = null
            bao = t("Đã lưu ${a.tenHien} (${coChu(tong)}) vào điện thoại.", "${a.tenHien} (${coChu(tong)}) saved to the phone.")
        }
    }

    fun huyLuu() { dangLuu = null }

    private fun baoTienDo(viec: String, da: Long, tong: Long) {
        val pt = if (tong > 0) (da * 100 / tong).toInt() else 0
        if (pt == phanTramCu) return
        phanTramCu = pt
        vm.viewModelScope.launch(Dispatchers.Main) { vm.thongBao = "$viec $pt%" }
    }
}
