package vn.tivikit.iptv

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONObject

/**
 * TiviKit / Map Voice gọi qua ADB:
 *   content call --uri content://vn.tivikit.iptv --method dat --arg '{"link":"https://…"}'   (link danh sách IPTV)
 *   content call --uri content://vn.tivikit.iptv --method doc                                  (link, số kênh, lúc tải)
 *   content call --uri content://vn.tivikit.iptv --method tai                                  (tải lại danh sách ngay)
 *   content call --uri content://vn.tivikit.iptv --method tim --arg '{"ten":"VTV3"}'          (kênh cùng tên: ma, ten)
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đổi được danh sách.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
        val kho = Kho(context!!)
        val kq = Bundle()
        val j = runCatching { JSONObject(arg ?: "{}") }.getOrDefault(JSONObject())
        when (method) {
            "dat" -> {
                val link = j.optString("link").trim()
                if (!LINK.matches(link)) return kq.apply { putString("loi", "link sai") }
                if (link != kho.link) kho.link = link
                kq.putString("ok", "1")
            }
            "doc" -> kq.putString("json", JSONObject()
                .put("phienBan", runCatching { context!!.packageManager.getPackageInfo(context!!.packageName, 0).versionName }.getOrNull() ?: "")
                .put("link", kho.link)
                .put("soKenh", kho.daTai().size)
                .put("lucTai", kho.lucTai)
                .toString())
            "tai" -> {
                val ds = kho.taiLai() ?: return kq.apply { putString("loi", "khong tai duoc") }
                kq.putString("ok", "1"); kq.putString("soKenh", ds.size.toString())
            }
            "tim" -> {
                val k = DanhSach.tim(kho.choMoKenh(), "", j.optString("ten").trim().take(60))
                    ?: return kq.apply { putString("loi", "khong co") }
                kq.putString("ok", "1"); kq.putString("ma", k.ma); kq.putString("ten", k.ten)
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

    companion object {
        /** Link http(s) không có khoảng trắng, nháy (đi trong lệnh content call bọc nháy đơn). */
        val LINK = Regex("https?://[^\\s'\"`]{3,2000}")
    }
}
