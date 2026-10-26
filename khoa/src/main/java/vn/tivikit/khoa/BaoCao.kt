package vn.tivikit.khoa

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Báo cáo cho TiviKit (25/10/2026), [soNgay] ngày gần nhất, cũ trước:
 *   {homNay, demTu, coQuyen, ngay: [{ngay, thu, giay, gioiHan, apps: [{goi, ten, giay}]}]}
 * giay của ngày = thời gian tivi được xem do dịch vụ khoá đếm (giới hạn mỗi ngày tính theo số này; chỉ có từ ngày
 * [Kho.demTu]); apps = app đã mở, lấy từ thống kê của tivi (cần quyền GET_USAGE_STATS, TiviKit cấp qua ADB), không tính
 * màn hình chính.
 */
object BaoCao {
    private const val SO_APP = 8

    fun lap(ctx: Context, soNgay: Int = 7): JSONObject {
        val kho = Kho(ctx)
        val bay = Calendar.getInstance()
        val dau = (bay.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -(soNgay - 1))
        }
        val coQuyen = coQuyen(ctx)
        val apps = if (!coQuyen) emptyMap() else runCatching { thoiGianApp(ctx, dau.timeInMillis, bay.timeInMillis) }.getOrDefault(emptyMap())
        val pm = ctx.packageManager
        val ten = mutableMapOf<String, String>()
        fun tenApp(goi: String) = ten.getOrPut(goi) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(goi, 0)).toString() }.getOrDefault(goi) }
        val c = kho.cauHinh
        val ds = JSONArray()
        val x = dau.clone() as Calendar
        repeat(soNgay) {
            val ngay = Luat.ngay(x); val thu = Luat.thu(x)
            val top = apps[ngay].orEmpty().filterValues { it >= 60_000 }.entries.sortedByDescending { it.value }.take(SO_APP)
            ds.put(JSONObject().put("ngay", ngay).put("thu", thu).put("giay", KhoaDichVu.giayDaXem(kho, ngay))
                .put("gioiHan", c?.let { Luat.gioiHanPhut(it, thu) } ?: 0)
                .put("apps", JSONArray(top.map { JSONObject().put("goi", it.key).put("ten", tenApp(it.key)).put("giay", it.value / 1000) })))
            x.add(Calendar.DAY_OF_MONTH, 1)
        }
        return JSONObject().put("homNay", Luat.ngay(bay)).put("demTu", kho.demTu ?: "").put("coQuyen", coQuyen).put("ngay", ds)
    }

    private fun coQuyen(ctx: Context): Boolean = runCatching {
        val ao = ctx.getSystemService(AppOpsManager::class.java)
        val che = if (Build.VERSION.SDK_INT >= 29) ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            else @Suppress("DEPRECATION") ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        che == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Ngày → gói → mili giây ở trên cùng màn hình, bỏ màn hình chính (launcher), giao diện hệ thống và chính app này. */
    private fun thoiGianApp(ctx: Context, tu: Long, den: Long): Map<String, Map<String, Long>> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val bo = ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet() + setOf(ctx.packageName, "android", "com.android.systemui")
        // Đọc từ một ngày trước [tu]: app đang mở từ hôm trước vẫn có sự kiện lên màn hình.
        val ev = usm.queryEvents(tu - 24 * 3_600_000L, den) ?: return emptyMap()
        val ds = ArrayList<ThongKe.SuKien>()
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            // Mã sự kiện giữ nguyên từ Android 8: 1 lên màn hình, 2 rời màn hình, 16 màn hình tắt (9+), 26 tắt máy (10+).
            val loai = when (e.eventType) {
                1 -> ThongKe.LEN
                2 -> ThongKe.XUONG
                16, 26 -> ThongKe.TAT
                else -> continue
            }
            ds += ThongKe.SuKien(e.timeStamp, e.packageName ?: continue, loai)
        }
        return ThongKe.gom(ds, tu, den).mapValues { (_, m) -> m.filterKeys { it !in bo } }
    }
}
