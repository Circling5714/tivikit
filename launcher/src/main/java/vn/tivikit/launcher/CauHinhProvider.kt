package vn.tivikit.launcher

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/**
 * TiviKit đặt / đọc cấu hình qua ADB:
 *   content call --uri content://vn.tivikit.launcher --method dat --arg '{"ten":"Nhà bác Hùng","dock":["org.smarttube.stable"],"ngon_ngu":"vi"}'
 *   content call --uri content://vn.tivikit.launcher --method doc
 *   content call --uri content://vn.tivikit.launcher --method bat-home   (hoặc tat-home)
 *   content write --uri content://vn.tivikit.launcher/anh-nen.jpg < ảnh.jpg   (ảnh nền riêng; content delete để về ảnh mặc định)
 * bat-home / tat-home bật / tắt bí danh .Home (bộ lọc HOME): ADB không đổi được trạng thái từng thành phần của app thường
 * ("Shell cannot change component state", chỉ đổi được cả gói), còn app thì tự đổi được thành phần của mình.
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đổi được.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    private fun chiAdb() {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        chiAdb()
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
                    .put("nen", File(ctx.filesDir, ANH_NEN).length())
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

    /**
     * Ảnh nền riêng: ghi vào file tạm, đóng xong mới kiểm (giải mã được, không quá 12 MB) rồi thay ảnh cũ và báo màn Home
     * vẽ lại; ảnh hỏng thì bỏ, ảnh cũ giữ nguyên. Đọc ("r") trả ảnh đang dùng.
     */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        chiAdb()
        if (uri.lastPathSegment != ANH_NEN) throw FileNotFoundException(uri.toString())
        val ctx = context!!
        val dich = File(ctx.filesDir, ANH_NEN)
        if (!mode.contains('w')) return ParcelFileDescriptor.open(dich, ParcelFileDescriptor.MODE_READ_ONLY)
        val tam = File(ctx.filesDir, "$ANH_NEN.tmp")
        val ghi = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        return ParcelFileDescriptor.open(tam, ghi, Handler(Looper.getMainLooper())) { loi ->
            if (loi == null && tam.length() in 1..12L * 1024 * 1024 && AnhNen.hopLe(tam) && tam.renameTo(dich)) CauHinh.lanNen.value++
            else tam.delete()
        }
    }

    companion object {
        /** Bí danh có bộ lọc HOME (AndroidManifest.xml). */
        const val HOME = "vn.tivikit.launcher.Home"
        const val ANH_NEN = "anh-nen.jpg"
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null

    /** Bỏ ảnh nền riêng, về ảnh mặc định. */
    override fun delete(u: Uri, s: String?, a: Array<out String>?): Int {
        chiAdb()
        if (u.lastPathSegment != ANH_NEN) return 0
        val xoa = File(context!!.filesDir, ANH_NEN).delete()
        CauHinh.lanNen.value++
        return if (xoa) 1 else 0
    }
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
