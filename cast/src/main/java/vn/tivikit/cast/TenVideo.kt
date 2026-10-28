package vn.tivikit.cast

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Tên và kênh của một video YouTube, tra bằng oEmbed của YouTube, hỏng thì bằng API player của trang web YouTube (không
 * cần khoá API; tên gốc của video, không phải tên YouTube dịch). SmartTube mở video từ đường link (video cast sang) thì
 * phiên phát chỉ có mã video, không có tên bài: nó ghi phiên lúc chưa tải xong tên và không ghi lại (28/10/2026,
 * Skyworth). Tra nền, nhớ trong bộ nhớ; tra hỏng thì 10 phút sau mới thử lại. TiviKit Launcher có bản giống hệt
 * (launcher/TenVideo.kt).
 */
object TenVideo {
    private val MA = Regex("^[A-Za-z0-9_-]{11}$")
    private val daBiet = ConcurrentHashMap<String, Pair<String, String>>()
    private val hong = ConcurrentHashMap<String, Long>()
    private val dangTra = ConcurrentHashMap.newKeySet<String>()

    /** (tên, kênh) của video [id] nếu đã tra được; chưa thì tra nền, lần hỏi sau có. */
    fun lay(id: String): Pair<String, String>? {
        daBiet[id]?.let { return it }
        if (!MA.matches(id)) return null
        if ((hong[id] ?: 0L) > System.currentTimeMillis() - 10 * 60_000L) return null
        if (!dangTra.add(id)) return null
        thread(isDaemon = true) {
            try {
                val kq = runCatching { tra(id) }.getOrNull()
                if (kq == null) hong[id] = System.currentTimeMillis()
                else { if (daBiet.size > 200) daBiet.clear(); daBiet[id] = kq }
            } finally {
                dangTra.remove(id)
            }
        }
        return null
    }

    /** JSON oEmbed → (tên, kênh); null nếu không có tên. */
    fun tuJson(s: String): Pair<String, String>? {
        val j = runCatching { JSONObject(s) }.getOrNull() ?: return null
        val ten = j.optString("title").trim().takeIf { it.isNotEmpty() } ?: return null
        return ten to j.optString("author_name").trim()
    }

    /** JSON của API player (chỉ xin videoDetails) → (tên, kênh); null nếu không có tên. */
    fun tuPlayer(s: String): Pair<String, String>? {
        val v = runCatching { JSONObject(s).getJSONObject("videoDetails") }.getOrNull() ?: return null
        val ten = v.optString("title").trim().takeIf { it.isNotEmpty() } ?: return null
        return ten to v.optString("author").trim()
    }

    private fun tra(id: String): Pair<String, String>? = traOembed(id) ?: traPlayer(id)

    private fun traOembed(id: String): Pair<String, String>? {
        val link = URLEncoder.encode("https://www.youtube.com/watch?v=$id", "UTF-8")
        val c = URL("https://www.youtube.com/oembed?format=json&url=$link").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            if (c.responseCode != 200) null else tuJson(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally {
            c.disconnect()
        }
    }

    /**
     * Video tắt quyền nhúng (hay gặp ở video nhạc) thì oEmbed trả 401 (28/10/2026, Skyworth: nghe nền mất tên bài). API
     * player của trang web YouTube vẫn có tên: chỉ xin tên và kênh (X-Goog-FieldMask, trả chừng 150 byte).
     */
    private fun traPlayer(id: String): Pair<String, String>? {
        val c = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("X-Goog-FieldMask", "videoDetails(title,author)")
            val khach = JSONObject().put("clientName", "WEB").put("clientVersion", "2.20261001.00.00")
            val than = JSONObject().put("videoId", id).put("context", JSONObject().put("client", khach))
            c.outputStream.use { it.write(than.toString().toByteArray(Charsets.UTF_8)) }
            if (c.responseCode != 200) null else tuPlayer(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally {
            c.disconnect()
        }
    }
}
