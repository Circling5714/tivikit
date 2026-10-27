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

        const val TEN_TOI_DA = 40

        /**
         * Ghép cấu hình TiviKit gửi ([moi]) vào [cu]: chỉ nhận "ten" (chữ, tối đa 40 ký tự, "" = bỏ tên), "dock" (mảng tên
         * gói hợp lệ) và "ngon_ngu" ("vi" / "en" / "" = theo tivi). Trả các khoá đã nhận.
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
            return nhan
        }

        fun dsGoi(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    }
}
