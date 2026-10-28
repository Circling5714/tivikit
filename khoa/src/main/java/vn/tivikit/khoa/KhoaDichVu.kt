package vn.tivikit.khoa

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Dịch vụ khoá (23/10/2026): trong khung giờ phụ huynh đặt, phủ một lớp kín màn hình (lớp trợ năng nằm trên mọi
 * app, kể cả HDMI) và nuốt mọi phím remote — chỉ còn số / mũi tên / OK để nhập mã. Đúng mã thì mở tạm moTamPhut
 * (mặc định 60). Đang khoá thì dừng tiếng (giành audio focus + tắt tiếng nhạc).
 * Giới hạn mỗi ngày (25/10/2026): đếm thời gian tivi được xem, hết giờ thì khoá tới hết ngày; 5 phút trước khi khoá
 * (vì giờ khoá, hết giờ xem hay hết lượt mở tạm) hiện lời báo nhỏ ở góc màn hình.
 * Mã mở (28/10/2026): mã OTP 6 số trong app xác thực của phụ huynh hoặc mã dự phòng 8 số thay PIN 4 số (xem Otp,
 * XacThuc); sai 5 lần liền thì chờ, lần sau lâu hơn.
 * Khoá theo app (28/10/2026): app có luật khoá (KhoaApp) mà lên màn hình thì về Home, che lại hỏi mã của riêng app đó;
 * Back thì ra ngoài. Khoá đang bật thì Cài đặt, hộp gỡ / cài app và hộp cho phép gỡ lỗi ADB cũng phải nhập mã — trẻ
 * không xoá dữ liệu, buộc dừng hay gỡ được app khoá.
 * Bảng phụ huynh (28/10/2026, BangPhuHuynh): nút Phụ huynh dưới bàn phím số hay ô "Khoá trẻ em" trong danh sách app (PhuHuynh)
 * → nhập mã → bảng việc nhanh bằng remote, khi bố mẹ không cầm điện thoại.
 */
class KhoaDichVu : AccessibilityService() {
    companion object {
        @Volatile var dangChay: KhoaDichVu? = null
        @Volatile var dangKhoa = false
        /** Thời gian xem đã đếm nhưng chưa ghi vào Kho (ghi một phút một lần): thuộc ngày [ngayCho]. */
        @Volatile var ngayCho = ""
        @Volatile var msCho = 0L
        /** Như [msCho] cho các luật app có giới hạn phút: id luật → mili giây, cũng thuộc ngày [ngayCho]. */
        val msApp = ConcurrentHashMap<String, Long>()
        private const val NHIP_MS = 15_000L
        /** Gõ đủ 6 số mà sai: chờ chừng này xem có gõ tiếp thành mã dự phòng 8 số không rồi mới báo sai. */
        private const val CHO_GO_TIEP_MS = 2_500L
        /** Có yêu cầu quên mã: báo ở góc màn hình mỗi chừng này lúc tivi đang xem. */
        private const val BAO_QUEN_MS = 60 * 60_000L
        /** Lớp che một app / Cài đặt để yên chừng này thì tự đóng (đứng ở Home). */
        private const val CHE_APP_MS = 2 * 60_000L
        /** Nhập mã ở Cài đặt / hộp gỡ app / hộp cho phép ADB thì mở chừng này. */
        private const val MO_CAI_DAT_MS = 10 * 60_000L
        private const val HE_THONG = "com.android.systemui"
        private const val LOP_ADB = "UsbDebuggingActivity"
        /** Vân tay khoá RSA trong hộp cho phép ADB: MD5 là 16 cặp số hex nối bằng dấu hai chấm. */
        private val VAN_TAY = Regex("(?:[0-9A-Fa-f]{2}:){15,}[0-9A-Fa-f]{2}")
        private val PHIM = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓", PHU_HUYNH)
        /** Ô cuối, dưới bàn phím số: mở bảng phụ huynh (nhập mã trước). */
        private const val PHU_HUYNH = "⚙  Phụ huynh"

        /** Số giây tivi được xem ngày [ngay], tính cả phần chưa ghi. */
        fun giayDaXem(kho: Kho, ngay: String): Long = kho.giayXem(ngay) + if (ngay == ngayCho) msCho / 1000 else 0

        /** Số giây từng luật app đã dùng ngày [ngay], tính cả phần chưa ghi. */
        fun giayDung(kho: Kho, ngay: String): (String) -> Long {
            val da = kho.giayDung(ngay)
            return { id -> (da[id] ?: 0) + if (ngay == ngayCho) (msApp[id] ?: 0) / 1000 else 0 }
        }
    }

    /** Lớp khoá đang che gì: cả tivi (khung giờ / hết giờ xem), một app đang khoá, hay chỗ cần mã lúc khoá bật. */
    private sealed interface Che
    private object CheTivi : Che
    private data class CheApp(val goi: String, val khoa: KhoaApp.Khoa) : Che
    private data class CheBaoVe(val goi: String, val loai: BaoVe) : Che
    private enum class BaoVe { CAI_DAT, GO_APP, ADB }
    /** Nhập mã để mở bảng phụ huynh; [cheTruoc] = lớp đang che lúc bấm Phụ huynh (Back thì về đó). */
    private object CheMaPh : Che
    /** App khác của TiviKit xin mã bố mẹ (XacNhan): [viecXacNhan] hiện trên lớp, [xongXacNhan] nhận kết quả. */
    private object CheXacNhan : Che
    private var viecXacNhan = ""
    private var xongXacNhan: ((Boolean) -> Unit)? = null
    private var cheTruoc: Che? = null
    private val bangPh by lazy { BangPhuHuynh(this) }

