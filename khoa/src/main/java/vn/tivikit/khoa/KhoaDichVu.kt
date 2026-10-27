package vn.tivikit.khoa

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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

/**
 * Dịch vụ khoá (23/10/2026): trong khung giờ phụ huynh đặt, phủ một lớp kín màn hình (lớp trợ năng nằm trên mọi
 * app, kể cả HDMI) và nuốt mọi phím remote — chỉ còn số / mũi tên / OK để nhập mã. Đúng mã thì mở tạm moTamPhut
 * (mặc định 60). Đang khoá thì dừng tiếng (giành audio focus + tắt tiếng nhạc).
 * Giới hạn mỗi ngày (25/10/2026): đếm thời gian tivi được xem, hết giờ thì khoá tới hết ngày; 5 phút trước khi khoá
 * (vì giờ khoá, hết giờ xem hay hết lượt mở tạm) hiện lời báo nhỏ ở góc màn hình.
 * Mã mở (28/10/2026): mã OTP 6 số trong app xác thực của phụ huynh hoặc mã dự phòng 8 số thay PIN 4 số (xem Otp,
 * XacThuc); sai 5 lần liền thì chờ, lần sau lâu hơn.
 */
class KhoaDichVu : AccessibilityService() {
    companion object {
        @Volatile var dangChay: KhoaDichVu? = null
        @Volatile var dangKhoa = false
        /** Thời gian xem đã đếm nhưng chưa ghi vào Kho (ghi một phút một lần): thuộc ngày [ngayCho]. */
        @Volatile var ngayCho = ""
        @Volatile var msCho = 0L
        private const val NHIP_MS = 15_000L
        /** Gõ đủ 6 số mà sai: chờ chừng này xem có gõ tiếp thành mã dự phòng 8 số không rồi mới báo sai. */
        private const val CHO_GO_TIEP_MS = 2_500L
        /** Có yêu cầu quên mã: báo ở góc màn hình mỗi chừng này lúc tivi đang xem. */
        private const val BAO_QUEN_MS = 60 * 60_000L
        private val PHIM = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")

        /** Số giây tivi được xem ngày [ngay], tính cả phần chưa ghi. */
        fun giayDaXem(kho: Kho, ngay: String): Long = kho.giayXem(ngay) + if (ngay == ngayCho) msCho / 1000 else 0
    }

    private val tay = Handler(Looper.getMainLooper())
    private lateinit var kho: Kho
    private var lop: View? = null
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
    private fun t(v: String, e: String) = if (vi) v else e

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

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun capNhatSau() = tay.post { capNhat() }

    private fun capNhat() {
        demGio()
        val c = kho.cauHinh
        val bay = Calendar.getInstance()
        val daXem = giayDaXem(kho, Luat.ngay(bay))
        val ly = Luat.lyDo(c, bay, daXem, kho.moTamDen)
        if (ly != null && lop == null) hienKhoa()
        if (ly == null && lop != null) anKhoa()
        if (ly != null) {
            anBaoTruoc(); daBaoTruoc = false
            if (ly == Luat.LyDo.HET_GIO) {
                val gh = Luat.gioiHanPhut(c!!, Luat.thu(bay))
                tvBieuTuong.text = "⏳"
                tvTieuDe.text = t("Hết giờ xem hôm nay", "TV time is up for today")
                tvPhu.text = t("Hôm nay đã xem đủ ${Luat.chuThoiGian(gh, true)}. Mai xem tiếp nhé! Bố mẹ nhập mã để mở thêm ${c.moTamPhut} phút.",
                    "That's ${Luat.chuThoiGian(gh, false)} of TV today. More tomorrow! A parent's code adds ${c.moTamPhut} minutes.")
            } else {
                val het = Luat.hetKhoaLuc(c!!, bay)?.let { Luat.chuGio(it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)) }
                tvBieuTuong.text = "🔒"
                tvTieuDe.text = t("Tivi đang nghỉ", "The TV is resting")
                tvPhu.text = (if (het != null) t("Mở lại lúc $het. ", "Unlocks at $het. ") else "") +
                    t("Bố mẹ nhập mã để mở ${c.moTamPhut} phút.", "A parent's code unlocks it for ${c.moTamPhut} minutes.")
            }
            // Giờ tivi: mã OTP tính theo giờ, giờ sai (hộp không pin giữ giờ, mất điện lúc chưa có mạng) thì mã không khớp.
            val gio = Luat.chuGio(bay.get(Calendar.HOUR_OF_DAY) * 60 + bay.get(Calendar.MINUTE))
            tvGio.text = t("Mã 6 số trong app xác thực của bố mẹ, hoặc mã dự phòng 8 số · Giờ tivi $gio",
                "The 6-digit code from the parent's authenticator app, or an 8-digit backup code · TV time $gio")
            val quen = chuQuen(trenManKhoa = true)
            tvQuen.text = quen; tvQuen.visibility = if (quen.isEmpty()) View.GONE else View.VISIBLE
            veChan()
        } else if (c != null) {
            // Sắp khoá: báo một lần cho đợt này (không báo lúc màn hình tắt).
            val sap = Luat.sapKhoa(c, bay, daXem, kho.moTamDen)
            if (sap != null && !daBaoTruoc && dangXem()) { baoTruoc(sap); daBaoTruoc = true }
            if (sap == null) daBaoTruoc = false
            // Có yêu cầu quên mã: nhắc ở góc màn hình, một giờ một lần, để bố mẹ huỷ nếu không phải mình gửi.
            val luc = System.currentTimeMillis()
            if (kho.yeuCauGo > 0 && bang == null && dangXem() && luc - lanBaoQuen > BAO_QUEN_MS) { bao(chuQuen(trenManKhoa = false), 30_000); lanBaoQuen = luc }
        }
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

