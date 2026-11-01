package vn.tivikit.kenh

import android.content.Context
import org.json.JSONObject

/**
 * Kênh của ô Kênh nhanh. Ô TV360: số kênh trong TV360 (3 chữ số, VD 001 = VTV1) và tên (dự phòng khi TV360 đổi số kênh).
 * Ô VTVgo (26/10/2026, [app] vtvgo): mã kênh của VTVgo (contentId trong vtvgo://play) và tên. Ô FPT Play (31/10/2026, [app]
 * fptplay): mã kênh của FPT Play (fptplay.vn/xem-truyen-hinh/<mã>, VD vtv1-hd) và tên. Ô M3U (1/11/2026, [app] m3u): mã kênh
 * (số) trong M3UAndroid, lấy từ phím tắt "channel_<mã>" người dùng tạo trong M3U (Create Shortcut), và tên.
 */
data class Kenh(val so: String, val ten: String, val ma: String = "", val app: String = if (ma.isEmpty()) APP_TV360 else APP_VTVGO) {
    val laVtvGo: Boolean get() = app == APP_VTVGO
    val laFpt: Boolean get() = app == APP_FPT
    val laM3u: Boolean get() = app == APP_M3U

    fun json(): String = JSONObject().put("ten", ten)
        .apply { if (app == APP_TV360) put("so", so) else put("app", app).put("ma", ma) }.toString()

    companion object {
        const val TV360 = "com.viettel.tv360.tv"
        const val VTVGO = "vn.vtv.vtvgotv"
        const val FPTPLAY = "net.fptplay.ottbox"
        const val APP_TV360 = "tv360"
        const val APP_VTVGO = "vtvgo"
        const val APP_FPT = "fptplay"
        const val APP_M3U = "m3u"
        /** M3UAndroid (github.com/oxyroid/M3UAndroid), bản 1.15.0 (Android TV dùng bản này, gói smartphone). */
        const val M3U = "com.m3u.smartphone"
        /**
         * Màn phát của M3U nhận mã kênh qua [M3U_MA_KENH] (phím tắt Create Shortcut và widget Kênh yêu thích của M3U dùng
         * đường này, Contracts.PLAYER_SHORTCUT_CHANNEL_ID): mở lên là phát ngay kênh đó.
         */
        const val M3U_PHAT = "com.m3u.smartphone.ui.business.channel.PlayerActivity"
        const val M3U_MA_KENH = "shortcut:channel-id"

        /** Chưa đặt gì: VTV1 (số 001 trong danh sách kênh của TV360, 25/10/2026). */
        val MAC_DINH = Kenh("001", "VTV1")

        private val MA = Regex("[A-Za-z0-9_-]{1,60}")
        /** Mã kênh của M3U: số nguyên dương (khoá chính trong cơ sở dữ liệu kênh của M3U). */
        private val MA_M3U = Regex("[1-9][0-9]{0,8}")

        /** "1" / "01" / "001" → "001", dạng số kênh (shortcutKeyboard) của TV360; ngoài 1..999 → null. */
        fun chuanSo(s: String?): String? = s?.trim()?.toIntOrNull()?.takeIf { it in 1..999 }?.toString()?.padStart(3, '0')

        /** Link mở kênh [ma] trong VTVgo, như ô kênh của Home sạch (LinkTiles). */
        fun linkVtvGo(ma: String): String = "vtvgo://play?mediaType=CHANNEL&contentId=$ma"

        /**
         * Link mở kênh [ma] trong FPT Play bản tivi: deep link của màn xem truyền hình (LiveTvFragment trong live_nav của
         * FPT Play 7.34), cũng là link kênh trên fptplay.vn.
         */
        fun linkFpt(ma: String): String = "https://fptplay.vn/xem-truyen-hinh/$ma"

        fun tuJson(s: String?): Kenh? {
            val j = runCatching { JSONObject(s ?: "") }.getOrNull() ?: return null
            val ten = j.optString("ten").trim().take(40)
            val app = j.optString("app")
            if (app == APP_VTVGO || app == APP_FPT || app == APP_M3U) {
                val ma = j.optString("ma").trim()
                return if ((if (app == APP_M3U) MA_M3U else MA).matches(ma) && ten.isNotEmpty()) Kenh("", ten, ma, app) else null
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
