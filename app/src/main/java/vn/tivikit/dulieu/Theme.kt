package vn.tivikit.dulieu

import org.json.JSONObject

/**
 * Giao diện cho Home sạch: các khoá config.json của launcher (upstream themes/README.md)
 * cộng một ảnh nền tuỳ chọn trong assets/theme/.
 */
data class Theme(
    val id: String,
    val tenVi: String,
    val tenEn: String,
    val moTaVi: String,
    val moTaEn: String,
    val cauHinh: Map<String, Any>,
    val anhNen: String? = null,
    val mau: List<Long>,
    /** Thư mục assets chứa BỘ ảnh nền xoay vòng (đẩy vào wallpapers/ của launcher); ảnh xem trước trong app. */
    val boAnh: String? = null,
    val anhXem: String? = null,
) {
    fun ghiVao(o: JSONObject) {
        cauHinh.forEach { (k, v) -> o.put(k, v) }
        o.put("wallpaper", anhNen != null || boAnh != null)
        // Lần sau kết nối lại: mở đúng giao diện này, và biết khoá nào do giao diện ghi chứ không phải người dùng bật.
        o.put("vgs_theme", id)
    }
}

val THEMES = listOf(
    // Ảnh vẽ bằng scripts/AnhNenTiviKit.java (26/10/2026), thay giao diện VGS cũ (logo VGS không thuộc MIT).
    Theme("tivikit", "TiviKit", "TiviKit", "Hình tivi TiviKit trên nền chàm, đổi sắc theo giờ", "TiviKit's TV on indigo, sky follows the time of day",
        mapOf("background_color" to "#0F0E45", "sky_intensity" to 55, "dock_band" to false), "theme/tivikit.jpg", listOf(0xFF080B2A, 0xFF16156A, 0xFF3024B0)),
    Theme("aurora", "Cực quang", "Aurora", "Dải xanh ngọc và tím trên nền navy", "Teal and violet bands on navy",
        mapOf("background_color" to "#070A14", "dock_band" to false), "theme/aurora.jpg", listOf(0xFF0E4F5C, 0xFF3B1E6E)),
    Theme("bau-troi", "Bầu trời theo giờ", "Time-of-day sky", "Nền đổi màu theo giờ trong ngày", "Background follows the time of day",
        mapOf("sky_intensity" to 70, "dock_band" to false), null, listOf(0xFF1B2B5A, 0xFF5A3A4A)),
    Theme("dien-anh", "Điện ảnh", "Cinematic", "Ảnh app đang chọn phủ kín nửa trên", "The focused app's artwork fills the top",
        mapOf("cinematic_home" to true, "sky_intensity" to 35), null, listOf(0xFF4A0A0A, 0xFF101010)),
    // Giao diện "kiểu Apple TV" của CleanTV 2.9.0, đổi tên vì Apple TV là nhãn hiệu của Apple: nửa trên phủ màu + ảnh của
    // app đang chọn, tên app lớn, dock kính mờ, ô icon đều, viền chọn trắng, không lời chào, các hàng mờ dần.
    Theme("kinh-mo", "Kính mờ", "Frosted", "Nền theo màu app đang chọn, dock kính mờ, ô icon đều", "Backdrop in the focused app's colour, frosted dock, even icon tiles",
        mapOf("background_color" to "#000000", "cinematic_home" to true, "cinematic_dots" to false, "cinematic_art" to 70,
            "cinematic_scrim" to 60, "cinematic_white_focus" to true, "uniform_tiles" to true, "dock_glass" to true,
            "show_greeting" to false, "header_white" to true, "fade_rows" to true, "rows_below_dock" to 1, "columns" to 5,
            "rows_offset_dp" to 90),
        null, listOf(0xFF7A0A14, 0xFF0B3A6E, 0xFF050505)),
    // Scenes của CleanYourTV (manas-04): 14 tranh phong cảnh tối vẽ bằng code, tự đổi mỗi giờ (22/10/2026).
    Theme("phong-canh", "Phong cảnh", "Scenes", "14 tranh phong cảnh, tự đổi mỗi giờ", "14 landscapes, a new one every hour",
        mapOf("wallpaper_rotation_minutes" to 60, "dock_band" to false, "background_color" to "#05060A"), null,
        listOf(0xFF1A2236, 0xFF4A3A5A), boAnh = "theme/phong-canh", anhXem = "theme/phong-canh/02-golden-coast.jpg"),
    Theme("toi-gian", "Tối giản", "Minimal", "Nền đen tuyền, gọn nhất", "Pure black, the plainest",
        mapOf("background_color" to "#000000"), null, listOf(0xFF000000, 0xFF111111)),
    Theme("sau-cot", "Nhiều app", "More apps", "6 cột, thấy thêm một hàng app", "6 columns, one more row visible",
        mapOf("columns" to 6, "rows_below_dock" to 1, "background_color" to "#000000"), null, listOf(0xFF111111, 0xFF222233)),
)

/** Giao diện theo id đã lưu (config.json "vgs_theme", file cấu hình); "vgs" của bản cũ thành giao diện TiviKit. */
fun themeTheoId(id: String): Theme? = THEMES.firstOrNull { it.id == (if (id == "vgs") "tivikit" else id) }