    // ---------- Đếm thời gian xem (25/10/2026) ----------

    /** Màn hình chờ (screensaver) đang chạy: không tính là đang xem. */
    private var dangCho = false
    private var lanDem = 0L

    private fun dangXem() = !dangCho && getSystemService(PowerManager::class.java)?.isInteractive == true

    /** Cộng thời gian từ lần đếm trước nếu tivi đang được xem: màn hình sáng, không chạy màn hình chờ, không bị khoá. */
    private fun demGio() {
        val luc = SystemClock.elapsedRealtime()
        val qua = (luc - lanDem).coerceIn(0, NHIP_MS * 2) // máy ngủ sâu thì nhịp không chạy: không cộng cả quãng ngủ
        lanDem = luc
        val ngay = Luat.ngay(Calendar.getInstance())
        if (ngay != ngayCho) { luuDem(); ngayCho = ngay }
        if (lop == null && dangXem()) msCho += qua
        if (msCho >= 60_000) luuDem()
    }

    /** Ghi phần đã đếm vào Kho (một phút một lần cho đỡ ghi bộ nhớ). */
    private fun luuDem() {
        if (ngayCho.isNotEmpty() && msCho >= 1000) { kho.congXem(ngayCho, msCho / 1000); msCho %= 1000 }
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

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun chu(s: String, sp: Float, mau: Int = Color.WHITE, dam: Boolean = false) = TextView(this).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(mau); gravity = Gravity.CENTER
        if (dam) typeface = Typeface.DEFAULT_BOLD
    }

    private fun hienKhoa() {
        nhap = ""; choSai = false; chon = 4
        val cot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        // Gọn cho vừa màn 540dp (720p và 1080p đều vậy) cả khi có dòng báo quên mã hai dòng (28/10/2026).
        tvBieuTuong = chu("🔒", 36f); cot.addView(tvBieuTuong)
        tvTieuDe = chu(t("Tivi đang nghỉ", "The TV is resting"), 34f, dam = true).apply { setPadding(0, dp(4), 0, dp(4)) }; cot.addView(tvTieuDe)
        tvPhu = chu("", 18f, Color.parseColor("#C9C3FF")); cot.addView(tvPhu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        tvCham = chu("", 34f).apply { letterSpacing = 0.4f; setPadding(0, dp(10), 0, dp(6)) }; cot.addView(tvCham)
        val luoi = GridLayout(this).apply { columnCount = 3 }
        oPhim.clear()
        PHIM.forEach { k ->
            val o = chu(k, 24f, dam = true).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = dp(72); height = dp(46); setMargins(dp(4), dp(4), dp(4), dp(4)) }
            }
            oPhim += o; luoi.addView(o)
        }
        cot.addView(luoi, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        tvBao = chu("", 16f, Color.parseColor("#FF8FA3")).apply { setPadding(0, dp(8), 0, 0) }; cot.addView(tvBao)
        tvQuen = chu("", 14f, Color.parseColor("#FFB86B")).apply { setPadding(0, dp(8), 0, 0); visibility = View.GONE }; cot.addView(tvQuen)
        tvGio = chu("", 13f, Color.parseColor("#8A84B8")).apply { setPadding(0, dp(12), 0, 0) }; cot.addView(tvGio)
        val nen = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#FF0A0718"))
            addView(cot, FrameLayout.LayoutParams(dp(640), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT)
        runCatching { getSystemService(WindowManager::class.java).addView(nen, lp) }.onSuccess {
            lop = nen; dangKhoa = true
            performGlobalAction(GLOBAL_ACTION_HOME) // app đang chạy lui về nền
            imTieng(true)
            vePin(); veChon()
        }
    }

    private fun anKhoa() {
        lop?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        if (lop != null) imTieng(false)
        lop = null; dangKhoa = false
    }

    private fun imTieng(bat: Boolean) {
        val am = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (bat) {
                val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).build()
                am.requestAudioFocus(r); focus = r
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            } else {
                focus?.let { am.abandonAudioFocusRequest(it) }; focus = null
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
        }
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
        if (lop == null) return false
        if (e.action == KeyEvent.ACTION_DOWN) tay.post { xuLy(e.keyCode) }
        return true // đang khoá: nuốt mọi phím, kể cả Home, Back, nút app, giọng nói
    }

    private fun xuLy(k: Int) {
        if (lop == null) return
        when (k) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> go(('0' + (k - KeyEvent.KEYCODE_0)).toString())
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> go(('0' + (k - KeyEvent.KEYCODE_NUMPAD_0)).toString())
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (chon % 3 > 0) chon--; veChon() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (chon % 3 < 2) chon++; veChon() }
            KeyEvent.KEYCODE_DPAD_UP -> { if (chon >= 3) chon -= 3; veChon() }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (chon < 9) chon += 3; veChon() }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> bam(PHIM[chon])
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_BACK -> bam("⌫")
        }
    }

    private fun bam(nhan: String) = when (nhan) {
        // Xoá lùi sau khi 6 số vừa gõ sai: tính luôn một lần sai, không thì xoá số cuối gõ lại là dò được mã không giới hạn.
        "⌫" -> if (choSai) thu() else { nhap = nhap.dropLast(1); vePin() }
        "✓" -> thu()
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

    private fun moKhoa() {
        val c = kho.cauHinh ?: return
        tay.removeCallbacks(saiCho)
        kho.moTamDen = System.currentTimeMillis() + c.moTamPhut * 60_000L
        demGio() // quãng vừa khoá không tính là xem
        nhap = ""; choSai = false; anKhoa()
    }
}
