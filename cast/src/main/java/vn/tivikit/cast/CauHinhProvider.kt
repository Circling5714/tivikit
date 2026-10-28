package vn.tivikit.cast

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject

/**
 * TiviKit đọc trạng thái / đặt tên qua ADB:
 *   content call --uri content://vn.tivikit.cast --method doc
 *   content call --uri content://vn.tivikit.cast --method dat --arg '{"ten":"SmartTube phòng khách"}'   ("" = tên mặc định)
 *   content call --uri content://vn.tivikit.cast --method ma-tv   (mã TV 12 số để liên kết điện thoại, sống 5 phút)
 *   content call --uri content://vn.tivikit.cast --method dang-phat   (bài SmartTube đang phát: mã video, tên, kênh)
 *   content call --uri content://vn.tivikit.cast --method dat --arg '{"nhanCast":false}'   (tạm ngừng nhận cast)
 *   content call --uri content://vn.tivikit.cast --method dien-thoai --arg '{"id":"…","lam":"cho-phep|hom-nay|24h|chan|xoa"}'
 *   content call --uri content://vn.tivikit.cast --method huy-lien-ket   (mọi điện thoại mất liên kết, phải nhập mã mới)
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
                    .put("dangPhat", may?.dangPhat ?: "")
                    .put("biChe", may?.biChe == true)
                    .put("nhanCast", kho.nhanCast)
                    .put("dienThoai", JSONArray(DanhBa.json(kho.dienThoai)))
                    .put("dangNoi", JSONArray((may?.dangNoi ?: emptySet()).toList()))
                    .put("nhatKy", kho.nhatKy)
                    .toString())
            }
            "dat" -> {
                val o = runCatching { JSONObject(arg ?: "") }.getOrNull() ?: return kq.apply { putString("loi", "sai") }
                if (o.has("ten")) kho.ten = o.optString("ten").trim().take(60).takeIf { it.isNotEmpty() }
                if (o.has("nhanCast")) kho.nhanCast = o.optBoolean("nhanCast", true)
                kq.putString("ok", "1")
            }
            "dien-thoai" -> {
                val o = runCatching { JSONObject(arg ?: "") }.getOrNull()
                val lam = o?.optString("lam")?.let(DanhBa.Lam::tu)
                val id = o?.optString("id").orEmpty()
                if (lam == null || id.isEmpty()) return kq.apply { putString("loi", "sai") }
                val may = CastDichVu.dangChay?.may
                if (may != null) may.lamDienThoai(id, lam) else kho.doiDienThoai { DanhBa.lam(it, id, lam, System.currentTimeMillis()) }
                kq.putString("ok", "1")
            }
            "huy-lien-ket" -> {
                val may = CastDichVu.dangChay?.may ?: return kq.apply { putString("loi", "dich-vu-tat") }
                may.huyLienKet()
                kq.putString("ok", "1")
            }
            "thu-tim" -> kq.putString("json", CastDichVu.dangChay?.may?.thuTim()?.toString() ?: "[]")
            "ma-tv" -> kq.putString("ma", CastDichVu.dangChay?.may?.xinMaTv().orEmpty())
            "dang-phat" -> kq.putString("json", CastDichVu.dangChay?.may?.baiDangPhat()?.toString() ?: "{}")
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
