package vn.tivikit.khoa

import android.content.Context
import org.json.JSONObject

/** Trạng thái lưu trên tivi: cấu hình, mở tạm tới lúc nào, số lần nhập sai. */
class Kho(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("khoa", Context.MODE_PRIVATE)
    var json: String? get() = p.getString("cau_hinh", null); set(v) = p.edit().putString("cau_hinh", v).apply()
    var moTamDen: Long get() = p.getLong("mo_tam_den", 0); set(v) = p.edit().putLong("mo_tam_den", v).apply()
    var sai: Int get() = p.getInt("sai", 0); set(v) = p.edit().putInt("sai", v).apply()
    var chanDen: Long get() = p.getLong("chan_den", 0); set(v) = p.edit().putLong("chan_den", v).apply()
    val cauHinh: CauHinh? get() = Luat.docJson(json)

    /**
     * Sổ thời gian tivi được xem (màn hình sáng, không chạy màn hình chờ, không bị khoá): ngày → giây, 14 ngày gần nhất.
     * Có từ ngày [demTu] (bản app khoá có giới hạn giờ xem, 25/10/2026).
     */
    val soXem: JSONObject get() = runCatching { JSONObject(p.getString("so_xem", null) ?: "{}") }.getOrDefault(JSONObject())
    val demTu: String? get() = p.getString("dem_tu", null)

    fun congXem(ngay: String, giay: Long) {
        val e = p.edit().putString("so_xem", Luat.cong(soXem, ngay, giay).toString())
        if (!p.contains("dem_tu")) e.putString("dem_tu", ngay)
        e.apply()
    }

    fun giayXem(ngay: String): Long = soXem.optLong(ngay)
}
