package vn.tivikit.khoa

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Tivi khởi động / app cập nhật: bật lại dịch vụ khoá nếu đang bật khoá, hẹn canh gác 15 phút một lần. */
class KhoiDong : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        CanhGac.damBao(ctx)
        CanhGac.hen(ctx)
    }
}

/** Canh gác: ai tắt dịch vụ khoá trong Cài đặt → Trợ năng thì bật lại (cần WRITE_SECURE_SETTINGS, CleanTV cấp qua ADB). */
class CanhGac : JobService() {
    override fun onStartJob(p: JobParameters?): Boolean { damBao(this); return false }
    override fun onStopJob(p: JobParameters?) = false

    companion object {
        fun hen(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            js.schedule(JobInfo.Builder(2310, ComponentName(ctx, CanhGac::class.java)).setPeriodic(15 * 60_000L).setPersisted(true).build())
        }

        fun damBao(ctx: Context) {
            val c = Kho(ctx).cauHinh ?: return
            if (!c.bat) return
            val ten = ComponentName(ctx, KhoaDichVu::class.java).flattenToString()
            runCatching {
                val cr = ctx.contentResolver
                val s = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                if (!s.split(':').contains(ten)) Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (s.isBlank()) ten else "$s:$ten")
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
        }
    }
}
