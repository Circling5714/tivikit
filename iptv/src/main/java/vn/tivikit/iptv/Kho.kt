package vn.tivikit.iptv

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Link danh sách IPTV (TiviKit đặt qua ADB) và bản danh sách đã tải (file + lúc tải). Luôn tải theo link người dùng đặt —
 * link rút gọn cũng được, đi theo chuyển hướng tới file thật mỗi lần — nên danh sách đổi nguồn kênh thì app thấy ở lần tải
 * sau. Mở kênh mà bản đã tải cũ quá [CU] thì tải lại trước; tải không được thì dùng bản cũ.
 */
class Kho(ctx: Context) {
    private val app = ctx.applicationContext
    private val p = app.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    private val file = File(app.filesDir, "danh-sach.m3u")

    var link: String
        get() = p.getString("link", null).orEmpty()
        set(v) { p.edit().putString("link", v).remove("luc_tai").apply(); file.delete(); nho = null }

    /** Lúc tải danh sách gần nhất (ms), 0 = chưa tải. */
    val lucTai: Long get() = p.getLong("luc_tai", 0)

    /** Danh sách đã tải (đọc file một lần rồi nhớ). */
    @Synchronized
    fun daTai(): List<KenhIptv> {
        nho?.let { return it }
        val ds = runCatching { if (file.isFile) DanhSach.doc(file.readText()) else emptyList() }.getOrDefault(emptyList())
        nho = ds
        return ds
    }

    /** Tải lại danh sách theo link (chặn tới khi xong, tối đa vài chục giây); null nếu không tải được hay không có kênh nào. */
    @Synchronized
    fun taiLai(): List<KenhIptv>? {
        val l = link.takeIf { it.isNotEmpty() } ?: return null
        val chu = runCatching { tai(l) }.getOrNull() ?: return null
        val ds = DanhSach.doc(chu)
        if (ds.isEmpty()) return null
        runCatching { file.writeText(chu) }
        p.edit().putLong("luc_tai", System.currentTimeMillis()).apply()
        nho = ds
        return ds
    }

    /** Danh sách để mở kênh: bản đã tải nếu còn mới, không thì tải lại (lỗi mạng thì bản cũ). */
    fun choMoKenh(): List<KenhIptv> {
        val cu = daTai()
        if (cu.isNotEmpty() && System.currentTimeMillis() - lucTai < CU) return cu
        return taiLai() ?: cu
    }

    companion object {
        /** Bản đã tải cũ hơn thế này thì mở kênh sẽ tải lại trước (2 giờ). */
        const val CU = 2 * 3_600_000L
        @Volatile private var nho: List<KenhIptv>? = null

        /** Tải văn bản ở [url], đi theo chuyển hướng (cả http ↔ https, tối đa 6 lần), 10 giây mỗi bước. */
        fun tai(url: String): String {
            var u = url
            repeat(6) {
                val c = URL(u).openConnection() as HttpURLConnection
                try {
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 10_000; c.readTimeout = 10_000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) TiviKit-IPTV")
                    val ma = c.responseCode
                    if (ma in 300..399) {
                        u = URL(URL(u), c.getHeaderField("Location") ?: error("chuyen huong rong")).toString()
                        return@repeat
                    }
                    if (ma !in 200..299) error("HTTP $ma")
                    return c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
                } finally {
                    c.disconnect()
                }
            }
            error("chuyen huong qua nhieu")
        }
    }
}
