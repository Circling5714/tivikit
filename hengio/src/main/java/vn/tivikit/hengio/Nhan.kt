package vn.tivikit.hengio

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
 * Dịch vụ trợ năng của Hẹn giờ: không đọc màn hình, không lọc phím (nên không ảnh hưởng nút âm lượng như dịch vụ phím).
 * Để đưa tivi về chế độ chờ đúng giờ (GLOBAL_ACTION_LOCK_SCREEN) và để Android cho mở màn hình (đánh thức, báo trước, app
 * hẹn mở) khi app đang chạy nền. Bình thường không nghe sự kiện nào; chỉ nghe đổi cửa sổ vài giây sau khi bật tivi mở app
 * (canhHome). Người dùng thấy và tắt được trong Cài đặt → Trợ năng.
 */
class HenGioDichVu : AccessibilityService() {
    private val tay = Handler(Looper.getMainLooper())
    private var canhGoi: String? = null
    private var homes: Set<String> = emptySet()
    private val hetCanh = Runnable { boCanh() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        dangChay = this
        HenGio.datBaoThuc(this)
    }

    /**
     * Vừa bật tivi và mở [goi]: trong [CANH_MS] mà màn chính (launcher) nào lên trên thì mở lại [goi] một lần. Launcher
     * tự về Home lúc tivi vừa bật (Home sạch mở TiviKit Launcher 0,8 giây sau khi màn hình sáng) đè lên app vừa mở; app
     * chưa kịp chạy thì không bao giờ chạy nữa (Kênh nhanh không mở VTV1, Skyworth 28/10/2026).
     */
    fun canhHome(goi: String) {
        homes = HenGio.manChinh(this) - goi
        if (homes.isEmpty()) return
        canhGoi = goi
        nghe(true)
        tay.removeCallbacks(hetCanh); tay.postDelayed(hetCanh, CANH_MS)
    }

    private fun boCanh() {
        canhGoi = null
        tay.removeCallbacks(hetCanh)
        nghe(false)
    }

    /** Bật / tắt nghe đổi cửa sổ (chỉ tên gói, không đọc nội dung). */
    private fun nghe(bat: Boolean) {
        val info = serviceInfo ?: return
        info.eventTypes = if (bat) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
        info.notificationTimeout = if (bat) 100 else 1000
        serviceInfo = info
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val goi = canhGoi ?: return
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.packageName?.toString() !in homes) return
        boCanh()
        // Chờ Home lên xong rồi mới mở lại, không thì Home đang mở dở lại đè lên.
        tay.postDelayed({ HenGio.moApp(this, goi); Kho(this).ghi("mo:$goi", "mo-lai") }, 500)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (dangChay === this) dangChay = null
        boCanh()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (dangChay === this) dangChay = null
        tay.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        @Volatile var dangChay: HenGioDichVu? = null
        private const val CANH_MS = 10_000L
    }
}
