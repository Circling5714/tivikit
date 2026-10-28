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

    /**
     * Dịch vụ trợ năng Nút nguồn (bản 1.4 trở đi): menu ⏻ của launcher tắt tivi và mở menu nguồn nhờ nó. TiviKit bật khi
     * cài / cập nhật, bỏ khỏi danh sách trợ năng khi gỡ.
     */
    const val DICH_VU = "$GOI/$GOI.NguonDichVu"

    const val LENH_BAT_HOME = "content call --uri content://$GOI --method bat-home"
    const val LENH_DOC = "content call --uri content://$GOI --method doc"

    /** Ảnh nền riêng của launcher: content write để gửi, content delete để về ảnh mặc định. */
    const val URI_NEN = "content://$GOI/anh-nen.jpg"

    /**
     * Bộ câu riêng thay Kinh Pháp Cú đi kèm launcher (câu kinh mỗi ngày dưới đồng hồ, bản 1.4 trở đi): content write để
     * gửi, content delete để về Kinh Pháp Cú.
     */
    const val URI_CAU = "content://$GOI/cau.txt"
    /** Cỡ tối đa của bộ câu riêng (launcher cũng kiểm). */
    const val CAU_TOI_DA = 1024 * 1024

    /** Cấu hình câu kinh mỗi ngày: [bat] hiện / ẩn; [ten] tên bộ riêng (null = giữ tên đang có, "" = bỏ). Gửi bằng [lenhDat]. */
    fun cauHinhCau(bat: Boolean, ten: String? = null): String {
        val o = JSONObject().put("bat", bat)
        if (ten != null) o.put("ten", boNhay(ten).trim().take(40))
        return chiAscii(JSONObject().put("cau", o).toString())
    }

    /**
     * Chữ của file bộ câu người dùng chọn → UTF-8 không BOM (Notepad lưu "Unicode" là UTF-16 có BOM); null nếu rỗng, quá
     * [CAU_TOI_DA] hay không phải chữ (có byte 0 ngoài UTF-16).
     */
    fun chuBoCau(b: ByteArray): ByteArray? {
        val chu = when {
            b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16LE)
            b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> String(b, 2, b.size - 2, Charsets.UTF_16BE)
            b.contains(0.toByte()) -> return null
            else -> String(b, Charsets.UTF_8).removePrefix("\uFEFF")
        }
        if (chu.isBlank()) return null
        return chu.toByteArray(Charsets.UTF_8).takeIf { it.size <= CAU_TOI_DA }
    }

    /**
     * Ảnh thay banner của app [goi] trên launcher (bản 1.1 trở đi; logo kênh của ô Kênh nhanh, như app_banners của Home
     * sạch): content write để gửi, content delete để về banner của app.
     */
    fun uriAnhApp(goi: String): String {
        require(TEN_GOI.matches(goi)) { "goi" }
        return "content://$GOI/anh-app/$goi.jpg"
    }

    /**
     * Cấu hình đặt tên riêng của app trên launcher (bản 1.1 trở đi; tên kênh của ô Kênh nhanh, như app_labels của Home
     * sạch): [ten] gói → tên, null / rỗng = bỏ tên riêng (về tên của app). Gửi bằng [lenhDat].
     */
    fun tenApp(ten: Map<String, String?>): String {
        val o = JSONObject()
        ten.forEach { (goi, t) -> if (TEN_GOI.matches(goi)) o.put(goi, boNhay(t.orEmpty()).trim().take(40)) }
        return chiAscii(JSONObject().put("ten_app", o).toString())
    }

    /** Ảnh nền có sẵn trong TiviKit để chọn cho launcher. */
    data class AnhCoSan(val asset: String, val tenVi: String, val tenEn: String)

    /** Ảnh mặc định của launcher (đi kèm launcher): chọn nó = bỏ ảnh riêng. */
    const val NEN_MAC_DINH = "theme/phong-canh/08-tea-hills.jpg"

    val ANH_CO_SAN = listOf(
        AnhCoSan(NEN_MAC_DINH, "Đồi chè", "Tea hills"),
        AnhCoSan("theme/phong-canh/14-autumn-valley.jpg", "Thung lũng thu", "Autumn valley"),
        AnhCoSan("theme/phong-canh/02-golden-coast.jpg", "Bờ biển vàng", "Golden coast"),
        AnhCoSan("theme/phong-canh/07-mirror-lake.jpg", "Hồ gương", "Mirror lake"),
        AnhCoSan("theme/phong-canh/11-balloon-sunrise.jpg", "Khinh khí cầu", "Balloon sunrise"),
        AnhCoSan("theme/phong-canh/10-city-lights.jpg", "Phố đêm", "City lights"),
        AnhCoSan("theme/phong-canh/01-dusk-peaks.jpg", "Núi chiều", "Dusk peaks"),
        AnhCoSan("theme/phong-canh/03-moon-lake.jpg", "Hồ trăng", "Moon lake"),
        AnhCoSan("theme/phong-canh/04-aurora.jpg", "Cực quang", "Aurora"),
        AnhCoSan("theme/phong-canh/05-dunes.jpg", "Đồi cát", "Dunes"),
        AnhCoSan("theme/phong-canh/06-above-the-clouds.jpg", "Trên mây", "Above the clouds"),
        AnhCoSan("theme/phong-canh/09-alpenglow.jpg", "Núi tuyết hồng", "Alpenglow"),
        AnhCoSan("theme/phong-canh/12-monsoon.jpg", "Mùa mưa", "Monsoon"),
        AnhCoSan("theme/phong-canh/13-lighthouse-coast.jpg", "Hải đăng", "Lighthouse coast"),
        AnhCoSan("theme/aurora.jpg", "Dải cực quang", "Aurora bands"),
        AnhCoSan("theme/tivikit.jpg", "TiviKit", "TiviKit"),
    )

    /**
     * Cấu hình gửi khi cài / cập nhật: tên lời chào và nơi xem thời tiết (như của Home sạch), ngôn ngữ của TiviKit trên
     * điện thoại, và [dock] chỉ khi mới cài (cập nhật thì giữ dock người dùng đã sắp trên tivi). Nháy đơn đổi thành ’ vì
     * lệnh shell bọc JSON trong nháy đơn.
     */
    fun cauHinh(ten: String, tiengViet: Boolean, noi: ThanhPho? = null, dock: List<String>? = null, boNoiNeuRong: Boolean = false): String {
        val o = JSONObject().put("ten", boNhay(ten).trim().take(40)).put("ngon_ngu", if (tiengViet) "vi" else "en")
        if (noi != null && !noi.lat.isNaN() && !noi.lon.isNaN()) o.put("noi", JSONObject().put("ten", boNhay(noi.ten)).put("lat", noi.lat).put("lon", noi.lon))
        // Người dùng chọn "Không hiện" thời tiết ở phần Tuỳ chỉnh: launcher bỏ nơi đã lưu.
        else if (boNoiNeuRong) o.put("noi", JSONObject.NULL)
        if (!dock.isNullOrEmpty()) o.put("dock", org.json.JSONArray(dock.filter { TEN_GOI.matches(it) }))
        return chiAscii(o.toString())
    }

    /**
     * Chữ ngoài ASCII thành \uXXXX (JSON vẫn đọc ra đúng chữ): lệnh shell gửi qua libadb chỉ an toàn với ASCII — tên lời chào
     * "Chào CaoMinh Family", nơi "Nghĩa Lộ" làm hỏng lệnh với BufferOverflowException (Skyworth, 27/10/2026), như lý do bộ
     * gõ tiếng Việt gửi base64.
     */
    fun chiAscii(s: String): String = buildString(s.length) {
        for (c in s) if (c.code < 0x80) append(c) else append("\\u").append(String.format(java.util.Locale.ROOT, "%04x", c.code))
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

    /**
     * Biên dịch sẵn phần mã hay dùng (danh sách đi kèm APK của Compose, cài bằng ProfileInstallReceiver) thay vì cả app:
     * trên TX3 Mini mở nguội vẫn 1,1 giây như biên dịch hết (trước đó 1,55 giây) mà phần mã chiếm RAM 5,3 thay vì 7,3 MB.
     */
    const val LENH_TOI_UU = "am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE " +
        "-n $GOI/androidx.profileinstaller.ProfileInstallReceiver >/dev/null 2>&1; cmd package compile -m speed-profile -f $GOI"

    fun lenhDat(json: String): String {
        require(!json.contains('\'') && json.all { it.code < 0x80 }) { "json" }
        return "content call --uri content://$GOI --method dat --arg '$json'"
    }

    /** Launcher bản 1.0 không có "anhApp" trong kết quả doc: chưa nhận ảnh / tên riêng của app ([j] null = không đọc được). */
    fun nhanAnhApp(j: JSONObject?): Boolean = j?.has("anhApp") == true

    /**
     * Trạng thái trên tivi: [ban] = versionName (null = chưa cài), [homeBat] = bí danh Home đã bật, [canCapNhat] = khác bản
     * đi kèm; tên lời chào, nơi xem thời tiết, có ảnh nền riêng (để điền sẵn phần Tuỳ chỉnh).
     */
    data class TrangThai(val ban: String?, val homeBat: Boolean = false, val canCapNhat: Boolean = false, val ten: String = "",
                         val noi: ThanhPho? = null, val coNenRieng: Boolean = false, val khongDocDuocDangPhat: Boolean = false,
                         val cau: Cau? = null) {
        val daCai: Boolean get() = ban != null
    }

    /**
     * Câu kinh mỗi ngày trên launcher (null = bản cũ chưa có): [bat] đang hiện, [rieng] dùng bộ riêng [ten] (không thì Kinh
     * Pháp Cú đi kèm), [so] số câu của bộ đang dùng, [co] cỡ file bộ riêng.
     */
    data class Cau(val bat: Boolean, val ten: String, val rieng: Boolean, val so: Int, val co: Long)

    /** Trạng thái từ kết quả `doc` của launcher ([j] null = không đọc được). */
    fun trangThai(j: JSONObject?, canCapNhat: Boolean): TrangThai {
        val ch = j?.optJSONObject("cauHinh")
        val n = ch?.optJSONObject("noi")
        val noi = n?.let { ThanhPho(it.optString("ten"), it.optDouble("lat"), it.optDouble("lon")) }?.takeIf { !it.lat.isNaN() && !it.lon.isNaN() }
        val cau = j?.optJSONObject("cau")?.let { Cau(it.optBoolean("bat", true), it.optString("ten"), it.optBoolean("rieng"), it.optInt("so"), it.optLong("co")) }
        return TrangThai(j?.optString("ban")?.takeIf { it.isNotBlank() } ?: "?", j?.optBoolean("homeBat") == true, canCapNhat,
            ch?.optString("ten").orEmpty(), noi, (j?.optLong("nen") ?: 0L) > 0, j?.optString("dangPhat") == "khong-co-quyen", cau)
    }

    /** Đọc kết quả `content call … doc` của launcher ("Result: Bundle[{json={…}}]"); null nếu không đọc được. */
    fun docKetQua(out: String): JSONObject? {
        val a = out.indexOf("json=")
        val b = out.lastIndexOf("}]")
        return if (a < 0 || b <= a) null else runCatching { JSONObject(out.substring(a + 5, b)) }.getOrNull()
    }
}
