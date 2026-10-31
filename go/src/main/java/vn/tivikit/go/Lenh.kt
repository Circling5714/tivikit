package vn.tivikit.go

import org.json.JSONObject
import java.util.Base64

/**
 * Lệnh gõ TiviKit gửi qua ADB: [chu] cần gõ, [cu] = bộ gõ của tivi trước khi bật bộ gõ này (gói/lớp, để trả lại), [thay] =
 * thay hết chữ đang có trong ô (Tele Remote gõ từ Telegram, không thấy con trỏ; 31/10/2026), không thì chèn ở con trỏ.
 */
data class Lenh(val chu: String, val cu: String, val thay: Boolean = false) {
    companion object {
        private val TEN_BO_GO = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

        /** --arg = base64 của JSON {"chu", "cu", "thay"}: chữ có dấu, nháy, dấu cách qua shell nguyên vẹn. Sai dạng / rỗng thì null. */
        fun doc(arg: String?): Lenh? = runCatching {
            val o = JSONObject(String(Base64.getDecoder().decode(arg!!.trim()), Charsets.UTF_8))
            Lenh(o.getString("chu"), o.optString("cu").takeIf { TEN_BO_GO.matches(it) } ?: "", o.optBoolean("thay"))
        }.getOrNull()?.takeIf { it.chu.isNotEmpty() }
    }
}