    internal val tay = Handler(Looper.getMainLooper())
    internal lateinit var kho: Kho
    private var lop: View? = null
    private var che: Che? = null
    private lateinit var tvBieuTuong: TextView
    private lateinit var tvTieuDe: TextView
    private lateinit var tvPhu: TextView
    private lateinit var tvCham: TextView
    private lateinit var tvBao: TextView
    private lateinit var tvQuen: TextView
    private lateinit var tvGio: TextView
    private val oPhim = mutableListOf<TextView>()
    private var chon = 4
    /** Các số đang gõ: mã OTP 6 số hoặc mã dự phòng 8 số. */
    private var nhap = ""
    /** 6 số vừa gõ không phải mã OTP đúng, chưa tính là một lần sai (có thể đang gõ mã dự phòng). */
    private var choSai = false
    private val saiCho = Runnable { thu() }
    private var lanBaoQuen = 0L
    private var focus: AudioFocusRequest? = null
    private val vi get() = Locale.getDefault().language == "vi"
    internal fun t(v: String, e: String) = if (vi) v else e

    private val nhip = object : Runnable {
        override fun run() { capNhat(); tay.postDelayed(this, NHIP_MS) }
    }

    /**
     * Giờ tự động (27/10/2026): khung giờ khoá, giới hạn mỗi ngày và lượt mở tạm đều tính theo giờ tivi — trẻ tắt giờ tự
     * động rồi chỉnh giờ là né được khoá (tv-remote khoá luôn Cài đặt vì lý do này). Khoá đang bật thì ai tắt giờ tự động
     * là bật lại ngay, Android đồng bộ lại giờ mạng. Cần WRITE_SECURE_SETTINGS (TiviKit cấp qua ADB, như CanhGac).
     */
    private val gioTuDong = object : android.database.ContentObserver(tay) {
        override fun onChange(selfChange: Boolean) = giuGioTuDong()
    }

    private fun giuGioTuDong() {
        if (kho.cauHinh?.bat != true) return
        runCatching {
            val cr = contentResolver
            if (android.provider.Settings.Global.getInt(cr, android.provider.Settings.Global.AUTO_TIME, 1) == 0)
                android.provider.Settings.Global.putInt(cr, android.provider.Settings.Global.AUTO_TIME, 1)
        }
    }

