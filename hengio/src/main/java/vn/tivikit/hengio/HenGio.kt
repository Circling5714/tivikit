package vn.tivikit.hengio

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import java.time.ZoneId

/**
 * Bộ hẹn giờ trên tivi (25/10/2026): một báo thức hệ thống cho lần kế tiếp (Lich.keTiep), tới giờ thì bật tivi (đánh
 * thức màn hình rồi mở app nếu có), tắt tivi (báo trước 60 giây, có Hoãn) hoặc mở app. Lịch do TiviKit gửi qua ADB
 * (CauHinhProvider); tivi tự chạy, không cần điện thoại.
 */
object HenGio {
    const val BAO = "vn.tivikit.hengio.BAO"

    /** Báo thức đúng giờ được không (Android 12+ cần quyền báo thức chính xác; không có thì Android dời tối đa vài phút). */
    fun chinhXac(am: AlarmManager): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    /** Đặt báo thức cho lần kế tiếp; gọi sau mỗi lần đổi lịch, chạy việc, tivi khởi động, đổi giờ / múi giờ. */
    fun datBaoThuc(ctx: Context) {
        val kho = Kho(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val ke = Lich.keTiep(kho.lich, kho.motLan(), System.currentTimeMillis(), ZoneId.systemDefault())
        val i = Intent(ctx, BaoThuc::class.java).setAction(BAO)
        if (ke == null) {
            PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { am.cancel(it) }
            kho.keTiep = 0
            return
        }
        val pi = PendingIntent.getBroadcast(ctx, 1, i.putExtra("luc", ke.luc), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Tivi cắm điện nên không vào Doze; RTC_WAKEUP đánh thức cả lúc tivi đang ở chế độ chờ.
        if (chinhXac(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ke.luc, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ke.luc, pi)
        kho.keTiep = ke.luc
    }

    /** Chạy các việc đến hạn lúc [luc] (giờ đã hẹn, không phải giờ báo thức tới — Android có thể giao trễ vài giây). */
    fun thucHien(ctx: Context, luc: Long) {
        val kho = Kho(ctx)
        val laThu = kho.thuDen == luc
        val ke = Lich.keTiep(kho.lich, kho.motLan(), luc - 1, ZoneId.systemDefault())
        kho.xongMotLan(luc)
        if (ke == null || ke.luc != luc) return
        if (Lich.daLo(luc, System.currentTimeMillis())) { ke.viec.forEach { kho.ghi(it.loai, "lo-gio") }; return }
        val dangBat = ctx.getSystemService(PowerManager::class.java)?.isInteractive == true
        for (v in ke.viec) when (v.loai) {
            Viec.BAT -> if (!dangBat) {
                danhThuc(ctx, v.goi, laThu); kho.ghi(if (laThu) "thu" else "bat", "danh-thuc")
            } else {
                v.goi?.let { moApp(ctx, it) }; kho.ghi(if (laThu) "thu" else "bat", "dang-bat")
            }
            Viec.TAT -> if (dangBat) { moManHinh(ctx, Intent(ctx, BaoTat::class.java)); kho.ghi("tat", "bao-truoc") } else kho.ghi("tat", "da-tat-san")
            Viec.MO -> {
                val goi = v.goi ?: continue
                kho.ghi("mo:$goi", if (!dangBat) "tivi-dang-tat" else if (moApp(ctx, goi)) "ok" else "khong-mo-duoc")
            }
        }
    }

    /**
     * Đánh thức tivi: màn DanhThuc có turnScreenOn (cách Android khuyên dùng) và thêm khoá thức ACQUIRE_CAUSES_WAKEUP
     * (chạy trên Android 13 trở xuống; Android 14+ cần quyền TURN_SCREEN_ON, TiviKit cấp qua ADB), rồi mở [goi].
     */
    private fun danhThuc(ctx: Context, goi: String?, laThu: Boolean) {
        @Suppress("DEPRECATION")
        runCatching {
            ctx.getSystemService(PowerManager::class.java)?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "hengio:bat")?.acquire(10_000)
        }
        moManHinh(ctx, Intent(ctx, DanhThuc::class.java).putExtra("goi", goi).putExtra("thu", laThu))
    }

    fun moManHinh(ctx: Context, i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    /** Các app màn chính (launcher) trên tivi. */
    fun manChinh(ctx: Context): Set<String> = runCatching {
        ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet()
    }.getOrDefault(emptySet())

    /** Mở màn chính của [goi] (bản tivi trước, bản điện thoại sau). Kênh nhanh cũng mở theo đường này. */
    fun moApp(ctx: Context, goi: String): Boolean {
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(goi) ?: pm.getLaunchIntentForPackage(goi) ?: return false
        return runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Đưa tivi về chế độ chờ như bấm nút nguồn: cần dịch vụ trợ năng của app đang chạy, Android 9+. */
    fun tatTivi(ctx: Context): Boolean {
        val dv = HenGioDichVu.dangChay
        val ok = dv != null && Build.VERSION.SDK_INT >= 28 && dv.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        Kho(ctx).ghi("tat", if (ok) "ok" else if (dv == null) "dich-vu-tat" else "khong-tat-duoc")
        return ok
    }

    /** Hoãn tắt tivi [phut] phút: lần báo trước kế tiếp hiện lại lúc đó. */
    fun hoan(ctx: Context, phut: Int) {
        val kho = Kho(ctx)
        kho.hoanDen = System.currentTimeMillis() + phut * 60_000L
        kho.ghi("tat", "hoan-$phut")
        datBaoThuc(ctx)
    }
}
