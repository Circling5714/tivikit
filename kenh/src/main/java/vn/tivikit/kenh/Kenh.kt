package vn.tivikit.kenh

import android.content.Context
import org.json.JSONObject

/** Kênh của ô Kênh nhanh: số kênh trong TV360 (3 chữ số, VD 001 = VTV1) và tên (dự phòng khi TV360 đổi số kênh). */
data class Kenh(val so: String, val ten: String) {
    fun json(): String = JSONObject().put("so", so).put("ten", ten).toString()

    companion object {
        const val TV360 = "com.viettel.tv360.tv"

        /** Chưa đặt gì: VTV1 (số 001 trong danh sách kênh của TV360, 25/10/2026). */
        val MAC_DINH = Kenh("001", "VTV1")

        /** "1" / "01" / "001" → "001", dạng số kênh (shortcutKeyboard) của TV360; ngoài 1..999 → null. */
        fun chuanSo(s: String?): String? = s?.trim()?.toIntOrNull()?.takeIf { it in 1..999 }?.toString()?.padStart(3, '0')

        fun tuJson(s: String?): Kenh? {
            val j = runCatching { JSONObject(s ?: "") }.getOrNull() ?: return null
            val so = chuanSo(j.optString("so")) ?: return null
            return Kenh(so, j.optString("ten").trim().take(40))
        }
    }
}

/** Kênh đã đặt, lưu trên tivi. */
class Kho(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("kenh", Context.MODE_PRIVATE)
    var kenh: Kenh
        get() = Kenh.tuJson(p.getString("kenh", null)) ?: Kenh.MAC_DINH
        set(v) { p.edit().putString("kenh", v.json()).apply() }
}
