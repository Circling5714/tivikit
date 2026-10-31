package vn.tivikit.teleremote

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * Dịch vụ trợ năng của Tele Remote (31/10/2026): chạy từ lúc tivi bật, giữ vòng nghe bot Telegram ([Bot]) và kết nối ADB
 * tới chính tivi ([GiuAdb], để bấm phím). Chỉ nghe đổi cửa sổ (app nào vừa hiện, để báo "Đang mở"), không đọc nội dung
 * màn hình, không lọc phím. Người dùng thấy và tắt được trong Cài đặt → Trợ năng.
 */
class TeleRemoteDichVu : AccessibilityService() {
    lateinit var kho: Kho
        private set
    private var adb: GiuAdb? = null
    private var bot: Bot? = null
    private val chinh = Handler(Looper.getMainLooper())
    /** Gói app vừa hiện trước mặt (sự kiện đổi cửa sổ); "" nếu chưa biết. */
    @Volatile private var appHien = ""
    private val laApp = ConcurrentHashMap<String, Boolean>()

    val trangThaiAdb: GiuAdb.TrangThai get() = adb?.trangThai ?: GiuAdb.TrangThai.TAT
    val trangThaiBot: Bot.TrangThai get() = bot?.trangThai ?: Bot.TrangThai.TAT
    val lanCuoi: Long get() = bot?.lanCuoi ?: 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        kho = Kho(this)
        dangChay = this
        if (adb == null) adb = GiuAdb(filesDir) {
            hien(getString(R.string.dang_hoi), dai = true)
            chinh.postDelayed({ hien(getString(R.string.dang_hoi), dai = true) }, 4000)
        }.also { it.batDau() }
        if (bot == null) bot = Bot(this).also { it.batDau() }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        ghiApp(e.packageName?.toString() ?: return)
    }

    /** Ghi [p] là app đang hiện, chỉ khi là app có màn mở hay màn Home (bỏ hộp thoại hệ thống, bàn phím, thông báo nhỏ). */
    private fun ghiApp(p: String) {
        if (p != packageName && laApp.getOrPut(p) { moApp(p) != null || p in home() }) appHien = p
    }

    override fun onInterrupt() {}

    /** Token / cấu hình vừa đổi (TiviKit gửi): bot làm lại với cái mới. */
    fun lamLai() { bot?.lamLai() }

    fun noiLai() { adb?.noiLai() }

    fun coAdb(): Boolean = trangThaiAdb == GiuAdb.TrangThai.DA_NOI

    /** Chạy lệnh shell qua ADB của chính tivi; null nếu chưa nối. */
    fun lenh(l: String, choToiDa: Long = 5000): String? = adb?.lenh(l, choToiDa)

    /**
     * Tên app đang hiện trước mặt; "" nếu chưa biết. Dịch vụ vừa chạy lại (cài đè, chưa có sự kiện đổi cửa sổ nào) thì hỏi
     * ADB cửa sổ đang nhận phím ("mCurrentFocus=Window{… u0 gói/lớp}"; màn bảo vệ không có app thì vẫn chưa biết).
     */
    fun appDangMo(): String {
        if (appHien.isEmpty()) lenh("dumpsys window | grep -m1 mCurrentFocus", 3000)?.let { Regex(" ([\\w.]+)/").find(it) }?.let { ghiApp(it.groupValues[1]) }
        return appHien.takeIf { it.isNotEmpty() }?.let(::tenApp).orEmpty()
    }

    /**
     * Bấm phím [ma] như remote: qua ADB của tivi (input keyevent, giống bấm thật). Chưa nối ADB thì âm lượng, phát / dừng,
     * Back, Home vẫn làm được bằng cách của Android; phím ▲▼◀▶ OK cần ADB (hay Android 13+). false nếu không làm được.
     */
    fun bamPhim(ma: Int): Boolean {
        if (lenh("input keyevent $ma", 4000) != null) return true
        val am = getSystemService(AudioManager::class.java)
        return when (ma) {
            KeyEvent.KEYCODE_VOLUME_UP -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); true }
            KeyEvent.KEYCODE_VOLUME_MUTE -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, ma))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, ma))
                true
            }
            KeyEvent.KEYCODE_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            KeyEvent.KEYCODE_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            else -> Build.VERSION.SDK_INT >= 33 && when (ma) {
                KeyEvent.KEYCODE_DPAD_UP -> performGlobalAction(GLOBAL_ACTION_DPAD_UP)
                KeyEvent.KEYCODE_DPAD_DOWN -> performGlobalAction(GLOBAL_ACTION_DPAD_DOWN)
                KeyEvent.KEYCODE_DPAD_LEFT -> performGlobalAction(GLOBAL_ACTION_DPAD_LEFT)
                KeyEvent.KEYCODE_DPAD_RIGHT -> performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT)
                KeyEvent.KEYCODE_DPAD_CENTER -> performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)
                else -> false
            }
        }
    }

    /**
     * Đưa tivi về chế độ chờ như bấm nút nguồn: Android 9+ bằng thao tác khoá màn hình của dịch vụ trợ năng (như Hẹn giờ,
     * đã thử trên TX3 và Skyworth); Android 8 thì phím ngủ qua ADB.
     */
    fun tatTivi(): Boolean =
        if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else lenh("input keyevent 223", 4000) != null

    /** Trạng thái màn hình theo Android (Awake / Dreaming / Asleep / Dozing); "" nếu chưa nối ADB. */
    fun manHinh(): String = lenh("dumpsys power | grep -m1 mWakefulness=", 3000)?.substringAfter('=')?.trim().orEmpty()

    fun dangSang(): Boolean = getSystemService(PowerManager::class.java).isInteractive

    /** Các ô Kênh nhanh đang có trên tivi: (số ô, tên kênh). Tên đọc từ chính ô qua ADB; chưa nối thì "Kênh nhanh n". */
    fun dsKenh(): List<Pair<Int, String>> = (1..XuLyLenh.SO_O).mapNotNull { o ->
        val goi = XuLyLenh.goiKenh(o)
        if (!coApp(goi)) return@mapNotNull null
        val ten = lenh("content call --uri content://$goi --method doc", 3000)?.let(XuLyLenh::tenKenh)
        o to (ten ?: if (kho.cauHinh.tiengAnh) "Quick channel $o" else "Kênh nhanh $o")
    }

    /** Tên tivi đặt trong Cài đặt (device_name), không có thì tên máy. */
    fun tenTivi(): String = runCatching { Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    /** Địa chỉ IPv4 trong mạng nhà; "" nếu không thấy. */
    fun ip(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull().orEmpty()

    /**
     * Mở [i]; tivi đang ở màn bảo vệ thì sáng màn hình như bấm remote trước (như Map Voice, Cast), không thì app mở phía
     * sau màn bảo vệ.
     */
    @Suppress("DEPRECATION")
    fun mo(i: Intent?): Boolean = i != null && try {
        runCatching {
            getSystemService(PowerManager::class.java).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "tivikit:teleremote").acquire(3_000)
        }
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Gói các app Home (launcher) trên tivi: không có trong danh sách app nhưng vẫn là "đang mở". */
    private fun home(): Set<String> = runCatching {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
    }.getOrDefault(emptySet())

    fun moApp(goi: String): Intent? = runCatching {
        packageManager.getLeanbackLaunchIntentForPackage(goi) ?: packageManager.getLaunchIntentForPackage(goi)
    }.getOrNull()

    fun coApp(goi: String): Boolean = try {
        packageManager.getApplicationInfo(goi, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun tenApp(goi: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(goi, 0)).toString() }.getOrDefault(goi)

    private fun hien(chu: String, dai: Boolean = false) {
        chinh.post { Toast.makeText(applicationContext, chu, if (dai) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        tat()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tat()
        super.onDestroy()
    }

    private fun tat() {
        bot?.dung()
        bot = null
        adb?.dung()
        adb = null
        if (dangChay === this) dangChay = null
    }

    companion object {
        @Volatile var dangChay: TeleRemoteDichVu? = null
    }
}
