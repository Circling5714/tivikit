package vn.tivikit.hengio

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Đánh thức: bật màn hình (turnScreenOn), mở app đã hẹn nếu có, rồi đóng. Không hiện gì ngoài lời báo khi thử. */
class DanhThuc : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or if (Build.VERSION.SDK_INT < 27)
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED else 0)
        if (intent.getBooleanExtra("thu", false)) Toast.makeText(this, R.string.da_danh_thuc, Toast.LENGTH_LONG).show()
        val goi = intent.getStringExtra("goi")
        val ctx = applicationContext
        // Chờ tivi lên hẳn rồi mới mở app: tivi vừa thức còn đang chuyển nguồn hình, và launcher kiểu Home sạch tự mở Home
        // khoảng 2 giây sau khi đánh thức (Skyworth). Màn này có thể đã bị Home đè và đóng (noHistory): mở bằng ctx của app.
        Handler(Looper.getMainLooper()).postDelayed({
            if (goi != null && HenGio.moApp(ctx, goi)) HenGioDichVu.dangChay?.canhHome(goi)
            finish()
        }, CHO_MO_MS)
    }

    companion object {
        private const val CHO_MO_MS = 3000L
    }
}

/**
 * Báo trước khi tắt tivi: đếm ngược 60 giây, "Hoãn 30 phút" (chọn sẵn) hoặc "Tắt ngay". Bấm Back hay Home cũng là
 * hoãn — có người đang dùng tivi; không ai bấm gì thì hết giờ tivi tắt.
 */
class BaoTat : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private var con = (Lich.BAO_TRUOC_MS / 1000).toInt()
    private var xong = false
    private lateinit var chu: TextView

    private val dem = object : Runnable {
        override fun run() {
            if (con <= 0) { tat(); return }
            chu.text = getString(R.string.bao_tat, con)
            con--
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        chu = TextView(this).apply { textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        val hoan = Button(this).apply { text = getString(R.string.hoan_30); setOnClickListener { hoan() } }
        val tatNgay = Button(this).apply { text = getString(R.string.tat_ngay); setOnClickListener { tat() } }
        val nut = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(hoan)
            addView(tatNgay, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(20) })
        }
        val the = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(px(48), px(36), px(48), px(36))
            background = GradientDrawable().apply { setColor(0xF2151520.toInt()); cornerRadius = px(24).toFloat() }
            addView(chu)
            addView(nut, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(28) })
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            addView(the, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        })
        hoan.requestFocus()
        h.post(dem)
    }

    private fun tat() {
        if (xong) return
        xong = true; h.removeCallbacks(dem)
        HenGio.tatTivi(this)
        finish()
    }

    private fun hoan() {
        if (xong) return
        xong = true; h.removeCallbacks(dem)
        HenGio.hoan(this, 30)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { hoan(); return true }
        return super.onKeyDown(keyCode, event)
    }

    /** Bấm Home: có người đang dùng tivi. */
    override fun onUserLeaveHint() { hoan() }

    override fun onDestroy() { h.removeCallbacks(dem); super.onDestroy() }
}
