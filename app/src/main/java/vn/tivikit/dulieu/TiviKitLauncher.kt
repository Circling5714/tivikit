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
     * Cấu hình gửi khi cài / cập nhật: tên lời chào và nơi xem thời tiết (như của Home sạch), ngôn ngữ của TiviKit trên
     * điện thoại, và [dock] chỉ khi mới cài (cập nhật thì giữ dock người dùng đã sắp trên tivi). Nháy đơn đổi thành ’ vì
     * lệnh shell bọc JSON trong nháy đơn.
     */
    fun cauHinh(ten: String, tiengViet: Boolean, noi: ThanhPho? = null, dock: List<String>? = null): String {
        val o = JSONObject().put("ten", boNhay(ten).trim().take(40)).put("ngon_ngu", if (tiengViet) "vi" else "en")
        if (noi != null && !noi.lat.isNaN() && !noi.lon.isNaN()) o.put("noi", JSONObject().put("ten", boNhay(noi.ten)).put("lat", noi.lat).put("lon", noi.lon))
        if (!dock.isNullOrEmpty()) o.put("dock", org.json.JSONArray(dock.filter { TEN_GOI.matches(it) }))
        return o.toString()
    }

    private val TEN_GOI = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")

    private fun boNhay(s: String) = s.replace('\'', '’')

    /** Những gì lấy được từ config.json của Home sạch trên tivi: tên lời chào, nơi xem thời tiết, các app trong dock. */
    data class TuHomeSach(val ten: String = "", val noi: ThanhPho? = null, val dock: List<String> = emptyList())

    /** Đọc config.json của Home sạch ([j] null = tivi không có Home sạch); bỏ ô kênh giả vgs.kenh.* trong dock. */
    fun tuHomeSach(j: JSONObject?): TuHomeSach {
        if (j == null) return TuHomeSach()
        val ten = j.optString("greeting_name").trim()
        val noiTen = j.optString("weather_place").trim()
        val noi = if (noiTen.isEmpty()) null else ThanhPho(noiTen, j.optDouble("weather_lat"), j.optDouble("weather_lon")).takeIf { !it.lat.isNaN() && !it.lon.isNaN() }
        val dock = j.optJSONArray("dock_apps")?.let { a -> (0 until a.length()).map { a.optString(it).trim() } }.orEmpty()
            .filter { it.isNotEmpty() && !it.startsWith(KenhVtvGo.TIEN_TO) && TEN_GOI.matches(it) }
        return TuHomeSach(ten, noi, dock)
    }

    /**
     * Lệnh cấp quyền sau khi cài: vị trí (tên Wi-Fi), đọc phiên phát (thẻ Đang phát, không đọc thông báo), thống kê sử
     * dụng (app mở gần đây cả khi mở bằng giọng nói / nút remote). Lệnh nào tivi không có thì bỏ qua.
     */
    const val LENH_CAP_QUYEN = "pm grant $GOI android.permission.ACCESS_FINE_LOCATION 2>/dev/null; " +
        "cmd notification allow_listener $GOI/$GOI.NgheNhac 2>/dev/null; appops set $GOI GET_USAGE_STATS allow 2>/dev/null; true"

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