    /** Màn hình tắt / bật, màn hình chờ chạy / thôi: đếm cho đúng lúc đổi. */
    private val manHinh = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { demGio(); dangCho = false; luuDem() }
                Intent.ACTION_SCREEN_ON -> lanDem = SystemClock.elapsedRealtime() // lúc màn tắt không tính
                Intent.ACTION_DREAMING_STARTED -> { demGio(); dangCho = true }
                Intent.ACTION_DREAMING_STOPPED -> { demGio(); dangCho = false }
            }
        }
    }

    override fun onServiceConnected() {
        kho = Kho(this)
        dangChay = this
        CanhGac.hen(this)
        lanDem = SystemClock.elapsedRealtime()
        napGoiHeThong()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_DREAMING_STARTED); addAction(Intent.ACTION_DREAMING_STOPPED)
        }
        runCatching { if (Build.VERSION.SDK_INT >= 33) registerReceiver(manHinh, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(manHinh, f) }
        runCatching { contentResolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.AUTO_TIME), false, gioTuDong) }
        giuGioTuDong()
        tay.post(nhip)
    }

    override fun onDestroy() {
        tay.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(manHinh) }
        runCatching { contentResolver.unregisterContentObserver(gioTuDong) }
        if (::kho.isInitialized) { demGio(); luuDem() }
        anBaoTruoc()
        anKhoa()
        dangChay = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    fun capNhatSau() = tay.post { capNhat() }

    private fun capNhat() {
        demGio()
        val c = kho.cauHinh
        val bay = Calendar.getInstance()
        val daXem = giayDaXem(kho, Luat.ngay(bay))
        val ly = Luat.lyDo(c, bay, daXem, kho.moTamDen)
        if (ly != null && che != CheTivi && che != CheMaPh && che != CheXacNhan) hienKhoa(CheTivi)
        if (ly == null && che == CheTivi) anKhoa()
        if (ly != null) {
            anBaoTruoc(); daBaoTruoc = false
            if (che == CheTivi) { veCheTivi(c!!, ly, bay); veChan() }
        } else if (c != null) {
            // Sắp khoá: báo một lần cho đợt này (không báo lúc màn hình tắt).
            val sap = Luat.sapKhoa(c, bay, daXem, kho.moTamDen)
            if (sap != null && !daBaoTruoc && dangXem()) { baoTruoc(sap); daBaoTruoc = true }
            if (sap == null) daBaoTruoc = false
            capNhatApp(c, bay)
            // Có yêu cầu quên mã: nhắc ở góc màn hình, một giờ một lần, để bố mẹ huỷ nếu không phải mình gửi.
            val luc = System.currentTimeMillis()
            if (kho.yeuCauGo > 0 && bang == null && dangXem() && luc - lanBaoQuen > BAO_QUEN_MS) { bao(chuQuen(trenManKhoa = false), 30_000); lanBaoQuen = luc }
        }
    }

    private fun veCheTivi(c: CauHinh, ly: Luat.LyDo, bay: Calendar) {
        if (ly == Luat.LyDo.HET_GIO) {
            val gh = Luat.gioiHanPhut(c, Luat.thu(bay))
            tvBieuTuong.text = "⏳"
            tvTieuDe.text = t("Hết giờ xem hôm nay", "TV time is up for today")
            tvPhu.text = t("Hôm nay đã xem đủ ${Luat.chuThoiGian(gh, true)}. Mai xem tiếp nhé! Bố mẹ nhập mã để mở thêm ${c.moTamPhut} phút.",
                "That's ${Luat.chuThoiGian(gh, false)} of TV today. More tomorrow! A parent's code adds ${c.moTamPhut} minutes.")
        } else {
            val het = Luat.hetKhoaLuc(c, bay)?.let { Luat.chuGio(it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)) }
            tvBieuTuong.text = "🔒"
            tvTieuDe.text = t("Tivi đang nghỉ", "The TV is resting")
            tvPhu.text = (if (het != null) t("Mở lại lúc $het. ", "Unlocks at $het. ") else "") +
                t("Bố mẹ nhập mã để mở ${c.moTamPhut} phút.", "A parent's code unlocks it for ${c.moTamPhut} minutes.")
        }
        veChan2Dong(bay)
    }

    /** Dòng giờ tivi + dòng quên mã, chung cho mọi lớp che. */
    private fun veChan2Dong(bay: Calendar) {
        // Giờ tivi: mã OTP tính theo giờ, giờ sai (hộp không pin giữ giờ, mất điện lúc chưa có mạng) thì mã không khớp.
        val gio = Luat.chuGio(bay.get(Calendar.HOUR_OF_DAY) * 60 + bay.get(Calendar.MINUTE))
        tvGio.text = t("Mã 6 số trong app xác thực của bố mẹ, hoặc mã dự phòng 8 số · Giờ tivi $gio",
            "The 6-digit code from the parent's authenticator app, or an 8-digit backup code · TV time $gio")
        val quen = chuQuen(trenManKhoa = true)
        tvQuen.text = quen; tvQuen.visibility = if (quen.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Lời báo có yêu cầu quên mã đang chờ ("" nếu không có): trên màn khoá thì nhập mã ngay đó, lời báo góc thì vào TiviKit. */
    private fun chuQuen(trenManKhoa: Boolean): String {
        val y = kho.yeuCauGo
        if (y <= 0) return ""
        val luc = java.text.SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(java.util.Date(y + XacThuc.CHO_QUEN_MS))
        return t("Có người bấm Quên mã Khoá trẻ em trong TiviKit: từ $luc ai cũng đổi được khoá mà không cần mã. Không phải bố mẹ gửi thì " +
            (if (trenManKhoa) "nhập đúng mã ở đây (hoặc trong TiviKit) để huỷ." else "vào Khoá trẻ em trong TiviKit, nhập đúng mã để huỷ."),
            "Someone tapped Forgot code for the Kids lock in TiviKit: from $luc anyone can change the lock without the code. If it wasn't you, " +
            (if (trenManKhoa) "enter the code here (or in TiviKit) to cancel." else "open Kids lock in TiviKit and enter the code to cancel."))
    }

    // ---------- Khoá theo app, bảo vệ Cài đặt (28/10/2026) ----------

    /** App đang ở trên cùng (theo sự kiện đổi cửa sổ) và lớp màn của nó; app thường mở gần nhất (không phải Home / hệ thống). */
    private var appTren: String? = null
    private var lopTren = ""
    private var appGanNhat: String? = null
    private var homes: Set<String> = emptySet()
    private var caiDat: Set<String> = emptySet()
    private var trinhCai: Set<String> = emptySet()
    /** App gần nhất đang khoá ở nhịp trước: khoá mới bắt đầu lúc nó phát nền thì dừng phát. */
    private var ganNhatDaKhoa = false
    /** App đã được báo "còn N phút" trong đợt này. */
    private var daBaoApp: String? = null
    private val anCheApp = Runnable { if (che == CheMaPh || che == CheXacNhan) veLai() else if (che is CheApp || che is CheBaoVe) roiDi() }

    /** Home, Cài đặt và trình gỡ / cài app của chính tivi này (tên gói khác nhau tuỳ hãng). */
    private fun napGoiHeThong() = runCatching {
        val pm = packageManager
        fun xuLy(vararg ds: Intent) = ds.mapNotNull { pm.resolveActivity(it, 0)?.activityInfo?.packageName }.filter { it != "android" }.toSet()
        homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet()
        caiDat = xuLy(Intent(android.provider.Settings.ACTION_SETTINGS),
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) +
            setOf("com.android.tv.settings", "com.android.settings")
        trinhCai = xuLy(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))) +
            setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
    }

    /** Bàn phím ảo đang dùng: cửa sổ của nó không phải app mới lên màn hình. */
    private fun laBoGo(goi: String) = runCatching {
        android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/') == goi
    }.getOrDefault(false)

    /**
     * Hộp "Cho phép gỡ lỗi qua USB?" của System UI. Nó là AlertActivity nên sự kiện báo lớp android.app.Dialog chứ không
     * phải UsbDebuggingActivity (Skyworth Google TV, 28/10/2026): nhận bằng vân tay khoá RSA (các cặp số hex) trong chữ
     * của hộp, ngôn ngữ nào cũng có.
     */
    private fun laHopAdb(e: AccessibilityEvent): Boolean {
        val lop = e.className?.toString().orEmpty()
        return lop.contains("UsbDebugging") || (lop == "android.app.Dialog" && e.text.any { it != null && VAN_TAY.containsMatchIn(it) })
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !::kho.isInitialized) return
        val goi = e.packageName?.toString() ?: return
        if (goi == packageName || goi == "android" || laBoGo(goi)) return
        // System UI chỉ xét hộp cho phép gỡ lỗi ADB.
        val lopMan = if (goi != HE_THONG) e.className?.toString().orEmpty() else if (laHopAdb(e)) LOP_ADB else return
        if (goi == appTren && lopMan == lopTren) return
        demGio() // quãng vừa rồi tính cho app cũ
        appTren = goi; lopTren = lopMan
        if (goi !in homes && goi != HE_THONG && goi !in caiDat && goi !in trinhCai) { appGanNhat = goi; daBaoApp = null }
        kiemTraApp(kho.cauHinh ?: return, Calendar.getInstance())
    }

    /** Lúc khoá đang bật: Cài đặt, hộp gỡ / cài app, hộp cho phép gỡ lỗi ADB cần mã (trừ khi bố mẹ vừa nhập mã). */
    private fun baoVe(c: CauHinh, goi: String, lopMan: String, ms: Long): BaoVe? {
        if (!c.bat || !c.coMa || ms < kho.moCaiDatDen) return null
        return when {
            goi in caiDat -> BaoVe.CAI_DAT
            goi in trinhCai -> BaoVe.GO_APP
            goi == HE_THONG && lopMan == LOP_ADB -> BaoVe.ADB
            else -> null
        }
    }

    /** App đang ở trên cùng có cần che không (khi cả tivi không khoá): cần thì che lại, hỏi mã. */
    private fun kiemTraApp(c: CauHinh, bay: Calendar) {
        if (che == CheTivi || che == CheMaPh || che == CheXacNhan || bangPh.dangMo) return
        val goi = appTren ?: return
        val moi: Che = baoVe(c, goi, lopTren, bay.timeInMillis)?.let { CheBaoVe(goi, it) }
            ?: KhoaApp.khoa(c, kho.hen, goi, bay, giayDung(kho, Luat.ngay(bay)), kho.moApp[goi] ?: 0)?.let { CheApp(goi, it) }
            ?: return // không cần che: lớp che app đang mở (nếu có) vẫn chờ nhập mã hay Back
        if (che != moi) hienKhoa(moi)
    }

    /** Mỗi nhịp lúc cả tivi không khoá: app đang mở tới giờ khoá thì che; sắp khoá thì báo; app phát nền vừa bị khoá thì dừng phát. */
    private fun capNhatApp(c: CauHinh, bay: Calendar) {
        val dung = giayDung(kho, Luat.ngay(bay))
        val hen = kho.hen
        val moApp = kho.moApp
        when (val ch = che) {
            is CheApp -> KhoaApp.khoa(c, hen, ch.goi, bay, dung, moApp[ch.goi] ?: 0)?.let { if (it != ch.khoa) hienKhoa(ch.copy(khoa = it)) else veCheApp(c, ch, bay) }
                ?: anKhoa() // hết giờ khoá trong lúc đang che: mở luôn
            // Khoá vừa tắt, hay bố mẹ vừa nhập mã ở chỗ khác: thôi che.
            is CheBaoVe -> if (!c.bat || !c.coMa || bay.timeInMillis < kho.moCaiDatDen) anKhoa() else veCheBaoVe(ch, bay)
            else -> kiemTraApp(c, bay)
        }
        appGanNhat?.let { g ->
            val khoaNay = KhoaApp.khoa(c, hen, g, bay, dung, moApp[g] ?: 0) != null
            if (khoaNay && !ganNhatDaKhoa && g != appTren) dungPhat() // app phát nền (SmartTube chạy nền…) vừa tới giờ khoá
            ganNhatDaKhoa = khoaNay
        }
        val g = appTren ?: return
        if (che != null || !dangXem() || g == daBaoApp) return
        KhoaApp.sapKhoa(c, hen, g, bay, dung, moApp[g] ?: 0)?.let { phut ->
            bao(t("Còn $phut phút nữa ${tenApp(g)} sẽ nghỉ", "${tenApp(g)} rests in $phut min"), 15_000); daBaoApp = g
        }
    }

    internal fun tenApp(goi: String): String = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(goi, 0)).toString() }.getOrDefault(goi)

    private fun gio(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let { Luat.chuGio(it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)) }

    private fun veCheApp(c: CauHinh, ch: CheApp, bay: Calendar) {
        val ten = tenApp(ch.goi)
        val k = ch.khoa
        tvBieuTuong.text = if (k.lyDo == KhoaApp.LyDo.HET_GIO) "⏳" else "🔒"
        tvTieuDe.text = t("$ten đang nghỉ", "$ten is resting")
        val vi = when (k.lyDo) {
            KhoaApp.LyDo.HEN -> if (k.den == KhoaApp.nuaDem(bay.timeInMillis)) "Bố mẹ hẹn khoá app này tới hết hôm nay." else "Bố mẹ hẹn khoá app này tới ${gio(k.den!!)}."
            KhoaApp.LyDo.LUON -> "Bố mẹ đã khoá app này."
            KhoaApp.LyDo.GIO_KHOA -> k.den?.let { "Mở lại lúc ${gio(it)}." } ?: "App này đang trong giờ khoá."
            KhoaApp.LyDo.HET_GIO -> "Hôm nay đã dùng đủ ${Luat.chuThoiGian(k.phut, true)}. Mai dùng tiếp nhé!"
        }
        val en = when (k.lyDo) {
            KhoaApp.LyDo.HEN -> if (k.den == KhoaApp.nuaDem(bay.timeInMillis)) "A parent locked this app for the rest of today." else "A parent locked this app until ${gio(k.den!!)}."
            KhoaApp.LyDo.LUON -> "A parent locked this app."
            KhoaApp.LyDo.GIO_KHOA -> k.den?.let { "Unlocks at ${gio(it)}." } ?: "This app is in a lock period."
            KhoaApp.LyDo.HET_GIO -> "That's ${Luat.chuThoiGian(k.phut, false)} today. More tomorrow!"
        }
        tvPhu.text = t("$vi Bố mẹ nhập mã để mở $ten ${c.moTamPhut} phút; bấm Back để ra ngoài.",
            "$en A parent's code opens $ten for ${c.moTamPhut} minutes; press Back to leave.")
        veChan2Dong(bay)
        veChan()
    }

    private fun veCheBaoVe(ch: CheBaoVe, bay: Calendar) {
        tvBieuTuong.text = "🛡"
        tvTieuDe.text = when (ch.loai) {
            BaoVe.CAI_DAT -> t("Cài đặt cần mã của bố mẹ", "Settings need a parent's code")
            BaoVe.GO_APP -> t("Gỡ / cài app cần mã của bố mẹ", "Installing or removing apps needs a parent's code")
            BaoVe.ADB -> t("Cho phép gỡ lỗi cần mã của bố mẹ", "Allowing debugging needs a parent's code")
        }
        tvPhu.text = t("Khoá trẻ em đang bật. Bố mẹ nhập mã để mở ${MO_CAI_DAT_MS / 60_000} phút; bấm Back để ra ngoài.",
            "The Kids lock is on. A parent's code opens it for ${MO_CAI_DAT_MS / 60_000} minutes; press Back to leave.")
        veChan2Dong(bay)
        veChan()
    }

    /** Dừng phát của app đang phát (app vừa bị khoá chạy nền): giành audio focus rồi bấm Dừng media; không tắt tiếng cả tivi. */
    private fun dungPhat() {
        val am = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).build()
            am.requestAudioFocus(r)
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            tay.postDelayed({ runCatching { am.abandonAudioFocusRequest(r) } }, 1_000)
        }
    }

    /** Bấm Back ở lớp che app / Cài đặt: ra Home (hộp cho phép ADB thì đóng hộp = không cho), bỏ lớp che. */
    private fun roiDi() {
        performGlobalAction(if ((che as? CheBaoVe)?.loai == BaoVe.ADB) GLOBAL_ACTION_BACK else GLOBAL_ACTION_HOME)
        anKhoa()
    }

    // ---------- Bảng phụ huynh (28/10/2026) ----------

    /** Ô "Khoá trẻ em" trong danh sách app của tivi (PhuHuynh): hỏi mã rồi mở bảng phụ huynh. */
    fun moBangPhuHuynh() = tay.post {
        if (!::kho.isInitialized) return@post
        val c = kho.cauHinh
        if (c == null || !c.coMa) {
            bao(t("Khoá trẻ em chưa có mã: cài bằng TiviKit trên điện thoại trước.", "The Kids lock has no code yet: set it up with TiviKit on a phone first."), 10_000)
            return@post
        }
        if (bangPh.dangMo || che == CheMaPh) return@post
        cheTruoc = che
        hienKhoa(CheMaPh)
    }

    /** Thôi nhập mã mở bảng (Back, để yên): về lớp che trước đó nếu còn cần, không thì bỏ che. */
    private fun veLai() {
        val truoc = cheTruoc
        cheTruoc = null
        if (che == CheXacNhan) traXacNhan(false)
        if (truoc != null && truoc != CheMaPh && truoc != CheXacNhan) hienKhoa(truoc) else anKhoa()
        capNhat()
    }

    /**
     * App khác của TiviKit xin mã bố mẹ trước một việc quản lý (XacNhan, 28/10/2026: Cast SmartTube duyệt / chặn điện thoại,
     * huỷ liên kết…). Khoá đang tắt hay chưa có mã thì cho qua ngay; tivi đang khoá cả / bảng phụ huynh đang mở thì thôi;
     * không thì hiện ô nhập mã như bảng phụ huynh: [xong] nhận true khi nhập đúng, false khi Back, để yên hay việc khác chen.
     */
    fun xacNhanChoApp(viec: String, xong: (Boolean) -> Unit) = tay.post {
        val c = if (::kho.isInitialized) kho.cauHinh else null
        if (c == null || !c.coMa || !c.bat) { xong(true); return@post }
        if (bangPh.dangMo || che == CheTivi) { xong(false); return@post }
        traXacNhan(false)
        xongXacNhan = xong
        viecXacNhan = viec
        if (che != CheXacNhan) cheTruoc = che
        hienKhoa(CheXacNhan)
    }

    /** Báo kết quả cho app đang chờ xác nhận (một lần). */
    private fun traXacNhan(ok: Boolean) {
        val f = xongXacNhan ?: return
        xongXacNhan = null
        f(ok)
    }

    private fun veCheXacNhan(bay: Calendar) {
        tvBieuTuong.text = "🔑"
        tvTieuDe.text = t("Cần mã của bố mẹ", "A parent's code is needed")
        tvPhu.text = t("Nhập mã của bố mẹ để $viecXacNhan. Bấm Back để thôi.", "Enter a parent's code to $viecXacNhan. Press Back to cancel.")
        veChan2Dong(bay)
        veChan()
    }

    /** Bảng phụ huynh vừa đóng: xét lại khoá ngay (che lại nếu còn trong giờ khoá), báo việc vừa làm ở góc. */
    internal fun sauBang(baoChu: String?) {
        capNhat()
        if (baoChu != null && che == null) bao(baoChu, 15_000)
    }

    /** App đang khoá lúc này (theo luật / hẹn khoá, trừ app bố mẹ đã mở riêng), theo tên. */
    internal fun appDangKhoa(): List<String> {
        val c = kho.cauHinh ?: return emptyList()
        val bay = Calendar.getInstance()
        val hen = kho.hen; val moApp = kho.moApp; val dung = giayDung(kho, Luat.ngay(bay))
        return KhoaApp.goiCo(c, hen).filter { KhoaApp.khoa(c, hen, it, bay, dung, moApp[it] ?: 0) != null }.sortedBy { tenApp(it).lowercase() }
    }

    /** App mở được trên tivi để khoá (trừ Home, Cài đặt, chính app này): (gói, tên), theo tên. */
    internal fun dsAppMo(): List<Pair<String, String>> = runCatching {
        val pm = packageManager
        (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .map { it.activityInfo.packageName }.distinct()
            .filter { it != packageName && it !in homes && it !in caiDat }
            .map { it to tenApp(it) }.sortedBy { it.second.lowercase() }
    }.getOrDefault(emptyList())

    /** Dòng tóm tắt đầu bảng: hôm nay xem bao lâu, giới hạn, đang mở tạm tới đâu. */
    internal fun tomTat(): String {
        val c = kho.cauHinh
        val bay = Calendar.getInstance()
        val phut = (giayDaXem(kho, Luat.ngay(bay)) / 60).toInt()
        val gh = c?.let { Luat.gioiHanPhut(it, Luat.thu(bay)) } ?: 0
        return t("Hôm nay đã xem ", "Watched today: ") + Luat.chuThoiGian(phut, vi) +
            (if (gh > 0) t(" / giới hạn ", " / limit ") + Luat.chuThoiGian(gh, vi) else "") +
            (kho.moTamDen.takeIf { it > bay.timeInMillis }?.let { t(" · đang mở tới ${gio(it)}", " · unlocked until ${gio(it)}") } ?: "") +
            (if (c?.bat != true) t(" · khoá đang tắt", " · the lock is off") else "")
    }

    private fun veCheMaPh(bay: Calendar) {
        tvBieuTuong.text = "🔑"
        tvTieuDe.text = t("Bảng phụ huynh", "Parent panel")
        tvPhu.text = t("Nhập mã của bố mẹ để mở bảng: mở tivi thêm, khoá hay mở từng app, hẹn giờ khoá. Bấm Back để thôi.",
            "Enter a parent's code to open the panel: unlock the TV for longer, lock or open single apps, set lock timers. Press Back to cancel.")
        veChan2Dong(bay)
        veChan()
    }

    // ---------- Đếm thời gian xem (25/10/2026) ----------

    /** Màn hình chờ (screensaver) đang chạy: không tính là đang xem. */
    private var dangCho = false
    private var lanDem = 0L

    private fun dangXem() = !dangCho && getSystemService(PowerManager::class.java)?.isInteractive == true

    /**
     * Cộng thời gian từ lần đếm trước nếu tivi đang được xem: màn hình sáng, không chạy màn hình chờ, không bị che. Cộng
     * cả cho các luật app có giới hạn phút của app đang ở trên cùng (28/10/2026).
     */
    private fun demGio() {
        val luc = SystemClock.elapsedRealtime()
        val qua = (luc - lanDem).coerceIn(0, NHIP_MS * 2) // máy ngủ sâu thì nhịp không chạy: không cộng cả quãng ngủ
        lanDem = luc
        val ngay = Luat.ngay(Calendar.getInstance())
        if (ngay != ngayCho) { luuDem(); ngayCho = ngay }
        if (lop == null && dangXem()) {
            msCho += qua
            appTren?.let { g -> KhoaApp.luatDem(kho.cauHinh, g).forEach { id -> msApp.merge(id, qua) { a, b -> a + b } } }
        }
        if (msCho >= 60_000 || msApp.values.any { it >= 60_000 }) luuDem()
    }

    /** Ghi phần đã đếm vào Kho (một phút một lần cho đỡ ghi bộ nhớ). */
    private fun luuDem() {
        if (ngayCho.isEmpty()) return
        if (msCho >= 1000) { kho.congXem(ngayCho, msCho / 1000); msCho %= 1000 }
        val ghi = msApp.filterValues { it >= 1000 }.mapValues { it.value / 1000 }
        kho.congDung(ngayCho, ghi)
        ghi.forEach { (id, giay) -> msApp.merge(id, -giay * 1000) { a, b -> a + b } }
    }

    // ---------- Báo trước ----------

    private var bang: View? = null
    private var daBaoTruoc = false
    private val anBang = Runnable { anBaoTruoc() }

    /** Lời báo "Còn N phút nữa tivi sẽ nghỉ" ở góc trên, 15 giây. */
    private fun baoTruoc(phut: Int) = bao(t("Còn $phut phút nữa tivi sẽ nghỉ", "The TV rests in $phut min"), 15_000)

    /** Lời báo nhỏ ở góc trên trong [ms], không che hình, không nhận phím. */
    private fun bao(s: String, ms: Long) {
        anBaoTruoc()
        val v = chu(s, 18f, dam = true).apply {
            maxWidth = dp(560)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#E6221B45")) }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = dp(32); y = dp(28) }
        runCatching { getSystemService(WindowManager::class.java).addView(v, lp) }.onSuccess {
            bang = v
            tay.removeCallbacks(anBang); tay.postDelayed(anBang, ms)
        }
    }

    private fun anBaoTruoc() {
        bang?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        bang = null
    }

    // ---------- Lớp khoá ----------

    internal fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    internal fun chu(s: String, sp: Float, mau: Int = Color.WHITE, dam: Boolean = false) = TextView(this).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(mau); gravity = Gravity.CENTER
        if (dam) typeface = Typeface.DEFAULT_BOLD
    }

    /** Che màn hình (hay đổi thứ đang che) theo [moi]: cả tivi thì về Home và tắt tiếng; một app thì về Home và dừng phát. */
    private fun hienKhoa(moi: Che) {
        val cu = che
        if (lop == null && bangPh.dangMo) return // bảng phụ huynh đang mở: đóng bảng thì xét lại (sauBang)
        if (lop == null && !taoLop()) return
        if (cu == CheTivi && moi != CheTivi) imTieng(false)
        if (cu is CheApp && moi !is CheApp) thaFocus()
        if (cu != moi) { nhap = ""; choSai = false; chon = 4; tvBao.text = ""; vePin(); veChon() }
        if (cu == CheXacNhan && moi != CheXacNhan) traXacNhan(false)
        oPhim.getOrNull(PHIM.lastIndex)?.visibility = if (moi == CheMaPh || moi == CheXacNhan) View.GONE else View.VISIBLE
        che = moi; dangKhoa = moi == CheTivi
        tay.removeCallbacks(anCheApp)
        when (moi) {
            CheTivi -> if (cu != CheTivi) { performGlobalAction(GLOBAL_ACTION_HOME); imTieng(true) } // app đang chạy lui về nền
            is CheApp -> {
                if (cu !is CheApp || cu.goi != moi.goi) { performGlobalAction(GLOBAL_ACTION_HOME); giuFocus() }
                kho.cauHinh?.let { veCheApp(it, moi, Calendar.getInstance()) }
                tay.postDelayed(anCheApp, CHE_APP_MS)
            }
            is CheBaoVe -> {
                // Hộp cho phép ADB để nguyên bên dưới cho bố mẹ bấm Cho phép sau khi nhập mã.
                if (moi.loai != BaoVe.ADB) performGlobalAction(GLOBAL_ACTION_HOME)
                veCheBaoVe(moi, Calendar.getInstance())
                tay.postDelayed(anCheApp, CHE_APP_MS)
            }
            CheMaPh -> { veCheMaPh(Calendar.getInstance()); tay.postDelayed(anCheApp, CHE_APP_MS) }
            CheXacNhan -> { veCheXacNhan(Calendar.getInstance()); tay.postDelayed(anCheApp, CHE_APP_MS) }
        }
    }

    private fun taoLop(): Boolean {
        val cot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        // Gọn cho vừa màn 540dp (720p và 1080p đều vậy) cả khi có dòng báo quên mã hai dòng (28/10/2026).
        tvBieuTuong = chu("🔒", 32f); cot.addView(tvBieuTuong)
        tvTieuDe = chu(t("Tivi đang nghỉ", "The TV is resting"), 34f, dam = true).apply { setPadding(0, dp(2), 0, dp(2)) }; cot.addView(tvTieuDe)
        tvPhu = chu("", 18f, Color.parseColor("#C9C3FF")); cot.addView(tvPhu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        tvCham = chu("", 34f).apply { letterSpacing = 0.4f; setPadding(0, dp(6), 0, dp(4)) }; cot.addView(tvCham)
        val luoi = GridLayout(this).apply { columnCount = 3 }
        oPhim.clear()
        PHIM.forEach { k ->
            val o = if (k == PHU_HUYNH) chu(t(k, "⚙  Parent"), 17f, dam = true).apply {
                layoutParams = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(0, 3)).apply {
                    width = dp(232); height = dp(40); setMargins(dp(4), dp(8), dp(4), dp(4))
                }
            } else chu(k, 24f, dam = true).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = dp(72); height = dp(44); setMargins(dp(4), dp(4), dp(4), dp(4)) }
            }
            oPhim += o; luoi.addView(o)
        }
        cot.addView(luoi, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        tvBao = chu("", 16f, Color.parseColor("#FF8FA3")).apply { setPadding(0, dp(8), 0, 0) }; cot.addView(tvBao)
        tvQuen = chu("", 14f, Color.parseColor("#FFB86B")).apply { setPadding(0, dp(8), 0, 0); visibility = View.GONE }; cot.addView(tvQuen)
        tvGio = chu("", 13f, Color.parseColor("#8A84B8")).apply { setPadding(0, dp(8), 0, 0) }; cot.addView(tvGio)
        val nen = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#FF0A0718"))
            addView(cot, FrameLayout.LayoutParams(dp(640), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT)
        return runCatching { getSystemService(WindowManager::class.java).addView(nen, lp) }.onSuccess { lop = nen }.isSuccess
    }

    private fun anKhoa() {
        lop?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        if (che == CheTivi) imTieng(false)
        if (che is CheApp) thaFocus()
        tay.removeCallbacks(anCheApp)
        lop = null; che = null; dangKhoa = false
    }

    private fun imTieng(bat: Boolean) {
        val am = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (bat) {
                giuFocus()
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            } else {
                thaFocus()
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
        }
    }

    /** Giành audio focus: app đang phát (kể cả phát nền) tự dừng. */
    private fun giuFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).build()
            am.requestAudioFocus(r); focus = r
        }
    }

    private fun thaFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        focus?.let { r -> runCatching { am.abandonAudioFocusRequest(r) } }; focus = null
    }

    /** Ô số: 6 cho mã OTP, gõ tới số thứ 7 thì thành 8 (mã dự phòng). */
    private fun vePin() {
        val o = if (nhap.length > Otp.SO_CHU_SO) Otp.SO_CHU_SO_DU_PHONG else Otp.SO_CHU_SO
        tvCham.text = (0 until o).joinToString(" ") { if (it < nhap.length) "●" else "○" }
    }

    private fun veChon() = oPhim.forEachIndexed { i, o ->
        o.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (i == chon) Color.parseColor("#8B5CF6") else Color.parseColor("#221B45"))
        }
    }

    private fun veChan() {
        val con = XacThuc.choCon(kho)
        if (con <= 0) return
        val s = (con + 999) / 1000
        tvBao.text = if (s > 60) t("Sai nhiều lần. Thử lại sau ${(s + 59) / 60} phút.", "Too many tries. Try again in ${(s + 59) / 60} min.")
            else t("Sai nhiều lần. Thử lại sau $s giây.", "Too many tries. Try again in $s s.")
    }

    // ---------- Phím remote ----------

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (bangPh.dangMo) {
            if (e.action == KeyEvent.ACTION_DOWN) tay.post { bangPh.phim(e.keyCode) }
            return true
        }
        if (lop == null) return false
        if (e.action == KeyEvent.ACTION_DOWN) tay.post { xuLy(e.keyCode) }
        return true // đang khoá: nuốt mọi phím, kể cả Home, Back, nút app, giọng nói
    }

    private fun xuLy(k: Int) {
        if (lop == null) return
        // Lớp che app / Cài đặt: Back, Home là ra ngoài; nhập mã mở bảng thì Back là thôi; để yên thì tự đóng sau CHE_APP_MS.
        if (che != CheTivi) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_HOME) { if (che == CheMaPh || che == CheXacNhan) veLai() else roiDi(); return }
            tay.removeCallbacks(anCheApp); tay.postDelayed(anCheApp, CHE_APP_MS)
        }
        val ph = che != CheMaPh && che != CheXacNhan // có ô Phụ huynh (số 12, dưới bàn phím số)
        when (k) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> go(('0' + (k - KeyEvent.KEYCODE_0)).toString())
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> go(('0' + (k - KeyEvent.KEYCODE_NUMPAD_0)).toString())
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (chon < 12 && chon % 3 > 0) chon--; veChon() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (chon < 12 && chon % 3 < 2) chon++; veChon() }
            KeyEvent.KEYCODE_DPAD_UP -> { if (chon == 12) chon = 10 else if (chon >= 3) chon -= 3; veChon() }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (chon < 9) chon += 3 else if (chon < 12 && ph) chon = 12; veChon() }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> bam(PHIM[chon])
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_BACK -> bam("⌫")
        }
    }

    private fun bam(nhan: String) = when (nhan) {
        // Xoá lùi sau khi 6 số vừa gõ sai: tính luôn một lần sai, không thì xoá số cuối gõ lại là dò được mã không giới hạn.
        "⌫" -> if (choSai) thu() else { nhap = nhap.dropLast(1); vePin() }
        "✓" -> thu()
        // Ô Phụ huynh: nhập mã để mở bảng phụ huynh thay vì mở khoá.
        PHU_HUYNH -> { cheTruoc = che; hienKhoa(CheMaPh) }
        else -> go(nhan)
    }

    private fun go(so: String) {
        if (XacThuc.choCon(kho) > 0) { veChan(); return }
        if (nhap.length >= Otp.SO_CHU_SO_DU_PHONG) return
        tay.removeCallbacks(saiCho) // gõ tiếp sau 6 số: đang gõ mã dự phòng
        nhap += so; tvBao.text = ""; vePin()
        when (nhap.length) {
            Otp.SO_CHU_SO -> if (XacThuc.dung(kho, nhap)) moKhoa() else { choSai = true; tay.postDelayed(saiCho, CHO_GO_TIEP_MS) }
            Otp.SO_CHU_SO_DU_PHONG -> tay.postDelayed({ thu() }, 150)
        }
    }

    /** Kiểm tra mã đang gõ (đủ 8 số, bấm ✓, hay 6 số sai mà không gõ tiếp); sai thì tính một lần sai. */
    private fun thu() {
        tay.removeCallbacks(saiCho)
        if (lop == null) return
        if (nhap.length == Otp.SO_CHU_SO_DU_PHONG && XacThuc.dung(kho, nhap)) { moKhoa(); return }
        // Chưa đủ 6 số mà bấm ✓: chưa thử gì, không tính.
        if (!choSai && nhap.length < Otp.SO_CHU_SO_DU_PHONG) return
        nhap = ""; choSai = false; vePin()
        when (val r = XacThuc.ghiSai(kho)) {
            is XacThuc.Sai -> tvBao.text = t("Sai mã, còn ${r.conLan} lần.", "Wrong code, ${r.conLan} tries left.")
            else -> veChan()
        }
    }

    /** Đúng mã: cả tivi thì mở tạm; app thì mở riêng app đó rồi mở lại nó; Cài đặt… thì mở 10 phút. */
    private fun moKhoa() {
        val c = kho.cauHinh ?: return
        tay.removeCallbacks(saiCho)
        val bay = System.currentTimeMillis()
        val ch = che
        demGio() // quãng vừa khoá không tính là xem
        nhap = ""; choSai = false
        when (ch) {
            CheTivi -> kho.moTamDen = bay + c.moTamPhut * 60_000L
            is CheApp -> kho.moRiengApp(ch.goi, bay + c.moTamPhut * 60_000L)
            is CheBaoVe -> kho.moCaiDatDen = bay + MO_CAI_DAT_MS
            CheMaPh, CheXacNhan, null -> {}
        }
        anKhoa()
        if (ch == CheMaPh) { cheTruoc = null; bangPh.mo(); return }
        if (ch == CheXacNhan) {
            val truoc = cheTruoc
            cheTruoc = null
            traXacNhan(true)
            if (truoc != null && truoc != CheMaPh) hienKhoa(truoc)
            return
        }
        runCatching {
            when {
                ch is CheApp -> packageManager.getLaunchIntentForPackage(ch.goi) ?: packageManager.getLeanbackLaunchIntentForPackage(ch.goi)
                ch is CheBaoVe && ch.loai == BaoVe.CAI_DAT -> Intent(android.provider.Settings.ACTION_SETTINGS)
                else -> null
            }?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
