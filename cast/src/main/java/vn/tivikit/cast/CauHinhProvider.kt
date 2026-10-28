package vn.tivikit.cast

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONObject

/**
 * TiviKit đọc trạng thái / đặt tên qua ADB:
 *   content call --uri content://vn.tivikit.cast --method doc
 *   content call --uri content://vn.tivikit.cast --method dat --arg '{"ten":"SmartTube phòng khách"}'   ("" = tên mặc định)
 *   content call --uri content://vn.tivikit.cast --method ma-tv   (mã TV 12 số để liên kết điện thoại, sống 5 phút)
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
        val kho = Kho(context!!)
        val kq = Bundle()
        when (method) {
            "doc" -> {
                val may = CastDichVu.dangChay?.may
                kq.putString("json", JSONObject()
                    .put("dichVuChay", may != null)
                    .put("ten", may?.tenHien() ?: kho.ten.orEmpty())
                    .put("cong", may?.cong ?: 0)
                    .put("daNoi", may?.daNoi ?: false)
                    .put("dienThoai", may?.soDienThoai ?: 0)
                    .put("dangPhat", may?.dangPhat ?: "")
                    .put("biChe", may?.biChe == true)
                    .put("nhatKy", kho.nhatKy)
                    .toString())
            }
            "dat" -> {
                val o = runCatching { JSONObject(arg ?: "") }.getOrNull() ?: return kq.apply { putString("loi", "sai") }
                kho.ten = o.optString("ten").trim().take(60).takeIf { it.isNotEmpty() }
                kq.putString("ok", "1")
            }
            "thu-tim" -> kq.putString("json", CastDichVu.dangChay?.may?.thuTim()?.toString() ?: "[]")
            "ma-tv" -> kq.putString("ma", CastDichVu.dangChay?.may?.xinMaTv().orEmpty())
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
