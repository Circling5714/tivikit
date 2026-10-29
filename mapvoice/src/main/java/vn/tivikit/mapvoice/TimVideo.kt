package vn.tivikit.mapvoice

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Link YouTube cho app YouTube trên tivi (SmartTube nhận cả hai dạng): trang tìm kiếm, hay video đầu tiên của trang tìm kiếm
 * (câu "mở / phát …": phát luôn, như Super Voice). Tìm video đầu bằng trang tìm kiếm công khai của YouTube, không đăng nhập.
 */
object TimVideo {
    fun linkTim(tim: String): String = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(tim, "UTF-8")

    fun linkXem(id: String): String = "https://www.youtube.com/watch?v=$id"

    /** Mã video thường đầu tiên trong trang tìm kiếm (bỏ qua quảng cáo, Shorts, danh sách phát); null nếu không thấy. */
    fun maDau(html: String): String? =
        Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
            ?: Regex("/watch\\?v=([A-Za-z0-9_-]{11})").find(html)?.groupValues?.get(1)

    /** Tải trang tìm kiếm rồi lấy video đầu tiên (gọi ngoài luồng chính); null nếu mạng lỗi / quá giờ. */
    fun tim(tim: String, choToiDa: Int = 5000): String? = runCatching {
        val c = URL(linkTim(tim) + "&hl=vi&gl=VN").openConnection() as HttpURLConnection
        c.connectTimeout = choToiDa
        c.readTimeout = choToiDa
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
        c.setRequestProperty("Accept-Language", "vi,en;q=0.8")
        try {
            // Video đầu nằm trong ytInitialData ở nửa đầu trang: đọc tối đa 3 MB (readNBytes chỉ có từ Android 13).
            val ra = java.io.ByteArrayOutputStream()
            c.inputStream.use { s ->
                val dem = ByteArray(16 * 1024)
                while (ra.size() < 3 * 1024 * 1024) {
                    val n = s.read(dem)
                    if (n < 0) break
                    ra.write(dem, 0, n)
                }
            }
            maDau(ra.toString(Charsets.UTF_8.name()))
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
