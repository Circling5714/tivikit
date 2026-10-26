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
 * app, kể cả HDMI) và nuốt mọi phím remote — chỉ còn số / mũi tên / OK để nhập PIN. Đúng PIN thì mở tạm
 * moTamPhut (mặc định 60). Sai 5 lần thì chờ 60 giây. Đang khoá thì dừng tiếng (giành audio focus + tắt tiếng nhạc).
 * Giới hạn mỗi ngày (25/10/2026): đếm thời gian tivi được xem, hết giờ thì khoá tới hết ngày; 5 phút trước khi khoá
 * (vì giờ khoá, hết giờ xem hay hết lượt mở tạm) hiện lời báo nhỏ ở góc màn hình.
 */
class KhoaDichVu : AccessibilityService() {
    companion object {
        @Volatile var dangChay: KhoaDichVu? = null
        @Volatile var dangKhoa = false
        /** Thời gian xem đã đếm nhưng chưa ghi vào Kho (ghi một phút một lần): thuộc ngày [ngayCho]. */
        @Volatile var ngayCho = ""
        @Volatile var msCho = 0L
        private const val NHIP_MS = 15_000L
        private const val SAI_TOI_DA = 5
        private const val CHAN_MS = 60_000L
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
    private val oPhim = mutableListOf<TextView>()
    private var chon = 4
    private var pin = ""
    private var focus: AudioFocusRequest? = null
    private val vi get() = Locale.getDefault().language == "vi"
    private fun t(v: String, e: String) = if (vi) v else e

    private val nhip = object : Runnable {
        override fun run() { capNhat(); tay.postDelayed(this, NHIP_MS) }
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
        tay.post(nhip)
    }

    override fun onDestroy() {
        tay.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(manHinh) }
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
                tvPhu.text = t("Hôm nay đã xem đủ ${Luat.chuThoiGian(gh, true)}. Mai xem tiếp nhé! Nhập mã PIN để mở thêm ${c.moTamPhut} phút.",
                    "That's ${Luat.chuThoiGian(gh, false)} of TV today. More tomorrow! Enter the PIN to add ${c.moTamPhut} minutes.")
            } else {
                val het = Luat.hetKhoaLuc(c!!, bay)?.let { Luat.chuGio(it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)) }
                tvBieuTuong.text = "🔒"
                tvTieuDe.text = t("Tivi đang nghỉ", "The TV is resting")
                tvPhu.text = (if (het != null) t("Mở lại lúc $het. ", "Unlocks at $het. ") else "") +
                    t("Nhập mã PIN để mở ${c.moTamPhut} phút.", "Enter the PIN to unlock for ${c.moTamPhut} minutes.")
            }
            veChan()
        } else if (c != null) {
            // Sắp khoá: báo một lần cho đợt này (không báo lúc màn hình tắt).
            val sap = Luat.sapKhoa(c, bay, daXem, kho.moTamDen)
            if (sap != null && !daBaoTruoc && dangXem()) { baoTruoc(sap); daBaoTruoc = true }
            if (sap == null) daBaoTruoc = false
        }
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

    /** Lời báo "Còn N phút nữa tivi sẽ nghỉ" ở góc trên, 15 giây, không che hình, không nhận phím. */
    private fun baoTruoc(phut: Int) {
        anBaoTruoc()
        val v = chu(t("Còn $phut phút nữa tivi sẽ nghỉ", "The TV rests in $phut min"), 18f, dam = true).apply {
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
            tay.removeCallbacks(anBang); tay.postDelayed(anBang, 15_000)
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
        pin = ""; chon = 4
        val cot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        tvBieuTuong = chu("🔒", 44f); cot.addView(tvBieuTuong)
        tvTieuDe = chu(t("Tivi đang nghỉ", "The TV is resting"), 34f, dam = true).apply { setPadding(0, dp(8), 0, dp(6)) }; cot.addView(tvTieuDe)
        tvPhu = chu("", 18f, Color.parseColor("#C9C3FF")); cot.addView(tvPhu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        tvCham = chu("", 34f).apply { letterSpacing = 0.4f; setPadding(0, dp(18), 0, dp(10)) }; cot.addView(tvCham)
        val luoi = GridLayout(this).apply { columnCount = 3 }
        oPhim.clear()
        PHIM.forEach { k ->
            val o = chu(k, 24f, dam = true).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = dp(72); height = dp(50); setMargins(dp(5), dp(5), dp(5), dp(5)) }
            }
            oPhim += o; luoi.addView(o)
        }
        cot.addView(luoi, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        tvBao = chu("", 16f, Color.parseColor("#FF8FA3")).apply { setPadding(0, dp(12), 0, 0) }; cot.addView(tvBao)
        cot.addView(chu(t("Phụ huynh đổi giờ khoá và giới hạn trong app TiviKit.", "Parents change the schedule and limits in the TiviKit app."), 13f, Color.parseColor("#8A84B8")).apply { setPadding(0, dp(18), 0, 0) })
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

    private fun vePin() { tvCham.text = (0 until 4).joinToString(" ") { if (it < pin.length) "●" else "○" } }

    private fun veChon() = oPhim.forEachIndexed { i, o ->
        o.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (i == chon) Color.parseColor("#8B5CF6") else Color.parseColor("#221B45"))
        }
    }

    private fun veChan() {
        val con = kho.chanDen - System.currentTimeMillis()
        if (con > 0) tvBao.text = t("Sai nhiều lần. Thử lại sau ${(con + 999) / 1000} giây.", "Too many tries. Try again in ${(con + 999) / 1000} s.")
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
        "⌫" -> { pin = pin.dropLast(1); vePin() }
        "✓" -> if (pin.length == 4) thu() else Unit
        else -> go(nhan)
    }

    private fun go(so: String) {
        if (System.currentTimeMillis() < kho.chanDen) { veChan(); return }
        if (pin.length >= 4) return
        pin += so; tvBao.text = ""; vePin()
        if (pin.length == 4) tay.postDelayed({ thu() }, 150)
    }

    private fun thu() {
        val c = kho.cauHinh ?: return
        if (Luat.dungPin(c, pin)) {
            kho.sai = 0
            kho.moTamDen = System.currentTimeMillis() + c.moTamPhut * 60_000L
            demGio() // quãng vừa khoá không tính là xem
            pin = ""; anKhoa()
            return
        }
        pin = ""; vePin()
        kho.sai = kho.sai + 1
        if (kho.sai >= SAI_TOI_DA) { kho.sai = 0; kho.chanDen = System.currentTimeMillis() + CHAN_MS; veChan() }
        else tvBao.text = t("Sai mã PIN, còn ${SAI_TOI_DA - kho.sai} lần.", "Wrong PIN, ${SAI_TOI_DA - kho.sai} tries left.")
    }
}
