package vn.tivikit.teleremote

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gọi Bot API của Telegram (https://api.telegram.org/bot<token>/<lệnh>) bằng HttpURLConnection, gửi và nhận JSON, không
 * thư viện ngoài. Token nằm trong đường dẫn: không ghi đường dẫn hay lỗi kèm đường dẫn ra nhật ký.
 */
class Telegram(private val token: String) {
    /** Telegram trả ok=false: [ma] = error_code (401 / 404 token sai, 409 bot đang được nơi khác hỏi, 429 gửi quá nhanh). */
    class Loi(val ma: Int, val moTa: String, val choGiay: Int = 0) : IOException("Telegram $ma: $moTa")

    @Volatile private var dangGoi: HttpURLConnection? = null

    /** Gọi lệnh [lenh] với [thamSo]; trả phần result. Lỗi mạng: IOException; Telegram từ chối: [Loi]. */
    fun goi(lenh: String, thamSo: JSONObject = JSONObject(), choToiDa: Int = 15_000): Any? {
        val c = URL("$GOC/bot$token/$lenh").openConnection() as HttpURLConnection
        dangGoi = c
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10_000
            c.readTimeout = choToiDa
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(thamSo.toString().toByteArray(Charsets.UTF_8)) }
            return ketQua(c)
        } finally {
            c.disconnect()
            if (dangGoi === c) dangGoi = null
        }
    }

    /** Đọc trả lời của Telegram; ok=false thì [Loi]. */
    private fun ketQua(c: HttpURLConnection): Any? {
        val ma = c.responseCode
        val chu = (if (ma >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        val j = runCatching { JSONObject(chu) }.getOrNull() ?: throw IOException("Telegram trả lời lạ ($ma)")
        if (!j.optBoolean("ok")) {
            throw Loi(j.optInt("error_code", ma), j.optString("description"), j.optJSONObject("parameters")?.optInt("retry_after") ?: 0)
        }
        return j.opt("result")
    }

    /** Ngắt lời gọi đang chờ (getUpdates chờ tới [CHO_GIAY] giây): đổi token, tắt dịch vụ. */
    fun ngat() {
        runCatching { dangGoi?.disconnect() }
    }

    /** Thông tin bot (getMe): username, tên. */
    fun toi(): JSONObject = goi("getMe") as? JSONObject ?: JSONObject()

    /** Tin mới từ [offset], chờ tối đa [choGiay] giây nếu chưa có (long polling: không cần mở cổng hay máy chủ). */
    fun capNhat(offset: Long, choGiay: Int = CHO_GIAY): List<JSONObject> {
        val r = goi("getUpdates", JSONObject().put("offset", offset).put("timeout", choGiay)
            .put("allowed_updates", JSONArray(listOf("message", "callback_query"))), (choGiay + 15) * 1000) as? JSONArray ?: return emptyList()
        return (0 until r.length()).mapNotNull { r.optJSONObject(it) }
    }

    /**
     * Tin dồn lại từ lúc bot chưa hỏi (tivi tắt, mất mạng, dịch vụ vừa chạy lại; Telegram giữ 24 giờ), cũ trước, cùng offset
     * để hỏi tiếp; lấy hết (mỗi lần tối đa 100) để không sót nút bấm cũ vào vòng hỏi thường. Bot chỉ làm tin chữ vừa gửi.
     */
    fun tinDon(): Pair<List<JSONObject>, Long> {
        val ds = mutableListOf<JSONObject>()
        var offset = 0L
        repeat(10) {
            val r = goi("getUpdates", JSONObject().put("offset", offset).put("timeout", 0)
                .put("allowed_updates", JSONArray(listOf("message", "callback_query")))) as? JSONArray ?: return ds to offset
            for (i in 0 until r.length()) r.optJSONObject(i)?.let { ds += it; offset = it.optLong("update_id") + 1 }
            if (r.length() < 100) return ds to offset
        }
        return ds to offset
    }

    /** Gửi tin [chu] (chữ thường, không định dạng) kèm bàn phím [banPhim]; trả số tin. */
    fun gui(chat: Long, chu: String, banPhim: JSONObject? = null): Long =
        (goi("sendMessage", JSONObject().put("chat_id", chat).put("text", chu).apply { if (banPhim != null) put("reply_markup", banPhim) }) as? JSONObject)
            ?.optLong("message_id") ?: 0

    /** Sửa tin [idTin]; tin y hệt (bấm cùng nút hai lần) thì Telegram báo "not modified": bỏ qua. */
    fun sua(chat: Long, idTin: Long, chu: String, banPhim: JSONObject? = null) {
        try {
            goi("editMessageText", JSONObject().put("chat_id", chat).put("message_id", idTin).put("text", chu)
                .apply { if (banPhim != null) put("reply_markup", banPhim) })
        } catch (e: Loi) {
            if (!e.moTa.contains("not modified")) throw e
        }
    }

    /** Trả lời nút bấm (tắt vòng chờ trên điện thoại); [chu] hiện thành thông báo nhỏ, [canhBao] = hộp phải bấm OK. */
    fun traLoiNut(id: String, chu: String = "", canhBao: Boolean = false) {
        goi("answerCallbackQuery", JSONObject().put("callback_query_id", id).apply {
            if (chu.isNotEmpty()) put("text", chu.take(190))
            if (canhBao) put("show_alert", true)
        })
    }

    /** Gửi ảnh JPEG [anh] (chụp màn hình tivi) kèm [chuThich] và nút [banPhim]. */
    fun guiAnh(chat: Long, anh: ByteArray, chuThich: String, banPhim: JSONObject? = null) =
        guiTep("sendPhoto", "photo", "tivi.jpg", "image/jpeg", anh, chat, chuThich, banPhim)

    /** Gửi video MP4 [video] (quay màn hình tivi) kèm [chuThich] và nút [banPhim]; xem được ngay khi đang tải. */
    fun guiVideo(chat: Long, video: ByteArray, chuThich: String, banPhim: JSONObject? = null) =
        guiTep("sendVideo", "video", "tivi.mp4", "video/mp4", video, chat, chuThich, banPhim, mapOf("supports_streaming" to "true"))

    /** Tải tệp lên kiểu multipart/form-data (sendPhoto, sendVideo). */
    private fun guiTep(lenh: String, truong: String, tenTep: String, loai: String, du: ByteArray, chat: Long, chuThich: String,
                       banPhim: JSONObject?, them: Map<String, String> = emptyMap()) {
        val ranh = "tivikit" + System.nanoTime()
        val than = ByteArrayOutputStream().apply {
            fun phan(ten: String, giaTri: String) = write("--$ranh\r\nContent-Disposition: form-data; name=\"$ten\"\r\n\r\n$giaTri\r\n".toByteArray(Charsets.UTF_8))
            phan("chat_id", chat.toString())
            if (chuThich.isNotEmpty()) phan("caption", chuThich.take(1000))
            if (banPhim != null) phan("reply_markup", banPhim.toString())
            them.forEach { (k, v) -> phan(k, v) }
            write("--$ranh\r\nContent-Disposition: form-data; name=\"$truong\"; filename=\"$tenTep\"\r\nContent-Type: $loai\r\n\r\n".toByteArray(Charsets.UTF_8))
            write(du)
            write("\r\n--$ranh--\r\n".toByteArray(Charsets.UTF_8))
        }.toByteArray()
        val c = URL("$GOC/bot$token/$lenh").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10_000
            c.readTimeout = 120_000
            c.setFixedLengthStreamingMode(than.size)
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$ranh")
            c.outputStream.use { it.write(than) }
            ketQua(c)
        } finally {
            c.disconnect()
        }
    }

    /**
     * Tải tệp [id] (file_id của ảnh / video người dùng vừa gửi) về [dich]: getFile lấy đường dẫn rồi tải
     * $GOC/file/bot<token>/<đường dẫn>. Lớn quá [toiDa] thì thôi (IOException). Bot chỉ tải được tệp tới 20 MB.
     */
    fun taiTep(id: String, dich: File, toiDa: Long) {
        val f = goi("getFile", JSONObject().put("file_id", id)) as? JSONObject ?: throw IOException("getFile")
        val duong = f.optString("file_path").takeIf { it.isNotEmpty() } ?: throw IOException("getFile")
        if (f.optLong("file_size") > toiDa) throw IOException("lon-qua")
        val c = URL("$GOC/file/bot$token/$duong").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 60_000
            if (c.responseCode != 200) throw IOException("tai ${c.responseCode}")
            c.inputStream.use { vao ->
                dich.outputStream().use { ra ->
                    val dem = ByteArray(64 * 1024)
                    var tong = 0L
                    while (true) {
                        val n = vao.read(dem)
                        if (n < 0) break
                        tong += n
                        if (tong > toiDa) throw IOException("lon-qua")
                        ra.write(dem, 0, n)
                    }
                }
            }
        } catch (e: IOException) {
            dich.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }

    /** Xoá tin [idTin] (bot xoá được tin người dùng gửi trong chat riêng: mã mở khoá sau khi dùng). */
    fun xoaTin(chat: Long, idTin: Long) {
        goi("deleteMessage", JSONObject().put("chat_id", chat).put("message_id", idTin))
    }

    /** Hiện "đang gõ…" / "đang gửi ảnh…" trong chat lúc tivi làm việc lâu (tự tắt sau 5 giây hay khi bot trả lời). */
    fun dangLam(chat: Long, viec: String) {
        goi("sendChatAction", JSONObject().put("chat_id", chat).put("action", viec))
    }

    /** Danh sách lệnh / hiện ở nút Menu của chat với bot. */
    fun datLenh(ds: List<Pair<String, String>>) {
        goi("setMyCommands", JSONObject().put("commands", JSONArray(ds.map { (lenh, moTa) -> JSONObject().put("command", lenh).put("description", moTa) })))
    }

    /** Bot đang đặt webhook thì getUpdates bị từ chối (409): bỏ webhook (tin đang chờ vẫn giữ). */
    fun boWebhook() {
        goi("deleteWebhook")
    }

    companion object {
        const val GOC = "https://api.telegram.org"
        const val CHO_GIAY = 50
        /** Token của @BotFather: "<số bot>:<35 ký tự>". */
        val TOKEN = Regex("\\d{5,12}:[A-Za-z0-9_-]{30,50}")
    }
}
