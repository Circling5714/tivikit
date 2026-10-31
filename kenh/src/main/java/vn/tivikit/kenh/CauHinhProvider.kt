package vn.tivikit.kenh

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

/**
 * TiviKit đặt / đọc kênh qua ADB:
 *   content call --uri content://vn.tivikit.kenh --method dat --arg '{"so":"001","ten":"VTV1"}'
 *   content call --uri content://vn.tivikit.kenh --method dat --arg '{"app":"vtvgo","ma":"3","ten":"VTV3"}'
 *   content call --uri content://vn.tivikit.kenh --method dat --arg '{"app":"fptplay","ma":"vtv1-hd","ten":"VTV1"}'
 *   content call --uri content://vn.tivikit.kenh --method doc
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đổi được ô này mở gì.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
        val kho = Kho(context!!)
        val kq = Bundle()
        when (method) {
            "dat" -> {
                val k = Kenh.tuJson(arg) ?: return kq.apply { putString("loi", "kenh sai") }
                kho.kenh = k
                kq.putString("ok", "1")
            }
            "doc" -> kq.putString("json", kho.kenh.json())
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
