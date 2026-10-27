package vn.tivikit.launcher

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process
import org.json.JSONObject

/**
 * TiviKit đặt / đọc cấu hình qua ADB:
 *   content call --uri content://vn.tivikit.launcher --method dat --arg '{"ten":"Nhà bác Hùng","dock":["org.smarttube.stable"],"ngon_ngu":"vi"}'
 *   content call --uri content://vn.tivikit.launcher --method doc
 *   content call --uri content://vn.tivikit.launcher --method bat-home   (hoặc tat-home)
 * bat-home / tat-home bật / tắt bí danh .Home (bộ lọc HOME): ADB không đổi được trạng thái từng thành phần của app thường
 * ("Shell cannot change component state", chỉ đổi được cả gói), còn app thì tự đổi được thành phần của mình.
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đổi được.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
        val ctx = context!!
        val ch = CauHinh(ctx)
        val kq = Bundle()
        when (method) {
            "dat" -> {
                val moi = runCatching { JSONObject(arg.orEmpty()) }.getOrNull() ?: return kq.apply { putString("loi", "json sai") }
                var nhan = emptyList<String>()
                ch.sua { nhan = CauHinh.tron(it, moi) }
                kq.putString("ok", nhan.joinToString(",").ifEmpty { "-" })
            }
            "doc" -> {
                val pm = ctx.packageManager
                val pi = pm.getPackageInfo(ctx.packageName, 0)
                @Suppress("DEPRECATION")
                val ma = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
                val homeBat = pm.getComponentEnabledSetting(ComponentName(ctx, HOME)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                val o = JSONObject().put("ban", pi.versionName).put("ma", ma).put("homeBat", homeBat).put("cauHinh", ch.doc())
                kq.putString("json", o.toString())
            }
            "bat-home", "tat-home" -> {
                // tat-home trả về mặc định trong manifest (tắt). DONT_KILL_APP: đang chạy chính trong tiến trình này.
                val trangThai = if (method == "bat-home") PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                ctx.packageManager.setComponentEnabledSetting(ComponentName(ctx, HOME), trangThai, PackageManager.DONT_KILL_APP)
                kq.putString("ok", "1")
            }
            else -> kq.putString("loi", "khong biet $method")
        }
        return kq
    }

    companion object {
        /** Bí danh có bộ lọc HOME (AndroidManifest.xml). */
        const val HOME = "vn.tivikit.launcher.Home"
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
