package vn.tivikit.teleremote

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 📢 Lời nhắn từ Telegram hiện to trên màn hình tivi (1/11/2026), kèm một tiếng bíp: "Các con tắt tivi đi ngủ nhé!". Bấm OK
 * (hay Back) trên remote để đóng; lúc đó bot báo người gửi là tivi đã xem. Không ai bấm thì tự đóng sau [TOI_DA], không báo.
 * Lời nhắn mới tới thì thay lời nhắn đang hiện.
 */
class LoiNhan : Activity() {
    private var chat = 0L
    private val tay = Handler(Looper.getMainLooper())
    private val dong = Runnable { chat = 0; finish() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hien(intent)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        hien(i)
    }

    override fun onDestroy() {
        tay.removeCallbacks(dong)
        super.onDestroy()
    }

    private fun hien(i: Intent) {
        chat = i.getLongExtra(CHAT, 0)
        val en = i.getBooleanExtra(TIENG_ANH, false)
        fun chu(s: String, co: Float, mau: Int) = TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, co)
            setTextColor(mau)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
        }
        val dp = resources.displayMetrics.density
        val khung = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF510151C.toInt())
            setPadding((64 * dp).toInt(), (32 * dp).toInt(), (64 * dp).toInt(), (32 * dp).toInt())
        }
        val lp = { duoi: Float -> LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = (duoi * dp).toInt() } }
        khung.addView(chu("📢 " + (i.getStringExtra(NGUOI_GUI)?.takeIf { it.isNotBlank() } ?: "Telegram"), 22f, 0xFF9FC5FF.toInt()), lp(20f))
        khung.addView(chu(i.getStringExtra(CHU).orEmpty(), 40f, Color.WHITE), lp(28f))
        khung.addView(chu(if (en) "Press OK on the remote to close" else "Bấm OK trên remote để đóng", 18f, 0xFFB0B8C0.toInt()), lp(0f))
        setContentView(khung)
        bip()
        tay.removeCallbacks(dong)
        tay.postDelayed(dong, TOI_DA)
    }

    /** Một tiếng bíp nhỏ cho người đang ngồi trước tivi để ý. */
    private fun bip() = runCatching {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        tay.postDelayed({ tg.release() }, 800)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode in PHIM_DONG) {
            if (e.action == KeyEvent.ACTION_UP) {
                TeleRemoteDichVu.dangChay?.let { dv -> val c = chat; Thread { dv.daXemLoiNhan(c) }.start() }
                chat = 0
                finish()
            }
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    companion object {
        const val CHU = "chu"
        const val NGUOI_GUI = "nguoiGui"
        const val CHAT = "chat"
        const val TIENG_ANH = "tiengAnh"
        /** Không ai bấm OK thì lời nhắn tự đóng sau chừng này (ms). */
        private const val TOI_DA = 30 * 60_000L
        private val PHIM_DONG = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK)
    }
}
