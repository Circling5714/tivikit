package vn.tivikit.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Phần chạy trên tivi của màn Home: đọc app, ảnh nền, cấu hình, thông số mỗi giây, và làm các việc màn Home nhờ. */
class DieuKhien(private val act: ComponentActivity, private val tt: TrangThaiHome) : HanhDong {
    private val ctx: Context = act.applicationContext
    private val pm = ctx.packageManager
    private val ch = CauHinh(ctx)
    private val scope = act.lifecycleScope
    private var nhip: Job? = null
    private var tatBao: Job? = null
    /** Dock đã lưu; null = chưa có (lần đầu), sẽ lấy dock mặc định. */
    private var dockLuu: List<String>? = null
    private var lichSu: List<String> = emptyList()
    /** App mở gần đây theo thống kê sử dụng (rỗng = chưa có quyền) và các gói launcher (không đưa vào app gần đây). */
    private var suDung: List<String> = emptyList()
    private var goiHome: Set<String> = emptySet()
    /** App đọc từ hệ thống (tên của app), và tên riêng TiviKit đặt (ten_app: tên kênh của các ô Kênh nhanh). */
    private var dsGoc: List<AppTv> = emptyList()
    private var tenRieng: Map<String, String> = emptyMap()
    private val dangTai = HashSet<String>()
    /** Cỡ giải mã banner / biểu tượng theo màn hình: box 720p không cần ảnh cỡ 1080p (ít RAM hơn). */
    private var rongBanner = 256
    private var coBieuTuong = 96
    private var noi: ThoiTiet.Noi? = null
    /** Lần lấy thời tiết gần nhất (giờ hệ thống); 0 = chưa lấy. */
    private var lucThoiTiet = 0L
    private var dangLayThoiTiet = false
    /** Phiên phát của thẻ Đang phát (mở app, tạm dừng / phát tiếp). */
    private var phien: MediaController? = null
    /** false = chưa được cho phép đọc phiên phát (thử lại mỗi lần màn Home hiện ra: TiviKit có thể vừa cấp quyền). */
    @Volatile private var docDuocDangPhat = true
    /** Câu kinh đang hiện ứng với ngày / tên bộ / file bộ riêng nào (khác thì đọc lại), và ngày đã xét. */
    private var khoaCau: String? = null
    private var ngayCau: LocalDate? = null

