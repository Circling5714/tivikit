package vn.tivikit.dulieu

import org.json.JSONObject

/**
 * TiviKit Launcher (app vn.tivikit.launcher, module launcher/, 27/10/2026): màn Home kính mờ của TiviKit. TiviKit đóng sẵn
 * APK trong assets, cài lên tivi qua ADB như các app phụ khác — không cần cài TiviKit lên tivi — và chỉnh qua
 * `content call` (CauHinhProvider của launcher, chỉ nhận lời gọi từ ADB).
 */
object TiviKitLauncher {
    const val GOI = "vn.tivikit.launcher"
    const val APK = "tivikit-launcher.apk"

    /** Màn Home thật, luôn bật: Mở thử, và Home sạch mở nó đè lên Home của hãng (home_target trỏ vào bí danh .Home). */
    const val MAN = "$GOI/.ManHome"

    /**
     * Bí danh có bộ lọc HOME, tắt sẵn để cài / cập nhật không làm tivi hỏi chọn Home. ADB không đổi được trạng thái từng
     * thành phần của app thường ("Shell cannot change component state"), nên nhờ chính launcher tự bật: [LENH_BAT_HOME].
     */
    const val HOME = "$GOI/.Home"

    const val LENH_BAT_HOME = "content call --uri content://$GOI --method bat-home"
    const val LENH_DOC = "content call --uri content://$GOI --method doc"

    /**
     * Cấu hình gửi khi cài / cập nhật: tên lời chào (như tên chào của Home sạch) và ngôn ngữ của TiviKit trên điện thoại.
     * Nháy đơn đổi thành ’ vì lệnh shell bọc JSON trong nháy đơn.
     */
    fun cauHinh(ten: String, tiengViet: Boolean): String =
        JSONObject().put("ten", ten.replace('\'', '’').trim().take(40)).put("ngon_ngu", if (tiengViet) "vi" else "en").toString()

    fun lenhDat(json: String): String {
        require(!json.contains('\'')) { "json" }
        return "content call --uri content://$GOI --method dat --arg '$json'"
    }

    /** Trạng thái trên tivi: [ban] = versionName (null = chưa cài), [homeBat] = bí danh Home đã bật, [canCapNhat] = khác bản đi kèm. */
    data class TrangThai(val ban: String?, val homeBat: Boolean = false, val canCapNhat: Boolean = false) {
        val daCai: Boolean get() = ban != null
    }

    /** Đọc kết quả `content call … doc` của launcher ("Result: Bundle[{json={…}}]"); null nếu không đọc được. */
    fun docKetQua(out: String): JSONObject? {
        val a = out.indexOf("json=")
        val b = out.lastIndexOf("}]")
        return if (a < 0 || b <= a) null else runCatching { JSONObject(out.substring(a + 5, b)) }.getOrNull()
    }
}
