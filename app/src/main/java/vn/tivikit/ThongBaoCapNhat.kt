package vn.tivikit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Báo "có bản mới" bằng thông báo Android cả khi app đang đóng (anh Alex 21/10/2026: "có chuông thông báo trong app
 * báo có update mới mời nâng cấp đc ko"). WorkManager hỏi cap-nhat.json ~12 giờ một lần, mỗi bản chỉ báo một lần.
 * 26/10/2026: Android 12+ tự tải và cài luôn lúc chạy nền (CapNhat.nenTuCai), cài xong báo "đã cập nhật".
 */
object ThongBaoCapNhat {
    private const val KENH = "cap-nhat"
    const val MO_CAP_NHAT = "mo_cap_nhat"

    fun hen(ctx: Context) {
        // Tắt tự cập nhật: huỷ cả việc đã hẹn từ bản dựng trước (WorkManager giữ việc qua lần cài đè).
        if (!BuildConfig.TU_CAP_NHAT) { WorkManager.getInstance(ctx).cancelUniqueWork("kiem-tra-ban-moi"); return }
        val viec = PeriodicWorkRequestBuilder<KiemTraBanMoi>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("kiem-tra-ban-moi", ExistingPeriodicWorkPolicy.KEEP, viec)
    }

    /** Mời cập nhật bản [b] (chạm để mở app và cài); mỗi bản báo một lần. */
    fun bao(ctx: Context, b: CapNhat.BanMoi) {
        val p = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        if (p.getInt("da_bao", 0) >= b.versionCode) return
        if (hien(ctx, 1, t("TiviKit ${b.versionName} đã có", "TiviKit ${b.versionName} is out"), b.ghiChu, t("Chạm để cập nhật.", "Tap to update."), moCapNhat = true))
            p.edit().putInt("da_bao", b.versionCode).apply()
    }

    /** Đã tự cài bản [b] lúc chạy nền: báo để người dùng biết app vừa đổi bản và bản đó có gì. */
    fun baoDaCai(ctx: Context, b: CapNhat.BanMoi) {
        hien(ctx, 2, t("TiviKit đã cập nhật lên ${b.versionName}", "TiviKit updated to ${b.versionName}"), b.ghiChu, "", moCapNhat = false)
    }

    /** Một thông báo của kênh cập nhật; false khi không được phép hiện (Android 13+ chưa cho quyền thông báo). */
    private fun hien(ctx: Context, id: Int, tieuDe: String, ghiChu: String, cuoi: String, moCapNhat: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(KENH, t("Bản mới của app", "App updates"), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val mo = PendingIntent.getActivity(ctx, id,
            Intent(ctx, MainActivity::class.java).putExtra(MO_CAP_NHAT, moCapNhat).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val chu = listOf(ghiChu, cuoi).filter { it.isNotBlank() }.joinToString("\n")
        val tb = NotificationCompat.Builder(ctx, KENH)
            .setSmallIcon(R.drawable.ic_thong_bao)
            .setContentTitle(tieuDe)
            .setContentText(ghiChu.ifBlank { cuoi })
            .setStyle(NotificationCompat.BigTextStyle().bigText(chu))
            .setColor(0xFF8B5CF6.toInt())
            .setContentIntent(mo)
            .setAutoCancel(true)
            .build()
        return runCatching { NotificationManagerCompat.from(ctx).notify(id, tb) }.isSuccess
    }
}

class KiemTraBanMoi(ctx: Context, ts: WorkerParameters) : CoroutineWorker(ctx, ts) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        NgonNgu.nap(ctx)
        val b = runCatching { CapNhat.kiemTraApp() }.getOrElse { return Result.retry() } ?: return Result.success()
        // Android 12+: tải, kiểm tra SHA-256, cài lặng lẽ; KetQuaCai báo đã cập nhật (hoặc mời cài nếu Android vẫn đòi bấm).
        if (CapNhat.nenTuCai(ctx, b) && runCatching { CapNhat.cai(ctx, b, CapNhat.taiVe(ctx, b), ngam = true) }.isSuccess) return Result.success()
        ThongBaoCapNhat.bao(ctx, b)
        return Result.success()
    }
}
