package vn.tivikit.go

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Bộ gõ trung gian (25/10/2026): lệnh `input text` của Android chỉ gõ được chữ không dấu, nên TiviKit bật tạm bộ gõ
 * này (`ime set`), gửi chữ qua ChuProvider (chỉ nhận từ ADB); bộ gõ đưa chữ vào ô nhập đang chọn rồi tự trả bàn phím cũ
 * của tivi. Không hiện bàn phím, không đọc chữ người dùng gõ bằng remote.
 */
class BoGo : InputMethodService() {
    companion object {
        @Volatile var dangChay: BoGo? = null
        private const val TU_TRA_MS = 15_000L

        /**
         * Gọi từ luồng của provider: chờ bộ gõ gắn vào một ô nhập chữ (tối đa [choMs]), đưa [chu] vào, rồi trả bàn
         * phím [cu]. Trả "ok", hoặc "chua-bat" (bộ gõ chưa được bật), "khong-o-nhap" (tivi không có ô nhập chữ đang
         * chọn, hoặc ô do app tự vẽ bàn phím), "khong-go-duoc".
         */
        fun go(chu: String, cu: String, choMs: Long = 2000): String {
            val het = SystemClock.uptimeMillis() + choMs
            while (dangChay?.coONhap != true && SystemClock.uptimeMillis() < het) Thread.sleep(40)
            val bg = dangChay ?: return "chua-bat"
            var kq = "khong-o-nhap"
            val xong = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                try { if (bg.coONhap) kq = if (bg.dua(chu)) "ok" else "khong-go-duoc" } finally { bg.traBanPhimCu(cu); xong.countDown() }
            }
            xong.await(5, TimeUnit.SECONDS)
            return kq
        }
    }

    /** Đang gắn với một ô nhập chữ thật (không phải cửa sổ không có ô nhập). */
    @Volatile private var coONhap = false
    private val tay = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        dangChay = this
        // Lưới an toàn: TiviKit rớt kết nối giữa lúc bật bộ gõ này và lúc gửi chữ → tự trả bàn phím cũ.
        tay.postDelayed({ traBanPhimCu(getSharedPreferences("go", Context.MODE_PRIVATE).getString("truoc", null)) }, TU_TRA_MS)
    }

    override fun onDestroy() {
        tay.removeCallbacksAndMessages(null)
        if (dangChay === this) dangChay = null
        super.onDestroy()
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        coONhap = info != null && info.inputType != InputType.TYPE_NULL
    }

    override fun onFinishInput() {
        coONhap = false
        super.onFinishInput()
    }

    // Không hiện bàn phím, không chiếm cả màn hình.
    override fun onEvaluateInputViewShown() = false
    override fun onEvaluateFullscreenMode() = false

    /** Đưa [chu] vào ô nhập; đọc lại một chữ trước con trỏ (lệnh chờ trả lời) để chắc app đã nhận trước khi đổi bàn phím. */
    private fun dua(chu: String): Boolean {
        val ic = currentInputConnection ?: return false
        if (!ic.commitText(chu, 1)) return false
        ic.getTextBeforeCursor(1, 0)
        return true
    }

    /** Trả bàn phím [cu]; không biết thì bàn phím dùng ngay trước bộ gõ này. */
    @Suppress("DEPRECATION") // Android 8.x: chưa có switchInputMethod, dùng InputMethodManager với khoá cửa sổ bộ gõ
    private fun traBanPhimCu(cu: String?) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                if (!cu.isNullOrBlank()) runCatching { switchInputMethod(cu) }.onFailure { switchToPreviousInputMethod() }
                else switchToPreviousInputMethod()
            } else {
                val imm = getSystemService(InputMethodManager::class.java)
                val the = window?.window?.attributes?.token
                if (!cu.isNullOrBlank()) imm.setInputMethod(the, cu) else imm.switchToLastInputMethod(the)
            }
        }
    }
}
