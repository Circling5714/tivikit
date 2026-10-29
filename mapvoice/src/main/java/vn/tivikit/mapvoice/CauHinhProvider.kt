package vn.tivikit.mapvoice

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject

/**
 * TiviKit đặt / đọc qua ADB:
 *   content call --uri content://vn.tivikit.mapvoice --method dat --arg '{"bat":true,"cachMo":"theo-cau",…}'
 *   content call --uri content://vn.tivikit.mapvoice --method kenh --arg '{"vtvgo":[{"ten":"VTV1","ma":"1"}],"tv360":[…]}'
 *   content call --uri content://vn.tivikit.mapvoice --method xem --arg '{"cau":"mở nhạc piano trên YouTube"}'  (chỉ xem hiểu thế nào)
 *   content call --uri content://vn.tivikit.mapvoice --method thu --arg '{"cau":"…"}'  (làm như vừa nói)
 *   content call --uri content://vn.tivikit.mapvoice --method noi-lai
 *   content call --uri content://vn.tivikit.mapvoice --method doc
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đổi được cấu hình hay đọc câu đã nói.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
        val ctx = context!!
        val kho = Kho(ctx)
        val dv = MapVoiceDichVu.dangChay
        val kq = Bundle()
        when (method) {
            "dat" -> {
                val ch = CauHinh.tuJson(arg) ?: return kq.apply { putString("loi", "cau hinh sai") }
                kho.cauHinh = ch
                kq.putString("ok", "1")
            }
            "kenh" -> {
                val so = XuLyCau.dsKenh(arg ?: "").size
                if (so == 0) return kq.apply { putString("loi", "kenh sai") }
                kho.kenhJson = arg!!
                kq.putString("ok", "1"); kq.putString("so", so.toString())
            }
            "xem", "thu" -> {
                val cau = runCatching { JSONObject(arg ?: "").optString("cau") }.getOrDefault("").trim()
                if (cau.isEmpty()) return kq.apply { putString("loi", "cau trong") }
                if (method == "thu") {
                    if (dv == null) return kq.apply { putString("loi", "dich-vu-tat") }
                    kq.putString("viec", dv.thu(cau))
                } else {
                    val coApp = { g: String -> co(ctx.packageManager, g) }
                    kq.putString("viec", moTa(XuLyCau.quyet(cau, kho.cauHinh, kho.dsKenh, coApp)))
                }
                kq.putString("ok", "1")
            }
            "noi-lai" -> {
                if (dv == null) return kq.apply { putString("loi", "dich-vu-tat") }
                dv.noiLai()
                kq.putString("ok", "1")
            }
            "doc" -> {
                val pm = ctx.packageManager
                val nk = JSONArray()
                kho.nhatKy.forEach { nk.put(JSONObject().put("luc", it.luc).put("cau", it.cau).put("viec", it.viec)) }
                val j = JSONObject()
                    .put("phienBan", runCatching { pm.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "")
                    .put("dichVuChay", dv != null)
                    .put("adb", dv?.trangThaiNghe?.ma ?: Nghe.TrangThai.TAT.ma)
                    .put("troLy", co(pm, XuLyCau.TRO_LY))
                    .put("appYouTube", dv?.appYouTube() ?: XuLyCau.SMARTTUBE.firstOrNull { co(pm, it) } ?: "")
                    .put("cauHinh", kho.cauHinh.json())
                    .put("soKenh", kho.dsKenh.size)
                    .put("nhatKy", nk)
                kq.putString("json", j.toString())
            }
            else -> kq.putString("loi", "khong biet $method")
        }
        return kq
    }

    /** Việc sẽ làm, dạng như MapVoiceDichVu.xuLy trả về (chưa làm gì). */
    private fun moTa(v: XuLyCau.Viec): String = when (v) {
        is XuLyCau.YouTube -> if (v.tim.isEmpty()) "yt-app|" else (if (v.phatLuon) "yt-phat|" else "yt-tim|") + v.tim
        is XuLyCau.MoKenh -> "kenh|${v.app}|${v.ten}"
        XuLyCau.BoQua -> "bo-qua|"
    }

    private fun co(pm: PackageManager, goi: String): Boolean = try {
        pm.getApplicationInfo(goi, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
