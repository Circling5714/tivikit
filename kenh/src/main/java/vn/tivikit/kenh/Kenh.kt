package vn.tivikit.kenh

import android.content.Context
import org.json.JSONObject

/**
 * Kênh của ô Kênh nhanh. Ô TV360: số kênh trong TV360 (3 chữ số, VD 001 = VTV1) và tên (dự phòng khi TV360 đổi số kênh).
 * Ô VTVgo (26/10/2026, có [ma]): mã kênh của VTVgo (contentId trong vtvgo://play) và tên.
 */
data class Kenh(val so: String, val ten: String, val ma: String = "") {
    val laVtvGo: Boolean get() = ma.isNotEmpty()

    fun json(): String = JSONObject().put("ten", ten)
        .apply { if (laVtvGo) put("app", "vtvgo").put("ma", ma) else put("so", so) }.toString()

    companion object {
        const val TV360 = "com.viettel.tv360.tv"
        const val VTVGO = "vn.vtv.vtvgotv"

        /** Chưa đặt gì: VTV1 (số 001 trong danh sách kênh của TV360, 25/10/2026). */
        val MAC_DINH = Kenh("001", "VTV1")

        private val MA = Regex("[A-Za-z0-9_-]{1,60}")

        /** "1" / "01" / "001" → "001", dạng số kênh (shortcutKeyboard) của TV360; ngoài 1..999 → null. */
        fun chuanSo(s: String?): String? = s?.trim()?.toIntOrNull()?.takeIf { it in 1..999 }?.toString()?.padStart(3, '0')

        /** Link mở kênh [ma] trong VTVgo, như ô kênh của Home sạch (LinkTiles). */
        fun linkVtvGo(ma: String): String = "vtvgo://play?mediaType=CHANNEL&contentId=$ma"

        fun tuJson(s: String?): Kenh? {
            val j = runCatching { JSONObject(s ?: "") }.getOrNull() ?: return null
            val ten = j.optString("ten").trim().take(40)
            if (j.optString("app") == "vtvgo") {
                val ma = j.optString("ma").trim()
                return if (MA.matches(ma) && ten.isNotEmpty()) Kenh("", ten, ma) else null
            }
            val so = chuanSo(j.optString("so")) ?: return null
            return Kenh(so, ten)
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
