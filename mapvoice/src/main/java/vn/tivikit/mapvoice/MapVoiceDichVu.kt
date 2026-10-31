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
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Dịch vụ trợ năng của Map Voice: chỉ nghe đổi cửa sổ (tên app vừa hiện), không đọc nội dung màn hình, không lọc phím. Giữ
 * vòng nghe ([Nghe]) chạy suốt từ lúc tivi bật, để Android cho mở SmartTube / VTVgo / TV360 / FPT Play khi app đang chạy
 * nền, và biết Trợ lý Google có vừa mở YouTube gốc không. Người dùng thấy và tắt được trong Cài đặt → Trợ năng.
 *
 * Một câu cần chuyển: đóng Trợ lý (và YouTube gốc, trừ khi chính nó là app được chọn) qua ADB để nó không làm tiếp, rồi mở
 * app đã chọn. Câu không cần chuyển mà vài giây sau Trợ lý tự mở YouTube gốc (VD nói tên bài hát) thì cũng chuyển sang app
 * YouTube đã chọn với chính câu đó (trừ câu riêng để Trợ lý làm).
 */
class MapVoiceDichVu : AccessibilityService() {
    private lateinit var kho: Kho
    private var nghe: Nghe? = null
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
        if (nghe == null) nghe = Nghe(filesDir, { cau -> viec.execute { xuLy(cau, epYouTube = false, laThu = false) } }) {
            hien(getString(R.string.dang_hoi), dai = true)
            chinh.postDelayed({ hien(getString(R.string.dang_hoi), dai = true) }, 4000)
        }.also { it.batDau() }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.packageName?.toString() != XuLyCau.YOUTUBE_GOC) return
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

    override fun onInterrupt() {}

    /**
     * Làm việc cho câu [cau] (trên luồng [viec]); [epYouTube] = Trợ lý đã tự mở YouTube gốc cho câu này. Việc đã làm dạng
     * "loại|chi tiết" (ghi nhật ký; TiviKit đọc để hiện): "yt-phat|chữ tìm", "yt-tim|chữ tìm", "yt-app|", "kenh|app|tên",
     * "mo-app|gói", "tro-ly|chữ của câu riêng", "bo-qua|", "loi|lý do".
     */
    fun xuLy(cau: String, epYouTube: Boolean, laThu: Boolean): Lam {
        val ch = kho.cauHinh
        val quyet = XuLyCau.quyet(cau, ch, kho.dsKenh, ::coApp)
        val v = if (quyet == XuLyCau.BoQua && epYouTube && ch.bat) XuLyCau.youTube(cau, ch) else quyet
        val ket = runCatching {
            when (v) {
                is XuLyCau.YouTube -> moYouTube(v, ch)
                is XuLyCau.MoKenh -> moKenh(v)
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
    private fun dangChay(goi: String): Boolean = nghe?.lenh("pidof $goi", 2000)?.trim()?.let { Regex("\\d+( \\d+)*").matches(it) } == true

    private fun moKenh(v: XuLyCau.MoKenh): Lam {
        val goi = when (v.app) {
            CauHinh.APP_VTVGO -> XuLyCau.VTVGO
            CauHinh.APP_FPT -> XuLyCau.FPTPLAY
            else -> XuLyCau.TV360
        }
        dongTroLy(dongYouTube = true)
        chanYouTubeDen = SystemClock.elapsedRealtime() + CHAN_YOUTUBE
        hien(getString(R.string.mo_kenh, v.ten, tenApp(goi)))
        val i = when (v.app) {
            CauHinh.APP_VTVGO -> Intent(Intent.ACTION_VIEW, Uri.parse("vtvgo://play?mediaType=CHANNEL&contentId=${v.ma}")).setPackage(goi)
            // Deep link màn xem truyền hình của FPT Play bản tivi, như ô Kênh nhanh FPT Play (kenh/MoKenh).
            CauHinh.APP_FPT -> Intent(Intent.ACTION_VIEW, Uri.parse("https://fptplay.vn/xem-truyen-hinh/${v.ma}")).setPackage(goi)
            // Cổng nhận lệnh trợ lý giọng nói của TV360 bản tivi, như ô Kênh nhanh (kenh/MoKenh): tìm theo số kênh rồi tên kênh.
            else -> Intent("$goi.VIEW", Uri.parse("content://$goi/SWITCH_CHANNEL")).setClassName(goi, "$goi.assistant.PostmanAssistant")
                .apply { if (v.so.isNotEmpty()) putExtra("number_channel", v.so) }.putExtra("name_channel", v.ten)
        }
        if (!mo(i) && !mo(moApp(goi))) {
            hien(getString(R.string.khong_mo_duoc, tenApp(goi)))
            return Lam("loi|khong-mo-duoc", goi)
        }
        return Lam("kenh|${v.app}|${v.ten}", goi)
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
    private fun mo(i: Intent?): Boolean = i != null && try {
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

    private fun tenApp(goi: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(goi, 0)).toString() }.getOrDefault(goi)

    private fun hien(chu: String, dai: Boolean = false) {
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
        nghe?.dung()
        nghe = null
        if (dangChay === this) dangChay = null
    }

    companion object {
        @Volatile var dangChay: MapVoiceDichVu? = null
        private const val CHO_YOUTUBE = 10_000L
        private const val CHAN_YOUTUBE = 8_000L
        /** Gửi lại link trang tìm kiếm cho app Cobalt vừa mở nguội (TX3: gửi lại sau 5 giây thì tìm đúng chữ). */
        private const val GUI_LAI_COBALT = 5_000L
    }
}
