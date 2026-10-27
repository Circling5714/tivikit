package vn.tivikit.launcher

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cấu hình màn Home trong cau-hinh.json (thư mục riêng của app). TiviKit ghi qua CauHinhProvider (ten, dock, ngon_ngu);
 * màn Home ghi dock khi người dùng sửa ngay trên tivi và lịch sử app vừa mở (gan_day).
 */
class CauHinh(ctx: Context) {
    private val file = File(ctx.filesDir, "cau-hinh.json")

    fun doc(): JSONObject = synchronized(KHOA) { runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject()) }

    /** Sửa rồi ghi cả file (qua file tạm), báo màn Home đọc lại. */
    fun sua(doi: (JSONObject) -> Unit) {
        synchronized(KHOA) {
            val j = doc()
            doi(j)
            val tam = File(file.path + ".tmp")
            tam.writeText(j.toString())
            if (!tam.renameTo(file)) { file.writeText(j.toString()); tam.delete() }
        }
        lanDoi.value++
    }

    companion object {
        private val KHOA = Any()

        /** Tăng mỗi lần cấu hình đổi (kể cả TiviKit ghi qua ADB) để màn Home đọc lại. */
        val lanDoi = MutableStateFlow(0)

        /** Tăng mỗi lần TiviKit gửi / bỏ ảnh nền riêng (CauHinhProvider) để màn Home dựng lại ảnh nền và kính. */
        val lanNen = MutableStateFlow(0)

        const val TEN_TOI_DA = 40

        /** Nơi xem thời tiết đã lưu ("noi": {ten, lat, lon}); null nếu chưa có / sai. */
        fun noi(j: JSONObject): ThoiTiet.Noi? {
            val o = j.optJSONObject("noi") ?: return null
            val lat = o.optDouble("lat")
            val lon = o.optDouble("lon")
            if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return ThoiTiet.Noi(o.optString("ten").trim().take(60), lat, lon)
        }

        /**
         * Ghép cấu hình TiviKit gửi ([moi]) vào [cu]: chỉ nhận "ten" (chữ, tối đa 40 ký tự, "" = bỏ tên), "dock" (mảng tên
         * gói hợp lệ), "ngon_ngu" ("vi" / "en" / "" = theo tivi) và "noi" (nơi xem thời tiết {ten, lat, lon}; sai / rỗng =
         * bỏ). Trả các khoá đã nhận.
         */
        fun tron(cu: JSONObject, moi: JSONObject): List<String> {
            val nhan = mutableListOf<String>()
            if (moi.has("ten")) {
                val ten = moi.optString("ten").trim().take(TEN_TOI_DA)
                if (ten.isEmpty()) cu.remove("ten") else cu.put("ten", ten)
                nhan += "ten"
            }
            moi.optJSONArray("dock")?.let { a ->
                cu.put("dock", JSONArray(dsGoi(a).filter(Dock::hopLe).distinct()))
                nhan += "dock"
            }
            if (moi.has("ngon_ngu")) {
                val nn = moi.optString("ngon_ngu")
                if (nn == "vi" || nn == "en") cu.put("ngon_ngu", nn) else cu.remove("ngon_ngu")
                nhan += "ngon_ngu"
            }
            if (moi.has("noi")) {
                val n = noi(moi)
                if (n == null) cu.remove("noi") else cu.put("noi", JSONObject().put("ten", n.ten).put("lat", n.lat).put("lon", n.lon))
                nhan += "noi"
            }
            return nhan
        }

        fun dsGoi(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    }
}
