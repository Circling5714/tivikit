package vn.tivikit.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
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
    private val dangTai = HashSet<String>()

    fun batDau() {
        tt.coDonRam = HeThong.donDuocRam()
        // StateFlow phát giá trị hiện tại trước: lần đầu cũng đọc cấu hình ở đây.
        scope.launch { CauHinh.lanDoi.collect { apCauHinh() } }
        scope.launch { tt.nen = withContext(Dispatchers.Default) { runCatching { taiNen() }.getOrNull() } }
    }

    fun hien() {
        docApp()
        chayNhip()
    }

    fun an() {
        nhip?.cancel()
        nhip = null
        if (tt.dangDoi != null) xongDoi()
        tt.menu = null
    }

    /** Bấm Home khi đang ở màn Home (hoặc quay về bằng nút Home): đóng mọi thứ đè lên, chọn ô đầu dock. */
    fun veDau() {
        tt.menu = null
        if (tt.dangDoi != null) xongDoi()
        if (tt.thuVien) { tt.thuVien = false; giuAnhDock() }
        tt.traChon = null
        tt.lanVeDau++
    }

    fun botAnh(muc: Int) {
        if (muc >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && !tt.thuVien) giuAnhDock()
    }

    private fun giuAnhDock() {
        val giu = (tt.dock + tt.ganDay).toSet()
        tt.anh.keys.retainAll(giu)
    }

    private fun apCauHinh() {
        val j = ch.doc()
        NgonNgu.tiengViet = when (j.optString("ngon_ngu")) {
            "vi" -> true
            "en" -> false
            else -> Locale.getDefault().language == "vi"
        }
        tt.ten = j.optString("ten")
        dockLuu = if (j.has("dock")) CauHinh.dsGoi(j.optJSONArray("dock")) else null
        lichSu = CauHinh.dsGoi(j.optJSONArray("gan_day"))
        capNhatDock()
    }

    private fun docApp() {
        scope.launch {
            val ds = withContext(Dispatchers.IO) { runCatching { DanhSachApp.doc(pm, setOf(ctx.packageName)) }.getOrNull() } ?: return@launch
            if (ds == tt.apps) return@launch
            // App vừa cập nhật: vẽ lại ảnh (banner có thể đã đổi).
            val cu = tt.apps.associateBy(AppTv::goi)
            ds.forEach { a -> cu[a.goi]?.let { if (it.capNhat != a.capNhat) tt.anh.remove(a.goi) } }
            tt.apps = ds
            capNhatDock()
        }
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
        tt.ganDay = Dock.ganDay(lichSu, dock, coTren.toSet())
    }

    private fun luuDock(moi: List<String>) {
        dockLuu = moi
        tt.dock = moi
        tt.ganDay = Dock.ganDay(lichSu, moi, tt.apps.map(AppTv::goi).toSet())
        ch.sua { it.put("dock", JSONArray(moi)) }
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

    /** Ảnh nền: ảnh TiviKit gửi (anh-nen.jpg), không có thì ảnh đồi chè đi kèm; đúng cỡ màn hình, tối đa 1920×1080. */
    private fun taiNen(): NenKinh? {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        act.windowManager.defaultDisplay.getRealMetrics(dm)
        var w = max(dm.widthPixels, dm.heightPixels)
        var h = min(dm.widthPixels, dm.heightPixels)
        if (w > 1920) { h = h * 1920 / w; w = 1920 }
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

    override fun canAnh(goi: String) {
        if (goi in tt.anh || !dangTai.add(goi)) return
        scope.launch {
            val a = withContext(Dispatchers.IO) { runCatching { DanhSachApp.anh(pm, goi) }.getOrNull() }
            dangTai.remove(goi)
            if (a != null) tt.anh[goi] = a
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
}
