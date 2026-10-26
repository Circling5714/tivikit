package vn.tivikit.khoa

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONObject
import java.util.Calendar

/**
 * TiviKit đặt / đọc cấu hình qua ADB:
 *   content call --uri content://vn.tivikit.khoa --method dat --arg '<json>'
 *   content call --uri content://vn.tivikit.khoa --method doc
 *   content call --uri content://vn.tivikit.khoa --method mo-tam --arg 60   (phút; 0 = khoá lại ngay)
 *   content call --uri content://vn.tivikit.khoa --method thoi-gian       (báo cáo 7 ngày, xem BaoCao)
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đổi được giờ khoá hay PIN.
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
                // Không gửi PIN mới (chỉ đổi giờ / tắt) thì giữ PIN cũ.
                val moi = runCatching { JSONObject(arg ?: "") }.getOrNull() ?: return kq.apply { putString("loi", "cau hinh sai") }
                if (moi.optString("pinBam").isBlank()) kho.cauHinh?.let { cu -> moi.put("muoi", cu.muoi).put("pinBam", cu.pinBam) }
                val c = Luat.docJson(moi.toString()) ?: return kq.apply { putString("loi", "cau hinh sai") }
                kho.json = moi.toString()
                kho.sai = 0; kho.chanDen = 0
                if (!c.bat) kho.moTamDen = 0
                kq.putString("ok", "1")
            }
            "mo-tam" -> {
                val phut = arg?.toIntOrNull() ?: 60
                kho.moTamDen = if (phut <= 0) 0 else System.currentTimeMillis() + phut * 60_000L
                kq.putString("ok", "1")
            }
            "doc" -> {
                val c = kho.cauHinh
                val o = JSONObject(kho.json ?: "{}").apply { remove("pinBam"); remove("muoi") }
                o.put("coPin", c?.pinBam?.isNotBlank() == true)
                o.put("moTamDen", kho.moTamDen)
                o.put("dangKhoa", KhoaDichVu.dangKhoa)
                o.put("dichVuChay", KhoaDichVu.dangChay != null)
                // Giới hạn mỗi ngày (25/10/2026): hôm nay đã xem bao lâu, được bao nhiêu, đang khoá vì đâu.
                val bay = Calendar.getInstance()
                val daXem = KhoaDichVu.giayDaXem(kho, Luat.ngay(bay))
                o.put("giayHomNay", daXem)
                o.put("gioiHanHomNay", c?.let { Luat.gioiHanPhut(it, Luat.thu(bay)) } ?: 0)
                o.put("lyDo", when (Luat.lyDo(c, bay, daXem, kho.moTamDen)) { Luat.LyDo.HET_GIO -> "het-gio"; Luat.LyDo.GIO_KHOA -> "gio-khoa"; null -> "" })
                kq.putString("json", o.toString())
            }
            "thoi-gian" -> kq.putString("json", BaoCao.lap(context!!).toString())
            else -> kq.putString("loi", "khong biet $method")
        }
        KhoaDichVu.dangChay?.capNhatSau()
        return kq
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
