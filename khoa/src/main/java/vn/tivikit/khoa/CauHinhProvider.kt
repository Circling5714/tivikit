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
 *   content call --uri content://vn.tivikit.khoa --method doc
 *   content call --uri content://vn.tivikit.khoa --method xac-thuc --arg <mã OTP 6 số / mã dự phòng 8 số>   (→ phien)
 *   content call --uri content://vn.tivikit.khoa --method dat --arg '<json>'
 *       (kèm "phien" khi tivi đã có mã; cài / đổi mã thì thêm "otp": mã gốc base32, "duPhong": {muoi, bam: [...]})
 *   content call --uri content://vn.tivikit.khoa --method mo-tam --arg '{"phut":60,"phien":…}'   (0 = khoá lại ngay)
 *   content call --uri content://vn.tivikit.khoa --method hen-khoa --arg '{"phien":…,"goi":[…],"sauPhut":30}'
 *       (khoá các app đó từ 30 phút nữa tới hết ngày; {"phien":…,"bo":0} bỏ hẹn thứ 0)
 *   content call --uri content://vn.tivikit.khoa --method quen-ma        (24 giờ sau đổi được không cần mã, xem XacThuc)
 *   content call --uri content://vn.tivikit.khoa --method thoi-gian      (báo cáo 7 ngày, xem BaoCao)
 * Chỉ nhận lời gọi từ shell (ADB) hoặc root: app khác trên tivi không đổi được giờ khoá hay mã. Tivi đã có mã mở khoá
 * (28/10/2026) thì đổi gì cũng phải kèm phiên lấy bằng xac-thuc.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != 0 && uid != Process.myUid()) throw SecurityException("chỉ ADB")
        val kho = Kho(context!!)
        val kq = Bundle()
        fun loi(s: String) = kq.apply { putString("loi", s) }
        when (method) {
            "dat" -> {
                val moi = runCatching { JSONObject(arg ?: "") }.getOrNull() ?: return loi("cau hinh sai")
                if (!XacThuc.duocDoi(kho, moi.optString("phien"))) return loi("can-ma")
                // Phiên và PIN của bản trước 1.2 không thuộc cấu hình; mã mới (cài lần đầu) lưu riêng.
                listOf("phien", "muoi", "pinBam").forEach { moi.remove(it) }
                val ma = runCatching { MaMo.tuLenh(moi, kho.maMo) }.getOrElse { return loi("ma sai") }
                val c = Luat.docJson(moi.toString()) ?: return loi("cau hinh sai")
                kho.json = moi.toString()
                if (ma != null) { kho.maMo = ma; kho.yeuCauGo = 0 }
                kho.sai = 0; kho.chanDen = 0
                if (!c.bat) kho.moTamDen = 0
                kq.putString("ok", "1")
            }
            "xac-thuc" -> {
                val o = JSONObject()
                when (val r = XacThuc.thu(kho, arg.orEmpty().filter { it.isDigit() })) {
                    XacThuc.Dung -> o.put("ok", true).put("phien", XacThuc.moPhien(kho))
                    is XacThuc.Sai -> o.put("loi", "sai").put("con", r.conLan)
                    is XacThuc.Cho -> o.put("loi", "cho").put("giay", (r.ms + 999) / 1000)
                }
                kq.putString("json", o.toString())
            }
            "mo-tam" -> {
                // Bản trước 1.2 nhận số phút trần; từ 1.2 là JSON để kèm phiên.
                val o = arg?.trim()?.takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (!XacThuc.duocDoi(kho, o?.optString("phien"))) return loi("can-ma")
                val phut = o?.optInt("phut", 60) ?: arg?.toIntOrNull() ?: 60
                kho.moTamDen = if (phut <= 0) 0 else System.currentTimeMillis() + phut * 60_000L
                kq.putString("ok", "1")
            }
            "hen-khoa" -> {
                val o = runCatching { JSONObject(arg ?: "") }.getOrNull() ?: return loi("hen sai")
                if (!XacThuc.duocDoi(kho, o.optString("phien"))) return loi("can-ma")
                val ds = kho.hen.toMutableList()
                if (o.has("bo")) o.optInt("bo", -1).takeIf { it in ds.indices }?.let { ds.removeAt(it) }
                else {
                    val goi = KhoaApp.dsGoi(o.optJSONArray("goi")).takeIf { it.isNotEmpty() } ?: return loi("hen sai")
                    val tu = System.currentTimeMillis() + o.optInt("sauPhut", 0).coerceIn(0, 24 * 60) * 60_000L
                    ds += HenKhoa(goi, tu, KhoaApp.nuaDem(tu))
                }
                kho.hen = ds
                kq.putString("ok", "1")
            }
            "quen-ma" -> {
                // Không cần mã: ai cũng gửi được, nhưng phải chờ 24 giờ, tivi báo suốt lúc chờ, ai nhập đúng mã là huỷ.
                if (kho.maMo != null && kho.yeuCauGo == 0L) kho.yeuCauGo = System.currentTimeMillis()
                kq.putString("json", JSONObject().put("yeuCauGo", kho.yeuCauGo).put("goDuocLuc", kho.yeuCauGo + XacThuc.CHO_QUEN_MS).toString())
            }
            "doc" -> {
                val c = kho.cauHinh
                val ma = kho.maMo
                val bay = System.currentTimeMillis()
                val o = JSONObject(kho.json ?: "{}").apply { remove("pinBam"); remove("muoi") }
                o.put("coOtp", ma != null)
                o.put("soDuPhong", ma?.duPhong?.size ?: 0)
                o.put("choGiay", (XacThuc.choCon(kho, bay) + 999) / 1000)
                o.put("yeuCauGo", kho.yeuCauGo)
                o.put("goDuoc", XacThuc.goDuoc(kho, bay))
                o.put("moTamDen", kho.moTamDen)
                o.put("dangKhoa", KhoaDichVu.dangKhoa)
                o.put("dichVuChay", KhoaDichVu.dangChay != null)
                // Giới hạn mỗi ngày (25/10/2026): hôm nay đã xem bao lâu, được bao nhiêu, đang khoá vì đâu.
                val lich = Calendar.getInstance()
                val daXem = KhoaDichVu.giayDaXem(kho, Luat.ngay(lich))
                o.put("giayHomNay", daXem)
                o.put("gioiHanHomNay", c?.let { Luat.gioiHanPhut(it, Luat.thu(lich)) } ?: 0)
                o.put("lyDo", when (Luat.lyDo(c, lich, daXem, kho.moTamDen)) { Luat.LyDo.HET_GIO -> "het-gio"; Luat.LyDo.GIO_KHOA -> "gio-khoa"; null -> "" })
                // Khoá theo app (28/10/2026): hẹn khoá còn hạn, phút đã dùng hôm nay của từng luật, app đang khoá lúc này.
                val hen = kho.hen
                val dung = KhoaDichVu.giayDung(kho, Luat.ngay(lich))
                val moApp = kho.moApp
                o.put("hen", KhoaApp.jsonHen(hen))
                o.put("dungApp", JSONObject().apply { c?.apps?.forEach { put(it.id, dung(it.id)) } })
                o.put("appKhoa", org.json.JSONArray(KhoaApp.goiCo(c, hen).filter { KhoaApp.khoa(c, hen, it, lich, dung, moApp[it] ?: 0) != null }.sorted()))
                o.put("baoVe", c != null && c.bat && c.coMa && bay >= kho.moCaiDatDen)
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
