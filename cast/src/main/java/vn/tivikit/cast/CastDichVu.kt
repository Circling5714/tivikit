package vn.tivikit.cast

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.view.accessibility.AccessibilityEvent

/**
 * Dịch vụ trợ năng của Cast SmartTube: không nghe sự kiện, không đọc màn hình, không lọc phím. Giữ máy nhận (DIAL + kênh
 * Lounge) chạy suốt từ lúc tivi bật, và để Android cho mở SmartTube khi app đang chạy nền. Người dùng thấy và tắt được
 * trong Cài đặt → Trợ năng.
 */
class CastDichVu : AccessibilityService() {
    var may: MayThu? = null; private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        dangChay = this
        if (may == null) may = MayThu(this).also { it.batDau() }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
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
