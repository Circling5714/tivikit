package vn.tivikit.hengio

import android.app.AlarmManager
import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import org.json.JSONObject
import java.time.ZoneId

/**
 * TiviKit đặt / đọc lịch qua ADB:
 *   content call --uri content://vn.tivikit.hengio --method dat --arg '[{"gio":6,"phut":30,"ngay":[1,2,3,4,5],"loai":"bat"}]'
 *   content call --uri content://vn.tivikit.hengio --method thu --arg 120   (thử đánh thức sau 120 giây)
 *   content call --uri content://vn.tivikit.hengio --method doc
 * Việc một lần (31/10/2026, bản 1.2):
 *   content call --uri content://vn.tivikit.hengio --method mot-lan --arg '{"loai":"tat","sauPhut":30}'
 *     (hay {"loai":"bat|tat|mo","gio":6,"phut":30,"goi":"…"}: lần tới của giờ đó; trả id và luc)
 *   content call --uri content://vn.tivikit.hengio --method huy-mot-lan --arg tat-1793422740000
 *   content call --uri content://vn.tivikit.hengio --method ten-app --arg '{"vn.tivikit.kenh":"Kênh nhanh: VTV1"}'
 *   content call --uri content://vn.tivikit.hengio --method hoan        (màn báo trước đang đếm ngược: hoãn 30 phút; tat-ngay: tắt luôn)
 * Auto Reboot (2/11/2026, bản 1.4):
 *   content call --uri content://vn.tivikit.hengio --method kdl --arg '{"bat":true,"gio":4,"phut":0,"ngay":[1,2,3,4,5,6,7]}'
 *   content call --uri content://vn.tivikit.hengio --method kdl-adb   (nối thử ADB của tivi; lần đầu tivi hỏi Cho phép gỡ lỗi;
 *     kết quả ở doc → kdl.adb)
 *   content call --uri content://vn.tivikit.hengio --method kdl-thu   (khởi động lại ngay, lên lại thì tắt màn hình)
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đổi được lịch.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
        val ctx = context!!
        val kho = Kho(ctx)
        val kq = Bundle()
        when (method) {
            "dat" -> {
                val ds = Lich.docJson(arg) ?: return kq.apply { putString("loi", "lich sai") }
                kho.lich = ds
                kho.hoanDen = 0
                HenGio.datBaoThuc(ctx)
                kq.putString("ok", "1")
            }
            "thu" -> {
                val giay = arg?.trim()?.toIntOrNull()?.coerceIn(30, 900) ?: 120
                kho.thuDen = System.currentTimeMillis() + giay * 1000L
                kho.ghi("thu", "hen-$giay")
                HenGio.datBaoThuc(ctx)
                kq.putString("ok", "1")
            }
            "mot-lan" -> {
                val m = Lich.docMotLan(arg, System.currentTimeMillis(), ZoneId.systemDefault()) ?: return kq.apply { putString("loi", "viec sai") }
                HenGio.themMotLan(ctx, m)?.let { return kq.apply { putString("loi", it) } }
                kq.putString("ok", "1"); kq.putString("id", m.id); kq.putLong("luc", m.luc)
            }
            "huy-mot-lan" -> if (HenGio.huyMotLan(ctx, arg?.trim().orEmpty())) kq.putString("ok", "1") else kq.putString("loi", "khong-co")
            // Bấm từ Telegram lúc màn báo trước đang đếm ngược (Tele Remote, 31/10/2026): như bấm Hoãn 30 phút / Tắt ngay.
            "hoan", "tat-ngay" -> {
                val bt = BaoTat.dangMo ?: return kq.apply { putString("loi", "khong-con") }
                bt.tuXa(tat = method == "tat-ngay")
                kq.putString("ok", "1")
            }
            "kdl" -> {
                val c = AutoReboot.doc(arg) ?: return kq.apply { putString("loi", "kdl sai") }
                kho.kdl = c
                kho.kdlCho = 0
                HenGio.datBaoThuc(ctx)
                kq.putString("ok", "1")
            }
            "kdl-adb" -> { ChayAutoReboot.thuAdb(ctx); kq.putString("ok", "1") }
            "kdl-thu" -> { ChayAutoReboot.khoiDongLai(ctx, System.currentTimeMillis(), thu = true); kq.putString("ok", "1") }
            "ten-app" -> {
                val o = runCatching { JSONObject(arg.orEmpty()) }.getOrNull() ?: return kq.apply { putString("loi", "ten sai") }
                kho.tenApp = o.keys().asSequence().filter { Lich.laTenGoi(it) }.take(100).associateWith { o.optString(it).take(60) }
                kq.putString("ok", "1")
            }
            "doc" -> {
                val am = ctx.getSystemService(AlarmManager::class.java)
                val o = JSONObject()
                    .put("phienBan", runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull())
                    .put("lich", Lich.json(kho.lich))
                    .put("motLan", Lich.jsonMotLan(kho.motLanDs))
                    .put("keTiep", kho.keTiep).put("hoanDen", kho.hoanDen).put("thuDen", kho.thuDen)
                    .put("dichVuChay", HenGioDichVu.dangChay != null)
                    .put("chinhXac", am != null && HenGio.chinhXac(am))
                    .put("dangBat", ctx.getSystemService(PowerManager::class.java)?.isInteractive == true)
                    .put("bayGio", System.currentTimeMillis())
                    .put("muiGio", ZoneId.systemDefault().id)
                    .put("nhatKy", kho.nhatKy)
                    // Auto Reboot: cấu hình, lần kế tiếp (giờ hẹn hay lần kiểm lại), giờ hẹn đang chờ tivi tắt, ADB của app.
                    .put("kdl", kho.kdl.json().put("ke", kho.kdlKeTiep(System.currentTimeMillis(), ZoneId.systemDefault()) ?: 0L)
                        .put("cho", kho.kdlCho).put("adb", kho.kdlAdb))
                kq.putString("json", o.toString())
            }
            else -> kq.putString("loi", "khong biet $method")
        }
        return kq
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
