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
 * thức màn hình rồi mở app nếu có), tắt tivi (báo trước 60 giây, có Hoãn) hoặc mở app. Lịch hằng tuần và việc một lần
 * (31/10/2026) do TiviKit gửi qua ADB (CauHinhProvider) hay đặt ở màn Hẹn giờ trên tivi (BangHenGio); tivi tự chạy, không
 * cần điện thoại.
 */
object HenGio {
    const val BAO = "vn.tivikit.hengio.BAO"

    /** Báo thức đúng giờ được không (Android 12+ cần quyền báo thức chính xác; không có thì Android dời tối đa vài phút). */
    fun chinhXac(am: AlarmManager): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    /** Đặt báo thức cho lần kế tiếp; gọi sau mỗi lần đổi lịch, chạy việc, tivi khởi động, đổi giờ / múi giờ. */
    fun datBaoThuc(ctx: Context) {
        val kho = Kho(ctx)
        // Đặt lại đúng trong phút báo trước của một lần tắt (tivi vừa khởi động lại, đổi giờ): báo ngay, mỗi lần tắt một lần.
        Lich.tatLoBao(kho.lich, System.currentTimeMillis(), ZoneId.systemDefault())?.let { tat ->
            if (kho.daBaoTat != tat) { kho.daBaoTat = tat; baoTat(ctx, kho, "bao-tre") }
        }
        motLanQua(ctx, kho, System.currentTimeMillis())
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
        if (Lich.daLo(luc, System.currentTimeMillis())) { ke.viec.forEach { kho.ghi(Lich.tenViec(it, laThu), "lo-gio") }; return }
        // Màn bảo vệ cũng tính là đang bật: bật tivi / mở app lúc đó phải tắt màn bảo vệ trước (sangManHinh).
        val dangBat = ctx.getSystemService(PowerManager::class.java)?.isInteractive == true
        for (v in ke.viec) when (v.loai) {
            Viec.BAT -> if (!dangBat) {
                danhThuc(ctx, v.goi, laThu); kho.ghi(Lich.tenViec(v, laThu), "danh-thuc")
            } else {
                sangManHinh(ctx, 3_000)
                kho.ghi(Lich.tenViec(v, laThu), "dang-bat")
                v.goi?.let { kho.ghi("mo:$it", if (moApp(ctx, it)) "ok" else "khong-mo-duoc") }
            }
            Viec.TAT -> {
                // Giờ tắt; datBaoThuc vừa báo trễ cho đúng lần này thì không báo lần nữa.
                val tat = luc + Lich.BAO_TRUOC_MS
                if (kho.daBaoTat != tat) { kho.daBaoTat = tat; baoTat(ctx, kho, "bao-truoc") }
            }
            Viec.MO -> {
                val goi = v.goi ?: continue
                val kq = if (!dangBat) "tivi-dang-tat" else { sangManHinh(ctx, 3_000); if (moApp(ctx, goi)) "ok" else "khong-mo-duoc" }
                kho.ghi(Lich.tenViec(v, laThu), kq)
            }
        }
    }

    /**
     * Việc một lần đã qua giờ báo mà chưa chạy (tivi vừa khởi động lại, đổi giờ): tắt tivi còn trong phút báo trước thì báo
     * ngay; việc khác trễ quá 1 phút thì bỏ, ghi lo-gio (chưa tới 1 phút thì có thể báo thức đang tới, để nguyên).
     */
    private fun motLanQua(ctx: Context, kho: Kho, bay: Long) {
        val ds = kho.motLanDs
        val bo = ds.filter { m ->
            when {
                m.bao > bay -> false
                m.loai == Viec.TAT && bay < m.luc -> {
                    if (kho.daBaoTat != m.luc) { kho.daBaoTat = m.luc; baoTat(ctx, kho, "bao-tre") }
                    true
                }
                bay - m.bao > 60_000L -> { kho.ghi(Lich.tenViec(m.viec(), false), "lo-gio"); true }
                else -> false
            }
        }
        if (bo.isNotEmpty()) kho.motLanDs = ds - bo.toSet()
    }

    /** Thêm việc một lần (TiviKit hay màn Hẹn giờ trên tivi); trả mã lỗi ("trung", "qua-nhieu"), null nếu xong. */
    fun themMotLan(ctx: Context, m: MotLan): String? {
        val kho = Kho(ctx)
        val ds = kho.motLanDs
        if (ds.any { it.id == m.id }) return "trung"
        if (ds.size >= Lich.MOT_LAN_TOI_DA) return "qua-nhieu"
        kho.motLanDs = ds + m
        datBaoThuc(ctx)
        return null
    }

    /** Huỷ việc một lần [id]; false nếu không còn việc đó (đã chạy hay đã huỷ). */
    fun huyMotLan(ctx: Context, id: String): Boolean {
        val kho = Kho(ctx)
        val ds = kho.motLanDs
        if (ds.none { it.id == id }) return false
        kho.motLanDs = ds.filter { it.id != id }
        datBaoThuc(ctx)
        return true
    }

    /** Lưu lịch hằng tuần sửa trên màn Hẹn giờ của tivi (như lệnh dat của TiviKit, nhưng giữ lần hoãn tắt đang có). */
    fun luuLich(ctx: Context, ds: List<Viec>) {
        Kho(ctx).lich = Lich.sapXep(ds)
        datBaoThuc(ctx)
    }

    /** Báo sắp tắt tivi (màn BaoTat đếm ngược rồi tắt); tivi đã ở chế độ chờ thì thôi. */
    private fun baoTat(ctx: Context, kho: Kho, ghiChu: String) {
        val dangBat = ctx.getSystemService(PowerManager::class.java)?.isInteractive == true
        if (dangBat) { moManHinh(ctx, Intent(ctx, BaoTat::class.java)); kho.ghi("tat", ghiChu) } else kho.ghi("tat", "da-tat-san")
    }

    /** Đánh thức tivi đang chờ: sáng màn hình, mở màn DanhThuc (turnScreenOn, cách Android khuyên dùng), màn đó mở [goi]. */
    private fun danhThuc(ctx: Context, goi: String?, laThu: Boolean) {
        sangManHinh(ctx, 10_000)
        moManHinh(ctx, Intent(ctx, DanhThuc::class.java).putExtra("goi", goi).putExtra("thu", laThu))
    }

    /**
     * Sáng màn hình như bấm remote (31/10/2026): tivi đang chờ thì bật lên, đang ở màn bảo vệ thì tắt nó. Màn bảo vệ vẫn
     * tính là đang bật, mà app mở lúc đó nằm phía sau nó, không ai thấy (app Cast, TX3 28/10/2026). Khoá thức
     * ACQUIRE_CAUSES_WAKEUP; Android 14+ cần quyền TURN_SCREEN_ON (TiviKit cấp qua ADB).
     */
    private fun sangManHinh(ctx: Context, ms: Long) {
        @Suppress("DEPRECATION")
        runCatching {
            ctx.getSystemService(PowerManager::class.java)?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "hengio:bat")?.acquire(ms)
        }
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
