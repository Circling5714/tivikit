package vn.tivikit.mapvoice

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Dịch vụ trợ năng của Map Voice: nghe đổi cửa sổ (tên app vừa hiện), không đọc nội dung màn hình. Giữ vòng nghe ([Nghe])
 * chạy suốt từ lúc tivi bật, để Android cho mở SmartTube / VTVgo / TV360 / FPT Play khi app đang chạy nền, và biết Trợ lý
 * Google có vừa mở YouTube gốc không. Người dùng thấy và tắt được trong Cài đặt → Trợ năng. Từ 1/11/2026 (kênh tự chọn
 * nguồn, [TuChon]) còn ghi lúc có người bấm remote — chỉ lúc bấm và có phải phím chuyển kênh không, không chặn, không giữ
 * phím nào — để không tự đổi nguồn khi người xem tự tạm dừng.
 *
 * Một câu cần chuyển: đóng Trợ lý (và YouTube gốc, trừ khi chính nó là app được chọn) qua ADB để nó không làm tiếp, rồi mở
 * app đã chọn. Câu không cần chuyển mà vài giây sau Trợ lý tự mở YouTube gốc (VD nói tên bài hát) thì cũng chuyển sang app
 * YouTube đã chọn với chính câu đó (trừ câu riêng để Trợ lý làm).
 */
class MapVoiceDichVu : AccessibilityService() {
    internal lateinit var kho: Kho
    private var nghe: Nghe? = null
    private var tuChon: TuChon? = null
    /** App vừa hiện trước mặt (cửa sổ đổi gần nhất, bỏ cửa sổ của hệ thống và của Map Voice). */
    @Volatile var appTruoc: String? = null
        private set
    /** Lúc bấm phím remote gần nhất (trừ âm lượng) / phím chuyển kênh, Back, Home (SystemClock.elapsedRealtime). */
    @Volatile var lucBamPhim = 0L
        private set
    @Volatile var lucPhimDoiKenh = 0L
        private set
    private val viec = Executors.newSingleThreadExecutor()
    private val chinh = Handler(Looper.getMainLooper())
    /** Câu vừa bỏ qua cho Trợ lý: Trợ lý mở YouTube gốc trong [CHO_YOUTUBE] thì chuyển câu này sang app YouTube đã chọn. */
    @Volatile private var cauCho: String? = null
    @Volatile private var lucCho = 0L
    /** Vừa mở app YouTube khác / kênh: YouTube gốc hiện lên trước lúc này (Trợ lý mở trước khi bị đóng) thì đóng nó. */
    @Volatile private var chanYouTubeDen = 0L

    /** Việc đã làm cho một câu ("loại|chi tiết", như ghi nhật ký) và gói app đã mở ("" nếu không mở app nào). */
    data class Lam(val viec: String, val app: String = "")

    val trangThaiNghe: Nghe.TrangThai get() = nghe?.trangThai ?: Nghe.TrangThai.TAT

    override fun onServiceConnected() {
        super.onServiceConnected()
        kho = Kho(this)
        dangChay = this
        if (tuChon == null) tuChon = TuChon(this)
        if (nghe == null) nghe = Nghe(filesDir, { cau -> viec.execute { xuLy(cau, epYouTube = false, laThu = false) } }) {
            hien(getString(R.string.dang_hoi), dai = true)
            chinh.postDelayed({ hien(getString(R.string.dang_hoi), dai = true) }, 4000)
        }.also { it.batDau() }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val goi = e.packageName?.toString() ?: return
        if (goi != CUA_SO_HE_THONG && goi != packageName) appTruoc = goi
        if (goi != XuLyCau.YOUTUBE_GOC) return
        val bay = SystemClock.elapsedRealtime()
        if (bay < chanYouTubeDen) {
            viec.execute { nghe?.lenh("am force-stop ${XuLyCau.YOUTUBE_GOC}") }
            return
        }
        val cau = cauCho ?: return
        if (bay - lucCho > CHO_YOUTUBE) return
        cauCho = null
        viec.execute { xuLy(cau, epYouTube = true, laThu = false) }
    }

