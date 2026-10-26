package vn.tivikit.dulieu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kênh của VTVgo. Danh sách kênh lấy từ API công khai của VTVgo (display/v21.0/catalogs/with-channels, không cần đăng
 * nhập), nhớ vào máy; ô Kênh nhanh VTVgo (app phụ, KenhNhanh) mở kênh bằng vtvgo://play?mediaType=CHANNEL&contentId=<mã>.
 *
 * Ô kênh kiểu cũ trên Home sạch (CleanTV 2.10.0 – 3.0.0; TiviKit 1.2 – 1.4 chọn ở màn Home sạch): mỗi kênh một ô link ở
 * đầu dock. Launcher vẽ ô theo link_tiles của bản vá (LinkTiles.java); ô mang tên gói giả vgs.kenh.<slug> trong dock_apps
 * nên đổi chỗ / bỏ khỏi Home như app thường. Ảnh ô là thumbnail kênh của VTVgo, gửi kèm khi Áp dụng (launcher không có
 * mạng); app không đóng sẵn logo của đài, tải không được thì launcher vẽ ô chữ. Ô kiểu này không gán được nút remote nên
 * màn Kênh nhanh chuyển chúng thành ô app phụ.
 */
object KenhVtvGo {
    const val GOI = "vn.vtv.vtvgotv"
    /** Tiền tố tên gói giả của ô kênh trong dock_apps (LinkTiles.PREFIX của bản vá launcher). */
    const val TIEN_TO = "vgs.kenh."
    /** Launcher nhận tối đa 12 ô link, tên tối đa 24 ký tự. */
    const val TOI_DA = 12

    /** VTV1–9: (tên, mã kênh trên VTVgo, màu ô khi chưa có ảnh). Mã từ API của VTVgo (CleanTV thử trên VTVgo TV 12.10.23). */
    val VTV = listOf(
        Triple("VTV1", "1", "#D71920"), Triple("VTV2", "2", "#2E7D32"), Triple("VTV3", "3", "#E8590C"),
        Triple("VTV4", "4", "#1565C0"), Triple("VTV5", "5", "#6A1B9A"), Triple("VTV6", "13", "#00897B"),
        Triple("VTV7", "27", "#F9A825"), Triple("VTV8", "36", "#AD1457"), Triple("VTV9", "39", "#283593"),
    )

    data class Kenh(val ten: String, val ma: String, val nhom: String, val anh: String, val canDangNhap: Boolean) {
        /** Tên trên ô: bỏ chữ "Truyền hình" cho gọn, tối đa 24 ký tự. */
        val tenO: String get() = tenNgan(ten)
    }

    private const val API = "https://api.vtvdigital.org/display/v21.0/catalogs/with-channels"
    private const val PREF = "kenh_vtvgo"

    fun tenNgan(ten: String): String = ten.removePrefix("Truyền hình ").trim().take(24).trim()

    /** Phần sau tiền tố của tên gói giả, cũng là tên file ảnh kenh-<slug>.jpg: như LinkTiles (chữ thường, chỉ a-z 0-9). */
    fun slug(ten: String): String = ten.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    /** Một dòng link_tiles của launcher: "tên|gói|link|màu". */
    fun oLink(ten: String, ma: String): String {
        val mau = VTV.firstOrNull { it.second == ma }?.third ?: "#C62828"
        return "$ten|$GOI|vtvgo://play?mediaType=CHANNEL&contentId=$ma|$mau"
    }

    /** (tên, mã kênh) từ một dòng link_tiles mở VTVgo; null nếu là ô khác. */
    fun tuLink(dong: String): Pair<String, String>? {
        val p = dong.split('|')
        if (p.size < 3 || p[1] != GOI) return null
        val ma = Regex("contentId=([A-Za-z0-9_-]+)").find(p[2])?.groupValues?.get(1) ?: return null
        return p[0] to ma
    }

    /** Các ô VTVgo kiểu cũ đang có trong config.json [j] (link_tiles): (tên, mã kênh), theo thứ tự. */
    fun docTu(j: JSONObject): List<Pair<String, String>> =
        j.optJSONArray("link_tiles")?.let { a -> (0 until a.length()).mapNotNull { tuLink(a.optString(it)) } }.orEmpty()

