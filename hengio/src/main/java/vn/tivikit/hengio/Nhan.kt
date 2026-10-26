package vn.tivikit.hengio

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** Báo thức tới giờ (chỉ AlarmManager của chính app gửi được: không xuất ra ngoài). */
class BaoThuc : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == HenGio.BAO) HenGio.thucHien(ctx, i.getLongExtra("luc", 0))
        HenGio.datBaoThuc(ctx)
    }
}

/** Tivi khởi động / app cập nhật / đổi giờ hay múi giờ: báo thức cũ mất hoặc lệch, đặt lại. Việc đã lỡ thì bỏ, không chạy bù. */
class KhoiDongLai : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> HenGio.datBaoThuc(ctx)
        }
    }
}

/**
 * Dịch vụ trợ năng của Hẹn giờ: không nghe sự kiện nào, không đọc màn hình, không lọc phím (nên không ảnh hưởng nút
 * âm lượng như dịch vụ phím). Chỉ để đưa tivi về chế độ chờ đúng giờ (GLOBAL_ACTION_LOCK_SCREEN) và để Android cho mở
 * màn hình (đánh thức, báo trước, app hẹn mở) khi app đang chạy nền. Người dùng thấy và tắt được trong Cài đặt → Trợ năng.
 */
class HenGioDichVu : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        dangChay = this
        HenGio.datBaoThuc(this)
    }

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
        @Volatile var dangChay: HenGioDichVu? = null
    }
}
