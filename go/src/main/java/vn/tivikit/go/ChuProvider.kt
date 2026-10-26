package vn.tivikit.go

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

/**
 * TiviKit gõ chữ qua ADB, sau khi bật bộ gõ này (`ime enable` + `ime set vn.tivikit.go/.BoGo`):
 *   content call --uri content://vn.tivikit.go --method chu --arg <base64 JSON {"chu": "…", "cu": "bộ gõ cũ"}>
 * Trả ok=1, hoặc loi=<mã> (xem BoGo.go). Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không gõ chữ
 * được qua đây.
 */
class ChuProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0) throw SecurityException("chỉ ADB")
        val kq = Bundle()
        when (method) {
            "chu" -> {
                val l = Lenh.doc(arg) ?: return kq.apply { putString("loi", "lenh-sai") }
                // Nhớ bộ gõ cũ cho lưới an toàn của BoGo (lần sau bị ngắt giữa chừng).
                if (l.cu.isNotEmpty()) context!!.getSharedPreferences("go", Context.MODE_PRIVATE).edit().putString("truoc", l.cu).apply()
                val r = BoGo.go(l.chu, l.cu)
                if (r == "ok") kq.putString("ok", "1") else kq.putString("loi", r)
            }
            else -> kq.putString("loi", "khong-biet")
        }
        return kq
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
