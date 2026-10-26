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
            "doc" -> {
                val am = ctx.getSystemService(AlarmManager::class.java)
                val o = JSONObject()
                    .put("lich", Lich.json(kho.lich))
                    .put("keTiep", kho.keTiep).put("hoanDen", kho.hoanDen).put("thuDen", kho.thuDen)
                    .put("dichVuChay", HenGioDichVu.dangChay != null)
                    .put("chinhXac", am != null && HenGio.chinhXac(am))
                    .put("dangBat", ctx.getSystemService(PowerManager::class.java)?.isInteractive == true)
                    .put("bayGio", System.currentTimeMillis())
                    .put("muiGio", ZoneId.systemDefault().id)
                    .put("nhatKy", kho.nhatKy)
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
