package vn.tivikit.teleremote

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.Collator
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Dịch vụ trợ năng của Tele Remote (31/10/2026): chạy từ lúc tivi bật, giữ vòng nghe bot Telegram ([Bot]) và kết nối ADB
 * tới chính tivi ([GiuAdb], để bấm phím, gõ chữ, gọi các app phụ của TiviKit, chụp màn hình). Chỉ nghe đổi cửa sổ (app nào
 * vừa hiện, để báo "Đang mở"), không đọc nội dung màn hình, không lọc phím. Người dùng thấy và tắt được trong Cài đặt → Trợ
 * năng.
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
    /** Việc đang chờ tin chữ kế tiếp của từng chat (gõ chữ, mã mở khoá), giữ qua các tin và cả lệnh thu. */
    val choTin = ConcurrentHashMap<Long, LamViec.Cho>()
    /** Ảnh người dùng gửi gần nhất (🖼 Đặt làm ảnh nền). */
    @Volatile var anhCuoi: File? = null
    /** Việc bot tự để ý (máy lạ xin cast). */
    var theoDoi: TheoDoi? = null
        private set
    /** Lúc Tele Remote vừa tự tắt tivi (báo tin khỏi báo "vừa tắt" thêm lần nữa) và lúc màn hình đổi gần nhất. */
    @Volatile private var lucTuTat = 0L
    @Volatile private var lucManHinh = SystemClock.elapsedRealtime()

    /** Báo tin: màn hình vừa bật / tắt (bỏ qua khi vừa đổi chưa tới một phút: bấm nhầm, tivi chập chờn). */
    private val manHinhDoi = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val bay = SystemClock.elapsedRealtime()
            val truoc = lucManHinh
            lucManHinh = bay
            if (!::kho.isInitialized || !kho.cauHinh.baoTin || bay - truoc < 60_000) return
            when (i.action) {
                Intent.ACTION_SCREEN_ON -> baoTin(LamViec.chuBat(this@TeleRemoteDichVu))
                Intent.ACTION_SCREEN_OFF -> if (bay - lucTuTat > 15_000) baoTin(LamViec.chuTat(this@TeleRemoteDichVu))
            }
        }
    }

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
        if (theoDoi == null) theoDoi = TheoDoi(this).also { it.batDau() }
        val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        runCatching { if (Build.VERSION.SDK_INT >= 33) registerReceiver(manHinhDoi, f, RECEIVER_NOT_EXPORTED) else registerReceiver(manHinhDoi, f) }
    }

    val botDangNghe: Boolean get() = bot?.dangNghe == true

    /** Nhắn [chu] kèm nút [banPhim] cho các chủ tivi (máy lạ xin cast…). */
    fun baoChuKemNut(chu: String, banPhim: org.json.JSONObject) {
        bot?.baoChu(chu, banPhim)
    }

    /**
     * Trẻ bấm Xin bố mẹ thêm giờ trên màn khoá (app Khoá trẻ em): nhắn các chủ tivi kèm nút mở tạm / không cho. false nếu
     * chưa có bot hay chưa chủ nào nhắn bot.
     */
    fun xinThemGio(lyDo: String): Boolean {
        val ch = kho.cauHinh
        if (kho.token.isEmpty() || ch.nguoiDung.none { it.vaiTro == VaiTro.CHU && it.id != 0L }) return false
        val en = ch.tiengAnh
        val vi = when (lyDo) { "het-gio" -> "hết giờ xem hôm nay"; "gio-khoa" -> "đang trong giờ khoá"; "bo-me-khoa" -> "bố mẹ đã khoá"; "gio-sai" -> "tivi chưa có giờ đúng"; else -> "" }
        val enLy = when (lyDo) { "het-gio" -> "today's viewing time is used up"; "gio-khoa" -> "within locked hours"; "bo-me-khoa" -> "locked by a parent"; "gio-sai" -> "the TV's clock is wrong"; else -> "" }
        baoTin(if (en) "🧒 Someone on ${tenTivi()} asks for more TV time" + (if (enLy.isNotEmpty()) " ($enLy)." else ".")
            else "🧒 Trên tivi ${tenTivi()} có người xin thêm giờ xem" + (if (vi.isNotEmpty()) " ($vi)." else "."), XuLyLenh.bangXinGio(en))
        kho.ghi(tenTivi(), "xin-them|$lyDo")
        return true
    }

    /** Hẹn giờ sắp tắt tivi (màn báo trước vừa hiện): báo tin bật thì nhắn các chủ kèm nút hoãn / tắt luôn. */
    fun sapTat(luc: Long) {
        val ch = kho.cauHinh
        if (!ch.baoTin) return
        val gio = java.text.SimpleDateFormat("HH:mm", Locale.ROOT).format(java.util.Date(luc))
        baoTin(if (ch.tiengAnh) "⏰ ${tenTivi()} turns off at $gio by its Timer (about a minute left)." else "⏰ Tivi ${tenTivi()} sẽ tắt lúc $gio theo Hẹn giờ (còn chừng một phút).",
            XuLyLenh.bangSapTat(ch.tiengAnh))
    }

    /** Nhắn [chu] cho các chủ tivi (báo tin); giữ máy thức một lúc cho kịp gửi khi tivi vừa tắt màn hình. */
    fun baoTin(chu: String, banPhim: org.json.JSONObject? = null) {
        runCatching { getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tivikit:teleremote-bao").acquire(30_000) }
        bot?.baoChu(chu, banPhim)
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
    fun tatTivi(): Boolean {
        lucTuTat = SystemClock.elapsedRealtime()
        return if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else lenh("input keyevent 223", 4000) != null
    }

    /** Khởi động lại tivi bằng lệnh reboot qua ADB của chính tivi (như nút ⏻ của TiviKit Launcher; tivi MediaTek không tắt hẳn). */
    fun khoiDongLai() {
        thread(isDaemon = true) { lenh("reboot", 10_000) }
    }

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

    /** App mở được trên tivi (danh sách app của tivi): (gói, tên), xếp theo tên; bỏ chính Tele Remote. */
    fun dsApp(): List<Pair<String, String>> {
        val goi = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { loai ->
            runCatching { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(loai), 0) }.getOrDefault(emptyList())
        }.map { it.activityInfo.packageName }.distinct().filter { it != packageName }
        val so = Collator.getInstance(Locale("vi"))
        return goi.map { it to tenApp(it) }.sortedWith(compareBy(so) { it.second })
    }

    /** Gói app đang hiện trước mặt; "" nếu chưa biết. */
    fun goiDangMo(): String {
        appDangMo()
        return appHien
    }

    fun laHome(goi: String): Boolean = goi in home()

    /** Buộc dừng app [goi] qua ADB (như Quản lý app của TiviKit); false nếu chưa nối ADB. */
    fun dungApp(goi: String): Boolean = lenh("am force-stop $goi", 5000) != null

    /**
     * Gọi app phụ [goi] của TiviKit qua ADB (provider của chúng chỉ nhận lời gọi từ shell): content call, [thamSo] thuần ASCII
     * không có nháy đơn (XuLyLenh.thamSo, mã hẹn, mã số). null nếu chưa nối ADB / quá giờ.
     */
    fun goiAppPhu(goi: String, phuongThuc: String, thamSo: String? = null, choToiDa: Long = 15_000): String? {
        require(thamSo == null || thamSo.all { it in ' '..'~' && it != '\'' }) { "tham so" }
        return lenh("content call --uri content://$goi --method $phuongThuc" + (thamSo?.let { " --arg '$it'" } ?: ""), choToiDa)
    }

    /**
     * Gõ [chu] vào ô nhập đang chọn trên tivi (GoChuTr; [thay]: thay hết chữ đang có trong ô, không thì viết tiếp): "ok", mã
     * lỗi của bộ gõ ("khong-o-nhap"…), "can-bo-go" (chữ có dấu mà tivi chưa có bộ gõ của TiviKit), "chua-adb". Có bộ gõ của
     * TiviKit thì gõ mọi chữ qua nó (biết chắc chữ đã vào ô, chưa chọn ô thì báo); chưa có thì chữ không dấu gõ bằng
     * `input text` (chèn ở con trỏ, không biết có ô hay không).
     */
    fun goChu(chu: String, thay: Boolean): String {
        if (!coAdb()) return "chua-adb"
        if (!coApp(GoChuTr.GOI)) {
            if (GoChuTr.canBoGo(chu)) return "can-bo-go"
            return if (lenh(GoChuTr.lenhAscii(chu), 10_000) != null) "ok" else "chua-adb"
        }
        var cu = lenh("settings get secure default_input_method", 3000)?.trim().orEmpty()
        // Bộ gõ đang dùng là của TiviKit (lần trước bị ngắt giữa chừng): trả về bàn phím khác đang bật.
        if (!GoChuTr.laBoGoCu(cu)) cu = lenh("ime list -s", 3000).orEmpty().lines().map { it.trim() }.firstOrNull { GoChuTr.laBoGoCu(it) } ?: ""
        return GoChuTr.ketQua(lenh(GoChuTr.lenhBoGo(chu, cu, thay), 15_000).orEmpty())
    }

    /**
     * Chụp màn hình tivi (screencap qua ADB), thu nhỏ về rộng tối đa 1280 điểm, nén JPEG; null nếu chưa nối ADB / không
     * chụp được. App có nội dung bảo vệ (phim bản quyền) thì ảnh đen.
     */
    fun chup(): ByteArray? {
        val png = adb?.lenhByte("screencap -p", 20_000)?.takeIf { it.size > 100 } ?: return null
        val anh = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val nho = if (anh.width > 1280) Bitmap.createScaledBitmap(anh, 1280, anh.height * 1280 / anh.width, true) else anh
        return ByteArrayOutputStream().also { nho.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    /** Tệp mới cho ảnh / video người dùng gửi (thư mục riêng của app); chỉ giữ hai tệp gần nhất. */
    fun tepMoi(): File {
        val thuMuc = File(cacheDir, "nhan").apply { mkdirs() }
        thuMuc.listFiles()?.sortedByDescending { it.lastModified() }?.drop(1)?.forEach { it.delete() }
        return File(thuMuc, "tep-${System.currentTimeMillis()}")
    }

    /** Chiếu ảnh / phát video [tep] toàn màn hình tivi (XemTep). */
    fun chieu(tep: File, laVideo: Boolean): Boolean = mo(Intent(this, XemTep::class.java).putExtra(XemTep.TEP, tep.path).putExtra(XemTep.VIDEO, laVideo))

    /** Đưa [cau] cho Trợ lý Google của tivi như vừa nói (màn tìm kiếm của Trợ lý); false nếu tivi không có / đã tắt Trợ lý. */
    fun troLy(cau: String): Boolean = mo(Intent("android.search.action.GLOBAL_SEARCH").putExtra(SearchManager.QUERY, cau)
        .apply { if (coApp(KATNISS)) setPackage(KATNISS) })

    /**
     * Khoá trẻ em ngay, như mục trong menu ⏻ của TiviKit Launcher: màn GoiKhoaNgay gọi KhoaNgay của app khoá (chỉ nhận app
     * ký cùng khoá TiviKit, gọi bằng startActivityForResult). "" = đã khoá; không thì mã lỗi ("chua-co-ma", "dich-vu"…).
     */
    fun khoaNgay(): String {
        val f = CompletableFuture<String>()
        GoiKhoaNgay.ketQua = f
        if (!mo(Intent(this, GoiKhoaNgay::class.java), sang = false)) return "khong-mo-duoc"
        return runCatching { f.get(8, TimeUnit.SECONDS) }.getOrDefault("khong-tra-loi")
    }

    /**
     * Số đo cho Làm mới / tình trạng: RAM, bộ nhớ, thời gian chạy (của Android); [doCpu] thì đo thêm CPU bận trong 1 giây và
     * nhiệt độ chip qua ADB (app không đọc được /proc/stat).
     */
    fun soDo(doCpu: Boolean): ChuBot.SoDo {
        val mi = ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val st = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val kq = if (doCpu) lenh("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null; echo @@; head -n 1 /proc/stat; sleep 1; head -n 1 /proc/stat", 6000)?.split("@@") else null
        return ChuBot.SoDo(mi.totalMem, mi.availMem, kq?.getOrNull(1)?.let(ChuBot::tuProcStat), kq?.getOrNull(0)?.let(ChuBot::tuNhietDo),
            SystemClock.elapsedRealtime(), st?.totalBytes ?: 0, st?.availableBytes ?: 0)
    }

    /**
     * Bật màn hình (tivi đang chờ mà còn mạng, hay đang ở màn bảo vệ): phím WAKEUP qua ADB (thoát cả màn bảo vệ), chưa nối ADB
     * thì khoá thức của Android. true nếu sau đó màn hình bật.
     */
    @Suppress("DEPRECATION")
    fun batManHinh(): Boolean {
        if (lenh("input keyevent 224", 4000) == null) runCatching {
            getSystemService(PowerManager::class.java).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "tivikit:teleremote-bat").acquire(3_000)
        }
        Thread.sleep(1500)
        return dangSang() && manHinh().let { it.isEmpty() || it == "Awake" }
    }

    /**
     * Âm lượng nhạc / video: [lam] "dat" (mức [so]), "to" / "nho" (hai nấc), "tat" / "bat" (tắt / bật tiếng). Trả (mức, mức tối
     * đa, đang tắt tiếng).
     */
    fun amLuong(lam: String, so: Int): Triple<Int, Int, Boolean> {
        val am = getSystemService(AudioManager::class.java)
        val loai = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(loai)
        runCatching {
            when (lam) {
                "dat" -> am.setStreamVolume(loai, so.coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
                "to" -> repeat(2) { am.adjustStreamVolume(loai, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
                "nho" -> repeat(2) { am.adjustStreamVolume(loai, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
                "tat" -> am.adjustStreamVolume(loai, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "bat" -> am.adjustStreamVolume(loai, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            }
        }
        return Triple(am.getStreamVolume(loai), max, am.isStreamMute(loai))
    }

    /** Kiểm tra mạng như TiviKit (MangTr): đường ra Internet, Wi-Fi, ping router / Internet / YouTube; null nếu chưa nối ADB. */
    fun kiemTraMang(): MangTr.KetQua? {
        if (!coAdb()) return null
        val p = lenh("ip route get 1.1.1.1 2>&1; echo @@; dumpsys wifi 2>/dev/null | grep -m 1 mWifiInfo", 10_000)?.replace("\r", "")?.split("@@") ?: return null
        val duong = MangTr.tuIpRoute(p[0])
        val wifi = MangTr.tuWifiInfo(p.getOrElse(1) { "" })
        val lan = duong?.dev?.takeIf { MangTr.laLan(it) && Regex("^[A-Za-z0-9_.-]+$").matches(it) }
            ?.let { lenh("cat /sys/class/net/$it/speed 2>/dev/null", 3000)?.trim()?.toIntOrNull()?.takeIf { s -> s > 0 } }
        // Mỗi đích 10 gói cách 0,2 giây, chờ mỗi gói 1 giây (như TiviKit): chừng 2–3 giây một đích.
        fun ping(dich: String) = lenh("ping -c 10 -i 0.2 -W 1 $dich 2>&1", 40_000).orEmpty()
        val router = duong?.router?.takeIf { Regex("^[0-9a-fA-F.:]+$").matches(it) }?.let { MangTr.tuPing(ping(it)) }
        val net = MangTr.tuPing(ping("1.1.1.1"))
        val yt = ping("www.youtube.com")
        return MangTr.KetQua(duong, wifi, lan, router, net, MangTr.tuPing(yt), MangTr.loiTen(yt))
    }

    /** Hiện lời nhắn [chu] của [nguoiGui] lên màn hình tivi (LoiNhan); có người bấm OK thì báo về chat [chat]. */
    fun hienLoiNhan(chu: String, nguoiGui: String, chat: Long): Boolean = mo(Intent(this, LoiNhan::class.java)
        .putExtra(LoiNhan.CHU, chu).putExtra(LoiNhan.NGUOI_GUI, nguoiGui).putExtra(LoiNhan.CHAT, chat).putExtra(LoiNhan.TIENG_ANH, kho.cauHinh.tiengAnh))

    /** Có người bấm OK đóng lời nhắn trên tivi: báo người gửi. */
    fun daXemLoiNhan(chat: Long) {
        if (chat == 0L) return
        val gio = java.text.SimpleDateFormat("HH:mm", Locale.ROOT).format(java.util.Date())
        bot?.nhanRieng(chat, if (kho.cauHinh.tiengAnh) "👀 Someone on ${tenTivi()} read your message ($gio)." else "👀 Tivi ${tenTivi()} đã xem lời nhắn ($gio).")
    }

    /** Thư mục riêng của app trên bộ nhớ chung: shell (ADB) ghi / đọc được, app đọc được (quay màn hình, ảnh nền). */
    private fun thuMucChung(): File? = getExternalFilesDir(null)?.apply { mkdirs() }

    /** Quay màn hình tivi [giay] giây (screenrecord qua ADB, 720p, 2 Mbps), trả MP4; null nếu chưa nối ADB / không quay được. */
    fun quayManHinh(giay: Int): ByteArray? {
        val tep = File(thuMucChung() ?: return null, "quay.mp4")
        tep.delete()
        lenh("screenrecord --time-limit $giay --size 1280x720 --bit-rate 2000000 '${tep.absolutePath}'", (giay + 20) * 1000L) ?: return null
        return runCatching { tep.readBytes() }.getOrNull()?.takeIf { it.size > 1000 }.also { tep.delete() }
    }

    /**
     * Đặt [anh] (cắt giữa 16:9) làm ảnh nền TiviKit Launcher như TiviKit (content write anh-nen.jpg qua ADB, đọc lại cỡ ảnh
     * launcher đang dùng); [anh] null = về ảnh nền mặc định. false nếu chưa nối ADB / launcher không nhận.
     */
    fun datNenTkl(anh: File?): Boolean {
        if (!coAdb()) return false
        val uri = "content://${LamViec.TKL}/anh-nen.jpg"
        if (anh == null) return lenh("content delete --uri $uri 2>/dev/null; true", 5000) != null
        val du = AnhTr.nen(anh) ?: return false
        val tam = File(thuMucChung() ?: return false, "nen.jpg")
        tam.writeBytes(du)
        lenh("content write --uri $uri < '${tam.absolutePath}' 2>/dev/null", 15_000)
        var ok = false
        for (i in 1..10) {
            if (lenh("content call --uri content://${LamViec.TKL} --method doc", 3000)?.let(XuLyLenh::jsonTrong)?.optLong("nen") == du.size.toLong()) {
                ok = true
                break
            }
            Thread.sleep(300)
        }
        tam.delete()
        return ok
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
     * sau màn bảo vệ. [sang] = false: màn không cần thấy (gọi Khoá trẻ em ngay), không sáng màn hình.
     */
    @Suppress("DEPRECATION")
    fun mo(i: Intent?, sang: Boolean = true): Boolean = i != null && try {
        if (sang) runCatching {
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
        runCatching { unregisterReceiver(manHinhDoi) }
        theoDoi?.dung()
        theoDoi = null
        bot?.dung()
        bot = null
        adb?.dung()
        adb = null
        if (dangChay === this) dangChay = null
    }

    companion object {
        @Volatile var dangChay: TeleRemoteDichVu? = null
        /** Trợ lý Google bản tivi (Android TV / Google TV). */
        const val KATNISS = "com.google.android.katniss"
    }
}
