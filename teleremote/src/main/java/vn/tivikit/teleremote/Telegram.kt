package vn.tivikit.teleremote

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
            val ma = c.responseCode
            val chu = (if (ma >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val j = runCatching { JSONObject(chu) }.getOrNull() ?: throw IOException("Telegram trả lời lạ ($ma)")
            if (!j.optBoolean("ok")) {
                throw Loi(j.optInt("error_code", ma), j.optString("description"), j.optJSONObject("parameters")?.optInt("retry_after") ?: 0)
            }
            return j.opt("result")
        } finally {
            c.disconnect()
            if (dangGoi === c) dangGoi = null
        }
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
     * Bỏ các tin dồn lại lúc tivi tắt / mất mạng (Telegram giữ 24 giờ): chỉ làm lệnh gửi từ bây giờ, để "Tắt tivi" bấm từ
     * tối qua không tắt tivi vừa bật sáng nay. Trả offset để hỏi tiếp.
     */
    fun boTinCu(): Long {
        val r = goi("getUpdates", JSONObject().put("offset", -1).put("timeout", 0)) as? JSONArray ?: return 0
        return r.optJSONObject(r.length() - 1)?.optLong("update_id")?.plus(1) ?: 0
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