    /** Khoá trẻ em ngay (menu ⏻): app Khoá trẻ em khoá tivi rồi trả kết quả; không khoá được thì báo vì sao. */
    private val khoaNgay = act.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) return@registerForActivityResult
        bao(when (r.data?.getStringExtra("loi")) {
            "chua-co-ma" -> t("Khoá trẻ em chưa có mã: cài mã bằng TiviKit trước", "The Kids lock has no code yet: set one up in TiviKit first")
            "dich-vu" -> t("Dịch vụ Khoá trẻ em đang tắt: mở Khoá trẻ em trong TiviKit để bật lại", "The Kids lock service is off: open Kids lock in TiviKit to turn it back on")
            else -> t("Không khoá được: cập nhật Khoá trẻ em bằng TiviKit", "Couldn't lock: update the Kids lock with TiviKit")
        })
    }

    fun batDau() {
        tt.coDonRam = HeThong.donDuocRam()
        // Ô lớn nhất (thư viện, phóng 1,08) rộng khoảng 140 dp trên khung 960 dp; biểu tượng tối đa khoảng 48 dp.
        val rong = coManHinh().first
        rongBanner = (140 * rong / 960).coerceIn(128, 256)
        coBieuTuong = (48 * rong / 960).coerceIn(48, 96)
        // StateFlow phát giá trị hiện tại trước: lần đầu cũng đọc cấu hình ở đây.
        scope.launch { CauHinh.lanDoi.collect { apCauHinh() } }
        // Lần đầu, và mỗi lần TiviKit gửi / bỏ ảnh nền riêng: dựng lại ảnh nền và bản mờ cho kính.
        scope.launch { CauHinh.lanNen.collect { tt.nen = withContext(Dispatchers.Default) { runCatching { taiNen() }.getOrNull() } ?: tt.nen } }
        // TiviKit gửi / bỏ ảnh riêng của app (logo kênh): đọc lại ảnh các app đó đang hiện (ảnh cũ giữ tới khi có ảnh mới).
        scope.launch {
            CauHinh.lanAnh.collect {
                val doi = CauHinh.anhDoi.iterator()
                while (doi.hasNext()) { val g = doi.next(); doi.remove(); if (g in tt.anh) taiAnh(g, lai = true) }
            }
        }
    }

    fun hien() {
        docDuocDangPhat = true
        tt.coKhoa = runCatching { pm.getPackageInfo(GOI_KHOA, 0); true }.getOrDefault(false)
        docApp()
        chayNhip()
    }

    fun an() {
        nhip?.cancel()
        nhip = null
        if (tt.dangDoi != null) xongDoi()
        tt.menu = null
        tt.menuNguon = false
    }

    /** Bấm Home khi đang ở màn Home (hoặc quay về bằng nút Home): đóng mọi thứ đè lên, chọn ô đầu dock. */
    fun veDau() {
        tt.menu = null
        tt.lich = false; tt.xemCau = false; tt.menuNguon = false
        if (tt.dangDoi != null) xongDoi()
        if (tt.thuVien) { tt.thuVien = false; giuAnhDock() }
        tt.traChon = null
        tt.lanVeDau++
    }

    /**
     * Thiếu RAM: thư viện app đóng thì chỉ giữ ảnh dock và gần đây; đang mở thì giữ ảnh nạp gần nhất (đang thấy), ô nào mất
     * ảnh mà đang hiện thì tự nạp lại (28/10/2026: trước đây thư viện mở là không bỏ ảnh nào).
     */
    fun botAnh(muc: Int) {
        if (muc < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        if (tt.thuVien) gioiHanAnh(ANH_KHI_THIEU_RAM) else giuAnhDock()
    }

    private fun giuAnhDock() {
        val giu = (tt.dock + tt.ganDay).toSet()
        tt.anh.keys.retainAll(giu)
        thuTuAnh.retainAll(giu)
    }

    /** Thứ tự nạp ảnh (cũ trước), để bỏ ảnh nạp lâu nhất khi cuộn hết thư viện app trên hộp ít RAM. */
    private val thuTuAnh = LinkedHashSet<String>()

    /** Bớt ảnh còn [toiDa] (không tính ảnh dock / gần đây), bỏ ảnh nạp lâu nhất trước. */
    private fun gioiHanAnh(toiDa: Int) {
        val giu = (tt.dock + tt.ganDay).toSet()
        val it = thuTuAnh.iterator()
        while (tt.anh.size > toiDa && it.hasNext()) {
            val g = it.next()
            if (g !in giu) { it.remove(); tt.anh.remove(g) }
        }
    }

    private fun apCauHinh() {
        val j = ch.doc()
        NgonNgu.tiengViet = when (j.optString("ngon_ngu")) {
            "vi" -> true
            "en" -> false
            else -> Locale.getDefault().language == "vi"
        }
        tt.ten = j.optString("ten")
        val ten = CauHinh.tenApp(j)
        if (ten != tenRieng) { tenRieng = ten; datApps() }
        dockLuu = if (j.has("dock")) CauHinh.dsGoi(j.optJSONArray("dock")) else null
        lichSu = CauHinh.dsGoi(j.optJSONArray("gan_day"))
        val n = CauHinh.noi(j)
        if (n != noi) {
            noi = n
            tt.thoiTiet = null
            lucThoiTiet = 0L
            if (n != null) docThoiTietCu(n)
            xemThoiTiet()
        }
        capNhatDock()
        napCau(j)
    }

    /**
     * Câu kinh hôm nay (TrangThaiHome.cau): bộ riêng TiviKit gửi (cau.txt) hay Kinh Pháp Cú đi kèm. Đọc lại khi sang ngày,
     * khi bật / tắt hay đổi bộ ("cau" trong cấu hình, file cau.txt); cấu hình đổi vì việc khác thì thôi.
     */
    private fun napCau(j: JSONObject = ch.doc()) {
        val ngay = LocalDate.now()
        ngayCau = ngay
        if (!CauHinh.cauBat(j)) { khoaCau = null; tt.cau = null; tt.xemCau = false; return }
        val f = File(ctx.filesDir, CauHinhProvider.CAU)
        val ten = CauHinh.tenCau(j)
        val khoa = "$ngay|$ten|${f.length()}|${f.lastModified()}"
        if (khoa == khoaCau) return
        khoaCau = khoa
        scope.launch {
            val (rieng, bo) = withContext(Dispatchers.IO) { runCatching { CauHinhProvider.docBoCau(ctx) }.getOrNull() } ?: return@launch
            if (khoa != khoaCau || bo.isEmpty()) return@launch
            tt.cau = CauHomNay(if (rieng) ten else null, bo[CauMoiNgay.chiSo(ngay.toEpochDay(), bo.size)])
        }
    }

    private fun docApp() {
        scope.launch {
            val ds = withContext(Dispatchers.IO) { runCatching { DanhSachApp.doc(pm, setOf(ctx.packageName)) }.getOrNull() } ?: return@launch
            // Khoá trẻ em: màn KhoaNgay / XacNhan chớp qua (trong suốt, menu ⏻ hay Cast xin mã) cũng tính là vừa dùng app, nên
            // bỏ khỏi phần gần đây theo thống kê; mở nó từ launcher thì vẫn vào gần đây (lichSu).
            suDung = withContext(Dispatchers.IO) { HeThong.dungGanDay(ctx) }.filter { it != GOI_KHOA }
            goiHome = withContext(Dispatchers.IO) { HeThong.goiHome(pm) }
            dsGoc = ds
            datApps()
            capNhatDock()
        }
    }

    /** Danh sách app hiện ra: tên riêng TiviKit đặt thay tên app. */
    private fun datApps() {
        val ds = DanhSachApp.doiTen(dsGoc, tenRieng)
        if (ds == tt.apps) return
        // App vừa cập nhật: vẽ lại ảnh (banner có thể đã đổi).
        val cu = tt.apps.associateBy(AppTv::goi)
        ds.forEach { a -> cu[a.goi]?.let { if (it.capNhat != a.capNhat) tt.anh.remove(a.goi) } }
        tt.apps = ds
    }

    private fun capNhatDock() {
        val coTren = tt.apps.map(AppTv::goi)
        if (coTren.isEmpty()) return
        val luu = dockLuu
        val dock = if (luu != null) Dock.conLai(luu, coTren.toSet()) else Dock.macDinh(coTren).also { moi ->
            dockLuu = moi
            ch.sua { it.put("dock", JSONArray(moi)) }
        }
        if (tt.dangDoi == null) tt.dock = dock
        tt.ganDay = Dock.ganDayTu(suDung, lichSu, dock, coTren.toSet(), goiHome + ctx.packageName)
    }

    private fun luuDock(moi: List<String>) {
        dockLuu = moi
        tt.dock = moi
        tt.ganDay = Dock.ganDayTu(suDung, lichSu, moi, tt.apps.map(AppTv::goi).toSet(), goiHome + ctx.packageName)
        ch.sua { it.put("dock", JSONArray(moi)) }
    }

    /** Thời tiết lưu lần trước cho đúng nơi này, còn mới (dưới 3 giờ): hiện ngay khi mở màn Home. */
    private fun docThoiTietCu(n: ThoiTiet.Noi) {
        val j = runCatching { JSONObject(File(ctx.filesDir, "thoi-tiet.json").readText()) }.getOrNull() ?: return
        val luc = j.optLong("luc")
        if (j.optDouble("lat") != n.lat || j.optDouble("lon") != n.lon || System.currentTimeMillis() - luc > 3 * 3_600_000L) return
        tt.thoiTiet = ThoiTiet.HienTai(j.optInt("nhiet"), j.optInt("ma"))
        lucThoiTiet = luc
    }

    /** Lấy thời tiết khi đã quá 30 phút (lỗi mạng thì thử lại sau 5 phút). */
    private fun xemThoiTiet() {
        val n = noi ?: return
        val bay = System.currentTimeMillis()
        if (dangLayThoiTiet || bay - lucThoiTiet < 30 * 60_000L) return
        dangLayThoiTiet = true
        scope.launch {
            val h = withContext(Dispatchers.IO) { ThoiTiet.tai(n) }
            dangLayThoiTiet = false
            if (n != noi) return@launch
            if (h == null) { lucThoiTiet = bay - 25 * 60_000L; return@launch }
            tt.thoiTiet = h
            lucThoiTiet = bay
            withContext(Dispatchers.IO) {
                runCatching {
                    File(ctx.filesDir, "thoi-tiet.json").writeText(JSONObject().put("lat", n.lat).put("lon", n.lon)
                        .put("nhiet", h.nhietDo).put("ma", h.ma).put("luc", bay).toString())
                }
            }
        }
    }

    /** Thẻ Đang phát: đọc phiên phát hiện tại (giữ ảnh bìa cũ nếu vẫn là bài đó). Chưa có quyền thì thôi đọc tới lần hiện sau. */
    private fun xemDangPhat() {
        if (!docDuocDangPhat) return
        val ten = tt.apps.associate { it.goi to it.ten }
        val cu = tt.dangPhat
        scope.launch {
            val kq = withContext(Dispatchers.IO) {
                try {
                    DangPhat.doc(ctx, { g -> ten[g] ?: g }, cu)
                } catch (_: SecurityException) {
                    docDuocDangPhat = false
                    null
                } catch (_: Exception) {
                    null
                }
            }
            phien = kq?.second
            if (kq?.first != tt.dangPhat) tt.dangPhat = kq?.first
        }
    }

    /** Đồng hồ mỗi giây, tốc độ mạng mỗi giây, RAM / bộ nhớ / mạng mỗi 5 giây — chỉ khi màn Home đang hiện. */
    private fun chayNhip() {
        nhip?.cancel()
        nhip = scope.launch {
            var xuong = HeThong.byteXuong()
            var len = HeThong.byteLen()
            var luc = SystemClock.elapsedRealtime()
            var dem = 0
            while (isActive) {
                tt.bayGio = LocalDateTime.now()
                if (tt.bayGio.toLocalDate() != ngayCau) napCau()
                if (dem % 2 == 0) xemDangPhat()
                if (dem % 60 == 0) xemThoiTiet()
                if (dem % 5 == 0) {
                    val ram = withContext(Dispatchers.IO) { HeThong.ramTrong(ctx) }
                    val boNho = withContext(Dispatchers.IO) { HeThong.boNhoTrong() }
                    val mang = withContext(Dispatchers.IO) { runCatching { HeThong.mang(ctx) }.getOrNull() }
                    tt.ramTrong = ram
                    tt.boNhoTrong = boNho
                    tt.mang = mang
                }
                delay(1000 - System.currentTimeMillis() % 1000)
                val x = HeThong.byteXuong()
                val l = HeThong.byteLen()
                val bay = SystemClock.elapsedRealtime()
                val ms = max(1L, bay - luc)
                tt.tocXuong = if (x < 0 || xuong < 0) -1 else (x - xuong).coerceAtLeast(0) * 1000 / ms
                tt.tocLen = if (l < 0 || len < 0) -1 else (l - len).coerceAtLeast(0) * 1000 / ms
                xuong = x; len = l; luc = bay
                dem++
            }
        }
    }

    /** Cỡ màn hình thật (nằm ngang), tối đa 1920×1080 (tivi 4K vẫn vẽ giao diện ở 1080p). */
    private fun coManHinh(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        act.windowManager.defaultDisplay.getRealMetrics(dm)
        var w = max(dm.widthPixels, dm.heightPixels)
        var h = min(dm.widthPixels, dm.heightPixels)
        if (w > 1920) { h = h * 1920 / w; w = 1920 }
        return w to h
    }

    /** Ảnh nền: ảnh TiviKit gửi (anh-nen.jpg), không có thì ảnh đồi chè đi kèm; đúng cỡ màn hình, tối đa 1920×1080. */
    private fun taiNen(): NenKinh? {
        val (w, h) = coManHinh()
        val rieng = File(ctx.filesDir, "anh-nen.jpg")
        val anh = (if (rieng.isFile) AnhNen.giaiMa({ rieng.inputStream() }, w, h) else null)
            ?: AnhNen.giaiMa({ ctx.assets.open("nen/doi-che.jpg") }, w, h)
            ?: return null
        return AnhNen.tao(anh, w, h)
    }

    private fun bao(s: String) {
        tt.bao = s
        tatBao?.cancel()
        tatBao = scope.launch { delay(4000); tt.bao = null }
    }

    private fun moManHinh(vararg ie: Intent): Boolean {
        for (i in ie) {
            try {
                act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        bao(t("Tivi này không mở được mục đó", "This TV can't open that"))
        return false
    }

    override fun mo(goi: String) {
        val ie = DanhSachApp.ieMo(pm, goi) ?: return bao(t("Không mở được app này", "Can't open this app"))
        if (!moManHinh(ie)) return
        lichSu = Dock.ghiMo(lichSu, goi)
        ch.sua { it.put("gan_day", JSONArray(lichSu)) }
    }

    override fun canAnh(goi: String) = taiAnh(goi, lai = false)

    /** Đọc ảnh của [goi]; [lai] = đọc lại cả khi đã có. TiviKit gửi ảnh riêng mới giữa lúc đọc thì đọc lại lần nữa. */
    private fun taiAnh(goi: String, lai: Boolean) {
        if ((!lai && goi in tt.anh) || !dangTai.add(goi)) return
        val lan = CauHinh.lanAnh.value
        scope.launch {
            val rieng = CauHinhProvider.anhApp(ctx.filesDir, goi)
            val a = withContext(Dispatchers.IO) { runCatching { DanhSachApp.anh(pm, goi, rongBanner, coBieuTuong, rieng) }.getOrNull() }
            dangTai.remove(goi)
            if (CauHinh.lanAnh.value != lan) return@launch taiAnh(goi, lai = true)
            if (a != null) {
                tt.anh[goi] = a
                thuTuAnh.remove(goi); thuTuAnh.add(goi)
                gioiHanAnh(ANH_TOI_DA)
            }
        }
    }

    override fun donRam() {
        if (tt.dangDon) return
        tt.dangDon = true
        scope.launch {
            val truoc = HeThong.ramTrong(ctx)
            val goi = tt.apps.map(AppTv::goi).filterNot { HeThong.giuLai(it, ctx.packageName) }
            withContext(Dispatchers.IO) { HeThong.donRam(ctx, goi) }
            delay(1500)
            val sau = HeThong.ramTrong(ctx)
            tt.ramTrong = sau
            val viet = NgonNgu.tiengViet
            val them = sau - truoc
            bao(if (them >= 8L * 1024 * 1024) t("Đã dọn RAM: trống ${Chu.dungLuong(sau, viet)} (+${Chu.dungLuong(them, viet)})",
                "RAM freed: ${Chu.dungLuong(sau, viet)} free (+${Chu.dungLuong(them, viet)})")
            else t("RAM đã gọn sẵn: trống ${Chu.dungLuong(sau, viet)}", "RAM was already tidy: ${Chu.dungLuong(sau, viet)} free"))
            tt.dangDon = false
        }
    }

    override fun moMang() {
        moManHinh(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    }

    override fun moCaiDat() {
        moManHinh(Intent(Settings.ACTION_SETTINGS))
    }

    override fun ghim(goi: String) {
        tt.menu = null
        tt.traChon = goi
        luuDock(Dock.them(tt.dock, goi))
        bao(t("Đã ghim vào dock", "Pinned to the dock"))
    }

    override fun boGhim(goi: String) {
        tt.menu = null
        val i = tt.dock.indexOf(goi)
        val moi = Dock.bo(tt.dock, goi)
        // Ô bên cạnh nhận lựa chọn (trong thư viện thì ô vừa bỏ ghim vẫn ở đó).
        tt.traChon = if (tt.thuVien) goi else moi.getOrNull(i) ?: moi.lastOrNull() ?: TrangThaiHome.TAT_CA
        luuDock(moi)
    }

    override fun doi(goi: String, buoc: Int) {
        tt.dock = Dock.doi(tt.dock, goi, buoc)
    }

    override fun xongDoi() {
        tt.dangDoi = null
        luuDock(tt.dock)
    }

    override fun thongTin(goi: String) {
        moManHinh(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", goi, null)))
    }

    override fun go(goi: String) {
        moManHinh(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", goi, null)))
    }

    override fun moThuVien() {
        tt.thuVien = true
    }

    override fun dongThuVien() {
        tt.thuVien = false
        tt.traChon = TrangThaiHome.TAT_CA
        giuAnhDock()
    }

    override fun dongMenu() {
        tt.traChon = tt.menu
        tt.menu = null
    }

    override fun moDangPhat() {
        val c = phien ?: return
        // Màn của chính phiên phát (đúng video / bài đang phát), không có thì mở app.
        if (c.sessionActivity?.let { runCatching { it.send() }.isSuccess } == true) return
        mo(c.packageName)
    }

    override fun dungPhat() {
        val c = phien ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        scope.launch { delay(500); xemDangPhat() }
    }

    override fun tatTivi() {
        dongMenuNguon()
        if (!NguonDichVu.tatTivi()) baoNguon()
    }

    override fun khoiDongLai() {
        dongMenuNguon()
        if (!NguonDichVu.menuNguon()) baoNguon()
    }

    override fun khoaTreEm() {
        dongMenuNguon()
        try {
            khoaNgay.launch(Intent().setClassName(GOI_KHOA, "$GOI_KHOA.KhoaNgay"))
        } catch (_: ActivityNotFoundException) {
            bao(t("Cần cập nhật Khoá trẻ em bằng TiviKit", "Update the Kids lock with TiviKit first"))
        } catch (_: SecurityException) {
            bao(t("Cần cập nhật Khoá trẻ em bằng TiviKit", "Update the Kids lock with TiviKit first"))
        }
    }

    private fun dongMenuNguon() {
        tt.menuNguon = false
        tt.traChon = TrangThaiHome.NGUON
    }

    /** Dịch vụ Nút nguồn chưa bật (TiviKit bật khi cài / cập nhật launcher), hay tivi Android 8 không có lệnh tắt màn hình. */
    private fun baoNguon() = bao(
        if (!NguonDichVu.chay) t("Cần bật Nút nguồn của launcher: cập nhật TiviKit Launcher bằng TiviKit", "Turn on the launcher's power button: update TiviKit Launcher with TiviKit")
        else t("Tivi này không cho app tắt: bấm nút nguồn trên remote", "This TV can't be turned off by an app: use the remote's power button")
    )
}

private const val GOI_KHOA = "vn.tivikit.khoa"

/** Ảnh app giữ tối đa lúc cuộn thư viện app (banner chừng 150 KB một ảnh), và khi thiếu RAM. */
private const val ANH_TOI_DA = 60
private const val ANH_KHI_THIEU_RAM = 24
