package vn.tivikit.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Dịch vụ trợ năng nhỏ của menu ⏻ (28/10/2026): tắt tivi như bấm nút nguồn (khoá màn hình, Android 9+) và mở menu nguồn
 * của tivi (Khởi động lại khi tivi không mở ADB qua mạng; có ADB thì khởi động lại bằng AdbNoiBo, 29/10/2026). Không đọc
 * màn hình, không nhận sự kiện nào. TiviKit bật nó khi cài / cập nhật launcher.
 */
class NguonDichVu : AccessibilityService() {
    override fun onServiceConnected() { dangChay = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (dangChay === this) dangChay = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (dangChay === this) dangChay = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var dangChay: NguonDichVu? = null

        val chay: Boolean get() = dangChay != null

        /** Tắt màn hình tivi (về chế độ chờ như nút nguồn); false nếu dịch vụ chưa chạy hoặc Android cũ hơn 9. */
        fun tatTivi(): Boolean = Build.VERSION.SDK_INT >= 28 && dangChay?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        /** Menu nguồn của tivi (tắt máy / khởi động lại tuỳ hãng). */
        fun menuNguon(): Boolean = dangChay?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) == true
    }
}
