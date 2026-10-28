package vn.tivikit.cast

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.view.accessibility.AccessibilityEvent

/**
 * Dịch vụ trợ năng của Cast SmartTube: chỉ nghe đổi cửa sổ (tên app đang hiện), không đọc nội dung màn hình, không lọc
 * phím. Giữ máy nhận (DIAL + kênh Lounge) chạy suốt từ lúc tivi bật, để Android cho mở SmartTube khi app đang chạy nền, và
 * bấm Home cho SmartTube về nghe nền khi video mới tới lúc đang nghe nền. Người dùng thấy và tắt được trong Cài đặt → Trợ năng.
 */
class CastDichVu : AccessibilityService() {
    var may: MayThu? = null; private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        dangChay = this
        if (may == null) may = MayThu(this).also { it.batDau() }
    }

    /** Bàn phím ảo đang dùng: cửa sổ của nó không phải app đang xem. */
    private val boGo: String? by lazy {
        runCatching { android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD) }
            .getOrNull()?.substringBefore('/')
    }

    /** Chỉ lấy tên gói của cửa sổ vừa lên (biết SmartTube có đang ở trên màn hình không), không đọc nội dung. */
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val goi = e.packageName?.toString() ?: return
        if (goi == packageName || goi == "android" || goi == "com.android.systemui" || goi == boGo) return
        may?.doiTren(goi)
    }
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        tat()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tat()
        super.onDestroy()
    }

    private fun tat() {
        may?.dung()
        may = null
        if (dangChay === this) dangChay = null
    }

    companion object {
        @Volatile var dangChay: CastDichVu? = null
    }
}

/** Chỉ để Android cho đọc / điều khiển phiên media của SmartTube (MediaSessionManager đòi app nghe thông báo); không làm gì. */
class NgheThongBao : NotificationListenerService()