/**
 * SHA-256 của ảnh nền giao diện VGS cũ (theme/vgs.jpg, bỏ khỏi app 26/10/2026). Tivi còn ảnh này thì đó là ảnh của
 * giao diện chứ không phải ảnh riêng của người dùng, để lần Áp dụng sau thay bằng ảnh TiviKit thay vì giữ lại.
 */
const val SHA256_ANH_VGS_CU = "1df8223bd79e297e8f2acde6967d7187469b1fbaae4659ab485a52c751d4c88b"

fun laAnhNenVgsCu(anh: ByteArray): Boolean = sha256(anh) == SHA256_ANH_VGS_CU

/** SHA-256 dạng hex chữ thường, như sha256sum. */
fun sha256(b: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

/** Thành phố cho dòng thời tiết trên Home sạch (weather_lat/lon/place của launcher). */
data class ThanhPho(val ten: String, val lat: Double, val lon: Double)

/**
 * 3.321 phường/xã sau sáp nhập 2025 kèm toạ độ (assets/xa.json, scripts/lam-xa.py) cho thời tiết theo xã (góp ý
 * 23/10/2026). [timXa] tìm không dấu: "ben thanh", "cua lo hoi an"…
 */
object XaPhuong {
    data class Xa(val ten: String, val tinh: String, val lat: Double, val lon: Double) {
        /** Tên ngắn hiện trên tivi: bỏ chữ "Phường"/"Xã"/"Đặc khu". */
        val tenNgan get() = ten.removePrefix("Phường ").removePrefix("Xã ").removePrefix("Đặc khu ")
        val khoaTim by lazy { boDau("$ten $tinh") }
    }
    @Volatile private var ds: List<Xa>? = null
    fun nap(ctx: android.content.Context): List<Xa> = ds ?: runCatching {
        val o = org.json.JSONObject(ctx.assets.open("xa.json").bufferedReader().readText())
        val t = o.getJSONArray("tinh"); val tinh = (0 until t.length()).map { t.getJSONArray(it).getString(0) }
        val x = o.getJSONArray("xa")
        (0 until x.length()).map { i -> x.getJSONArray(i).let { Xa(it.getString(0), tinh[it.getInt(1)], it.getDouble(2), it.getDouble(3)) } }
    }.getOrDefault(emptyList()).also { ds = it }

    fun boDau(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')

    /**
     * Thành phố ngoài Việt Nam (CleanTV 2.10.7, người dùng ở Indonesia, Nhật xin thời tiết nơi mình ở): hỏi dịch vụ tìm
     * địa danh của Open-Meteo, cùng nơi cấp số thời tiết. Bỏ kết quả ở Việt Nam (đã có danh sách xã). Lỗi mạng thì rỗng.
     */
    fun timTheGioi(q: String, tiengViet: Boolean, toiDa: Int = 6): List<Xa> = runCatching {
        val url = java.net.URL("https://geocoding-api.open-meteo.com/v1/search?count=$toiDa&format=json&language=${if (tiengViet) "vi" else "en"}&name=" +
            java.net.URLEncoder.encode(q.trim(), "UTF-8"))
        val c = url.openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000
        tachTheGioi(c.inputStream.bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList())

    /** Kết quả của Open-Meteo geocoding thành danh sách địa danh ngoài Việt Nam (tên, "vùng, nước", toạ độ). */
    fun tachTheGioi(json: String): List<Xa> {
        val ds = org.json.JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until ds.length()).map { ds.getJSONObject(it) }.filter { it.optString("country_code") != "VN" }.map { r ->
            Xa(r.optString("name"), listOf(r.optString("admin1"), r.optString("country")).filter { it.isNotBlank() }.joinToString(", "),
                r.optDouble("latitude"), r.optDouble("longitude"))
        }
    }

    fun timXa(ds: List<Xa>, q: String, toiDa: Int = 8): List<Xa> {
        val tu = boDau(q).split(' ').filter { it.isNotBlank() }
        if (tu.isEmpty()) return emptyList()
        return ds.filter { x -> tu.all { x.khoaTim.contains(it) } }
            .sortedBy { x -> if (boDau(x.tenNgan).startsWith(tu.joinToString(" "))) 0 else 1 }.take(toiDa)
    }
}

val THANH_PHO = listOf(
    ThanhPho("Hà Nội", 21.0285, 105.8542), ThanhPho("TP.HCM", 10.7769, 106.7009), ThanhPho("Đà Nẵng", 16.0544, 108.2022),
    ThanhPho("Hải Phòng", 20.8449, 106.6881), ThanhPho("Cần Thơ", 10.0452, 105.7469), ThanhPho("Vinh", 18.6796, 105.6813),
    ThanhPho("Huế", 16.4637, 107.5909), ThanhPho("Nha Trang", 12.2388, 109.1967), ThanhPho("Đà Lạt", 11.9404, 108.4583),
    ThanhPho("Quy Nhơn", 13.7820, 109.2192), ThanhPho("Buôn Ma Thuột", 12.6667, 108.0500), ThanhPho("Vũng Tàu", 10.3460, 107.0843),
    ThanhPho("Biên Hoà", 10.9574, 106.8426), ThanhPho("Thanh Hoá", 19.8067, 105.7852), ThanhPho("Nam Định", 20.4200, 106.1683),
    ThanhPho("Hạ Long", 20.9599, 107.0425), ThanhPho("Thái Nguyên", 21.5942, 105.8482), ThanhPho("Bắc Ninh", 21.1861, 106.0763),
)