    /** Chỉ ghi lúc bấm (TuChon xem để không đổi nguồn khi người xem tự tạm dừng); không chặn phím nào. */
    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN && e.keyCode !in PHIM_AM_LUONG) {
            val bay = SystemClock.elapsedRealtime()
            lucBamPhim = bay
            if (e.keyCode in PHIM_DOI_KENH || e.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) lucPhimDoiKenh = bay
        }
        return false
    }

    override fun onInterrupt() {}

    /** Ô Kênh nhanh Auto (qua MoTuDong): mở kênh [ten] tự chọn trong [nguon] ([coDinh]: đúng thứ tự riêng của ô). */
    fun moTuDong(ten: String, nguon: List<TuChonNguon.Nguon>, coDinh: Boolean = false) { tuChon?.mo(ten, nguon, coDinh) }

    /**
     * Làm việc cho câu [cau] (trên luồng [viec]); [epYouTube] = Trợ lý đã tự mở YouTube gốc cho câu này. Việc đã làm dạng
     * "loại|chi tiết" (ghi nhật ký; TiviKit đọc để hiện): "yt-phat|chữ tìm", "yt-tim|chữ tìm", "yt-app|", "kenh|app|tên",
     * "mo-app|gói", "tro-ly|chữ của câu riêng", "bo-qua|", "loi|lý do".
     */
    fun xuLy(cau: String, epYouTube: Boolean, laThu: Boolean): Lam {
        val ch = kho.cauHinh
        val quyet = XuLyCau.quyet(cau, ch, kho.dsKenh, ::coApp)
        val v = if (quyet == XuLyCau.BoQua && epYouTube && ch.bat) XuLyCau.youTube(cau, ch) else quyet
        // Câu mới thay việc tự chọn nguồn đang làm / đang theo dõi.
        if (v != XuLyCau.BoQua) tuChon?.huy()
        val ket = runCatching {
            when (v) {
                is XuLyCau.YouTube -> moYouTube(v, ch)
                is XuLyCau.MoKenh -> moKenh(v)
                is XuLyCau.KenhTuDong -> moKenhTuDong(v.k)
                is XuLyCau.MoApp -> moAppRieng(v.goi)
                is XuLyCau.TroLy -> Lam("tro-ly|${v.chu}")
                XuLyCau.BoQua -> {
                    // Trợ lý tự mở YouTube gốc cho câu này thì chuyển sang app YouTube đã chọn (chọn YouTube gốc thì để yên).
                    if (!laThu && appYouTube(ch) != XuLyCau.YOUTUBE_GOC) { cauCho = cau; lucCho = SystemClock.elapsedRealtime() }
                    Lam("bo-qua|")
                }
            }
        }.getOrElse { Lam("loi|${it.javaClass.simpleName}") }
        kho.ghi(cau, ket.viec, ket.app)
        return ket
    }

    /** Thử một câu gõ từ TiviKit như vừa nói (chờ làm xong, tối đa 15 giây). */
    fun thu(cau: String): Lam = runCatching { viec.submit<Lam> { xuLy(cau, epYouTube = false, laThu = true) }.get(15, TimeUnit.SECONDS) }
        .getOrElse { Lam("loi|${it.javaClass.simpleName}") }

    /**
     * App mở YouTube: app chọn ở TiviKit nếu còn trên tivi, không thì SmartTube đầu tiên có trên tivi, rồi TizenTube (YouTube
     * gốc chỉ khi được chọn: không chọn thì đó là việc của Trợ lý).
     */
    fun appYouTube(ch: CauHinh = kho.cauHinh): String? =
        ch.appYouTube.takeIf { it.isNotEmpty() && coApp(it) } ?: (XuLyCau.SMARTTUBE + XuLyCau.TIZENTUBE).firstOrNull(::coApp)

    private fun moYouTube(v: XuLyCau.YouTube, ch: CauHinh): Lam {
        val app = v.app.takeIf { it.isNotEmpty() && coApp(it) } ?: appYouTube(ch) ?: return Lam("loi|khong-co-smarttube")
        val laGoc = app == XuLyCau.YOUTUBE_GOC
        dongTroLy(dongYouTube = !laGoc)
        if (!laGoc) chanYouTubeDen = SystemClock.elapsedRealtime() + CHAN_YOUTUBE
        val ten = tenApp(app)
        if (v.tim.isEmpty()) {
            hien(getString(R.string.mo_app, ten))
            if (!mo(moApp(app))) return Lam("loi|khong-mo-duoc", app)
            return Lam("yt-app|", app)
        }
        hien(getString(R.string.mo_youtube, v.tim, ten))
        // Tìm video đầu mất vài giây: không thấy (mạng lỗi) thì mở trang tìm kiếm.
        val link = if (v.phatLuon) TimVideo.tim(v.tim)?.let(TimVideo::linkXem) else null
        // App Cobalt đang tắt mà mở bằng link trang tìm kiếm thì mất chữ tìm: gửi lại link khi app đã lên.
        val guiLai = link == null && app in XuLyCau.COBALT && !dangChay(app)
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(link ?: TimVideo.linkTim(v.tim))).setPackage(app)
        if (!laGoc) chanYouTubeDen = SystemClock.elapsedRealtime() + CHAN_YOUTUBE
        if (!mo(i) && !mo(moApp(app))) return Lam("loi|khong-mo-duoc", app)
        if (guiLai) chinh.postDelayed({ mo(Intent(Intent.ACTION_VIEW, Uri.parse(TimVideo.linkTim(v.tim))).setPackage(app)) }, GUI_LAI_COBALT)
        return Lam((if (link != null) "yt-phat|" else "yt-tim|") + v.tim, app)
    }

    /** [goi] đang chạy trên tivi (hỏi qua ADB của tivi); chưa nối ADB / không hỏi được thì coi như chưa chạy. */
    internal fun dangChay(goi: String): Boolean = nghe?.lenh("pidof $goi", 2000)?.trim()?.let { Regex("\\d+( \\d+)*").matches(it) } == true

    /** Chạy một lệnh shell ngắn qua ADB của chính tivi; null nếu chưa nối. */
    internal fun adb(lenh: String, choToiDa: Long = 5000): String? = nghe?.lenh(lenh, choToiDa)

    internal fun uidCua(goi: String): Int? = runCatching { packageManager.getApplicationInfo(goi, 0).uid }.getOrNull()

    private fun moKenh(v: XuLyCau.MoKenh): Lam {
        // VTVgo / FPT Play theo mã kênh, TV360 theo số rồi tên kênh: lệnh như ô Kênh nhanh (kenh/MoKenh), TuChon.lenhMo.
        val n = TuChonNguon.Nguon(v.app, v.ten, ma = v.ma, so = v.so)
        val goi = n.goi
        dongTroLy(dongYouTube = true)
        chanYouTubeDen = SystemClock.elapsedRealtime() + CHAN_YOUTUBE
        hien(getString(R.string.mo_kenh, v.ten, tenApp(goi)))
        if (!mo(TuChon.lenhMo(n)) && !mo(moApp(goi))) {
            hien(getString(R.string.khong_mo_duoc, tenApp(goi)))
            return Lam("loi|khong-mo-duoc", goi)
        }
        return Lam("kenh|${v.app}|${v.ten}", goi)
    }

    /** Câu mở kênh khi app kênh là "auto": TuChon thử các nguồn trên luồng riêng; báo app sẽ thử trước. */
    private fun moKenhTuDong(k: KenhTv): Lam {
        dongTroLy(dongYouTube = true)
        chanYouTubeDen = SystemClock.elapsedRealtime() + CHAN_YOUTUBE
        val khoa = XuLyCau.khoaKenh(k.ten)
        val dau = (kho.thuTuRieng[khoa]?.filter { coApp(it.goi) }
            ?: TuChonNguon.xep(TuChonNguon.nguonCua(k, emptyList()).filter { coApp(it.goi) }, kho.thongKe(khoa), System.currentTimeMillis()))
            .firstOrNull()?.goi ?: ""
        tuChon?.mo(k)
        return Lam("kenh|${CauHinh.APP_AUTO}|${k.ten}", dau)
    }

    /** Câu riêng mở app [goi] (không tìm gì trong app). */
    private fun moAppRieng(goi: String): Lam {
        if (!coApp(goi)) return Lam("loi|khong-co-app", goi)
        val laGoc = goi == XuLyCau.YOUTUBE_GOC
        dongTroLy(dongYouTube = !laGoc)
        if (!laGoc) chanYouTubeDen = SystemClock.elapsedRealtime() + CHAN_YOUTUBE
        hien(getString(R.string.mo_app, tenApp(goi)))
        if (!mo(moApp(goi))) {
            hien(getString(R.string.khong_mo_duoc, tenApp(goi)))
            return Lam("loi|khong-mo-duoc", goi)
        }
        return Lam("mo-app|$goi", goi)
    }

    /** Đóng Trợ lý Google (và YouTube gốc) để nó không làm tiếp câu vừa nghe, như Super Voice; chưa nối ADB thì thôi. */
    private fun dongTroLy(dongYouTube: Boolean) {
        nghe?.lenh("am force-stop ${XuLyCau.TRO_LY}" + if (dongYouTube) "; am force-stop ${XuLyCau.YOUTUBE_GOC}" else "", 3000)
    }

    private fun moApp(goi: String): Intent? = packageManager.getLeanbackLaunchIntentForPackage(goi) ?: packageManager.getLaunchIntentForPackage(goi)

    /**
     * Mở [i]; tivi đang ở màn bảo vệ (câu thử từ TiviKit) thì tắt nó như bấm remote, như app Cast (TrinhPhat.danhThuc), không
     * thì app mở phía sau màn bảo vệ.
     */
    @Suppress("DEPRECATION")
    internal fun mo(i: Intent?): Boolean = i != null && try {
        runCatching {
            getSystemService(PowerManager::class.java).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "tivikit:mapvoice").acquire(3_000)
        }
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    fun coApp(goi: String): Boolean = try {
        packageManager.getApplicationInfo(goi, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    internal fun tenApp(goi: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(goi, 0)).toString() }.getOrDefault(goi)

    internal fun hien(chu: String, dai: Boolean = false) {
        chinh.post { Toast.makeText(applicationContext, chu, if (dai) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }
    }

    fun noiLai() { nghe?.noiLai() }

    override fun onUnbind(intent: Intent?): Boolean {
        tat()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tat()
        super.onDestroy()
    }

    private fun tat() {
        tuChon?.dong()
        tuChon = null
        nghe?.dung()
        nghe = null
        if (dangChay === this) dangChay = null
    }

    companion object {
        @Volatile var dangChay: MapVoiceDichVu? = null
        /** Cửa sổ của hệ thống (bảng âm lượng…) không tính là app trước mặt. */
        private const val CUA_SO_HE_THONG = "com.android.systemui"
        private val PHIM_AM_LUONG = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE)
        /** Phím rời kênh đang xem: thôi theo dõi kênh tự chọn nguồn (số kênh 0–9 xét riêng). */
        private val PHIM_DOI_KENH = setOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_ESCAPE,
        )
        private const val CHO_YOUTUBE = 10_000L
        private const val CHAN_YOUTUBE = 8_000L
        /** Gửi lại link trang tìm kiếm cho app Cobalt vừa mở nguội (TX3: gửi lại sau 5 giây thì tìm đúng chữ). */
        private const val GUI_LAI_COBALT = 5_000L
    }
}