    /**
     * Đặt các ô [kenh] vào config.json [j]: link_tiles, và tên gói giả của chúng ở đầu dock_apps, trước các app đang có
     * (ô kênh cũ bỏ đi). false = [j] không có dock_apps (Home sạch đang dùng dock mặc định, ghi vào là mất dock đó): chưa
     * đổi gì.
     */
    fun datVao(j: JSONObject, kenh: List<Pair<String, String>>): Boolean {
        val dock = j.optJSONArray("dock_apps") ?: return false
        val app = (0 until dock.length()).map { dock.optString(it).trim() }.filter { it.isNotEmpty() && !it.startsWith(TIEN_TO) }
        if (kenh.isEmpty()) j.remove("link_tiles") else j.put("link_tiles", JSONArray(kenh.map { (ten, ma) -> oLink(ten, ma) }))
        j.put("dock_apps", JSONArray(kenh.map { TIEN_TO + slug(it.first) } + app))
        return true
    }

    fun docNho(ctx: Context): List<Kenh> =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("ds", null)?.let { runCatching { tach(it) }.getOrNull() } ?: emptyList()

    /** Tải danh sách mới (gọi trên luồng IO); lỗi mạng thì trả bản nhớ. */
    fun tai(ctx: Context): List<Kenh> = runCatching {
        val c = java.net.URL(API).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        val chu = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        val ds = tach(chu)
        if (ds.isNotEmpty()) ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("ds", chu).apply()
        ds
    }.getOrElse { docNho(ctx) }

    fun tach(json: String): List<Kenh> {
        val out = mutableListOf<Kenh>(); val da = mutableSetOf<String>()
        val data = JSONObject(json).optJSONArray("data") ?: return out
        for (i in 0 until data.length()) {
            val nhom = data.optJSONObject(i) ?: continue
            val ds = nhom.optJSONArray("channels") ?: continue
            for (k in 0 until ds.length()) {
                val c = ds.optJSONObject(k) ?: continue
                val ma = c.optString("id"); val ten = c.optString("name").trim()
                if (ma.isBlank() || ten.isBlank() || !c.optBoolean("enabled", true) || !da.add(ma)) continue
                if (!Regex("[A-Za-z0-9_-]{1,60}").matches(ma) || slug(tenNgan(ten)).isEmpty()) continue
                out += Kenh(ten, ma, nhom.optString("name").trim(), c.optString("thumbnail").ifBlank { c.optString("logo") }, c.optBoolean("mustLogin", false))
            }
        }
        return out
    }

    /**
     * Ảnh ô cho kênh [ma]: đã tải lần trước thì lấy trong bộ nhớ đệm của app, không thì tải thumbnail [url] của VTVgo,
     * thu về 320×180 rồi cất. null nếu không tải được (launcher vẽ ô chữ).
     */
    fun anhO(ctx: Context, ma: String, url: String?): ByteArray? {
        val f = File(File(ctx.cacheDir, "kenh-vtvgo").apply { mkdirs() }, "$ma.jpg")
        if (f.isFile && f.length() > 0) return runCatching { f.readBytes() }.getOrNull()
        val b = url?.let { taiAnh(it) } ?: return null
        runCatching { f.writeBytes(b) }
        return b
    }

    /** Ảnh ô kênh 320×180 JPEG từ thumbnail của VTVgo (webp), null nếu không tải được. */
    private fun taiAnh(url: String): ByteArray? = runCatching {
        if (!url.startsWith("https://")) return null
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        val goc = c.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
        val ra = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val cv = Canvas(ra); cv.drawColor(Color.WHITE)
        // Vừa khung, giữ tỉ lệ, giữa ô.
        val tl = minOf(320f / goc.width, 180f / goc.height)
        val w = (goc.width * tl).toInt(); val h = (goc.height * tl).toInt()
        cv.drawBitmap(goc, null, Rect((320 - w) / 2, (180 - h) / 2, (320 + w) / 2, (180 + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        ByteArrayOutputStream().also { ra.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }.getOrNull()
}
